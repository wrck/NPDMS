-- =============================================================================
-- F-PROJ-007 / PM-11：项目任务树、责任区间、完成判定与状态机物理基础。
-- 仅前向扩展V1.8当前真值；V8旧WBS表保持历史只读，不建立双写或迁移触发器。
-- =============================================================================

ALTER TABLE `proj_project`
    ADD COLUMN `task_tree_version` BIGINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '项目内任务树结构版本水位' AFTER `assignment_status`,
    ADD COLUMN `task_progress_version` BIGINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '项目内任务进度事实版本水位' AFTER `task_tree_version`,
    ADD UNIQUE KEY `uk_proj_project_tenant_row` (`tenant_id`, `id`);

ALTER TABLE `proj_project_task`
    ADD COLUMN `parent_task_id` BIGINT NULL COMMENT '当前直接父任务ID' AFTER `parent_task_code`,
    ADD COLUMN `root_task_id` BIGINT NULL COMMENT '当前根任务ID' AFTER `parent_task_id`,
    ADD COLUMN `tree_depth` INT UNSIGNED NULL COMMENT '结构深度，根任务为0' AFTER `root_task_id`,
    ADD COLUMN `business_level_code` VARCHAR(64) NULL COMMENT '业务层级编码，与结构深度无关' AFTER `tree_depth`,
    ADD COLUMN `milestone_id` BIGINT NULL COMMENT '关联里程碑实例ID' AFTER `business_level_code`,
    ADD COLUMN `plan_start_time` DATETIME(3) NULL COMMENT '计划开始时间' AFTER `milestone_id`,
    ADD COLUMN `plan_end_time` DATETIME(3) NULL COMMENT '计划结束时间' AFTER `plan_start_time`,
    ADD COLUMN `actual_start_time` DATETIME(3) NULL COMMENT '实际开始时间' AFTER `plan_end_time`,
    ADD COLUMN `actual_end_time` DATETIME(3) NULL COMMENT '实际结束时间' AFTER `actual_start_time`,
    ADD COLUMN `progress` DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '任务进度0～100' AFTER `actual_end_time`,
    ADD COLUMN `state_machine_revision_id` BIGINT NULL COMMENT '创建时冻结的任务状态机版本ID' AFTER `progress`,
    ADD UNIQUE KEY `uk_proj_task_tenant_row` (`tenant_id`, `id`),
    ADD UNIQUE KEY `uk_proj_task_tenant_project_row` (`tenant_id`, `project_id`, `id`),
    ADD KEY `idx_proj_task_direct_children`
        (`tenant_id`, `project_id`, `parent_task_id`, `sort_order`, `id`),
    ADD KEY `idx_proj_task_root_depth`
        (`tenant_id`, `project_id`, `root_task_id`, `tree_depth`, `id`),
    ADD KEY `idx_proj_task_business_level`
        (`tenant_id`, `project_id`, `business_level_code`, `sort_order`, `id`),
    ADD CONSTRAINT `chk_proj_task_progress` CHECK (`progress` >= 0 AND `progress` <= 100),
    ADD CONSTRAINT `chk_proj_task_plan_time`
        CHECK (`plan_end_time` IS NULL OR `plan_start_time` IS NULL OR `plan_end_time` >= `plan_start_time`);

-- parent_task_code只用于恢复冻结模板来源关系；无法在同租户、同项目唯一解析时迁移失败。
UPDATE `proj_project_task` child
JOIN `proj_project_task` parent
  ON parent.`tenant_id` = child.`tenant_id`
 AND parent.`project_id` = child.`project_id`
 AND parent.`task_code` = child.`parent_task_code`
SET child.`parent_task_id` = parent.`id`
WHERE child.`parent_task_code` IS NOT NULL
  AND child.`parent_task_id` IS NULL;

DROP TEMPORARY TABLE IF EXISTS `_fproj007_assert`;
CREATE TEMPORARY TABLE `_fproj007_assert` (`id` TINYINT NOT NULL PRIMARY KEY);
INSERT INTO `_fproj007_assert` VALUES (1);
INSERT INTO `_fproj007_assert` (`id`)
SELECT 1
WHERE EXISTS (
    SELECT 1
    FROM `proj_project_task`
    WHERE `parent_task_code` IS NOT NULL AND `parent_task_id` IS NULL
);
TRUNCATE TABLE `_fproj007_assert`;

