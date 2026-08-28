CREATE TABLE `ast_device_project_relationship` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目关系编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `relationship_type` varchar(32) NOT NULL COMMENT '关系类型',
  `effective_from` datetime(3) NOT NULL COMMENT '生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `assignment_version` int unsigned NOT NULL COMMENT '归属版本',
  `reason` varchar(500) DEFAULT NULL COMMENT '变更原因',
  `operation_id` varchar(128) NOT NULL COMMENT '操作编号',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `current_direct_device_sn` varchar(100)
    GENERATED ALWAYS AS (
      CASE
        WHEN `relationship_type` = 'DIRECT' AND `effective_to` IS NULL AND `deleted` = b'0'
        THEN `device_sn`
        ELSE NULL
      END
    ) STORED,
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_project_relationship_source` (`tenant_id`, `source_system`, `source_key`),
  UNIQUE KEY `uk_ast_device_project_relationship_current` (`tenant_id`, `current_direct_device_sn`),
  KEY `idx_ast_device_project_relationship_device` (`tenant_id`, `device_sn`, `effective_to`, `project_id`, `id`),
  KEY `idx_ast_device_project_relationship_project` (`tenant_id`, `project_id`, `effective_to`, `device_sn`, `id`),
  CONSTRAINT `fk_ast_device_project_relationship_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_project_relationship_dates`
    CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备项目时态关系';

CREATE TABLE `ast_device_project_ancestor` (
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `project_id` bigint NOT NULL COMMENT '直接归属项目编号',
  `ancestor_project_id` bigint NOT NULL COMMENT '祖先项目编号',
  `tree_version` int unsigned NOT NULL COMMENT '项目树版本',
  `assignment_version` int unsigned NOT NULL COMMENT '设备归属版本',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`tenant_id`, `device_sn`, `project_id`, `ancestor_project_id`, `tree_version`),
  KEY `idx_ast_device_project_ancestor_project` (`tenant_id`, `ancestor_project_id`, `device_sn`),
  CONSTRAINT `fk_ast_device_project_ancestor_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备项目祖先投影';

CREATE TABLE `ast_device_customer_relationship` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户关系编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `customer_id` bigint NOT NULL COMMENT '客户编号',
  `relationship_type` varchar(32) NOT NULL COMMENT '关系类型',
  `effective_from` datetime(3) NOT NULL COMMENT '生效时间',
  `effective_to` datetime(3) DEFAULT NULL COMMENT '失效时间',
  `assignment_version` int unsigned NOT NULL COMMENT '归属版本',
  `reason` varchar(500) DEFAULT NULL COMMENT '变更原因',
  `operation_id` varchar(128) NOT NULL COMMENT '操作编号',
  `source_system` varchar(32) NOT NULL COMMENT '来源系统',
  `source_key` varchar(128) NOT NULL COMMENT '来源键',
  `source_version` varchar(64) DEFAULT NULL COMMENT '来源版本',
  `current_direct_device_sn` varchar(100)
    GENERATED ALWAYS AS (
      CASE
        WHEN `relationship_type` = 'DIRECT' AND `effective_to` IS NULL AND `deleted` = b'0'
        THEN `device_sn`
        ELSE NULL
      END
    ) STORED,
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_customer_relationship_source` (`tenant_id`, `source_system`, `source_key`),
  UNIQUE KEY `uk_ast_device_customer_relationship_current` (`tenant_id`, `current_direct_device_sn`),
  KEY `idx_ast_device_customer_relationship_device` (`tenant_id`, `device_sn`, `effective_to`, `customer_id`, `id`),
  KEY `idx_ast_device_customer_relationship_customer` (`tenant_id`, `customer_id`, `effective_to`, `device_sn`, `id`),
  CONSTRAINT `fk_ast_device_customer_relationship_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`),
  CONSTRAINT `chk_ast_device_customer_relationship_dates`
    CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备客户时态关系';

CREATE TABLE `ast_device_assignment_reconciliation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '归属核对编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `project_id` bigint DEFAULT NULL COMMENT '当前项目编号',
  `project_customer_id` bigint DEFAULT NULL COMMENT '项目当前客户编号',
  `device_customer_id` bigint DEFAULT NULL COMMENT '设备当前客户编号',
  `status` varchar(32) NOT NULL COMMENT '核对状态',
  `reason` varchar(500) DEFAULT NULL COMMENT '核对原因',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_ast_device_assignment_reconciliation_device` (`tenant_id`, `device_sn`, `status`, `id`),
  CONSTRAINT `fk_ast_device_assignment_reconciliation_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备项目客户归属核对';
