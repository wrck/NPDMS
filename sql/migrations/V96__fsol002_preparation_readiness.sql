-- =============================================================================
-- F-SOL-002 / PRE-02：工勘分工信息采集与实施就绪物理基础。
-- SOL持有六张业务表；project/user/FileArtifact/OA仅保存稳定引用，不建立跨Context外键。
-- =============================================================================

CREATE TABLE `sol_preparation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '准备业务版本ID',
    `project_id` BIGINT NOT NULL COMMENT 'PROJ项目ID',
    `preparation_type_code` VARCHAR(32) NOT NULL COMMENT '准备类型，V1固定PRE_02_SITE_SURVEY',
    `business_version` INT UNSIGNED NOT NULL COMMENT '项目内准备业务版本',
    `current_marker` TINYINT NULL COMMENT '当前版本标记：当前=1，历史=NULL',
    `template_id` BIGINT NOT NULL COMMENT '冻结项目模板ID',
    `template_revision_id` BIGINT NOT NULL COMMENT '冻结项目模板修订ID',
    `template_snapshot` JSON NOT NULL COMMENT '冻结PRE-02模板及WorkBinding事实',
    `fixed_form_catalog_version` INT UNSIGNED NOT NULL COMMENT '固定表单目录版本',
    `status_code` VARCHAR(32) NOT NULL COMMENT '准备生命周期状态',
    `readiness_status_code` VARCHAR(32) NOT NULL COMMENT '当前实施就绪状态',
    `latest_readiness_snapshot_id` BIGINT NULL COMMENT '最新不可变就绪快照ID',
    `input_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '就绪输入版本',
    `readiness_version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '就绪事实版本',
    `snapshot_current` BIT(1) NOT NULL DEFAULT b'0' COMMENT '最新快照是否仍对应当前输入',
    `submitted_at` DATETIME(3) NULL COMMENT '提交确认时间',
    `confirmed_at` DATETIME(3) NULL COMMENT '全部确认时间',
    `returned_at` DATETIME(3) NULL COMMENT '退回时间',
    `return_reason` VARCHAR(1000) NULL COMMENT '退回原因',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_preparation_business_version`
        (`tenant_id`, `project_id`, `preparation_type_code`, `business_version`),
    UNIQUE KEY `uk_sol_preparation_current`
        (`tenant_id`, `project_id`, `preparation_type_code`, `current_marker`),
    UNIQUE KEY `uk_sol_preparation_tenant_row` (`tenant_id`, `id`),
    KEY `idx_sol_preparation_project_status`
        (`tenant_id`, `project_id`, `status_code`, `business_version`),
    CONSTRAINT `chk_sol_preparation_type`
        CHECK (`preparation_type_code` = 'PRE_02_SITE_SURVEY'),
    CONSTRAINT `chk_sol_preparation_business_version`
        CHECK (`business_version` > 0 AND `fixed_form_catalog_version` > 0),
    CONSTRAINT `chk_sol_preparation_current_marker`
        CHECK (`current_marker` IS NULL OR `current_marker` = 1),
    CONSTRAINT `chk_sol_preparation_status`
        CHECK (`status_code` IN ('DRAFT', 'PENDING_CONFIRMATION', 'CONFIRMED', 'RETURNED')),
    CONSTRAINT `chk_sol_preparation_readiness_status`
        CHECK (`readiness_status_code` IN ('NOT_READY', 'READY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL PRE-02准备聚合与当前就绪指针';

CREATE TABLE `sol_preparation_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工勘项ID',
    `preparation_id` BIGINT NOT NULL COMMENT '准备业务版本ID',
    `source_item_id` BIGINT NULL COMMENT '退回新版本的来源工勘项ID',
    `item_code` VARCHAR(64) NOT NULL COMMENT '冻结工勘项编码',
    `item_name` VARCHAR(128) NOT NULL COMMENT '冻结工勘项名称',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '冻结排序',
    `applicability_code` VARCHAR(32) NOT NULL COMMENT '适用性状态',
    `confirmation_status_code` VARCHAR(32) NOT NULL COMMENT '逐项确认状态',
    `form_code` VARCHAR(64) NOT NULL COMMENT '固定表单编码',
    `form_version` INT UNSIGNED NOT NULL COMMENT '固定表单版本',
    `form_schema_snapshot` JSON NOT NULL COMMENT '固定表单Schema快照',
    `evidence_policy_snapshot` JSON NOT NULL COMMENT '证据策略快照',
    `source_policy_snapshot` JSON NOT NULL COMMENT '来源策略快照',
    `waiver_policy_snapshot` JSON NOT NULL COMMENT '豁免策略快照',
    `outsourced` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否外包',
    `assignee_user_id` BIGINT NULL COMMENT '当前负责人用户ID',
    `assignee_effective_from` DATETIME(3) NULL COMMENT '负责人开始生效时间',
    `site_result_code` VARCHAR(64) NULL COMMENT '现场结论编码',
    `site_result_detail` VARCHAR(2000) NULL COMMENT '现场结论说明',
    `evidence_reference_snapshot` JSON NULL COMMENT '精确FileArtifact引用数组',
    `not_applicable_reason` VARCHAR(1000) NULL COMMENT '不适用原因',
    `not_applicable_confirmed_by` BIGINT NULL COMMENT '不适用确认人',
    `not_applicable_confirmed_at` DATETIME(3) NULL COMMENT '不适用确认时间',
    `confirmed_by` BIGINT NULL COMMENT '逐项确认人',
    `confirmed_at` DATETIME(3) NULL COMMENT '逐项确认时间',
    `returned_by` BIGINT NULL COMMENT '逐项退回人',
    `returned_at` DATETIME(3) NULL COMMENT '逐项退回时间',
    `return_reason` VARCHAR(1000) NULL COMMENT '逐项退回原因',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_preparation_item_code` (`tenant_id`, `preparation_id`, `item_code`),
    UNIQUE KEY `uk_sol_preparation_item_tenant_row` (`tenant_id`, `id`),
    KEY `idx_sol_preparation_item_order`
        (`tenant_id`, `preparation_id`, `sort_order`, `item_code`, `id`),
    KEY `idx_sol_preparation_item_assignee`
        (`tenant_id`, `assignee_user_id`, `confirmation_status_code`, `id`),
    CONSTRAINT `fk_sol_preparation_item_preparation`
        FOREIGN KEY (`tenant_id`, `preparation_id`)
        REFERENCES `sol_preparation` (`tenant_id`, `id`),
    CONSTRAINT `fk_sol_preparation_item_source`
        FOREIGN KEY (`tenant_id`, `source_item_id`)
        REFERENCES `sol_preparation_item` (`tenant_id`, `id`),
    CONSTRAINT `chk_sol_preparation_item_form_version` CHECK (`form_version` > 0),
    CONSTRAINT `chk_sol_preparation_item_applicability`
        CHECK (`applicability_code` IN
            ('REQUIRED', 'NOT_APPLICABLE_PENDING', 'NOT_APPLICABLE_CONFIRMED')),
    CONSTRAINT `chk_sol_preparation_item_confirmation`
        CHECK (`confirmation_status_code` IN ('PENDING', 'CONFIRMED', 'RETURNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL PRE-02冻结工勘项与逐项结果';

CREATE TABLE `sol_dynamic_form_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '固定表单实例ID',
    `preparation_id` BIGINT NOT NULL COMMENT '准备业务版本ID',
    `item_id` BIGINT NOT NULL COMMENT '工勘项ID',
    `form_code` VARCHAR(64) NOT NULL COMMENT '固定表单编码',
    `form_version` INT UNSIGNED NOT NULL COMMENT '固定表单版本',
    `schema_snapshot` JSON NOT NULL COMMENT '固定表单Schema快照',
    `value_snapshot` JSON NOT NULL COMMENT '表单值快照',
    `status_code` VARCHAR(32) NOT NULL COMMENT '表单状态',
    `frozen_at` DATETIME(3) NULL COMMENT '冻结时间',
    `frozen_by` BIGINT NULL COMMENT '冻结操作者',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_dynamic_form_instance`
        (`tenant_id`, `preparation_id`, `item_id`, `form_code`),
    UNIQUE KEY `uk_sol_dynamic_form_instance_tenant_row` (`tenant_id`, `id`),
    CONSTRAINT `fk_sol_dynamic_form_instance_preparation`
        FOREIGN KEY (`tenant_id`, `preparation_id`)
        REFERENCES `sol_preparation` (`tenant_id`, `id`),
    CONSTRAINT `fk_sol_dynamic_form_instance_item`
        FOREIGN KEY (`tenant_id`, `item_id`)
        REFERENCES `sol_preparation_item` (`tenant_id`, `id`),
    CONSTRAINT `chk_sol_dynamic_form_instance_version` CHECK (`form_version` > 0),
    CONSTRAINT `chk_sol_dynamic_form_instance_status`
        CHECK (`status_code` IN ('DRAFT', 'FROZEN')),
    CONSTRAINT `chk_sol_dynamic_form_instance_frozen`
        CHECK (`status_code` <> 'FROZEN' OR (`frozen_at` IS NOT NULL AND `frozen_by` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL PRE-02固定表单实例';

CREATE TABLE `sol_preparation_source_reference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权威来源引用ID',
    `preparation_id` BIGINT NOT NULL COMMENT '准备业务版本ID',
    `item_id` BIGINT NOT NULL COMMENT '工勘项ID',
    `source_type_code` VARCHAR(32) NOT NULL COMMENT '来源类型，例OA',
    `source_object_type` VARCHAR(64) NOT NULL COMMENT '来源对象类型',
    `source_object_id` VARCHAR(128) NOT NULL COMMENT '来源对象稳定ID',
    `source_reference_key` VARCHAR(128) NOT NULL COMMENT '来源稳定引用键',
    `required_result_policy_snapshot` JSON NOT NULL COMMENT '来源终态策略快照',
    `normalized_result_code` VARCHAR(64) NULL COMMENT '当前权威归一结果',
    `source_fact_version` VARCHAR(128) NULL COMMENT '当前权威事实版本',
    `source_watermark` VARCHAR(255) NULL COMMENT '当前权威水位',
    `sync_status_code` VARCHAR(32) NOT NULL COMMENT '同步状态',
    `last_success_result_code` VARCHAR(64) NULL COMMENT '最近成功归一结果',
    `last_success_fact_version` VARCHAR(128) NULL COMMENT '最近成功事实版本',
    `last_success_watermark` VARCHAR(255) NULL COMMENT '最近成功水位',
    `last_success_at` DATETIME(3) NULL COMMENT '最近成功时间',
    `last_synced_at` DATETIME(3) NULL COMMENT '最近同步尝试时间',
    `last_sync_error_code` VARCHAR(128) NULL COMMENT '最近同步错误码',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_preparation_source_reference`
        (`tenant_id`, `item_id`, `source_type_code`, `source_reference_key`),
    UNIQUE KEY `uk_sol_preparation_source_tenant_row` (`tenant_id`, `id`),
    KEY `idx_sol_preparation_source_status`
        (`tenant_id`, `preparation_id`, `sync_status_code`, `id`),
    CONSTRAINT `fk_sol_preparation_source_preparation`
        FOREIGN KEY (`tenant_id`, `preparation_id`)
        REFERENCES `sol_preparation` (`tenant_id`, `id`),
    CONSTRAINT `fk_sol_preparation_source_item`
        FOREIGN KEY (`tenant_id`, `item_id`)
        REFERENCES `sol_preparation_item` (`tenant_id`, `id`),
    CONSTRAINT `chk_sol_preparation_source_status`
        CHECK (`sync_status_code` IN ('SYNCED', 'ERROR', 'UNKNOWN')),
    CONSTRAINT `chk_sol_preparation_source_current_fact`
        CHECK (`sync_status_code` <> 'SYNCED'
            OR (`normalized_result_code` IS NOT NULL
                AND `source_fact_version` IS NOT NULL
                AND `source_watermark` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL PRE-02权威来源引用与同步事实';

CREATE TABLE `sol_preparation_item_waiver` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '逐项豁免ID',
    `project_id` BIGINT NOT NULL COMMENT 'PROJ项目ID',
    `preparation_id` BIGINT NOT NULL COMMENT '准备业务版本ID',
    `item_id` BIGINT NOT NULL COMMENT '工勘项ID',
    `item_code` VARCHAR(64) NOT NULL COMMENT '冻结工勘项编码',
    `waiver_no` INT UNSIGNED NOT NULL COMMENT '项目工勘项内豁免序号',
    `status_code` VARCHAR(32) NOT NULL COMMENT '豁免审批状态',
    `blocker_codes_snapshot` JSON NOT NULL COMMENT '允许替代的阻断码快照',
    `reason` VARCHAR(1000) NOT NULL COMMENT '豁免原因',
    `risk` VARCHAR(1000) NOT NULL COMMENT '风险说明',
    `compensation` VARCHAR(1000) NOT NULL COMMENT '补偿措施',
    `valid_from` DATETIME(3) NOT NULL COMMENT '生效时间',
    `valid_until` DATETIME(3) NOT NULL COMMENT '失效时间',
    `approval_role_code` VARCHAR(64) NOT NULL COMMENT '冻结审批角色',
    `applicant_user_id` BIGINT NOT NULL COMMENT '申请人',
    `submitted_at` DATETIME(3) NULL COMMENT '提交时间',
    `decided_by` BIGINT NULL COMMENT '决策人',
    `decided_at` DATETIME(3) NULL COMMENT '决策时间',
    `decision_opinion` VARCHAR(1000) NULL COMMENT '决策意见',
    `withdrawn_at` DATETIME(3) NULL COMMENT '撤回时间',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_preparation_item_waiver`
        (`tenant_id`, `project_id`, `item_code`, `waiver_no`),
    UNIQUE KEY `uk_sol_preparation_waiver_tenant_row` (`tenant_id`, `id`),
    KEY `idx_sol_preparation_waiver_status`
        (`tenant_id`, `preparation_id`, `item_id`, `status_code`, `valid_until`, `id`),
    CONSTRAINT `fk_sol_preparation_waiver_preparation`
        FOREIGN KEY (`tenant_id`, `preparation_id`)
        REFERENCES `sol_preparation` (`tenant_id`, `id`),
    CONSTRAINT `fk_sol_preparation_waiver_item`
        FOREIGN KEY (`tenant_id`, `item_id`)
        REFERENCES `sol_preparation_item` (`tenant_id`, `id`),
    CONSTRAINT `chk_sol_preparation_waiver_status`
        CHECK (`status_code` IN
            ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT `chk_sol_preparation_waiver_window` CHECK (`valid_until` >= `valid_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL PRE-02逐项豁免审批历史';

CREATE TABLE `sol_preparation_readiness_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '就绪快照ID',
    `preparation_id` BIGINT NOT NULL COMMENT '准备业务版本ID',
    `snapshot_no` INT UNSIGNED NOT NULL COMMENT '准备版本内快照序号',
    `result_code` VARCHAR(32) NOT NULL COMMENT '评估结果',
    `rule_version` INT UNSIGNED NOT NULL COMMENT '就绪规则版本',
    `project_scope_version` BIGINT NOT NULL COMMENT '冻结项目范围版本',
    `input_version` INT UNSIGNED NOT NULL COMMENT '冻结输入版本',
    `preparation_version` INT UNSIGNED NOT NULL COMMENT '冻结准备乐观锁版本',
    `readiness_version` INT UNSIGNED NOT NULL COMMENT '冻结就绪事实版本',
    `item_facts_snapshot` JSON NOT NULL COMMENT '逐项事实快照',
    `file_facts_snapshot` JSON NOT NULL COMMENT '文件事实快照',
    `source_facts_snapshot` JSON NOT NULL COMMENT '来源事实快照',
    `waiver_facts_snapshot` JSON NOT NULL COMMENT '豁免事实快照',
    `blockers_snapshot` JSON NOT NULL COMMENT '阻断清单快照',
    `evaluated_by` BIGINT NOT NULL COMMENT '评估操作者',
    `evaluated_at` DATETIME(3) NOT NULL COMMENT '评估时间',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_preparation_readiness_snapshot`
        (`tenant_id`, `preparation_id`, `snapshot_no`),
    UNIQUE KEY `uk_sol_preparation_snapshot_tenant_row` (`tenant_id`, `id`),
    KEY `idx_sol_preparation_snapshot_result`
        (`tenant_id`, `preparation_id`, `result_code`, `snapshot_no`, `id`),
    CONSTRAINT `fk_sol_preparation_snapshot_preparation`
        FOREIGN KEY (`tenant_id`, `preparation_id`)
        REFERENCES `sol_preparation` (`tenant_id`, `id`),
    CONSTRAINT `chk_sol_preparation_snapshot_no`
        CHECK (`snapshot_no` > 0 AND `rule_version` > 0),
    CONSTRAINT `chk_sol_preparation_snapshot_result`
        CHECK (`result_code` IN ('NOT_READY', 'READY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL PRE-02不可变实施就绪快照';

ALTER TABLE `sol_preparation`
    ADD CONSTRAINT `fk_sol_preparation_latest_snapshot`
        FOREIGN KEY (`tenant_id`, `latest_readiness_snapshot_id`)
        REFERENCES `sol_preparation_readiness_snapshot` (`tenant_id`, `id`);
