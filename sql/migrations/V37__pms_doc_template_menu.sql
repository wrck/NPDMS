-- =============================================================================
-- V37: 新增"文档模板"菜单及按钮权限 - 工程文档模板管理入口
-- 背景：V36 已建立 pms_doc_template / pms_doc_template_version 表结构，
--      本迁移在"项目交付"父菜单（id=18000）下注册"文档模板"子菜单，
--      对应前端组件 pms/engineering/doc-template/index，路由 /pms/eng-doc-template。
--      同时补齐 create/update/delete/query/publish 五个按钮权限（type=3），
--      供前端 v-hasPermi 指令使用。
-- ID 范围：18030~18035
-- =============================================================================
INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 文档模板菜单（type=2）==========
(18030, '文档模板', 'pms:eng-doc-template:query', 2, 10, 18000, 'eng-doc-template', 'ep:document',
 'pms/engineering/doc-template/index', 'PmsEngDocTemplate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 文档模板按钮权限（type=3）==========
(18031, '文档模板创建', 'pms:eng-doc-template:create', 3, 1, 18030, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18032, '文档模板更新', 'pms:eng-doc-template:update', 3, 2, 18030, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18033, '文档模板删除', 'pms:eng-doc-template:delete', 3, 3, 18030, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18034, '文档模板查询', 'pms:eng-doc-template:query', 3, 4, 18030, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18035, '文档模板发布', 'pms:eng-doc-template:publish', 3, 5, 18030, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');
