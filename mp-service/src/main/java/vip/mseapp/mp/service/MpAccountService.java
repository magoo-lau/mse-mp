package vip.mseapp.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mseapp.core.context.TenantContext;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.mp.config.MpCrypt;
import vip.mseapp.mp.entity.MpAccount;
import vip.mseapp.mp.entity.MpFan;
import vip.mseapp.mp.entity.MpMenu;
import vip.mseapp.mp.entity.MpMessage;
import vip.mseapp.mp.entity.MpReplyRule;
import vip.mseapp.mp.entity.MpToken;
import vip.mseapp.mp.mapper.MpAccountMapper;
import vip.mseapp.mp.mapper.MpFanMapper;
import vip.mseapp.mp.mapper.MpMenuMapper;
import vip.mseapp.mp.mapper.MpMessageMapper;
import vip.mseapp.mp.mapper.MpReplyRuleMapper;
import vip.mseapp.mp.mapper.MpTokenMapper;

import java.util.Set;

/**
 * 公众号账号管理。
 *
 * <p>账号世界化（一切皆租户，同 mse-im 回调范式）：账号行随绑定租户世界路由（独立库租户
 * 落专属库、共享租户落绑定共享库、平台世界 0 落平台库/降级主库）；回调新路径
 * {@code /mp/callback/{tenantId}/{appId}} 凭 URL 世界段路由定位（{@link #findByTenantAndAppId}），
 * 旧路径按 appId 主库 {@code runIgnore} 兜底（deprecated，存量公众号在微信后台改配新回调 URL
 * 并迁移账号行后即收敛）。管理侧读写同随世界路由 + 显式租户归属校验，租户会话只能触达本租户账号；
 * AppID 世界内唯一（世界段已消歧，跨世界接入同一公众号时各自回调互不串线）；
 * 敏感字段加密落库、接口不回显，编辑留空表示不修改。
 */
@Service
public class MpAccountService {

    /** 敏感字段合法字符（防误录入空白/换行） */
    private static final Set<String> ENCRYPT_MODES = Set.of(
            MpAccount.ENCRYPT_PLAIN, MpAccount.ENCRYPT_COMPATIBLE, MpAccount.ENCRYPT_SAFE);

    private final MpAccountMapper accountMapper;
    private final MpFanMapper fanMapper;
    private final MpMessageMapper messageMapper;
    private final MpMenuMapper menuMapper;
    private final MpReplyRuleMapper ruleMapper;
    private final MpTokenMapper tokenMapper;
    private final MpCrypt crypt;
    private final MpTokenManager tokenManager;

    public MpAccountService(MpAccountMapper accountMapper, MpFanMapper fanMapper,
                            MpMessageMapper messageMapper, MpMenuMapper menuMapper,
                            MpReplyRuleMapper ruleMapper, MpTokenMapper tokenMapper,
                            MpCrypt crypt, MpTokenManager tokenManager) {
        this.accountMapper = accountMapper;
        this.fanMapper = fanMapper;
        this.messageMapper = messageMapper;
        this.menuMapper = menuMapper;
        this.ruleMapper = ruleMapper;
        this.tokenMapper = tokenMapper;
        this.crypt = crypt;
        this.tokenManager = tokenManager;
    }

