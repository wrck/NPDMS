-- Generated exact production DDL; synthetic data is supplied separately.
SET FOREIGN_KEY_CHECKS=0;
-- V63__fproj001_v18_atomic_project_creation.sql
CREATE TABLE IF NOT EXISTS `plt_idempotency_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `scope_code` VARCHAR(128) NOT NULL,
    `actor_id` BIGINT NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `request_digest` CHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `resource_type` VARCHAR(64) NULL,
    `resource_key` VARCHAR(128) NULL,
    `response_payload` JSON NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_idempotency_scope` (`tenant_id`, `scope_code`, `actor_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台命令幂等记录';

-- V63__fproj001_v18_atomic_project_creation.sql
CREATE TABLE IF NOT EXISTS `plt_operation_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `operation_code` VARCHAR(128) NOT NULL,
    `aggregate_type` VARCHAR(64) NOT NULL,
    `aggregate_key` VARCHAR(128) NULL,
    `actor_id` BIGINT NOT NULL,
    `correlation_id` VARCHAR(128) NOT NULL,
    `idempotency_key_digest` CHAR(64) NULL,
    `result_code` VARCHAR(32) NOT NULL,
    `detail_snapshot` JSON NOT NULL,
    `occurred_at` DATETIME(3) NOT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_plt_operation_audit_aggregate`
        (`tenant_id`, `aggregate_type`, `aggregate_key`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台操作审计';

-- V63__fproj001_v18_atomic_project_creation.sql
CREATE TABLE IF NOT EXISTS `plt_outbox_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `event_id` VARCHAR(64) NOT NULL,
    `event_type` VARCHAR(128) NOT NULL,
    `aggregate_type` VARCHAR(64) NOT NULL,
    `aggregate_key` VARCHAR(128) NOT NULL,
    `payload` JSON NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `occurred_at` DATETIME(3) NOT NULL,
    `next_retry_time` DATETIME(3) NULL,
    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_outbox_event_id` (`event_id`),
    KEY `idx_plt_outbox_dispatch` (`status`, `next_retry_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台事务Outbox事件';

-- V109__fast001_device_master_and_source_facts.sql
CREATE TABLE `ast_device` (
  `id` bigint NOT NULL COMMENT '设备编号',
  `sn` varchar(100) NOT NULL COMMENT '租户内唯一设备序列号',
  `name` varchar(128) DEFAULT NULL COMMENT '设备名称',
  `product_code` varchar(64) DEFAULT NULL COMMENT '产品编码',
  `product_model` varchar(128) DEFAULT NULL COMMENT '产品型号',
  `product_name` varchar(128) DEFAULT NULL COMMENT '产品名称',
  `product_desc` text DEFAULT NULL COMMENT '产品描述',
  `shipment_time` datetime(3) DEFAULT NULL COMMENT '最新有效发货时间',
  `package_no` varchar(64) DEFAULT NULL COMMENT '最新有效装箱单编号',
  `contract_no` varchar(64) DEFAULT NULL COMMENT '最新有效发货合同编号',
  `shipment_record_id` bigint DEFAULT NULL COMMENT '最新有效发货记录编号',
  `project_id` bigint DEFAULT NULL COMMENT '当前直接归属项目编号',
  `project_assignment_version` int unsigned NOT NULL DEFAULT 0 COMMENT '项目归属版本',
  `customer_id` bigint DEFAULT NULL COMMENT '当前直接归属客户编号',
  `customer_assignment_version` int unsigned NOT NULL DEFAULT 0 COMMENT '客户归属版本',
  `site_id` bigint DEFAULT NULL COMMENT '当前站点编号',
  `site_location_id` bigint DEFAULT NULL COMMENT '当前站点位置编号',
  `location_resolution_status` varchar(16) NOT NULL DEFAULT 'UNRESOLVED' COMMENT '当前位置解析状态',
  `location_snapshot` text DEFAULT NULL COMMENT '当前位置快照',
  `location_effective_from` datetime(3) DEFAULT NULL COMMENT '当前位置生效时间',
  `location_record_id` bigint DEFAULT NULL COMMENT '当前位置记录编号',
  `warranty_start_date` date DEFAULT NULL COMMENT '当前维保开始日期',
  `warranty_end_date` date DEFAULT NULL COMMENT '当前维保结束日期',
  `warranty_status` varchar(32) DEFAULT NULL COMMENT '当前客观维保状态',
  `conp_version` varchar(255) DEFAULT NULL COMMENT '当前在网CONP原始版本',
  `conp_type` varchar(100) DEFAULT NULL COMMENT '当前在网CONP类型',
  `conp_series` varchar(100) DEFAULT NULL COMMENT '当前在网CONP系列',
  `conp_mark` varchar(255) DEFAULT NULL COMMENT '当前在网CONP版本掩码',
  `status` varchar(32) NOT NULL COMMENT '设备状态',
  `remark` varchar(500) DEFAULT NULL COMMENT '平台备注',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) DEFAULT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT '来源更新时间',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近成功同步时间',
  `sync_status` varchar(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '同步状态',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_tenant_sn` (`tenant_id`, `sn`),
  KEY `idx_ast_device_project` (`tenant_id`, `project_id`, `deleted`, `id`),
  KEY `idx_ast_device_customer` (`tenant_id`, `customer_id`, `deleted`, `id`),
  KEY `idx_ast_device_product` (`tenant_id`, `product_code`, `product_model`, `deleted`, `id`),
  KEY `idx_ast_device_warranty` (`tenant_id`, `warranty_status`, `warranty_end_date`, `deleted`, `id`),
  KEY `idx_ast_device_shipment` (`tenant_id`, `shipment_time`, `deleted`, `id`),
  KEY `idx_ast_device_conp_parse` (`tenant_id`, `conp_type`, `conp_series`, `conp_mark`, `deleted`, `id`),
  CONSTRAINT `chk_ast_device_location_resolution`
    CHECK (`location_resolution_status` IN ('UNRESOLVED', 'RESOLVED')),
  CONSTRAINT `chk_ast_device_warranty_dates`
    CHECK (`warranty_end_date` IS NULL OR `warranty_start_date` IS NULL OR `warranty_end_date` >= `warranty_start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备主档及高频当前事实投影';

-- V110__fast001_device_shipments_and_software_versions.sql
CREATE TABLE `ast_device_shipment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发货记录编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `shipment_time` datetime(3) DEFAULT NULL COMMENT '发货时间',
  `package_no` varchar(64) DEFAULT NULL COMMENT '装箱单编号',
  `contract_no` varchar(64) DEFAULT NULL COMMENT '合同编号',
  `event_type` varchar(32) NOT NULL COMMENT '发货事件类型',
  `warranty_start_date` date DEFAULT NULL COMMENT '发货约定维保开始日期',
  `warranty_months` int unsigned DEFAULT NULL COMMENT '发货约定维保月数',
  `rma_no` varchar(128) DEFAULT NULL COMMENT 'RMA编号',
  `related_device_sn` varchar(100) DEFAULT NULL COMMENT '关联设备序列号',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `source_updated_at` datetime(3) DEFAULT NULL COMMENT '来源更新时间',
  `synced_at` datetime(3) DEFAULT NULL COMMENT '最近成功同步时间',
  `sync_status` varchar(32) NOT NULL COMMENT '同步状态',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_shipment_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_device_shipment_device` (`tenant_id`, `device_sn`, `shipment_time`, `id`),
  KEY `idx_ast_device_shipment_package` (`tenant_id`, `package_no`, `device_sn`),
  KEY `idx_ast_device_shipment_contract` (`tenant_id`, `contract_no`, `device_sn`),
  CONSTRAINT `fk_ast_device_shipment_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `fk_ast_device_shipment_related_device`
    FOREIGN KEY (`tenant_id`, `related_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备发货记录';

-- V112__fast001_device_relationship_location_warranty.sql
CREATE TABLE `ast_device_relationship` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备关系编号',
  `source_device_sn` varchar(100) NOT NULL COMMENT '来源设备序列号',
  `target_device_sn` varchar(100) NOT NULL COMMENT '目标设备序列号',
  `relationship_type` varchar(32) NOT NULL COMMENT '关系类型',
  `contract_no` varchar(64) DEFAULT NULL COMMENT '合同编号',
  `effective_from` datetime(3) DEFAULT NULL COMMENT '生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_relationship_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_device_relationship_source_device` (`tenant_id`, `source_device_sn`, `relationship_type`, `effective_to`, `id`),
  KEY `idx_ast_device_relationship_target_device` (`tenant_id`, `target_device_sn`, `relationship_type`, `effective_to`, `id`),
  CONSTRAINT `fk_ast_device_relationship_source_device`
    FOREIGN KEY (`tenant_id`, `source_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `fk_ast_device_relationship_target_device`
    FOREIGN KEY (`tenant_id`, `target_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_relationship_self` CHECK (`source_device_sn` <> `target_device_sn`),
  CONSTRAINT `chk_ast_device_relationship_dates`
    CHECK (`effective_to` IS NULL OR `effective_from` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST一般设备时态关系';

-- V160__fcom001_contract_order_scope_forward_migration.sql
CREATE TABLE `com_sales_order` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL,
      `source_system` VARCHAR(32) NOT NULL,
      `source_record_key` VARCHAR(128) COLLATE utf8mb4_0900_bin NULL,
      `source_version` VARCHAR(64) COLLATE utf8mb4_0900_bin NULL,
      `company_id` BIGINT NULL, `company_code` VARCHAR(64) NOT NULL,
      `company_name` VARCHAR(255) NULL, `order_type` VARCHAR(32) NOT NULL,
      `order_no` VARCHAR(64) NOT NULL, `sales_type` VARCHAR(32) NULL,
      `customer_id` BIGINT NULL, `customer_code` VARCHAR(64) NULL,
      `customer_name` VARCHAR(512) NULL, `source_project_name` VARCHAR(512) NULL,
      `order_comment` VARCHAR(2048) NULL, `order_amount` DECIMAL(20, 2) NULL,
      `currency_code` VARCHAR(32) NULL, `authority_status` VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
      `source_lifecycle_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
      `order_create_time` DATETIME(3) NULL,
      `customer_required_time` DATETIME(3) NULL, `source_sync_time` DATETIME(3) NULL,
      `source_updated_at` DATETIME(3) NULL, `status` VARCHAR(32) NOT NULL,
      `version` INT UNSIGNED NOT NULL DEFAULT 0,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_sales_order_tenant_row` (`tenant_id`, `id`),
      UNIQUE KEY `uk_sales_order_business` (`tenant_id`, `source_system`, `company_code`, `order_type`, `order_no`),
      UNIQUE KEY `uk_sales_order_source` (`tenant_id`, `source_system`, `source_record_key`),
      KEY `idx_sales_order_company` (`tenant_id`, `company_id`, `status`, `order_no`),
      KEY `idx_sales_order_customer` (`tenant_id`, `customer_code`, `status`),
      KEY `idx_sales_order_no` (`tenant_id`, `order_no`),
      KEY `idx_sales_order_time` (`tenant_id`, `order_create_time`, `status`),
      CONSTRAINT `chk_sales_order_authority_status` CHECK (`authority_status` IN ('PENDING_AUTHORITY', 'CONFIRMED')),
      CONSTRAINT `chk_sales_order_source_lifecycle` CHECK (`source_lifecycle_status` IN ('ACTIVE', 'CANCELLED', 'RETURNED')),
      CONSTRAINT `chk_sales_order_deleted` CHECK (`deleted` IN (0, 1))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP销售订单主档';

-- V160__fcom001_contract_order_scope_forward_migration.sql
CREATE TABLE `com_sales_order_line` (
      `id` BIGINT NOT NULL, `tenant_id` BIGINT NOT NULL, `order_id` BIGINT NOT NULL,
      `source_system` VARCHAR(32) NOT NULL,
      `source_record_key` VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
      `source_version` VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
      `company_id` BIGINT NULL, `company_code` VARCHAR(64) NOT NULL,
      `company_name` VARCHAR(255) NULL, `order_type` VARCHAR(32) NOT NULL,
      `order_no` VARCHAR(64) NOT NULL, `line_no` VARCHAR(32) NOT NULL,
      `line_type` VARCHAR(32) NULL, `item_code` VARCHAR(64) NULL, `model_code` VARCHAR(64) NULL,
      `item_desc` VARCHAR(512) NULL, `product_id` BIGINT NULL,
      `bundle_code` VARCHAR(64) NULL, `profit_center` VARCHAR(64) NULL,
      `real_execution_no` VARCHAR(64) NULL, `warranty_month` INT NULL,
      `customer_id` BIGINT NULL, `customer_code` VARCHAR(64) NULL,
      `customer_name` VARCHAR(512) NULL,
      `order_qty` DECIMAL(18, 6) NULL, `open_qty` DECIMAL(18, 6) NULL,
      `delivered_qty` DECIMAL(18, 6) NULL, `unit_code` VARCHAR(32) NOT NULL,
      `unit_scale` TINYINT UNSIGNED NOT NULL, `quantity_status` VARCHAR(32) NOT NULL,
      `source_lifecycle_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
      `source_sync_time` DATETIME(3) NULL, `source_updated_at` DATETIME(3) NULL,
      `status` VARCHAR(32) NOT NULL, `version` INT UNSIGNED NOT NULL DEFAULT 0,
      `creator` VARCHAR(64) NOT NULL DEFAULT '',
      `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
      `updater` VARCHAR(64) NOT NULL DEFAULT '',
      `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      `deleted` TINYINT NOT NULL DEFAULT 0,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_sales_order_line_tenant_row` (`tenant_id`, `id`),
      UNIQUE KEY `uk_sales_order_line` (`tenant_id`, `order_id`, `line_no`),
      UNIQUE KEY `uk_sales_order_line_source` (`tenant_id`, `source_system`, `source_record_key`),
      KEY `idx_sales_order_line_business` (`tenant_id`, `source_system`, `company_code`, `order_type`, `order_no`, `line_no`),
      KEY `idx_sales_order_line_customer` (`tenant_id`, `customer_code`, `status`, `id`),
      KEY `idx_sales_order_line_item` (`tenant_id`, `item_code`),
      KEY `idx_sales_order_line_profit` (`tenant_id`, `profit_center`, `order_id`),
      CONSTRAINT `fk_sales_order_line_order` FOREIGN KEY (`tenant_id`, `order_id`)
        REFERENCES `com_sales_order` (`tenant_id`, `id`),
      CONSTRAINT `chk_sales_order_line_deleted` CHECK (`deleted` IN (0, 1)),
      CONSTRAINT `chk_sales_order_line_unit_scale` CHECK (`unit_scale` BETWEEN 0 AND 6),
      CONSTRAINT `chk_sales_order_line_quantity_scale` CHECK (
        (`order_qty` IS NULL OR ROUND(`order_qty`, `unit_scale`) = `order_qty`)
        AND (`open_qty` IS NULL OR ROUND(`open_qty`, `unit_scale`) = `open_qty`)
        AND (`delivered_qty` IS NULL OR ROUND(`delivered_qty`, `unit_scale`) = `delivered_qty`)),
      CONSTRAINT `chk_sales_order_line_authority` CHECK (`quantity_status` <> 'CONFIRMED' OR `order_qty` IS NOT NULL)
      ,CONSTRAINT `chk_sales_order_line_source_lifecycle` CHECK (`source_lifecycle_status` IN ('ACTIVE', 'CANCELLED', 'RETURNED'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ERP销售订单行及数量快照';

-- V161__fcom001_permissions_menu_and_acceptance_seed.sql
ALTER TABLE `com_sales_order_line`
  ADD COLUMN `product_code` varchar(64) NULL COMMENT 'ERP订单行产品编码' AFTER `product_id`;

-- V176__platform_migration_evidence.sql
CREATE TABLE `plt_migration_batch` (
    `id` bigint NOT NULL,
    `owner_context_code` varchar(32) NOT NULL,
    `purpose_code` varchar(64) NOT NULL,
    `release_id` varchar(128) NOT NULL,
    `source_system` varchar(32) NOT NULL,
    `source_table` varchar(64) NOT NULL,
    `manifest_schema_version` varchar(64) NOT NULL,
    `expected_row_count` bigint NOT NULL,
    `content_sha256` char(64) NOT NULL,
    `exported_at` datetime(3) NOT NULL,
    `previous_batch_id` bigint DEFAULT NULL,
    `previous_issue_id` bigint DEFAULT NULL,
    `batch_status` varchar(32) NOT NULL,
    `source_count` bigint NOT NULL,
    `mapped_count` bigint NOT NULL,
    `issue_count` bigint NOT NULL,
    `retained_count` bigint NOT NULL,
    `failure_code` varchar(64) DEFAULT NULL,
    `rule_version` varchar(64) DEFAULT NULL,
    `version` int NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_migration_batch_identity`
        (`tenant_id`, `owner_context_code`, `purpose_code`, `release_id`, `source_system`, `source_table`),
    KEY `idx_plt_migration_batch_claim`
        (`tenant_id`, `owner_context_code`, `purpose_code`, `batch_status`, `create_time`, `id`),
    CONSTRAINT `ck_plt_migration_batch_status` CHECK
        (`batch_status` IN ('IMPORTING', 'STAGED_READY', 'RECONCILING', 'COMPLETED', 'FAILED')),
    CONSTRAINT `ck_plt_migration_batch_counts` CHECK
        (`expected_row_count` >= 0 AND `source_count` >= 0 AND `mapped_count` >= 0
            AND `issue_count` >= 0 AND `retained_count` >= 0),
    CONSTRAINT `ck_plt_migration_batch_version` CHECK (`version` >= 0),
    CONSTRAINT `ck_plt_migration_batch_failure` CHECK (
        (`batch_status` = 'FAILED' AND `failure_code` IN (
            'MANIFEST_STRUCTURE_INVALID', 'MANIFEST_ROW_COUNT_MISMATCH',
            'MANIFEST_SCHEMA_VERSION_MISMATCH', 'MANIFEST_CONTENT_SHA256_MISMATCH',
            'SOURCE_PAYLOAD_INVALID', 'SOURCE_RECORD_CONFLICT'))
        OR (`batch_status` <> 'FAILED' AND `failure_code` IS NULL)
    ),
    CONSTRAINT `ck_plt_migration_batch_final_counts` CHECK (
        (`batch_status` = 'COMPLETED'
            AND `source_count` = `mapped_count` + `issue_count` + `retained_count`
            AND `rule_version` IS NOT NULL)
        OR (`batch_status` <> 'COMPLETED' AND `mapped_count` = 0
            AND `issue_count` = 0 AND `retained_count` = 0 AND `rule_version` IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PLT迁移证据批次';

-- V176__platform_migration_evidence.sql
CREATE TABLE `plt_migration_source_record` (
    `id` bigint NOT NULL,
    `batch_id` bigint NOT NULL,
    `source_system` varchar(32) NOT NULL,
    `source_table` varchar(64) NOT NULL,
    `source_record_key` varchar(128) NOT NULL,
    `source_business_key` varchar(512) DEFAULT NULL,
    `source_payload` json NOT NULL,
    `source_checksum` char(64) NOT NULL,
    `extracted_at` datetime(3) NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_migration_source_identity`
        (`tenant_id`, `batch_id`, `source_system`, `source_table`, `source_record_key`),
    KEY `idx_plt_migration_source_cursor` (`tenant_id`, `batch_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PLT不可变迁移来源行';

-- V176__platform_migration_evidence.sql
CREATE TABLE `plt_external_key_mapping` (
    `id` bigint NOT NULL,
    `batch_id` bigint NOT NULL,
    `source_record_id` bigint NOT NULL,
    `result_type` varchar(16) NOT NULL,
    `target_context` varchar(32) DEFAULT NULL,
    `target_object_type` varchar(64) DEFAULT NULL,
    `target_table` varchar(64) DEFAULT NULL,
    `target_id` bigint DEFAULT NULL,
    `target_role` varchar(32) DEFAULT NULL,
    `target_sequence` int DEFAULT NULL,
    `result_key` varchar(96) GENERATED ALWAYS AS (
        CASE WHEN `result_type` = 'RETAINED' THEN 'RETAINED'
             ELSE CONCAT(`target_role`, ':', `target_sequence`) END
    ) STORED,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_external_mapping_result` (`tenant_id`, `source_record_id`, `result_key`),
    KEY `idx_plt_external_mapping_batch` (`tenant_id`, `batch_id`, `source_record_id`),
    CONSTRAINT `ck_plt_external_mapping_union` CHECK (
        (`result_type` = 'MAPPED' AND `target_context` IS NOT NULL
            AND `target_object_type` IS NOT NULL AND `target_table` IS NOT NULL
            AND `target_id` > 0 AND `target_role` IS NOT NULL AND `target_sequence` >= 0)
        OR (`result_type` = 'RETAINED' AND `target_context` IS NULL
            AND `target_object_type` IS NULL AND `target_table` IS NULL
            AND `target_id` IS NULL AND `target_role` IS NULL AND `target_sequence` IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PLT迁移外部键映射或留存分类';

-- V176__platform_migration_evidence.sql
CREATE TABLE `plt_migration_issue` (
    `id` bigint NOT NULL,
    `batch_id` bigint NOT NULL,
    `source_record_id` bigint NOT NULL,
    `issue_key` varchar(128) NOT NULL,
    `issue_type` varchar(64) NOT NULL,
    `raw_business_key` varchar(512) DEFAULT NULL,
    `candidate_target_ids` json NOT NULL,
    `raw_payload` json DEFAULT NULL,
    `issue_status` varchar(16) NOT NULL,
    `resolver_user_id` bigint DEFAULT NULL,
    `rule_version` varchar(64) DEFAULT NULL,
    `target_result` json DEFAULT NULL,
    `resolved_at` datetime(3) DEFAULT NULL,
    `version` int NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_migration_issue_key` (`tenant_id`, `source_record_id`, `issue_key`),
    KEY `idx_plt_migration_issue_batch` (`tenant_id`, `batch_id`, `source_record_id`),
    CONSTRAINT `ck_plt_migration_issue_status` CHECK (`issue_status` IN ('OPEN', 'CLOSED')),
    CONSTRAINT `ck_plt_migration_issue_resolution` CHECK (
        (`issue_status` = 'OPEN' AND `resolver_user_id` IS NULL AND `rule_version` IS NULL
            AND `target_result` IS NULL AND `resolved_at` IS NULL)
        OR (`issue_status` = 'CLOSED' AND `resolver_user_id` > 0 AND `rule_version` IS NOT NULL
            AND `target_result` IS NOT NULL AND `resolved_at` IS NOT NULL)
    ),
    CONSTRAINT `ck_plt_migration_issue_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PLT迁移问题与追加式关闭证据';

-- V237__integration_data_sync_foundation.sql
CREATE TABLE `int_sync_connection` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `data_source_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_connection_source` (`tenant_id`,`data_source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V237__integration_data_sync_foundation.sql
CREATE TABLE `int_sync_task` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `connection_id` bigint NOT NULL,
  `adapter` varchar(64) NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `definition` json NOT NULL,
  `version` int NOT NULL DEFAULT 0,
  `validated_version` int NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'0',
  `active_run_id` bigint NULL,
  `checkpoint` datetime(6) NULL,
  `next_run_at` datetime(3) NULL,
  `next_full_at` datetime(3) NULL,
  `retry_attempt` int NOT NULL DEFAULT 0,
  `last_failed_run_id` bigint NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_task_source_adapter` (`tenant_id`,`source_system`,`adapter`),
  KEY `idx_int_task_due` (`tenant_id`,`enabled`,`next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V237__integration_data_sync_foundation.sql
CREATE TABLE `int_sync_run` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `parent_run_id` bigint NULL,
  `request_key` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `preview` bit(1) NOT NULL,
  `full_snapshot` bit(1) NOT NULL,
  `adopt_existing` bit(1) NOT NULL DEFAULT b'0',
  `config_snapshot` json NOT NULL,
  `config_version` int NOT NULL,
  `result_json` json NULL,
  `summary_json` json NULL,
  `read_count` int NOT NULL DEFAULT 0,
  `error_message` varchar(1000) NULL,
  `evidence_json` json NULL,
  `source_upper` datetime(6) NULL,
  `started_at` datetime(3) NOT NULL,
  `finished_at` datetime(3) NULL,
  `cache_pending` bit(1) NOT NULL DEFAULT b'0',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_run_request` (`tenant_id`,`task_id`,`request_key`),
  KEY `idx_int_run_task` (`tenant_id`,`task_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V237__integration_data_sync_foundation.sql
CREATE TABLE `int_sync_binding` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `object_key` varchar(64) NOT NULL,
  `source_object` varchar(64) NOT NULL,
  `source_key` varchar(128) NOT NULL,
  `target_id` bigint NOT NULL,
  `fields_json` json NOT NULL,
  `last_run_id` bigint NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_binding_source` (`tenant_id`,`task_id`,`source_object`,`source_key`),
  UNIQUE KEY `uk_int_binding_target` (`tenant_id`,`task_id`,`object_key`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V243__commerce_order_line_unknown_unit.sql
ALTER TABLE com_sales_order_line MODIFY COLUMN unit_code varchar(32) NULL;

-- V243__commerce_order_line_unknown_unit.sql
ALTER TABLE com_sales_order_line ADD CONSTRAINT chk_sales_order_line_known_unit
    CHECK (quantity_status = 'PENDING_AUTHORITY' OR unit_code IS NOT NULL);

-- V243_1__integration_shared_target_bindings.sql
ALTER TABLE int_sync_binding
    ADD COLUMN target_shared boolean NOT NULL DEFAULT FALSE,
    ADD COLUMN exclusive_target_id bigint GENERATED ALWAYS AS
        (CASE WHEN target_shared = FALSE THEN target_id ELSE NULL END) STORED,
    DROP INDEX uk_int_binding_target,
    ADD UNIQUE KEY uk_int_binding_target (tenant_id, task_id, object_key, exclusive_target_id),
    ADD KEY idx_int_binding_target (tenant_id, task_id, object_key, target_id);

-- V243_2__integration_automatic_paging.sql
ALTER TABLE int_sync_run
    ADD COLUMN page_number int NULL COMMENT '自动分页子批次序号；主运行为空',
    ADD COLUMN paging_json json NULL COMMENT '已提交分页游标与固定来源主键上界';

-- V294__migration_field_naming_and_comment_alignment.sql
ALTER TABLE com_sales_order
    MODIFY COLUMN `company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公司编码',
    MODIFY COLUMN `company_id` bigint NULL COMMENT '公司ID',
    MODIFY COLUMN `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '公司名称',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `customer_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户编码',
    MODIFY COLUMN `customer_id` bigint NULL COMMENT '客户ID',
    MODIFY COLUMN `customer_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
    MODIFY COLUMN `customer_required_time` datetime(3) NULL COMMENT '客户要求时间',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `order_comment` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '订单说明',
    MODIFY COLUMN `order_create_time` datetime(3) NULL COMMENT '订单创建时间',
    MODIFY COLUMN `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单编号',
    MODIFY COLUMN `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单类型编码',
    MODIFY COLUMN `sales_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '销售类型编码',
    MODIFY COLUMN `source_project_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源项目名称',
    MODIFY COLUMN `source_sync_time` datetime(3) NULL COMMENT '来源同步时间',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源系统',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

-- V294__migration_field_naming_and_comment_alignment.sql
ALTER TABLE com_sales_order_line
    MODIFY COLUMN `bundle_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '套件编码',
    MODIFY COLUMN `company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公司编码',
    MODIFY COLUMN `company_id` bigint NULL COMMENT '父订单所属公司主档ID',
    MODIFY COLUMN `company_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '公司名称',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `customer_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户编码',
    MODIFY COLUMN `customer_id` bigint NULL COMMENT '父订单客户主档ID',
    MODIFY COLUMN `customer_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
    MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标志：0否，1是',
    MODIFY COLUMN `delivered_qty` decimal(18,6) NULL COMMENT 'ERP订单行当前累计发货数量',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `item_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '物料编码',
    MODIFY COLUMN `item_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '物料描述',
    MODIFY COLUMN `line_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '行编号',
    MODIFY COLUMN `line_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '行类型编码',
    MODIFY COLUMN `open_qty` decimal(18,6) NULL COMMENT 'ERP订单行当前未执行数量',
    MODIFY COLUMN `order_id` bigint NOT NULL COMMENT '订单ID',
    MODIFY COLUMN `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '父订单编号',
    MODIFY COLUMN `order_qty` decimal(18,6) NULL COMMENT 'ERP订单行下单数量',
    MODIFY COLUMN `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '父订单类型编码',
    MODIFY COLUMN `product_id` bigint NULL COMMENT '产品ID',
    MODIFY COLUMN `profit_center` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '利润中心',
    MODIFY COLUMN `real_execution_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '实际执行单编号',
    MODIFY COLUMN `source_sync_time` datetime(3) NULL COMMENT '来源同步时间',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '父订单来源系统',
    MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人',
    MODIFY COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    MODIFY COLUMN `warranty_month` int NULL COMMENT '质保期限月数';

-- V294__migration_field_naming_and_comment_alignment.sql
ALTER TABLE plt_external_key_mapping
    MODIFY COLUMN `batch_id` bigint NOT NULL COMMENT '批次ID',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `target_id` bigint NULL COMMENT '目标ID',
    MODIFY COLUMN `target_role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '业务角色',
    MODIFY COLUMN `target_sequence` int NULL COMMENT '目标顺序',
    MODIFY COLUMN `target_table` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '目标表',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人';

-- V294__migration_field_naming_and_comment_alignment.sql
ALTER TABLE plt_migration_issue
    MODIFY COLUMN `batch_id` bigint NOT NULL COMMENT '批次ID',
    MODIFY COLUMN `candidate_target_ids` json NOT NULL COMMENT '目标记录ID数组JSON',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `issue_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题类型编码',
    MODIFY COLUMN `raw_business_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '原始业务键',
    MODIFY COLUMN `raw_payload` json NULL COMMENT '原始字段和值JSON',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人';

-- V294__migration_field_naming_and_comment_alignment.sql
ALTER TABLE plt_migration_source_record
    MODIFY COLUMN `batch_id` bigint NOT NULL COMMENT '批次ID',
    MODIFY COLUMN `create_time` datetime(3) NOT NULL COMMENT '创建时间',
    MODIFY COLUMN `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
    MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
    MODIFY COLUMN `source_business_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '来源记录可读业务键',
    MODIFY COLUMN `source_checksum` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源记录SHA-256校验值',
    MODIFY COLUMN `source_payload` json NOT NULL COMMENT '来源记录原值JSON',
    MODIFY COLUMN `source_system` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源系统',
    MODIFY COLUMN `source_table` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源系统物理表名',
    MODIFY COLUMN `tenant_id` bigint NOT NULL COMMENT '租户ID',
    MODIFY COLUMN `update_time` datetime(3) NOT NULL COMMENT '更新时间',
    MODIFY COLUMN `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新人';

-- V324__configurable_shipment_sync_fields.sql
ALTER TABLE ast_device
    MODIFY COLUMN package_no VARCHAR(128) NULL,
    ADD COLUMN internal_serial_no VARCHAR(128) NULL COMMENT '内部序列号',
    ADD COLUMN secondary_sn VARCHAR(100) NULL COMMENT '最新发货附加SN缓存',
    ADD COLUMN secondary_item VARCHAR(64) NULL COMMENT '最新发货附加物料缓存';

-- V324__configurable_shipment_sync_fields.sql
ALTER TABLE ast_device_shipment
    MODIFY COLUMN package_no VARCHAR(128) NULL,
    ADD COLUMN legacy_package_key VARCHAR(128) NULL COMMENT '来源装箱单键，未解析时仍保留',
    ADD COLUMN rma_related_sn VARCHAR(100) NULL COMMENT '来源RMA关联SN候选，不生成替换关系',
    ADD COLUMN rma_marked TINYINT NOT NULL DEFAULT 0 COMMENT '来源RMA标识',
    ADD COLUMN order_line_id BIGINT NULL COMMENT '唯一解析的ERP订单行ID',
    ADD COLUMN source_payload JSON NULL COMMENT '来源条码记录';

-- V337__device_organization_projection.sql
ALTER TABLE ast_device
  ADD COLUMN company_id BIGINT NULL COMMENT '所属公司 ID',
  ADD COLUMN company_name VARCHAR(255) NULL COMMENT '所属公司名称',
  ADD COLUMN department_id BIGINT NULL COMMENT '所属办事处/部门 ID',
  ADD COLUMN department_code VARCHAR(64) NULL COMMENT '所属办事处/部门编码',
  ADD COLUMN department_name VARCHAR(255) NULL COMMENT '所属办事处/部门名称',
  ADD COLUMN organization_source VARCHAR(20) NOT NULL DEFAULT 'UNRESOLVED' COMMENT 'PROJECT/CONTRACT/UNRESOLVED',
  ADD COLUMN organization_updated_at DATETIME(3) NULL COMMENT '归属重建时间',
  ADD INDEX idx_device_organization (tenant_id, deleted, company_id, department_id, id);

-- V351__drop_device_sn_foreign_keys.sql
ALTER TABLE `ast_device_shipment` DROP FOREIGN KEY `fk_ast_device_shipment_device`;

-- V351__drop_device_sn_foreign_keys.sql
ALTER TABLE `ast_device_shipment` DROP FOREIGN KEY `fk_ast_device_shipment_related_device`;

-- V351__drop_device_sn_foreign_keys.sql
ALTER TABLE `ast_device_relationship` DROP FOREIGN KEY `fk_ast_device_relationship_source_device`;

-- V351__drop_device_sn_foreign_keys.sql
ALTER TABLE `ast_device_relationship` DROP FOREIGN KEY `fk_ast_device_relationship_target_device`;

-- V367__ubmc009_b4_aggregate_id_autoincrement.sql
ALTER TABLE com_sales_order
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '销售订单编号';

-- V367__ubmc009_b4_aggregate_id_autoincrement.sql
ALTER TABLE ast_device
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备编号';

-- V378__fcom001_order_line_material_dedup_and_custom_info.sql
ALTER TABLE `com_sales_order`
  ADD COLUMN `custom_info` JSON NULL COMMENT '来源customInfo原样同步' AFTER `customer_required_time`;

-- V378__fcom001_order_line_material_dedup_and_custom_info.sql
ALTER TABLE `com_sales_order_line` DROP INDEX `idx_sales_order_line_item`;

-- V378__fcom001_order_line_material_dedup_and_custom_info.sql
ALTER TABLE `com_sales_order_line`
  CHANGE COLUMN `item_desc` `product_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL
    COMMENT '产品描述/物料描述（原item_desc）';

-- V378__fcom001_order_line_material_dedup_and_custom_info.sql
ALTER TABLE `com_sales_order_line`
  CHANGE COLUMN `model_code` `product_model` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL
    COMMENT '产品型号/物料型号（原model_code）';

-- V378__fcom001_order_line_material_dedup_and_custom_info.sql
ALTER TABLE `com_sales_order_line` DROP COLUMN `item_code`;

-- V378__fcom001_order_line_material_dedup_and_custom_info.sql
ALTER TABLE `com_sales_order_line`
  MODIFY COLUMN `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL
    COMMENT '产品编码/物料编码（ERP订单行）' AFTER `line_type`;

-- V378__fcom001_order_line_material_dedup_and_custom_info.sql
ALTER TABLE `com_sales_order_line`
  ADD COLUMN `custom_info` JSON NULL COMMENT '来源customInfo原样同步' AFTER `product_desc`;

-- V378__fcom001_order_line_material_dedup_and_custom_info.sql
ALTER TABLE `com_sales_order_line`
  ADD INDEX `idx_sales_order_line_product` (`tenant_id`, `product_code`);

-- V381__shipment_event_source_order_columns.sql
ALTER TABLE ast_device_shipment
    ADD COLUMN order_no VARCHAR(64) NULL COMMENT '来源订单号',
    ADD COLUMN line_no VARCHAR(64) NULL COMMENT '来源订单行号';

-- V382__shipment_barcode_raw_landing.sql
ALTER TABLE ast_device_shipment
    ADD COLUMN product_code VARCHAR(64) NULL COMMENT '来源物料编码 item',
    ADD COLUMN secondary_sn VARCHAR(100) NULL COMMENT '来源附加SN barcode2',
    ADD COLUMN secondary_item VARCHAR(64) NULL COMMENT '来源附加物料 item2',
    ADD COLUMN internal_serial_no VARCHAR(128) NULL COMMENT '来源组合条码 com_barcode',
    ADD COLUMN is_rma INT NULL COMMENT '来源RMA标识 isRMA',
    ADD COLUMN profit_center VARCHAR(32) NULL COMMENT '来源利润中心 profitCenter',
    ADD COLUMN sole_agent_suffix VARCHAR(32) NULL COMMENT '来源独家代理后缀 soleAgentSuffix',
    ADD COLUMN sync_time DATETIME NULL COMMENT '来源同步时间 syncTime',
    ADD COLUMN uuid VARCHAR(64) NULL COMMENT '来源UUID';

-- V382__shipment_barcode_raw_landing.sql
ALTER TABLE ast_device_relationship
    ADD COLUMN source_item VARCHAR(15) NULL COMMENT '来源主SN物料 item1',
    ADD COLUMN target_item VARCHAR(15) NULL COMMENT '来源目标SN物料 item2';

-- V383__sales_order_primary_contract_execution_columns.sql
ALTER TABLE com_sales_order
    ADD COLUMN contract_no VARCHAR(64) NULL
        COMMENT '主合同号(快照来源订单头contractNo,对应合同主档业务键)' AFTER sales_type,
    ADD COLUMN execution_no VARCHAR(64) NULL
        COMMENT '主执行单号(快照来源订单头orderExecNumber)' AFTER contract_no,
    ADD KEY idx_sales_order_contract (tenant_id, contract_no),
    ADD KEY idx_sales_order_execution (tenant_id, execution_no);
SET FOREIGN_KEY_CHECKS=1;
