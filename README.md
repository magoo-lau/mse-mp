# mse-mp —— 微信公众号管理平台

基于 `ai-mse` 基座积木式组装的独立业务工程，位于业务工程目录 `mse-modules/` 下；功能对标 `go-wechat`（Go 版多公众号接入管理平台），以 Java 21 + Spring Boot 4.0.8 + Element Plus 重新实现。

## 功能（一期）

### 公众号接入
- **多账号/多租户**：每个租户可接入多个公众号（订阅号/服务号、是否认证、明文/兼容/安全三种消息加密方式）；AppID 全局唯一，回调按 `/mp/callback/{appId}` 路由，单实例承接任意多个号；
- **凭据加密**：AppSecret / Token / EncodingAESKey 以 AES-256-GCM 加密落库，接口不回显，编辑留空 = 不修改（密钥 `mse.mp.crypt-key`，生产必须修改）；
- **连通性测试**：实时获取 access_token 验证凭据；Token 状态列展示有效期与失败原因（40125 密钥错误 / 48001 未认证无权限等）；
- **access_token 管理**：内存缓存 + `mp_token` 表双写，过期前 5 分钟预刷新，同号并发刷新串行化（等价 singleflight），成功/失败均落库供排查。

### 微信回调（核心链路）
- **接入验证**（GET）：sha1 验签后原样返回 echostr；
- **消息/事件推送**（POST）：验签 → 按加密模式 AES-CBC 解密（PKCS7 块 32，校验 receiveid）→ 幂等去重（`dedup_key` 唯一索引，MD5 锚点挡微信重试）→ 粉丝状态流转 → 自动回复规则匹配 → 落库 → 5 秒窗口内应答；
- **类型归一化**：subscribe / subscribe_scan（带参扫码）/ unsubscribe / click / view / scan / location 事件与 text / image / voice（含语音识别）/ video / shortvideo / location / link 消息全覆盖，载荷 JSON（snake_case）+ 解密后明文报文双份落库；
- **不可恢复错误一律应答 success**，避免微信约 5 秒间隔重试放大流量；回复内容落库缓存，重试直接复用；
- 兼容/安全模式下被动回复加密包装；回复 FromUserName 使用入站 ToUserName（gh_ 原始 ID）。

### 粉丝管理（只读）
关注/取关/扫码事件自动维护：关注状态、关注/取关时间与场景、昵称头像、性别地区、UnionID、最近活跃时间；未认证号拉不到资料时标记「资料不全」。

### 自定义菜单
- 每号一份草稿（与微信 `menu/create` 同构 JSON），**可视化 / JSON 双模式编辑**（一级 ≤3、二级 ≤5，支持排序）；
- 保存草稿 ≠ 发布：同步到微信、拉取线上菜单（归一化，兼容 JSON/XML 两种返回）、删除线上菜单；未认证号 48001 附友好提示。

### 自动回复规则
- 匹配类型：关注 / 取关 / 菜单点击（Key 精确）/ 任意消息 / 关键词精确 / 关键词包含（多关键词 `,` 空格 `|` 分隔）/ 兜底；
- 匹配顺序固定（subscribe → unsubscribe → click → msg_any → keyword_exact → keyword_contains → default），同类型内 priority 大者先；
- 回复类型：文本 / 图文（≤8 条）。

### 仪表盘
公众号/粉丝/今日消息/启用规则统计卡片 + 近 N 天（≤30）入站事件/消息双线趋势（Java 侧分桶，三方言数据库通用）+ 最近消息流水。

## 工程结构

