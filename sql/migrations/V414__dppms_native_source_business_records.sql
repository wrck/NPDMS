-- DPPMS business records: current baseline follows the user's whole-table decisions.
-- Adds nullable source attributes and separate immutable facts; no existing business row is updated.
-- Current lifecycle, current device ownership, renewal state and installation history remain Owner-controlled.

-- User-adjudicated shipment fields reuse the existing shipment and product entities.
-- No duplicate shipment product labels, legacy product IDs or source update date columns.

-- User-adjudicated execution orders reuse all existing official fields and INT source bindings.

-- Maintenance records are deferred by the user; no business table is introduced.

-- Approved formal software history: named values, previous record, one-time legacy extensions.
-- Existing deployed rows require verified Owner migration before obsolete columns are retired.
CREATE TABLE ast_device_software_version_record (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  device_id BIGINT NULL,
  device_sn VARCHAR(100) NULL,
  project_id BIGINT NULL,
  previous_record_id BIGINT NULL,
  conp_version VARCHAR(255) NULL,
  conp_type VARCHAR(100) NULL,
  conp_series VARCHAR(100) NULL,
  conp_mark VARCHAR(255) NULL,
  boot_version VARCHAR(255) NULL,
  cpld_version VARCHAR(255) NULL,
  pcb_version VARCHAR(255) NULL,
  customized BIT NULL,
  release_date DATE NULL,
  version_desc VARCHAR(1000) NULL,
  recorded_at DATETIME(3) NULL,
  data_source VARCHAR(32) NOT NULL,
  parsed_fact_id BIGINT NULL,
  request_key VARCHAR(180) COLLATE utf8mb4_bin NULL,
  change_batch_id BIGINT NULL,
  contract_no VARCHAR(100) NULL,
  product_code VARCHAR(25) NULL,
  execute_time DATE NULL,
  data_state INT NULL,
  custom_info JSON NULL,
  creator VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL,
  updater VARCHAR(64) NULL,
  update_time DATETIME(3) NOT NULL,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_software_request (tenant_id,request_key),
  UNIQUE KEY uk_software_parsed_fact (tenant_id,parsed_fact_id),
  KEY idx_software_device (tenant_id,device_id,recorded_at,id),
  KEY idx_software_project (tenant_id,project_id,recorded_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Device software version registrations';

CREATE TABLE ast_device_software_change_batch (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  change_version VARCHAR(10) NULL,
  change_remark VARCHAR(255) NULL,
  latest INT NULL,
  creator VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL,
  updater VARCHAR(64) NULL,
  update_time DATETIME(3) NOT NULL,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_project (tenant_id,project_id,deleted,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Project software registration batches';

CREATE TABLE ast_device_factory_version_record (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NULL COMMENT 'Verified historical project association, not current device ownership',
  device_sn VARCHAR(100) NOT NULL,
  conp_version VARCHAR(100) NULL,
  boot_version VARCHAR(100) NULL,
  cpld_version VARCHAR(100) NULL,
  pcb_version VARCHAR(100) NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_project (tenant_id,project_id,deleted,id),
  KEY idx_device (tenant_id,device_sn,deleted,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Device factory software version records';

-- Installation source fact table withdrawn; business history is carried by imp_eng_installation.

CREATE TABLE ast_device_warranty_service_record (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  source_version VARCHAR(128) NULL,
  source_read_at DATETIME(3) NOT NULL,
  source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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

CREATE TABLE ast_device_warranty_registration (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  source_version VARCHAR(128) NULL,
  source_read_at DATETIME(3) NOT NULL,
  source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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

CREATE TABLE ast_device_warranty_change_record (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  source_version VARCHAR(128) NULL,
  source_read_at DATETIME(3) NOT NULL,
  source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
