package vip.mseapp.mp.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 粉丝分页结果（对外稳定子集）。
 */
public class MpFanPageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;

    private List<FanItem> records;

    public record FanItem(Long id, Long accountId, String openId, Integer subscribed,
                          LocalDateTime subscribeTime, String nickname, String headImgUrl)
            implements Serializable {
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<FanItem> getRecords() {
        return records;
    }

    public void setRecords(List<FanItem> records) {
        this.records = records;
    }
}
