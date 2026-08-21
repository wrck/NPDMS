-- F-PROJ-001 / PM-03 / PM-11 / CUT-03 / INT-12
-- ADR-0030 六张正式物理载体的前向落地。
-- V52 已建立模板任务定义旧结构，因此本迁移仅增量扩展；不删除、改名或回填存量数据。

ALTER TABLE `proj_project_template_task_definition`
    ADD COLUMN `stage_definition_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin
        GENERATED ALWAYS AS (`stage_code`) STORED,
    ADD COLUMN `task_definition_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin
        GENERATED ALWAYS AS (`task_code`) STORED,
    ADD COLUMN `parent_task_definition_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin
        GENERATED ALWAYS AS (`parent_task_code`) STORED,
    ADD COLUMN `work_binding_type_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin
        NOT NULL DEFAULT 'TASK_NATIVE',
    ADD COLUMN `target_context_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    ADD COLUMN `target_object_type` VARCHAR(64)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    ADD COLUMN `target_object_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    ADD COLUMN `component_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    ADD COLUMN `dynamic_form_revision_id` BIGINT NULL,
    ADD COLUMN `approval_definition_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    ADD COLUMN `binding_config` JSON NOT NULL
        DEFAULT (JSON_OBJECT('schemaVersion', 1)),
    ADD COLUMN `permission_policy_ref` VARCHAR(512)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin
        NOT NULL DEFAULT 'PROJECT_TASK_DEFAULT',
    ADD COLUMN `completion_rule_type_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin
        NOT NULL DEFAULT 'TASK_NATIVE',
    ADD COLUMN `completion_rule_config` JSON NOT NULL
        DEFAULT (JSON_OBJECT('schemaVersion', 1)),
    ADD COLUMN `gate_ref` VARCHAR(512)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    ADD COLUMN `definition_version` INT UNSIGNED NOT NULL DEFAULT 1,
    ADD COLUMN `version` INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '草稿阶段乐观锁版本',
    ADD UNIQUE KEY `uk_project_template_task_definition_tenant_row` (`tenant_id`, `id`),
    ADD UNIQUE KEY `uk_project_template_task_definition`
        (`tenant_id`, `template_revision_id`, `task_definition_key`),
    ADD KEY `idx_project_template_task_definition_tree`
        (`tenant_id`, `template_revision_id`, `stage_definition_key`,
         `parent_task_definition_key`, `sort_order`),
    ADD CONSTRAINT `chk_project_template_task_definition_version`
        CHECK (`definition_version` > 0);

CREATE TABLE `proj_project_task_execution_contract` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `project_task_id` BIGINT NOT NULL COMMENT 'ProjectTask逻辑引用',
    `template_task_definition_id` BIGINT NULL COMMENT '来源模板任务定义逻辑引用',
    `work_binding_type_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `target_context_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `target_object_type` VARCHAR(64)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `target_object_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `component_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `dynamic_form_revision_id` BIGINT NULL,
    `approval_instance_id` BIGINT NULL,
    `binding_parameter_snapshot` JSON NOT NULL COMMENT '冻结绑定参数与Schema版本',
    `permission_policy_ref` VARCHAR(512)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `completion_rule_type_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `completion_rule_snapshot` JSON NOT NULL COMMENT '冻结完成规则与Schema版本',
    `gate_ref` VARCHAR(512)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `source_definition_version` INT UNSIGNED NOT NULL,
    `contract_version` INT UNSIGNED NOT NULL,
    `effective_from` DATETIME(3) NOT NULL,
    `effective_to` DATETIME(3) NULL,
    `current_marker` TINYINT GENERATED ALWAYS AS (
        CASE WHEN `effective_to` IS NULL THEN 1 ELSE NULL END
    ) STORED,
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '受控换绑乐观锁版本',
    `creator` BIGINT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` BIGINT NULL,
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_task_execution_contract_tenant_row` (`tenant_id`, `id`),
    UNIQUE KEY `uk_project_task_execution_contract_version`
        (`tenant_id`, `project_task_id`, `contract_version`),
    UNIQUE KEY `uk_project_task_execution_contract_current`
        (`tenant_id`, `project_task_id`, `current_marker`),
    KEY `idx_project_task_execution_contract_target`
        (`tenant_id`, `target_context_code`, `target_object_type`, `target_object_key`),
    CONSTRAINT `fk_project_task_execution_contract_definition`
        FOREIGN KEY (`tenant_id`, `template_task_definition_id`)
        REFERENCES `proj_project_template_task_definition` (`tenant_id`, `id`),
    CONSTRAINT `chk_project_task_execution_contract_version`
        CHECK (`source_definition_version` > 0 AND `contract_version` > 0),
    CONSTRAINT `chk_project_task_execution_contract_dates`
        CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='ProjectTask当前及历史WorkBinding、权限与完成规则冻结契约';

