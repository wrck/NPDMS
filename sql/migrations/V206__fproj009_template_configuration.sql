-- F-PROJ-009 | Owner: PROJ | Requirement: PM-03
-- Source: docs/design/09-database-design.md (exact composition / frozen closure),
-- docs/traceability/sds-revision-016-physical-contract.json.
-- Candidate version only; master integration determines the final Flyway number.
-- Configuration structure only: no runtime graph or StageExecutionContract tables.
-- Q-FPROJ009-001 still blocks historical runtime switching: no inferred graph,
-- history rewrite, template replacement or definition/template seed is performed.

CREATE TABLE `proj_delivery_definition_revision` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `definition_kind` VARCHAR(32) NOT NULL,
    `definition_code` VARCHAR(64) NOT NULL,
    `revision_no` BIGINT UNSIGNED NOT NULL,
    `revision_state` VARCHAR(16) NOT NULL,
    `schema_version` INT UNSIGNED NOT NULL,
    `payload` JSON NOT NULL,
    `published_at` DATETIME(6) NULL,
    `disabled_at` DATETIME(6) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pdd_revision` (`tenant_id`, `definition_kind`, `definition_code`, `revision_no`),
    UNIQUE KEY `uk_pdd_tenant_id` (`tenant_id`, `id`),
    CONSTRAINT `ck_pdd_kind` CHECK (definition_kind IN ('STAGE','TASK','DELIVERABLE','WORK_BINDING','COMPLETION_RULE','PERMISSION_POLICY','GATE','MILESTONE')),
    CONSTRAINT `ck_pdd_version` CHECK (revision_no > 0 AND schema_version > 0),
    CONSTRAINT `ck_pdd_state` CHECK (revision_state IN ('DRAFT','PUBLISHED')),
    CONSTRAINT `ck_pdd_published` CHECK ((revision_state='DRAFT' AND published_at IS NULL) OR (revision_state='PUBLISHED' AND published_at IS NOT NULL)),
    CONSTRAINT `ck_pdd_payload` CHECK (JSON_TYPE(payload) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `proj_delivery_definition_reference` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `owner_revision_id` BIGINT NOT NULL,
    `reference_key` VARCHAR(128) NOT NULL,
    `target_revision_id` BIGINT NOT NULL,
    `version` INT NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pdr_slot` (`tenant_id`, `owner_revision_id`, `reference_key`),
    KEY `idx_pdr_target` (`tenant_id`, `target_revision_id`),
    CONSTRAINT `ck_pdr_not_self` CHECK (owner_revision_id <> target_revision_id),
    CONSTRAINT `fk_pdr_source` FOREIGN KEY (`tenant_id`,`owner_revision_id`) REFERENCES `proj_delivery_definition_revision` (`tenant_id`,`id`),
    CONSTRAINT `fk_pdr_target` FOREIGN KEY (`tenant_id`,`target_revision_id`) REFERENCES `proj_delivery_definition_revision` (`tenant_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `proj_stage_transition_definition` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `template_revision_id` BIGINT NOT NULL,
    `transition_code` VARCHAR(64) NOT NULL,
    `from_stage_code` VARCHAR(32) NOT NULL,
    `to_stage_code` VARCHAR(32) NOT NULL,
    `condition_rule_revision_id` BIGINT NULL,
    `priority` INT NOT NULL,
    `is_default` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `default_marker` TINYINT GENERATED ALWAYS AS (CASE WHEN is_default=1 THEN 1 ELSE NULL END) STORED,
    `revision_no` BIGINT UNSIGNED NOT NULL,
    `version` INT NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_std_code` (`tenant_id`, `template_revision_id`, `transition_code`),
    UNIQUE KEY `uk_std_default` (`tenant_id`, `template_revision_id`, `from_stage_code`, `default_marker`),
    KEY `idx_std_out` (`tenant_id`, `template_revision_id`, `from_stage_code`, `priority`),
    CONSTRAINT `ck_std_flags` CHECK (is_default IN (0,1) AND revision_no > 0),
    CONSTRAINT `ck_std_self` CHECK (from_stage_code <> to_stage_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- SDS09: the existing template identity receives a technical CAS version only.
ALTER TABLE `proj_project_template`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0;

-- All definition/snapshot additions are NULL for existing rows, without inferred defaults.
-- Owner/type/exact-version validation and immutable publication remain service duties.
ALTER TABLE `proj_project_template_revision`
    ADD COLUMN `definition_snapshot` JSON NULL;

ALTER TABLE `proj_project_template_stage_definition`
    ADD COLUMN `definition_revision_id` BIGINT NULL,
    ADD COLUMN `start_node` BIT(1) NULL,
    ADD COLUMN `terminal_node` BIT(1) NULL,
    ADD COLUMN `work_binding_revision_id` BIGINT NULL,
    ADD COLUMN `permission_policy_revision_id` BIGINT NULL,
    ADD COLUMN `completion_rule_revision_id` BIGINT NULL;

ALTER TABLE `proj_project_template_task_definition`
    ADD COLUMN `definition_revision_id` BIGINT NULL,
    ADD COLUMN `work_binding_revision_id` BIGINT NULL,
    ADD COLUMN `permission_policy_revision_id` BIGINT NULL,
    ADD COLUMN `completion_rule_revision_id` BIGINT NULL;

ALTER TABLE `proj_project_template_milestone_definition`
    ADD COLUMN `definition_revision_id` BIGINT NULL;

ALTER TABLE `proj_project_template_deliverable_definition`
    ADD COLUMN `definition_revision_id` BIGINT NULL;

ALTER TABLE `proj_project_template_gate_definition`
    ADD COLUMN `definition_revision_id` BIGINT NULL;