-- 由当前邻接真值计算根任务和结构深度；循环或孤立链不会被猜测，会由随后断言阻断。
WITH RECURSIVE `task_tree` AS (
    SELECT t.`tenant_id`, t.`project_id`, t.`id`, t.`id` AS `root_task_id`,
           0 AS `tree_depth`
    FROM `proj_project_task` t
    WHERE t.`parent_task_id` IS NULL
    UNION ALL
    SELECT child.`tenant_id`, child.`project_id`, child.`id`, tree.`root_task_id`,
           tree.`tree_depth` + 1
    FROM `task_tree` tree
    JOIN `proj_project_task` child
      ON child.`tenant_id` = tree.`tenant_id`
     AND child.`project_id` = tree.`project_id`
     AND child.`parent_task_id` = tree.`id`
)
UPDATE `proj_project_task` task
JOIN `task_tree` tree
  ON tree.`tenant_id` = task.`tenant_id`
 AND tree.`project_id` = task.`project_id`
 AND tree.`id` = task.`id`
SET task.`root_task_id` = tree.`root_task_id`,
    task.`tree_depth` = tree.`tree_depth`;

INSERT INTO `_fproj007_assert` VALUES (1);
INSERT INTO `_fproj007_assert` (`id`)
SELECT 1
WHERE EXISTS (
    SELECT 1 FROM `proj_project_task`
    WHERE `root_task_id` IS NULL OR `tree_depth` IS NULL
);
DROP TEMPORARY TABLE `_fproj007_assert`;

ALTER TABLE `proj_project_task`
    MODIFY COLUMN `root_task_id` BIGINT NOT NULL COMMENT '当前根任务ID',
    MODIFY COLUMN `tree_depth` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '结构深度，根任务为0',
    ADD CONSTRAINT `fk_proj_task_parent`
        FOREIGN KEY (`tenant_id`, `project_id`, `parent_task_id`)
        REFERENCES `proj_project_task` (`tenant_id`, `project_id`, `id`),
    ADD CONSTRAINT `fk_proj_task_root`
        FOREIGN KEY (`tenant_id`, `project_id`, `root_task_id`)
        REFERENCES `proj_project_task` (`tenant_id`, `project_id`, `id`);

