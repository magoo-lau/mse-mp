package vip.mseapp.mp.api.dto;

import java.io.Serializable;

/**
 * 消息分页查询请求（全字段可选）。
 */
public class MpMessagePageQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 公众号账号编号 */
    private Long accountId;

    /** 方向（in=粉丝上行 / out=平台下行） */
    private String direction;

    /** 类别（message=消息 / event=事件） */
    private String kind;

    /** 微信消息/事件类型（如 text/image/subscribe） */
    private String type;

    /** 关键词（openId/payload/replyContent 模糊） */
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

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
