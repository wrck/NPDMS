-- F-IMP-002 / EXE-01: arrival acceptance and receipt evidence owner schema.
-- Legacy reconciliation is application-level and intentionally absent here.

CREATE TABLE `imp_delivery_evidence` (
  `id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `source_requirement` varchar(32) NOT NULL,
  `source_object_type` varchar(64) NOT NULL,
  `source_object_id` bigint NOT NULL,
  `current_revision_no` int NOT NULL,
  `acc_sync_status` varchar(40) NOT NULL DEFAULT 'NOT_PUBLISHED',
  `acc_last_published_at` datetime DEFAULT NULL,
  `acc_next_retry_at` datetime DEFAULT NULL,
  `acc_retry_count` int NOT NULL DEFAULT 0,
  `acc_last_event_id` varchar(128) DEFAULT NULL,
  `acc_accepted_record_id` varchar(128) DEFAULT NULL,
  `acc_archived_record_id` varchar(128) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_imp_delivery_evidence_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_imp_delivery_evidence_source`
    (`tenant_id`, `source_requirement`, `source_object_type`, `source_object_id`),
  KEY `idx_imp_delivery_evidence_project`
    (`tenant_id`, `project_id`, `source_requirement`, `acc_sync_status`, `id`),
  KEY `idx_imp_delivery_evidence_retry`
    (`tenant_id`, `acc_sync_status`, `acc_next_retry_at`, `id`),
  CONSTRAINT `chk_imp_delivery_evidence_source` CHECK (
    `source_requirement` = 'EXE-01'
    AND `source_object_type` = 'ARRIVAL_ACCEPTANCE'
  ),
  CONSTRAINT `chk_imp_delivery_evidence_sync_status` CHECK (`acc_sync_status` IN (
    'NOT_PUBLISHED', 'PUBLISHED_PENDING_ACC', 'ARCHIVE_PENDING_RETRY',
    'ACCEPTED_PENDING_ARCHIVE', 'ARCHIVE_ACK_PENDING_RETRY', 'ARCHIVED'
  )),
  CONSTRAINT `chk_imp_delivery_evidence_retry_count` CHECK (`acc_retry_count` >= 0),
  CONSTRAINT `chk_imp_delivery_evidence_revision` CHECK (`current_revision_no` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='EXE-01到货签收证据根及ACC同步投影';

CREATE TABLE `imp_delivery_evidence_revision` (
  `id` bigint NOT NULL,
  `evidence_id` bigint NOT NULL,
  `revision_no` int NOT NULL,
  `file_reference_id` varchar(128) NOT NULL,
  `file_version_no` int NOT NULL,
  `file_hash` char(64) NOT NULL,
  `source_record_id` bigint NOT NULL,
  `source_version` bigint NOT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_imp_delivery_evidence_revision_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_imp_delivery_evidence_revision`
    (`tenant_id`, `evidence_id`, `revision_no`),
  KEY `idx_imp_delivery_evidence_file`
    (`tenant_id`, `file_reference_id`, `file_version_no`, `id`),
  CONSTRAINT `fk_imp_delivery_evidence_revision_root`
    FOREIGN KEY (`tenant_id`, `evidence_id`)
    REFERENCES `imp_delivery_evidence` (`tenant_id`, `id`),
  CONSTRAINT `chk_imp_delivery_evidence_revision_no` CHECK (`revision_no` > 0),
  CONSTRAINT `chk_imp_delivery_evidence_file_version` CHECK (`file_version_no` > 0),
  CONSTRAINT `chk_imp_delivery_evidence_source_version` CHECK (`source_version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='EXE-01到货签收不可变证据修订';

CREATE TABLE `imp_arrival_acceptance` (
  `id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `batch_code` varchar(64) NOT NULL,
  `logistics_no` varchar(128) NOT NULL,
  `arrived_at` datetime NOT NULL,
  `signer_snapshot` json NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `delivery_scope_version` bigint NOT NULL,
  `expected_scope_snapshot` json NOT NULL,
  `scope_watermark` json NOT NULL,
  `migration_resolution_status` varchar(40) NOT NULL DEFAULT 'NOT_APPLICABLE',
  `migration_reason_code` varchar(64) DEFAULT NULL,
  `legacy_source_id` bigint DEFAULT NULL,
  `project_fact_version` bigint DEFAULT NULL,
  `evidence_id` bigint DEFAULT NULL,
  `evidence_revision` int DEFAULT NULL,
  `predecessor_acceptance_id` bigint DEFAULT NULL,
  `submitted_by` bigint DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `confirmed_by` bigint DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_imp_arrival_acceptance_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_imp_arrival_batch` (`tenant_id`, `project_id`, `batch_code`),
  UNIQUE KEY `uk_imp_arrival_project_fact`
    (`tenant_id`, `project_id`, `project_fact_version`),
  UNIQUE KEY `uk_imp_arrival_legacy_source` (`tenant_id`, `legacy_source_id`),
  KEY `idx_imp_arrival_project_status`
    (`tenant_id`, `project_id`, `status`, `arrived_at`, `id`),
  KEY `idx_imp_arrival_evidence` (`tenant_id`, `evidence_id`, `evidence_revision`),
  KEY `idx_imp_arrival_predecessor` (`tenant_id`, `predecessor_acceptance_id`),
  CONSTRAINT `fk_imp_arrival_evidence_revision`
    FOREIGN KEY (`tenant_id`, `evidence_id`, `evidence_revision`)
    REFERENCES `imp_delivery_evidence_revision` (`tenant_id`, `evidence_id`, `revision_no`),
  CONSTRAINT `fk_imp_arrival_predecessor`
    FOREIGN KEY (`tenant_id`, `predecessor_acceptance_id`)
    REFERENCES `imp_arrival_acceptance` (`tenant_id`, `id`),
  CONSTRAINT `chk_imp_arrival_status` CHECK (`status` IN (
    'DRAFT', 'PARTIALLY_ACCEPTED', 'DIFFERENCE_PENDING', 'ACCEPTED', 'CONFIRMED'
  )),
  CONSTRAINT `chk_imp_arrival_scope_version` CHECK (`delivery_scope_version` >= 0),
  CONSTRAINT `chk_imp_arrival_project_fact_version` CHECK (
    `project_fact_version` IS NULL OR `project_fact_version` >= 0
  ),
  CONSTRAINT `chk_imp_arrival_evidence_pair` CHECK (
    (`evidence_id` IS NULL AND `evidence_revision` IS NULL)
    OR (`evidence_id` IS NOT NULL AND `evidence_revision` > 0)
  ),
  CONSTRAINT `chk_imp_arrival_migration_status` CHECK (`migration_resolution_status` IN (
    'NOT_APPLICABLE', 'PENDING_RECONCILIATION', 'RECONCILED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='EXE-01到货签收批次根';

CREATE TABLE `imp_arrival_line` (
  `id` bigint NOT NULL,
  `arrival_acceptance_id` bigint NOT NULL,
  `line_no` int NOT NULL,
  `line_revision` int NOT NULL,
  `scope_type` varchar(32) NOT NULL,
  `device_id` bigint DEFAULT NULL,
  `device_assignment_version` bigint DEFAULT NULL,
  `order_line_id` bigint DEFAULT NULL,
  `product_code` varchar(128) DEFAULT NULL,
  `model_code` varchar(128) DEFAULT NULL,
  `expected_quantity` decimal(20,6) NOT NULL,
  `accepted_quantity` decimal(20,6) NOT NULL DEFAULT 0,
  `unit` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'NOT_ARRIVED',
  `current_marker` tinyint DEFAULT 1,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_imp_arrival_line_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_imp_arrival_line_revision`
    (`tenant_id`, `arrival_acceptance_id`, `line_no`, `line_revision`),
  UNIQUE KEY `uk_imp_arrival_line_current`
    (`tenant_id`, `arrival_acceptance_id`, `line_no`, `current_marker`),
  KEY `idx_imp_arrival_line_device`
    (`tenant_id`, `device_id`, `status`, `arrival_acceptance_id`, `id`),
  KEY `idx_imp_arrival_line_order`
    (`tenant_id`, `order_line_id`, `model_code`, `status`, `arrival_acceptance_id`, `id`),
  CONSTRAINT `fk_imp_arrival_line_root`
    FOREIGN KEY (`tenant_id`, `arrival_acceptance_id`)
    REFERENCES `imp_arrival_acceptance` (`tenant_id`, `id`),
  CONSTRAINT `chk_imp_arrival_line_revision` CHECK (`line_no` > 0 AND `line_revision` > 0),
  CONSTRAINT `chk_imp_arrival_line_scope_type` CHECK (`scope_type` IN (
    'DEVICE', 'ORDER_MODEL_QUANTITY'
  )),
  CONSTRAINT `chk_imp_arrival_line_status` CHECK (`status` IN (
    'NOT_ARRIVED', 'ACCEPTED', 'DIFFERENCE_PENDING', 'REJECTED'
  )),
  CONSTRAINT `chk_imp_arrival_line_current` CHECK (`current_marker` IS NULL OR `current_marker` = 1),
  CONSTRAINT `chk_imp_arrival_line_quantity` CHECK (
    `expected_quantity` >= 0 AND `accepted_quantity` >= 0
    AND `accepted_quantity` <= `expected_quantity`
  ),
  CONSTRAINT `chk_imp_arrival_line_scope` CHECK (
    (`scope_type` = 'DEVICE'
      AND `device_id` IS NOT NULL AND `device_assignment_version` IS NOT NULL
      AND `order_line_id` IS NULL)
    OR (`scope_type` = 'ORDER_MODEL_QUANTITY'
      AND `device_id` IS NULL AND `order_line_id` IS NOT NULL
      AND (`product_code` IS NOT NULL OR `model_code` IS NOT NULL))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='EXE-01到货签收明细追加版本';

CREATE TABLE `imp_arrival_difference` (
  `id` bigint NOT NULL,
  `arrival_acceptance_id` bigint NOT NULL,
  `arrival_line_id` bigint NOT NULL,
  `difference_no` int NOT NULL,
  `revision_no` int NOT NULL,
  `difference_type` varchar(48) NOT NULL,
  `resolution_status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `reason` varchar(1000) NOT NULL,
  `risk_description` varchar(1000) DEFAULT NULL,
  `scope_snapshot` json NOT NULL,
  `project_fact_version` bigint NOT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `exemption_expires_at` datetime DEFAULT NULL,
  `evidence_id` bigint DEFAULT NULL,
  `evidence_revision` int DEFAULT NULL,
  `current_marker` tinyint DEFAULT 1,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_imp_arrival_difference_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_imp_arrival_difference_revision`
    (`tenant_id`, `arrival_acceptance_id`, `difference_no`, `revision_no`),
  UNIQUE KEY `uk_imp_arrival_difference_current`
    (`tenant_id`, `arrival_acceptance_id`, `difference_no`, `current_marker`),
  KEY `idx_imp_arrival_difference_line`
    (`tenant_id`, `arrival_line_id`, `resolution_status`, `id`),
  KEY `idx_imp_arrival_difference_evidence`
    (`tenant_id`, `evidence_id`, `evidence_revision`),
  CONSTRAINT `fk_imp_arrival_difference_root`
    FOREIGN KEY (`tenant_id`, `arrival_acceptance_id`)
    REFERENCES `imp_arrival_acceptance` (`tenant_id`, `id`),
  CONSTRAINT `fk_imp_arrival_difference_line`
    FOREIGN KEY (`tenant_id`, `arrival_line_id`)
    REFERENCES `imp_arrival_line` (`tenant_id`, `id`),
  CONSTRAINT `fk_imp_arrival_difference_evidence`
    FOREIGN KEY (`tenant_id`, `evidence_id`, `evidence_revision`)
    REFERENCES `imp_delivery_evidence_revision` (`tenant_id`, `evidence_id`, `revision_no`),
  CONSTRAINT `chk_imp_arrival_difference_revision` CHECK (
    `difference_no` > 0 AND `revision_no` > 0
  ),
  CONSTRAINT `chk_imp_arrival_difference_type` CHECK (`difference_type` IN (
    'QUANTITY_MISMATCH', 'MODEL_OR_SN_MISMATCH', 'APPEARANCE_OR_QUALITY',
    'EVIDENCE_INCOMPLETE'
  )),
  CONSTRAINT `chk_imp_arrival_difference_status` CHECK (`resolution_status` IN (
    'OPEN', 'SUPPLEMENTED', 'REJECTED', 'EXEMPTED', 'CLOSED'
  )),
  CONSTRAINT `chk_imp_arrival_difference_current` CHECK (
    `current_marker` IS NULL OR `current_marker` = 1
  ),
  CONSTRAINT `chk_imp_arrival_difference_fact_version` CHECK (`project_fact_version` >= 0),
  CONSTRAINT `chk_imp_arrival_difference_exemption` CHECK (
    `resolution_status` <> 'EXEMPTED'
    OR (`approved_by` IS NOT NULL AND `approved_at` IS NOT NULL
      AND `evidence_id` IS NOT NULL AND `evidence_revision` > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='EXE-01到货差异拒收与明确豁免追加版本';
