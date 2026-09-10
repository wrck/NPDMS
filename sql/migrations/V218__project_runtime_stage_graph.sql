-- PM-01 / PM-03 / PM-11: freeze new project runtime graphs. No historical edges or status migration.
ALTER TABLE proj_project_stage
    ADD COLUMN definition_revision_id BIGINT NULL,
    ADD COLUMN graph_version BIGINT NULL,
    ADD COLUMN start_node BIT(1) NULL,
    ADD COLUMN terminal_node BIT(1) NULL;

ALTER TABLE proj_project_task_execution_contract
    ADD COLUMN definition_revision_id BIGINT NULL,
    ADD COLUMN work_binding_revision_id BIGINT NULL,
    ADD COLUMN permission_policy_revision_id BIGINT NULL,
    ADD COLUMN completion_rule_revision_id BIGINT NULL,
    ADD COLUMN definition_snapshot JSON NULL;

CREATE TABLE proj_project_stage_transition (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    template_revision_id BIGINT NOT NULL,
    source_transition_id BIGINT NOT NULL,
    transition_code VARCHAR(128) NOT NULL,
    transition_revision BIGINT NOT NULL,
    from_stage_id BIGINT NOT NULL,
    to_stage_id BIGINT NOT NULL,
    priority INT NOT NULL,
    is_default BIT(1) NOT NULL,
    default_marker TINYINT GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN 1 ELSE NULL END) STORED,
    condition_rule_revision_id BIGINT NULL,
    condition_snapshot JSON NULL,
    graph_version BIGINT NOT NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pst_edge (tenant_id, project_id, graph_version, source_transition_id),
    UNIQUE KEY uk_pst_code (tenant_id, project_id, graph_version, transition_code),
    UNIQUE KEY uk_pst_default (tenant_id, project_id, graph_version, from_stage_id, default_marker),
    KEY idx_pst_out (tenant_id, project_id, graph_version, from_stage_id, priority),
    CONSTRAINT ck_pst_versions CHECK (transition_revision > 0 AND graph_version > 0),
    CONSTRAINT ck_pst_self CHECK (from_stage_id <> to_stage_id),
    CONSTRAINT ck_pst_default CHECK (is_default IN (0,1) AND (is_default = 0 OR condition_rule_revision_id IS NULL)),
    CONSTRAINT ck_pst_condition CHECK ((condition_rule_revision_id IS NULL AND condition_snapshot IS NULL)
        OR (condition_rule_revision_id IS NOT NULL AND condition_snapshot IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE proj_project_stage_execution_contract (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    graph_version BIGINT NOT NULL,
    definition_revision_id BIGINT NOT NULL,
    work_binding_revision_id BIGINT NOT NULL,
    binding_version INT NOT NULL,
    binding_type VARCHAR(32) NOT NULL,
    binding_snapshot JSON NOT NULL,
    permission_policy_revision_id BIGINT NOT NULL,
    completion_rule_revision_id BIGINT NOT NULL,
    definition_snapshot JSON NOT NULL,
    effective_from DATETIME NOT NULL,
    effective_to DATETIME NULL,
    current_marker TINYINT GENERATED ALWAYS AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END) STORED,
    version INT NOT NULL DEFAULT 0,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_psec_version (tenant_id, project_id, stage_id, binding_version),
    UNIQUE KEY uk_psec_current (tenant_id, project_id, stage_id, current_marker),
    KEY idx_psec_project (tenant_id, project_id, stage_id),
    CONSTRAINT ck_psec_version CHECK (binding_version > 0 AND graph_version > 0),
    CONSTRAINT ck_psec_time CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_psec_type CHECK (binding_type IN ('STAGE_NATIVE','BUSINESS_OBJECT','BUSINESS_COMPONENT','DYNAMIC_FORM','APPROVAL','COMPOSITE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
