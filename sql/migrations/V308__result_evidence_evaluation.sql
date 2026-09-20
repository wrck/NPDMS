-- Append-only result evidence items; finalized scans cannot be changed through their Mapper.
CREATE TABLE proj_result_evidence_scan (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    subscription_version INT NOT NULL,
    through_sequence BIGINT NOT NULL,
    after_candidate_id BIGINT NOT NULL,
    accumulator LONGTEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    version INT NOT NULL,
    UNIQUE KEY uk_evidence_scan (tenant_id,subscription_id,subscription_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE proj_result_evidence_item (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    scan_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL,
    object_id VARCHAR(128) NOT NULL,
    result_id VARCHAR(128) NOT NULL,
    formation_sequence BIGINT NULL,
    eligibility VARCHAR(24) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    observation LONGTEXT NOT NULL,
    UNIQUE KEY uk_evidence_candidate (tenant_id,scan_id,candidate_id),
    KEY ix_evidence_item_page (tenant_id,project_id,scan_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
