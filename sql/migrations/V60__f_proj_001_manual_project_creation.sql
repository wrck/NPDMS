-- F-PROJ-001 / PM-01 / PM-03
-- Forward-only adaptation of the directly cut-over project model.
-- No project creation draft and no parallel project/template/deliverable carrier is introduced.

ALTER TABLE proj_project_template_revision
    CHANGE COLUMN code template_code VARCHAR(64) NOT NULL,
    CHANGE COLUMN name template_name VARCHAR(255) NOT NULL,
    CHANGE COLUMN snapshot_json definition_snapshot JSON NULL,
    MODIFY COLUMN status VARCHAR(32) NOT NULL,
    ADD COLUMN template_id BIGINT NULL AFTER tenant_id,
    ADD COLUMN applicability_snapshot JSON NULL AFTER revision_no,
    ADD COLUMN business_scene_code VARCHAR(64) NULL AFTER applicability_snapshot,
    ADD COLUMN match_priority INT NOT NULL DEFAULT 0 AFTER business_scene_code,
    ADD COLUMN default_flag TINYINT NOT NULL DEFAULT 0 AFTER match_priority,
    ADD COLUMN workflow_definition_key VARCHAR(128) NULL AFTER default_flag,
    ADD COLUMN workflow_definition_version INT UNSIGNED NULL AFTER workflow_definition_key,
    ADD COLUMN content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER definition_snapshot,
    ADD COLUMN effective_from DATETIME(3) NULL AFTER content_sha256,
    ADD COLUMN effective_to DATETIME(3) NULL AFTER effective_from;

-- Legacy templates remain reusable source data, but are not advertised as published
-- revisions until their workflow, deliverable and gate definitions are formally completed.
UPDATE proj_project_template_revision
SET template_id = id,
    applicability_snapshot = JSON_OBJECT(
        'schemaVersion', 1,
        'legacyProjectType', project_type
    ),
    business_scene_code = 'LEGACY_REVIEW_REQUIRED',
    workflow_definition_key = 'LEGACY_REVIEW_REQUIRED',
    workflow_definition_version = 1,
    definition_snapshot = JSON_OBJECT(
        'schemaVersion', 1,
        'stages', COALESCE(JSON_EXTRACT(definition_snapshot, '$.phases'), JSON_ARRAY()),
        'milestones', JSON_ARRAY(),
        'deliverables', JSON_ARRAY(),
        'gates', JSON_ARRAY(),
        'legacySnapshot', COALESCE(definition_snapshot, JSON_OBJECT())
    ),
    effective_from = create_time,
    status = 'DRAFT';

UPDATE proj_project_template_revision
SET content_sha256 = SHA2(CAST(definition_snapshot AS CHAR CHARACTER SET utf8mb4), 256);

ALTER TABLE proj_project_template_revision
    MODIFY COLUMN template_id BIGINT NOT NULL,
    MODIFY COLUMN applicability_snapshot JSON NOT NULL,
    MODIFY COLUMN business_scene_code VARCHAR(64) NOT NULL,
    MODIFY COLUMN workflow_definition_key VARCHAR(128) NOT NULL,
    MODIFY COLUMN workflow_definition_version INT UNSIGNED NOT NULL,
    MODIFY COLUMN definition_snapshot JSON NOT NULL,
    MODIFY COLUMN content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY COLUMN effective_from DATETIME(3) NOT NULL,
    DROP INDEX uk_code,
    RENAME INDEX uk_proj_template_revision_tenant_row TO uk_project_template_revision_tenant_row,
    RENAME INDEX uk_proj_template_revision TO uk_project_template_revision_code,
    ADD UNIQUE KEY uk_project_template_revision (tenant_id, template_id, revision_no),
    ADD KEY idx_project_template_revision_candidate (
        tenant_id, status, business_scene_code, match_priority
    ),
    ADD CONSTRAINT chk_project_template_revision_default CHECK (default_flag IN (0, 1)),
    ADD CONSTRAINT chk_project_template_revision_dates CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    );