CREATE TABLE `proj_project_task_completion_evaluation` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `project_task_id` BIGINT NOT NULL COMMENT 'ProjectTask逻辑引用',
    `execution_contract_id` BIGINT NOT NULL COMMENT '执行契约版本逻辑引用',
    `task_version` INT UNSIGNED NOT NULL,
    `contract_version` INT UNSIGNED NOT NULL,
    `fact_context_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `fact_object_type` VARCHAR(64)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `fact_object_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `fact_version` VARCHAR(64)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `evaluation_result_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `unmet_item_snapshot` JSON NULL,
    `gate_snapshot_ref` VARCHAR(512)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `command_id` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `idempotency_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `evaluated_by` BIGINT NOT NULL,
    `evaluated_at` DATETIME(3) NOT NULL,
    `creator` BIGINT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_task_completion_evaluation_tenant_row` (`tenant_id`, `id`),
    UNIQUE KEY `uk_project_task_completion_evaluation_idempotency`
        (`tenant_id`, `project_task_id`, `idempotency_key`),
    KEY `idx_project_task_completion_evaluation_time`
        (`tenant_id`, `project_task_id`, `evaluated_at`, `id`),
    CONSTRAINT `fk_project_task_completion_evaluation_contract`
        FOREIGN KEY (`tenant_id`, `execution_contract_id`)
        REFERENCES `proj_project_task_execution_contract` (`tenant_id`, `id`),
    CONSTRAINT `chk_project_task_completion_evaluation_version`
        CHECK (`task_version` > 0 AND `contract_version` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='ProjectTask完成规则对绑定事实的不可覆盖判定记录';

CREATE TABLE `cut_cutover_checklist` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `cutover_task_id` BIGINT NOT NULL COMMENT 'CUT-01割接任务逻辑引用',
    `assessment_id` BIGINT NOT NULL COMMENT 'CUT-02等级评估逻辑引用',
    `assessment_version` INT UNSIGNED NOT NULL,
    `checklist_version` INT UNSIGNED NOT NULL,
    `status_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `input_snapshot` JSON NOT NULL COMMENT 'P3规则匹配输入冻结快照',
    `input_snapshot_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `config_revision_snapshot` JSON NOT NULL COMMENT '采集项与匹配配置revision快照',
    `match_trace` JSON NOT NULL COMMENT '逐项规则匹配轨迹',
    `config_gap_snapshot` JSON NULL COMMENT '无匹配规则时的配置缺口快照',
    `submitted_by` BIGINT NULL,
    `submitted_at` DATETIME(3) NULL,
    `invalidated_at` DATETIME(3) NULL,
    `invalidated_reason` VARCHAR(1000) NULL,
    `current_marker` TINYINT GENERATED ALWAYS AS (
        CASE WHEN `invalidated_at` IS NULL THEN 1 ELSE NULL END
    ) STORED,
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '草稿重匹配乐观锁版本',
    `creator` BIGINT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` BIGINT NULL,
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cutover_checklist_tenant_row` (`tenant_id`, `id`),
    UNIQUE KEY `uk_cutover_checklist_version`
        (`tenant_id`, `cutover_task_id`, `checklist_version`),
    UNIQUE KEY `uk_cutover_checklist_current`
        (`tenant_id`, `cutover_task_id`, `current_marker`),
    KEY `idx_cutover_checklist_assessment`
        (`tenant_id`, `assessment_id`, `assessment_version`),
    CONSTRAINT `chk_cutover_checklist_version`
        CHECK (`assessment_version` > 0 AND `checklist_version` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='CUT-03调研及风险考察清单的输入、匹配与配置缺口版本';

CREATE TABLE `cut_cutover_checklist_item` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `checklist_id` BIGINT NOT NULL COMMENT 'CUT-03清单版本ID',
    `stable_item_key` VARCHAR(128)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `item_definition_id` BIGINT NULL COMMENT '系统采集项定义逻辑引用；自定义项为空',
    `item_definition_version` INT UNSIGNED NULL,
    `item_type_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `item_name` VARCHAR(255) NOT NULL,
    `item_description` VARCHAR(2000) NULL,
    `interface_format_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `interface_schema_snapshot` JSON NULL COMMENT '冻结界面与输入Schema',
    `display_condition_snapshot` JSON NULL COMMENT '冻结显示条件',
    `work_mode_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `required_flag` TINYINT NOT NULL DEFAULT 0,
    `source_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    `device_id` BIGINT NULL COMMENT '适用设备逻辑引用',
    `command_template_id` BIGINT NULL COMMENT 'DAC命令模板逻辑引用',
    `matched_rule_id` BIGINT NULL,
    `matched_rule_version` INT UNSIGNED NULL,
    `applicable_flag` TINYINT NOT NULL DEFAULT 1,
    `custom_creator_user_id` BIGINT NULL COMMENT '一线补充自定义项的创建人',
    `sort_order` INT UNSIGNED NOT NULL DEFAULT 0,
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '草稿阶段乐观锁版本',
    `creator` BIGINT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` BIGINT NULL,
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cutover_checklist_item_tenant_row` (`tenant_id`, `id`),
    UNIQUE KEY `uk_cutover_checklist_item_key`
        (`tenant_id`, `checklist_id`, `stable_item_key`),
    KEY `idx_cutover_checklist_item_type`
        (`tenant_id`, `checklist_id`, `item_type_code`, `applicable_flag`, `sort_order`),
    KEY `idx_cutover_checklist_item_device` (`tenant_id`, `device_id`, `checklist_id`),
    CONSTRAINT `fk_cutover_checklist_item_checklist`
        FOREIGN KEY (`tenant_id`, `checklist_id`)
        REFERENCES `cut_cutover_checklist` (`tenant_id`, `id`),
    CONSTRAINT `chk_cutover_checklist_item_required`
        CHECK (`required_flag` IN (0, 1)),
    CONSTRAINT `chk_cutover_checklist_item_applicable`
        CHECK (`applicable_flag` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='CUT-03清单版本内稳定采集项、界面与匹配快照';

CREATE TABLE `cut_cutover_checklist_item_result` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `checklist_item_id` BIGINT NOT NULL COMMENT 'CUT-03清单项ID',
    `result_version` INT UNSIGNED NOT NULL,
    `result_source_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL
        COMMENT '直接填写、自动采集、外部加载或人工降级',
    `answer_snapshot` JSON NULL COMMENT '结构化答案冻结快照',
    `fact_description` TEXT NULL COMMENT '文本事实说明',
    `collection_task_id` BIGINT NULL COMMENT 'DAC CollectionTask逻辑引用',
    `collection_result_reference_id` BIGINT NULL COMMENT 'DAC结果稳定引用',
    `collection_result_version` VARCHAR(64)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `external_source_code` VARCHAR(32)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `query_condition_snapshot` JSON NULL COMMENT '外部加载查询条件脱敏快照',
    `queried_at` DATETIME(3) NULL,
    `load_failure_code` VARCHAR(64)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `manual_evidence_file_reference` VARCHAR(512)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `selection_started_at` DATETIME(3) NOT NULL COMMENT '成为当前选择结果的时间',
    `selection_ended_at` DATETIME(3) NULL COMMENT '受控切换后结束当前选择的时间',
    `selected_by` BIGINT NOT NULL,
    `selection_reason_code` VARCHAR(64)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    `current_marker` TINYINT GENERATED ALWAYS AS (
        CASE WHEN `selection_ended_at` IS NULL THEN 1 ELSE NULL END
    ) STORED,
    `created_by` BIGINT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cutover_checklist_item_result_tenant_row` (`tenant_id`, `id`),
    UNIQUE KEY `uk_cutover_checklist_item_result_version`
        (`tenant_id`, `checklist_item_id`, `result_version`),
    UNIQUE KEY `uk_cutover_checklist_item_result_current`
        (`tenant_id`, `checklist_item_id`, `current_marker`),
    KEY `idx_cutover_checklist_item_result_collection_task`
        (`tenant_id`, `collection_task_id`),
    KEY `idx_cutover_checklist_item_result_selected`
        (`tenant_id`, `checklist_item_id`, `selection_started_at`),
    CONSTRAINT `fk_cutover_checklist_item_result_item`
        FOREIGN KEY (`tenant_id`, `checklist_item_id`)
        REFERENCES `cut_cutover_checklist_item` (`tenant_id`, `id`),
    CONSTRAINT `chk_cutover_checklist_item_result_version`
        CHECK (`result_version` > 0),
    CONSTRAINT `chk_cutover_checklist_item_result_selection`
        CHECK (`selection_ended_at` IS NULL OR `selection_ended_at` >= `selection_started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='CUT-03直接填写、采集、外部加载和人工降级结果的追加事实';
