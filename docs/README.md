# mse-mp —— 微信公众号管理平台（产品规划）

> **状态：一期已落地**（多账号接入 / 回调核心链路 / 粉丝 / 消息 / 自动回复 / 自定义菜单 / 仪表盘 / 开放契约均已可用；功能以工程 [README](../README.md) 与实际代码为准）。
> 本文档集为产品留档，实际落库以工程内版本化脚本为准（`mp-service/src/main/resources/db/mysql/`）；02 中的 id 即种子脚本已落地的真实值。

## 一、产品定位

面向**多租户 SaaS** 的微信公众号管理平台：一个实例承接任意多个公众号（订阅号/服务号），管理「接入 → 回调 → 粉丝 → 消息 → 自动回复 → 菜单运营」的公众号运营闭环，功能对标 go-wechat（Go 版多公众号接入管理平台），以 Java 21 + Spring Boot 重新实现。

在本仓库中的形态：**独立业务工程仓**（自 ai-mse 单仓拆分，源 commit cc5410a3；独立 Maven 工程），wujie 微前端子应用 + 微服务启动器，积木拼法与 mse-demo / mse-mes 一致：

- `mp-api`：跨服务 RPC 契约（粉丝/消息只读查询，经 mse-open 自注册对外开放）
- `mp-service`：业务积木（SPI 自装配，Controller / Service / Mapper 随 jar 就绪）
- `mp-boot`：微服务启动器（micro-starter + mp-service，纯 `@SpringBootApplication`）
- `mp-ui`：子应用前端（Vue3 + Element Plus 紧凑型，wujie 嵌入基座）

**多租户**：所有 `mp_*` 业务表带 `tenant_id` 行级隔离（一期建在共享主库）；`mp_account.app_id` **全局唯一**（回调按 `/mp/callback/{appId}` 路由的要求），接入时跨租户查重；微信服务器没有租户上下文，回调链路是唯一的跨租户入口——按 AppID 定位账号后即固定到其所属租户。

## 二、文档导读

| 分册 | 内容 | 读者 |
| --- | --- | --- |
| [01-功能规划.md](01-功能规划.md) | 功能模块与功能点清单、核心业务闭环、已知边界与后续演进 | 产品 / 全员 |
| [02-权限规划.md](02-权限规划.md) | 子应用注册、页面菜单（sys_menu，真实 id）、权限点（sys_permission，真实 id）与编码规范 | 后端 / 前端 |
| [03-角色规划.md](03-角色规划.md) | 建议角色清单、角色 × 页面授权矩阵、数据权限设计、种子落库情况 | 后端 / 实施 |
| [04-操作说明书/](04-操作说明书/README.md) | **分角色操作说明书**（总览 + 3 个岗位手册：公众号管理员 / 内容运营 / 客服专员），含通用操作、状态字典与常见问题 | 最终用户 / 培训交付 |
| [05-配置手册-零基础版.md](05-配置手册-零基础版.md) | **面向门外汉的部署配置指南**：前置依赖、docker 一键部署、crypt-key 生产必改、微信后台服务器配置对接、排查表与上线清单 | 实施 / 现场 IT |

阅读顺序：01 → 02 → 03。02 的表格即 `V20261001_01_mp__mp_base.sql` 已落库内容的转录。**04 为面向最终用户的交付文档、05 为面向实施的零基础部署文档**，均以系统实际界面与配置为准。

## 三、资源分配（实际占用，已登记 alloc-tables.md）

| 资源 | 实际值 | 说明 |
| --- | --- | --- |
| 后端端口 `server.port` | **8091** | 微服务形态 mp-boot；单体聚合不占独立端口 |
| 前端 dev 端口 | **3500** | mp-ui vite dev server |
| 子应用注册 `sys_micro_app.id` | **4** | name=`mp`，side=1（租户端），标题「微信公众号」，sort=4 |
| 页面菜单 `sys_menu.id` | **430-436** | 430=type=1 分组目录；431-436 六个页面（仪表盘/公众号管理/菜单配置/粉丝管理/消息事件/回复规则） |
| 权限点 `sys_permission.id` | **6001-6011** | 6xxx 段归 mp，已用满 6001-6011，续增走 61xx（登记后使用） |
| 权限码前缀 | `mp:` | 格式 `mp:<模块>:<动作>`，与 oa/mes/fs 等工程同风格 |
| 网关路由前缀 | `/mp` | `Path=/mp/** → lb://mse-mp`；白名单 `/mp/callback/**`（微信回调以签名鉴权，无登录态） |
| 微服务应用名 | `mse-mp` | spring.application.name；`@TenantServiceModule("mse-mp")` |
| 版本化脚本 | `V20261001_01_mp__mp_base.sql`（base 通道）、`V20261001_02_mp__mp_init.sql`（master 通道） | 均仅 MySQL；达梦/金仓暂未随 jar 提供 |

