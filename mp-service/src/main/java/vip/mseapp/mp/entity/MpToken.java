package vip.mseapp.mp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

import java.time.LocalDateTime;

/**
 * access_token 落库记录（每号一份；运行期内存缓存为主，此表供状态展示与失败原因排查）。
 *
 * <p>获取失败（如 AppSecret 错误 40125、能力未开放 48001）时 errCode/errMsg 落库，管理端可见。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mp_token")
public class MpToken extends BaseEntity {

    /** 公众号（每号一份） */
    private Long accountId;

    /** token 密文 */
    private String token;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 最近一次失败的微信错误码（成功时清空） */
    private String errCode;

    /** 最近一次失败的错误信息 */
    private String errMsg;
}
