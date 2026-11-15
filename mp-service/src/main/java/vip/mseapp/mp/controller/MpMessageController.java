package vip.mseapp.mp.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mp.entity.MpMessage;
import vip.mseapp.mp.service.MpMessageService;

/**
 * 消息/事件流水查询。
 */
@RestController
@RequestMapping("/mp/message")
public class MpMessageController {

    private final MpMessageService messageService;

    public MpMessageController(MpMessageService messageService) {
        this.messageService = messageService;
    }

    @SaCheckPermission(value = "mp:message:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MpMessage>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) Long accountId,
                                              @RequestParam(required = false) String direction,
                                              @RequestParam(required = false) String kind,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) String keyword) {
        return Result.ok(messageService.page(pageNum, pageSize, accountId, direction, kind, type, keyword));
    }

    @SaCheckPermission(value = "mp:message:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{id}")
    public Result<MpMessage> get(@PathVariable Long id) {
        return Result.ok(messageService.get(id));
    }
}
