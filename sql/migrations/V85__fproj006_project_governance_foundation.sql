-- F-PROJ-006 / PM-10：项目阶段共享快照物理基础。
-- 本表由PM-03、PM-10、EXE-06共享；PM-10动作字段仅做前向可空加法。

CREATE TABLE IF NOT EXISTS `proj_project_stage_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '阶段快照ID',
    `project_id` BIGINT NOT NULL COMMENT '项目ID',
    `stage_code` VARCHAR(32) NOT NULL COMMENT '快照阶段编码',
    `snapshot_no` INT NOT NULL COMMENT '同项目同阶段快照序号',
    `operation_type` VARCHAR(32) NULL COMMENT 'PM-10动作类型：ROLLBACK/EXCEPTION_CLOSE/REOPEN',
    `before_stage` VARCHAR(32) NULL COMMENT '动作前阶段',
    `after_stage` VARCHAR(32) NULL COMMENT '动作后阶段',
    `before_lifecycle_status` VARCHAR(32) NULL COMMENT '动作前生命周期状态',
    `after_lifecycle_status` VARCHAR(32) NULL COMMENT '动作后生命周期状态',
    `before_assignment_status` VARCHAR(32) NULL COMMENT '动作前指派状态',
    `after_assignment_status` VARCHAR(32) NULL COMMENT '动作后指派状态',
    `reason_code` VARCHAR(64) NULL COMMENT '动作原因编码',
    `reason_detail` VARCHAR(1000) NULL COMMENT '动作原因说明',
    `reassignment_requirement` VARCHAR(1000) NULL COMMENT '回退后的重新指派要求',
    `business_basis` TEXT NULL COMMENT '异常关闭业务依据',
    `legacy_items_json` JSON NULL COMMENT '异常关闭遗留事项快照',
    `guard_snapshot_json` JSON NULL COMMENT '提交使用的守卫令牌快照',
    `tree_version` BIGINT NULL COMMENT '守卫冻结的完整树版本',
    `provider_facts_json` JSON NULL COMMENT '守卫冻结的提供方事实版本与摘要',
    `related_snapshot_id` BIGINT NULL COMMENT '重开关联的异常关闭快照ID',
    `operation_id` VARCHAR(128) NULL COMMENT 'PM-10稳定业务操作ID',
    `operator_user_id` BIGINT NULL COMMENT 'PM-10操作者用户ID',
    `operated_at` DATETIME(3) NULL COMMENT 'PM-10业务操作时间',
    `creator` VARCHAR(64) NULL DEFAULT '',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL DEFAULT '',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_proj_stage_snapshot_project_stage_no`
        (`tenant_id`, `project_id`, `stage_code`, `snapshot_no`),
    UNIQUE KEY `uk_proj_stage_snapshot_operation` (`tenant_id`, `operation_id`),
    KEY `idx_proj_stage_snapshot_project_time`
        (`tenant_id`, `project_id`, `operated_at`, `id`),
    KEY `idx_proj_stage_snapshot_related`
        (`tenant_id`, `project_id`, `related_snapshot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PM-03、PM-10、EXE-06共享项目阶段快照（只追加）';
