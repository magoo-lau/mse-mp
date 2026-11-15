package vip.mseapp.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import vip.mseapp.mp.entity.MpAccount;
import vip.mseapp.mp.entity.MpFan;
import vip.mseapp.mp.entity.MpMessage;
import vip.mseapp.mp.entity.MpReplyRule;
import vip.mseapp.mp.mapper.MpAccountMapper;
import vip.mseapp.mp.mapper.MpFanMapper;
import vip.mseapp.mp.mapper.MpMessageMapper;
import vip.mseapp.mp.mapper.MpReplyRuleMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计。
 *
 * <p>趋势按天分桶在 Java 侧完成（不写方言相关日期函数，MySQL/达梦/金仓通用）。
 */
@Service
public class MpStatsService {

    private final MpAccountMapper accountMapper;
    private final MpFanMapper fanMapper;
    private final MpMessageMapper messageMapper;
    private final MpReplyRuleMapper ruleMapper;

    public MpStatsService(MpAccountMapper accountMapper, MpFanMapper fanMapper,
                          MpMessageMapper messageMapper, MpReplyRuleMapper ruleMapper) {
        this.accountMapper = accountMapper;
        this.fanMapper = fanMapper;
        this.messageMapper = messageMapper;
        this.ruleMapper = ruleMapper;
    }

    public Summary summary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return new Summary(
                accountMapper.selectCount(null),
                accountMapper.selectCount(new LambdaQueryWrapper<MpAccount>()
                        .eq(MpAccount::getStatus, MpAccount.STATUS_ENABLED)),
                fanMapper.selectCount(new LambdaQueryWrapper<MpFan>().eq(MpFan::getSubscribed, 1)),
                messageMapper.selectCount(new LambdaQueryWrapper<MpMessage>()
                        .eq(MpMessage::getDirection, MpMessage.DIRECTION_IN)
                        .ge(MpMessage::getCreateTime, todayStart)),
                ruleMapper.selectCount(new LambdaQueryWrapper<MpReplyRule>().eq(MpReplyRule::getEnabled, 1)));
    }

    /** 近 N 天入站事件/消息双线趋势（默认 7 天，最大 30 天） */
    public List<TrendPoint> trend(int days, Long accountId) {
        int safeDays = Math.min(Math.max(days, 1), 30);
        LocalDate start = LocalDate.now().minusDays(safeDays - 1L);
        List<MpMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<MpMessage>()
                .select(MpMessage::getCreateTime, MpMessage::getKind)
                .eq(MpMessage::getDirection, MpMessage.DIRECTION_IN)
                .eq(accountId != null, MpMessage::getAccountId, accountId)
                .ge(MpMessage::getCreateTime, start.atStartOfDay()));
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < safeDays; i++) {
            buckets.put(start.plusDays(i), new long[2]);
        }
        for (MpMessage row : rows) {
            long[] counts = buckets.get(row.getCreateTime().toLocalDate());
            if (counts == null) {
                continue;
            }
            if (MpMessage.KIND_MESSAGE.equals(row.getKind())) {
                counts[1]++;
            } else {
                counts[0]++;
            }
        }
        List<TrendPoint> points = new ArrayList<>(buckets.size());
        buckets.forEach((date, counts) -> points.add(
                new TrendPoint(date.toString(), counts[0], counts[1])));
        return points;
    }

    /** 概览 */
    public record Summary(long accountTotal, long accountEnabled, long fanSubscribed,
                          long messageToday, long ruleEnabled) {
    }

    /** 趋势点（date=yyyy-MM-dd） */
    public record TrendPoint(String date, long eventCount, long messageCount) {
    }
}
