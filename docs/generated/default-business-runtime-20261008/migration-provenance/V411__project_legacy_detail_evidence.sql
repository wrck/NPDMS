-- Project-owned, read-only DPPMS evidence. No lifecycle, member, approval, file or delivery writes.
-- V400 is already used by the domain-test migration lineage; this is a new forward migration.
CREATE TABLE IF NOT EXISTS proj_legacy_detail_source (
  id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
  source_system VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  source_project_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  source_contract_no VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  current_snapshot_id BIGINT NULL, version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_legacy_detail_identity(tenant_id,source_system,source_project_key),
  KEY idx_legacy_detail_project(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS proj_legacy_detail_snapshot (
  id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, source_id BIGINT NOT NULL,
  batch_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL, checksum CHAR(64) NOT NULL,
  source_read_at DATETIME(3) NOT NULL, captured_at DATETIME(3) NOT NULL,
  operator_user_id BIGINT NOT NULL, domain_counts_json LONGTEXT NOT NULL, record_count INT NOT NULL,
  UNIQUE KEY uk_legacy_detail_batch(tenant_id,source_id,batch_key),
  KEY idx_legacy_detail_snapshot_source(tenant_id,source_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS proj_legacy_detail_record (
  id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, snapshot_id BIGINT NOT NULL,
  domain_code VARCHAR(48) COLLATE utf8mb4_bin NOT NULL,
  source_table VARCHAR(96) COLLATE utf8mb4_bin NOT NULL,
  source_key VARCHAR(191) COLLATE utf8mb4_bin NOT NULL,
  parent_domain VARCHAR(48) COLLATE utf8mb4_bin NULL,
  parent_source_key VARCHAR(191) COLLATE utf8mb4_bin NULL,
  source_updated_at VARCHAR(64) NULL, checksum CHAR(64) NOT NULL,
  payload_json LONGTEXT NOT NULL, redacted_fields_json TEXT NOT NULL,
  UNIQUE KEY uk_legacy_detail_record(tenant_id,snapshot_id,domain_code,source_key),
  KEY idx_legacy_detail_parent(tenant_id,snapshot_id,parent_domain,parent_source_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
