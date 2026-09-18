-- V259: pms_equipment 旧链由 ast_device 承接（需求方2026-09-18指示）
-- 档案管理/位置生效/守卫查询/配置日志功能迁入 ast_device 体系；本迁移处理表与菜单。

-- 设备档案版本历史（ast_device 独立 id 空间，历史版本表 pms_equipment_version 保留为来源证据不迁移数据）
CREATE TABLE `ast_device_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` BIGINT NOT NULL COMMENT '设备编号（ast_device.id）',
  `version_no` INT NOT NULL COMMENT '设备内递增版本号',
  `change_type` VARCHAR(32) NOT NULL COMMENT '变更类型 CREATE/UPDATE/DEPLOY/REPORT_FAULT/START_REPAIR/COMPLETE_REPAIR/SCRAP',
  `change_description` VARCHAR(500) NULL DEFAULT NULL COMMENT '变更说明',
  `before_snapshot` JSON NULL COMMENT '变更前快照',
  `after_snapshot` JSON NULL COMMENT '变更后快照',
  `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_ast_device_version_device` (`tenant_id`, `device_id`, `version_no`)
) ENGINE = InnoDB COMMENT = 'AST 设备档案版本历史（追加只读）';

-- 配置日志表跟随设备档案迁移（新链 DeviceConfigurationLogQueryService 已按 ast_device 主体消费本表）
RENAME TABLE `pms_equipment_config_log` TO `ast_device_config_log`;
ALTER TABLE `ast_device_config_log` RENAME COLUMN `equipment_id` TO `device_id`;

-- 菜单权限并入设备权限族（pms:equipment:* -> pms:device:*）
UPDATE `system_menu` SET `permission` = 'pms:device:query', `update_time` = NOW() WHERE `permission` = 'pms:equipment:query' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:create', `update_time` = NOW() WHERE `permission` = 'pms:equipment:create' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:update', `update_time` = NOW() WHERE `permission` = 'pms:equipment:update' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:delete', `update_time` = NOW() WHERE `permission` = 'pms:equipment:delete' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:status-change', `update_time` = NOW() WHERE `permission` = 'pms:equipment:status-change' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device-version:query', `update_time` = NOW() WHERE `permission` = 'pms:equipment-version:query' AND `deleted` = b'0';
UPDATE `system_menu` SET `permission` = 'pms:device:query', `update_time` = NOW() WHERE `permission` = 'pms:equipment-config:query' AND `deleted` = b'0';

-- 菜单归位：设备档案页与配置日志页并入 device 域
UPDATE `system_menu` SET `path` = 'device-archive', `component` = 'pms/asset/device/archive/index',
  `component_name` = 'PmsAssetDeviceArchive', `update_time` = NOW() WHERE `id` = 19001 AND `deleted` = b'0';
UPDATE `system_menu` SET `path` = 'device-config-log', `component` = 'pms/asset/device/config-log/index',
  `component_name` = 'PmsAssetDeviceConfigLog', `update_time` = NOW() WHERE `id` = 19008 AND `deleted` = b'0';

-- 设备档案状态字典（String 值域，与 ast_device.status 存量值一致；原 pms_equipment_status Integer 字典保留为历史）
INSERT IGNORE INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
(2061, 'PMS-设备档案状态', 'pms_device_status', 0, '设备档案状态（ast_device 承载，String 值域）', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

INSERT IGNORE INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(21510, 0, '在库', 'IN_STOCK', 'pms_device_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21511, 1, '在用', 'IN_USE', 'pms_device_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21512, 2, '故障', 'FAULT', 'pms_device_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21513, 3, '维修中', 'REPAIRING', 'pms_device_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21514, 4, '已报废', 'RETIRED', 'pms_device_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0');

-- 旧链停写前的最后状态增量前向迁移（重跑 V114 五个幂等段：主档/项目关系/客户关系/位置/保修）
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
