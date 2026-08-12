-- =============================================================================
-- V34: 新增"项目详情"菜单 - 项目实施各角色操作的核心界面
-- 背景：项目详情界面是囊括所有业务操作的统一入口，集成项目全景、阶段、任务WBS、
--      风险、单机风险、技术公告、授权、交付件、团队、客户、割接、验收等模块。
--      对应前端组件 pms/project/project-detail/index，路由 /pms/project-detail。
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(18020, '项目详情', 'pms:project-detail:query', 2, 9, 18000, 'project-detail', 'ep:monitor',
 'pms/project/project-detail/index', 'PmsProjectDetail', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `path` = VALUES(`path`),
 `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
 `parent_id` = VALUES(`parent_id`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
 `icon` = VALUES(`icon`), `update_time` = NOW(), `deleted` = b'0';
