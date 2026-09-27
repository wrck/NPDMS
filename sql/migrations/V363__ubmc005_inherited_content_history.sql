-- =============================================================================
-- 统一业务模型公共能力 P08：继承式可选内容历史与审批适用性。
-- 演示工单修订实体继承业务字段并另存修订元数据；审批尝试记录提交时的内容并发基准，
-- 生效时基准不一致即判定批准不再适用于当前内容（不新增自动重审规则）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `pms_plat_demo_ticket_revision` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '修订主键',
    `summary` VARCHAR(255) NOT NULL COMMENT '摘要',
    `detail` TEXT NULL COMMENT '详情',
    `priority` INT NULL COMMENT '优先级',
    `handled` BIT(1) NULL COMMENT '已受理',
    `entity_id` BIGINT NOT NULL COMMENT '所属业务实体ID（演示工单主键）',
    `revision_no` INT NOT NULL COMMENT '修订号（同实体递增，1 起）',
    `source_revision_id` BIGINT NULL COMMENT '复制来源修订主键（初始修订为空）',
    `base_effective_revision_id` BIGINT NULL COMMENT '创建基线的生效修订主键',
    `base_entity_version` INT NULL COMMENT '创建基线的业务行版本',
    `revision_state` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '修订状态 DRAFT/FROZEN',
    `effective` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否当前生效修订',
    `change_reason` VARCHAR(255) NULL COMMENT '修订原因',
    `frozen_by` BIGINT NULL COMMENT '冻结人',
    `frozen_at` DATETIME(3) NULL COMMENT '冻结时间',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_demo_ticket_revision_no` (`tenant_id`, `entity_id`, `revision_no`),
    KEY `idx_demo_ticket_revision_state` (`tenant_id`, `entity_id`, `revision_state`)
) ENGINE = InnoDB COMMENT '演示工单修订（继承式内容历史）';

ALTER TABLE `plt_approval_attempt`
    ADD COLUMN `submission_concurrency_basis` INT NULL
        COMMENT '提交时的业务内容并发基准（生效时校验批准是否仍适用）' AFTER `submission_basis`;
