-- Approved minimal NORMAL closure. Forward-only new storage; no existing project/template backfill.
ALTER TABLE proj_project
  ADD COLUMN closure_policy_snapshot JSON NULL COMMENT 'Explicit closure policy frozen from published template',
  ADD COLUMN closure_type VARCHAR(32) NULL,
  ADD COLUMN closed_from_stage VARCHAR(32) NULL,
  ADD COLUMN closed_at DATETIME(3) NULL;
ALTER TABLE proj_project_template_revision
  ADD COLUMN closure_policy JSON NULL COMMENT 'Explicit NORMAL policy frozen at template publication';

CREATE TABLE acc_closure_gate_snapshot (
  id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
  project_version INT NOT NULL, tree_version BIGINT NOT NULL, from_stage VARCHAR(32) NOT NULL,
  closure_type VARCHAR(32) NOT NULL, rule_revision INT NOT NULL, passed BIT NOT NULL,
  evidence JSON NOT NULL COMMENT 'Immutable check results',
  source_vector JSON NOT NULL COMMENT 'Immutable source identity and version vector',
  source_digest CHAR(64) NOT NULL, checked_by BIGINT NOT NULL, checked_at DATETIME(3) NOT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted BIT NOT NULL DEFAULT b'0', PRIMARY KEY(id),
  KEY idx_acc_closure_snapshot_latest(tenant_id,project_id,checked_at,id),
  CONSTRAINT ck_acc_closure_snapshot_policy CHECK(closure_type='NORMAL' AND rule_revision=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE acc_project_closure (
  id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL, snapshot_id BIGINT NOT NULL,
  closure_type VARCHAR(32) NOT NULL, rule_revision INT NOT NULL, from_stage VARCHAR(32) NOT NULL,
  project_version INT NOT NULL, tree_version BIGINT NOT NULL, status VARCHAR(32) NOT NULL,
  applicant_user_id BIGINT NOT NULL, service_manager_user_id BIGINT NOT NULL, reviewer_user_id BIGINT NOT NULL,
  process_definition_key VARCHAR(128) NOT NULL, process_definition_id VARCHAR(128) NOT NULL,
  process_instance_id VARCHAR(128) NOT NULL, business_key VARCHAR(128) NOT NULL,
  process_evidence JSON NOT NULL COMMENT 'Immutable actual BPM definition/tasks/candidates evidence',
  submitted_at DATETIME(3) NOT NULL, decided_at DATETIME(3) NULL, version INT NOT NULL DEFAULT 0,
  active_marker INT GENERATED ALWAYS AS (CASE WHEN status='IN_REVIEW' THEN 1 ELSE NULL END) STORED,
  creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted BIT NOT NULL DEFAULT b'0', PRIMARY KEY(id),
  UNIQUE KEY uk_acc_closure_snapshot(tenant_id,snapshot_id),
  UNIQUE KEY uk_acc_closure_process(tenant_id,process_instance_id),
  UNIQUE KEY uk_acc_closure_active(tenant_id,project_id,active_marker),
  KEY idx_acc_closure_latest(tenant_id,project_id,submitted_at,id),
  CONSTRAINT ck_acc_closure_type CHECK(closure_type='NORMAL' AND rule_revision=1),
  CONSTRAINT ck_acc_closure_status CHECK(status IN ('IN_REVIEW','APPROVED','REJECTED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE acc_closure_review (
  id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, application_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
  process_instance_id VARCHAR(128) NOT NULL, process_definition_id VARCHAR(128) NOT NULL,
  task_id VARCHAR(128) NOT NULL, task_definition_key VARCHAR(128) NOT NULL,
  reviewer_user_id BIGINT NOT NULL, outcome VARCHAR(32) NOT NULL, reason VARCHAR(2048) NULL,
  reviewed_at DATETIME(3) NOT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted BIT NOT NULL DEFAULT b'0', PRIMARY KEY(id),
  UNIQUE KEY uk_acc_closure_review_task(tenant_id,application_id,task_id),
  KEY idx_acc_closure_review_application(tenant_id,application_id),
  CONSTRAINT ck_acc_closure_review_outcome CHECK(outcome IN ('APPROVE','REJECT','CANCEL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE proj_project_exit_record (
  id BIGINT NOT NULL, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
  application_id BIGINT NULL, snapshot_id BIGINT NULL, closure_type VARCHAR(32) NOT NULL,
  project_version INT NOT NULL, stage_instance_id BIGINT NOT NULL, template_revision_id BIGINT NOT NULL,
  scope_version BIGINT NOT NULL, gate_snapshot_ref BIGINT NOT NULL,
  source_context VARCHAR(32) NOT NULL, source_record_id BIGINT NOT NULL, source_record_revision INT NOT NULL,
  closed_from_stage VARCHAR(32) NOT NULL, before_lifecycle_status VARCHAR(32) NOT NULL,
  after_lifecycle_status VARCHAR(32) NOT NULL, before_project_version INT NOT NULL, after_project_version INT NOT NULL,
  process_instance_id VARCHAR(128) NULL, revalidation_evidence JSON NOT NULL, closed_at DATETIME(3) NOT NULL,
  creator VARCHAR(64) NOT NULL DEFAULT '', create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater VARCHAR(64) NOT NULL DEFAULT '', update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted BIT NOT NULL DEFAULT b'0', PRIMARY KEY(id),
  UNIQUE KEY uk_proj_exit_application(tenant_id,application_id),
  UNIQUE KEY uk_per_project_version(tenant_id,project_id,project_version),
  UNIQUE KEY uk_per_source(tenant_id,source_context,source_record_id,source_record_revision),
  CONSTRAINT ck_per_type CHECK((closure_type IN ('NORMAL','NO_TRACKING') AND source_context='ACC')
    OR (closure_type='EXCEPTION' AND source_context='PROJ')),
  CONSTRAINT ck_per_stage CHECK(closed_from_stage IN ('S0','S1','S2','S3','S4','S5','S6'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
