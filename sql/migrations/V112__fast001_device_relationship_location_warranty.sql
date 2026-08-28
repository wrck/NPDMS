CREATE TABLE `ast_device_assembly` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '装配关系编号',
  `parent_device_sn` varchar(100) NOT NULL COMMENT '父设备序列号',
  `child_device_sn` varchar(100) NOT NULL COMMENT '子设备序列号',
  `position_code` varchar(64) NOT NULL COMMENT '装配位置编码',
  `assembly_type` varchar(32) NOT NULL COMMENT '装配类型',
  `effective_from` datetime(3) NOT NULL COMMENT '装配生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '装配失效时间',
  `evidence_ref` varchar(128) DEFAULT NULL COMMENT '装配证据引用',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `current_parent_device_sn` varchar(100)
    GENERATED ALWAYS AS (CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN `parent_device_sn` ELSE NULL END) STORED,
  `current_child_device_sn` varchar(100)
    GENERATED ALWAYS AS (CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN `child_device_sn` ELSE NULL END) STORED,
  `current_position_code` varchar(64)
    GENERATED ALWAYS AS (CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN `position_code` ELSE NULL END) STORED,
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_assembly_source` (`tenant_id`, `source_system`, `source_key`),
  UNIQUE KEY `uk_ast_device_assembly_current_child` (`tenant_id`, `current_child_device_sn`),
  UNIQUE KEY `uk_ast_device_assembly_current_position` (`tenant_id`, `current_parent_device_sn`, `current_position_code`),
  KEY `idx_ast_device_assembly_parent` (`tenant_id`, `parent_device_sn`, `effective_to`, `child_device_sn`, `id`),
  KEY `idx_ast_device_assembly_child` (`tenant_id`, `child_device_sn`, `effective_to`, `parent_device_sn`, `id`),
  CONSTRAINT `fk_ast_device_assembly_parent`
    FOREIGN KEY (`tenant_id`, `parent_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `fk_ast_device_assembly_child`
    FOREIGN KEY (`tenant_id`, `child_device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_assembly_self` CHECK (`parent_device_sn` <> `child_device_sn`),
  CONSTRAINT `chk_ast_device_assembly_dates` CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备装配时态关系';

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

CREATE TABLE `ast_device_location` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备位置记录编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `site_id` bigint DEFAULT NULL COMMENT '站点编号',
  `site_location_id` bigint DEFAULT NULL COMMENT '站点位置编号',
  `resolution_status` varchar(16) NOT NULL DEFAULT 'UNRESOLVED' COMMENT '位置解析状态',
  `location_snapshot` text DEFAULT NULL COMMENT '位置快照',
  `effective_from` datetime(3) NOT NULL COMMENT '位置生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '位置失效时间',
  `installation_id` bigint DEFAULT NULL COMMENT '安装记录编号',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `current_device_sn` varchar(100)
    GENERATED ALWAYS AS (CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN `device_sn` ELSE NULL END) STORED,
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_location_source` (`tenant_id`, `source_system`, `source_key`),
  UNIQUE KEY `uk_ast_device_location_current` (`tenant_id`, `current_device_sn`),
  KEY `idx_ast_device_location_device` (`tenant_id`, `device_sn`, `effective_to`, `effective_from`, `id`),
  KEY `idx_ast_device_location_site` (`tenant_id`, `site_id`, `site_location_id`, `effective_to`, `id`),
  CONSTRAINT `fk_ast_device_location_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_location_fact_resolution` CHECK (`resolution_status` IN ('UNRESOLVED', 'RESOLVED')),
  CONSTRAINT `chk_ast_device_location_dates` CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备位置时态事实';

CREATE TABLE `ast_device_warranty` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '当前维保编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `warranty_start_date` date DEFAULT NULL COMMENT '维保开始日期',
  `warranty_end_date` date DEFAULT NULL COMMENT '维保结束日期',
  `warranty_months` int unsigned DEFAULT NULL COMMENT '维保期限月数',
  `warranty_grade` varchar(32) DEFAULT NULL COMMENT '维保等级',
  `warranty_contract_no` varchar(64) DEFAULT NULL COMMENT '维保合同编号',
  `warranty_provider` varchar(128) DEFAULT NULL COMMENT '维保服务商',
  `warranty_type` varchar(32) DEFAULT NULL COMMENT '维保类型',
  `warranty_status` varchar(32) DEFAULT NULL COMMENT '客观维保状态',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
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
  UNIQUE KEY `uk_ast_device_warranty_device` (`tenant_id`, `device_sn`),
  UNIQUE KEY `uk_ast_device_warranty_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_device_warranty_status` (`tenant_id`, `warranty_status`, `warranty_end_date`, `device_sn`),
  CONSTRAINT `fk_ast_device_warranty_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_warranty_fact_dates`
    CHECK (`warranty_end_date` IS NULL OR `warranty_start_date` IS NULL OR `warranty_end_date` >= `warranty_start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备当前维保投影';

CREATE TABLE `ast_device_warranty_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '维保记录编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `warranty_start_date` date DEFAULT NULL COMMENT '维保开始日期',
  `warranty_end_date` date DEFAULT NULL COMMENT '维保结束日期',
  `warranty_months` int unsigned DEFAULT NULL COMMENT '维保期限月数',
  `warranty_grade` varchar(32) DEFAULT NULL COMMENT '维保等级',
  `warranty_contract_no` varchar(64) DEFAULT NULL COMMENT '维保合同编号',
  `extended` bit(1) DEFAULT NULL COMMENT '是否延保',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_warranty_record_source` (`tenant_id`, `source_system`, `source_key`),
  KEY `idx_ast_device_warranty_record_device` (`tenant_id`, `device_sn`, `warranty_start_date`, `id`),
  KEY `idx_ast_device_warranty_record_contract` (`tenant_id`, `warranty_contract_no`, `device_sn`),
  CONSTRAINT `fk_ast_device_warranty_record_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_warranty_record_dates`
    CHECK (`warranty_end_date` IS NULL OR `warranty_start_date` IS NULL OR `warranty_end_date` >= `warranty_start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备维保与续保记录';
