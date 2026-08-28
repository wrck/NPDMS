-- =============================================================================
-- F-PLT-001 / PLT-02：统一文件身份、不可变版本与固定版本业务引用。
-- PLATFORM 持有业务真值；infra_file_id 仅为技术存储回执，不建立跨模块外键。
-- =============================================================================

CREATE TABLE `plt_file_artifact` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件Artifact稳定ID',
    `name` VARCHAR(256) NOT NULL COMMENT '文件显示名称',
    `category_code` VARCHAR(64) NOT NULL COMMENT '文件类别编码',
    `owner_context` VARCHAR(32) NOT NULL COMMENT '业务Owner Context',
    `lifecycle_status_code` VARCHAR(32) NOT NULL COMMENT 'Artifact生命周期',
    `invalid_reason_code` VARCHAR(64) NULL COMMENT '失效原因编码',
    `invalid_reason_detail` VARCHAR(1000) NULL COMMENT '失效原因说明',
    `invalidated_at` DATETIME(3) NULL COMMENT '失效时间',
    `invalidated_by` BIGINT NULL COMMENT '失效操作人',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_file_artifact_tenant_row` (`tenant_id`, `id`),
    KEY `idx_plt_file_artifact_owner_category`
        (`tenant_id`, `owner_context`, `category_code`, `id`),
    KEY `idx_plt_file_artifact_creator`
        (`tenant_id`, `creator`, `create_time`, `id`),
    CONSTRAINT `chk_plt_file_artifact_lifecycle`
        CHECK (`lifecycle_status_code` IN ('DRAFT', 'ACTIVE', 'INVALIDATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PLATFORM文件Artifact稳定身份';

CREATE TABLE `plt_file_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件版本记录ID',
    `artifact_id` BIGINT NOT NULL COMMENT '文件Artifact稳定ID',
    `version_no` INT UNSIGNED NOT NULL COMMENT 'Artifact内版本号',
    `infra_file_id` BIGINT NOT NULL COMMENT 'INFRA技术文件记录ID',
    `availability_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '可用性事实版本',
    `sha256` CHAR(64) NOT NULL COMMENT '服务端计算的SHA-256',
    `size_bytes` BIGINT UNSIGNED NOT NULL COMMENT '文件大小（字节）',
    `declared_media_type` VARCHAR(128) NOT NULL COMMENT '声明媒体类型',
    `detected_media_type` VARCHAR(128) NOT NULL COMMENT '服务端检测媒体类型',
    `scan_status_code` VARCHAR(32) NOT NULL COMMENT '安全扫描状态',
    `scan_provider_code` VARCHAR(64) NULL COMMENT '扫描Provider编码',
    `scan_provider_version` VARCHAR(64) NULL COMMENT '扫描Provider版本',
    `availability_status_code` VARCHAR(32) NOT NULL COMMENT '版本可用性状态',
    `unavailable_reason_code` VARCHAR(64) NULL COMMENT '不可用原因编码',
    `unavailable_at` DATETIME(3) NULL COMMENT '不可用时间',
    `version_note` VARCHAR(1000) NULL COMMENT '版本说明',
    `created_by` BIGINT NOT NULL COMMENT '创建人用户ID',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_file_version_artifact_no`
        (`tenant_id`, `artifact_id`, `version_no`),
    UNIQUE KEY `uk_plt_file_version_infra_file` (`infra_file_id`),
    UNIQUE KEY `uk_plt_file_version_tenant_row` (`tenant_id`, `id`),
    KEY `idx_plt_file_version_created`
        (`tenant_id`, `artifact_id`, `created_at`, `id`),
    KEY `idx_plt_file_version_availability`
        (`tenant_id`, `availability_status_code`, `id`),
    CONSTRAINT `fk_plt_file_version_artifact`
        FOREIGN KEY (`tenant_id`, `artifact_id`)
        REFERENCES `plt_file_artifact` (`tenant_id`, `id`),
    CONSTRAINT `chk_plt_file_version_scan`
        CHECK (`scan_status_code` = 'PASSED'),
    CONSTRAINT `chk_plt_file_version_availability`
        CHECK (`availability_status_code` IN ('AVAILABLE', 'UNAVAILABLE', 'INVALIDATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PLATFORM不可变文件版本';

CREATE TABLE `plt_file_reference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件业务引用ID',
    `owner_context` VARCHAR(32) NOT NULL COMMENT '业务Owner Context',
    `object_type` VARCHAR(64) NOT NULL COMMENT '业务对象类型',
    `object_id` VARCHAR(128) NOT NULL COMMENT '业务对象稳定ID',
    `purpose_code` VARCHAR(64) NOT NULL COMMENT '业务用途编码',
    `reference_key` VARCHAR(128) NOT NULL COMMENT '用途内稳定引用槽位',
    `artifact_id` BIGINT NOT NULL COMMENT '文件Artifact稳定ID',
    `file_version_no` INT UNSIGNED NOT NULL COMMENT '冻结文件版本号',
    `sensitivity_code` VARCHAR(32) NOT NULL COMMENT '敏感级别编码',
    `status_code` VARCHAR(32) NOT NULL COMMENT '引用生命周期状态',
    `scope_version` BIGINT UNSIGNED NOT NULL COMMENT '业务范围事实版本',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `detached_at` DATETIME(3) NULL COMMENT '解绑时间',
    `detached_by` BIGINT NULL COMMENT '解绑操作人',
    `detached_reason` VARCHAR(1000) NULL COMMENT '解绑原因',
    `archived_at` DATETIME(3) NULL COMMENT '归档时间',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_file_reference_slot`
        (`tenant_id`, `owner_context`, `object_type`, `object_id`, `purpose_code`, `reference_key`),
    UNIQUE KEY `uk_plt_file_reference_tenant_row` (`tenant_id`, `id`),
    KEY `idx_plt_file_reference_artifact`
        (`tenant_id`, `artifact_id`, `file_version_no`, `status_code`, `id`),
    KEY `idx_plt_file_reference_object`
        (`tenant_id`, `owner_context`, `object_type`, `object_id`, `status_code`, `id`),
    CONSTRAINT `fk_plt_file_reference_artifact`
        FOREIGN KEY (`tenant_id`, `artifact_id`)
        REFERENCES `plt_file_artifact` (`tenant_id`, `id`),
    CONSTRAINT `fk_plt_file_reference_version`
        FOREIGN KEY (`tenant_id`, `artifact_id`, `file_version_no`)
        REFERENCES `plt_file_version` (`tenant_id`, `artifact_id`, `version_no`),
    CONSTRAINT `chk_plt_file_reference_status`
        CHECK (`status_code` IN ('ACTIVE', 'DETACHED', 'ARCHIVED')),
    CONSTRAINT `chk_plt_file_reference_key`
        CHECK (CHAR_LENGTH(TRIM(`reference_key`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PLATFORM业务对象固定版本引用';

CREATE TABLE `plt_file_upload_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '上传会话ID',
    `mode_code` VARCHAR(32) NOT NULL COMMENT '上传模式',
    `owner_context` VARCHAR(32) NOT NULL COMMENT '业务Owner Context',
    `object_type` VARCHAR(64) NOT NULL COMMENT '业务对象类型',
    `object_id` VARCHAR(128) NOT NULL COMMENT '业务对象稳定ID',
    `purpose_code` VARCHAR(64) NOT NULL COMMENT '业务用途编码',
    `reference_key` VARCHAR(128) NOT NULL COMMENT '用途内稳定引用槽位',
    `file_name` VARCHAR(256) NOT NULL COMMENT '文件名称',
    `category_code` VARCHAR(64) NOT NULL COMMENT '文件类别编码',
    `declared_size_bytes` BIGINT UNSIGNED NOT NULL COMMENT '客户端声明大小',
    `declared_media_type` VARCHAR(128) NOT NULL COMMENT '客户端声明媒体类型',
    `storage_operation_id` VARCHAR(64) NOT NULL COMMENT '技术存储幂等操作ID',
    `status_code` VARCHAR(32) NOT NULL COMMENT '上传会话状态',
    `scope_version` BIGINT UNSIGNED NOT NULL COMMENT '业务范围事实版本',
    `expires_at` DATETIME(3) NOT NULL COMMENT '会话失效时间',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `artifact_id` BIGINT NULL COMMENT '目标Artifact ID',
    `reference_id` BIGINT NULL COMMENT '目标引用ID',
    `expected_reference_version` INT UNSIGNED NULL COMMENT '期望引用版本',
    `client_sha256` CHAR(64) NULL COMMENT '客户端摘要',
    `actual_sha256` CHAR(64) NULL COMMENT '服务端摘要',
    `completed_file_version_no` INT UNSIGNED NULL COMMENT '完成的文件版本号',
    `registered_infra_file_id` BIGINT NULL COMMENT '已登记INFRA技术文件ID',
    `failure_code` VARCHAR(64) NULL COMMENT '失败码',
    `failure_detail` VARCHAR(1000) NULL COMMENT '失败说明',
    `completed_at` DATETIME(3) NULL COMMENT '完成时间',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_file_upload_storage_operation`
        (`tenant_id`, `storage_operation_id`),
    UNIQUE KEY `uk_plt_file_upload_session_tenant_row` (`tenant_id`, `id`),
    KEY `idx_plt_file_upload_session_expiry`
        (`tenant_id`, `status_code`, `expires_at`, `id`),
    KEY `idx_plt_file_upload_session_object`
        (`tenant_id`, `owner_context`, `object_type`, `object_id`, `create_time`, `id`),
    CONSTRAINT `chk_plt_file_upload_session_mode`
        CHECK (`mode_code` IN ('CREATE_ARTIFACT', 'ADD_VERSION')),
    CONSTRAINT `chk_plt_file_upload_session_status`
        CHECK (`status_code` IN
            ('INITIALIZED', 'VALIDATING', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_FINAL', 'EXPIRED')),
    CONSTRAINT `chk_plt_file_upload_reference_key`
        CHECK (CHAR_LENGTH(TRIM(`reference_key`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PLATFORM文件上传会话';

CREATE TABLE `plt_file_access_grant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '短时访问授权ID',
    `artifact_id` BIGINT NOT NULL COMMENT '文件Artifact稳定ID',
    `file_version_no` INT UNSIGNED NOT NULL COMMENT '文件版本号',
    `subject_user_id` BIGINT NOT NULL COMMENT '授权用户ID',
    `operation_code` VARCHAR(32) NOT NULL COMMENT '授权操作',
    `business_scope_hash` CHAR(64) NOT NULL COMMENT '业务范围事实摘要',
    `token_digest` CHAR(64) NOT NULL COMMENT '不可逆令牌摘要',
    `status_code` VARCHAR(32) NOT NULL COMMENT '授权状态',
    `expires_at` DATETIME(3) NOT NULL COMMENT '授权失效时间',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `consumed_at` DATETIME(3) NULL COMMENT '消费时间',
    `revoked_at` DATETIME(3) NULL COMMENT '撤销时间',
    `revoked_by` BIGINT NULL COMMENT '撤销操作人',
    `revoke_reason` VARCHAR(1000) NULL COMMENT '撤销原因',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_file_access_token_digest` (`token_digest`),
    UNIQUE KEY `uk_plt_file_access_grant_tenant_row` (`tenant_id`, `id`),
    KEY `idx_plt_file_access_grant_subject`
        (`tenant_id`, `subject_user_id`, `status_code`, `expires_at`, `id`),
    KEY `idx_plt_file_access_grant_artifact`
        (`tenant_id`, `artifact_id`, `file_version_no`, `status_code`, `id`),
    CONSTRAINT `fk_plt_file_access_grant_version`
        FOREIGN KEY (`tenant_id`, `artifact_id`, `file_version_no`)
        REFERENCES `plt_file_version` (`tenant_id`, `artifact_id`, `version_no`),
    CONSTRAINT `chk_plt_file_access_grant_operation`
        CHECK (`operation_code` IN ('DOWNLOAD', 'PREVIEW')),
    CONSTRAINT `chk_plt_file_access_grant_status`
        CHECK (`status_code` IN ('ACTIVE', 'CONSUMED', 'REVOKED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PLATFORM文件短时访问授权';

CREATE TABLE `plt_file_archive_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件归档事实ID',
    `artifact_id` BIGINT NOT NULL COMMENT '文件Artifact稳定ID',
    `file_version_no` INT UNSIGNED NOT NULL COMMENT '文件版本号',
    `archive_batch_id` VARCHAR(128) NOT NULL COMMENT '归档批次ID',
    `business_decision_ref` VARCHAR(256) NOT NULL COMMENT '业务决定稳定引用',
    `archived_by` BIGINT NOT NULL COMMENT '归档操作人',
    `archived_at` DATETIME(3) NOT NULL COMMENT '归档时间',
    `archive_note` VARCHAR(1000) NULL COMMENT '归档说明',
    `created_at` DATETIME(3) NOT NULL COMMENT '记录创建时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_file_archive_fact`
        (`tenant_id`, `archive_batch_id`, `artifact_id`, `file_version_no`),
    UNIQUE KEY `uk_plt_file_archive_record_tenant_row` (`tenant_id`, `id`),
    KEY `idx_plt_file_archive_record_artifact`
        (`tenant_id`, `artifact_id`, `file_version_no`, `archived_at`, `id`),
    CONSTRAINT `fk_plt_file_archive_record_version`
        FOREIGN KEY (`tenant_id`, `artifact_id`, `file_version_no`)
        REFERENCES `plt_file_version` (`tenant_id`, `artifact_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PLATFORM文件版本追加归档事实';
