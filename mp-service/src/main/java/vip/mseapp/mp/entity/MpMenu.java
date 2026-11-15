package vip.mseapp.mp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.mseapp.mybatis.data.BaseEntity;

import java.time.LocalDateTime;

/**
 * 自定义菜单草稿（每个公众号一份）。
 *
 * <p>menuJson 与公众平台 menu/create 接口同构：{"button":[{name,type,key/url,sub_button:[...]}]}。
 * 本地草稿与微信线上菜单独立：保存草稿不等于发布，同步后记录 syncedTime。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mp_menu")
public class MpMenu extends BaseEntity {

    /** 草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 已同步至微信 */
    public static final int STATUS_SYNCED = 1;

    /** 公众号（每号一份） */
    private Long accountId;

    /** 菜单 JSON（与微信 menu/create 同构） */
    private String menuJson;

    /** 最近同步时间 */
    private LocalDateTime syncedTime;

    /** 状态：0 草稿有改动 1 已同步 */
    private Integer status;
}
