-- F-PROJ-009 / PM-03
-- V2 authoring -> compiler -> immutable execution snapshot.
-- Forward-only: existing published templates and project runtime rows are not rewritten.

ALTER TABLE `proj_project_template_revision`
    ADD COLUMN `designer_schema_version` INT UNSIGNED NULL AFTER `definition_snapshot`,
    ADD COLUMN `designer_document` JSON NULL AFTER `designer_schema_version`,
    ADD COLUMN `execution_schema_version` INT UNSIGNED NULL AFTER `designer_document`,
    ADD COLUMN `execution_snapshot` JSON NULL AFTER `execution_schema_version`,
    ADD COLUMN `compiler_version` VARCHAR(64) NULL AFTER `execution_snapshot`,
    ADD COLUMN `snapshot_hash` CHAR(64) NULL AFTER `compiler_version`,
    ADD CONSTRAINT `ck_project_template_revision_designer_v2`
        CHECK ((`designer_document` IS NULL AND `designer_schema_version` IS NULL)
            OR (`designer_document` IS NOT NULL AND `designer_schema_version` > 0)),
    ADD CONSTRAINT `ck_project_template_revision_execution_v2`
        CHECK ((`execution_snapshot` IS NULL AND `execution_schema_version` IS NULL
                AND `compiler_version` IS NULL AND `snapshot_hash` IS NULL)
            OR (`execution_snapshot` IS NOT NULL AND `execution_schema_version` > 0
                AND `compiler_version` IS NOT NULL AND `snapshot_hash` REGEXP '^[0-9a-f]{64}$'));

-- Runtime contracts keep legacy revision ids only as optional source evidence.
-- V2 semantics are carried by stable node keys and immutable snapshots.
ALTER TABLE `proj_project_stage_execution_contract`
    MODIFY COLUMN `definition_revision_id` BIGINT NULL,
    MODIFY COLUMN `work_binding_revision_id` BIGINT NULL,
    MODIFY COLUMN `permission_policy_revision_id` BIGINT NULL,
    MODIFY COLUMN `completion_rule_revision_id` BIGINT NULL,
    ADD COLUMN `source_node_key` VARCHAR(128) NULL AFTER `stage_id`,
    ADD COLUMN `permission_snapshot` JSON NULL AFTER `binding_snapshot`,
    ADD COLUMN `completion_rule_snapshot` JSON NULL AFTER `permission_policy_revision_id`,
    ADD KEY `idx_psec_source_node` (`tenant_id`, `project_id`, `source_node_key`);

ALTER TABLE `proj_project_task_execution_contract`
    ADD COLUMN `source_node_key` VARCHAR(128) NULL AFTER `project_task_id`,
    ADD COLUMN `binding_view_snapshot` JSON NULL AFTER `binding_parameter_snapshot`,
    ADD COLUMN `permission_snapshot` JSON NULL AFTER `permission_policy_ref`,
    ADD KEY `idx_ptc_source_node` (`tenant_id`, `project_task_id`, `source_node_key`);

-- V2 transitions are identified by the compiled stable key. A legacy row may still carry the old id/revision.
ALTER TABLE `proj_project_stage_transition`
    MODIFY COLUMN `source_transition_id` BIGINT NULL,
    MODIFY COLUMN `transition_revision` BIGINT NULL,
    ADD COLUMN `source_transition_key` VARCHAR(128) NULL AFTER `template_revision_id`,
    DROP CHECK `ck_pst_condition`,
    DROP CHECK `ck_pst_default`,
    ADD CONSTRAINT `ck_pst_condition_v2`
        CHECK (`condition_rule_revision_id` IS NULL OR `condition_snapshot` IS NOT NULL),
    ADD CONSTRAINT `ck_pst_default_v2`
        CHECK (`is_default` IN (0,1)
            AND (`is_default` = 0 OR (`condition_rule_revision_id` IS NULL AND `condition_snapshot` IS NULL))),
    ADD KEY `idx_pst_source_key` (`tenant_id`, `project_id`, `graph_version`, `source_transition_key`);
