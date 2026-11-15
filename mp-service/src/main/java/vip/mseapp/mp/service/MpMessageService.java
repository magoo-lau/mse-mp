package vip.mseapp.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.mp.entity.MpMessage;
import vip.mseapp.mp.mapper.MpMessageMapper;

/** 消息/事件流水查询（只读）。 */
@Service
public class MpMessageService {

    private final MpMessageMapper messageMapper;

    public MpMessageService(MpMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    public PageResult<MpMessage> page(long pageNum, long pageSize, Long accountId, String direction,
                                      String kind, String type, String keyword) {
        LambdaQueryWrapper<MpMessage> wrapper = new LambdaQueryWrapper<MpMessage>()
                .eq(accountId != null, MpMessage::getAccountId, accountId)
                .eq(direction != null && !direction.isBlank(), MpMessage::getDirection, direction)
                .eq(kind != null && !kind.isBlank(), MpMessage::getKind, kind)
                .eq(type != null && !type.isBlank(), MpMessage::getType, type)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MpMessage::getOpenId, keyword)
                        .or().like(MpMessage::getPayload, keyword)
                        .or().like(MpMessage::getReplyContent, keyword))
                .orderByDesc(MpMessage::getId);
        Page<MpMessage> page = messageMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    public MpMessage get(Long id) {
        MpMessage message = messageMapper.selectById(id);
        if (message == null) {
            throw new ServiceException(404, "消息不存在");
        }
        return message;
    }
}
