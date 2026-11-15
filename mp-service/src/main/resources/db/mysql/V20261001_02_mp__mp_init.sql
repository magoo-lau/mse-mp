-- mse-target: master
-- =====================================================================
-- 微信公众号管理平台初始化脚本（独立工程 mse-modules/mse-mp，MySQL）
-- 目标库：业务库（业务表所在库；master 默认库 mse_platform，首启自动建库；分库部署对业务库执行）
-- 内容：mp_* 业务表
-- 配套：mp_base.sql（对基座库执行：子应用注册 + 页面菜单 + 权限点）
--
-- 前置：无（基座库随基座应用启动自动初始化；本工程 mp_base.sql 随 jar 自动推送注册）
-- 执行：自动——随服务启动应用主库（mse.tenant.schema.auto-migrate 默认开启）；租户独立库经「租户数据源初始化/懒初始化」克隆+迁移自动就绪
-- 版本化：V{yyyyMMdd}_{NN}_<biz>__<描述>.sql（biz 段路由分册账本 mse_<biz>_schema_history，同 biz 内字典序即时间序）；
--           已应用脚本不再修改（改动报 checksum 冲突），变更一律新增版本文件
--
-- 多租户：所有 mp_* 表带 tenant_id，经基座租户插件自动隔离；
--         mp_account.app_id 全局唯一（回调按 AppID 路由，不允许跨租户重复）
-- =====================================================================

