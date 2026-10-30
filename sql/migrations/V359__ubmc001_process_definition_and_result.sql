-- =============================================================================
-- 统一业务模型公共能力 P05：中性过程定义发布 + 最小业务结果登记。
-- 定义由统一目录校验后冻结实体契约版本与操作版本；结果记录保留形成依据，
-- 重复形成按 (result_type, 实体, 形成依据) 幂等。执行实例与消费检查点归执行绑定模块。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `pms_plat_process_definition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `definition_code` VARCHAR(64) NOT NULL COMMENT '稳定定义编码',
    `definition_version` INT NOT NULL DEFAULT 0 COMMENT '发布版本，发布一次递增',
    `name` VARCHAR(128) NOT NULL COMMENT '定义名称',
    `owner_module` VARCHAR(64) NOT NULL COMMENT '实体所属模块',
    `entity_type` VARCHAR(64) NOT NULL COMMENT '实体类型',
    `entity_stable_code` VARCHAR(64) NOT NULL COMMENT '冻结的实体稳定编码',
    `entity_contract_version` INT NOT NULL COMMENT '冻结的实体契约版本',
    `operation_code` VARCHAR(64) NOT NULL COMMENT '触发的业务操作编码',
    `operation_version` INT NOT NULL COMMENT '冻结的操作版本',
    `rule_code` VARCHAR(64) NOT NULL COMMENT '规则语义编码',
    `rule_version` VARCHAR(32) NOT NULL COMMENT '规则语义版本',
    `conditions_json` TEXT NULL COMMENT '类型化字段条件 JSON',
    `result_type` VARCHAR(64) NOT NULL COMMENT '满足条件后形成的业务结果类型',
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
    `published_at` DATETIME(3) NULL COMMENT '发布时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pms_proc_def_code_ver` (`tenant_id`, `definition_code`, `definition_version`),
    KEY `idx_pms_proc_def_entity` (`tenant_id`, `owner_module`, `entity_type`)
) ENGINE = InnoDB COMMENT '中性过程定义';

CREATE TABLE IF NOT EXISTS `pms_plat_business_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `result_type` VARCHAR(64) NOT NULL COMMENT '业务结果类型',
    `owner_module` VARCHAR(64) NOT NULL COMMENT '对象所属模块',
    `entity_type` VARCHAR(64) NOT NULL COMMENT '对象实体类型',
    `entity_id` BIGINT NOT NULL COMMENT '对象实体ID',
    `result_id` VARCHAR(64) NOT NULL COMMENT '结果身份',
    `semantics` VARCHAR(32) NOT NULL COMMENT '结果语义 NEW_RESULT 等',
    `formation_basis` VARCHAR(128) NOT NULL COMMENT '形成依据（事件ID/执行引用），不为字段修改时间',
    `formed_by_backend` VARCHAR(64) NOT NULL COMMENT '形成结果的后端标识',
    `formed_at` DATETIME(3) NOT NULL COMMENT '形成时间',
    `valid` BIT(1) NOT NULL DEFAULT b'1' COMMENT '有效性',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pms_biz_result_basis` (`tenant_id`, `result_type`, `owner_module`, `entity_type`, `entity_id`, `formation_basis`),
    KEY `idx_pms_biz_result_object` (`tenant_id`, `result_type`, `owner_module`, `entity_type`, `entity_id`, `valid`)
) ENGINE = InnoDB COMMENT '统一业务结果登记';
