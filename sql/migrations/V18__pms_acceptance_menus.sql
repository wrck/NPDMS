-- =============================================================================
-- V18: PMS 验收与闭环域可见菜单 + 按钮权限（T-V1-ACC-001 ~ T-V1-ACC-004 UI 闭环）
-- 父菜单 18000 项目交付（V4 已存在）；本迁移补齐 6 个验收闭环可见菜单。
-- 使用 ID 19094~19099 避免与 V11(19009~19018)、V13(19019~19023)、V15(19024~19028)、V16(19029~19093) 冲突。
-- 按钮权限 ID 19100~19129，每个实体 5 个按钮（create/update/delete/audit/submit）。
-- =============================================================================
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 验收闭环可见菜单（18000 下，sort 81~86）==========
-- 注：component 路径与 component_name 必须与前端 Vue 文件实际路径和 defineOptions 一致。
(19094, '电子完工证明', 'pms:acc-completion-certificate:query', 2, 81, 18000, 'completion-certificate', 'ep:document-checked',
 'pms/project/completion-certificate/index', 'PmsCompletionCertificate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19095, '验收管理', 'pms:acc-acceptance:query', 2, 82, 18000, 'acceptance', 'ep:finished',
 'pms/project/acceptance/index', 'PmsAcceptance', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19096, '交付件检查', 'pms:acc-deliverable-checklist:query', 2, 83, 18000, 'deliverable-checklist', 'ep:folder-checked',
 'pms/project/deliverable-checklist/index', 'PmsDeliverableChecklist', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19097, '项目闭环', 'pms:acc-project-closure:query', 2, 84, 18000, 'project-closure', 'ep:circle-close',
 'pms/project/project-closure/index', 'PmsProjectClosure', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19098, '归档文档', 'pms:acc-archive-document:query', 2, 85, 18000, 'archive-document', 'ep:folder',
 'pms/project/archive-document/index', 'PmsArchiveDocument', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19099, '转维保', 'pms:acc-maintenance-transition:query', 2, 86, 18000, 'maintenance-transition', 'exp:guide',
 'pms/project/maintenance-transition/index', 'PmsMaintenanceTransition', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 完工证明按钮权限（parent: 19094）==========
(19100, '完工证明创建', 'pms:acc-completion-certificate:create', 3, 1, 19094, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19101, '完工证明更新', 'pms:acc-completion-certificate:update', 3, 2, 19094, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19102, '完工证明删除', 'pms:acc-completion-certificate:delete', 3, 3, 19094, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19103, '完工证明审核', 'pms:acc-completion-certificate:audit', 3, 4, 19094, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19104, '完工证明提交', 'pms:acc-completion-certificate:submit', 3, 5, 19094, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 验收管理按钮权限（parent: 19095）==========
(19105, '验收创建', 'pms:acc-acceptance:create', 3, 1, 19095, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19106, '验收更新', 'pms:acc-acceptance:update', 3, 2, 19095, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19107, '验收删除', 'pms:acc-acceptance:delete', 3, 3, 19095, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19108, '验收审核', 'pms:acc-acceptance:audit', 3, 4, 19095, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19109, '验收提交', 'pms:acc-acceptance:submit', 3, 5, 19095, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 交付件检查按钮权限（parent: 19096）==========
(19110, '交付件创建', 'pms:acc-deliverable-checklist:create', 3, 1, 19096, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19111, '交付件更新', 'pms:acc-deliverable-checklist:update', 3, 2, 19096, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19112, '交付件删除', 'pms:acc-deliverable-checklist:delete', 3, 3, 19096, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19113, '交付件审核', 'pms:acc-deliverable-checklist:audit', 3, 4, 19096, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19114, '交付件提交', 'pms:acc-deliverable-checklist:submit', 3, 5, 19096, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 项目闭环按钮权限（parent: 19097）==========
(19115, '项目闭环创建', 'pms:acc-project-closure:create', 3, 1, 19097, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19116, '项目闭环更新', 'pms:acc-project-closure:update', 3, 2, 19097, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19117, '项目闭环删除', 'pms:acc-project-closure:delete', 3, 3, 19097, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19118, '项目闭环审核', 'pms:acc-project-closure:audit', 3, 4, 19097, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19119, '项目闭环提交', 'pms:acc-project-closure:submit', 3, 5, 19097, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 归档文档按钮权限（parent: 19098）==========
(19120, '归档文档创建', 'pms:acc-archive-document:create', 3, 1, 19098, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19121, '归档文档更新', 'pms:acc-archive-document:update', 3, 2, 19098, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19122, '归档文档删除', 'pms:acc-archive-document:delete', 3, 3, 19098, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19123, '归档文档审核', 'pms:acc-archive-document:audit', 3, 4, 19098, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19124, '归档文档提交', 'pms:acc-archive-document:submit', 3, 5, 19098, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 转维保按钮权限（parent: 19099）==========
(19125, '转维保创建', 'pms:acc-maintenance-transition:create', 3, 1, 19099, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19126, '转维保更新', 'pms:acc-maintenance-transition:update', 3, 2, 19099, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19127, '转维保删除', 'pms:acc-maintenance-transition:delete', 3, 3, 19099, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19128, '转维保审核', 'pms:acc-maintenance-transition:audit', 3, 4, 19099, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19129, '转维保提交', 'pms:acc-maintenance-transition:submit', 3, 5, 19099, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `path` = VALUES(`path`),
 `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
 `parent_id` = VALUES(`parent_id`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
 `icon` = VALUES(`icon`), `update_time` = NOW(), `deleted` = b'0';

-- =============================================================================
-- 将 PMS 验收闭环域全部菜单（可见菜单 + 按钮权限）分配给超级管理员角色（role_id=1）
-- 使用 INSERT IGNORE 避免重复插入已存在的 role_menu 记录
-- =============================================================================
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, m.id, 'admin', NOW(), 'admin', NOW(), b'0'
FROM `system_menu` m
WHERE m.deleted = b'0'
  AND m.id BETWEEN 19094 AND 19129
  AND m.permission LIKE 'pms:acc-%'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = b'0'
  );
