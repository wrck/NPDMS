-- Generated from docs/traceability/sds-revision-016-physical-contract.json.
-- Prospective SDS carrier design only. NOT a Flyway migration or upgrade script.
-- Execute only in a new isolated validation schema; no production authorization.
-- Cross-Context references are logical and MUST be revalidated through Owner APIs.
SET NAMES utf8mb4;

-- Owner PROJ; Requirements: PM-03
CREATE TABLE `proj_delivery_definition_revision` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `definition_kind` VARCHAR(32) NOT NULL,
  `definition_code` VARCHAR(64) NOT NULL,
  `revision_no` BIGINT UNSIGNED NOT NULL,
  `revision_state` VARCHAR(16) NOT NULL,
  `schema_version` INT UNSIGNED NOT NULL,
  `payload` JSON NOT NULL,
  `published_at` DATETIME(6) NULL,
  `disabled_at` DATETIME(6) NULL,
  `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pdd_revision` (`tenant_id`, `definition_kind`, `definition_code`, `revision_no`),
  UNIQUE KEY `uk_pdd_tenant_id` (`tenant_id`, `id`),
  CONSTRAINT `ck_pdd_kind` CHECK (definition_kind IN ('STAGE','TASK','DELIVERABLE','WORK_BINDING','COMPLETION_RULE','PERMISSION_POLICY','GATE','MILESTONE')),
  CONSTRAINT `ck_pdd_version` CHECK (revision_no > 0 AND schema_version > 0),
  CONSTRAINT `ck_pdd_state` CHECK (revision_state IN ('DRAFT','PUBLISHED')),
  CONSTRAINT `ck_pdd_published` CHECK ((revision_state='DRAFT' AND published_at IS NULL) OR (revision_state='PUBLISHED' AND published_at IS NOT NULL)),
  CONSTRAINT `ck_pdd_payload` CHECK (JSON_TYPE(payload) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner PROJ; Requirements: PM-03
CREATE TABLE `proj_delivery_definition_reference` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `owner_revision_id` BIGINT UNSIGNED NOT NULL,
  `reference_key` VARCHAR(128) NOT NULL,
  `target_revision_id` BIGINT UNSIGNED NOT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pdr_slot` (`tenant_id`, `owner_revision_id`, `reference_key`),
  KEY `idx_pdr_target` (`tenant_id`, `target_revision_id`),
  CONSTRAINT `ck_pdr_not_self` CHECK (owner_revision_id <> target_revision_id),
  CONSTRAINT `fk_pdr_source` FOREIGN KEY (`tenant_id`,`owner_revision_id`) REFERENCES `proj_delivery_definition_revision` (`tenant_id`,`id`),
  CONSTRAINT `fk_pdr_target` FOREIGN KEY (`tenant_id`,`target_revision_id`) REFERENCES `proj_delivery_definition_revision` (`tenant_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner PROJ; Requirements: PM-03
CREATE TABLE `proj_stage_transition_definition` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `template_revision_id` BIGINT UNSIGNED NOT NULL,
  `transition_code` VARCHAR(64) NOT NULL,
  `from_stage_code` VARCHAR(32) NOT NULL,
  `to_stage_code` VARCHAR(32) NOT NULL,
  `condition_rule_revision_id` BIGINT UNSIGNED NULL,
  `priority` INT NOT NULL,
  `is_default` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `default_marker` TINYINT GENERATED ALWAYS AS (CASE WHEN is_default=1 THEN 1 ELSE NULL END) STORED,
  `revision_no` BIGINT UNSIGNED NOT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_std_code` (`tenant_id`, `template_revision_id`, `transition_code`),
  UNIQUE KEY `uk_std_default` (`tenant_id`, `template_revision_id`, `from_stage_code`, `default_marker`),
  KEY `idx_std_out` (`tenant_id`, `template_revision_id`, `from_stage_code`, `priority`),
  CONSTRAINT `ck_std_flags` CHECK (is_default IN (0,1) AND revision_no > 0),
  CONSTRAINT `ck_std_self` CHECK (from_stage_code <> to_stage_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner PROJ; Requirements: PM-03
CREATE TABLE `proj_project_stage_transition` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `project_id` BIGINT UNSIGNED NOT NULL,
  `template_revision_id` BIGINT UNSIGNED NOT NULL,
  `source_transition_id` BIGINT UNSIGNED NOT NULL,
  `transition_revision` BIGINT UNSIGNED NOT NULL,
  `from_stage_id` BIGINT UNSIGNED NOT NULL,
  `to_stage_id` BIGINT UNSIGNED NOT NULL,
  `priority` INT NOT NULL,
  `is_default` TINYINT UNSIGNED NOT NULL,
  `default_marker` TINYINT GENERATED ALWAYS AS (CASE WHEN is_default=1 THEN 1 ELSE NULL END) STORED,
  `condition_snapshot` JSON NOT NULL,
  `graph_version` BIGINT UNSIGNED NOT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pst_edge` (`tenant_id`, `project_id`, `graph_version`, `source_transition_id`),
  UNIQUE KEY `uk_pst_default` (`tenant_id`, `project_id`, `graph_version`, `from_stage_id`, `default_marker`),
  KEY `idx_pst_out` (`tenant_id`, `project_id`, `graph_version`, `from_stage_id`, `priority`),
  CONSTRAINT `ck_pst_versions` CHECK (transition_revision > 0 AND graph_version > 0),
  CONSTRAINT `ck_pst_self` CHECK (from_stage_id <> to_stage_id),
  CONSTRAINT `ck_pst_default` CHECK (is_default IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner PROJ; Requirements: PM-03, PM-11
CREATE TABLE `proj_project_stage_execution_contract` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `project_id` BIGINT UNSIGNED NOT NULL,
  `stage_id` BIGINT UNSIGNED NOT NULL,
  `binding_version` BIGINT UNSIGNED NOT NULL,
  `binding_type` VARCHAR(32) NOT NULL,
  `binding_snapshot` JSON NOT NULL,
  `permission_policy_revision_id` BIGINT UNSIGNED NOT NULL,
  `completion_rule_revision_id` BIGINT UNSIGNED NOT NULL,
  `effective_from` DATETIME(6) NOT NULL,
  `effective_to` DATETIME(6) NULL,
  `current_marker` TINYINT GENERATED ALWAYS AS (CASE WHEN effective_to IS NULL THEN 1 ELSE NULL END) STORED,
  `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_psec_version` (`tenant_id`, `stage_id`, `binding_version`),
  UNIQUE KEY `uk_psec_current` (`tenant_id`, `stage_id`, `current_marker`),
  KEY `idx_psec_project` (`tenant_id`, `project_id`, `stage_id`),
  CONSTRAINT `ck_psec_version` CHECK (binding_version > 0),
  CONSTRAINT `ck_psec_time` CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CONSTRAINT `ck_psec_type` CHECK (binding_type IN ('STAGE_NATIVE','BUSINESS_OBJECT','BUSINESS_COMPONENT','DYNAMIC_FORM','APPROVAL','COMPOSITE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner PLT; Requirements: PM-03, PM-11
CREATE TABLE `plt_business_view_revision` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `entity_type` VARCHAR(64) NOT NULL,
  `view_key` VARCHAR(128) NOT NULL,
  `revision_no` BIGINT UNSIGNED NOT NULL,
  `owner_context` VARCHAR(32) NOT NULL,
  `component_key` VARCHAR(128) NOT NULL,
  `component_version` VARCHAR(64) NOT NULL,
  `context_schema` JSON NOT NULL,
  `supported_actions` JSON NOT NULL,
  `query_provider_key` VARCHAR(128) NOT NULL,
  `command_provider_key` VARCHAR(128) NOT NULL,
  `permission_provider_key` VARCHAR(128) NOT NULL,
  `published_at` DATETIME(6) NOT NULL,
  `disabled_at` DATETIME(6) NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bvr_identity` (`tenant_id`, `entity_type`, `view_key`, `revision_no`),
  CONSTRAINT `ck_bvr_version` CHECK (revision_no > 0),
  CONSTRAINT `ck_bvr_actions` CHECK (JSON_TYPE(supported_actions)='ARRAY'),
  CONSTRAINT `ck_bvr_schema` CHECK (JSON_TYPE(context_schema)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner PROJ; Requirements: PM-06
CREATE TABLE `proj_contract_scope_append_request` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `project_id` BIGINT UNSIGNED NOT NULL,
  `operation_id` VARCHAR(64) NOT NULL,
  `request_digest` CHAR(64) NOT NULL,
  `request_revision` BIGINT UNSIGNED NOT NULL,
  `expected_project_version` BIGINT UNSIGNED NOT NULL,
  `expected_scope_version` BIGINT UNSIGNED NOT NULL,
  `request_snapshot` JSON NOT NULL,
  `impact_snapshot` JSON NOT NULL,
  `bpm_process_definition_key` VARCHAR(128) NULL,
  `actual_process_definition_id` VARCHAR(128) NULL,
  `process_instance_id` VARCHAR(128) NULL,
  `approval_fact_ref` VARCHAR(128) NULL,
  `apply_state` VARCHAR(16) NOT NULL DEFAULT 'NOT_APPLIED',
  `applied_scope_version` BIGINT UNSIGNED NULL,
  `applied_at` DATETIME(6) NULL,
  `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_csar_operation` (`tenant_id`, `project_id`, `operation_id`),
  KEY `idx_csar_project` (`tenant_id`, `project_id`, `created_at`, `id`),
  CONSTRAINT `ck_csar_revision` CHECK (request_revision > 0),
  CONSTRAINT `ck_csar_apply` CHECK ((apply_state='NOT_APPLIED' AND applied_scope_version IS NULL AND applied_at IS NULL) OR (apply_state='APPLIED' AND applied_scope_version IS NOT NULL AND applied_scope_version > expected_scope_version AND applied_at IS NOT NULL AND approval_fact_ref IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner COM; Requirements: COM-01, PM-06
CREATE TABLE `com_delivery_scope_project_version` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `project_id` BIGINT UNSIGNED NOT NULL,
  `scope_version` BIGINT UNSIGNED NOT NULL,
  `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cspv_project` (`tenant_id`, `project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner COM; Requirements: COM-01, PM-06
CREATE TABLE `com_project_scope_revision` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `project_id` BIGINT UNSIGNED NOT NULL,
  `scope_version` BIGINT UNSIGNED NOT NULL,
  `previous_scope_version` BIGINT UNSIGNED NULL,
  `origin_context` VARCHAR(32) NOT NULL,
  `origin_record_id` BIGINT UNSIGNED NOT NULL,
  `origin_revision` BIGINT UNSIGNED NOT NULL,
  `scope_snapshot` JSON NOT NULL,
  `scope_digest` CHAR(64) NOT NULL,
  `difference_snapshot` JSON NOT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpsr_version` (`tenant_id`, `project_id`, `scope_version`),
  UNIQUE KEY `uk_cpsr_source` (`tenant_id`, `origin_context`, `origin_record_id`, `origin_revision`),
  CONSTRAINT `ck_cpsr_previous` CHECK (previous_scope_version IS NULL OR scope_version > previous_scope_version),
  CONSTRAINT `ck_cpsr_json` CHECK (JSON_TYPE(scope_snapshot)='ARRAY' AND JSON_TYPE(difference_snapshot)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner ACC; Requirements: ACC-03
CREATE TABLE `acc_acceptance_report` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `project_id` BIGINT UNSIGNED NOT NULL,
  `report_type` VARCHAR(16) NOT NULL,
  `current_revision_id` BIGINT UNSIGNED NULL,
  `row_version` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aar_type` (`tenant_id`, `project_id`, `report_type`),
  UNIQUE KEY `uk_aar_tenant_id` (`tenant_id`, `id`),
  CONSTRAINT `ck_aar_type` CHECK (report_type IN ('PRELIMINARY','FINAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner ACC; Requirements: ACC-03
CREATE TABLE `acc_acceptance_report_revision` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `report_id` BIGINT UNSIGNED NOT NULL,
  `revision_no` BIGINT UNSIGNED NOT NULL,
  `project_scope_version` BIGINT UNSIGNED NOT NULL,
  `scope_revision_id` BIGINT UNSIGNED NOT NULL,
  `scope_digest` CHAR(64) NOT NULL,
  `conclusion_code` VARCHAR(64) NOT NULL,
  `accepted_at` DATETIME(6) NOT NULL,
  `acceptor_reference` VARCHAR(255) NOT NULL,
  `file_artifact_id` BIGINT UNSIGNED NOT NULL,
  `file_version` BIGINT UNSIGNED NOT NULL,
  `file_digest` CHAR(64) NOT NULL,
  `template_revision_id` BIGINT UNSIGNED NOT NULL,
  `initial_report_revision_id` BIGINT UNSIGNED NULL,
  `source_evidence_refs` JSON NOT NULL,
  `supersedes_revision_id` BIGINT UNSIGNED NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aarr_version` (`tenant_id`, `report_id`, `revision_no`),
  KEY `idx_aarr_scope` (`tenant_id`, `scope_revision_id`),
  CONSTRAINT `ck_aarr_version` CHECK (revision_no > 0 AND file_version > 0),
  CONSTRAINT `fk_aarr_report` FOREIGN KEY (`tenant_id`,`report_id`) REFERENCES `acc_acceptance_report` (`tenant_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner PROJ; Requirements: CLO-02, PM-10
CREATE TABLE `proj_project_exit_record` (
  `id` BIGINT UNSIGNED NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `project_id` BIGINT UNSIGNED NOT NULL,
  `project_version` BIGINT UNSIGNED NOT NULL,
  `closure_type` VARCHAR(16) NOT NULL,
  `closed_from_stage` VARCHAR(32) NOT NULL,
  `stage_instance_id` BIGINT UNSIGNED NOT NULL,
  `source_context` VARCHAR(16) NOT NULL,
  `source_record_id` BIGINT UNSIGNED NOT NULL,
  `source_record_revision` BIGINT UNSIGNED NOT NULL,
  `template_revision_id` BIGINT UNSIGNED NOT NULL,
  `scope_version` BIGINT UNSIGNED NOT NULL,
  `gate_snapshot_ref` VARCHAR(128) NOT NULL,
  `closed_at` DATETIME(6) NOT NULL,
  `created_by` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_per_project_version` (`tenant_id`, `project_id`, `project_version`),
  UNIQUE KEY `uk_per_source` (`tenant_id`, `source_context`, `source_record_id`, `source_record_revision`),
  CONSTRAINT `ck_per_type` CHECK ((closure_type IN ('NORMAL','NO_TRACKING') AND source_context='ACC') OR (closure_type='EXCEPTION' AND source_context='PROJ')),
  CONSTRAINT `ck_per_stage` CHECK (closed_from_stage IN ('S0','S1','S2','S3','S4','S5','S6'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
