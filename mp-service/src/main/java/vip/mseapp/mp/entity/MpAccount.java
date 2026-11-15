package vip.mseapp.mp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

/**
 * 公众号账号（多租户：每个租户可接入自己的公众号）。
 *
 * <p>AppSecret / CallbackToken / EncodingAESKey 经 AES-GCM 加密落库，接口不回显；
 * 编辑时留空表示不修改。加密模式与公众平台「服务器配置」一致：
 * plain 明文 / compatible 兼容 / safe 安全。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mp_account")
public class MpAccount extends BaseEntity {

    /** 类型：订阅号 */
    public static final String TYPE_SUBSCRIPTION = "subscription";
    /** 类型：服务号 */
    public static final String TYPE_SERVICE = "service";

    /** 加密模式：明文 */
    public static final String ENCRYPT_PLAIN = "plain";
    /** 加密模式：兼容（明密文共存） */
    public static final String ENCRYPT_COMPATIBLE = "compatible";
    /** 加密模式：安全（纯密文） */
    public static final String ENCRYPT_SAFE = "safe";

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 0;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 1;

    /** 公众号 AppID（全局唯一：回调按 AppID 路由，不允许跨租户重复） */
    private String appId;

    /** 公众号名称 */
    private String name;

    /** 类型：subscription 订阅号 / service 服务号 */
    private String type;

    /** 是否已认证：0 否 1 是（未认证号部分 API 能力受限，如菜单 48001、拉不到粉丝资料） */
    private Integer verified;

    /** AppSecret 密文 */
    private String appSecret;

    /** 服务器配置 Token 密文（验签用） */
    private String callbackToken;

    /** 消息加解密密钥 EncodingAESKey（43 位）密文 */
    private String aesKey;

    /** 加密模式：plain / compatible / safe */
    private String encryptMode;

    /** 状态：0 启用 1 停用（停用后回调直接应答 success，不处理） */
    private Integer status;

    private String remark;
}