-- ---------------- 公众号账号 ----------------
CREATE TABLE IF NOT EXISTS mp_account
(
    id             BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    app_id         VARCHAR(64)  NOT NULL COMMENT '公众号 AppID（全局唯一：回调按此路由）',
    name           VARCHAR(100) NOT NULL COMMENT '公众号名称',
    type           VARCHAR(20)  NOT NULL DEFAULT 'subscription' COMMENT 'subscription 订阅号 / service 服务号',
    verified       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已认证：0 否 1 是',
    app_secret     VARCHAR(255) NULL COMMENT 'AppSecret 密文（AES-GCM）',
    callback_token VARCHAR(255) NULL COMMENT '服务器配置 Token 密文',
    aes_key        VARCHAR(255) NULL COMMENT 'EncodingAESKey 密文',
    encrypt_mode   VARCHAR(20)  NOT NULL DEFAULT 'safe' COMMENT 'plain/compatible/safe',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 启用 1 停用',
    remark         VARCHAR(255) NULL COMMENT '备注',
    create_by      BIGINT       NULL COMMENT '创建者',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      BIGINT       NULL COMMENT '更新者',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_app_id (app_id, deleted),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT ='公众号账号';

-- ---------------- 粉丝 ----------------
CREATE TABLE IF NOT EXISTS mp_fan
(
    id                 BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id          BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    account_id         BIGINT       NOT NULL COMMENT '公众号账号ID',
    open_id            VARCHAR(64)  NOT NULL COMMENT '粉丝 OpenID',
    subscribed         TINYINT      NOT NULL DEFAULT 1 COMMENT '1 关注中 0 已取关',
    subscribe_time     DATETIME     NULL COMMENT '关注时间',
    unsubscribe_time   DATETIME     NULL COMMENT '取关时间',
    subscribe_scene    VARCHAR(40)  NULL COMMENT '关注场景（ADD_SCENE_QR_CODE 等）',
    nickname           VARCHAR(100) NULL COMMENT '粉丝昵称',
    head_img_url       VARCHAR(500) NULL COMMENT '头像地址',
    sex                TINYINT      NOT NULL DEFAULT 0 COMMENT '1 男 2 女 0 未知',
    province           VARCHAR(50)  NULL COMMENT '省份',
    city               VARCHAR(50)  NULL COMMENT '城市',
    union_id           VARCHAR(64)  NULL COMMENT '开放平台 UnionID',
    profile_incomplete TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = 资料拉取失败（未认证号常见）',
    last_active_time   DATETIME     NULL COMMENT '最近互动时间',
    remark             VARCHAR(255) NULL COMMENT '备注',
    create_by          BIGINT       NULL COMMENT '创建者',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by          BIGINT       NULL COMMENT '更新者',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_account_openid (account_id, open_id),
    KEY idx_tenant_sub (tenant_id, subscribed)
) ENGINE = InnoDB COMMENT ='粉丝（回调事件自动维护，管理端只读）';

-- ---------------- 消息/事件流水 ----------------
CREATE TABLE IF NOT EXISTS mp_message
(
    id            BIGINT      NOT NULL COMMENT '主键ID',
    tenant_id     BIGINT      NOT NULL DEFAULT 0 COMMENT '租户编号',
    account_id    BIGINT      NOT NULL COMMENT '公众号账号ID',
    open_id       VARCHAR(64) NOT NULL COMMENT '粉丝 OpenID',
    direction     VARCHAR(10) NOT NULL DEFAULT 'in' COMMENT 'in 收 / out 发（预留）',
    kind          VARCHAR(10) NOT NULL COMMENT 'event / message',
    type          VARCHAR(30) NOT NULL COMMENT 'subscribe/click/text/image/…（归一化小写）',
    msg_id        VARCHAR(64) NULL COMMENT '微信消息 id（事件类为空）',
    dedup_key     VARCHAR(64) NOT NULL COMMENT '幂等锚点 MD5（挡微信重试）',
    payload       LONGTEXT    NULL COMMENT '归一化载荷 JSON（snake_case）',
    raw_xml       LONGTEXT    NULL COMMENT '解密后明文报文（超长截断）',
    reply_content LONGTEXT    NULL COMMENT '被动回复缓存（明文 XML，重试复用）',
    trace_id      VARCHAR(64) NULL COMMENT '链路追踪ID',
    create_by     BIGINT      NULL COMMENT '创建者',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT      NULL COMMENT '更新者',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_dedup (dedup_key),
    KEY idx_tenant_account (tenant_id, account_id, type),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB COMMENT ='消息/事件流水';

-- ---------------- 自定义菜单草稿 ----------------
CREATE TABLE IF NOT EXISTS mp_menu
(
    id          BIGINT      NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '租户编号',
    account_id  BIGINT      NOT NULL COMMENT '公众号账号ID',
    menu_json   LONGTEXT    NULL COMMENT '与微信 menu/create 同构 JSON',
    synced_time DATETIME    NULL COMMENT '最近同步时间',
    status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0 草稿有改动 1 已同步',
    create_by   BIGINT      NULL COMMENT '创建者',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT      NULL COMMENT '更新者',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_account (account_id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT ='自定义菜单草稿（每号一份）';

-- ---------------- 自动回复规则 ----------------
CREATE TABLE IF NOT EXISTS mp_reply_rule
(
    id            BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    account_id    BIGINT       NOT NULL COMMENT '公众号账号ID',
    name          VARCHAR(100) NULL COMMENT '规则名',
    match_type    VARCHAR(30)  NOT NULL COMMENT 'subscribe/unsubscribe/click/msg_any/keyword_exact/keyword_contains/default',
    match_value   VARCHAR(255) NULL COMMENT '菜单 Key / 关键词（, 空格 | 分隔多值）',
    reply_type    VARCHAR(10)  NOT NULL DEFAULT 'text' COMMENT 'text / news',
    reply_content LONGTEXT     NULL COMMENT '{"content":…} 或 {"items":[…≤8]}',
    priority      INT          NOT NULL DEFAULT 0 COMMENT '同类型内大者先',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用（0 停用 1 启用）',
    create_by     BIGINT       NULL COMMENT '创建者',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     BIGINT       NULL COMMENT '更新者',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    KEY idx_tenant_account (tenant_id, account_id, enabled)
) ENGINE = InnoDB COMMENT ='自动回复规则';

-- ---------------- access_token 状态 ----------------
CREATE TABLE IF NOT EXISTS mp_token
(
    id          BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户编号',
    account_id  BIGINT       NOT NULL COMMENT '公众号账号ID',
    token       VARCHAR(500) NULL COMMENT 'token 密文（运行期内存缓存为主）',
    expires_at  DATETIME     NULL COMMENT '过期时间',
    err_code    VARCHAR(20)  NULL COMMENT '最近失败错误码（成功时清空）',
    err_msg     VARCHAR(500) NULL COMMENT '最近失败错误信息（成功时清空）',
    create_by   BIGINT       NULL COMMENT '创建者',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新者',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 未删除 1 已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY ux_account (account_id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB COMMENT ='access_token 落库状态（失败原因供管理端排查）';

-- =====================================================================
-- 权限点已迁移至 mp_base.sql（对基座库执行；sys_permission 为基座库全局表）
-- =====================================================================
