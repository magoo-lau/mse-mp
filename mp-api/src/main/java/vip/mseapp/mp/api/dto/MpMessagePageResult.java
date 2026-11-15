package vip.mseapp.mp.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息分页结果（对外稳定子集）。
 */
public class MpMessagePageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;

    private List<MessageItem> records;

    public record MessageItem(Long id, Long accountId, String openId, String direction, String kind,
                              String type, String payload, String replyContent, LocalDateTime createTime)
            implements Serializable {
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<MessageItem> getRecords() {
        return records;
    }

    public void setRecords(List<MessageItem> records) {
        this.records = records;
    }
}
