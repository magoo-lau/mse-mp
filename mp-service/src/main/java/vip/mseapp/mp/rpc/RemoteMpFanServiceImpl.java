package vip.mseapp.mp.rpc;

import tools.jackson.databind.json.JsonMapper;
import vip.mseapp.core.annotation.RpcService;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.mp.api.RemoteMpFanService;
import vip.mseapp.mp.api.dto.MpFanPageQueryRequest;
import vip.mseapp.mp.api.dto.MpFanPageResult;
import vip.mseapp.mp.entity.MpFan;
import vip.mseapp.mp.service.MpFanService;

/**
 * 公众号粉丝开放契约实现（只读：复用 MpFanService 查询，机器语义 JSON 进出）。
 * 装配：由 MpServiceAutoConfiguration @Bean 注册（可声明同契约 Bean 整体替换）。
 */
@RpcService(RemoteMpFanService.class)
public class RemoteMpFanServiceImpl implements RemoteMpFanService {

    private final MpFanService fanService;
    private final JsonMapper objectMapper;

    public RemoteMpFanServiceImpl(MpFanService fanService, JsonMapper objectMapper) {
        this.fanService = fanService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String pageFans(String requestJson) {
        MpFanPageQueryRequest request = parse(requestJson, MpFanPageQueryRequest.class);
        long pageNum = request.getPageNum() <= 0 ? 1 : request.getPageNum();
        long pageSize = request.getPageSize() <= 0 ? 10 : Math.min(request.getPageSize(), 100);
        PageResult<MpFan> page = fanService.page(pageNum, pageSize,
                request.getAccountId(), request.getSubscribed(), request.getKeyword());
        MpFanPageResult result = new MpFanPageResult();
        result.setTotal(page.total());
        result.setRecords(page.records().stream()
                .map(fan -> new MpFanPageResult.FanItem(fan.getId(), fan.getAccountId(), fan.getOpenId(),
                        fan.getSubscribed(), fan.getSubscribeTime(), fan.getNickname(), fan.getHeadImgUrl()))
                .toList());
        return toJson(result);
    }

    private MpFanPageQueryRequest parse(String json, Class<MpFanPageQueryRequest> type) {
        if (json == null || json.isBlank()) {
            return new MpFanPageQueryRequest();
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
