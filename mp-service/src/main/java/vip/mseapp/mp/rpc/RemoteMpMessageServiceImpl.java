package vip.mseapp.mp.rpc;

import tools.jackson.databind.json.JsonMapper;
import vip.mseapp.core.annotation.RpcService;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.mp.api.RemoteMpMessageService;
import vip.mseapp.mp.api.dto.MpMessagePageQueryRequest;
import vip.mseapp.mp.api.dto.MpMessagePageResult;
import vip.mseapp.mp.entity.MpMessage;
import vip.mseapp.mp.service.MpMessageService;

/**
 * 公众号消息开放契约实现（只读：复用 MpMessageService 查询，机器语义 JSON 进出）。
 * 装配：由 MpServiceAutoConfiguration @Bean 注册（可声明同契约 Bean 整体替换）。
 */
@RpcService(RemoteMpMessageService.class)
public class RemoteMpMessageServiceImpl implements RemoteMpMessageService {

    private final MpMessageService messageService;
    private final JsonMapper objectMapper;

    public RemoteMpMessageServiceImpl(MpMessageService messageService, JsonMapper objectMapper) {
        this.messageService = messageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String pageMessages(String requestJson) {
        MpMessagePageQueryRequest request = parse(requestJson, MpMessagePageQueryRequest.class);
        long pageNum = request.getPageNum() <= 0 ? 1 : request.getPageNum();
        long pageSize = request.getPageSize() <= 0 ? 10 : Math.min(request.getPageSize(), 100);
        PageResult<MpMessage> page = messageService.page(pageNum, pageSize, request.getAccountId(),
                request.getDirection(), request.getKind(), request.getType(), request.getKeyword());
        MpMessagePageResult result = new MpMessagePageResult();
        result.setTotal(page.total());
        result.setRecords(page.records().stream()
                .map(message -> new MpMessagePageResult.MessageItem(message.getId(), message.getAccountId(),
                        message.getOpenId(), message.getDirection(), message.getKind(), message.getType(),
                        message.getPayload(), message.getReplyContent(), message.getCreateTime()))
                .toList());
        return toJson(result);
    }

    private MpMessagePageQueryRequest parse(String json, Class<MpMessagePageQueryRequest> type) {
        if (json == null || json.isBlank()) {
            return new MpMessagePageQueryRequest();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new ServiceException(400, "请求 JSON 解析失败: " + e.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ServiceException(500, "响应序列化失败: " + e.getMessage());
        }
    }
}
