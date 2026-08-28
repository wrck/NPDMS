-- =============================================================================
-- F-PROJ-001 / PRD V1.8：正式项目原子创建前向承载
-- 旧V1.7迁移不可修改；旧status不原地改义，只有S0～S6执行确定性映射。
-- =============================================================================

ALTER TABLE `proj_project`
    ADD COLUMN `lifecycle_status` VARCHAR(32) NULL COMMENT '生命周期：ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED',
    ADD COLUMN `current_stage` VARCHAR(32) NULL COMMENT '当前阶段：S0～S6',
    ADD COLUMN `assignment_status` VARCHAR(32) NULL COMMENT '主责指派状态：UNASSIGNED/ASSIGNED';

UPDATE `proj_project`
SET `lifecycle_status` = 'ACTIVE',
    `current_stage` = `status`,
    `assignment_status` = 'UNASSIGNED'
WHERE `status` IN ('S0', 'S1', 'S2', 'S3', 'S4', 'S5', 'S6')
  AND `lifecycle_status` IS NULL
  AND `current_stage` IS NULL
  AND `assignment_status` IS NULL;

ALTER TABLE `proj_project_template_task_definition`
    ADD COLUMN `stage_definition_key` VARCHAR(128) NULL AFTER `template_revision_id`,
    ADD COLUMN `task_definition_key` VARCHAR(128) NULL AFTER `stage_definition_key`,
    ADD COLUMN `parent_task_definition_key` VARCHAR(128) NULL AFTER `task_definition_key`,
    ADD COLUMN `work_binding_type_code` VARCHAR(32) NULL AFTER `description`,
    ADD COLUMN `target_context_code` VARCHAR(32) NULL AFTER `work_binding_type_code`,
    ADD COLUMN `target_object_type` VARCHAR(64) NULL AFTER `target_context_code`,
    ADD COLUMN `target_object_key` VARCHAR(128) NULL AFTER `target_object_type`,
    ADD COLUMN `component_key` VARCHAR(128) NULL AFTER `target_object_key`,
    ADD COLUMN `dynamic_form_revision_id` BIGINT NULL AFTER `component_key`,
    ADD COLUMN `approval_definition_key` VARCHAR(128) NULL AFTER `dynamic_form_revision_id`,
    ADD COLUMN `binding_config` JSON NULL AFTER `approval_definition_key`,
    ADD COLUMN `permission_policy_ref` VARCHAR(512) NULL AFTER `binding_config`,
    ADD COLUMN `completion_rule_type_code` VARCHAR(32) NULL AFTER `permission_policy_ref`,
    ADD COLUMN `completion_rule_config` JSON NULL AFTER `completion_rule_type_code`,
    ADD COLUMN `gate_ref` VARCHAR(512) NULL AFTER `completion_rule_config`,
    ADD COLUMN `definition_version` INT UNSIGNED NULL AFTER `gate_ref`;

UPDATE `proj_project_template_task_definition`
SET `stage_definition_key` = `stage_code`,
    `task_definition_key` = `task_code`,
    `parent_task_definition_key` = `parent_task_code`,
    `work_binding_type_code` = 'TASK_NATIVE',
    `binding_config` = JSON_OBJECT('schemaVersion', 1),
    `permission_policy_ref` = 'PROJECT_TASK_NATIVE_DEFAULT',
    `completion_rule_type_code` = 'TASK_NATIVE_STATUS',
    `completion_rule_config` = JSON_OBJECT('schemaVersion', 1, 'requiredStatus', 'COMPLETED'),
    `definition_version` = 1
WHERE `definition_version` IS NULL;

ALTER TABLE `proj_project_template_task_definition`
    MODIFY COLUMN `stage_definition_key` VARCHAR(128) NOT NULL,
    MODIFY COLUMN `task_definition_key` VARCHAR(128) NOT NULL,
    MODIFY COLUMN `work_binding_type_code` VARCHAR(32) NOT NULL,
    MODIFY COLUMN `binding_config` JSON NOT NULL,
    MODIFY COLUMN `permission_policy_ref` VARCHAR(512) NOT NULL,
    MODIFY COLUMN `completion_rule_type_code` VARCHAR(32) NOT NULL,
    MODIFY COLUMN `completion_rule_config` JSON NOT NULL,
    MODIFY COLUMN `definition_version` INT UNSIGNED NOT NULL,
    ADD UNIQUE KEY `uk_project_template_task_definition_tenant_row` (`tenant_id`, `id`),
    ADD UNIQUE KEY `uk_project_template_task_definition_v18`
        (`tenant_id`, `template_revision_id`, `task_definition_key`),
    ADD KEY `idx_project_template_task_definition_tree_v18`
        (`tenant_id`, `template_revision_id`, `stage_definition_key`, `parent_task_definition_key`, `sort_order`);

