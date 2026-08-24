-- F-PROJ-003 / PRD V1.8：项目授权组合验收种子。
-- 复用V1平台用户1/100与V72项目树；不引入外部授权系统值域。

INSERT INTO `plt_authorization_grant` (`id`, `subject_type_code`, `subject_id`,
  `resource_context_code`, `resource_type_code`, `resource_id`, `action_code`, `scope_code`,
  `effective_from`, `effective_to`, `status_code`, `source_context_code`, `source_object_type`,
  `source_object_id`, `granted_by`, `granted_at`, `revoked_by`, `revoked_at`, `revoke_reason`,
  `version`, `current_marker`, `creator`, `updater`, `deleted`, `tenant_id`) VALUES
-- 精确当前项目：用户100只能获得叶节点30的查看授权。
(992003100001, 'USER', 100, 'PROJ', 'PROJECT', 992002000030, 'PROJECT_VIEW', 'CURRENT_PROJECT',
 '2026-08-01 00:00:00', NULL, 'ACTIVE', 'PROJ', 'Project', 'FPROJ003-EXACT-CURRENT',
 1, '2026-08-01 00:00:00', NULL, NULL, NULL, 0, 1, 'seed', 'seed', b'0', 0),
-- 全部后代：用户100从根项目获得查看整棵项目树的授权。
(992003100002, 'USER', 100, 'PROJ', 'PROJECT', 992002000000, 'PROJECT_VIEW', 'PROJECT_AND_DESCENDANTS',
 '2026-08-01 00:00:00', NULL, 'ACTIVE', 'PROJ', 'Project', 'FPROJ003-ALL-DESCENDANTS',
 1, '2026-08-01 00:00:00', NULL, NULL, NULL, 0, 1, 'seed', 'seed', b'0', 0),
-- 未生效：有效期固定在未来，不参与当前权限计算。
(992003100003, 'USER', 1, 'PROJ', 'PROJECT', 992002000031, 'PROJECT_VIEW', 'CURRENT_PROJECT',
 '2099-01-01 00:00:00', NULL, 'ACTIVE', 'PROJ', 'Project', 'FPROJ003-NOT-YET-EFFECTIVE',
 1, '2026-08-01 00:00:00', NULL, NULL, NULL, 0, 1, 'seed', 'seed', b'0', 0),
-- 已到期：历史记录不占用当前唯一标记。
(992003100004, 'USER', 1, 'PROJ', 'PROJECT', 992002000032, 'PROJECT_VIEW', 'CURRENT_PROJECT',
 '2026-08-01 00:00:00', '2026-08-02 00:00:00', 'EXPIRED', 'PROJ', 'Project', 'FPROJ003-EXPIRED',
 1, '2026-08-01 00:00:00', NULL, NULL, NULL, 1, NULL, 'seed', 'seed', b'0', 0),
-- 已撤权：保留撤权人、时间和原因，历史记录不参与权限计算。
(992003100005, 'USER', 1, 'PROJ', 'PROJECT', 992002000032, 'PROJECT_MANAGE', 'CURRENT_PROJECT',
 '2026-08-01 00:00:00', NULL, 'REVOKED', 'PROJ', 'Project', 'FPROJ003-REVOKED',
 1, '2026-08-01 00:00:00', 1, '2026-08-02 00:00:00', 'F-PROJ-003验收撤权',
 1, NULL, 'seed', 'seed', b'0', 0),
-- 停用不参与：逻辑删除模拟平台停用事实，查询必须排除。
(992003100006, 'USER', 100, 'PROJ', 'PROJECT', 992002000031, 'PROJECT_MANAGE', 'CURRENT_PROJECT',
 '2026-08-01 00:00:00', NULL, 'ACTIVE', 'PROJ', 'Project', 'FPROJ003-INACTIVE-NOT-PARTICIPATING',
 1, '2026-08-01 00:00:00', NULL, NULL, NULL, 0, 1, 'seed', 'seed', b'1', 0)
ON DUPLICATE KEY UPDATE `effective_from`=VALUES(`effective_from`),
  `effective_to`=VALUES(`effective_to`), `status_code`=VALUES(`status_code`),
  `source_object_id`=VALUES(`source_object_id`), `revoked_by`=VALUES(`revoked_by`),
  `revoked_at`=VALUES(`revoked_at`), `revoke_reason`=VALUES(`revoke_reason`),
  `version`=VALUES(`version`), `current_marker`=VALUES(`current_marker`),
  `updater`='seed', `update_time`=NOW(), `deleted`=VALUES(`deleted`);

-- NO_MATCH：用户100在项目992002000029没有PROJECT_MANAGE授权；该场景以缺失事实验收，
-- 不写一条会反向形成有效权限的“无匹配”授权记录。
