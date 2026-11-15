package vip.mseapp.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vip.mseapp.core.context.TenantContext;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.mp.config.MpCrypt;
import vip.mseapp.mp.entity.MpAccount;
import vip.mseapp.mp.entity.MpToken;
import vip.mseapp.mp.mapper.MpTokenMapper;
import vip.mseapp.mp.wx.WxClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * access_token 管理：每号一份内存缓存 + mp_token 表双写。
 *
 * <p>设计要点（对齐 go-wechat）：
 * <ul>
 *   <li>过期前 5 分钟预刷新，未到期直接用缓存；</li>
 *   <li>同 appId 并发刷新用对象锁串行化（等价 singleflight），避免同时打满微信配额；</li>
 *   <li>成功/失败均落库——失败原因（40125 密钥错误 / 48001 能力未开放等）供管理端展示；</li>
 *   <li>落库写入统一 {@link TenantContext#runAs}，兼容管理端与回调线程两种上下文。</li>
 * </ul>
 */
@Component
public class MpTokenManager {

    private static final Logger log = LoggerFactory.getLogger(MpTokenManager.class);
    /** 过期前 5 分钟预刷新 */
    private static final long REFRESH_AHEAD_MS = 5 * 60 * 1000L;

    private record CachedToken(String token, long expiresAtMs) {
    }

    private final Map<Long, CachedToken> cache = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    private final WxClient wxClient;
    private final MpCrypt crypt;
    private final MpTokenMapper tokenMapper;

    public MpTokenManager(WxClient wxClient, MpCrypt crypt, MpTokenMapper tokenMapper) {
        this.wxClient = wxClient;
        this.crypt = crypt;
        this.tokenMapper = tokenMapper;
    }

    /**
     * 获取可用 access_token。
     *
     * @param forceRefresh true 时强制刷新（连通性测试）
     */
    public String getToken(MpAccount account, boolean forceRefresh) {
        if (!forceRefresh) {
            CachedToken cached = cache.get(account.getId());
            if (cached != null && cached.expiresAtMs() - REFRESH_AHEAD_MS > System.currentTimeMillis()) {
                return cached.token();
            }
        }
        synchronized (locks.computeIfAbsent(account.getId(), k -> new Object())) {
            if (!forceRefresh) {
                CachedToken cached = cache.get(account.getId());
                if (cached != null && cached.expiresAtMs() - REFRESH_AHEAD_MS > System.currentTimeMillis()) {
                    return cached.token();
                }
            }
            return refresh(account);
        }
    }

    /** token 状态（mp_token 表最近一次结果，供管理端展示） */
    public MpToken statusOf(MpAccount account) {
        return tokenMapper.selectOne(new LambdaQueryWrapper<MpToken>()
                .eq(MpToken::getAccountId, account.getId()));
    }

    private String refresh(MpAccount account) {
        String appId = account.getAppId();
        JsonNode resp = wxClient.accessToken(appId, crypt.decrypt(account.getAppSecret()));
        int errCode = resp.path("errcode").asInt(0);
        String tokenValue = resp.path("access_token").asText(null);
        if (errCode != 0 || tokenValue == null) {
            String errMsg = resp.path("errmsg").asText("unknown");
            persistQuietly(account, null, null, String.valueOf(errCode), errMsg);
            throw new ServiceException(500, "获取 access_token 失败[" + errCode + "]：" + errMsg);
        }
        long expiresAtMs = System.currentTimeMillis() + resp.path("expires_in").asLong(7200) * 1000L;
        cache.put(account.getId(), new CachedToken(tokenValue, expiresAtMs));
        persistQuietly(account, tokenValue, millisToDateTime(expiresAtMs), null, null);
        log.debug("[mse-mp] access_token 已刷新: appId={}", appId);
        return tokenValue;
    }

    /** 落库（含失败原因）；失败不抛出，不影响主流程 */
    private void persistQuietly(MpAccount account, String token, LocalDateTime expiresAt,
                                String errCode, String errMsg) {
        try {
            TenantContext.runAs(account.getTenantId(), () -> {
                MpToken row = tokenMapper.selectOne(new LambdaQueryWrapper<MpToken>()
                        .eq(MpToken::getAccountId, account.getId()));
                boolean create = row == null;
                if (create) {
                    row = new MpToken();
                    row.setAccountId(account.getId());
                }
                row.setToken(token == null ? row.getToken() : crypt.encrypt(token));
                row.setExpiresAt(expiresAt != null ? expiresAt : row.getExpiresAt());
                row.setErrCode(errCode);
                row.setErrMsg(errMsg != null && errMsg.length() > 480 ? errMsg.substring(0, 480) : errMsg);
                if (create) {
                    tokenMapper.insert(row);
                } else {
                    tokenMapper.updateById(row);
                }
            });
        } catch (Exception e) {
            log.warn("[mse-mp] access_token 状态落库失败: appId={}", account.getAppId(), e);
        }
    }

    private static LocalDateTime millisToDateTime(long epochMs) {
        return LocalDateTime.now().plusSeconds((epochMs - System.currentTimeMillis()) / 1000);
    }

    /** 清空某账号缓存（账号凭据变更后调用） */
    public void evict(MpAccount account) {
        cache.remove(account.getId());
    }

    /** 供测试注入：以 Map 形式检查缓存 */
    boolean isCached(Long accountId) {
        return cache.containsKey(accountId);
    }
}
