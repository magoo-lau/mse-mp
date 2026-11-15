package vip.mseapp.mp.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import vip.mseapp.mp.entity.MpAccount;
import vip.mseapp.mp.service.MpAccountService;
import vip.mseapp.mp.service.MpCallbackService;

/**
 * 微信服务器回调入口（匿名：以签名而非登录态鉴权）。
 *
 * <p>回调世界化（标识符自带世界段，同 mse-im 回调范式）：新路径
 * {@code /mp/callback/{tenantId}/{appId}}——凭 URL 世界段直接路由到账号所属租户库校验；
 * 旧路径 {@code /mp/callback/{appId}}（微信 appId 为外部分配、无法内嵌世界）主库兜底
 * （deprecated，存量公众号请在微信后台改配新回调 URL——管理端「接入信息」弹窗已展示新格式）。
 *
 * <p>未知/停用账号直接应答 "success"，避免微信对不可处理的消息无意义重试。
 */
@RestController
@RequestMapping("/mp/callback")
public class MpCallbackController {

    private final MpAccountService accountService;
    private final MpCallbackService callbackService;

    public MpCallbackController(MpAccountService accountService, MpCallbackService callbackService) {
        this.accountService = accountService;
        this.callbackService = callbackService;
    }

    // ==================== 新路径：URL 自带世界段（随租户库路由） ====================

    /** 接入验证（公众平台服务器配置校验）：原样返回 echostr */
    @GetMapping(value = "/{tenantId}/{appId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verifyRouted(@PathVariable Long tenantId,
                               @PathVariable String appId,
                               @RequestParam String signature,
                               @RequestParam String timestamp,
                               @RequestParam String nonce,
                               @RequestParam String echostr) {
        MpAccount account = accountService.findByTenantAndAppId(tenantId, appId);
        if (account == null || isDisabled(account)) {
            return "fail";
        }
        return callbackService.verify(account, signature, timestamp, nonce, echostr);
    }

    /** 消息/事件推送：应答被动回复 XML 或 "success"（body 以字节读取，避免 text/xml 默认编码乱码） */
    @PostMapping(value = "/{tenantId}/{appId}", produces = MediaType.TEXT_XML_VALUE)
    public String pushRouted(@PathVariable Long tenantId,
                             @PathVariable String appId,
                             @RequestBody byte[] body,
                             @RequestParam String signature,
                             @RequestParam String timestamp,
                             @RequestParam String nonce) {
        MpAccount account = accountService.findByTenantAndAppId(tenantId, appId);
        if (account == null || isDisabled(account)) {
            return "success";
        }
        return callbackService.onPush(account, new String(body, StandardCharsets.UTF_8),
                signature, timestamp, nonce);
    }

    // ==================== 旧路径：主库兜底（过渡期 deprecated） ====================

    /** 接入验证（旧格式，无世界段）：主库定位账号 */
    @GetMapping(value = "/{appId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@PathVariable String appId,
                         @RequestParam String signature,
                         @RequestParam String timestamp,
                         @RequestParam String nonce,
                         @RequestParam String echostr) {
        MpAccount account = accountService.findByAppId(appId);
        if (account == null || isDisabled(account)) {
            return "fail";
        }
        return callbackService.verify(account, signature, timestamp, nonce, echostr);
    }

    /** 消息/事件推送（旧格式，无世界段）：主库定位账号 */
    @PostMapping(value = "/{appId}", produces = MediaType.TEXT_XML_VALUE)
    public String push(@PathVariable String appId,
                       @RequestBody byte[] body,
                       @RequestParam String signature,
                       @RequestParam String timestamp,
                       @RequestParam String nonce) {
        MpAccount account = accountService.findByAppId(appId);
        if (account == null || isDisabled(account)) {
            return "success";
        }
        return callbackService.onPush(account, new String(body, StandardCharsets.UTF_8),
                signature, timestamp, nonce);
    }

    private static boolean isDisabled(MpAccount account) {
        return !Integer.valueOf(MpAccount.STATUS_ENABLED).equals(account.getStatus());
    }
}