CREATE TABLE IF NOT EXISTS `proj_project_task_execution_contract` (
    `id` BIGINT NOT NULL COMMENT '执行契约ID',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `project_task_id` BIGINT NOT NULL COMMENT 'ProjectTask逻辑引用',
    `template_task_definition_id` BIGINT NULL COMMENT '来源模板任务定义ID',
    `work_binding_type_code` VARCHAR(32) NOT NULL,
    `target_context_code` VARCHAR(32) NULL,
    `target_object_type` VARCHAR(64) NULL,
    `target_object_key` VARCHAR(128) NULL,
    `component_key` VARCHAR(128) NULL,
    `dynamic_form_revision_id` BIGINT NULL,
    `approval_instance_id` BIGINT NULL,
    `binding_parameter_snapshot` JSON NOT NULL,
    `permission_policy_ref` VARCHAR(512) NOT NULL,
    `completion_rule_type_code` VARCHAR(32) NOT NULL,
    `completion_rule_snapshot` JSON NOT NULL,
    `gate_ref` VARCHAR(512) NULL,
    `source_definition_version` INT UNSIGNED NOT NULL,
    `contract_version` INT UNSIGNED NOT NULL,
    `effective_from` DATETIME(3) NOT NULL,
    `effective_to` DATETIME(3) NULL,
    `current_marker` TINYINT GENERATED ALWAYS AS
        (CASE WHEN `effective_to` IS NULL THEN 1 ELSE NULL END) STORED,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `deleted_time` DATETIME(3) NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ProjectTask执行契约';

-- ADR-0030：历史任务只显式初始化TASK_NATIVE，不依据名称、URL或历史状态推断绑定。
INSERT INTO `proj_project_task_execution_contract` (
    `id`, `tenant_id`, `project_task_id`, `template_task_definition_id`,
    `work_binding_type_code`, `binding_parameter_snapshot`, `permission_policy_ref`,
    `completion_rule_type_code`, `completion_rule_snapshot`, `source_definition_version`,
    `contract_version`, `effective_from`, `creator`, `updater`
)
SELECT t.`id`, t.`tenant_id`, t.`id`, t.`source_definition_id`,
       'TASK_NATIVE', JSON_OBJECT('schemaVersion', 1), 'PROJECT_TASK_NATIVE_DEFAULT',
       'TASK_NATIVE_STATUS', JSON_OBJECT('schemaVersion', 1, 'requiredStatus', 'COMPLETED'), 1,
       1, t.`create_time`, 'v63-fproj001', 'v63-fproj001'
FROM `proj_project_task` t
WHERE NOT EXISTS (
    SELECT 1
    FROM `proj_project_task_execution_contract` c
    WHERE c.`tenant_id` = t.`tenant_id` AND c.`project_task_id` = t.`id`
);

-- ACC是交付件Owner。旧PROJ表只保留兼容读取；新创建路径只写ACC表。
CREATE TABLE IF NOT EXISTS `acc_project_deliverable` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL,
    `deliverable_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `stage_code` VARCHAR(32) NOT NULL,
    `task_code` VARCHAR(64) NULL,
    `required` BIT(1) NOT NULL DEFAULT b'1',
    `source_definition_id` BIGINT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `deleted_time` DATETIME(3) NULL,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_acc_project_deliverable` (`tenant_id`, `project_id`, `deliverable_code`),
    KEY `idx_acc_project_deliverable_stage` (`tenant_id`, `project_id`, `stage_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ACC拥有的项目交付件实例';

INSERT INTO `acc_project_deliverable` (
    `project_id`, `deliverable_code`, `name`, `stage_code`, `task_code`, `required`,
    `source_definition_id`, `status`, `version`, `creator`, `create_time`, `updater`,
    `update_time`, `deleted`, `deleted_time`, `tenant_id`
)
SELECT d.`project_id`, d.`deliverable_code`, d.`name`, d.`stage_code`, d.`task_code`, d.`required`,
       d.`source_definition_id`, d.`status`, d.`version`, d.`creator`, d.`create_time`, d.`updater`,
       d.`update_time`, d.`deleted`, d.`deleted_time`, d.`tenant_id`
FROM `proj_project_deliverable` d
WHERE NOT EXISTS (
    SELECT 1 FROM `acc_project_deliverable` a
    WHERE a.`tenant_id` = d.`tenant_id`
      AND a.`project_id` = d.`project_id`
      AND a.`deliverable_code` = d.`deliverable_code`
);

CREATE TABLE IF NOT EXISTS `plt_idempotency_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `scope_code` VARCHAR(128) NOT NULL,
    `actor_id` BIGINT NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `request_digest` CHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `resource_type` VARCHAR(64) NULL,
    `resource_key` VARCHAR(128) NULL,
    `response_payload` JSON NULL,
    `version` INT UNSIGNED NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_idempotency_scope` (`tenant_id`, `scope_code`, `actor_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台命令幂等记录';

CREATE TABLE IF NOT EXISTS `plt_operation_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `operation_code` VARCHAR(128) NOT NULL,
    `aggregate_type` VARCHAR(64) NOT NULL,
    `aggregate_key` VARCHAR(128) NULL,
    `actor_id` BIGINT NOT NULL,
    `correlation_id` VARCHAR(128) NOT NULL,
    `idempotency_key_digest` CHAR(64) NULL,
    `result_code` VARCHAR(32) NOT NULL,
    `detail_snapshot` JSON NOT NULL,
    `occurred_at` DATETIME(3) NOT NULL,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_plt_operation_audit_aggregate`
        (`tenant_id`, `aggregate_type`, `aggregate_key`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台操作审计';

CREATE TABLE IF NOT EXISTS `plt_outbox_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `event_id` VARCHAR(64) NOT NULL,
    `event_type` VARCHAR(128) NOT NULL,
    `aggregate_type` VARCHAR(64) NOT NULL,
    `aggregate_key` VARCHAR(128) NOT NULL,
    `payload` JSON NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `occurred_at` DATETIME(3) NOT NULL,
    `next_retry_time` DATETIME(3) NULL,
    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_outbox_event_id` (`event_id`),
    KEY `idx_plt_outbox_dispatch` (`status`, `next_retry_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台事务Outbox事件';
