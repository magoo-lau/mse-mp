package vip.mseapp.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import vip.mseapp.core.context.TenantContext;
import vip.mseapp.mp.config.MpCrypt;
import vip.mseapp.mp.entity.MpAccount;
import vip.mseapp.mp.entity.MpFan;
import vip.mseapp.mp.entity.MpMessage;
import vip.mseapp.mp.entity.MpReplyRule;
import vip.mseapp.mp.mapper.MpFanMapper;
import vip.mseapp.mp.mapper.MpMessageMapper;
import vip.mseapp.mp.mapper.MpReplyRuleMapper;
import vip.mseapp.mp.wx.WxClient;
import vip.mseapp.mp.wx.WxCrypto;
import vip.mseapp.mp.wx.WxSignatures;
import vip.mseapp.mp.wx.WxXmlUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信回调核心链路（对齐 go-wechat processor）：
 * 验签 → 解密 → 幂等去重 → 粉丝状态流转 → 自动回复规则匹配 → 落库 → 5 秒窗口内应答。
 *
 * <p>纪律：
 * <ul>
 *   <li>任何异常都以 "success" 应答——不可恢复错误若回 500，微信会以约 5 秒间隔重试放大流量；</li>
 *   <li>回复内容落库缓存，微信重试（dedupKey 命中）时直接复用，不重复执行规则；</li>
 *   <li>回复的 FromUserName 必须为入站 ToUserName（gh_ 原始 ID），填 AppID 会被微信丢弃；</li>
 *   <li>compatible/safe 模式回复加密包装（signature = sha1(sort(token, encrypt, ts, nonce))）。</li>
 * </ul>
 */
@Service
public class MpCallbackService {

