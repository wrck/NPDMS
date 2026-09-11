-- F-PROJ-009 / PM-03: Template Runtime V2
-- DesignerDocument -> Compiler -> immutable ExecutionSnapshot.
-- Existing projects and legacy published template rows are not rewritten or inferred.

ALTER TABLE `proj_project_template_revision`
    ADD COLUMN `designer_schema_version` INT UNSIGNED NULL AFTER `definition_snapshot`,
    ADD COLUMN `designer_document` JSON NULL AFTER `designer_schema_version`,
    ADD COLUMN `execution_schema_version` INT UNSIGNED NULL AFTER `designer_document`,
    ADD COLUMN `execution_snapshot` JSON NULL AFTER `execution_schema_version`,
    ADD COLUMN `compiler_version` VARCHAR(64) NULL AFTER `execution_snapshot`,
    ADD COLUMN `snapshot_hash` CHAR(64) NULL AFTER `compiler_version`,
    ADD CONSTRAINT `ck_ptr_v2_designer_schema`
        CHECK (`designer_schema_version` IS NULL OR `designer_schema_version` >= 2),
    ADD CONSTRAINT `ck_ptr_v2_execution_schema`
        CHECK (`execution_schema_version` IS NULL OR `execution_schema_version` >= 2),
    ADD CONSTRAINT `ck_ptr_v2_published_snapshot`
        CHECK (`execution_snapshot` IS NULL OR (`status` = 'PUBLISHED' AND `execution_schema_version` IS NOT NULL));

-- Stage runtime contracts become self-contained. Legacy revision ids stay only as provenance.
ALTER TABLE `proj_project_stage_execution_contract`
    ADD COLUMN `source_node_key` VARCHAR(128) NULL AFTER `stage_id`,
    ADD COLUMN `permission_snapshot` JSON NULL AFTER `binding_snapshot`,
    ADD COLUMN `completion_rule_snapshot` JSON NULL AFTER `permission_policy_revision_id`,
    MODIFY COLUMN `definition_revision_id` BIGINT NULL,
    MODIFY COLUMN `work_binding_revision_id` BIGINT NULL,
    MODIFY COLUMN `permission_policy_revision_id` BIGINT NULL,
    MODIFY COLUMN `completion_rule_revision_id` BIGINT NULL,
    MODIFY COLUMN `definition_snapshot` JSON NULL,
    ADD KEY `idx_psec_source_node` (`tenant_id`, `project_id`, `source_node_key`);

-- ProjectTask keeps the existing execution-contract table, but V2 identifies the compiled node
-- directly and freezes BusinessView/permission requirements. No legacy template row is required.
ALTER TABLE `proj_project_task_execution_contract`
    ADD COLUMN `source_node_key` VARCHAR(128) NULL AFTER `project_task_id`,
    ADD COLUMN `binding_view_snapshot` JSON NULL AFTER `binding_parameter_snapshot`,
    ADD COLUMN `permission_snapshot` JSON NULL AFTER `permission_policy_ref`,
    ADD KEY `idx_ptec_source_node` (`tenant_id`, `project_task_id`, `source_node_key`);

-- A compiled edge has its own stable key. Source row/revision ids are optional provenance only.
ALTER TABLE `proj_project_stage_transition`
    ADD COLUMN `source_transition_key` VARCHAR(128) NULL AFTER `template_revision_id`,
    MODIFY COLUMN `source_transition_id` BIGINT NULL,
    MODIFY COLUMN `transition_revision` BIGINT NULL,
    DROP CHECK `ck_pst_condition`,
    ADD CONSTRAINT `ck_pst_condition`
        CHECK (`condition_rule_revision_id` IS NULL OR `condition_snapshot` IS NOT NULL),
    ADD UNIQUE KEY `uk_pst_source_key`
        (`tenant_id`, `project_id`, `graph_version`, `source_transition_key`);
