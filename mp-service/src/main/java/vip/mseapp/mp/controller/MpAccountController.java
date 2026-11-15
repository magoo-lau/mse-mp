package vip.mseapp.mp.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mp.config.MpCrypt;
import vip.mseapp.mp.entity.MpAccount;
import vip.mseapp.mp.entity.MpToken;
import vip.mseapp.mp.service.MpAccountService;
import vip.mseapp.mp.service.MpTokenManager;

import java.time.LocalDateTime;

/**
 * 公众号账号管理（租户端）。
 */
@Validated
@RestController
@RequestMapping("/mp/account")
public class MpAccountController {

    private final MpAccountService accountService;
    private final MpTokenManager tokenManager;
    private final MpCrypt crypt;

    public MpAccountController(MpAccountService accountService, MpTokenManager tokenManager, MpCrypt crypt) {
        this.accountService = accountService;
        this.tokenManager = tokenManager;
        this.crypt = crypt;
    }

    @SaCheckPermission(value = "mp:account:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MpAccount>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer status) {
        return Result.ok(accountService.page(pageNum, pageSize, keyword, status));
    }

    @SaCheckPermission(value = "mp:account:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{id}")
    public Result<MpAccount> get(@PathVariable Long id) {
        return Result.ok(accountService.get(id));
    }

    /** 接入配置：回调 Token/EncodingAESKey 明文回显（填入公众平台用），AppSecret 不回显 */
    @SaCheckPermission(value = "mp:account:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{id}/credentials")
    public Result<CredentialView> credentials(@PathVariable Long id) {
        MpAccount account = accountService.getRaw(id);
        return Result.ok(new CredentialView(account.getAppId(),
                crypt.decrypt(account.getCallbackToken()),
                crypt.decrypt(account.getAesKey()),
                account.getEncryptMode()));
    }

    /** access_token 状态（不回显 token 本体） */
    @SaCheckPermission(value = "mp:account:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{id}/token-status")
    public Result<TokenView> tokenStatus(@PathVariable Long id) {
        MpToken token = tokenManager.statusOf(accountService.getRaw(id));
        if (token == null) {
            // 未获取过 token：提示文案属 errMsg 语义（errCode 留空），此前两字段错位
            return Result.ok(new TokenView(null, null, null, "尚未获取"));
        }
        return Result.ok(new TokenView(token.getExpiresAt(),
                token.getExpiresAt() == null ? null : token.getExpiresAt().isAfter(LocalDateTime.now()),
                token.getErrCode(), token.getErrMsg()));
    }

    @SaCheckPermission(value = "mp:account:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> create(@RequestBody @Validated CreateRequest request) {
        return Result.ok(accountService.create(request.appId(), request.name(), request.type(),
                request.verified(), request.encryptMode(), request.appSecret(),
                request.callbackToken(), request.aesKey(), request.remark()));
    }

    @SaCheckPermission(value = "mp:account:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PutMapping
    public Result<Void> update(@RequestBody @Validated UpdateRequest request) {
        accountService.update(request.id(), request.name(), request.type(), request.verified(),
                request.encryptMode(), request.appSecret(), request.callbackToken(),
                request.aesKey(), request.remark());
        return Result.ok();
    }

    @SaCheckPermission(value = "mp:account:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam boolean enable) {
        accountService.changeStatus(id, enable);
        return Result.ok();
    }

    /** 连通性测试：data.ok=false 时 message 带微信错误详情 */
    @SaCheckPermission(value = "mp:account:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/test")
    public Result<TestResult> test(@PathVariable Long id) {
        String error = accountService.test(id);
        return Result.ok(error == null ? new TestResult(true, null) : new TestResult(false, error));
    }

    @SaCheckPermission(value = "mp:account:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return Result.ok();
    }

    public record CreateRequest(
            @NotBlank(message = "AppID 不能为空") String appId,
            @NotBlank(message = "名称不能为空") String name,
            String type,
            boolean verified,
            String encryptMode,
            @NotBlank(message = "AppSecret 不能为空") String appSecret,
            @NotBlank(message = "Token 不能为空") String callbackToken,
            @NotBlank(message = "EncodingAESKey 不能为空") String aesKey,
            String remark) {
    }

    public record UpdateRequest(
            @NotNull(message = "编号不能为空") Long id,
            @NotBlank(message = "名称不能为空") String name,
            String type,
            Boolean verified,
            String encryptMode,
            /** 留空 = 保持不变 */
            String appSecret,
            String callbackToken,
            String aesKey,
            String remark) {
    }

    public record CredentialView(String appId, String callbackToken, String aesKey, String encryptMode) {
    }

    public record TokenView(LocalDateTime expiresAt, Boolean valid, String errCode, String errMsg) {
    }

    public record TestResult(boolean ok, String message) {
    }
}
