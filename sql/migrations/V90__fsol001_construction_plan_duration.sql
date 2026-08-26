-- =============================================================================
-- F-SOL-001 / PRE-01：项目工期基线、版本化变更与审批物理基础。
-- SOL拥有三张当前表；project_id仅保存跨Context稳定ID，不建立PROJ外键。
-- =============================================================================

CREATE TABLE `sol_construction_plan` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '施工计划根ID',
    `project_id` BIGINT NOT NULL COMMENT 'PROJ项目ID',
    `current_duration_revision_id` BIGINT NULL COMMENT '当前生效工期版本ID',
    `pending_change_id` BIGINT NULL COMMENT '当前在途工期变更ID',
    `plan_recalculation_status_code` VARCHAR(32) NOT NULL
        COMMENT '计划重算影响状态',
    `plan_recalculation_source_revision_id` BIGINT NULL
        COMMENT '触发计划待重算的工期版本ID',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_construction_plan_project` (`tenant_id`, `project_id`),
    UNIQUE KEY `uk_sol_construction_plan_tenant_row` (`tenant_id`, `id`),
    KEY `idx_sol_construction_plan_current_revision`
        (`tenant_id`, `current_duration_revision_id`),
    KEY `idx_sol_construction_plan_pending_change`
        (`tenant_id`, `pending_change_id`),
    KEY `idx_sol_construction_plan_recalculation`
        (`tenant_id`, `plan_recalculation_status_code`, `project_id`),
    CONSTRAINT `chk_sol_construction_plan_recalculation_status`
        CHECK (`plan_recalculation_status_code` IN
            ('PENDING_RECALCULATION', 'RECALCULATED', 'RECALCULATION_FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL施工计划根与当前工期指针';

CREATE TABLE `sol_construction_plan_revision` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工期版本ID',
    `plan_id` BIGINT NOT NULL COMMENT '施工计划根ID',
    `revision_no` INT UNSIGNED NOT NULL COMMENT '项目内工期版本号',
    `calculation_basis_code` VARCHAR(32) NOT NULL COMMENT '工期计算口径',
    `start_date` DATE NOT NULL COMMENT '开始日期',
    `end_date` DATE NOT NULL COMMENT '结束日期',
    `duration_days` INT UNSIGNED NOT NULL COMMENT '自然日工期',
    `source_change_id` BIGINT NULL COMMENT '来源工期变更ID',
    `frozen_at` DATETIME(3) NULL COMMENT '提交冻结时间',
    `effective_at` DATETIME(3) NULL COMMENT '生效时间',
    `created_by` BIGINT NOT NULL COMMENT '创建人用户ID',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_construction_plan_revision_no`
        (`tenant_id`, `plan_id`, `revision_no`),
    UNIQUE KEY `uk_sol_construction_plan_revision_tenant_row` (`tenant_id`, `id`),
    CONSTRAINT `fk_sol_construction_plan_revision_plan`
        FOREIGN KEY (`tenant_id`, `plan_id`)
        REFERENCES `sol_construction_plan` (`tenant_id`, `id`),
    CONSTRAINT `chk_sol_construction_plan_revision_basis`
        CHECK (`calculation_basis_code` IN ('DATE_RANGE', 'DURATION_FROM_START')),
    CONSTRAINT `chk_sol_construction_plan_revision_duration`
        CHECK (`duration_days` > 0),
    CONSTRAINT `chk_sol_construction_plan_revision_dates`
        CHECK (`end_date` >= `start_date`
            AND DATEDIFF(`end_date`, `start_date`) + 1 = `duration_days`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL项目工期版本';

CREATE TABLE `sol_construction_plan_change` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工期变更ID',
    `plan_id` BIGINT NOT NULL COMMENT '施工计划根ID',
    `base_revision_id` BIGINT NOT NULL COMMENT '变更基准工期版本ID',
    `candidate_revision_id` BIGINT NOT NULL COMMENT '候选工期版本ID',
    `status_code` VARCHAR(32) NOT NULL COMMENT '变更审批生命周期状态',
    `reason_type_code` VARCHAR(64) NOT NULL COMMENT '变更原因字典值',
    `reason_detail` VARCHAR(1000) NOT NULL COMMENT '变更原因说明',
    `customer_evidence_required` BIT(1) NOT NULL COMMENT '是否要求客户依据',
    `customer_evidence_file_id` BIGINT NULL COMMENT '客户依据FileArtifact稳定ID',
    `customer_evidence_file_version` INT UNSIGNED NULL COMMENT '客户依据文件版本',
    `process_definition_key` VARCHAR(128) NULL COMMENT '冻结BPM流程定义键',
    `process_instance_id` VARCHAR(64) NULL COMMENT '冻结BPM流程实例ID',
    `submitted_at` DATETIME(3) NULL COMMENT '提交审批时间',
    `applicant_user_id` BIGINT NOT NULL COMMENT '申请人用户ID',
    `approver_user_id` BIGINT NULL COMMENT '冻结审批人用户ID',
    `approved_at` DATETIME(3) NULL COMMENT '审批终态时间',
    `approval_opinion` VARCHAR(1000) NULL COMMENT '审批意见',
    `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_construction_plan_change_candidate`
        (`tenant_id`, `plan_id`, `candidate_revision_id`),
    UNIQUE KEY `uk_sol_construction_plan_change_process`
        (`tenant_id`, `process_instance_id`),
    UNIQUE KEY `uk_sol_construction_plan_change_tenant_row` (`tenant_id`, `id`),
    KEY `idx_sol_construction_plan_change_status`
        (`tenant_id`, `plan_id`, `status_code`, `id`),
    KEY `idx_sol_construction_plan_change_applicant`
        (`tenant_id`, `applicant_user_id`, `created_at`, `id`),
    KEY `idx_sol_construction_plan_change_approver`
        (`tenant_id`, `approver_user_id`, `approved_at`, `id`),
    CONSTRAINT `fk_sol_construction_plan_change_plan`
        FOREIGN KEY (`tenant_id`, `plan_id`)
        REFERENCES `sol_construction_plan` (`tenant_id`, `id`),
    CONSTRAINT `fk_sol_construction_plan_change_base_revision`
        FOREIGN KEY (`tenant_id`, `base_revision_id`)
        REFERENCES `sol_construction_plan_revision` (`tenant_id`, `id`),
    CONSTRAINT `fk_sol_construction_plan_change_candidate_revision`
        FOREIGN KEY (`tenant_id`, `candidate_revision_id`)
        REFERENCES `sol_construction_plan_revision` (`tenant_id`, `id`),
    CONSTRAINT `chk_sol_construction_plan_change_status`
        CHECK (`status_code` IN
            ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT `chk_sol_construction_plan_change_workflow`
        CHECK (`status_code` <> 'PENDING_APPROVAL'
            OR (`process_definition_key` IS NOT NULL AND `process_instance_id` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL项目工期变更与审批关联';

-- 指针列物理可空以解除首次创建时的插入环；应用事务提交前负责闭合当前指针不变量。
ALTER TABLE `sol_construction_plan`
    ADD CONSTRAINT `fk_sol_construction_plan_current_revision`
        FOREIGN KEY (`tenant_id`, `current_duration_revision_id`)
        REFERENCES `sol_construction_plan_revision` (`tenant_id`, `id`),
    ADD CONSTRAINT `fk_sol_construction_plan_pending_change`
        FOREIGN KEY (`tenant_id`, `pending_change_id`)
        REFERENCES `sol_construction_plan_change` (`tenant_id`, `id`),
    ADD CONSTRAINT `fk_sol_construction_plan_recalculation_revision`
        FOREIGN KEY (`tenant_id`, `plan_recalculation_source_revision_id`)
        REFERENCES `sol_construction_plan_revision` (`tenant_id`, `id`);
