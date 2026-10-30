-- 179-only forward migration; source: 25 previously reviewed and applied A statements (2026-10-21).
-- Executes only against the 179 recovery lineage; no legacy trigger definitions or task runs.
-- The 179 V506 failure and receipt are retained separately; P22's deferred foreign key is unchanged.
SET @sync_history_ok = (
 SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND
 ((version='506.1' AND checksum=-703476136 AND script='V506_1__reconcile_partial_cutover_support.sql') OR
  (version='516' AND checksum=-2064355135 AND script='V516__stage_plan_management_menu_roles.sql'))
);
SET @sync_guard_sql = IF(@sync_history_ok=2 AND ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('int_base_sync_task', 'int_base_sync_run', 'int_base_sync_binding'))=3 AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('proj_legacy_detail_source', 'proj_project_real_product', 'proj_project_lease_product', 'proj_project_product_configuration', 'com_contract_collection_plan', 'ast_device_software_change_batch', 'ast_device_software_version_record', 'ast_device_factory_version_record', 'ast_device_license_record', 'ast_device_warranty_renewal_record'))=0),
 'SELECT 1 AS sync_179_preconditions_verified','SYNC_179_PRECONDITIONS_NOT_MET');
PREPARE sync_179_guard FROM @sync_guard_sql;
EXECUTE sync_179_guard;
DEALLOCATE PREPARE sync_179_guard;

