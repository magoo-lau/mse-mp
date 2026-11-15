package vip.mseapp.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.mp.entity.MpFan;
import vip.mseapp.mp.mapper.MpFanMapper;

/** 粉丝查询（只读：粉丝由回调事件自动维护）。 */
@Service
public class MpFanService {

    private final MpFanMapper fanMapper;

    public MpFanService(MpFanMapper fanMapper) {
        this.fanMapper = fanMapper;
    }

    public PageResult<MpFan> page(long pageNum, long pageSize, Long accountId,
                                  Integer subscribed, String keyword) {
        LambdaQueryWrapper<MpFan> wrapper = new LambdaQueryWrapper<MpFan>()
                .eq(accountId != null, MpFan::getAccountId, accountId)
                .eq(subscribed != null, MpFan::getSubscribed, subscribed)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MpFan::getOpenId, keyword)
                        .or().like(MpFan::getNickname, keyword)
                        .or().like(MpFan::getUnionId, keyword))
                .orderByDesc(MpFan::getSubscribeTime)
                .orderByDesc(MpFan::getId);
        Page<MpFan> page = fanMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }
}
