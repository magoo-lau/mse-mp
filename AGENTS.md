# AGENTS.md —— mse-mp（微信公众号）协作约定

本文件面向所有在本仓库工作的 AI 助手与开发者。本仓库自 ai-mse 单仓（源 commit `cc5410a3`）拆分独立，仓根即工程根。

## 一、工程结构

```
mse-mp/
├── pom.xml          # parent = mse-starter-parent；properties: ai-api/open-api=1.0.0-SNAPSHOT
├── mp-api/          # 对外契约（RemoteMpMessageService 等，依赖仅基座 common-core）
├── mp-service/      # 业务积木（账号/粉丝/菜单/消息/回复规则/WxClient 对接/AI 工具）
├── mp-boot/         # 启动器（vip.mseapp.boot.mp；micro-starter + mp-service）
├── mp-ui/           # PC 子应用（--base=/sub-apps/mp/）
└── docker/          # nacos/config/mse-mp.yaml + mp-boot/Dockerfile + k8s（见 docker/README.md）
```

## 二、工程拼法（对齐基座，硬性要求）

1. parent = mse-starter-parent（前置：mse-framework 仓 `sh build.sh`）；依赖 `starter-web/mybatis/security/rpc` 四件 + `mp-api`；**跨仓契约**：`open-api` / `ai-api`（来自 mse-modules 仓，1.0.0-SNAPSHOT）——该仓先构建。
2. SPI 装配：`MpServiceAutoConfiguration`（`@TenantServiceModule("mse-mp")`）+ `MpControllerAutoConfiguration`（`mse.service.mp.controller.enabled` 开关）经 imports 登记；启动器纯 `@SpringBootApplication`。
3. db 版本化脚本：`mp-service/src/main/resources/db/mysql/V*_mp__*.sql`（幂等，已应用不改）；权限号段（mp=6xxx）唯一登记在 mse-agent-plugin 仓 `references/alloc-tables.md`。

## 三、回调与凭证（无上下文凭证进门范式）

公众号回调 URL 自带世界段：`/mp/callback/{tenantId}/{appId}`——解析租户号切库后在所属世界校验凭证；凭证数据主场=租户世界。AI 接入：`MpBizQueryTool`（AbstractBizQueryTool 模块化查询）+ `MpBizModuleProvider` 自注册。

## 四、租户与事务红线

写操作 `@Transactional(rollbackFor = Exception.class)`；跨服务/跨库入口叠加 `@GlobalTransactional`（组件扫描注册）；事务开启前必须已处于目标世界。

## 五、构建、镜像与验证

- `sh build.sh`；`sh build-ui.sh`（mp-ui）。
- 镜像 `docker build -f mp-boot/Dockerfile -t mse/mp-boot:1.0.0 .`；本地运行见 `docker/README.md`。
- 验证底线：`mvn install -DskipTests` 通过；mp-ui `npm run build` 通过；业务回归见 docs/verification.md。

## 六、提交纪律

本仓独立提交；改 open-api/ai-api 契约需先在 mse-modules 仓发版再适配本仓。
