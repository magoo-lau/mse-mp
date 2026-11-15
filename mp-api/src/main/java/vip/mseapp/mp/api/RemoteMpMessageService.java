package vip.mseapp.mp.api;

/**
 * 公众号消息开放契约（mp-service 实现，只读：消息/事件流水）。
 */
public interface RemoteMpMessageService {

    /**
     * 分页查询消息/事件流水。
     *
     * @param requestJson MpMessagePageQueryRequest JSON：accountId、direction、kind、type、keyword、pageNum、pageSize（均可选）
     * @return MpMessagePageResult JSON：total、records
     */
    String pageMessages(String requestJson);
}