    private static final Logger log = LoggerFactory.getLogger(MpCallbackService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 规则匹配顺序：先到先得，与 go-wechat 一致 */
    private static final List<String> MATCH_ORDER = List.of(
            MpReplyRule.MATCH_SUBSCRIBE, MpReplyRule.MATCH_UNSUBSCRIBE, MpReplyRule.MATCH_CLICK,
            MpReplyRule.MATCH_MSG_ANY, MpReplyRule.MATCH_KEYWORD_EXACT,
            MpReplyRule.MATCH_KEYWORD_CONTAINS, MpReplyRule.MATCH_DEFAULT);

    private final MpMessageMapper messageMapper;
    private final MpFanMapper fanMapper;
    private final MpReplyRuleMapper ruleMapper;
    private final MpCrypt crypt;
    private final MpTokenManager tokenManager;
    private final WxClient wxClient;

    public MpCallbackService(MpMessageMapper messageMapper, MpFanMapper fanMapper,
                             MpReplyRuleMapper ruleMapper, MpCrypt crypt,
                             MpTokenManager tokenManager, WxClient wxClient) {
        this.messageMapper = messageMapper;
        this.fanMapper = fanMapper;
        this.ruleMapper = ruleMapper;
        this.crypt = crypt;
        this.tokenManager = tokenManager;
        this.wxClient = wxClient;
    }

    /** 接入验证（GET）：校验通过原样返回 echostr */
    public String verify(MpAccount account, String signature, String timestamp, String nonce, String echostr) {
        String token = crypt.decrypt(account.getCallbackToken());
        return WxSignatures.signature(token, timestamp, nonce).equals(signature) ? echostr : "fail";
    }

    /**
     * 消息/事件推送（POST）。返回应答报文：被动回复 XML（按需加密）或 "success"。
     * 全链路兜底 catch，任何异常不外抛。
     */
    public String onPush(MpAccount account, String body, String signature, String timestamp, String nonce) {
        try {
            String token = crypt.decrypt(account.getCallbackToken());
            Map<String, String> envelope = WxXmlUtil.parse(body);
            String plaintext;
            if (MpAccount.ENCRYPT_PLAIN.equals(account.getEncryptMode())) {
                if (!WxSignatures.signature(token, timestamp, nonce).equals(signature)) {
                    log.warn("[mse-mp] 回调验签失败（明文模式）: appId={}", account.getAppId());
                    return "success";
                }
                plaintext = body;
            } else {
                String encrypt = WxXmlUtil.extractEncrypt(envelope);
                if (!WxSignatures.signature(token, timestamp, nonce, encrypt).equals(signature)) {
                    log.warn("[mse-mp] 回调验签失败（密文模式）: appId={}", account.getAppId());
                    return "success";
                }
                plaintext = new WxCrypto(crypt.decrypt(account.getAesKey()), account.getAppId()).decrypt(encrypt);
            }
            Map<String, String> msg = WxXmlUtil.parse(plaintext);
            String replyXml = TenantContext.runAs(account.getTenantId(), () -> process(account, msg, plaintext));

            if (replyXml == null) {
                return "success";
            }
            if (MpAccount.ENCRYPT_PLAIN.equals(account.getEncryptMode())) {
                return replyXml;
            }
            // compatible/safe：回复加密包装
            WxCrypto crypto = new WxCrypto(crypt.decrypt(account.getAesKey()), account.getAppId());
            String encrypted = crypto.encrypt(replyXml);
            String replySignature = WxSignatures.signature(token, timestamp, nonce, encrypted);
            return WxXmlUtil.encryptedReply(encrypted, replySignature, timestamp, nonce);
        } catch (Exception e) {
            log.error("[mse-mp] 回调处理异常（已按 success 应答）: appId={}", account.getAppId(), e);
            return "success";
        }
    }

    /** 租户上下文内的处理主流程：返回明文回复 XML（无回复为 null） */
    private String process(MpAccount account, Map<String, String> msg, String plaintext) {
        String msgType = msg.getOrDefault("MsgType", "").toLowerCase();
        boolean isEvent = "event".equals(msgType);
        String event = isEvent ? msg.getOrDefault("Event", "").toLowerCase() : "";
        String eventKey = msg.getOrDefault("EventKey", "");
        String openId = msg.getOrDefault("FromUserName", "");
        String toUser = msg.getOrDefault("ToUserName", "");
        String type = normalizeType(msgType, event, eventKey);
        String kind = isEvent ? MpMessage.KIND_EVENT : MpMessage.KIND_MESSAGE;

        // 幂等：微信对每条推送最多重试 3 次，dedupKey 唯一索引挡重试并复用缓存回复
        String dedupKey = dedupKey(account.getAppId(), openId, msg, msgType, event, eventKey);
        MpMessage existing = messageMapper.selectOne(new LambdaQueryWrapper<MpMessage>()
                .eq(MpMessage::getDedupKey, dedupKey));
        if (existing != null) {
            return existing.getReplyContent();
        }

        // 规则匹配 + 回复构造（规则异常不影响落库与应答）
        String replyXml = null;
        MpReplyRule rule = null;
        try {
            rule = matchRule(account.getId(), type, kind, eventKey, msg.getOrDefault("Content", ""));
        } catch (Exception e) {
            log.warn("[mse-mp] 规则匹配失败: appId={}", account.getAppId(), e);
        }
        if (rule != null) {
            try {
                replyXml = buildReply(rule, openId, toUser);
            } catch (Exception e) {
                log.warn("[mse-mp] 回复内容构造失败: ruleId={}", rule.getId(), e);
            }
        }

        // 幂等锚点尽快落库：粉丝资料补全（外部调用）若耗时，微信重试可在此秒回缓存回复
        MpMessage record = new MpMessage();
        record.setAccountId(account.getId());
        record.setOpenId(openId);
        record.setDirection(MpMessage.DIRECTION_IN);
        record.setKind(kind);
        record.setType(type);
        record.setMsgId(msg.get("MsgId"));
        record.setDedupKey(dedupKey);
        record.setPayload(buildPayload(msg, type, isEvent, event));
        record.setRawXml(plaintext.length() > 60000 ? plaintext.substring(0, 60000) : plaintext);
        record.setReplyContent(replyXml);
        record.setTraceId(account.getAppId() + "." + lastSixNano());
        try {
            messageMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发窗口下的重试：回读已插入记录复用其回复
            MpMessage winner = messageMapper.selectOne(new LambdaQueryWrapper<MpMessage>()
                    .eq(MpMessage::getDedupKey, dedupKey));
            return winner != null ? winner.getReplyContent() : null;
        }

        // 粉丝状态流转放在锚点落库之后（关注时可能拉取用户资料，外部调用耗时不可控）
        try {
            touchFan(account, type, openId, eventKey);
        } catch (Exception e) {
            log.warn("[mse-mp] 粉丝状态流转失败: appId={}, openId={}", account.getAppId(), openId, e);
        }
        return replyXml;
    }

    /** 类型归一化：事件小写、带参扫码关注 → subscribe_scan、菜单 CLICK → click */
    private String normalizeType(String msgType, String event, String eventKey) {
        if (!"event".equals(msgType)) {
            return msgType;
        }
        return switch (event) {
            case "subscribe" -> eventKey != null && eventKey.startsWith("qrscene_") ? "subscribe_scan" : "subscribe";
            case "unsubscribe" -> "unsubscribe";
            case "click" -> "click";
            case "view" -> "view";
            case "scan" -> "scan";
            case "location" -> "location";
            default -> event;
        };
    }

    /** 粉丝状态流转：关注/取关/活跃 */
    private void touchFan(MpAccount account, String type, String openId, String eventKey) {
        switch (type) {
            case "subscribe", "subscribe_scan" -> upsertSubscribed(account, openId, eventKey);
            case "unsubscribe" -> fanMapper.update(null, new LambdaUpdateWrapper<MpFan>()
                    .eq(MpFan::getAccountId, account.getId()).eq(MpFan::getOpenId, openId)
                    .set(MpFan::getSubscribed, 0).set(MpFan::getUnsubscribeTime, LocalDateTime.now()));
            default -> fanMapper.update(null, new LambdaUpdateWrapper<MpFan>()
                    .eq(MpFan::getAccountId, account.getId()).eq(MpFan::getOpenId, openId)
                    .set(MpFan::getLastActiveTime, LocalDateTime.now()));
        }
    }

    private void upsertSubscribed(MpAccount account, String openId, String eventKey) {
        LocalDateTime now = LocalDateTime.now();
        boolean scan = eventKey != null && eventKey.startsWith("qrscene_");
        MpFan fan = fanMapper.selectOne(new LambdaQueryWrapper<MpFan>()
                .eq(MpFan::getAccountId, account.getId()).eq(MpFan::getOpenId, openId));
        boolean create = fan == null;
        if (create) {
            fan = new MpFan();
            fan.setAccountId(account.getId());
            fan.setOpenId(openId);
            fan.setSubscribed(1);
            fan.setSubscribeTime(now);
            fan.setSubscribeScene(scan ? "ADD_SCENE_QR_CODE" : null);
        } else {
            fan.setSubscribed(1);
            fan.setSubscribeTime(now);
            fan.setUnsubscribeTime(null);
            if (scan) {
                fan.setSubscribeScene("ADD_SCENE_QR_CODE");
            }
        }
        // 资料补全：未认证号拉不到（48001/秘钥错误等）时标记 profileIncomplete，管理端可见
        if (create || intOne(fan.getProfileIncomplete()) || fan.getNickname() == null) {
            fillProfile(account, fan);
        }
        if (create) {
            fanMapper.insert(fan);
        } else {
            fanMapper.updateById(fan);
        }
    }

    private void fillProfile(MpAccount account, MpFan fan) {
        try {
            String token = tokenManager.getToken(account, false);
            JsonNode user = wxClient.userInfo(token, fan.getOpenId());
            if (user.path("errcode").asInt(0) != 0 || user.path("subscribe").asInt(0) != 1) {
                fan.setProfileIncomplete(1);
                return;
            }
            fan.setNickname(user.path("nickname").asText(null));
            fan.setHeadImgUrl(user.path("headimgurl").asText(null));
            fan.setSex(user.path("sex").asInt(0));
            fan.setProvince(user.path("province").asText(null));
            fan.setCity(user.path("city").asText(null));
            String unionId = user.path("unionid").asText(null);
            if (unionId != null && !unionId.isBlank()) {
                fan.setUnionId(unionId);
            }
            String scene = user.path("subscribe_scene").asText(null);
            if (scene != null && !scene.isBlank()) {
                fan.setSubscribeScene(scene);
            }
            fan.setProfileIncomplete(0);
        } catch (Exception e) {
            log.warn("[mse-mp] 粉丝资料补全失败: appId={}, openId={}", account.getAppId(), fan.getOpenId(), e);
            fan.setProfileIncomplete(1);
        }
    }

    /** 规则匹配：按固定类型顺序，同类型内 priority 大者先 */
    private MpReplyRule matchRule(Long accountId, String type, String kind, String eventKey, String content) {
        List<MpReplyRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<MpReplyRule>()
                .eq(MpReplyRule::getAccountId, accountId)
                .eq(MpReplyRule::getEnabled, 1)
                .orderByDesc(MpReplyRule::getPriority)
                .orderByAsc(MpReplyRule::getId));
        for (String matchType : MATCH_ORDER) {
            for (MpReplyRule rule : rules) {
                if (!matchType.equals(rule.getMatchType())) {
                    continue;
                }
                if (matches(rule, type, kind, eventKey, content)) {
                    return rule;
                }
            }
        }
        return null;
    }

