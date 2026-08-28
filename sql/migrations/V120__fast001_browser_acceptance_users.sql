INSERT INTO `system_role`
(`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000080001, 'FAST001浏览器只读角色', 'fast001_browser_readonly', 910, 1, '', 0, 2,
 'F-AST-001真实浏览器只读验收角色', 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1),
(970000000000080002, 'FAST001浏览器操作角色', 'fast001_browser_operator', 911, 1, '', 0, 2,
 'F-AST-001真实浏览器操作验收角色', 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1),
(970000000000080003, 'FAST001浏览器拒绝角色', 'fast001_browser_denied', 912, 1, '', 0, 2,
 'F-AST-001真实浏览器权限拒绝验收角色', 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `code` = VALUES(`code`), `sort` = VALUES(`sort`),
 `data_scope` = VALUES(`data_scope`), `status` = 0, `type` = VALUES(`type`),
 `remark` = VALUES(`remark`), `updater` = 'fast001_seed', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `system_users`
(`id`, `username`, `password`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`, `sex`,
 `avatar`, `status`, `login_ip`, `login_date`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000081001, 'fast001_readonly', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG',
 'FAST001只读验收', '本地隔离验收账号，口令沿用公开开发环境默认值', 103, '[]', '', '', 0, '', 0, '', NULL,
 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1),
(970000000000081002, 'fast001_operator', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG',
 'FAST001操作验收', '本地隔离验收账号，口令沿用公开开发环境默认值', 103, '[]', '', '', 0, '', 0, '', NULL,
 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1),
(970000000000081003, 'fast001_denied', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG',
 'FAST001拒绝验收', '本地隔离验收账号，口令沿用公开开发环境默认值', 103, '[]', '', '', 0, '', 0, '', NULL,
 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE
 `username` = VALUES(`username`), `password` = VALUES(`password`), `nickname` = VALUES(`nickname`),
 `remark` = VALUES(`remark`), `dept_id` = VALUES(`dept_id`), `status` = 0,
 `updater` = 'fast001_seed', `update_time` = NOW(), `deleted` = b'0', `tenant_id` = 1;

UPDATE `system_user_role`
SET `deleted` = b'1', `updater` = 'fast001_seed', `update_time` = NOW()
WHERE `user_id` IN (970000000000081001, 970000000000081002, 970000000000081003)
  AND `role_id` NOT IN (970000000000080001, 970000000000080002, 970000000000080003)
  AND `deleted` = b'0';

INSERT INTO `system_user_role`
(`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 970000000000081001, 970000000000080001, 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `system_user_role`
  WHERE `user_id` = 970000000000081001 AND `role_id` = 970000000000080001 AND `deleted` = b'0'
)
UNION ALL
SELECT 970000000000081002, 970000000000080002, 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `system_user_role`
  WHERE `user_id` = 970000000000081002 AND `role_id` = 970000000000080002 AND `deleted` = b'0'
)
UNION ALL
SELECT 970000000000081003, 970000000000080003, 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `system_user_role`
  WHERE `user_id` = 970000000000081003 AND `role_id` = 970000000000080003 AND `deleted` = b'0'
);

UPDATE `system_role_menu`
SET `deleted` = b'1', `updater` = 'fast001_seed', `update_time` = NOW()
WHERE `role_id` IN (970000000000080001, 970000000000080002, 970000000000080003)
  AND `deleted` = b'0';

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT role_menu.`role_id`, role_menu.`menu_id`, 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1
FROM (
  SELECT 970000000000080001 AS `role_id`, 19260 AS `menu_id`
  UNION ALL SELECT 970000000000080001, 198770
  UNION ALL SELECT 970000000000080001, 198771
  UNION ALL SELECT 970000000000080001, 19001
  UNION ALL SELECT 970000000000080001, 19006
  UNION ALL SELECT 970000000000080002, 19260
  UNION ALL SELECT 970000000000080002, 198770
  UNION ALL SELECT 970000000000080002, 198771
  UNION ALL SELECT 970000000000080002, 198772
  UNION ALL SELECT 970000000000080002, 198774
  UNION ALL SELECT 970000000000080002, 19001
  UNION ALL SELECT 970000000000080002, 19006
) role_menu
WHERE EXISTS (
  SELECT 1 FROM `system_menu`
  WHERE `id` = 198771 AND `permission` = 'pms:device:query' AND `deleted` = b'0'
)
  AND EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 198772 AND `permission` = 'pms:device:assign' AND `deleted` = b'0'
  )
  AND EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 198774 AND `permission` = 'pms:device-configuration-log:download' AND `deleted` = b'0'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.`role_id` = role_menu.`role_id`
      AND existing.`menu_id` = role_menu.`menu_id`
      AND existing.`deleted` = b'0'
  );
