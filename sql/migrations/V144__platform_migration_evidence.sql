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
