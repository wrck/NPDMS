-- =============================================================================
-- V15: PMS 巡检维保域可见菜单（T-V1-SRV-A / T-V1-SRV-B UI 闭环）
-- 父菜单 18000 项目交付（V4 已存在）；本迁移补齐 5 个巡检维保可见菜单。
-- 使用 ID 19024~19028 避免与 V11(19009~19018)、V13(19019~19023) 冲突。
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- 巡检维保菜单（18000 下，sort 71~75）
(19024, '巡检任务', 'pms:srv-task:query', 2, 71, 18000, 'srv-task', 'ep:search',
 'pms/service/srv-task/index', 'PmsSrvTask', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19025, '巡检规则', 'pms:srv-rule:query', 2, 72, 18000, 'srv-rule', 'ep:list',
 'pms/service/srv-rule/index', 'PmsSrvRule', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19026, '巡检报告', 'pms:srv-report:query', 2, 73, 18000, 'srv-report', 'ep:document',
 'pms/service/srv-report/index', 'PmsSrvReport', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19027, '巡检问题', 'pms:srv-issue:query', 2, 74, 18000, 'srv-issue', 'ep:warning',
 'pms/service/srv-issue/index', 'PmsSrvIssue', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19028, '维保状态', 'pms:srv-maintenance:query', 2, 75, 18000, 'srv-maintenance', 'ep:shield',
 'pms/service/srv-maintenance/index', 'PmsSrvMaintenance', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `path` = VALUES(`path`),
 `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
 `parent_id` = VALUES(`parent_id`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
 `icon` = VALUES(`icon`), `update_time` = NOW(), `deleted` = b'0';
