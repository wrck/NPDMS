-- Template-driven submissions retain the exact plan, intent and source evidence; no approval node.
CREATE TABLE acc_project_deliverable_submission (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    deliverable_id BIGINT NOT NULL,
    plan_version_id BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    request_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    request_payload JSON NOT NULL,
    configuration_snapshot JSON NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_evidence JSON NOT NULL,
    decision_evidence JSON NOT NULL,
    creator VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_acc_deliverable_submission_request (tenant_id, deliverable_id, request_key),
    UNIQUE KEY uk_acc_deliverable_submission_source (tenant_id, source_version_id),
    KEY idx_acc_deliverable_submission_history (tenant_id, project_id, deliverable_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板交付件提交原始证据，只追加';
