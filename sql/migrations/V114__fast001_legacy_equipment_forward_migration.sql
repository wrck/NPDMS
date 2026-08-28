INSERT INTO `ast_device` (
  `id`, `sn`, `name`, `product_model`,
  `project_id`, `project_assignment_version`,
  `customer_id`, `customer_assignment_version`,
  `site_id`, `site_location_id`, `location_resolution_status`,
  `location_snapshot`, `location_effective_from`,
  `warranty_start_date`, `warranty_end_date`, `warranty_status`,
  `status`, `remark`, `source_system`, `source_key`,
  `sync_status`, `version`, `creator`, `create_time`, `updater`, `update_time`,
  `deleted`, `tenant_id`
)
SELECT
  legacy.`id`, legacy.`serial_number`, legacy.`name`, legacy.`model`,
  legacy.`project_id`, CASE WHEN legacy.`project_id` IS NULL THEN 0 ELSE 1 END,
  legacy.`customer_id`, CASE WHEN legacy.`customer_id` IS NULL THEN 0 ELSE 1 END,
  legacy.`site_id`, legacy.`site_location_id`, legacy.`location_resolution_status`,
  COALESCE(legacy.`location_snapshot`, legacy.`location`), legacy.`location_effective_from`,
  legacy.`warranty_start_date`, legacy.`warranty_end_date`,
  CASE
    WHEN legacy.`warranty_end_date` IS NULL THEN NULL
    WHEN legacy.`warranty_end_date` >= CURRENT_DATE THEN 'ACTIVE'
    ELSE 'EXPIRED'
  END,
  CASE legacy.`status`
    WHEN 0 THEN 'IN_STOCK'
    WHEN 1 THEN 'IN_USE'
    WHEN 2 THEN 'FAULT'
    WHEN 3 THEN 'REPAIRING'
    WHEN 4 THEN 'RETIRED'
  END,
  legacy.`remark`, 'LEGACY_PMS', CONCAT('pms_equipment:', legacy.`id`),
  'NOT_APPLICABLE', legacy.`version`, legacy.`creator`, legacy.`create_time`,
  legacy.`updater`, legacy.`update_time`, legacy.`deleted`, legacy.`tenant_id`
FROM `pms_equipment` legacy
WHERE NOT EXISTS (
  SELECT 1
  FROM `ast_device` target
  WHERE target.`id` = legacy.`id`
     OR (target.`tenant_id` = legacy.`tenant_id` AND target.`sn` = legacy.`serial_number`)
);

INSERT INTO `ast_device_project_relationship` (
  `device_sn`, `project_id`, `relationship_type`, `effective_from`,
  `assignment_version`, `reason`, `operation_id`,
  `source_system`, `source_key`, `source_version`,
  `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT
  legacy.`serial_number`, legacy.`project_id`, 'DIRECT', legacy.`create_time`,
  1, '旧设备主档前向迁移', CONCAT('FAST001_LEGACY_PROJECT_', legacy.`id`),
  'LEGACY_PMS', CONCAT('pms_equipment_project:', legacy.`id`), CAST(legacy.`version` AS CHAR),
  legacy.`creator`, legacy.`create_time`, legacy.`updater`, legacy.`update_time`, b'0', legacy.`tenant_id`
FROM `pms_equipment` legacy
WHERE legacy.`project_id` IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM `ast_device` device
    WHERE device.`tenant_id` = legacy.`tenant_id` AND device.`sn` = legacy.`serial_number`
  )
  AND NOT EXISTS (
    SELECT 1 FROM `ast_device_project_relationship` relationship
    WHERE relationship.`tenant_id` = legacy.`tenant_id`
      AND relationship.`source_system` = 'LEGACY_PMS'
      AND relationship.`source_key` = CONCAT('pms_equipment_project:', legacy.`id`)
  );

INSERT INTO `ast_device_customer_relationship` (
  `device_sn`, `customer_id`, `relationship_type`, `effective_from`,
  `assignment_version`, `reason`, `operation_id`,
  `source_system`, `source_key`, `source_version`,
  `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT
  legacy.`serial_number`, legacy.`customer_id`, 'DIRECT', legacy.`create_time`,
  1, '旧设备主档前向迁移', CONCAT('FAST001_LEGACY_CUSTOMER_', legacy.`id`),
  'LEGACY_PMS', CONCAT('pms_equipment_customer:', legacy.`id`), CAST(legacy.`version` AS CHAR),
  legacy.`creator`, legacy.`create_time`, legacy.`updater`, legacy.`update_time`, b'0', legacy.`tenant_id`
FROM `pms_equipment` legacy
WHERE legacy.`customer_id` IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM `ast_device` device
    WHERE device.`tenant_id` = legacy.`tenant_id` AND device.`sn` = legacy.`serial_number`
  )
  AND NOT EXISTS (
    SELECT 1 FROM `ast_device_customer_relationship` relationship
    WHERE relationship.`tenant_id` = legacy.`tenant_id`
      AND relationship.`source_system` = 'LEGACY_PMS'
      AND relationship.`source_key` = CONCAT('pms_equipment_customer:', legacy.`id`)
  );

