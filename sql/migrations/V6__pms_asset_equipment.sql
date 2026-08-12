-- =============================================================================
-- V6: PMS 资产域 - 设备档案、版本与配置日志 (T-V1-AST-001 / T-V1-AST-002)
-- 对应需求：FR-RES-001 设备和序列号档案
--          FR-RES-002 设备版本历史（追加只读）
--          FR-RES-003 配置日志档案
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 设备主表 pms_equipment
--    序列号全局唯一；设备状态：0=在库,1=在用,2=故障,3=维修中,4=已报废
-- -----------------------------------------------------------------------------
CREATE TABLE `pms_equipment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备编号',
  `serial_number` varchar(128) NOT NULL COMMENT '全局唯一序列号',
  `name` varchar(128) NOT NULL COMMENT '设备名称',
  `model` varchar(128) DEFAULT NULL COMMENT '设备型号',
  `customer_id` bigint DEFAULT NULL COMMENT '所属客户编号',
  `project_id` bigint DEFAULT NULL COMMENT '所属项目编号',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0在库,1在用,2故障,3维修中,4已报废',
  `location` varchar(255) DEFAULT NULL COMMENT '设备位置',
  `warranty_start_date` date DEFAULT NULL COMMENT '保修开始日期',
  `warranty_end_date` date DEFAULT NULL COMMENT '保修结束日期',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_equipment_serial_number` (`serial_number`),
  KEY `idx_pms_equipment_customer` (`customer_id`),
  KEY `idx_pms_equipment_project` (`project_id`),
  KEY `idx_pms_equipment_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS设备档案';

-- -----------------------------------------------------------------------------
-- 2. 设备版本历史 pms_equipment_version
--    追加只读表：仅 INSERT，不提供 UPDATE/DELETE 业务通道
-- -----------------------------------------------------------------------------
CREATE TABLE `pms_equipment_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '版本记录编号',
  `equipment_id` bigint NOT NULL COMMENT '设备编号',
  `version_no` int NOT NULL COMMENT '版本号（按设备递增）',
  `change_type` varchar(32) NOT NULL COMMENT '变更类型：CREATE/UPDATE/DEPLOY/REPORT_FAULT/START_REPAIR/COMPLETE_REPAIR/SCRAP',
  `change_description` varchar(500) DEFAULT NULL COMMENT '变更描述',
  `before_snapshot` text COMMENT '变更前快照(JSON)',
  `after_snapshot` text COMMENT '变更后快照(JSON)',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_equipment_version_no` (`equipment_id`, `version_no`),
  KEY `idx_pms_equipment_version_equipment` (`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS设备版本历史(追加只读)';

-- -----------------------------------------------------------------------------
-- 3. 设备配置日志 pms_equipment_config_log
--    FR-RES-003 配置日志档案
-- -----------------------------------------------------------------------------
CREATE TABLE `pms_equipment_config_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置日志编号',
  `equipment_id` bigint NOT NULL COMMENT '设备编号',
  `config_type` varchar(64) NOT NULL COMMENT '配置类型',
  `config_content` text COMMENT '配置内容',
  `source_system` varchar(64) DEFAULT NULL COMMENT '来源系统',
  `collected_at` datetime NOT NULL COMMENT '采集时间',
  `file_url` varchar(512) DEFAULT NULL COMMENT '配置文件URL',
  `file_hash` varchar(64) DEFAULT NULL COMMENT '配置文件哈希',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_pms_equipment_config_equipment` (`equipment_id`),
  KEY `idx_pms_equipment_config_collected` (`collected_at`),
  KEY `idx_pms_equipment_config_hash` (`file_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS设备配置日志';

-- -----------------------------------------------------------------------------
-- 4. 菜单权限 (资产域父菜单 19000 + 设备相关子菜单 19001~19007)
-- -----------------------------------------------------------------------------
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(19000, '资产管理', '', 1, 30, 0, '/pms/asset', 'ep:box', '', '',
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19001, '设备档案管理', 'pms:equipment:query', 2, 10, 19000, 'equipment', 'ep:cpu',
 'pms/asset/equipment/index', 'PmsAssetEquipment', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19002, '设备创建', 'pms:equipment:create', 3, 1, 19001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19003, '设备更新', 'pms:equipment:update', 3, 2, 19001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19004, '设备删除', 'pms:equipment:delete', 3, 3, 19001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19005, '设备状态变更', 'pms:equipment:status-change', 3, 4, 19001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19006, '设备版本查询', 'pms:equipment-version:query', 3, 5, 19001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19007, '配置日志查询', 'pms:equipment-config:query', 3, 6, 19001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `component` = VALUES(`component`),
 `component_name` = VALUES(`component_name`), `update_time` = NOW(), `deleted` = b'0';
