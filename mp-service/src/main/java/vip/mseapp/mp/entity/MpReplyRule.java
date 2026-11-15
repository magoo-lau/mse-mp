package vip.mseapp.mp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

/**
 * 自动回复规则。
 *
 * <p>匹配顺序（先到先得）：subscribe → unsubscribe → click → msg_any →
 * keyword_exact → keyword_contains → default；同类型内 priority 大者先。
 * 关键词支持逗号/空格/竖线分隔多值（任一命中）。
 *
 * <p>replyContent 格式：text → {"content":"..."}；news → {"items":[{title,description,picUrl,url},...]}（最多 8 条）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mp_reply_rule")
public class MpReplyRule extends BaseEntity {

    /** 匹配：关注 */
    public static final String MATCH_SUBSCRIBE = "subscribe";
    /** 匹配：取关 */
    public static final String MATCH_UNSUBSCRIBE = "unsubscribe";
    /** 匹配：菜单点击（matchValue = 菜单 key 精确匹配） */
    public static final String MATCH_CLICK = "click";
    /** 匹配：任意消息 */
    public static final String MATCH_MSG_ANY = "msg_any";
    /** 匹配：关键词精确 */
    public static final String MATCH_KEYWORD_EXACT = "keyword_exact";
    /** 匹配：关键词包含 */
    public static final String MATCH_KEYWORD_CONTAINS = "keyword_contains";
    /** 兜底：以上全部未命中 */
    public static final String MATCH_DEFAULT = "default";

    /** 回复：文本 */
    public static final String REPLY_TEXT = "text";
    /** 回复：图文（最多 8 条） */
    public static final String REPLY_NEWS = "news";

    /** 公众号 */
    private Long accountId;

    /** 规则名（便于管理） */
    private String name;

    /** 匹配类型 */
    private String matchType;

    /** 匹配值：菜单 key / 关键词（多值分隔符 , 空格 |）；subscribe 等类型无值 */
    private String matchValue;

    /** 回复类型：text / news */
    private String replyType;

    /** 回复内容 JSON */
    private String replyContent;

    /** 优先级（同类型内大者先） */
    private Integer priority;

    /** 启用：1 是 0 否 */
    private Integer enabled;
}
