-- =============================================================================
-- 统一业务模型公共能力 P06：统一交付件（类型目录/适用配置/要求实例/实际材料/提交台账）。
-- 类型目录承载稳定编码与文件约束；必交/选交与数量属于要求或配置，不进入类型编码。
-- 实际材料记录关联来源实体与真实文件版本，可先于要求存在；
-- 要求通过（来源实体 + 类型）关系匹配材料；提交台账按 request_key 幂等并按要求取代。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `plt_delivery_type` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `type_code` VARCHAR(64) NOT NULL COMMENT '类型稳定编码',
    `name` VARCHAR(128) NOT NULL COMMENT '类型名称',
    `category` VARCHAR(64) NOT NULL COMMENT '分类',
    `allowed_media_json` VARCHAR(512) NOT NULL COMMENT '允许的媒体类型清单（JSON 数组）',
    `max_size_bytes` BIGINT NOT NULL COMMENT '单文件大小上限（字节）',
    `enabled` BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_delivery_type` (`tenant_id`, `type_code`)
) ENGINE = InnoDB COMMENT '交付-统一类型目录';

CREATE TABLE IF NOT EXISTS `plt_delivery_capability_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `owner_module` VARCHAR(64) NOT NULL COMMENT '适用实体所属模块',
    `entity_type` VARCHAR(64) NOT NULL COMMENT '适用实体类型',
    `type_code` VARCHAR(64) NOT NULL COMMENT '材料类型编码',
    `required` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否必交（属于要求，不属于类型）',
    `minimum_quantity` INT NOT NULL DEFAULT 0 COMMENT '最低数量',
    `counting_unit` VARCHAR(16) NOT NULL DEFAULT 'MATERIAL' COMMENT '计数单位 MATERIAL/FILE_VERSION/SUBMISSION',
    `enabled` BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_delivery_config` (`tenant_id`, `owner_module`, `entity_type`, `type_code`)
) ENGINE = InnoDB COMMENT '交付-实体能力适用配置';

CREATE TABLE IF NOT EXISTS `plt_delivery_requirement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `owner_module` VARCHAR(64) NOT NULL COMMENT '来源实体所属模块',
    `entity_type` VARCHAR(64) NOT NULL COMMENT '来源实体类型',
    `entity_id` BIGINT NOT NULL COMMENT '来源实体ID',
    `type_code` VARCHAR(64) NOT NULL COMMENT '材料类型编码',
    `required` BIT(1) NOT NULL COMMENT '是否必交',
    `minimum_quantity` INT NOT NULL COMMENT '最低数量',
    `counting_unit` VARCHAR(16) NOT NULL COMMENT '计数单位 MATERIAL/FILE_VERSION/SUBMISSION',
    `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '状态 OPEN/SATISFIED/CONFIRMED',
    `confirmed_by` VARCHAR(64) NULL COMMENT '确认人（仅 CONFIRMED 有效）',
    `confirmed_time` DATETIME(3) NULL COMMENT '确认时间（仅 CONFIRMED 有效）',
    `config_id` BIGINT NOT NULL DEFAULT 0 COMMENT '生成本要求的能力配置主键',
    `config_version` INT NOT NULL DEFAULT 0 COMMENT '生成本要求的配置版本',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_delivery_req` (`tenant_id`, `owner_module`, `entity_type`, `entity_id`, `type_code`),
    KEY `idx_plt_delivery_req_entity` (`tenant_id`, `owner_module`, `entity_type`, `entity_id`)
) ENGINE = InnoDB COMMENT '交付-要求实例';

CREATE TABLE IF NOT EXISTS `plt_delivery_material` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `owner_module` VARCHAR(64) NOT NULL COMMENT '来源实体所属模块',
    `entity_type` VARCHAR(64) NOT NULL COMMENT '来源实体类型',
    `entity_id` BIGINT NOT NULL COMMENT '来源实体ID',
    `type_code` VARCHAR(64) NOT NULL COMMENT '材料类型编码',
    `file_reference_id` BIGINT NOT NULL COMMENT '统一文件引用ID',
    `file_artifact_id` BIGINT NOT NULL COMMENT '文件工件ID',
    `file_version_no` INT NOT NULL COMMENT '文件版本号',
    `file_sha256` VARCHAR(64) NOT NULL COMMENT '文件内容摘要',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名称',
    `title` VARCHAR(255) NULL COMMENT '材料标题',
    `source_kind` VARCHAR(16) NOT NULL DEFAULT 'UPLOAD' COMMENT '来源 UPLOAD/GENERATED/ASSOCIATED',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/WITHDRAWN',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_delivery_mat` (`tenant_id`, `owner_module`, `entity_type`, `entity_id`,
        `file_artifact_id`, `file_version_no`),
    KEY `idx_plt_delivery_mat_entity` (`tenant_id`, `owner_module`, `entity_type`, `entity_id`, `type_code`)
) ENGINE = InnoDB COMMENT '交付-实际材料记录';

CREATE TABLE IF NOT EXISTS `plt_delivery_submission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `requirement_id` BIGINT NOT NULL COMMENT '要求实例ID',
    `request_key` VARCHAR(128) NOT NULL COMMENT '提交幂等键',
    `material_ids_json` VARCHAR(1024) NOT NULL COMMENT '本次提交的材料ID清单（JSON 数组）',
    `status` VARCHAR(16) NOT NULL DEFAULT 'CURRENT' COMMENT '状态 CURRENT/SUPERSEDED/WITHDRAWN',
    `submit_evidence_json` VARCHAR(1024) NULL COMMENT '提交证据快照（文件版本与摘要）',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_delivery_sub` (`tenant_id`, `request_key`),
    KEY `idx_plt_delivery_sub_req` (`tenant_id`, `requirement_id`, `status`)
) ENGINE = InnoDB COMMENT '交付-提交台账';
