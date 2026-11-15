package vip.mseapp.mp.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.Result;
import vip.mseapp.mp.service.MpStatsService;

import java.util.List;

/**
 * 仪表盘统计。
 */
@RestController
@RequestMapping("/mp/stats")
public class MpStatsController {

    private final MpStatsService statsService;

    public MpStatsController(MpStatsService statsService) {
        this.statsService = statsService;
    }

    @SaCheckPermission(value = "mp:stats:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/summary")
    public Result<MpStatsService.Summary> summary() {
        return Result.ok(statsService.summary());
    }

    @SaCheckPermission(value = "mp:stats:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/trend")
    public Result<List<MpStatsService.TrendPoint>> trend(@RequestParam(defaultValue = "7") int days,
                                                         @RequestParam(required = false) Long accountId) {
        return Result.ok(statsService.trend(days, accountId));
    }
}
