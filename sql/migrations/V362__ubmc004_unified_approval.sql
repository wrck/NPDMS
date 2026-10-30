-- =============================================================================
-- 统一业务模型公共能力 P07：统一审批关联与办理闭环。
-- 审批尝试关联记录业务主体、用途、尝试、提交依据与所选引擎的原生实例标识；
-- 可信结果由引擎产生、公共服务校验后幂等更新，"流程批准"与"领域生效"分别记录。
-- 重提生成新尝试并保留旧尝试的意见与结论，不覆盖历史。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `plt_approval_attempt` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `owner_module` VARCHAR(64) NOT NULL COMMENT '业务主体所属模块',
    `entity_type` VARCHAR(64) NOT NULL COMMENT '业务主体类型',
    `entity_id` BIGINT NOT NULL COMMENT '业务主体ID',
    `purpose` VARCHAR(64) NOT NULL COMMENT '审批用途',
    `attempt_id` VARCHAR(64) NOT NULL COMMENT '尝试幂等键',
    `submission_basis` VARCHAR(255) NOT NULL COMMENT '提交依据（内容范围/并发依据说明）',
    `neutral_process_ref` VARCHAR(128) NOT NULL COMMENT '中性流程引用',
    `backend_id` VARCHAR(64) NOT NULL COMMENT '承接本尝试的审批执行实现',
    `instance_ref` VARCHAR(128) NOT NULL COMMENT '引擎原生实例标识（仅存于关联记录）',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/APPROVED/REJECTED/WITHDRAWN',
    `conclusion_basis` VARCHAR(255) NULL COMMENT '结论依据（引擎随可信结果返回）',
    `decided_time` DATETIME(3) NULL COMMENT '流程结论时间',
    `previous_attempt_id` BIGINT NULL COMMENT '重提来源尝试主键（同主体同用途的上一轮）',
    `batch_group_ref` VARCHAR(64) NULL COMMENT '批次组标识（同批多主体共享）',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_approval_attempt` (`tenant_id`, `owner_module`, `entity_type`, `entity_id`,
        `purpose`, `attempt_id`),
    KEY `idx_plt_approval_attempt_instance` (`tenant_id`, `instance_ref`),
    KEY `idx_plt_approval_attempt_subject` (`tenant_id`, `owner_module`, `entity_type`, `entity_id`, `purpose`)
) ENGINE = InnoDB COMMENT '审批-公共尝试关联';

CREATE TABLE IF NOT EXISTS `plt_approval_opinion` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `attempt_row_id` BIGINT NOT NULL COMMENT '尝试关联主键',
    `action` VARCHAR(16) NOT NULL COMMENT '动作 SUBMIT/APPROVE/REJECT/WITHDRAW',
    `comment` VARCHAR(255) NULL COMMENT '意见',
    `actor_user_id` BIGINT NOT NULL COMMENT '操作者用户ID',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_plt_approval_opinion_attempt` (`tenant_id`, `attempt_row_id`)
) ENGINE = InnoDB COMMENT '审批-意见记录（旧尝试意见随行保留）';

CREATE TABLE IF NOT EXISTS `plt_approval_effect` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `attempt_row_id` BIGINT NOT NULL COMMENT '尝试关联主键',
    `operation_code` VARCHAR(64) NOT NULL COMMENT '批准后执行的实体命令（目录声明操作）',
    `idempotency_key` VARCHAR(64) NOT NULL COMMENT '命令执行幂等键',
    `status` VARCHAR(16) NOT NULL COMMENT '状态 SUCCESS/PENDING_RECOVERY/FAILED',
    `receipt_outcome` VARCHAR(16) NULL COMMENT '命令回执结果（成功时）',
    `concurrency_basis` INT NULL COMMENT '执行时并发依据',
    `detail` VARCHAR(255) NULL COMMENT '失败/待恢复原因',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_approval_effect` (`tenant_id`, `attempt_row_id`, `idempotency_key`)
) ENGINE = InnoDB COMMENT '审批-领域生效记录（与流程批准分列）';

CREATE TABLE IF NOT EXISTS `pms_bind_approval_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `instance_ref` VARCHAR(128) NOT NULL COMMENT '本引擎实例标识',
    `attempt_hint` VARCHAR(64) NOT NULL COMMENT '发起尝试幂等键（重放锚点）',
    `purpose` VARCHAR(64) NOT NULL COMMENT '审批用途',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/APPROVED/REJECTED/WITHDRAWN',
    `decision_comment` VARCHAR(255) NULL COMMENT '决定意见',
    `decided_time` DATETIME(3) NULL COMMENT '决定时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pms_bind_approval_instance` (`tenant_id`, `instance_ref`),
    KEY `idx_pms_bind_approval_attempt` (`tenant_id`, `attempt_hint`)
) ENGINE = InnoDB COMMENT '绑定-本地审批引擎实例';
