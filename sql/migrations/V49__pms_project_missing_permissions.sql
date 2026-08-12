-- =============================================================================
-- V49: 补充项目模块缺失的权限菜单（create / update / delete）
-- 背景：V5/V7 建表时仅创建了 query/assign/sync 权限，导致前端
--      v-hasPermi="['pms:project:create']" 和 v-hasPermi="['pms:project:update']"
--      按钮不渲染。从模板创建按钮依赖 pms:project:create 权限。
-- 修复：在项目总览菜单（18011）下新增 3 个按钮权限。
--      super_admin 角色自动继承所有菜单权限，无需手动分配。
-- =============================================================================
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(18050, '项目创建', 'pms:project:create', 3, 50, 18011, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18051, '项目更新', 'pms:project:update', 3, 51, 18011, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18052, '项目删除', 'pms:project:delete', 3, 52, 18011, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`), `update_time` = NOW(), `deleted` = b'0';
