CREATE TABLE IF NOT EXISTS srv_inspection_feedback_export (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, version bigint NOT NULL DEFAULT 1,
  exported_by bigint NOT NULL, format_code varchar(8) NOT NULL, status_code varchar(24) NOT NULL,
  rule_revision_id bigint NULL, detection_id varchar(64) NULL, category_code varchar(64) NULL,
  severity_code varchar(32) NULL, product_model varchar(256) NULL, confirmation varchar(16) NULL,
  from_at datetime(3) NULL, to_at datetime(3) NULL, data_as_of datetime(3) NOT NULL,
  current_only bit NOT NULL DEFAULT b'0', include_details bit NOT NULL DEFAULT b'0', result_count int NOT NULL,
  artifact_id bigint NULL, file_version_no int NULL, reference_key varchar(200) NULL,
  file_name varchar(300) NULL, sha256 char(64) NULL, failure_message varchar(2000) NULL, generated_at datetime(3) NULL,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit NOT NULL DEFAULT b'0', PRIMARY KEY(id), UNIQUE KEY uk_inspection_export_tenant(tenant_id,id),
  KEY idx_inspection_export_actor(tenant_id,exported_by,id),
  CONSTRAINT ck_inspection_export_format CHECK(format_code IN ('XLSX','CSV')),
  CONSTRAINT ck_inspection_export_status CHECK(status_code IN ('REQUESTED','GENERATING','AVAILABLE','FAILED')),
  CONSTRAINT ck_inspection_export_available CHECK(status_code <> 'AVAILABLE' OR (artifact_id IS NOT NULL AND file_version_no > 0 AND reference_key IS NOT NULL AND sha256 IS NOT NULL AND generated_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS srv_inspection_rule_optimization (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, version bigint NOT NULL DEFAULT 1,
  source_annotation_id bigint NOT NULL, source_rule_revision_id bigint NOT NULL, draft_rule_revision_id bigint NOT NULL,
  reason varchar(2000) NOT NULL, created_by bigint NOT NULL, created_at datetime(3) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit NOT NULL DEFAULT b'0', PRIMARY KEY(id), UNIQUE KEY uk_inspection_optimization_tenant(tenant_id,id),
  UNIQUE KEY uk_inspection_optimization_draft(tenant_id,draft_rule_revision_id),
  CONSTRAINT fk_inspection_optimization_annotation FOREIGN KEY(tenant_id,source_annotation_id) REFERENCES srv_inspection_annotation(tenant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS srv_inspection_feedback_export_item (
  id bigint NOT NULL AUTO_INCREMENT, tenant_id bigint NOT NULL, version bigint NOT NULL DEFAULT 1,
  export_id bigint NOT NULL, project_id bigint NOT NULL, annotation_id bigint NOT NULL, confirmation_id bigint NULL,
  active_flag bit NOT NULL, creator varchar(64) NOT NULL DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit NOT NULL DEFAULT b'0', PRIMARY KEY(id), UNIQUE KEY uk_inspection_export_item_tenant(tenant_id,id),
  UNIQUE KEY uk_inspection_export_annotation(tenant_id,export_id,annotation_id),
  CONSTRAINT fk_inspection_export_item FOREIGN KEY(tenant_id,export_id) REFERENCES srv_inspection_feedback_export(tenant_id,id),
  CONSTRAINT fk_inspection_export_annotation FOREIGN KEY(tenant_id,annotation_id) REFERENCES srv_inspection_annotation(tenant_id,id),
  CONSTRAINT fk_inspection_export_confirmation FOREIGN KEY(tenant_id,confirmation_id) REFERENCES srv_inspection_feedback_confirmation(tenant_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