-- A04 proj_legacy_detail_source; reviewed SHA-256 06b4d32bf338e7a7ca16188e99d71906c3531f37db6fd649b59cebc4cc90e761
CREATE TABLE `proj_legacy_detail_source` (
  id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
  source_system VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  source_project_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  source_contract_no VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  current_snapshot_id BIGINT NULL, version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_legacy_detail_identity(tenant_id,source_system,source_project_key),
  KEY idx_legacy_detail_project(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A05 proj_project_real_product; reviewed SHA-256 19ae47ea76cea00925b20f215048c6e317f984e0cf79d588c5fb4b9fbc112ed0
CREATE TABLE `proj_project_real_product` (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  contract_no TEXT NULL,
  project_code TEXT NULL,
  execution_no TEXT NULL,
  product_category_name TEXT NULL,
  product_name TEXT NULL,
  product_sub_code TEXT NULL,
  product_sub_model TEXT NULL,
  product_sub_name TEXT NULL,
  quantity DECIMAL(24,6) NULL,
  memo TEXT NULL,
  data_source VARCHAR(25) NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_project (tenant_id,project_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A06 proj_project_lease_product; reviewed SHA-256 5f98b6c4ef3a7bd6d06040f54504663f33d2d3015e444a6b78c3d7062a3ea3fe
CREATE TABLE `proj_project_lease_product` (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  project_code TEXT NULL,
  execution_no TEXT NULL,
  product_category TEXT NULL,
  product_type TEXT NULL,
  product_code TEXT NULL,
  product_model TEXT NULL,
  product_name TEXT NULL,
  quantity DECIMAL(24,6) NULL,
  memo TEXT NULL,
  lease_duration DECIMAL(16,2) NULL,
  data_source VARCHAR(25) NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_project (tenant_id,project_id,deleted),
  KEY idx_lease_cache_project (tenant_id,project_code(255),deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A07 proj_project_product_configuration; reviewed SHA-256 e7a3b0758aab8dc158293c38a9b4695df7f6f6d6c9524bc451abe552f947307c
CREATE TABLE `proj_project_product_configuration` (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  project_code TEXT NULL,
  execution_no TEXT NULL,
  item_group INT NULL,
  product_code TEXT NULL,
  parent_code TEXT NULL,
  quantity DECIMAL(24,6) NULL,
  bom_path TEXT NULL,
  product_model TEXT NULL,
  product_description TEXT NULL,
  level INT NULL,
  data_source VARCHAR(25) NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_project (tenant_id,project_id,deleted),
  KEY idx_configuration_cache_project (tenant_id,project_code(100),deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A08 com_contract_collection_plan; reviewed SHA-256 63ee5440316be4893d3770544f8bc0b6d5474a5c2a6724d5e71087de44624a88
CREATE TABLE `com_contract_collection_plan` (
  id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
  contract_id BIGINT NULL,
  contract_no VARCHAR(64) NULL,
  batch_code VARCHAR(32) NULL,
  collection_item_name VARCHAR(64) NULL,
  reference_event_name VARCHAR(64) NULL,
  event_planned_at DATETIME(3) NULL,
  after_days INT NULL,
  event_actual_finished_at DATETIME(3) NULL,
  marketing_feedback VARCHAR(2000) NULL,
  data_source VARCHAR(25) NULL,
  effective_from DATETIME(3) NULL,
  effective_to DATETIME(3) NULL,
  creator VARCHAR(64) NULL, create_time DATETIME(3) NULL,
  updater VARCHAR(64) NULL, update_time DATETIME(3) NULL,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), KEY idx_contract (tenant_id,contract_id,deleted),
  KEY idx_collection_plan_cache_contract (tenant_id,contract_no,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同回款计划';

-- A09 ast_device_software_change_batch; reviewed SHA-256 0faa08406195e0d5432878a4658b0c1ca76eeff9a9a61d3ece4e88213f53caa9
CREATE TABLE `ast_device_software_change_batch` (
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

-- A10 ast_device_software_version_record; reviewed SHA-256 ce6afe8ece8810a9097b84590db2b0b2961047946054fe59949a272315ed2fe4
CREATE TABLE `ast_device_software_version_record` (
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

-- A11 ast_device_factory_version_record; reviewed SHA-256 697da8edc7da8b6b9a931681d36f6946a545c447acdd5a90c902d46afbd5fa64
CREATE TABLE `ast_device_factory_version_record` (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NULL COMMENT 'Verified historical project association, not current device ownership',
  device_sn VARCHAR(100) NULL,
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

-- A12 ast_device_license_record; reviewed SHA-256 e932d254e6c8dbce8c46e0d15f5b2d03cd86458abd24e70d8def6150700aa736
CREATE TABLE `ast_device_license_record` (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    device_id BIGINT NULL,
    device_sn VARCHAR(255) NULL,
    contract_id BIGINT NULL,
    contract_no VARCHAR(32) NULL,
    contract_type VARCHAR(16) NULL,
    product_code VARCHAR(255) NULL,
    product_model VARCHAR(256) NULL,
    license_status VARCHAR(255) NULL,
    license_code_ciphertext TEXT CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creator VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL,
    updater VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY idx_license_device (tenant_id,device_id,deleted),
    KEY idx_license_contract (tenant_id,contract_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备许可历史记录';

-- A13 ast_device_warranty_renewal_record; reviewed SHA-256 06d2f27f9b343e059226e258f02644d0a4720edf8600ba56cdb47f64afb53b93
CREATE TABLE `ast_device_warranty_renewal_record` (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    device_sn VARCHAR(100) NULL COMMENT '设备序列号',
    warranty_contract_no VARCHAR(64) NULL COMMENT '续保合同号',
    warranty_grade VARCHAR(32) NULL COMMENT '维保等级编码',
    warranty_start_date DATE NULL COMMENT '实际开始日期',
    warranty_end_date DATE NULL COMMENT '实际结束日期',
    warranty_months INT UNSIGNED NULL COMMENT '参考月数，不据此重算实际日期',
    extended BIT NULL COMMENT '是否延保，NULL为未记录',
    is_latest BIT NULL DEFAULT NULL COMMENT '原最新选择标记，不代表在保状态',
    remark TEXT NULL COMMENT '续保备注',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL,
    updater VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    KEY idx_renewal_device (tenant_id,device_sn,warranty_start_date,id),
    KEY idx_renewal_contract (tenant_id,warranty_contract_no,device_sn),
    CONSTRAINT ck_renewal_dates CHECK (warranty_start_date IS NULL OR warranty_end_date IS NULL
        OR warranty_end_date >= warranty_start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备续保记录';