INSERT INTO `ast_device_location` (
  `device_sn`, `site_id`, `site_location_id`, `resolution_status`,
  `location_snapshot`, `effective_from`, `installation_id`,
  `source_system`, `source_key`, `source_version`,
  `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT
  legacy.`serial_number`, legacy.`site_id`, legacy.`site_location_id`,
  legacy.`location_resolution_status`, COALESCE(legacy.`location_snapshot`, legacy.`location`),
  COALESCE(legacy.`location_effective_from`, legacy.`create_time`),
  legacy.`location_source_installation_id`,
  'LEGACY_PMS', CONCAT('pms_equipment_location:', legacy.`id`), CAST(legacy.`version` AS CHAR),
  legacy.`creator`, legacy.`create_time`, legacy.`updater`, legacy.`update_time`, b'0', legacy.`tenant_id`
FROM `pms_equipment` legacy
WHERE (legacy.`site_id` IS NOT NULL OR legacy.`site_location_id` IS NOT NULL OR legacy.`location` IS NOT NULL)
  AND EXISTS (
    SELECT 1 FROM `ast_device` device
    WHERE device.`tenant_id` = legacy.`tenant_id` AND device.`sn` = legacy.`serial_number`
  )
  AND NOT EXISTS (
    SELECT 1 FROM `ast_device_location` location
    WHERE location.`tenant_id` = legacy.`tenant_id`
      AND location.`source_system` = 'LEGACY_PMS'
      AND location.`source_key` = CONCAT('pms_equipment_location:', legacy.`id`)
  );

INSERT INTO `ast_device_warranty` (
  `device_sn`, `warranty_start_date`, `warranty_end_date`, `warranty_status`,
  `source_system`, `source_key`, `source_version`,
  `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT
  legacy.`serial_number`, legacy.`warranty_start_date`, legacy.`warranty_end_date`,
  CASE
    WHEN legacy.`warranty_end_date` IS NULL THEN NULL
    WHEN legacy.`warranty_end_date` >= CURRENT_DATE THEN 'ACTIVE'
    ELSE 'EXPIRED'
  END,
  'LEGACY_PMS', CONCAT('pms_equipment_warranty:', legacy.`id`), CAST(legacy.`version` AS CHAR),
  legacy.`creator`, legacy.`create_time`, legacy.`updater`, legacy.`update_time`, b'0', legacy.`tenant_id`
FROM `pms_equipment` legacy
WHERE (legacy.`warranty_start_date` IS NOT NULL OR legacy.`warranty_end_date` IS NOT NULL)
  AND EXISTS (
    SELECT 1 FROM `ast_device` device
    WHERE device.`tenant_id` = legacy.`tenant_id` AND device.`sn` = legacy.`serial_number`
  )
  AND NOT EXISTS (
    SELECT 1 FROM `ast_device_warranty` warranty
    WHERE warranty.`tenant_id` = legacy.`tenant_id`
      AND warranty.`source_system` = 'LEGACY_PMS'
      AND warranty.`source_key` = CONCAT('pms_equipment_warranty:', legacy.`id`)
  );

INSERT INTO `ast_device_warranty_record` (
  `device_sn`, `warranty_start_date`, `warranty_end_date`,
  `source_system`, `source_key`, `source_version`,
  `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT
  legacy.`serial_number`, legacy.`warranty_start_date`, legacy.`warranty_end_date`,
  'LEGACY_PMS', CONCAT('pms_equipment_warranty_record:', legacy.`id`), CAST(legacy.`version` AS CHAR),
  legacy.`creator`, legacy.`create_time`, legacy.`updater`, legacy.`update_time`, b'0', legacy.`tenant_id`
FROM `pms_equipment` legacy
WHERE (legacy.`warranty_start_date` IS NOT NULL OR legacy.`warranty_end_date` IS NOT NULL)
  AND EXISTS (
    SELECT 1 FROM `ast_device` device
    WHERE device.`tenant_id` = legacy.`tenant_id` AND device.`sn` = legacy.`serial_number`
  )
  AND NOT EXISTS (
    SELECT 1 FROM `ast_device_warranty_record` record
    WHERE record.`tenant_id` = legacy.`tenant_id`
      AND record.`source_system` = 'LEGACY_PMS'
      AND record.`source_key` = CONCAT('pms_equipment_warranty_record:', legacy.`id`)
  );
