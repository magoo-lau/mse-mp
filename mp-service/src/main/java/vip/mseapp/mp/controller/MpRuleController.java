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
import vip.mseapp.mp.entity.MpReplyRule;
import vip.mseapp.mp.service.MpRuleService;

/**
 * 自动回复规则管理。
 */
@Validated
@RestController
@RequestMapping("/mp/rule")
public class MpRuleController {

    private final MpRuleService ruleService;

    public MpRuleController(MpRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @SaCheckPermission(value = "mp:rule:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MpReplyRule>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) Long accountId,
                                                @RequestParam(required = false) String keyword) {
        return Result.ok(ruleService.page(pageNum, pageSize, accountId, keyword));
    }

    @SaCheckPermission(value = "mp:rule:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> create(@RequestBody @Validated RuleRequest request) {
        return Result.ok(ruleService.create(request.accountId(), request.name(), request.matchType(),
                request.matchValue(), request.replyType(), request.replyContent(),
                request.priority(), request.enabled()));
    }

    @SaCheckPermission(value = "mp:rule:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PutMapping
    public Result<Void> update(@RequestBody @Validated RuleUpdateRequest request) {
        ruleService.update(request.id(), request.name(), request.matchType(), request.matchValue(),
                request.replyType(), request.replyContent(), request.priority(), request.enabled());
        return Result.ok();
    }

    @SaCheckPermission(value = "mp:rule:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/status")
    public Result<Void> changeEnabled(@PathVariable Long id, @RequestParam boolean enable) {
        ruleService.changeEnabled(id, enable);
        return Result.ok();
    }

    @SaCheckPermission(value = "mp:rule:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }

    public record RuleRequest(
            @NotNull(message = "公众号不能为空") Long accountId,
            @NotBlank(message = "规则名不能为空") String name,
            @NotBlank(message = "匹配类型不能为空") String matchType,
            String matchValue,
            @NotBlank(message = "回复类型不能为空") String replyType,
            @NotBlank(message = "回复内容不能为空") String replyContent,
            Integer priority,
            Integer enabled) {
    }

    public record RuleUpdateRequest(
            @NotNull(message = "规则编号不能为空") Long id,
            @NotBlank(message = "规则名不能为空") String name,
            @NotBlank(message = "匹配类型不能为空") String matchType,
            String matchValue,
            @NotBlank(message = "回复类型不能为空") String replyType,
            @NotBlank(message = "回复内容不能为空") String replyContent,
            Integer priority,
            Integer enabled) {
    }
}
