# 验证要点（MP 回归清单）

完整用例目录在 ai-mse 仓 docs/test/21-mp-*.md（历史存档）；本仓维护最终态回归要点。

## 一、账号与回调核心链路

1. 多账号接入：新增/停用公众号；凭证按租户世界存储（凭证数据主场=租户世界）。
2. 回调：关注/取关/消息事件到达 `/mp/callback/{tenantId}/{appId}`——无会话凭证按 URL 租户段切库校验；签名/加解密失败拒绝。
3. Token 管理（MpTokenManager）：access_token 刷新与并发安全。

## 二、运营功能

1. 粉丝同步/备注/标签；消息（文本/图文/事件）入库与检索。
2. 自动回复规则命中（关键词/事件）；自定义菜单拉取与发布（MpMenu）。
3. 仪表盘统计口径正确（按租户隔离）。

## 三、租户与工程底线

1. 租户 A 的公众号与粉丝对租户 B 不可见；伪造 X-Tenant-Id 被覆盖；越权直调 403。
2. 独立库租户数据落专属库；base 脚本推送进租户库车队副本（mse_mp_schema_history）。
3. `mvn install -DskipTests` 通过；mp-ui `npm run build` 通过；镜像构建成功、k8s 探针通过。
4. AI：MpBizQueryTool/MpBizModuleProvider 在 mse-ai 目录可见，biz_query 路由可用（mse.ai.mode 开关）。
