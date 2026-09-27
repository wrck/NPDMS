-- =============================================================================
-- 统一业务模型公共能力 P05：执行绑定模块自持表（两种执行实现各自独立）。
-- 同步执行实例按幂等键去重；事件驱动实例与事件检查点按事件身份去重，
-- 重复事件在结果层按形成依据幂等。执行绑定不共享平台表的写状态。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `pms_bind_inline_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `definition_code` VARCHAR(64) NOT NULL COMMENT '过程定义编码',
    `definition_version` INT NOT NULL COMMENT '消费的定义发布版本',
    `owner_module` VARCHAR(64) NOT NULL COMMENT '对象所属模块',
    `entity_type` VARCHAR(64) NOT NULL COMMENT '对象实体类型',
    `entity_id` BIGINT NOT NULL COMMENT '对象实体ID',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '本次同步执行的幂等键',
    `verdict` VARCHAR(16) NOT NULL COMMENT '条件判定 SATISFIED/UNSATISFIED/UNKNOWN',
    `result_id` VARCHAR(64) NULL COMMENT '满足条件形成的结果身份',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bind_inline_idem` (`tenant_id`, `idempotency_key`),
    KEY `idx_bind_inline_def` (`tenant_id`, `definition_code`, `entity_id`)
) ENGINE = InnoDB COMMENT '绑定-同步执行实例';

CREATE TABLE IF NOT EXISTS `pms_bind_evented_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `definition_code` VARCHAR(64) NOT NULL COMMENT '过程定义编码',
    `definition_version` INT NOT NULL COMMENT '消费的定义发布版本',
    `owner_module` VARCHAR(64) NOT NULL COMMENT '事件来源对象所属模块',
    `entity_type` VARCHAR(64) NOT NULL COMMENT '事件来源对象实体类型',
    `entity_id` BIGINT NOT NULL COMMENT '事件来源对象实体ID',
    `event_id` VARCHAR(64) NOT NULL COMMENT '消费的业务事件身份',
    `verdict` VARCHAR(16) NOT NULL COMMENT '条件判定 SATISFIED/UNSATISFIED/UNKNOWN',
    `result_id` VARCHAR(64) NULL COMMENT '满足条件形成的结果身份',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bind_evented_ev_def` (`tenant_id`, `event_id`, `definition_code`),
    KEY `idx_bind_evented_def` (`tenant_id`, `definition_code`, `entity_id`)
) ENGINE = InnoDB COMMENT '绑定-事件驱动执行实例';

CREATE TABLE IF NOT EXISTS `pms_bind_event_checkpoint` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `event_id` VARCHAR(64) NOT NULL COMMENT '已处理完的业务事件身份',
    `processed_at` DATETIME(3) NOT NULL COMMENT '处理完成时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bind_event_ckpt` (`tenant_id`, `event_id`)
) ENGINE = InnoDB COMMENT '绑定-事件驱动消费检查点';
