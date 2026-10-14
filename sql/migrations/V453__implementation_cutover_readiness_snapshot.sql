-- EXE-06 真实来源校验快照；只追加，不回写历史 CUT 引用。
CREATE TABLE IF NOT EXISTS imp_cutover_readiness_snapshot (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  project_id bigint NOT NULL,
  device_scope_key char(64) NOT NULL,
  snapshot_no int NOT NULL,
  decision varchar(16) NOT NULL,
  context_json json NOT NULL,
  unmet_codes_json json NOT NULL,
  evaluated_by bigint NOT NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_imp_cutover_readiness_no (tenant_id, project_id, snapshot_no),
  KEY idx_imp_cutover_readiness_scope (tenant_id, project_id, device_scope_key, snapshot_no),
  CONSTRAINT chk_imp_cutover_readiness_decision CHECK (decision IN ('READY', 'NOT_READY')),
  CONSTRAINT chk_imp_cutover_readiness_number CHECK (snapshot_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EXE-06割接前不可变就绪校验快照';
