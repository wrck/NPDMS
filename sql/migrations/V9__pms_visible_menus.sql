-- =============================================================================
-- V9: PMS 可见菜单补齐（T-V1-PROJ / T-V1-AST UI 闭环）
-- 背景：V4/V7/V8/V6 仅插入 type=3 按钮权限或单个 type=2 菜单（客户/设备），
--      其余 PMS 业务模块在前端缺少 type=2 可见菜单，导致 UI 404。
--      本迁移补齐客户联系人、项目主数据、项目树、项目团队、任务WBS、
--      阶段模板、项目阶段、项目风险、项目全景、设备配置日志共 10 个可见菜单。
-- =============================================================================

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- 项目交付父菜单 18000 已存在（V4）；资产管理父菜单 19000 已存在（V6）
-- 项目交付子菜单（18000 下）
(18010, '客户联系人', 'pms:customer-contact:query', 2, 11, 18000, 'customer-contact', 'ep:user',
 'pms/project/customer-contact/index', 'PmsCustomerContact', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18011, '项目主数据', 'pms:project:query', 2, 12, 18000, 'project', 'ep:folder-opened',
 'pms/project/project/index', 'PmsProject', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18012, '项目树', 'pms:project-tree:query', 2, 13, 18000, 'project-tree', 'ep:share',
 'pms/project/project-tree/index', 'PmsProjectTree', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18013, '项目团队', 'pms:project-team:query', 2, 14, 18000, 'project-team', 'ep:user-filled',
 'pms/project/project-team/index', 'PmsProjectTeam', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18014, '任务WBS', 'pms:project-task:query', 2, 15, 18000, 'project-task', 'ep:document-copy',
 'pms/project/project-task/index', 'PmsProjectTask', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18015, '阶段模板', 'pms:phase-template:query', 2, 16, 18000, 'phase-template', 'ep:files',
 'pms/project/project-phase-template/index', 'PmsProjectPhaseTemplate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18016, '项目阶段', 'pms:project-phase:query', 2, 17, 18000, 'project-phase', 'ep:flag',
 'pms/project/project-phase/index', 'PmsProjectPhase', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18017, '项目风险', 'pms:project-risk:query', 2, 18, 18000, 'project-risk', 'ep:warning-filled',
 'pms/project/project-risk/index', 'PmsProjectRisk', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18018, '项目全景', 'pms:project-panoramic:query', 2, 19, 18000, 'project-panoramic', 'ep:view',
 'pms/project/project-panoramic/index', 'PmsProjectPanoramic', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- 资产管理子菜单（19000 下）；19001 设备档案已存在（V6）
(19008, '设备配置日志', 'pms:equipment-config:query', 2, 20, 19000, 'equipment-config-log', 'ep:document',
 'pms/asset/equipment-config-log/index', 'PmsAssetEquipmentConfigLog', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `path` = VALUES(`path`),
 `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
 `parent_id` = VALUES(`parent_id`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
 `icon` = VALUES(`icon`), `update_time` = NOW(), `deleted` = b'0';