```
mse-mp/
├── pom.xml        # 父 pom：import mse-dependencies BOM
├── mp-service/    # 公众号业务积木（vip.mseapp.mp，纯 jar 无 main）
│   ├── wx/        #   微信协议薄封装：验签 / AES-CBC 加解密 / XML 解析与回复构造 / 最小 API 客户端（无第三方 SDK）
│   ├── config/    #   mse.mp.* 配置 + AES-GCM 字段加密器
│   ├── entity/    #   mp_account / mp_fan / mp_message / mp_menu / mp_reply_rule / mp_token
│   ├── service/   #   回调处理 / token 管理 / 账号 / 菜单 / 规则 / 粉丝 / 消息 / 统计
│   └── controller/#   /mp/callback（匿名）+ /mp/account /mp/menu /mp/fan /mp/message /mp/rule /mp/stats
│   └── resources/db/mysql/  # 版本化迁移脚本（jar 内）：V20261001_02_mp__mp_init.sql（业务表，mse-target: master）
│                           #   + V20261001_01_mp__mp_base.sql（基座注册+页面菜单+权限点，mse-target: base）
├── mp-boot/       # 微服务启动器：micro-starter + mp-service，端口 8091（application.yaml（spring.config.import）接 Nacos）
├── mp-ui/         # 子应用（复制 mse-ui-sub 模板，dev 3500，构建 /sub-apps/mp/）
└── docker/nacos/config/mse-mp.yaml   # Nacos 服务专属配置留档（Data ID: mse-mp.yaml）
```

## 组装方式（积木拼法）

- 基座先 `mvn install -DskipTests`，再在本工程 `mvn install -DskipTests`；
- mp-boot 依赖 micro-starter + mp-service（starter-bridge 契约消费桥接随 micro-starter 内置：鉴权数据源经契约消费 mse-system），
  业务积木经 Spring Boot SPI 自动装配，全程不修改基座代码，也不打包基座 HTTP 接口；
- 前端在基座注册（name=mp，side=tenant）：随 jar 内版本化脚本（`db/mysql/V20261001_01_mp__mp_base.sql`，mse-target: base，幂等）经 starter-bridge 自动推送基座库登记（mse-system 按账本应用，平台端「子应用管理」可调整），基座内置兜底表同参数；登录态/权限经 wujie props 下发；
- 微信回调接口为匿名接口（以签名而非登录态鉴权）：跨租户查号用 `TenantContext.runIgnore`，
  消息处理在 `TenantContext.runAs(账号租户)` 内完成，租户行级隔离由基座插件保证。

## 多租户与数据形态

- 一期所有 `mp_*` 业务表建在**共享主库**（带 tenant_id，经基座租户插件自动隔离）；
- `mp_account.app_id` 全局唯一（回调路由要求），接入时跨租户查重；
- 微信服务器没有租户上下文，回调链路是唯一的跨租户入口：按 AppID 定位账号后即固定到其所属租户。

## 快速开始

```bash
# 1. 初始化（基座 docker 中间件已起的前提下；微服务模式需 Nacos，见根 README 第 3 步）
#    SQL 免手工执行：业务表脚本（mse-target: master）随服务启动自动应用（mse.tenant.schema.auto-migrate=true，失败阻断启动）；
#    基座接入脚本（mse-target: base）由 starter-bridge 就绪即推 mse-system 按账本应用（周期重推自愈），重复推送无害。

# 2. 启动后端（微服务形态：application.yaml（spring.config.import）接 Nacos 配置中心 + 注册）
cd mse-modules/mse-mp && mvn install -DskipTests
CONFIG_DIR="$PWD/docker/nacos/config" sh ../../mse-framework/docker/nacos/init-configs.sh   # 首次：mse-mp.yaml 导入 Nacos
java -jar mp-boot/target/mp-boot-1.0.0-SNAPSHOT.jar    # 端口 8091，注册进 Nacos（网关 lb://mse-mp）；启动即自动建表 + 推送基座注册
# 生产必须修改加密密钥：Nacos mse-mp.yaml 中 MSE_MP_CRYPT_KEY=<随机串>
```

> 人工兜底：管理端「租户数据源」保留主库迁移/结构同步按钮，自动通道异常时可手动触发。

# 3. 启动子应用
cd mse-modules/mse-mp/mp-ui && npm install && npm run dev   # 端口 3500

# 4. 打开基座（3000）登录租户端 → 侧边栏「微信公众号」
#    嵌入联调时基座 mse-ui 用 MSE_API_TARGET=http://localhost:48080 npm run dev（网关）
```

### 公众号接入步骤

1. 「公众号管理 → 接入公众号」填写 AppID / AppSecret / Token / EncodingAESKey（与公众平台「设置与开发-基本配置-服务器配置」一致）与消息加密方式；
2. 在「接入配置」弹窗复制回调 URL：`https://<你的域名>/api/mp/callback/<AppID>`，连同 Token / EncodingAESKey 填入公众平台并启用；
3. 「测试」按钮验证凭据（真实调用 access_token 接口）；
4. 用微信向公众号发消息，「消息事件」页应出现流水，命中规则时展示被动回复。