## 四、一期范围与后续演进

| 范围 | 内容 | 状态 |
| --- | --- | --- |
| **一期（已落地）** | 多账号/多租户接入、凭据 AES-256-GCM 加密、连通性测试、access_token 管理、微信回调核心链路（验签/解密/幂等/粉丝流转/自动回复）、粉丝管理（只读）、消息事件流水、自动回复规则、自定义菜单、仪表盘、开放契约（粉丝/消息只读查询） | ✅ 已落地 |
| **后续（按需扩展）** | 客服消息、模板消息、素材上传、图文群发、粉丝标签管理、带参二维码生成、JSSDK 签名、网页授权 OAuth、微信支付 | 规划（README「已知边界」，代码未实现，勿当已有能力交付） |

后续演进的其他事项：独立库租户形态（粉丝/消息路由租户库，按 mse-demo `@GlobalTransactional` 模式处理）、「主动发消息」能力（`mp_message.direction=out` 字段已预留）。

## 五、工程结构蓝图

```
mse-mp/
├── pom.xml                  # 父 pom：import mse-dependencies BOM
├── README.md                # 工程主文档（功能权威描述）
├── docs/                    # 本文档集（产品系统规划）
├── mp-api/                  # RPC 契约（粉丝/消息只读查询 + Serializable DTO；经 mse-open 自注册开放）
├── mp-service/              # 业务积木（SPI 自装配；wx/ 微信协议薄封装、config/、entity/、service/、controller/）
│   └── src/main/resources/db/mysql/   # 版本化脚本随 jar：mp_base.sql（基座注册，mse-target: base）
│                                      #   + mp_init.sql（业务表，mse-target: master）
├── mp-boot/                 # 启动器：micro-starter + mp-service（vip.mseapp.boot.mp，端口 8091）
├── mp-ui/                   # 子应用（mse-ui-sub 模板；dev 3500；base=/sub-apps/mp/）
└── docker/nacos/config/     # mse-mp.yaml 服务专属配置留档（Data ID: mse-mp.yaml）
```

部署形态：微服务为默认形态（Nacos + Dubbo，网关 `/mp` 前缀）；单体聚合仅需引入 `mono-starter` + `mp-service` jar（`mse.rpc.mode=local` 本地直调）。微信协议层（`wx/` 包）为无第三方 SDK 的薄封装：验签 / AES-CBC 加解密 / XML 解析与回复构造 / 最小 API 客户端。

## 六、平台协作约定（落地红线）

1. **不改框架区**：一切定制经 SPI 自动装配 + `@ConditionalOnMissingBean` 替换语义实现（RPC 契约实现可整体替换）。
2. **SQL 免手工执行**：业务表脚本（`-- mse-target: master`）随启动自动迁移（失败阻断启动）；基座注册（`-- mse-target: base`）由 starter-bridge 自动推送（幂等版本化脚本，已应用脚本不再修改，变更一律新增版本文件）。
3. **权限点一律挂靠本工程 type=3 页面菜单**（6001-6011 已挂 431-436），参与角色授权分组与租户套餐裁剪；前端按钮用 `v-permission` 按权限码控制。
4. **回调匿名接口以签名鉴权**：`/mp/callback/**` 在网关白名单放行（无用户会话），下游按微信签名验签；管理端接口仍由 `@SaCheckPermission`（租户登录态）把守。
5. **凭据安全**：AppSecret / Token / EncodingAESKey / access_token 一律 AES-256-GCM 加密落库、接口不回显、编辑留空 = 不修改；生产必须更换 `mse.mp.crypt-key` 默认值。
6. **资源已登记**：第三节实际值均登记在 `mse-agent-plugin 仓 plugins/mse-dev/references/alloc-tables.md`，续占号段先查该表。

## 拆仓配套文档（新增）

| 文档 | 内容 |
| --- | --- |
| [build-and-run.md](build-and-run.md) | 构建（build.sh/build-ui.sh）、本地运行（framework 仓基础设施）、镜像与 K8s |
| [verification.md](verification.md) | 回归验证要点 |
| [../AGENTS.md](../AGENTS.md) | 协作约定；[../docker/README.md](../docker/README.md) 镜像资产与服务配置 |
