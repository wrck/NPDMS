-- F-CUS-001 controlled acceptance prerequisites. These rows are directory and access facts only.
INSERT INTO `system_tenant`
(`id`, `name`, `contact_user_id`, `contact_name`, `contact_mobile`, `status`, `websites`, `package_id`,
 `expire_time`, `account_count`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(970000000000090000, 'FCUS001验收租户', NULL, 'FCUS001验收', '', 0, '', 111,
 '2099-12-31 23:59:59', 10, 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `status` = 0, `expire_time` = VALUES(`expire_time`),
 `account_count` = VALUES(`account_count`), `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `system_dept`
(`id`, `code`, `name`, `parent_id`, `sort`, `status`, `version`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000090101, 'FCUS001_OFFICE', 'F-CUS-001 示例办事处', 100, 123, 0, 0,
 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000090102, 'FCUS001_CROSS_TENANT', 'F-CUS-001 跨租户验收部门', 0, 1, 0, 0,
 'seed', NOW(), 'seed', NOW(), b'0', 970000000000090000),
(970000000000090104, 'FAST001_DEPARTMENT', 'FAST001办事处', 100, 124, 0, 0,
 'seed', NOW(), 'seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE
 `code` = VALUES(`code`), `name` = VALUES(`name`), `parent_id` = VALUES(`parent_id`),
 `sort` = VALUES(`sort`), `status` = 0, `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `cus_market_relation`
(`id`, `market_code`, `market_name`, `system_code`, `system_name`, `expend_code`, `expend_name`,
 `industry_code`, `industry_name`, `mapping_status`, `source_version`, `data_as_of`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000090100, 'FCUS001_MARKET', 'F-CUS-001 示例市场部',
 'FCUS001_SYSTEM', 'F-CUS-001 示例系统部', 'FCUS001_EXPEND', 'F-CUS-001 示例拓展部',
 'FCUS001_INDUSTRY', 'F-CUS-001 示例子行业', 'ACTIVE', 'FCUS001_ACCEPTANCE_SEED_V1',
 '2026-08-28 00:00:00', 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000090105, 'FAST001_MARKET', 'FAST001市场部',
 'FAST001_SYSTEM', 'FAST001系统部', 'FAST001_EXPEND', 'FAST001拓展部',
 'FAST001_INDUSTRY', 'FAST001子行业', 'ACTIVE', 'FCUS001_ACCEPTANCE_SEED_V1',
 '2026-08-28 00:00:00', 'seed', NOW(), 'seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE
 `market_name` = VALUES(`market_name`), `system_name` = VALUES(`system_name`),
 `expend_name` = VALUES(`expend_name`), `industry_name` = VALUES(`industry_name`),
 `mapping_status` = 'ACTIVE', `source_version` = 'FCUS001_ACCEPTANCE_SEED_V1',
 `data_as_of` = VALUES(`data_as_of`), `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `pms_equipment`
(`id`, `serial_number`, `name`, `model`, `customer_id`, `status`, `remark`, `version`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000090103, 'FCUS001-DELETE-GUARD-001', 'F-CUS-001 删除守卫验收设备', 'ACCEPTANCE',
 970000000000002002, 1, '受控验收引用，仅用于验证客户删除守卫', 0,
 'seed', NOW(), 'seed', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `model` = VALUES(`model`), `customer_id` = VALUES(`customer_id`),
 `status` = VALUES(`status`), `remark` = VALUES(`remark`), `version` = 0,
 `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0', `tenant_id` = 1;

INSERT INTO `system_role`
(`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000090001, 'FCUS001浏览器只读角色', 'fcus001_browser_readonly', 920, 1, '', 0, 2,
 'F-CUS-001真实浏览器只读验收角色', 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000090002, 'FCUS001浏览器操作角色', 'fcus001_browser_operator', 921, 1, '', 0, 2,
 'F-CUS-001真实浏览器操作验收角色', 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000090003, 'FCUS001浏览器拒绝角色', 'fcus001_browser_denied', 922, 1, '', 0, 2,
 'F-CUS-001真实浏览器权限拒绝验收角色', 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000090004, 'FCUS001跨租户只读角色', 'fcus001_cross_tenant_readonly', 923, 1, '', 0, 2,
 'F-CUS-001第二租户隔离验收角色', 'seed', NOW(), 'seed', NOW(), b'0', 970000000000090000)
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `code` = VALUES(`code`), `sort` = VALUES(`sort`),
 `data_scope` = VALUES(`data_scope`), `status` = 0, `type` = VALUES(`type`),
 `remark` = VALUES(`remark`), `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `system_users`
(`id`, `username`, `password`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`, `sex`,
 `avatar`, `status`, `login_ip`, `login_date`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(970000000000091001, 'fcus001readonly', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG',
 'FCUS001只读验收', '本地隔离验收账号，口令沿用公开开发环境默认值', 103, '[]', '', '', 0, '', 0, '', NULL,
 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000091002, 'fcus001operator', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG',
 'FCUS001操作验收', '本地隔离验收账号，口令沿用公开开发环境默认值', 103, '[]', '', '', 0, '', 0, '', NULL,
 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000091003, 'fcus001denied', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG',
 'FCUS001拒绝验收', '本地隔离验收账号，口令沿用公开开发环境默认值', 103, '[]', '', '', 0, '', 0, '', NULL,
 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000091004, 'fcus001tenant121', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG',
 'FCUS001跨租户验收', '本地隔离验收账号，口令沿用公开开发环境默认值', 970000000000090102, '[]', '', '', 0, '', 0, '', NULL,
 'seed', NOW(), 'seed', NOW(), b'0', 970000000000090000)
ON DUPLICATE KEY UPDATE
 `username` = VALUES(`username`), `password` = VALUES(`password`), `nickname` = VALUES(`nickname`),
 `remark` = VALUES(`remark`), `dept_id` = VALUES(`dept_id`), `status` = 0,
 `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0', `tenant_id` = VALUES(`tenant_id`);

UPDATE `system_user_role`
SET `deleted` = b'1', `updater` = 'seed', `update_time` = NOW()
WHERE `user_id` IN (970000000000091001, 970000000000091002, 970000000000091003, 970000000000091004)
  AND `role_id` NOT IN (970000000000090001, 970000000000090002, 970000000000090003, 970000000000090004)
  AND `deleted` = b'0';

INSERT INTO `system_user_role`
(`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT assignment.`user_id`, assignment.`role_id`, 'seed', NOW(), 'seed', NOW(), b'0', assignment.`tenant_id`
FROM (
 SELECT 970000000000091001 AS `user_id`, 970000000000090001 AS `role_id`, 1 AS `tenant_id`
 UNION ALL SELECT 970000000000091002, 970000000000090002, 1
 UNION ALL SELECT 970000000000091003, 970000000000090003, 1
 UNION ALL SELECT 970000000000091004, 970000000000090004, 970000000000090000
) assignment
WHERE NOT EXISTS (
 SELECT 1 FROM `system_user_role` existing
 WHERE existing.`user_id` = assignment.`user_id`
   AND existing.`role_id` = assignment.`role_id`
   AND existing.`deleted` = b'0'
);

UPDATE `system_role_menu`
SET `deleted` = b'1', `updater` = 'seed', `update_time` = NOW()
WHERE `role_id` IN (970000000000090001, 970000000000090002, 970000000000090003, 970000000000090004)
  AND `deleted` = b'0';

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT grant_row.`role_id`, grant_row.`menu_id`, 'seed', NOW(), 'seed', NOW(), b'0', grant_row.`tenant_id`
FROM (
 SELECT 970000000000090001 AS `role_id`, 19260 AS `menu_id`, 1 AS `tenant_id`
 UNION ALL SELECT 970000000000090001, 198760, 1
 UNION ALL SELECT 970000000000090001, 198761, 1
 UNION ALL SELECT 970000000000090002, 19260, 1
 UNION ALL SELECT 970000000000090002, 198760, 1
 UNION ALL SELECT 970000000000090002, 198761, 1
 UNION ALL SELECT 970000000000090002, 198762, 1
 UNION ALL SELECT 970000000000090002, 198763, 1
 UNION ALL SELECT 970000000000090002, 198764, 1
 UNION ALL SELECT 970000000000090002, 198765, 1
 UNION ALL SELECT 970000000000090002, 198766, 1
 UNION ALL SELECT 970000000000090002, 198767, 1
 UNION ALL SELECT 970000000000090003, 19260, 1
 UNION ALL SELECT 970000000000090004, 19260, 970000000000090000
 UNION ALL SELECT 970000000000090004, 198760, 970000000000090000
 UNION ALL SELECT 970000000000090004, 198761, 970000000000090000
) grant_row
WHERE NOT EXISTS (
 SELECT 1 FROM `system_role_menu` existing
 WHERE existing.`role_id` = grant_row.`role_id`
   AND existing.`menu_id` = grant_row.`menu_id`
   AND existing.`deleted` = b'0'
);

INSERT INTO `cus_customer_scope_slice`
(`id`, `subject_type`, `subject_id`, `department_mode`, `department_codes`, `market_mode`, `market_codes`,
 `system_mode`, `system_codes`, `expend_mode`, `expend_codes`, `industry_mode`, `industry_codes`,
 `effective_from`, `effective_to`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`,
 `deleted`, `tenant_id`)
VALUES
(970000000000092001, 'ROLE', 970000000000090001, 'ALL', NULL, 'ALL', NULL, 'ALL', NULL,
 'ALL', NULL, 'ALL', NULL, '2026-01-01 00:00:00', NULL, 'ACTIVE', 0, 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000092002, 'ROLE', 970000000000090002, 'ALL', NULL, 'ALL', NULL, 'ALL', NULL,
 'ALL', NULL, 'ALL', NULL, '2026-01-01 00:00:00', NULL, 'ACTIVE', 0, 'seed', NOW(), 'seed', NOW(), b'0', 1),
(970000000000092004, 'ROLE', 970000000000090004, 'ALL', NULL, 'ALL', NULL, 'ALL', NULL,
 'ALL', NULL, 'ALL', NULL, '2026-01-01 00:00:00', NULL, 'ACTIVE', 0, 'seed', NOW(), 'seed', NOW(), b'0', 970000000000090000)
ON DUPLICATE KEY UPDATE
 `department_mode` = 'ALL', `department_codes` = NULL, `market_mode` = 'ALL', `market_codes` = NULL,
 `system_mode` = 'ALL', `system_codes` = NULL, `expend_mode` = 'ALL', `expend_codes` = NULL,
 `industry_mode` = 'ALL', `industry_codes` = NULL, `effective_to` = NULL, `status` = 'ACTIVE',
 `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';
