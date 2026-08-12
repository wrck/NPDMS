-- =============================================================================
-- V16: PMS 工程实施/割接/巡检维保三域按钮权限补齐
-- 背景：V11/V13/V15 仅插入 type=2 可见菜单（query 权限），
--      前端 v-hasPermi 指令要求 create/update/delete 等按钮权限（type=3），
--      否则新增/编辑/删除按钮被隐藏，UI 无法完成 CRUD 闭环。
--      本迁移补齐 65 个按钮权限并分配给超级管理员角色（role_id=1）。
-- ID 范围：19029~19093
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 工程实施域按钮权限（parent: 19009~19018）==========
(19029, '工勘创建', 'pms:eng-site-survey:create', 3, 1, 19009, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19030, '工勘更新', 'pms:eng-site-survey:update', 3, 2, 19009, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19031, '工勘删除', 'pms:eng-site-survey:delete', 3, 3, 19009, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19032, '需求创建', 'pms:eng-requirement:create', 3, 1, 19010, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19033, '需求更新', 'pms:eng-requirement:update', 3, 2, 19010, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19034, '需求删除', 'pms:eng-requirement:delete', 3, 3, 19010, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19035, '方案创建', 'pms:eng-solution:create', 3, 1, 19011, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19036, '方案更新', 'pms:eng-solution:update', 3, 2, 19011, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19037, '方案删除', 'pms:eng-solution:delete', 3, 3, 19011, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19038, '方案审核', 'pms:eng-solution:audit', 3, 4, 19011, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19039, '资源创建', 'pms:eng-resource:create', 3, 1, 19012, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19040, '资源更新', 'pms:eng-resource:update', 3, 2, 19012, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19041, '资源删除', 'pms:eng-resource:delete', 3, 3, 19012, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19042, '到货创建', 'pms:eng-arrival:create', 3, 1, 19013, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19043, '到货更新', 'pms:eng-arrival:update', 3, 2, 19013, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19044, '到货删除', 'pms:eng-arrival:delete', 3, 3, 19013, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19045, '安装创建', 'pms:eng-installation:create', 3, 1, 19014, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19046, '安装更新', 'pms:eng-installation:update', 3, 2, 19014, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19047, '安装删除', 'pms:eng-installation:delete', 3, 3, 19014, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19048, '配置创建', 'pms:eng-configuration:create', 3, 1, 19015, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19049, '配置更新', 'pms:eng-configuration:update', 3, 2, 19015, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19050, '配置删除', 'pms:eng-configuration:delete', 3, 3, 19015, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19051, '联调创建', 'pms:eng-joint-test:create', 3, 1, 19016, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19052, '联调更新', 'pms:eng-joint-test:update', 3, 2, 19016, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19053, '联调删除', 'pms:eng-joint-test:delete', 3, 3, 19016, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19054, '实施问题创建', 'pms:eng-issue:create', 3, 1, 19017, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19055, '实施问题更新', 'pms:eng-issue:update', 3, 2, 19017, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19056, '实施问题删除', 'pms:eng-issue:delete', 3, 3, 19017, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19057, '实施问题验证', 'pms:eng-issue:verify', 3, 4, 19017, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19058, '交付件创建', 'pms:eng-deliverable:create', 3, 1, 19018, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19059, '交付件更新', 'pms:eng-deliverable:update', 3, 2, 19018, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19060, '交付件删除', 'pms:eng-deliverable:delete', 3, 3, 19018, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19061, '交付件归档', 'pms:eng-deliverable:archive', 3, 4, 19018, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 割接域按钮权限（parent: 19019~19023）==========
(19062, '割接任务创建', 'pms:cut-task:create', 3, 1, 19019, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19063, '割接任务更新', 'pms:cut-task:update', 3, 2, 19019, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19064, '割接任务删除', 'pms:cut-task:delete', 3, 3, 19019, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19065, '割接任务审核', 'pms:cut-task:audit', 3, 4, 19019, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19066, '割接风险创建', 'pms:cut-risk:create', 3, 1, 19020, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19067, '割接风险更新', 'pms:cut-risk:update', 3, 2, 19020, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19068, '割接风险删除', 'pms:cut-risk:delete', 3, 3, 19020, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19069, '割接方案创建', 'pms:cut-plan:create', 3, 1, 19021, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19070, '割接方案更新', 'pms:cut-plan:update', 3, 2, 19021, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19071, '割接方案删除', 'pms:cut-plan:delete', 3, 3, 19021, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19072, '割接方案审核', 'pms:cut-plan:audit', 3, 4, 19021, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19073, '割接执行创建', 'pms:cut-execution:create', 3, 1, 19022, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19074, '割接执行更新', 'pms:cut-execution:update', 3, 2, 19022, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19075, '割接执行删除', 'pms:cut-execution:delete', 3, 3, 19022, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19076, '割接观察创建', 'pms:cut-observation:create', 3, 1, 19023, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19077, '割接观察更新', 'pms:cut-observation:update', 3, 2, 19023, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19078, '割接观察删除', 'pms:cut-observation:delete', 3, 3, 19023, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 巡检维保域按钮权限（parent: 19024~19028）==========
(19079, '巡检任务创建', 'pms:srv-task:create', 3, 1, 19024, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19080, '巡检任务更新', 'pms:srv-task:update', 3, 2, 19024, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19081, '巡检任务删除', 'pms:srv-task:delete', 3, 3, 19024, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19082, '巡检规则创建', 'pms:srv-rule:create', 3, 1, 19025, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19083, '巡检规则更新', 'pms:srv-rule:update', 3, 2, 19025, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19084, '巡检规则删除', 'pms:srv-rule:delete', 3, 3, 19025, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19085, '巡检报告创建', 'pms:srv-report:create', 3, 1, 19026, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19086, '巡检报告更新', 'pms:srv-report:update', 3, 2, 19026, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19087, '巡检报告删除', 'pms:srv-report:delete', 3, 3, 19026, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19088, '巡检问题创建', 'pms:srv-issue:create', 3, 1, 19027, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19089, '巡检问题更新', 'pms:srv-issue:update', 3, 2, 19027, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19090, '巡检问题删除', 'pms:srv-issue:delete', 3, 3, 19027, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19091, '维保状态创建', 'pms:srv-maintenance:create', 3, 1, 19028, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19092, '维保状态更新', 'pms:srv-maintenance:update', 3, 2, 19028, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19093, '维保状态删除', 'pms:srv-maintenance:delete', 3, 3, 19028, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `parent_id` = VALUES(`parent_id`),
 `type` = VALUES(`type`), `sort` = VALUES(`sort`), `update_time` = NOW(), `deleted` = b'0';

-- =============================================================================
-- 将全部 PMS 菜单（可见菜单 + 按钮权限）分配给超级管理员角色（role_id=1）
-- 使用 INSERT IGNORE 避免重复插入已存在的 role_menu 记录
-- =============================================================================
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, m.id, 'admin', NOW(), 'admin', NOW(), b'0'
FROM `system_menu` m
WHERE m.deleted = b'0'
  AND m.id BETWEEN 18000 AND 19093
  AND m.permission LIKE 'pms:%'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = b'0'
  );
