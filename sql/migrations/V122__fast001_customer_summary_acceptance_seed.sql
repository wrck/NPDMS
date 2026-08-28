INSERT INTO `cus_customer_master` (
  `id`, `code`, `name`, `short_name`, `lifecycle_status`, `source_type`, `sync_status`,
  `data_as_of`, `reconciliation_pending`, `remark`, `version`,
  `department_code`, `department_name`, `market_code`, `market_name`,
  `system_code`, `system_name`, `expend_code`, `expend_name`, `industry_code`, `industry_name`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000002002, 'FAST001_CUSTOMER_SUMMARY', 'FAST001客户设备摘要验收', 'FAST001摘要',
   'ENABLED', 'PLATFORM_CREATED', 'NOT_APPLICABLE', '2026-08-27 08:00:00', b'0',
   'FAST001客户详情摘要真实浏览器验收', 0,
   'FAST001_DEPARTMENT', 'FAST001办事处', 'FAST001_MARKET', 'FAST001市场部',
   'FAST001_SYSTEM', 'FAST001系统部', 'FAST001_EXPEND', 'FAST001拓展部',
   'FAST001_INDUSTRY', 'FAST001子行业',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000002099, 'FAST001_CUSTOMER_EMPTY', 'FAST001客户设备空摘要验收', 'FAST001空摘要',
   'ENABLED', 'PLATFORM_CREATED', 'NOT_APPLICABLE', '2026-08-27 08:00:00', b'0',
   'FAST001客户详情空设备摘要真实浏览器验收', 0,
   'FAST001_DEPARTMENT', 'FAST001办事处', 'FAST001_MARKET', 'FAST001市场部',
   'FAST001_SYSTEM', 'FAST001系统部', 'FAST001_EXPEND', 'FAST001拓展部',
   'FAST001_INDUSTRY', 'FAST001子行业',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `short_name` = VALUES(`short_name`),
  `lifecycle_status` = VALUES(`lifecycle_status`),
  `remark` = VALUES(`remark`),
  `department_code` = VALUES(`department_code`),
  `department_name` = VALUES(`department_name`),
  `market_code` = VALUES(`market_code`),
  `market_name` = VALUES(`market_name`),
  `system_code` = VALUES(`system_code`),
  `system_name` = VALUES(`system_name`),
  `expend_code` = VALUES(`expend_code`),
  `expend_name` = VALUES(`expend_name`),
  `industry_code` = VALUES(`industry_code`),
  `industry_name` = VALUES(`industry_name`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_customer_relationship` (
  `id`, `device_sn`, `customer_id`, `relationship_type`, `effective_from`, `effective_to`,
  `assignment_version`, `reason`, `operation_id`, `source_system`, `source_key`, `source_version`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000041002, 'FAST001_SN_CHILD_1', 970000000000002002, 'LEASE',
   '2026-01-01 08:00:00.000', NULL, 1, 'FAST001_EFFECTIVE_LEASE', 'FAST001_CUSTOMER_LEASE_OP',
   'FAST001_PLATFORM_TEST', 'FAST001_CUSTOMER_LEASE', '1', 'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000041003, 'FAST001_SN_CHILD_2', 970000000000002002, 'CO_MANAGED',
   '2026-01-01 08:00:00.000', '2099-12-31 23:59:59.999', 1, 'FAST001_EFFECTIVE_CO_MANAGED',
   'FAST001_CUSTOMER_CO_MANAGED_OP', 'FAST001_PLATFORM_TEST', 'FAST001_CUSTOMER_CO_MANAGED', '1',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000041004, 'FAST001_SN_MAIN', 970000000000002002, 'LEASE',
   '2026-02-01 08:00:00.000', NULL, 1, 'FAST001_DUPLICATE_RELATIONSHIP', 'FAST001_CUSTOMER_DUPLICATE_OP',
   'FAST001_PLATFORM_TEST', 'FAST001_CUSTOMER_DUPLICATE', '1', 'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000041005, 'FAST001_SN_NOT_AVAILABLE', 970000000000002002, 'HISTORY',
   '2025-01-01 08:00:00.000', '2025-12-31 23:59:59.999', 1, 'FAST001_EXPIRED_HISTORY',
   'FAST001_CUSTOMER_EXPIRED_OP', 'FAST001_PLATFORM_TEST', 'FAST001_CUSTOMER_EXPIRED', '1',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `customer_id` = VALUES(`customer_id`),
  `relationship_type` = VALUES(`relationship_type`),
  `effective_from` = VALUES(`effective_from`),
  `effective_to` = VALUES(`effective_to`),
  `reason` = VALUES(`reason`),
  `updater` = 'fast001_seed',
  `deleted` = VALUES(`deleted`);

UPDATE `system_role_menu`
SET `deleted` = b'0', `updater` = 'fast001_seed', `update_time` = NOW()
WHERE `role_id` IN (970000000000080001, 970000000000080002)
  AND `menu_id` IN (198760, 198761);

INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT role_menu.`role_id`, role_menu.`menu_id`, 'fast001_seed', NOW(), 'fast001_seed', NOW(), b'0', 1
FROM (
  SELECT 970000000000080001 AS `role_id`, 198760 AS `menu_id`
  UNION ALL SELECT 970000000000080001, 198761
  UNION ALL SELECT 970000000000080002, 198760
  UNION ALL SELECT 970000000000080002, 198761
) role_menu
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
  WHERE existing.`role_id` = role_menu.`role_id`
    AND existing.`menu_id` = role_menu.`menu_id`
    AND existing.`deleted` = b'0'
);
