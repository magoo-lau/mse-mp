package vip.mseapp.mp.api.dto;

import java.io.Serializable;

/**
 * 粉丝分页查询请求（全字段可选）。
 */
public class MpFanPageQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 公众号账号编号 */
    private Long accountId;

    /** 是否仍关注（1 是 0 否） */
    private Integer subscribed;

    /** 关键词（openId/昵称/unionId 模糊） */
    private String keyword;

    /** 页码（默认 1） */
    private long pageNum = 1;

    /** 页大小（默认 10，上限 100） */
    private long pageSize = 10;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Integer getSubscribed() {
        return subscribed;
    }

    public void setSubscribed(Integer subscribed) {
        this.subscribed = subscribed;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public long getPageNum() {
        return pageNum;
    }

    public void setPageNum(long pageNum) {
        this.pageNum = pageNum;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }
}
