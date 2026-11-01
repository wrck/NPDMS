-- B531 companion, for the new production initialization package only.

-- Approved source DDL only: no historical replay, business updates or seed replacement.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Source: V411__project_legacy_detail_evidence.sql

CREATE TABLE IF NOT EXISTS proj_legacy_detail_snapshot (
  id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, source_id BIGINT NOT NULL,
  batch_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL, checksum CHAR(64) NOT NULL,
  source_read_at DATETIME(3) NOT NULL, captured_at DATETIME(3) NOT NULL,
  operator_user_id BIGINT NOT NULL, domain_counts_json LONGTEXT NOT NULL, record_count INT NOT NULL,
  UNIQUE KEY uk_legacy_detail_batch(tenant_id,source_id,batch_key),
  KEY idx_legacy_detail_snapshot_source(tenant_id,source_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V411__project_legacy_detail_evidence.sql

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

-- Source: V414__dppms_native_source_business_records.sql

CREATE TABLE ast_device_warranty_service_record (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  source_version VARCHAR(128) NULL,
  source_read_at DATETIME(3) NOT NULL,
  source_checksum CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  source_id VARCHAR(64) NULL,
  renewal_contract_no VARCHAR(25) NULL,
  device_sn VARCHAR(50) NULL,
  grade VARCHAR(15) NULL,
  begin_date DATETIME(3) NULL,
  end_date DATETIME(3) NULL,
  warranty_flag VARCHAR(1) NULL,
  remark TEXT NULL,
  extension_flag INT NULL,
  source_state INT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_key,source_checksum),
  KEY idx_device (tenant_id,device_sn,deleted,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Immutable original warranty business facts';

-- Source: V414__dppms_native_source_business_records.sql

CREATE TABLE ast_device_warranty_registration (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  source_version VARCHAR(128) NULL,
  source_read_at DATETIME(3) NOT NULL,
  source_checksum CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  source_id BIGINT NOT NULL,
  package_sync_id INT NULL,
  contract_sync_id INT NULL,
  device_sn VARCHAR(25) NULL,
  office_code VARCHAR(25) NULL,
  source_project_name VARCHAR(255) NULL,
  contract_no VARCHAR(25) NULL,
  contract_type INT NULL,
  customer_name VARCHAR(255) NULL,
  item_code VARCHAR(8) NULL,
  item_name VARCHAR(255) NULL,
  warranty_level VARCHAR(8) NULL,
  warranty_start_time DATETIME(3) NULL,
  warranty_end_time DATETIME(3) NULL,
  warranty_limit INT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_key,source_checksum),
  KEY idx_device (tenant_id,device_sn,deleted,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Immutable original warranty business facts';

-- Source: V414__dppms_native_source_business_records.sql

CREATE TABLE ast_device_warranty_change_record (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  source_version VARCHAR(128) NULL,
  source_read_at DATETIME(3) NOT NULL,
  source_checksum CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  source_id BIGINT NOT NULL,
  source_username VARCHAR(10) NOT NULL,
  update_type INT NULL,
  device_sn VARCHAR(20) NULL,
  warranty_start_time DATETIME(3) NULL,
  warranty_end_time DATETIME(3) NULL,
  warranty_times INT NULL,
  source_updated_at DATETIME(3) NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_source (tenant_id,source_system,source_table,source_key,source_checksum),
  KEY idx_device (tenant_id,device_sn,deleted,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Immutable original warranty business facts';

-- Source: V414__dppms_native_source_business_records.sql

CREATE TABLE ast_device_warranty_fact_project (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  source_project_key VARCHAR(128) NOT NULL,
  fact_type VARCHAR(32) NOT NULL,
  fact_id BIGINT NOT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_source (tenant_id,project_id,fact_type,fact_id),
  KEY idx_fact (tenant_id,fact_type,fact_id,deleted),
  KEY idx_project (tenant_id,project_id,deleted,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Project associations of original warranty business facts';

-- Source: V482__sync_published_project_scope.sql

CREATE TABLE int_sync_project_scope_publication (
  tenant_id BIGINT NOT NULL,
  source_connection_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  published_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id,run_id),
  KEY idx_scope_publication_latest (tenant_id,source_connection_id,published_at,run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Source: V482__sync_published_project_scope.sql

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

-- Source: V412__dppms_native_detail_field_completion.sql

ALTER TABLE ast_device_warranty_record
  ADD COLUMN combined_device_sn TEXT NULL,
  ADD COLUMN product_code TEXT NULL,
  ADD COLUMN product_name TEXT NULL,
  ADD COLUMN product_description TEXT NULL,
  ADD COLUMN grade_name TEXT NULL,
  ADD COLUMN shipment_time DATETIME(3) NULL,
  ADD COLUMN warranty_flag TEXT NULL,
  ADD COLUMN source_warranty_start_time DATETIME(3) NULL,
  ADD COLUMN source_warranty_end_time DATETIME(3) NULL,
  ADD COLUMN original_warranty_start_time DATETIME(3) NULL,
  ADD COLUMN original_warranty_end_time DATETIME(3) NULL,
  ADD COLUMN remaining_days_at_source INT NULL,
  ADD COLUMN original_remaining_days_at_source INT NULL,
  ADD COLUMN contract_type TEXT NULL,
  ADD COLUMN contract_type_name TEXT NULL,
  ADD COLUMN project_name TEXT NULL,
  ADD COLUMN customer_name TEXT NULL,
  ADD COLUMN final_customer_name TEXT NULL,
  ADD COLUMN agent_name TEXT NULL,
  ADD COLUMN office_code TEXT NULL,
  ADD COLUMN office_name TEXT NULL,
  ADD COLUMN market_code TEXT NULL,
  ADD COLUMN market_name TEXT NULL,
  ADD COLUMN system_code TEXT NULL,
  ADD COLUMN system_name TEXT NULL,
  ADD COLUMN secondary_sn TEXT NULL,
  ADD COLUMN secondary_item TEXT NULL,
  ADD COLUMN secondary_product_name TEXT NULL,
  ADD COLUMN secondary_product_description TEXT NULL,
  ADD COLUMN source_sync_time DATETIME(3) NULL,
  ADD COLUMN source_shipment_key TEXT NULL;

-- Source: V412__dppms_native_detail_field_completion.sql

ALTER TABLE ast_device_network_version_event ADD COLUMN custom_info JSON NULL;

-- Source: V436__approved_final_customer_project_contacts.sql

ALTER TABLE cus_project_customer_contact_relation
  ADD COLUMN project_party_id BIGINT NULL COMMENT '联系人所属的项目最终客户相关方',
  ADD COLUMN contact_code VARCHAR(45) NULL COMMENT '联系人业务编码',
  ADD COLUMN registration_source VARCHAR(2) NULL COMMENT '登记来源：1项目信息、2手工成员',
  ADD COLUMN effective_from DATETIME(3) NULL COMMENT '联系人区间开始',
  ADD COLUMN effective_to DATETIME(3) NULL COMMENT '联系人区间结束',
  MODIFY COLUMN customer_id BIGINT NULL,
  MODIFY COLUMN customer_contact_id BIGINT NULL,
  ADD CONSTRAINT ck_cus_project_contact_affiliation CHECK (
    (customer_id IS NOT NULL AND customer_contact_id IS NOT NULL)
    OR (customer_id IS NULL AND customer_contact_id IS NULL
        AND project_party_id IS NOT NULL AND primary_flag=b'0')),
  ADD CONSTRAINT ck_cus_project_contact_interval CHECK (
    effective_from IS NULL OR effective_to IS NULL OR effective_to>=effective_from);

-- Source: V438__preserve_project_contract_auditor_comments.sql

ALTER TABLE com_project_contract_relation
    MODIFY COLUMN creator VARCHAR(64) NULL COMMENT '创建人',
    MODIFY COLUMN updater VARCHAR(64) NULL COMMENT '更新人';

-- Source: V399__requirement_revision_active_identity_uniqueness.sql

ALTER TABLE sol_requirement_analysis_revision
    ADD COLUMN active_revision_no INT GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN revision_no ELSE NULL END) STORED,
    ADD COLUMN active_draft_marker TINYINT GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN draft_marker ELSE NULL END) STORED,
    DROP INDEX uk_ra_revision,
    DROP INDEX uk_ra_project_revision,
    DROP INDEX uk_ra_draft,
    ADD UNIQUE KEY uk_ra_revision (tenant_id,entity_id,active_revision_no),
    ADD UNIQUE KEY uk_ra_project_revision (tenant_id,project_id,active_revision_no),
    ADD UNIQUE KEY uk_ra_draft (tenant_id,project_id,active_draft_marker);

-- Source: V435__migration_global_source_identity_index.sql

ALTER TABLE plt_migration_source_record
    ADD INDEX idx_plt_migration_global_source_identity
        (tenant_id, source_system, source_table, source_record_key, deleted),
    ALGORITHM=INPLACE, LOCK=NONE;