    private boolean matches(MpReplyRule rule, String type, String kind, String eventKey, String content) {
        return switch (rule.getMatchType()) {
            case MpReplyRule.MATCH_SUBSCRIBE -> "subscribe".equals(type) || "subscribe_scan".equals(type);
            case MpReplyRule.MATCH_UNSUBSCRIBE -> "unsubscribe".equals(type);
            case MpReplyRule.MATCH_CLICK -> "click".equals(type)
                    && rule.getMatchValue() != null && rule.getMatchValue().trim().equals(eventKey);
            case MpReplyRule.MATCH_MSG_ANY -> MpMessage.KIND_MESSAGE.equals(kind);
            case MpReplyRule.MATCH_KEYWORD_EXACT -> MpMessage.KIND_MESSAGE.equals(kind)
                    && keywordHit(rule.getMatchValue(), content, true);
            case MpReplyRule.MATCH_KEYWORD_CONTAINS -> MpMessage.KIND_MESSAGE.equals(kind)
                    && keywordHit(rule.getMatchValue(), content, false);
            case MpReplyRule.MATCH_DEFAULT -> true;
            default -> false;
        };
    }

    /** 关键词多值匹配：逗号/中文逗号/空格/竖线分隔，任一命中 */
    private boolean keywordHit(String matchValue, String content, boolean exact) {
        if (matchValue == null || content == null) {
            return false;
        }
        for (String keyword : matchValue.split("[,，\\s|]+")) {
            if (keyword.isBlank()) {
                continue;
            }
            if (exact ? keyword.equals(content.trim()) : content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** replyContent JSON → 回复 XML */
    private String buildReply(MpReplyRule rule, String openId, String toUser) throws Exception {
        JsonNode json = MAPPER.readTree(rule.getReplyContent() == null ? "{}" : rule.getReplyContent());
        if (MpReplyRule.REPLY_NEWS.equals(rule.getReplyType())) {
            List<WxXmlUtil.NewsItem> items = new ArrayList<>();
            for (JsonNode node : json.path("items")) {
                items.add(new WxXmlUtil.NewsItem(
                        node.path("title").asText(""),
                        node.path("description").asText(""),
                        node.path("picUrl").asText(""),
                        node.path("url").asText("")));
            }
            return items.isEmpty() ? null : WxXmlUtil.newsReply(openId, toUser, items);
        }
        String content = json.path("content").asText("");
        return content.isBlank() ? null : WxXmlUtil.textReply(openId, toUser, content);
    }

    /** 归一化载荷（snake_case），落到 payload JSON 列 */
    private String buildPayload(Map<String, String> msg, String type, boolean isEvent, String event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msg_type", msg.getOrDefault("MsgType", "").toLowerCase());
        if (isEvent) {
            payload.put("event", event);
        }
        putIfPresent(payload, "event_key", msg.get("EventKey"));
        putIfPresent(payload, "ticket", msg.get("Ticket"));
        switch (type) {
            case "text" -> putIfPresent(payload, "content", msg.get("Content"));
            case "image" -> {
                putIfPresent(payload, "pic_url", msg.get("PicUrl"));
                putIfPresent(payload, "media_id", msg.get("MediaId"));
            }
            case "voice" -> {
                putIfPresent(payload, "format", msg.get("Format"));
                putIfPresent(payload, "media_id", msg.get("MediaId"));
                putIfPresent(payload, "recognition", msg.get("Recognition"));
            }
            case "video", "shortvideo" -> {
                putIfPresent(payload, "media_id", msg.get("MediaId"));
                putIfPresent(payload, "thumb_media_id", msg.get("ThumbMediaId"));
            }
            case "location" -> {
                if (isEvent) {
                    putIfPresent(payload, "latitude", msg.get("Latitude"));
                    putIfPresent(payload, "longitude", msg.get("Longitude"));
                    putIfPresent(payload, "precision", msg.get("Precision"));
                } else {
                    putIfPresent(payload, "location_x", msg.get("Location_X"));
                    putIfPresent(payload, "location_y", msg.get("Location_Y"));
                    putIfPresent(payload, "scale", msg.get("Scale"));
                    putIfPresent(payload, "label", msg.get("Label"));
                }
            }
            case "link" -> {
                putIfPresent(payload, "title", msg.get("Title"));
                putIfPresent(payload, "description", msg.get("Description"));
                putIfPresent(payload, "url", msg.get("Url"));
            }
            default -> putIfPresent(payload, "content", msg.get("Content"));
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 幂等锚点：MD5(appid|openid|createTime|msgType|event|eventKey|msgId) */
    private String dedupKey(String appId, String openId, Map<String, String> msg,
                            String msgType, String event, String eventKey) {
        String raw = String.join("|",
                appId, openId,
                msg.getOrDefault("CreateTime", ""),
                msgType, event,
                eventKey == null ? "" : eventKey,
                msg.getOrDefault("MsgId", ""));
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("JVM 缺少 MD5 算法", e);
        }
    }

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private static boolean intOne(Integer value) {
        return value != null && value == 1;
    }

    private static String lastSixNano() {
        String nano = String.valueOf(System.nanoTime());
        return nano.length() <= 6 ? nano : nano.substring(nano.length() - 6);
    }
}
