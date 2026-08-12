-- =============================================================================
-- V39: 修正「文档模板」菜单 ID 冲突
-- 背景：V37 使用 ID 18030~18035 注册文档模板菜单，但该 ID 段已被
--      V16 中的「任务WBS管理」/「阶段模板管理」等菜单占用，
--      导致 V37 的 INSERT IGNORE 静默失败，文档模板菜单未注册。
-- 本迁移改用 19250~19255 重新注册文档模板菜单及按钮权限。
-- =============================================================================
INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 文档模板菜单（type=2）==========
(19250, '文档模板', 'pms:eng-doc-template:query', 2, 10, 18000, 'eng-doc-template', 'ep:document',
 'pms/engineering/doc-template/index', 'PmsEngDocTemplate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 文档模板按钮权限（type=3）==========
(19251, '文档模板创建', 'pms:eng-doc-template:create', 3, 1, 19250, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19252, '文档模板更新', 'pms:eng-doc-template:update', 3, 2, 19250, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19253, '文档模板删除', 'pms:eng-doc-template:delete', 3, 3, 19250, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19254, '文档模板查询', 'pms:eng-doc-template:query', 3, 4, 19250, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19255, '文档模板发布', 'pms:eng-doc-template:publish', 3, 5, 19250, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');
