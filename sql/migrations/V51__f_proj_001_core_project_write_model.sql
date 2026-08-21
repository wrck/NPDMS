-- F-PROJ-001 / PM-01 / PM-03
-- Direct in-place cutover: one physical truth per object, no parallel pms_* model.

RENAME TABLE
    pms_project TO proj_project,
    pms_project_task TO proj_project_task,
    pms_project_team_member TO proj_project_member_assignment,
    pms_project_template TO proj_project_template_revision,
    pms_acc_deliverable_checklist TO acc_project_deliverable;

ALTER TABLE proj_project
    CHANGE COLUMN code project_code VARCHAR(64) NOT NULL,
    CHANGE COLUMN name project_name VARCHAR(255) NOT NULL,
    CHANGE COLUMN path tree_path VARCHAR(1024) NOT NULL DEFAULT '/',
    CHANGE COLUMN depth tree_depth INT UNSIGNED NOT NULL DEFAULT 0,
    CHANGE COLUMN sort tree_sort INT NOT NULL DEFAULT 0,
    CHANGE COLUMN category project_category VARCHAR(32) NULL,
    CHANGE COLUMN manager_user_id manager_id BIGINT NULL,
    CHANGE COLUMN template_id lifecycle_template_id BIGINT NULL,
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN code_root_id BIGINT NULL AFTER project_code,
    ADD COLUMN project_sequence INT UNSIGNED NOT NULL DEFAULT 0 AFTER code_root_id,
    ADD COLUMN code_rule_version VARCHAR(32) NOT NULL DEFAULT 'LEGACY-V1' AFTER project_sequence,
    ADD COLUMN customer_code VARCHAR(64) NULL AFTER customer_id,
    ADD COLUMN customer_name VARCHAR(255) NULL AFTER customer_code,
    ADD COLUMN manager_employee_no VARCHAR(64) NULL AFTER manager_id,
    ADD COLUMN manager_name VARCHAR(128) NULL AFTER manager_employee_no,
    ADD COLUMN major_project_level VARCHAR(64) NULL AFTER major_project_flag,
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' AFTER lifecycle_template_id;

UPDATE proj_project
SET root_id = COALESCE(root_id, id),
    tree_path = CASE WHEN tree_path IS NULL OR tree_path = '/' THEN CONCAT('/', id, '/') ELSE tree_path END,
    code_root_id = id,
    status = CASE status WHEN '0' THEN 'ACTIVE' ELSE status END,
    source_type = CASE WHEN source_system = 'MANUAL' THEN 'MANUAL' ELSE 'INTEGRATION' END,
    major_project_level = CASE WHEN major_project_flag = b'1' THEN 'MAJOR' ELSE NULL END;

ALTER TABLE proj_project
    MODIFY COLUMN root_id BIGINT NOT NULL,
    MODIFY COLUMN code_root_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_proj_project_tenant_row (tenant_id, id),
    ADD UNIQUE KEY uk_proj_project_code (tenant_id, project_code),
    ADD UNIQUE KEY uk_proj_project_code_sequence (tenant_id, code_root_id, project_sequence),
    ADD KEY idx_proj_project_parent (tenant_id, parent_id, tree_sort, id),
    ADD KEY idx_proj_project_path (tenant_id, root_id, tree_path(191));

ALTER TABLE proj_project_task
    CHANGE COLUMN path tree_path VARCHAR(1024) NOT NULL DEFAULT '/',
    CHANGE COLUMN depth tree_depth INT UNSIGNED NOT NULL DEFAULT 0,
    CHANGE COLUMN sort tree_sort INT NOT NULL DEFAULT 0,
    MODIFY COLUMN name VARCHAR(255) NOT NULL,
    MODIFY COLUMN description VARCHAR(1024) NULL,
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0;

UPDATE proj_project_task
SET tree_path = CASE WHEN tree_path IS NULL OR tree_path = '/' THEN CONCAT('/', id, '/') ELSE tree_path END,
    status = CASE status
        WHEN '0' THEN 'DRAFT' WHEN '1' THEN 'PENDING' WHEN '2' THEN 'IN_PROGRESS'
        WHEN '3' THEN 'BLOCKED' WHEN '4' THEN 'PENDING_VERIFICATION'
        WHEN '5' THEN 'COMPLETED' WHEN '6' THEN 'CANCELLED' ELSE status END;

ALTER TABLE proj_project_task
    ADD UNIQUE KEY uk_proj_project_task_tenant_row (tenant_id, id),
    ADD KEY idx_proj_project_task_project (tenant_id, project_id, tree_sort, id),
    ADD KEY idx_proj_project_task_parent (tenant_id, project_id, parent_id, tree_sort, id),
    ADD KEY idx_proj_project_task_path (tenant_id, project_id, tree_path(191));

ALTER TABLE proj_project_member_assignment
    CHANGE COLUMN role_code member_role VARCHAR(64) NOT NULL,
    CHANGE COLUMN remark responsibility VARCHAR(500) NULL,
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN employee_no VARCHAR(64) NULL AFTER user_id,
    ADD COLUMN member_name VARCHAR(128) NULL AFTER employee_no,
    ADD COLUMN effective_from DATETIME(3) NULL AFTER responsibility,
    ADD COLUMN effective_to DATETIME(3) NULL AFTER effective_from,
    ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 0 AFTER status;

UPDATE proj_project_member_assignment
SET status = CASE status WHEN '0' THEN 'ACTIVE' WHEN '1' THEN 'INACTIVE' ELSE status END,
    effective_from = COALESCE(effective_from, create_time);

ALTER TABLE proj_project_member_assignment
    ADD UNIQUE KEY uk_proj_project_member_tenant_row (tenant_id, id),
    ADD KEY idx_proj_project_member_user (tenant_id, user_id, status, project_id);

