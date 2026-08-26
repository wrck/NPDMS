CREATE TABLE `cus_customer_master` (
  `id` bigint NOT NULL COMMENT '客户编号',
  `code` varchar(64) NOT NULL COMMENT '租户内稳定客户编码',
  `name` varchar(128) NOT NULL COMMENT '客户名称',
  `short_name` varchar(64) DEFAULT NULL COMMENT '客户简称',
  `lifecycle_status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '生命周期状态',
  `source_type` varchar(32) NOT NULL COMMENT '来源类型',
  `source_key` varchar(128) DEFAULT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `sync_status` varchar(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '同步状态',
  `data_as_of` datetime NOT NULL COMMENT '数据截止时间',
  `reconciliation_pending` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否待对账',
  `temporary_reason` varchar(500) DEFAULT NULL COMMENT '临时客户原因',
  `legacy_address_snapshot` varchar(255) DEFAULT NULL COMMENT '旧地址迁移快照',
  `remark` varchar(500) DEFAULT NULL COMMENT '平台备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cus_customer_master_tenant_code` (`tenant_id`, `code`),
  KEY `idx_cus_customer_master_tenant_name` (`tenant_id`, `name`),
  KEY `idx_cus_customer_master_tenant_status` (`tenant_id`, `lifecycle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CUS客户主档';

CREATE TABLE `cus_customer_external_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '映射编号',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `effective_from` datetime NOT NULL COMMENT '生效时间',
  `effective_to` datetime DEFAULT NULL COMMENT '失效时间',
  `current_marker` tinyint GENERATED ALWAYS AS (CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN 1 ELSE NULL END) STORED,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cus_customer_mapping_current_source` (`tenant_id`, `source_system`, `source_key`, `current_marker`),
  KEY `idx_cus_customer_mapping_customer` (`tenant_id`, `customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CUS客户外部映射';

CREATE TABLE `cus_customer_field_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `field_name` varchar(64) NOT NULL,
  `field_owner` varchar(16) NOT NULL,
  `before_value_digest` varchar(128) DEFAULT NULL,
  `after_value_digest` varchar(128) DEFAULT NULL,
  `source_type` varchar(32) NOT NULL,
  `operation_id` varchar(128) NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `occurred_at` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cus_customer_field_history_customer` (`tenant_id`, `customer_id`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CUS客户字段历史';

CREATE TABLE `cus_customer_location_reference` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `location_type` varchar(16) NOT NULL,
  `location_id` bigint NOT NULL,
  `source_version` int NOT NULL,
  `effective_from` datetime NOT NULL,
  `effective_to` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cus_customer_location_customer` (`tenant_id`, `customer_id`, `effective_to`),
  KEY `idx_cus_customer_location_target` (`tenant_id`, `location_type`, `location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CUS客户地点引用';

INSERT INTO `cus_customer_master` (
  `id`, `code`, `name`, `short_name`, `lifecycle_status`, `source_type`, `sync_status`,
  `data_as_of`, `legacy_address_snapshot`, `remark`, `version`, `creator`, `create_time`,
  `updater`, `update_time`, `deleted`, `tenant_id`
)
SELECT
  legacy.`id`, legacy.`code`, legacy.`name`, legacy.`short_name`,
  CASE WHEN legacy.`deleted` = b'1' THEN 'DELETED' WHEN legacy.`status` = 1 THEN 'DISABLED' ELSE 'ENABLED' END,
  'PLATFORM_CREATED', 'NOT_APPLICABLE', legacy.`update_time`, legacy.`address`, legacy.`remark`,
  legacy.`version`, legacy.`creator`, legacy.`create_time`, legacy.`updater`, legacy.`update_time`,
  legacy.`deleted`, legacy.`tenant_id`
FROM `pms_customer` legacy
WHERE NOT EXISTS (
  SELECT 1 FROM `cus_customer_master` target
  WHERE target.`id` = legacy.`id`
);
