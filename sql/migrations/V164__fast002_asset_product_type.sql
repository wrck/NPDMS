CREATE TABLE `ast_product_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type_code` varchar(64) NOT NULL,
  `display_name` varchar(128) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `source_system` varchar(32) NOT NULL,
  `source_key` varchar(128) NOT NULL,
  `source_version` varchar(128) NOT NULL,
  `source_updated_at` datetime(3) NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `sync_status` varchar(32) NOT NULL,
  `last_sync_attempt_at` datetime(3) DEFAULT NULL,
  `synced_at` datetime(3) NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_product_type_tenant_code` (`tenant_id`, `type_code`),
  UNIQUE KEY `uk_ast_product_type_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_ast_product_type_tenant_id_code` (`tenant_id`, `id`, `type_code`),
  KEY `idx_ast_product_type_sync` (`tenant_id`, `sync_status`, `source_updated_at`, `id`),
  CONSTRAINT `chk_ast_product_type_code` CHECK (`type_code` <> ''),
  CONSTRAINT `chk_ast_product_type_name` CHECK (`display_name` <> ''),
  CONSTRAINT `chk_ast_product_type_sync_status`
    CHECK (`sync_status` IN ('FRESH', 'STALE', 'FAILED', 'PENDING_MAPPING', 'NOT_AVAILABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ast_product_type_source_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_system` varchar(32) NOT NULL,
  `source_key` varchar(128) NOT NULL,
  `source_version` varchar(128) NOT NULL,
  `source_updated_at` datetime(3) NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `product_type_id` bigint DEFAULT NULL,
  `mapping_status` varchar(32) NOT NULL,
  `conflict_product_type_code` varchar(64) DEFAULT NULL,
  `conflict_source_version` varchar(128) DEFAULT NULL,
  `conflict_source_updated_at` datetime(3) DEFAULT NULL,
  `conflict_payload_hash` char(64) DEFAULT NULL,
  `synced_at` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_product_type_mapping_source` (`tenant_id`, `source_system`, `source_key`),
  UNIQUE KEY `uk_ast_product_type_mapping_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_ast_product_type_mapping_tenant_id_target` (`tenant_id`, `id`, `product_type_id`),
  KEY `idx_ast_product_type_mapping_target` (`tenant_id`, `product_type_id`, `id`),
  KEY `idx_ast_product_type_mapping_status` (`tenant_id`, `mapping_status`, `source_updated_at`, `id`),
  CONSTRAINT `fk_ast_product_type_mapping_target`
    FOREIGN KEY (`tenant_id`, `product_type_id`) REFERENCES `ast_product_type` (`tenant_id`, `id`),
  CONSTRAINT `chk_ast_product_type_mapping_status`
    CHECK (`mapping_status` IN ('RESOLVED', 'CONFLICT', 'UNRESOLVED')),
  CONSTRAINT `chk_ast_product_type_mapping_target`
    CHECK ((`mapping_status` = 'RESOLVED' AND `product_type_id` IS NOT NULL)
      OR (`mapping_status` = 'UNRESOLVED' AND `product_type_id` IS NULL)
      OR `mapping_status` = 'CONFLICT'),
  CONSTRAINT `chk_ast_product_type_mapping_conflict_evidence`
    CHECK ((`mapping_status` = 'CONFLICT'
        AND `conflict_product_type_code` IS NOT NULL
        AND `conflict_source_version` IS NOT NULL
        AND `conflict_source_updated_at` IS NOT NULL
        AND `conflict_payload_hash` IS NOT NULL)
      OR (`mapping_status` <> 'CONFLICT'
        AND `conflict_product_type_code` IS NULL
        AND `conflict_source_version` IS NULL
        AND `conflict_source_updated_at` IS NULL
        AND `conflict_payload_hash` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ast_device_current_product_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL,
  `product_type_id` bigint DEFAULT NULL,
  `product_type_code` varchar(64) DEFAULT NULL,
  `source_mapping_id` bigint DEFAULT NULL,
  `resolution_status` varchar(32) NOT NULL,
  `source_version` varchar(128) NOT NULL,
  `source_updated_at` datetime(3) NOT NULL,
  `effective_from` datetime(3) NOT NULL,
  `effective_to` datetime(3) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `current_marker` tinyint GENERATED ALWAYS AS (
    CASE WHEN `effective_to` IS NULL AND `deleted` = b'0' THEN 1 ELSE NULL END
  ) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_product_type_current` (`tenant_id`, `device_id`, `current_marker`),
  UNIQUE KEY `uk_ast_device_product_type_tenant_id` (`tenant_id`, `id`),
  KEY `idx_ast_device_product_type_target` (`tenant_id`, `product_type_id`, `device_id`, `id`),
  KEY `idx_ast_device_product_type_mapping` (`tenant_id`, `source_mapping_id`, `device_id`, `id`),
  CONSTRAINT `fk_ast_device_product_type_target`
    FOREIGN KEY (`tenant_id`, `product_type_id`, `product_type_code`)
    REFERENCES `ast_product_type` (`tenant_id`, `id`, `type_code`),
  CONSTRAINT `fk_ast_device_product_type_mapping`
    FOREIGN KEY (`tenant_id`, `source_mapping_id`)
    REFERENCES `ast_product_type_source_mapping` (`tenant_id`, `id`),
  CONSTRAINT `fk_ast_device_product_type_mapping_target`
    FOREIGN KEY (`tenant_id`, `source_mapping_id`, `product_type_id`)
    REFERENCES `ast_product_type_source_mapping` (`tenant_id`, `id`, `product_type_id`),
  CONSTRAINT `chk_ast_device_product_type_status`
    CHECK (`resolution_status` IN ('RESOLVED', 'UNKNOWN', 'CONFLICT', 'UNRESOLVED')),
  CONSTRAINT `chk_ast_device_product_type_effective`
    CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`),
  CONSTRAINT `chk_ast_device_product_type_resolution`
    CHECK ((`resolution_status` = 'RESOLVED'
      AND `product_type_id` IS NOT NULL
      AND `product_type_code` IS NOT NULL
      AND `source_mapping_id` IS NOT NULL)
      OR (`resolution_status` <> 'RESOLVED'
        AND `product_type_id` IS NULL
        AND `product_type_code` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
