package vip.mseapp.mp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

import java.time.LocalDateTime;

/**
 * 粉丝（由回调事件自动维护，管理端只读）。
 *
 * <p>关注/取关/扫码事件驱动状态流转；关注时异步补全资料（未认证号拉不到资料时
 * profileIncomplete=1 标记）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mp_fan")
public class MpFan extends BaseEntity {

    /** 公众号 */
    private Long accountId;

    /** 粉丝 openid（同一公众号下唯一） */
    private String openId;

    /** 关注状态：1 关注中 0 已取关 */
    private Integer subscribed;

    private LocalDateTime subscribeTime;

    private LocalDateTime unsubscribeTime;

    /** 关注场景（带参扫码：ADD_SCENE_QR_CODE 等） */
    private String subscribeScene;

    private String nickname;

    private String headImgUrl;

    /** 性别：1 男 2 女 0 未知 */
    private Integer sex;

    private String province;

    private String city;

    /** 已绑定开放平台时才有 */
    private String unionId;

    /** 资料不全：1 表示拉取用户信息失败（未认证号常见），可重试补全 */
    private Integer profileIncomplete;

    private LocalDateTime lastActiveTime;
}
