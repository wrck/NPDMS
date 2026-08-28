INSERT INTO `ast_device` (
  `id`, `sn`, `name`, `product_code`, `product_model`, `product_name`,
  `project_id`, `project_assignment_version`, `customer_id`, `customer_assignment_version`,
  `site_id`, `site_location_id`, `location_resolution_status`, `location_snapshot`,
  `location_effective_from`, `location_record_id`, `warranty_start_date`, `warranty_end_date`,
  `warranty_status`, `conp_version`, `conp_type`, `conp_series`, `conp_mark`, `status`,
  `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000000001, 'FAST001_SN_MAIN', 'FAST001 acceptance main', 'FAST001_TEST_PRODUCT_A', 'FAST001_TEST_MODEL_A', 'FAST001 test product A',
   970000000000001001, 1, 970000000000002002, 1,
   970000000000003001, 970000000000003002, 'RESOLVED', '{"fixture":"FAST001_LOCATION_RESOLVED"}',
   '2026-01-05 08:00:00.000', 970000000000006001, '2026-01-01', '2027-01-01',
   'FAST001_WARRANTY_ACTIVE', 'FAST001_CONP_EXACT_1.2.3', 'FAST001_CONP_TYPE_A', 'FAST001_CONP_SERIES_A', '1.2.3', 'ACTIVE',
   'FAST001_TEST_MES', 'FAST001_DEVICE_MAIN', '1', '2026-01-01 08:00:00.000', '2026-01-01 08:01:00.000', 'FRESH',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000000002, 'FAST001_SN_CHILD_1', 'FAST001 acceptance child 1', 'FAST001_TEST_PRODUCT_A', 'FAST001_TEST_MODEL_A', 'FAST001 test product A',
   NULL, 0, NULL, 0,
   NULL, NULL, 'UNRESOLVED', '{"fixture":"FAST001_LOCATION_UNRESOLVED"}',
   NULL, NULL, NULL, NULL,
   NULL, 'FAST001_CONP_RANGE_1.5.0', 'FAST001_CONP_TYPE_A', 'FAST001_CONP_SERIES_A', '1.x', 'ACTIVE',
   'FAST001_TEST_ITR', 'FAST001_DEVICE_CHILD_1', '2', '2026-01-02 08:00:00.000', '2026-01-02 08:01:00.000', 'STALE',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000000003, 'FAST001_SN_CHILD_2', 'FAST001 acceptance child 2', 'FAST001_TEST_PRODUCT_B', 'FAST001_TEST_MODEL_B', 'FAST001 test product B',
   NULL, 0, NULL, 0,
   NULL, NULL, 'UNRESOLVED', NULL,
   NULL, NULL, NULL, NULL,
   NULL, 'FAST001_CONP_UNKNOWN', NULL, NULL, NULL, 'ACTIVE',
   'FAST001_TEST_ITR', 'FAST001_DEVICE_CHILD_2', '3', '2026-01-03 08:00:00.000', NULL, 'FAILED',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000000004, 'FAST001_CROSS_TENANT_SN', 'FAST001 cross tenant one', 'FAST001_TEST_PRODUCT_C', 'FAST001_TEST_MODEL_C', 'FAST001 test product C',
   NULL, 0, NULL, 0,
   NULL, NULL, 'UNRESOLVED', NULL,
   NULL, NULL, NULL, NULL,
   NULL, NULL, NULL, NULL, NULL, 'ACTIVE',
   'FAST001_TEST_MES', 'FAST001_CROSS_TENANT_SN_T1', '1', '2026-01-04 08:00:00.000', NULL, 'PENDING_MAPPING',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000000005, 'FAST001_SN_NOT_AVAILABLE', 'FAST001 unavailable source', 'FAST001_TEST_PRODUCT_D', 'FAST001_TEST_MODEL_D', 'FAST001 test product D',
   NULL, 0, NULL, 0,
   NULL, NULL, 'UNRESOLVED', NULL,
   NULL, NULL, NULL, NULL,
   NULL, NULL, NULL, NULL, NULL, 'ACTIVE',
   'FAST001_TEST_KNO', 'FAST001_SOURCE_NOT_AVAILABLE', NULL, NULL, NULL, 'NOT_AVAILABLE',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000000101, 'FAST001_CROSS_TENANT_SN', 'FAST001 cross tenant two', 'FAST001_TEST_PRODUCT_C', 'FAST001_TEST_MODEL_C', 'FAST001 test product C',
   NULL, 0, NULL, 0,
   NULL, NULL, 'UNRESOLVED', NULL,
   NULL, NULL, NULL, NULL,
   NULL, NULL, NULL, NULL, NULL, 'ACTIVE',
   'FAST001_TEST_MES', 'FAST001_CROSS_TENANT_SN_T2', '1', '2026-01-04 08:00:00.000', '2026-01-04 08:01:00.000', 'FRESH',
   'fast001_seed', 'fast001_seed', b'0', 2)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `project_id` = VALUES(`project_id`),
  `project_assignment_version` = VALUES(`project_assignment_version`),
  `customer_id` = VALUES(`customer_id`),
  `customer_assignment_version` = VALUES(`customer_assignment_version`),
  `site_id` = VALUES(`site_id`),
  `site_location_id` = VALUES(`site_location_id`),
  `location_resolution_status` = VALUES(`location_resolution_status`),
  `location_snapshot` = VALUES(`location_snapshot`),
  `warranty_start_date` = VALUES(`warranty_start_date`),
  `warranty_end_date` = VALUES(`warranty_end_date`),
  `warranty_status` = VALUES(`warranty_status`),
  `conp_version` = VALUES(`conp_version`),
  `conp_type` = VALUES(`conp_type`),
  `conp_series` = VALUES(`conp_series`),
  `conp_mark` = VALUES(`conp_mark`),
  `sync_status` = VALUES(`sync_status`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_factory_info` (
  `id`, `device_sn`, `manufacturer`, `manufacture_date`, `factory_config`,
  `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000010001, 'FAST001_SN_MAIN', 'FAST001_TEST_MANUFACTURER', '2025-12-01', '{"fixture":"FAST001_FACTORY"}',
   'FAST001_TEST_MES', 'FAST001_FACTORY_MAIN', '1', '2026-01-01 08:00:00.000', '2026-01-01 08:01:00.000', 'FRESH',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `factory_config` = VALUES(`factory_config`),
  `sync_status` = VALUES(`sync_status`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_shipment` (
  `id`, `device_sn`, `shipment_time`, `package_no`, `contract_no`, `event_type`,
  `warranty_start_date`, `warranty_months`, `source_system`, `source_key`, `source_version`,
  `source_updated_at`, `synced_at`, `sync_status`, `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000020001, 'FAST001_SN_MAIN', '2026-01-10 08:00:00.000', 'FAST001_PACKAGE_CURRENT', 'FAST001_CONTRACT_CURRENT', 'FAST001_SHIPMENT_CURRENT',
   '2026-01-10', 12, 'FAST001_TEST_MES', 'FAST001_SHIPMENT_CURRENT', '3',
   '2026-01-10 08:00:00.000', '2026-01-10 08:01:00.000', 'FRESH', 'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000020002, 'FAST001_SN_MAIN', '2026-01-05 08:00:00.000', 'FAST001_PACKAGE_LATE', 'FAST001_CONTRACT_LATE', 'FAST001_SHIPMENT_LATE',
   '2026-01-05', 12, 'FAST001_TEST_MES', 'FAST001_SHIPMENT_LATE', '2',
   '2026-01-11 08:00:00.000', '2026-01-11 08:01:00.000', 'STALE', 'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000020003, 'FAST001_SN_MAIN', '2026-01-20 08:00:00.000', 'FAST001_PACKAGE_DISABLED', 'FAST001_CONTRACT_DISABLED', 'FAST001_SHIPMENT_DISABLED',
   '2026-01-20', 12, 'FAST001_TEST_MES', 'FAST001_SHIPMENT_DISABLED', '4',
   '2026-01-20 08:00:00.000', '2026-01-20 08:01:00.000', 'FRESH', 'fast001_seed', 'fast001_seed', b'1', 1)
ON DUPLICATE KEY UPDATE
  `shipment_time` = VALUES(`shipment_time`),
  `package_no` = VALUES(`package_no`),
  `contract_no` = VALUES(`contract_no`),
  `source_version` = VALUES(`source_version`),
  `sync_status` = VALUES(`sync_status`),
  `updater` = 'fast001_seed',
  `deleted` = VALUES(`deleted`);

UPDATE `ast_device` device
JOIN (
  SELECT shipment.`tenant_id`, shipment.`device_sn`, shipment.`id`, shipment.`shipment_time`, shipment.`package_no`, shipment.`contract_no`
  FROM `ast_device_shipment` shipment
  WHERE shipment.`source_key` = 'FAST001_SHIPMENT_CURRENT'
    AND shipment.`deleted` = b'0'
) current_shipment
  ON current_shipment.`tenant_id` = device.`tenant_id`
 AND current_shipment.`device_sn` = device.`sn`
SET device.`shipment_time` = current_shipment.`shipment_time`,
    device.`package_no` = current_shipment.`package_no`,
    device.`contract_no` = current_shipment.`contract_no`,
    device.`shipment_record_id` = current_shipment.`id`,
    device.`updater` = 'fast001_seed'
WHERE device.`tenant_id` = 1
  AND device.`sn` = 'FAST001_SN_MAIN';

INSERT INTO `ast_product_official_info` (
  `id`, `product_code`, `product_model`, `product_name`, `product_desc`, `technical_spec`,
  `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`, `status`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000030001, 'FAST001_TEST_PRODUCT_A', 'FAST001_TEST_MODEL_A', 'FAST001 test product A', 'FAST001_TEST_KNO controlled fixture', 'FAST001_TEST_KNO controlled fixture',
   'FAST001_TEST_KNO', 'FAST001_KNO_PRODUCT_A', '1', '2026-01-01 08:00:00.000', '2026-01-01 08:01:00.000', 'FRESH', 'FAST001_TEST_PUBLISHED',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `product_desc` = VALUES(`product_desc`),
  `technical_spec` = VALUES(`technical_spec`),
  `sync_status` = VALUES(`sync_status`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_product_official_version` (
  `id`, `product_code`, `product_model`, `conp_version`, `conp_type`, `conp_series`, `conp_mark`,
  `boot_version`, `cpld_version`, `pcb_version`, `customized`, `release_date`, `version_desc`, `status`,
  `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000031001, 'FAST001_TEST_PRODUCT_A', 'FAST001_TEST_MODEL_A', 'FAST001_CONP_EXACT_1.2.3', 'FAST001_CONP_TYPE_A', 'FAST001_CONP_SERIES_A', '1.2.3',
   'FAST001_BOOT_A', 'FAST001_CPLD_A', 'FAST001_PCB_A', b'0', '2026-01-01', 'FAST001_CONP_EXACT', 'FAST001_TEST_PUBLISHED',
   'FAST001_TEST_KNO', 'FAST001_CONP_EXACT', '1', '2026-01-01 08:00:00.000', '2026-01-01 08:01:00.000', 'FRESH',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000031002, 'FAST001_TEST_PRODUCT_A', 'FAST001_TEST_MODEL_A', 'FAST001_CONP_RANGE_1.X', 'FAST001_CONP_TYPE_A', 'FAST001_CONP_SERIES_A', '1.x',
   NULL, NULL, NULL, b'0', '2026-01-02', 'FAST001_CONP_RANGE', 'FAST001_TEST_PUBLISHED',
   'FAST001_TEST_KNO', 'FAST001_CONP_RANGE', '1', '2026-01-02 08:00:00.000', '2026-01-02 08:01:00.000', 'FRESH',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000031003, 'FAST001_TEST_PRODUCT_B', 'FAST001_TEST_MODEL_B', 'FAST001_CONP_UNKNOWN', NULL, NULL, NULL,
   NULL, NULL, NULL, b'0', '2026-01-03', 'FAST001_CONP_UNKNOWN', 'FAST001_TEST_PUBLISHED',
   'FAST001_TEST_KNO', 'FAST001_CONP_UNKNOWN', '1', '2026-01-03 08:00:00.000', NULL, 'PENDING_MAPPING',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `conp_version` = VALUES(`conp_version`),
  `conp_type` = VALUES(`conp_type`),
  `conp_series` = VALUES(`conp_series`),
  `conp_mark` = VALUES(`conp_mark`),
  `sync_status` = VALUES(`sync_status`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_network_version` (
  `id`, `device_sn`, `conp_version`, `conp_type`, `conp_series`, `conp_mark`,
  `boot_version`, `cpld_version`, `pcb_version`, `customized`, `effective_from`,
  `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000032001, 'FAST001_SN_MAIN', 'FAST001_CONP_EXACT_1.2.3', 'FAST001_CONP_TYPE_A', 'FAST001_CONP_SERIES_A', '1.2.3',
   'FAST001_BOOT_A', 'FAST001_CPLD_A', 'FAST001_PCB_A', b'0', '2026-01-01 08:00:00.000',
   'FAST001_TEST_ITR', 'FAST001_ITR_NETWORK_MAIN', '1', '2026-01-01 08:00:00.000', '2026-01-01 08:01:00.000', 'FRESH',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `conp_version` = VALUES(`conp_version`),
  `conp_type` = VALUES(`conp_type`),
  `conp_series` = VALUES(`conp_series`),
  `conp_mark` = VALUES(`conp_mark`),
  `sync_status` = VALUES(`sync_status`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_project_relationship` (
  `id`, `device_sn`, `project_id`, `relationship_type`, `effective_from`, `effective_to`,
  `assignment_version`, `reason`, `operation_id`, `source_system`, `source_key`, `source_version`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000040001, 'FAST001_SN_MAIN', 970000000000001001, 'DIRECT', '2026-01-01 08:00:00.000', NULL,
   1, 'FAST001_ASSIGNMENT_MISMATCH', 'FAST001_PROJECT_ASSIGNMENT_OP', 'FAST001_PLATFORM_TEST', 'FAST001_PROJECT_DIRECT', '1',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `project_id` = VALUES(`project_id`),
  `assignment_version` = VALUES(`assignment_version`),
  `reason` = VALUES(`reason`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_customer_relationship` (
  `id`, `device_sn`, `customer_id`, `relationship_type`, `effective_from`, `effective_to`,
  `assignment_version`, `reason`, `operation_id`, `source_system`, `source_key`, `source_version`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000041001, 'FAST001_SN_MAIN', 970000000000002002, 'DIRECT', '2026-01-01 08:00:00.000', NULL,
   1, 'FAST001_ASSIGNMENT_MISMATCH', 'FAST001_CUSTOMER_ASSIGNMENT_OP', 'FAST001_PLATFORM_TEST', 'FAST001_CUSTOMER_DIRECT', '1',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `customer_id` = VALUES(`customer_id`),
  `assignment_version` = VALUES(`assignment_version`),
  `reason` = VALUES(`reason`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_assignment_reconciliation` (
  `id`, `device_sn`, `project_id`, `project_customer_id`, `device_customer_id`, `status`, `reason`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000042001, 'FAST001_SN_MAIN', 970000000000001001, 970000000000002001, 970000000000002002,
   'FAST001_PENDING_REVIEW', 'FAST001_ASSIGNMENT_MISMATCH', 'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`),
  `reason` = VALUES(`reason`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_location` (
  `id`, `device_sn`, `site_id`, `site_location_id`, `resolution_status`, `location_snapshot`,
  `effective_from`, `effective_to`, `source_system`, `source_key`, `source_version`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000050001, 'FAST001_SN_MAIN', 970000000000003001, 970000000000003002, 'RESOLVED', '{"fixture":"FAST001_LOCATION_RESOLVED"}',
   '2026-01-05 08:00:00.000', NULL, 'FAST001_PLATFORM_TEST', 'FAST001_LOCATION_RESOLVED', '1',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000050002, 'FAST001_SN_CHILD_1', NULL, NULL, 'UNRESOLVED', '{"fixture":"FAST001_LOCATION_UNRESOLVED"}',
   '2026-01-05 08:00:00.000', NULL, 'FAST001_PLATFORM_TEST', 'FAST001_LOCATION_UNRESOLVED', '1',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `site_id` = VALUES(`site_id`),
  `site_location_id` = VALUES(`site_location_id`),
  `resolution_status` = VALUES(`resolution_status`),
  `location_snapshot` = VALUES(`location_snapshot`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_warranty` (
  `id`, `device_sn`, `warranty_start_date`, `warranty_end_date`, `warranty_months`,
  `warranty_grade`, `warranty_contract_no`, `warranty_provider`, `warranty_type`, `warranty_status`, `remark`,
  `source_system`, `source_key`, `source_version`, `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000060001, 'FAST001_SN_MAIN', '2026-01-01', '2027-01-01', 12,
   'FAST001_TEST_GRADE', 'FAST001_WARRANTY_CONTRACT', 'FAST001_TEST_PROVIDER', 'FAST001_TEST_TYPE', 'FAST001_WARRANTY_ACTIVE', 'FAST001_WARRANTY',
   'FAST001_PLATFORM_TEST', 'FAST001_WARRANTY', '1', 'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `warranty_start_date` = VALUES(`warranty_start_date`),
  `warranty_end_date` = VALUES(`warranty_end_date`),
  `warranty_months` = VALUES(`warranty_months`),
  `warranty_status` = VALUES(`warranty_status`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_warranty_record` (
  `id`, `device_sn`, `warranty_start_date`, `warranty_end_date`, `warranty_months`,
  `warranty_grade`, `warranty_contract_no`, `extended`, `remark`,
  `source_system`, `source_key`, `source_version`, `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000061001, 'FAST001_SN_MAIN', '2026-01-01', '2027-01-01', 12,
   'FAST001_TEST_GRADE', 'FAST001_WARRANTY_CONTRACT', b'0', 'FAST001_WARRANTY',
   'FAST001_PLATFORM_TEST', 'FAST001_WARRANTY_RECORD', '1', 'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `warranty_start_date` = VALUES(`warranty_start_date`),
  `warranty_end_date` = VALUES(`warranty_end_date`),
  `warranty_months` = VALUES(`warranty_months`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';

INSERT INTO `ast_device_assembly` (
  `id`, `parent_device_sn`, `child_device_sn`, `position_code`, `assembly_type`,
  `effective_from`, `effective_to`, `evidence_ref`, `source_system`, `source_key`, `source_version`,
  `creator`, `updater`, `deleted`, `tenant_id`
) VALUES
  (970000000000070001, 'FAST001_SN_MAIN', 'FAST001_SN_CHILD_1', 'FAST001_SLOT_1', 'FAST001_TEST_ASSEMBLY',
   '2026-01-01 08:00:00.000', NULL, 'FAST001_ASSEMBLY_LEVEL_1', 'FAST001_PLATFORM_TEST', 'FAST001_ASSEMBLY_LEVEL_1', '1',
   'fast001_seed', 'fast001_seed', b'0', 1),
  (970000000000070002, 'FAST001_SN_CHILD_1', 'FAST001_SN_CHILD_2', 'FAST001_SLOT_2', 'FAST001_TEST_ASSEMBLY',
   '2026-01-01 08:00:00.000', NULL, 'FAST001_ASSEMBLY_LEVEL_2', 'FAST001_PLATFORM_TEST', 'FAST001_ASSEMBLY_LEVEL_2', '1',
   'fast001_seed', 'fast001_seed', b'0', 1)
ON DUPLICATE KEY UPDATE
  `position_code` = VALUES(`position_code`),
  `assembly_type` = VALUES(`assembly_type`),
  `evidence_ref` = VALUES(`evidence_ref`),
  `updater` = 'fast001_seed',
  `deleted` = b'0';