CREATE TABLE proj_project_stage_snapshot (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    stage_code VARCHAR(32) NOT NULL,
    stage_name VARCHAR(128) NOT NULL,
    snapshot_no INT UNSIGNED NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    template_revision_id BIGINT NOT NULL,
    workflow_definition_key VARCHAR(128) NOT NULL,
    workflow_definition_version INT UNSIGNED NOT NULL,
    entry_rule_snapshot JSON NOT NULL,
    exit_rule_snapshot JSON NOT NULL,
    stage_status VARCHAR(32) NOT NULL,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_stage_snapshot_tenant_row (tenant_id, id),
    UNIQUE KEY uk_project_stage_snapshot (tenant_id, project_id, stage_code, snapshot_no),
    KEY idx_project_stage_snapshot_navigation (tenant_id, project_id, sort_order),
    CONSTRAINT fk_project_stage_snapshot_project FOREIGN KEY (tenant_id, project_id)
        REFERENCES proj_project (tenant_id, id),
    CONSTRAINT fk_project_stage_snapshot_template FOREIGN KEY (tenant_id, template_revision_id)
        REFERENCES proj_project_template_revision (tenant_id, id),
    CONSTRAINT chk_project_stage_snapshot_no CHECK (snapshot_no > 0),
    CONSTRAINT chk_project_stage_snapshot_workflow_version CHECK (workflow_definition_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='项目阶段与模板、流程版本的不可覆盖快照';

ALTER TABLE proj_project
    ADD COLUMN template_revision_id BIGINT NULL AFTER lifecycle_template_id,
    ADD COLUMN workflow_definition_key VARCHAR(128) NULL AFTER template_revision_id,
    ADD COLUMN workflow_definition_version INT UNSIGNED NULL AFTER workflow_definition_key,
    ADD COLUMN current_stage_code VARCHAR(32) NOT NULL DEFAULT 'S0' AFTER workflow_definition_version,
    ADD COLUMN assignment_status VARCHAR(32) NOT NULL DEFAULT 'UNASSIGNED' AFTER current_stage_code,
    ADD COLUMN create_reason VARCHAR(500) NULL AFTER source_type,
    ADD KEY idx_project_template_revision (tenant_id, template_revision_id, status),
    ADD KEY idx_project_stage_assignment (tenant_id, current_stage_code, assignment_status, status),
    ADD CONSTRAINT fk_project_template_revision FOREIGN KEY (tenant_id, template_revision_id)
        REFERENCES proj_project_template_revision (tenant_id, id),
    ADD CONSTRAINT chk_project_current_stage CHECK (current_stage_code IN ('S0', 'S1', 'S2', 'S3', 'S4', 'S5', 'S6')),
    ADD CONSTRAINT chk_project_assignment_status CHECK (assignment_status IN ('UNASSIGNED', 'ASSIGNED'));

ALTER TABLE proj_project_task
    ADD COLUMN stage_definition_key VARCHAR(128) NULL AFTER project_id,
    ADD COLUMN task_definition_key VARCHAR(128) NULL AFTER stage_definition_key,
    ADD COLUMN task_kind_code VARCHAR(32) NOT NULL DEFAULT 'TASK' AFTER task_definition_key,
    ADD COLUMN milestone_definition_key VARCHAR(128) NULL AFTER task_kind_code,
    ADD COLUMN template_task_definition_id BIGINT NULL AFTER milestone_definition_key,
    ADD COLUMN status_machine_version INT UNSIGNED NOT NULL DEFAULT 1 AFTER status,
    ADD KEY idx_project_task_definition (
        tenant_id, project_id, stage_definition_key, task_definition_key
    ),
    ADD KEY idx_project_task_template_definition (tenant_id, template_task_definition_id),
    ADD CONSTRAINT fk_project_task_template_definition FOREIGN KEY (tenant_id, template_task_definition_id)
        REFERENCES proj_project_template_task_definition (tenant_id, id),
    ADD CONSTRAINT chk_project_task_kind CHECK (task_kind_code IN ('TASK', 'MILESTONE')),
    ADD CONSTRAINT chk_project_task_status_machine_version CHECK (status_machine_version > 0);

ALTER TABLE acc_project_deliverable
    ADD COLUMN template_requirement_key VARCHAR(128) NULL AFTER project_id,
    ADD COLUMN source_template_revision_id BIGINT NULL AFTER template_requirement_key,
    ADD COLUMN applicable_stage_code VARCHAR(32) NULL AFTER source_template_revision_id,
    ADD COLUMN required_flag TINYINT NOT NULL DEFAULT 0 AFTER applicable_stage_code;

UPDATE acc_project_deliverable
SET template_requirement_key = code,
    required_flag = CASE WHEN deliverable_type = 'REQUIRED' THEN 1 ELSE 0 END;

ALTER TABLE acc_project_deliverable
    ADD UNIQUE KEY uk_acc_project_deliverable_requirement (
        tenant_id, project_id, source_template_revision_id, template_requirement_key
    ),
    ADD CONSTRAINT chk_acc_project_deliverable_required CHECK (required_flag IN (0, 1));

CREATE TABLE plt_business_code_rule (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    rule_version VARCHAR(32) NOT NULL,
    prefix VARCHAR(32) NOT NULL,
    padding_width INT UNSIGNED NOT NULL,
    next_value BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    current_rule_code VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' AND effective_to IS NULL THEN rule_code ELSE NULL END
    ) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater BIGINT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_code_rule_tenant_row (tenant_id, id),
    UNIQUE KEY uk_business_code_rule_version (tenant_id, rule_code, rule_version),
    UNIQUE KEY uk_business_code_rule_current (tenant_id, current_rule_code),
    CONSTRAINT chk_business_code_rule_padding CHECK (padding_width > 0),
    CONSTRAINT chk_business_code_rule_next CHECK (next_value > 0),
    CONSTRAINT chk_business_code_rule_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='租户级业务编码规则及原子流水水位';

CREATE TABLE plt_idempotency_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    scope_code VARCHAR(64) NOT NULL,
    actor_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    request_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_json JSON NULL,
    resource_id BIGINT NULL,
    correlation_id VARCHAR(128) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_record_tenant_row (tenant_id, id),
    UNIQUE KEY uk_idempotency_record_key (tenant_id, scope_code, actor_id, idempotency_key),
    KEY idx_idempotency_record_resource (tenant_id, scope_code, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='同一本地事务内提交的命令幂等结果';

CREATE TABLE plt_operation_audit (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    operation_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    decision_code VARCHAR(32) NOT NULL,
    detail_json JSON NOT NULL,
    correlation_id VARCHAR(128) NULL,
    operation_time DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_audit_tenant_row (tenant_id, id),
    KEY idx_operation_audit_resource (tenant_id, resource_type, resource_id, operation_time),
    KEY idx_operation_audit_correlation (tenant_id, correlation_id, operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='追加写的脱敏业务操作审计';

CREATE TABLE plt_outbox_event (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INT UNSIGNED NOT NULL,
    payload_json JSON NOT NULL,
    publish_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3) NULL,
    published_time DATETIME(3) NULL,
    correlation_id VARCHAR(128) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_tenant_row (tenant_id, id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_event_publish (publish_status, next_retry_time, id),
    KEY idx_outbox_event_aggregate (tenant_id, aggregate_type, aggregate_id, event_version),
    CONSTRAINT chk_outbox_event_version CHECK (event_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='与业务事实同事务提交的Outbox事件';