CREATE TABLE IF NOT EXISTS `proj_task_tree_path` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务闭包路径ID',
    `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
    `ancestor_task_id` BIGINT NOT NULL COMMENT '祖先任务ID',
    `descendant_task_id` BIGINT NOT NULL COMMENT '后代任务ID',
    `distance` INT UNSIGNED NOT NULL COMMENT '路径距离，自反路径为0',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `deleted_time` DATETIME(3) NULL,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_task_tree_path`
        (`tenant_id`, `project_id`, `ancestor_task_id`, `descendant_task_id`),
    KEY `idx_proj_task_tree_path_ancestor`
        (`tenant_id`, `project_id`, `ancestor_task_id`, `distance`, `descendant_task_id`),
    KEY `idx_proj_task_tree_path_descendant`
        (`tenant_id`, `project_id`, `descendant_task_id`, `ancestor_task_id`),
    CONSTRAINT `fk_proj_task_tree_path_ancestor`
        FOREIGN KEY (`tenant_id`, `project_id`, `ancestor_task_id`)
        REFERENCES `proj_project_task` (`tenant_id`, `project_id`, `id`),
    CONSTRAINT `fk_proj_task_tree_path_descendant`
        FOREIGN KEY (`tenant_id`, `project_id`, `descendant_task_id`)
        REFERENCES `proj_project_task` (`tenant_id`, `project_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='项目任务树祖先闭包当前投影';

INSERT INTO `proj_task_tree_path`
    (`project_id`, `ancestor_task_id`, `descendant_task_id`, `distance`, `creator`, `updater`, `tenant_id`)
WITH RECURSIVE `ancestor_paths` AS (
    SELECT t.`tenant_id`, t.`project_id`, t.`id` AS `ancestor_task_id`,
           t.`id` AS `descendant_task_id`, 0 AS `distance`
    FROM `proj_project_task` t
    UNION ALL
    SELECT path.`tenant_id`, path.`project_id`, parent.`parent_task_id`,
           path.`descendant_task_id`, path.`distance` + 1
    FROM `ancestor_paths` path
    JOIN `proj_project_task` parent
      ON parent.`tenant_id` = path.`tenant_id`
     AND parent.`project_id` = path.`project_id`
     AND parent.`id` = path.`ancestor_task_id`
    WHERE parent.`parent_task_id` IS NOT NULL
)
SELECT path.`project_id`, path.`ancestor_task_id`, path.`descendant_task_id`, path.`distance`,
       'v88-fproj007', 'v88-fproj007', path.`tenant_id`
FROM `ancestor_paths` path;

CREATE TABLE IF NOT EXISTS `proj_task_dependency` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务依赖ID',
    `project_id` BIGINT NOT NULL COMMENT '所属项目ID',
    `predecessor_task_id` BIGINT NOT NULL COMMENT '前置任务ID',
    `successor_task_id` BIGINT NOT NULL COMMENT '后置任务ID',
    `dependency_type_code` VARCHAR(32) NOT NULL COMMENT '依赖类型编码',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `deleted_time` DATETIME(3) NULL,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_task_dependency`
        (`tenant_id`, `predecessor_task_id`, `successor_task_id`, `dependency_type_code`),
    KEY `idx_proj_task_dependency_successor`
        (`tenant_id`, `project_id`, `successor_task_id`, `predecessor_task_id`),
    CONSTRAINT `fk_proj_task_dependency_predecessor`
        FOREIGN KEY (`tenant_id`, `project_id`, `predecessor_task_id`)
        REFERENCES `proj_project_task` (`tenant_id`, `project_id`, `id`),
    CONSTRAINT `fk_proj_task_dependency_successor`
        FOREIGN KEY (`tenant_id`, `project_id`, `successor_task_id`)
        REFERENCES `proj_project_task` (`tenant_id`, `project_id`, `id`),
    CONSTRAINT `chk_proj_task_dependency_distinct`
        CHECK (`predecessor_task_id` <> `successor_task_id`),
    CONSTRAINT `chk_proj_task_dependency_type`
        CHECK (`dependency_type_code` IN
            ('FINISH_TO_START', 'START_TO_START', 'FINISH_TO_FINISH', 'START_TO_FINISH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='项目任务基础依赖';

CREATE TABLE IF NOT EXISTS `proj_project_task_assignment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务责任区间ID',
    `project_task_id` BIGINT NOT NULL COMMENT '项目任务ID',
    `assignee_user_id` BIGINT NOT NULL COMMENT '负责人用户ID',
    `effective_from` DATETIME(3) NOT NULL COMMENT '责任开始时间',
    `effective_to` DATETIME(3) NULL COMMENT '责任结束时间',
    `current_marker` TINYINT GENERATED ALWAYS AS
        (CASE WHEN `effective_to` IS NULL THEN 1 ELSE NULL END) STORED,
    `assigned_by` BIGINT NOT NULL COMMENT '指派人用户ID',
    `reason` VARCHAR(500) NOT NULL COMMENT '指派或转派原因',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `deleted_time` DATETIME(3) NULL,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_task_assignment_current`
        (`tenant_id`, `project_task_id`, `current_marker`),
    KEY `idx_proj_task_assignment_assignee`
        (`tenant_id`, `assignee_user_id`, `effective_to`, `project_task_id`),
    CONSTRAINT `fk_proj_task_assignment_task`
        FOREIGN KEY (`tenant_id`, `project_task_id`)
        REFERENCES `proj_project_task` (`tenant_id`, `id`),
    CONSTRAINT `chk_proj_task_assignment_dates`
        CHECK (`effective_to` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='项目任务负责人责任区间';

CREATE TABLE IF NOT EXISTS `proj_project_task_completion_evaluation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务完成判定ID',
    `project_task_id` BIGINT NOT NULL COMMENT '项目任务ID',
    `execution_contract_id` BIGINT NOT NULL COMMENT '执行契约ID',
    `task_version` INT UNSIGNED NOT NULL COMMENT '判定时任务版本',
    `contract_version` INT UNSIGNED NOT NULL COMMENT '判定时契约版本',
    `evaluation_result_code` VARCHAR(32) NOT NULL COMMENT '完成判定结果编码',
    `unmet_items_json` JSON NULL COMMENT '未满足项快照',
    `command_id` VARCHAR(128) NOT NULL COMMENT '稳定命令ID',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键',
    `fact_context_code` VARCHAR(32) NULL COMMENT '外部事实Owner Context',
    `fact_object_type` VARCHAR(64) NULL COMMENT '外部事实对象类型',
    `fact_object_key` VARCHAR(128) NULL COMMENT '外部事实对象稳定键',
    `fact_version` BIGINT NULL COMMENT '外部事实版本',
    `gate_snapshot_ref` VARCHAR(512) NULL COMMENT '门禁快照引用',
    `evaluated_by` BIGINT NOT NULL COMMENT '判定操作者用户ID',
    `evaluated_at` DATETIME(3) NOT NULL COMMENT '判定时间',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `deleted_time` DATETIME(3) NULL,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_task_completion_evaluation`
        (`tenant_id`, `project_task_id`, `idempotency_key`),
    KEY `idx_proj_task_completion_evaluation_time`
        (`tenant_id`, `project_task_id`, `evaluated_at`, `id`),
    CONSTRAINT `fk_proj_task_completion_evaluation_task`
        FOREIGN KEY (`tenant_id`, `project_task_id`)
        REFERENCES `proj_project_task` (`tenant_id`, `id`),
    CONSTRAINT `fk_proj_task_completion_evaluation_contract`
        FOREIGN KEY (`tenant_id`, `execution_contract_id`)
        REFERENCES `proj_project_task_execution_contract` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='项目任务完成判定追加事实';

CREATE TABLE IF NOT EXISTS `proj_task_state_machine_revision` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务状态机版本ID',
    `revision_no` INT UNSIGNED NOT NULL COMMENT '租户内版本号',
    `status` VARCHAR(32) NOT NULL COMMENT 'DRAFT/PUBLISHED',
    `effective_from` DATETIME(3) NOT NULL COMMENT '生效时间',
    `published_by` BIGINT NULL COMMENT '发布人用户ID',
    `published_at` DATETIME(3) NULL COMMENT '发布时间',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `deleted_time` DATETIME(3) NULL,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_task_state_revision_tenant_row` (`tenant_id`, `id`),
    UNIQUE KEY `uk_proj_task_state_revision_no` (`tenant_id`, `revision_no`),
    KEY `idx_proj_task_state_revision_published`
        (`tenant_id`, `status`, `revision_no`),
    CONSTRAINT `chk_proj_task_state_revision_status`
        CHECK (`status` IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT `chk_proj_task_state_revision_publish_time`
        CHECK ((`status` = 'PUBLISHED' AND `published_at` IS NOT NULL)
            OR (`status` = 'DRAFT' AND `published_at` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='租户级项目任务状态机版本';

CREATE TABLE IF NOT EXISTS `proj_task_state_transition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务状态迁移ID',
    `revision_id` BIGINT NOT NULL COMMENT '状态机版本ID',
    `from_status_code` VARCHAR(64) NOT NULL COMMENT '来源状态编码',
    `action_code` VARCHAR(64) NOT NULL COMMENT '动作编码',
    `to_status_code` VARCHAR(64) NOT NULL COMMENT '目标状态编码',
    `standard_status_mapping` VARCHAR(64) NOT NULL COMMENT '目标状态的核心标准映射',
    `allowed_role_code` VARCHAR(128) NOT NULL COMMENT '适用主体约束编码',
    `entry_condition` JSON NOT NULL COMMENT '进入条件',
    `exit_condition` JSON NOT NULL COMMENT '退出条件',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `deleted_time` DATETIME(3) NULL,
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_task_state_transition`
        (`tenant_id`, `revision_id`, `from_status_code`, `action_code`),
    KEY `idx_proj_task_state_transition_target`
        (`tenant_id`, `revision_id`, `to_status_code`, `action_code`),
    CONSTRAINT `fk_proj_task_state_transition_revision`
        FOREIGN KEY (`tenant_id`, `revision_id`)
        REFERENCES `proj_task_state_machine_revision` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='项目任务状态机合法迁移';

-- V63已执行数据只做TASK_NATIVE精确前向修正，不修改V63且不触碰非原生绑定。
UPDATE `proj_project_template_task_definition`
SET `completion_rule_config` = JSON_SET(`completion_rule_config`, '$.requiredStatus', 'DONE'),
    `updater` = 'v88-fproj007', `update_time` = CURRENT_TIMESTAMP(3)
WHERE `work_binding_type_code` = 'TASK_NATIVE'
  AND `completion_rule_type_code` = 'TASK_NATIVE_STATUS'
  AND JSON_UNQUOTE(JSON_EXTRACT(`completion_rule_config`, '$.requiredStatus')) = 'COMPLETED';

UPDATE `proj_project_task_execution_contract`
SET `completion_rule_snapshot` = JSON_SET(`completion_rule_snapshot`, '$.requiredStatus', 'DONE'),
    `updater` = 'v88-fproj007', `update_time` = CURRENT_TIMESTAMP(3)
WHERE `work_binding_type_code` = 'TASK_NATIVE'
  AND `completion_rule_type_code` = 'TASK_NATIVE_STATUS'
  AND JSON_UNQUOTE(JSON_EXTRACT(`completion_rule_snapshot`, '$.requiredStatus')) = 'COMPLETED';
