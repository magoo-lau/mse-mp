package vip.mseapp.mp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

/**
 * 消息/事件流水（微信服务器推送的原始记录，管理端只读）。
 *
 * <p>幂等：dedupKey = MD5(appid|openid|createTime|msgType|event|eventKey|msgId)，
 * 唯一索引挡微信重试（每条推送最多重试 3 次）；被动回复内容落库缓存，
 * 重试时直接复用，不重复执行规则。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mp_message")
public class MpMessage extends BaseEntity {

    /** 方向：收 */
    public static final String DIRECTION_IN = "in";
    /** 方向：发（预留：客服/群发） */
    public static final String DIRECTION_OUT = "out";

    /** 类别：事件 */
    public static final String KIND_EVENT = "event";
    /** 类别：消息 */
    public static final String KIND_MESSAGE = "message";

    /** 公众号 */
    private Long accountId;

    /** 粉丝 openid */
    private String openId;

    /** 方向：in / out */
    private String direction;

    /** 类别：event / message */
    private String kind;

    /** 类型：subscribe/unsubscribe/subscribe_scan/click/view/scan/location 或 text/image/voice/video/shortvideo/location/link */
    private String type;

    /** 微信消息 id（事件类推送为空） */
    private String msgId;

    /** 幂等锚点（MD5），唯一索引 */
    private String dedupKey;

    /** 归一化载荷 JSON（snake_case，含 Content/PicUrl/Recognition/EventKey 等） */
    private String payload;

    /** 原始 XML 报文 */
    private String rawXml;

    /** 被动回复内容缓存（明文 XML，重试时复用；无回复为空） */
    private String replyContent;

    /** 链路追踪 ID（appId.纳秒后 6 位） */
    private String traceId;
}
