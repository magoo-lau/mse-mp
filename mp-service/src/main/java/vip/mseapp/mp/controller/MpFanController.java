package vip.mseapp.mp.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mp.entity.MpFan;
import vip.mseapp.mp.service.MpFanService;

/**
 * 粉丝管理（只读：粉丝由回调事件自动维护）。
 */
@RestController
@RequestMapping("/mp/fan")
public class MpFanController {

    private final MpFanService fanService;

    public MpFanController(MpFanService fanService) {
        this.fanService = fanService;
    }

    @SaCheckPermission(value = "mp:fan:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MpFan>> page(@RequestParam(defaultValue = "1") long pageNum,
                                          @RequestParam(defaultValue = "10") long pageSize,
                                          @RequestParam(required = false) Long accountId,
                                          @RequestParam(required = false) Integer subscribed,
                                          @RequestParam(required = false) String keyword) {
        return Result.ok(fanService.page(pageNum, pageSize, accountId, subscribed, keyword));
    }
}