ALTER TABLE proj_project_template_revision
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN revision_no INT UNSIGNED NOT NULL DEFAULT 1 AFTER code,
    ADD COLUMN published_at DATETIME(3) NULL AFTER snapshot_json,
    ADD UNIQUE KEY uk_proj_template_revision_tenant_row (tenant_id, id),
    ADD UNIQUE KEY uk_proj_template_revision (tenant_id, code, revision_no);

CREATE TABLE proj_project_template_task_definition (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_revision_id BIGINT NOT NULL,
    stage_definition_key VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    task_definition_key VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    parent_task_definition_key VARCHAR(128) COLLATE utf8mb4_0900_bin NULL,
    name VARCHAR(255) NOT NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    work_binding_type_code VARCHAR(32) COLLATE utf8mb4_0900_bin NOT NULL,
    target_context_code VARCHAR(32) COLLATE utf8mb4_0900_bin NULL,
    target_object_type VARCHAR(64) COLLATE utf8mb4_0900_bin NULL,
    target_object_key VARCHAR(128) COLLATE utf8mb4_0900_bin NULL,
    binding_config JSON NOT NULL,
    permission_policy_ref VARCHAR(512) COLLATE utf8mb4_0900_bin NOT NULL,
    completion_rule_type_code VARCHAR(32) COLLATE utf8mb4_0900_bin NOT NULL,
    completion_rule_config JSON NOT NULL,
    gate_ref VARCHAR(512) COLLATE utf8mb4_0900_bin NULL,
    definition_version INT UNSIGNED NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater BIGINT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_proj_template_task_tenant_row (tenant_id, id),
    UNIQUE KEY uk_proj_template_task_definition (tenant_id, template_revision_id, task_definition_key),
    KEY idx_proj_template_task_tree (
        tenant_id, template_revision_id, stage_definition_key, parent_task_definition_key, sort_order
    ),
    CONSTRAINT fk_proj_template_task_revision FOREIGN KEY (tenant_id, template_revision_id)
        REFERENCES proj_project_template_revision (tenant_id, id),
    CONSTRAINT chk_proj_template_task_version CHECK (definition_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='项目模板版本内的Stage-Task执行定义，发布后不可覆盖';

CREATE TABLE proj_project_task_execution_contract (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_task_id BIGINT NOT NULL,
    template_task_definition_id BIGINT NULL,
    work_binding_type_code VARCHAR(32) COLLATE utf8mb4_0900_bin NOT NULL,
    target_context_code VARCHAR(32) COLLATE utf8mb4_0900_bin NULL,
    target_object_type VARCHAR(64) COLLATE utf8mb4_0900_bin NULL,
    target_object_key VARCHAR(128) COLLATE utf8mb4_0900_bin NULL,
    binding_parameter_snapshot JSON NOT NULL,
    permission_policy_ref VARCHAR(512) COLLATE utf8mb4_0900_bin NOT NULL,
    completion_rule_type_code VARCHAR(32) COLLATE utf8mb4_0900_bin NOT NULL,
    completion_rule_snapshot JSON NOT NULL,
    gate_ref VARCHAR(512) COLLATE utf8mb4_0900_bin NULL,
    source_definition_version INT UNSIGNED NOT NULL,
    contract_version INT UNSIGNED NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    current_marker TINYINT GENERATED ALWAYS AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END) STORED,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    creator BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updater BIGINT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_proj_task_contract_tenant_row (tenant_id, id),
    UNIQUE KEY uk_proj_task_contract_version (tenant_id, project_task_id, contract_version),
    UNIQUE KEY uk_proj_task_contract_current (tenant_id, project_task_id, current_marker),
    KEY idx_proj_task_contract_target (tenant_id, target_context_code, target_object_type, target_object_key),
    CONSTRAINT fk_proj_task_contract_task FOREIGN KEY (tenant_id, project_task_id)
        REFERENCES proj_project_task (tenant_id, id),
    CONSTRAINT fk_proj_task_contract_definition FOREIGN KEY (tenant_id, template_task_definition_id)
        REFERENCES proj_project_template_task_definition (tenant_id, id),
    CONSTRAINT chk_proj_task_contract_version CHECK (source_definition_version > 0 AND contract_version > 0),
    CONSTRAINT chk_proj_task_contract_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='ProjectTask当前及历史执行契约';

ALTER TABLE acc_project_deliverable
    MODIFY COLUMN name VARCHAR(255) NOT NULL,
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    MODIFY COLUMN deleted TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN template_id BIGINT NULL AFTER project_id,
    ADD COLUMN document_id BIGINT NULL AFTER deliverable_url,
    ADD COLUMN planned_due_date DATE NULL AFTER document_id,
    ADD COLUMN submit_time DATETIME(3) NULL AFTER planned_due_date,
    ADD COLUMN accepted_time DATETIME(3) NULL AFTER submit_time,
    ADD COLUMN owner_id BIGINT NULL AFTER accepted_time;

UPDATE acc_project_deliverable
SET status = CASE status
    WHEN '0' THEN 'PENDING' WHEN '1' THEN 'SUBMITTED'
    WHEN '2' THEN 'ACCEPTED' WHEN '3' THEN 'REJECTED' ELSE status END;

ALTER TABLE acc_project_deliverable
    ADD UNIQUE KEY uk_acc_project_deliverable_tenant_row (tenant_id, id),
    ADD KEY idx_acc_project_deliverable (tenant_id, project_id, deliverable_type, status),
    ADD KEY idx_acc_deliverable_owner (tenant_id, owner_id, status, planned_due_date);
