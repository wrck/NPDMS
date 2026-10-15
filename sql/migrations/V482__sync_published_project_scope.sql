-- INT-owned immutable membership. Only a complete successful P16 run gets a publication row.
-- Failed/incomplete run members remain evidence and are never visible through the publication query.
CREATE TABLE int_sync_project_scope_publication (
  tenant_id BIGINT NOT NULL,
  source_connection_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  published_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id,run_id),
  KEY idx_scope_publication_latest (tenant_id,source_connection_id,published_at,run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE int_sync_project_scope_member (
  tenant_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  source_connection_id BIGINT NOT NULL,
  source_project_key VARCHAR(128) NOT NULL,
  project_id BIGINT NOT NULL,
  project_type VARCHAR(32) NOT NULL,
  original_project_code VARCHAR(255) NOT NULL,
  project_code VARCHAR(255) NOT NULL,
  PRIMARY KEY (tenant_id,run_id,source_project_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
