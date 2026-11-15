-- mse-target: base
-- =====================================================================
-- 微信公众号基座接入脚本（独立工程 mse-modules/mse-mp，MySQL）
-- 目标库：基座库（sys_* 全局表所在库，即 mse_platform；分库部署时对基座库执行）
-- 内容：子应用注册（sys_micro_app）+ 页面菜单（sys_menu type=3）+ 权限点（sys_permission）
-- 配套：mp_init.sql（对业务库执行：业务表 + 示例数据）；单库演示两脚本都对 mse_platform 执行
-- 幂等：全段 NOT EXISTS 判重，可重复执行
-- 执行：自动——随业务 jar 推送 mse-system 应用（mse_<biz>_schema_history 分册账本），无需手工执行
-- 版本化：V{yyyyMMdd}_{NN}_<biz>__<描述>.sql（biz 段路由分册账本 mse_<biz>_schema_history，同 biz 内字典序即时间序）；
--           已应用脚本不再修改（改动报 checksum 冲突），变更一律新增版本文件
-- 号段：micro_app id=4、菜单 430-436、权限点 6xxx（登记见 zcode-plugin/plugins/mse-dev/references/alloc-tables.md）
-- 租户端可见性：页面菜单需在「套餐管理」勾选（页面级裁剪），普通用户另需角色授权
-- =====================================================================

-- ---------------- 子应用注册（按 name 判重） ----------------
INSERT INTO sys_micro_app (id, name, title, dev_entry, prod_entry, side, sort, status, remark, create_time, update_time)
SELECT 4, 'mp', '微信公众号', '//localhost:3500', '/sub-apps/mp/', 1, 4, 0, '独立工程 mse-modules/mse-mp（mp-ui + mp-boot）', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_micro_app WHERE name = 'mp');

-- ---------------- 页面菜单（type=3；分组目录 path 即路由前缀 /sub/mp；按 id 判重） ----------------
INSERT INTO sys_menu (id, parent_id, name, type, menu_scope, path, micro_name, component, icon, sort, visible, status, create_time, update_time)
SELECT t.id, t.parent_id, t.name, t.type, t.menu_scope, t.path, t.micro_name, NULL, t.icon, t.sort, 0, 0, NOW(), NOW()
FROM (
    SELECT 430 AS id, 0 AS parent_id, '微信公众号' AS name, 1 AS type, 2 AS menu_scope, '/sub/mp' AS path, NULL AS micro_name, 'Box' AS icon, 23 AS sort
    UNION ALL SELECT 431, 430, '仪表盘',     3, 2, '/dashboard', 'mp', NULL, 1
    UNION ALL SELECT 432, 430, '公众号管理', 3, 2, '/account',   'mp', NULL, 2
    UNION ALL SELECT 433, 430, '菜单配置',   3, 2, '/menu',      'mp', NULL, 3
    UNION ALL SELECT 434, 430, '粉丝管理',   3, 2, '/fan',       'mp', NULL, 4
    UNION ALL SELECT 435, 430, '消息事件',   3, 2, '/message',   'mp', NULL, 5
    UNION ALL SELECT 436, 430, '回复规则',   3, 2, '/rule',      'mp', NULL, 6
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = t.id);

-- ---------------- 权限点（挂靠页面菜单：参与授权界面分组与套餐裁剪，替代早期“通用权限”形态） ----------------
INSERT INTO sys_permission (id, menu_id, module, name, code, sort, status, create_time, update_time)
SELECT t.id, t.menu_id, t.module, t.name, t.code, t.sort, 0, NOW(), NOW()
FROM (
    SELECT 6001 AS id, 432 AS menu_id, 'mp' AS module, '公众号查询' AS name, 'mp:account:list' AS code, 1 AS sort
    UNION ALL SELECT 6002, 432, 'mp', '公众号编辑', 'mp:account:save',   2
    UNION ALL SELECT 6003, 432, 'mp', '公众号删除', 'mp:account:delete', 3
    UNION ALL SELECT 6004, 433, 'mp', '菜单查询',   'mp:menu:list',      4
    UNION ALL SELECT 6005, 433, 'mp', '菜单编辑',   'mp:menu:save',      5
    UNION ALL SELECT 6006, 434, 'mp', '粉丝查询',   'mp:fan:list',       6
    UNION ALL SELECT 6007, 435, 'mp', '消息查询',   'mp:message:list',   7
    UNION ALL SELECT 6008, 436, 'mp', '规则查询',   'mp:rule:list',      8
    UNION ALL SELECT 6009, 436, 'mp', '规则编辑',   'mp:rule:save',      9
    UNION ALL SELECT 6010, 436, 'mp', '规则删除',   'mp:rule:delete',    10
    UNION ALL SELECT 6011, 431, 'mp', '统计查询',   'mp:stats:list',     11
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.id = t.id);

-- ---------------- 升级兼容：早期安装的权限点未挂菜单（menu_id=NULL，按通用权限登录即授予），此处幂等补挂 ----------------
UPDATE sys_permission SET menu_id = 432, update_time = NOW() WHERE id = 6001 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 432, update_time = NOW() WHERE id = 6002 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 432, update_time = NOW() WHERE id = 6003 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 433, update_time = NOW() WHERE id = 6004 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 433, update_time = NOW() WHERE id = 6005 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 434, update_time = NOW() WHERE id = 6006 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 435, update_time = NOW() WHERE id = 6007 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 436, update_time = NOW() WHERE id = 6008 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 436, update_time = NOW() WHERE id = 6009 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 436, update_time = NOW() WHERE id = 6010 AND menu_id IS NULL;
UPDATE sys_permission SET menu_id = 431, update_time = NOW() WHERE id = 6011 AND menu_id IS NULL;
