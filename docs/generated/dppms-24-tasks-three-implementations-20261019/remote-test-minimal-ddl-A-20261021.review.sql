-- REVIEW ONLY. NOT EXECUTED. No authorization to apply any statement in this turn.
-- Target: 172.17.90.178:13306 / npdms. Every object is explicitly schema-qualified.
-- Formal sources and folded later definitions are identified per statement.
-- No DML, task registration, backfill, Flyway-history update, original-task operation,
-- source data correction, service operation or 59191 operation.
-- No FOREIGN_KEY_CHECKS=0, disabled CHECK, password, token or credential.
-- This A file DOES NOT remove fk_scope_order_line or relax its pending snapshots.
-- P22's separately listed structural exception remains unapproved and blocks execution.
-- P14's line unique key follows the user's later line_type adjudication, not V481 order_type.

-- A01 CREATE int_base_sync_task
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V483__independent_base_sync_metadata.sql:3; sql/migrations/V484__base_sync_aliases_and_full_shipment_limit.sql:9
CREATE TABLE `npdms`.`int_base_sync_task` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `task_code` varchar(16) NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `connection_id` bigint NOT NULL,
  `batch_size` int NOT NULL,
  `max_rows` int NOT NULL,
  `active_run_id` bigint NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_base_sync_task` (`tenant_id`,`task_code`,`connection_id`),
  CONSTRAINT `ck_int_base_sync_batch_size` CHECK (`batch_size` BETWEEN 1 AND 2000),
  CONSTRAINT `ck_int_base_sync_max_rows` CHECK (`max_rows` BETWEEN 1 AND 5000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A02 CREATE int_base_sync_run
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V483__independent_base_sync_metadata.sql:24
CREATE TABLE `npdms`.`int_base_sync_run` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `request_key` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `preview` bit(1) NOT NULL,
  `task_code` varchar(16) NOT NULL,
  `connection_id` bigint NOT NULL,
  `batch_size` int NOT NULL,
  `max_rows` int NOT NULL,
  `read_count` bigint NOT NULL DEFAULT 0,
  `inserted_count` bigint NOT NULL DEFAULT 0,
  `updated_count` bigint NOT NULL DEFAULT 0,
  `unchanged_count` bigint NOT NULL DEFAULT 0,
  `deleted_count` bigint NOT NULL DEFAULT 0,
  `error_message` varchar(1000) NULL,
  `started_at` datetime(3) NOT NULL,
  `finished_at` datetime(3) NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_base_sync_run_request` (`tenant_id`,`task_id`,`request_key`),
  KEY `idx_int_base_sync_run_task` (`tenant_id`,`task_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A03 CREATE int_base_sync_binding
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V483__independent_base_sync_metadata.sql:53; sql/migrations/V484__base_sync_aliases_and_full_shipment_limit.sql:3
CREATE TABLE `npdms`.`int_base_sync_binding` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `object_key` varchar(64) NOT NULL,
  `source_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `target_id` bigint NOT NULL,
  `fingerprint` char(64) NOT NULL,
  `last_run_id` bigint NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_base_sync_binding_source` (`tenant_id`,`task_id`,`object_key`,`source_key`),
  KEY `idx_int_base_sync_binding_target` (`tenant_id`,`task_id`,`object_key`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A04 CREATE proj_legacy_detail_source
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V411__project_legacy_detail_evidence.sql:3
CREATE TABLE `npdms`.`proj_legacy_detail_source` (
  id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
  source_system VARCHAR(32) COLLATE utf8mb4_bin NOT NULL,
  source_project_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  source_contract_no VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  current_snapshot_id BIGINT NULL, version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_legacy_detail_identity(tenant_id,source_system,source_project_key),
  KEY idx_legacy_detail_project(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A05 CREATE proj_project_real_product
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V412__dppms_native_detail_field_completion.sql:36
CREATE TABLE `npdms`.`proj_project_real_product` (
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

-- A06 CREATE proj_project_lease_product
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V412__dppms_native_detail_field_completion.sql:57; sql/migrations/V481__sync_decided_configuration_contracts.sql:34
CREATE TABLE `npdms`.`proj_project_lease_product` (
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

-- A07 CREATE proj_project_product_configuration
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V412__dppms_native_detail_field_completion.sql:78; sql/migrations/V481__sync_decided_configuration_contracts.sql:34
CREATE TABLE `npdms`.`proj_project_product_configuration` (
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

-- A08 CREATE com_contract_collection_plan
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V412__dppms_native_detail_field_completion.sql:107; sql/migrations/V481__sync_decided_configuration_contracts.sql:37
CREATE TABLE `npdms`.`com_contract_collection_plan` (
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

-- A09 CREATE ast_device_software_change_batch
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V414__dppms_native_source_business_records.sql:53
CREATE TABLE `npdms`.`ast_device_software_change_batch` (
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

-- A10 CREATE ast_device_software_version_record
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V414__dppms_native_source_business_records.sql:14
CREATE TABLE `npdms`.`ast_device_software_version_record` (
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

-- A11 CREATE ast_device_factory_version_record
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V414__dppms_native_source_business_records.sql:69; sql/migrations/V481__sync_decided_configuration_contracts.sql:47
CREATE TABLE `npdms`.`ast_device_factory_version_record` (
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

-- A12 CREATE ast_device_license_record
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V433__approved_device_license_record.sql:2
CREATE TABLE `npdms`.`ast_device_license_record` (
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

-- A13 CREATE ast_device_warranty_renewal_record
-- 目标表缺失；按当前工作树既有定义建空表
-- Sources: sql/migrations/V473__approved_device_warranty_renewal_record.sql:2
CREATE TABLE `npdms`.`ast_device_warranty_renewal_record` (
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

-- A14 FK_SUPPORT com_sales_order_line
-- 替换uk_sales_order_line前为现有父头外键保留必要索引；不删除父头外键
-- Sources: D:\CodexTaskArtifacts\project-detail-followup\remote-ddl-review-20261021\target-current.json; https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html
ALTER TABLE `npdms`.`com_sales_order_line`
  ADD INDEX idx_sales_order_line_parent (tenant_id,order_id);

-- A15 ALTER system_users
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: sql/migrations/V419__approved_user_employee_no_and_custom_info.sql:4; sql/migrations/V419__approved_user_employee_no_and_custom_info.sql:5
ALTER TABLE `npdms`.`system_users`
  ADD COLUMN employee_no VARCHAR(50) NULL COMMENT '真实员工工号',
  ADD COLUMN custom_info JSON NULL COMMENT '账号扩展业务属性';

-- A16 ALTER ast_product_official_info
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: sql/migrations/V481__sync_decided_configuration_contracts.sql:9; sql/migrations/V481__sync_decided_configuration_contracts.sql:10; sql/migrations/V481__sync_decided_configuration_contracts.sql:26
ALTER TABLE `npdms`.`ast_product_official_info`
  ADD COLUMN `product_category` varchar(100) DEFAULT NULL COMMENT 'CRM productCode',
  ADD COLUMN `product_type` varchar(100) DEFAULT NULL COMMENT 'CRM productSubCode',
  ADD UNIQUE INDEX uk_product_official_tenant_code (tenant_id,product_code);

-- A17 ALTER com_shipment_contract_reference
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: sql/migrations/V258__project_party_and_contract_source_tables.sql:152; sql/migrations/V294__migration_field_naming_and_comment_alignment.sql:319
ALTER TABLE `npdms`.`com_shipment_contract_reference`
  ADD COLUMN warranty_years INT NULL COMMENT '合同保修年数，原空白或NULL表示未知；不代表当前保修状态';

-- A18 ALTER com_shipment_package
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: sql/migrations/V412__dppms_native_detail_field_completion.sql:106; sql/migrations/V481__sync_decided_configuration_contracts.sql:30
ALTER TABLE `npdms`.`com_shipment_package`
  ADD COLUMN carrier_code VARCHAR(256) NULL,
  ADD COLUMN shipment_contract_source_key VARCHAR(128) NULL COMMENT '原fb_shipment.con_id，保留精确父来源身份';

-- A19 ALTER proj_project
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: sql/migrations/V412__dppms_native_detail_field_completion.sql:101; sql/migrations/V412__dppms_native_detail_field_completion.sql:102; sql/migrations/V412__dppms_native_detail_field_completion.sql:103; sql/migrations/V412__dppms_native_detail_field_completion.sql:104; sql/migrations/V412__dppms_native_detail_field_completion.sql:105
ALTER TABLE `npdms`.`proj_project`
  ADD COLUMN custom_info JSON NULL,
  ADD COLUMN order_create_time DATETIME(3) NULL,
  ADD COLUMN disabled BIT(1) NULL,
  ADD COLUMN effective_from DATETIME(3) NULL,
  ADD COLUMN effective_to DATETIME(3) NULL;

-- A20 ALTER com_delivery_scope
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:4; sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:5; sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:6; sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:7; sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:10; sql/migrations/V421__approved_dppms_delivery_scope_attributes.sql:11
ALTER TABLE `npdms`.`com_delivery_scope`
  ADD COLUMN allocation_confirmed BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否确认占用订单分配数量',
  ADD COLUMN delivered_quantity DECIMAL(18,6) NULL COMMENT '项目范围原已发货数量',
  ADD COLUMN open_quantity DECIMAL(18,6) NULL COMMENT '项目范围原订单未交数量',
  MODIFY COLUMN current_order_line_id BIGINT GENERATED ALWAYS AS (CASE WHEN allocation_confirmed=b'1' AND deleted=0 AND effective_to IS NULL THEN order_line_id ELSE NULL END) STORED,
  DROP INDEX uk_scope_version,
  ADD UNIQUE KEY uk_scope_version (tenant_id,(CASE WHEN allocation_confirmed=b'1' THEN order_line_id ELSE NULL END),project_id,allocation_version);

-- A21 ALTER proj_project_member_assignment
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: sql/migrations/V434__approved_project_member_history_fields.sql:4; sql/migrations/V434__approved_project_member_history_fields.sql:5; sql/migrations/V434__approved_project_member_history_fields.sql:6; sql/migrations/V434__approved_project_member_history_fields.sql:7; sql/migrations/V434__approved_project_member_history_fields.sql:10; sql/migrations/V434__approved_project_member_history_fields.sql:11; sql/migrations/V434__approved_project_member_history_fields.sql:12
ALTER TABLE `npdms`.`proj_project_member_assignment`
  ADD COLUMN member_code VARCHAR(45) NULL COMMENT '成员区间人员编码',
  ADD COLUMN phone_number VARCHAR(20) NULL COMMENT '成员区间联系电话',
  ADD COLUMN email VARCHAR(45) NULL COMMENT '成员区间邮箱',
  ADD COLUMN registration_source VARCHAR(2) NULL COMMENT '登记来源：1项目信息，2手工成员',
  MODIFY COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，未确认历史可保留原NULL',
  MODIFY COLUMN update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间，未确认历史可保留原NULL',
  ADD CONSTRAINT ck_proj_member_confirmed_identity_audit CHECK (authorization_confirmed=b'0' OR (user_id IS NOT NULL AND create_time IS NOT NULL AND update_time IS NOT NULL));

-- A22 ALTER proj_project_party
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: sql/migrations/V461__preserve_project_party_nullable_audit.sql:3; sql/migrations/V461__preserve_project_party_nullable_audit.sql:4
ALTER TABLE `npdms`.`proj_project_party`
  MODIFY COLUMN creator VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建人',
  MODIFY COLUMN updater VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新人';

-- A23 ALTER com_sales_order_line
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: sql/migrations/V481__sync_decided_configuration_contracts.sql:27; sql/migrations/V481__sync_decided_configuration_contracts.sql:28; docs/generated/dppms-24-tasks-three-implementations-20261019/P14-business-keys-preview-20261020.md:5; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/basesync/task/P14SalesOrderSyncTask.java
ALTER TABLE `npdms`.`com_sales_order_line`
  MODIFY COLUMN order_id BIGINT NULL COMMENT '订单ID',
  DROP INDEX uk_sales_order_line,
  ADD UNIQUE INDEX uk_sales_order_line_business (tenant_id,source_system,company_code,line_type,order_no,line_no);

-- A24 ALTER cus_customer_master
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: docs/generated/dppms-24-tasks-three-implementations-20261019/P10-P33-column-capacity.review.sql:6; D:\CodexTaskArtifacts\project-detail-followup\remote-ddl-review-20261021\target-current.json
ALTER TABLE `npdms`.`cus_customer_master`
  MODIFY COLUMN `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称';

-- A25 ALTER imp_eng_installation
-- 仅补当前缺列、既有正式可空/约束定义及已决容量；保留其余列、索引和外键
-- Sources: docs/generated/dppms-24-tasks-three-implementations-20261019/P10-P33-column-capacity.review.sql:11; D:\CodexTaskArtifacts\project-detail-followup\remote-ddl-review-20261021\target-current.json
ALTER TABLE `npdms`.`imp_eng_installation`
  MODIFY COLUMN `install_location` VARCHAR(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '安装位置';
