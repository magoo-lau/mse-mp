package vip.mseapp.mp.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.Result;
import vip.mseapp.mp.entity.MpAccount;
import vip.mseapp.mp.service.MpAccountService;
import vip.mseapp.mp.service.MpMenuService;

import java.util.Map;

/**
 * 自定义菜单管理（每号一份草稿，与线上菜单独立）。
 */
@Validated
@RestController
@RequestMapping("/mp/menu")
public class MpMenuController {

    private final MpAccountService accountService;
    private final MpMenuService menuService;

    public MpMenuController(MpAccountService accountService, MpMenuService menuService) {
        this.accountService = accountService;
        this.menuService = menuService;
    }

    @SaCheckPermission(value = "mp:menu:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{accountId}")
    public Result<MpMenuService.DraftView> draft(@PathVariable Long accountId) {
        return Result.ok(menuService.draft(account(accountId)));
    }

    @SaCheckPermission(value = "mp:menu:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PutMapping("/{accountId}")
    public Result<Void> saveDraft(@PathVariable Long accountId, @RequestBody @Validated DraftRequest request) {
        menuService.saveDraft(account(accountId), request.menuJson());
        return Result.ok();
    }

    @SaCheckPermission(value = "mp:menu:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{accountId}/sync")
    public Result<Void> sync(@PathVariable Long accountId) {
        menuService.sync(account(accountId));
        return Result.ok();
    }

    @SaCheckPermission(value = "mp:menu:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{accountId}/remote")
    public Result<Map<String, Object>> remote(@PathVariable Long accountId) {
        return Result.ok(menuService.remote(account(accountId)));
    }

    @SaCheckPermission(value = "mp:menu:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{accountId}/remote")
    public Result<Void> deleteRemote(@PathVariable Long accountId) {
        menuService.deleteRemote(account(accountId));
        return Result.ok();
    }

    private MpAccount account(Long accountId) {
        return accountService.getRaw(accountId);
    }

    public record DraftRequest(@NotBlank(message = "菜单 JSON 不能为空") String menuJson) {
    }
}
