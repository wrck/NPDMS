-- F-INT-012 / INT-12：设备凭证、采集任务、Platform回调事实与消费确认。
-- 本迁移只创建PLT物理Owner表；不创建第二套文件表，不创建尚未完成的INT回调接入与对账表。

CREATE TABLE `plt_device_credential` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `credential_code` VARCHAR(64) NOT NULL,
    `credential_type` VARCHAR(32) NOT NULL,
    `username` VARCHAR(128) NOT NULL,
    `encrypted_secret` TEXT NULL,
    `kms_reference` VARCHAR(512) NULL,
    `credential_version` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `creator` VARCHAR(64) DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_credential_code` (`tenant_id`, `credential_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='INT-12设备凭证';

CREATE TABLE `plt_credential_grant` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `credential_id` BIGINT NOT NULL,
    `grantee_type` VARCHAR(32) NOT NULL,
    `grantee_id` VARCHAR(64) NOT NULL,
    `project_id` VARCHAR(64) NULL,
    `device_id` VARCHAR(64) NULL,
    `protocol` VARCHAR(16) NULL,
    `command_template_id` VARCHAR(64) NULL,
    `expires_at` DATETIME NULL,
    `status` VARCHAR(32) NOT NULL,
    `creator` VARCHAR(64) DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_plt_grant_credential` (`tenant_id`, `credential_id`, `status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='INT-12凭证显式授权';

CREATE TABLE `plt_collection_batch` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `batch_no` VARCHAR(64) NOT NULL,
    `source_context` VARCHAR(32) NOT NULL,
    `source_object_type` VARCHAR(64) NOT NULL,
    `source_object_id` VARCHAR(64) NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `task_count` INT NOT NULL,
    `success_count` INT NOT NULL DEFAULT 0,
    `failure_count` INT NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_batch_idempotency` (`tenant_id`, `idempotency_key`),
    UNIQUE KEY `uk_plt_batch_no` (`tenant_id`, `batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='INT-12采集批次';

CREATE TABLE `plt_collection_task` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `batch_id` BIGINT NOT NULL,
    `platform_task_id` VARCHAR(64) NOT NULL,
    `source_context` VARCHAR(32) NOT NULL,
    `source_object_type` VARCHAR(64) NOT NULL,
    `source_object_id` VARCHAR(64) NOT NULL,
    `project_id` VARCHAR(64) NULL,
    `device_id` VARCHAR(64) NOT NULL,
    `device_name` VARCHAR(128) NOT NULL,
    `host` VARCHAR(255) NOT NULL,
    `port` INT NOT NULL,
    `protocol` VARCHAR(16) NOT NULL,
    `template_id` VARCHAR(64) NOT NULL,
    `template_version` VARCHAR(64) NOT NULL,
    `template_hash` CHAR(64) NOT NULL,
    `credential_mode` VARCHAR(32) NOT NULL,
    `credential_id` BIGINT NULL,
    `grant_snapshot_id` BIGINT NULL,
    `temporary_username` VARCHAR(128) NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `completion_mode` VARCHAR(32) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `technical_stage` VARCHAR(32) NOT NULL,
    `external_task_id` VARCHAR(128) NULL,
    `external_status` VARCHAR(64) NULL,
    `result_version` BIGINT NULL,
    `file_version_id` BIGINT NULL,
    `quarantine_evidence_id` VARCHAR(128) NULL,
    `failure_category` VARCHAR(64) NULL,
    `consumer_context` VARCHAR(32) NULL,
    `consumer_object_type` VARCHAR(64) NULL,
    `consumer_object_id` VARCHAR(64) NULL,
    `consumed_result_version` BIGINT NULL,
    `last_callback_sequence` BIGINT NULL,
    `creator` VARCHAR(64) DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_collection_task_id` (`tenant_id`, `platform_task_id`),
    UNIQUE KEY `uk_plt_collection_idempotency` (`tenant_id`, `idempotency_key`),
    KEY `idx_plt_collection_batch` (`tenant_id`, `batch_id`),
    KEY `idx_plt_collection_external_task` (`tenant_id`, `external_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='INT-12逐设备采集任务';

CREATE TABLE `plt_collection_callback_record` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `platform_task_id` VARCHAR(64) NOT NULL,
    `callback_id` VARCHAR(128) NOT NULL,
    `receipt_id` BIGINT NOT NULL,
    `sequence_no` BIGINT NOT NULL,
    `external_task_id` VARCHAR(128) NOT NULL,
    `external_status` VARCHAR(64) NOT NULL,
    `mapped_status` VARCHAR(32) NOT NULL,
    `result_version` BIGINT NOT NULL,
    `file_version_id` BIGINT NULL,
    `quarantine_evidence_id` VARCHAR(128) NULL,
    `failure_category` VARCHAR(64) NULL,
    `processing_result` VARCHAR(32) NOT NULL,
    `started_at` DATETIME(3) NULL,
    `completed_at` DATETIME(3) NULL,
    `trace_id` VARCHAR(128) NULL,
    `creator` VARCHAR(64) DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_callback_id` (`tenant_id`, `callback_id`),
    UNIQUE KEY `uk_plt_callback_sequence` (`tenant_id`, `platform_task_id`, `sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='INT-12 Platform不可变回调事实';

CREATE TABLE `plt_collection_result_consumption` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `platform_task_id` VARCHAR(64) NOT NULL,
    `consumer_context` VARCHAR(32) NOT NULL,
    `consumer_object_type` VARCHAR(64) NOT NULL,
    `consumer_object_id` VARCHAR(64) NOT NULL,
    `result_version` BIGINT NOT NULL,
    `consumption_result` VARCHAR(32) NOT NULL,
    `consumed_at` DATETIME(3) NOT NULL,
    `trace_id` VARCHAR(128) NULL,
    `creator` VARCHAR(64) DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_collection_consumption` (
        `tenant_id`,
        `platform_task_id`,
        `consumer_context`,
        `consumer_object_type`,
        `consumer_object_id`,
        `result_version`
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='INT-12业务结果消费确认';
