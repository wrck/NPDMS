-- CUS只持有不可变来源引用；SRV拥有任务及处理业务真值。未关联跨Owner物理外键。
CREATE TABLE IF NOT EXISTS cus_inspection_history (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  version bigint NOT NULL DEFAULT 1,
  customer_id bigint NOT NULL,
  project_id bigint NOT NULL,
  project_code varchar(64) NULL,
  project_name varchar(256) NULL,
  task_id bigint NOT NULL,
  task_version bigint NOT NULL,
  task_code varchar(64) NOT NULL,
  task_name varchar(200) NOT NULL,
  report_id bigint NOT NULL,
  source_fingerprint char(64) NOT NULL,
  archived_by bigint NOT NULL,
  archived_at datetime(3) NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cus_inspection_history_tenant (tenant_id,id),
  UNIQUE KEY uk_cus_inspection_task (tenant_id,task_id),
  KEY idx_cus_inspection_history (tenant_id,customer_id,project_id,id),
  CONSTRAINT ck_cus_inspection_source CHECK (task_id > 0 AND task_version > 0 AND report_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cus_inspection_reference (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  version bigint NOT NULL DEFAULT 1,
  history_id bigint NOT NULL,
  reference_kind varchar(24) NOT NULL,
  source_id bigint NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cus_inspection_reference_tenant (tenant_id,id),
  UNIQUE KEY uk_cus_inspection_reference (tenant_id,history_id,reference_kind,source_id),
  CONSTRAINT fk_cus_inspection_history FOREIGN KEY (tenant_id,history_id) REFERENCES cus_inspection_history (tenant_id,id),
  CONSTRAINT ck_cus_inspection_reference_kind CHECK (reference_kind IN ('DEVICE','EXECUTION','RESULT','REPORT','ANNOTATION','ISSUE','REMEDIATION','EVIDENCE')),
  CONSTRAINT ck_cus_inspection_reference_id CHECK (source_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
CREATE TRIGGER trg_cus_inspection_history_bu BEFORE UPDATE ON cus_inspection_history FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Customer inspection history is immutable'; END$$
CREATE TRIGGER trg_cus_inspection_history_bd BEFORE DELETE ON cus_inspection_history FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Customer inspection history is immutable'; END$$
CREATE TRIGGER trg_cus_inspection_reference_bu BEFORE UPDATE ON cus_inspection_reference FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Customer inspection source reference is immutable'; END$$
CREATE TRIGGER trg_cus_inspection_reference_bd BEFORE DELETE ON cus_inspection_reference FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Customer inspection source reference is immutable'; END$$
DELIMITER ;
