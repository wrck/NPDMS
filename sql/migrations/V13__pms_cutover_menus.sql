-- =============================================================================
-- V13: PMS 割接域可见菜单（T-V1-CUT-A / T-V1-CUT-B UI 闭环）
-- 父菜单 18000 项目交付（V4 已存在）；本迁移补齐 5 个割接可见菜单。
-- 使用 ID 19019~19023 避免与 V11 已占用的 19009~19018 冲突。
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- 割接菜单（18000 下，sort 61~65）
(19019, '割接任务', 'pms:cut-task:query', 2, 61, 18000, 'cut-task', 'ep:switch',
 'pms/cutover/cut-task/index', 'PmsCutTask', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19020, '割接风险', 'pms:cut-risk:query', 2, 62, 18000, 'cut-risk', 'ep:warning',
 'pms/cutover/cut-risk/index', 'PmsCutRisk', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19021, '割接方案', 'pms:cut-plan:query', 2, 63, 18000, 'cut-plan', 'ep:document',
 'pms/cutover/cut-plan/index', 'PmsCutPlan', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19022, '割接执行', 'pms:cut-execution:query', 2, 64, 18000, 'cut-execution', 'ep:video-play',
 'pms/cutover/cut-execution/index', 'PmsCutExecution', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19023, '割接观察', 'pms:cut-observation:query', 2, 65, 18000, 'cut-observation', 'ep:view',
 'pms/cutover/cut-observation/index', 'PmsCutObservervation', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `path` = VALUES(`path`),
 `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
 `parent_id` = VALUES(`parent_id`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
 `icon` = VALUES(`icon`), `update_time` = NOW(), `deleted` = b'0';