> 开发环境无公网回调时，可用内网穿透（如 ngrok/nps）把 `https://xxx/api` 映射到本机 8091。

## 全链路验证脚本要点（人工回归）

1. **接入**：新增公众号 → 「测试」通过 → Token 状态列显示「有效」；「接入配置」回显 Token/AESKey；
2. **回调验证**：公众平台「服务器配置-提交验证」通过（GET echostr）；
3. **消息链路**：发文本消息 → 消息事件页出现记录（payload.content + 明文报文）→ 粉丝页出现该粉丝；
4. **自动回复**：建关键词规则（启用）→ 再发命中/不命中消息各一条 → 微信端收到/未收到回复；消息详情页「已回复」列与回复缓存一致；
5. **关注链路**：关注/取关 → 粉丝状态流转正确，带参二维码关注记录 subscribe_scene=ADD_SCENE_QR_CODE 且类型为 subscribe_scan；
6. **菜单**：可视化编辑保存草稿 → 同步到微信 → 手机端菜单生效；拉取线上与草稿一致；未认证号同步报 48001；
7. **幂等**：同一条消息在消息事件页只有一条记录（微信重试 3 次被 dedup_key 挡住）。

## 已知边界（一期）

- 客服消息、模板消息、素材上传、群发、粉丝标签管理、带参二维码生成、JSSDK 签名、网页授权 OAuth、微信支付未纳入（go-wechat 同样未实现，后续按需扩展）；
- 群发/客服等「主动发消息」能力依赖 `mp_message.direction=out`（已预留字段）；
- 独立库租户形态（粉丝/消息路由租户库）属后续演进，按 mse-demo 的 `@GlobalTransactional` 模式处理；
- 达梦/金仓暂未随 jar 提供等价建表脚本（一期以 MySQL 为主验证）。


## 开放契约（业务能力自注册）

本工程把粉丝/消息只读查询开放给第三方，经 mse-open 开放平台统一对外：

- `mp-api`：`RemoteMpFanService.pageFans`、`RemoteMpMessageService.pageMessages`（JSON 进出，租户经上下文隐式透传）；
- `mp-service`：`rpc` 包契约实现（装配可整体替换）+ `MpOpenApiContributor`（自注册清单）；
- 应用就绪后自动推送 mse-open 目录（`open:mp:fans-page` / `open:mp:messages-page`），对外路径 `/open/api/mp/**`。

「主动发消息」属微信侧重能力（见已知边界），开放通道暂不提供。
## 回调世界化（一切皆租户）

- 微信回调新路径 `/mp/callback/{tenantId}/{appId}`（URL 自带世界段，同 mse-im 回调范式）：凭租户段直路由账号所属租户库校验 token/AESKey；账号行随租户世界驻留（管理侧读写同随世界路由），管理端「接入信息」弹窗展示新格式 URL。
- 旧路径 `/mp/callback/{appId}` 主库兜底（deprecated）：存量公众号需在**微信后台**改配新回调 URL，并按下方模板迁移账号行后收敛；AppID 世界内唯一（世界段已消歧，跨世界接入同号公众号各自回调互不串线）。
- **存量迁移**（独立库租户，单实例跨库、主库执行，`<N>`=租户编号、`<tenant_db>`=专属库；粉丝/消息等随账号级联驻留同库，按需同款搬移）：

```sql
INSERT INTO <tenant_db>.mp_account SELECT * FROM mp_account WHERE tenant_id = <N> AND tenant_id > 0
  ON DUPLICATE KEY UPDATE app_secret = VALUES(app_secret), status = VALUES(status);
INSERT INTO <tenant_db>.mp_reply_rule SELECT * FROM mp_reply_rule WHERE tenant_id = <N> AND tenant_id > 0
  ON DUPLICATE KEY UPDATE reply_type = VALUES(reply_type);
```
