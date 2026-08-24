-- F-PROJ-002 V1.8：项目树权限验收成员区间。
-- user_id=1 为根项目经理，可见全树；user_id=100 仅为叶节点成员，只能看到直达路径占位。
INSERT INTO `proj_project_member_assignment` (`id`, `project_id`, `user_id`, `employee_no`, `member_name`,
  `member_role`, `responsibility`, `effective_from`, `effective_to`, `status`, `version`,
  `creator`, `updater`, `deleted`, `tenant_id`) VALUES
(992002600001, 992002000000, 1, 'SEED-ADMIN', 'FPROJ002根管理者', 'PROJECT_MANAGER',
 'F-PROJ-002 V1.8全树可见验收', '2026-08-01 00:00:00', NULL, 'ACTIVE', 0, 'seed', 'seed', b'0', 0),
(992002600002, 992002000030, 100, 'SEED-LIMITED', 'FPROJ002有限成员', 'MEMBER',
 'F-PROJ-002 V1.8同根有限可见验收', '2026-08-01 00:00:00', NULL, 'ACTIVE', 0, 'seed', 'seed', b'0', 0)
ON DUPLICATE KEY UPDATE `member_name`=VALUES(`member_name`), `responsibility`=VALUES(`responsibility`),
  `effective_to`=NULL, `status`='ACTIVE', `updater`='seed', `update_time`=NOW(), `deleted`=b'0';
