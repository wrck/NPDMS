-- Approved project-template upgrade: references only, preserve every closed interval.
CREATE TABLE proj_task_business_link (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    execution_contract_id BIGINT NOT NULL,
    contract_version INT NOT NULL,
    owner_context VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    object_type VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    object_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    fact_version VARCHAR(256) NOT NULL,
    linked_by BIGINT NOT NULL,
    linked_at DATETIME(6) NOT NULL,
    unlinked_by BIGINT NULL,
    unlinked_at DATETIME(6) NULL,
    active_marker TINYINT GENERATED ALWAYS AS (IF(unlinked_at IS NULL, 1, NULL)) STORED,
    version INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_business_active (tenant_id, task_id, owner_context, object_type, object_id, active_marker),
    KEY idx_task_business_history (tenant_id, project_id, task_id, linked_at, id),
    CONSTRAINT ck_task_business_interval CHECK (
        (unlinked_at IS NULL AND unlinked_by IS NULL) OR
        (unlinked_at IS NOT NULL AND unlinked_by IS NOT NULL AND unlinked_at >= linked_at)),
    CONSTRAINT ck_task_business_versions CHECK (contract_version > 0 AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Task to Owner business object relationship history';