    public PageResult<MpAccount> page(long pageNum, long pageSize, String keyword, Integer status) {
        Long world = TenantContext.getRequiredTenantId();
        LambdaQueryWrapper<MpAccount> wrapper = new LambdaQueryWrapper<MpAccount>()
                .eq(MpAccount::getTenantId, world)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MpAccount::getName, keyword).or().like(MpAccount::getAppId, keyword))
                .eq(status != null, MpAccount::getStatus, status)
                .orderByDesc(MpAccount::getId);
        Page<MpAccount> page = TenantContext.runAs(world, () ->
                accountMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
        // 敏感字段不回显（密文也不出域）
        page.getRecords().forEach(acc -> {
            acc.setAppSecret(null);
            acc.setCallbackToken(null);
            acc.setAesKey(null);
        });
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    public MpAccount get(Long id) {
        MpAccount account = requireAccount(id);
        account.setAppSecret(null);
        account.setCallbackToken(null);
        account.setAesKey(null);
        return account;
    }

    /** 按 租户+AppID 查账号（新回调路径专用：URL 世界段直路由租户库，token/AESKey 在租户库内校验） */
    public MpAccount findByTenantAndAppId(Long tenantId, String appId) {
        try {
            return TenantContext.runAs(tenantId, () -> accountMapper.selectOne(
                    new LambdaQueryWrapper<MpAccount>()
                            .eq(MpAccount::getTenantId, tenantId)
                            .eq(MpAccount::getAppId, appId)));
        } catch (Exception e) {
            // 租户已注销等路由失败场景按未接入处理（微信侧应答 success 避免无意义重试）
            return null;
        }
    }

    /** 按 AppID 查账号（旧回调路径兜底：主库跨租户查；deprecated——存量公众号改配新回调 URL 后收敛） */
    public MpAccount findByAppId(String appId) {
        return TenantContext.runIgnore(() -> accountMapper.selectOne(new LambdaQueryWrapper<MpAccount>()
                .eq(MpAccount::getAppId, appId)));
    }

    /** 取原始实体（含密文凭据）：供菜单同步/token 管理等需要真实凭据的内部链路使用 */
    public MpAccount getRaw(Long id) {
        return requireAccount(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(String appId, String name, String type, boolean verified,
                       String encryptMode, String appSecret, String callbackToken,
                       String aesKey, String remark) {
        String trimmedAppId = requireText(appId, "AppID 不能为空").trim();
        validateCommon(type, encryptMode, appSecret, callbackToken, aesKey);
        Long world = TenantContext.getRequiredTenantId();
        // AppID 世界内唯一（世界段已消歧，跨世界同号各自回调互不串线）：本世界查重
        Long duplicated = TenantContext.runAs(world, () -> accountMapper.selectCount(
                new LambdaQueryWrapper<MpAccount>().eq(MpAccount::getAppId, trimmedAppId)));
        if (duplicated != null && duplicated > 0) {
            throw new ServiceException(400, "AppID 已被接入（本租户已存在同号公众号）");
        }
        MpAccount account = new MpAccount();
        account.setAppId(trimmedAppId);
        applyCommon(account, name, type, verified, encryptMode, remark);
        account.setAppSecret(crypt.encrypt(appSecret));
        account.setCallbackToken(crypt.encrypt(callbackToken));
        account.setAesKey(crypt.encrypt(aesKey));
        account.setStatus(MpAccount.STATUS_ENABLED);
        account.setTenantId(world);
        TenantContext.runAs(world, () -> accountMapper.insert(account));
        return account.getId();
    }

    /** 更新基础信息；三个凭据留空 = 保持不变 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String name, String type, Boolean verified, String encryptMode,
                       String appSecret, String callbackToken, String aesKey, String remark) {
        validateCommon(type, encryptMode, appSecret, callbackToken, aesKey);
        MpAccount account = requireAccount(id);
        applyCommon(account, name, type,
                verified != null ? verified : intToBool(account.getVerified()), encryptMode, remark);
        if (appSecret != null && !appSecret.isBlank()) {
            account.setAppSecret(crypt.encrypt(appSecret));
        }
        if (callbackToken != null && !callbackToken.isBlank()) {
            account.setCallbackToken(crypt.encrypt(callbackToken));
        }
        if (aesKey != null && !aesKey.isBlank()) {
            account.setAesKey(crypt.encrypt(aesKey));
        }
        TenantContext.runAs(account.getTenantId(), () -> accountMapper.updateById(account));
        // 凭据可能变更：清空 token 缓存，下次取号强制重取
        tokenManager.evict(account);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, boolean enable) {
        MpAccount account = requireAccount(id);
        account.setStatus(enable ? MpAccount.STATUS_ENABLED : MpAccount.STATUS_DISABLED);
        TenantContext.runAs(account.getTenantId(), () -> accountMapper.updateById(account));
    }

    /** 删除账号并级联清理其下粉丝/消息/菜单/规则/token（级联随账号世界同库执行） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MpAccount account = requireAccount(id);
        TenantContext.runAs(account.getTenantId(), () -> {
            fanMapper.delete(new LambdaQueryWrapper<MpFan>().eq(MpFan::getAccountId, id));
            messageMapper.delete(new LambdaQueryWrapper<MpMessage>().eq(MpMessage::getAccountId, id));
            menuMapper.delete(new LambdaQueryWrapper<MpMenu>().eq(MpMenu::getAccountId, id));
            ruleMapper.delete(new LambdaQueryWrapper<MpReplyRule>().eq(MpReplyRule::getAccountId, id));
            tokenMapper.delete(new LambdaQueryWrapper<MpToken>().eq(MpToken::getAccountId, id));
            accountMapper.deleteById(id);
        });
    }

    /** 连通性测试：强制刷新 access_token，成功返回空错误，失败返回错误文案 */
    public String test(Long id) {
        MpAccount account = requireAccount(id);
        try {
            tokenManager.getToken(account, true);
            return null;
        } catch (ServiceException e) {
            return e.getMessage();
        }
    }

    /** 本租户自有账号（随世界路由读取 + 归属等值校验；与回调路由同世界） */
    private MpAccount requireAccount(Long id) {
        MpAccount account = TenantContext.runAs(TenantContext.getRequiredTenantId(), () ->
                accountMapper.selectById(id));
        if (account == null || !TenantContext.getRequiredTenantId().equals(account.getTenantId())) {
            throw new ServiceException(404, "公众号不存在");
        }
        return account;
    }

    private void validateCommon(String type, String encryptMode,
                                String appSecret, String callbackToken, String aesKey) {
        if (type != null && !MpAccount.TYPE_SUBSCRIPTION.equals(type) && !MpAccount.TYPE_SERVICE.equals(type)) {
            throw new ServiceException(400, "类型仅支持 subscription/service");
        }
        if (encryptMode != null && !ENCRYPT_MODES.contains(encryptMode)) {
            throw new ServiceException(400, "加密模式仅支持 plain/compatible/safe");
        }
        if (appSecret != null && !appSecret.isBlank() && appSecret.length() != 32) {
            throw new ServiceException(400, "AppSecret 长度应为 32 位");
        }
        if (callbackToken != null && !callbackToken.isBlank() && (callbackToken.length() < 3 || callbackToken.length() > 32)) {
            throw new ServiceException(400, "Token 长度应为 3-32 位");
        }
        if (aesKey != null && !aesKey.isBlank() && aesKey.length() != 43) {
            throw new ServiceException(400, "EncodingAESKey 长度应为 43 位");
        }
    }

    private void applyCommon(MpAccount account, String name, String type, boolean verified,
                             String encryptMode, String remark) {
        account.setName(requireText(name, "名称不能为空"));
        account.setType(type == null ? MpAccount.TYPE_SUBSCRIPTION : type);
        account.setVerified(verified ? 1 : 0);
        account.setEncryptMode(encryptMode == null ? MpAccount.ENCRYPT_SAFE : encryptMode);
        account.setRemark(remark);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(400, message);
        }
        return value.trim();
    }

    private static boolean intToBool(Integer value) {
        return value != null && value == 1;
    }
}
