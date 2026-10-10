-- 2026-10-10 approved: one completion, one satisfaction chain, one customer signature.
-- No backfill: historical standalone grants and signed certificates retain their original evidence.
CREATE TABLE IF NOT EXISTS acc_completion_satisfaction_bundle (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    certificate_id BIGINT NOT NULL,
    certificate_version BIGINT NOT NULL,
    collection_key VARCHAR(200) NOT NULL,
    certificate_snapshot LONGTEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_completion_bundle_certificate (tenant_id, certificate_id),
    UNIQUE KEY uk_completion_bundle_collection (tenant_id, collection_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS acc_completion_satisfaction_signing (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    bundle_id BIGINT NOT NULL,
    questionnaire_id BIGINT NOT NULL,
    response_id BIGINT NOT NULL,
    grant_id BIGINT NOT NULL,
    signature_file_fact LONGTEXT NOT NULL,
    signed_at DATETIME NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_completion_signing_response (tenant_id, response_id),
    KEY idx_completion_signing_bundle (tenant_id, bundle_id, signed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE acc_satisfaction_access_grant ADD COLUMN completion_bundle_id BIGINT NULL;
