package vip.mseapp.mp.api;

/**
 * 公众号粉丝开放契约（mp-service 实现，只读）。
 *
 * <p>机器语义约定见 {@code vip.mseapp.open.api.OpenApiDescriptor}：请求/返回均为 JSON 字符串，
 * 租户上下文由调用方注入、经 RPC 隐式透传。
 */
public interface RemoteMpFanService {

    /**
     * 分页查询粉丝（本地同步表，回调事件自动维护）。
     *
     * @param requestJson MpFanPageQueryRequest JSON：accountId、subscribed、keyword、pageNum、pageSize（均可选）
     * @return MpFanPageResult JSON：total、records
     */
    String pageFans(String requestJson);
}
