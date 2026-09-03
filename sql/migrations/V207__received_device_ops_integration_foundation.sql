-- Selectively received from prereq-parallel-check-kKiAdn V104.
-- Only the independent INT edge/audit tables absent from master V203 are retained.
-- PLT collection tables and the second Infra file Owner are intentionally not duplicated.
-- Feature F-INT-012 remains IN_PROGRESS.

CREATE TABLE IF NOT EXISTS int_device_ops_dispatch_attempt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    platform_task_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    attempt_no INT NOT NULL,
    request_digest CHAR(64) NOT NULL,
    endpoint VARCHAR(512) NOT NULL,
    duration_ms BIGINT NULL,
    http_status INT NULL,
    external_error VARCHAR(512) NULL,
    external_task_id VARCHAR(128) NULL,
    retry_reason VARCHAR(256) NULL,
    trace_id VARCHAR(128) NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_int_dispatch_attempt (tenant_id, platform_task_id, operation_type, attempt_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS int_device_ops_callback_receipt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    callback_id VARCHAR(128) NOT NULL,
    manifest_digest CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retryable BIT NOT NULL DEFAULT 0,
    file_version_id BIGINT NULL,
    quarantine_evidence_id VARCHAR(128) NULL,
    error_code VARCHAR(64) NULL,
    platform_task_id VARCHAR(64) NOT NULL,
    platform_submission_status VARCHAR(32) NULL,
    trace_id VARCHAR(128) NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_int_callback_receipt (tenant_id, provider_code, callback_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS int_device_ops_reconcile_batch (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    scanned_count INT NOT NULL DEFAULT 0,
    repaired_count INT NOT NULL DEFAULT 0,
    conflict_count INT NOT NULL DEFAULT 0,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_int_reconcile_batch (tenant_id, batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
