-- =============================================================================
-- F-PLT-002：PLATFORM共享动态表单模板、不可变修订和手工实例。
-- 仅新增独立PLT真值；不迁移、不双写BPM或旧PMS表单数据。
-- =============================================================================

CREATE TABLE `plt_dynamic_form_template` (
    `id` BIGINT NOT NULL COMMENT '模板稳定ID（应用分配）',
    `template_code` VARCHAR(64) NOT NULL COMMENT '租户内稳定模板编码',
    `template_name` VARCHAR(128) NOT NULL COMMENT '模板名称',
    `category_code` VARCHAR(64) NOT NULL COMMENT '模板分类编码',
    `description` VARCHAR(512) NULL COMMENT '模板说明',
    `availability_code` VARCHAR(16) NOT NULL COMMENT '模板可用性',
    `current_published_revision_id` BIGINT NULL COMMENT '当前已发布修订ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '元数据乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_dynamic_form_template_code` (`tenant_id`, `template_code`),
    UNIQUE KEY `uk_plt_dynamic_form_template_tenant_row` (`tenant_id`, `id`),
    KEY `idx_plt_dynamic_form_template_selection`
        (`tenant_id`, `availability_code`, `template_name`, `id`),
    KEY `idx_plt_dynamic_form_template_published`
        (`tenant_id`, `current_published_revision_id`),
    KEY `idx_plt_dynamic_form_template_published_fk`
        (`tenant_id`, `current_published_revision_id`, `id`),
    CONSTRAINT `chk_plt_dynamic_form_template_availability`
        CHECK (`availability_code` IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PLATFORM动态表单模板身份';

CREATE TABLE `plt_dynamic_form_template_revision` (
    `id` BIGINT NOT NULL COMMENT '模板修订ID（应用分配）',
    `template_id` BIGINT NOT NULL COMMENT '模板ID',
    `revision_no` INT NOT NULL COMMENT '模板内修订号',
    `status_code` VARCHAR(16) NOT NULL COMMENT '修订状态',
    `draft_marker` TINYINT NULL COMMENT '当前草稿唯一标记',
    `source_revision_id` BIGINT NULL COMMENT '复制来源修订ID',
    `form_conf_json` JSON NOT NULL COMMENT 'FormCreate配置对象',
    `form_rules_json` JSON NOT NULL COMMENT 'FormCreate规则数组',
    `engine_code` VARCHAR(64) NOT NULL COMMENT '渲染引擎编码',
    `designer_version` VARCHAR(32) NOT NULL COMMENT '设计器版本',
    `renderer_version` VARCHAR(32) NOT NULL COMMENT '渲染器版本',
    `published_by` BIGINT NULL COMMENT '发布人',
    `published_at` DATETIME NULL COMMENT '发布时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '修订乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_dynamic_form_revision_no` (`tenant_id`, `template_id`, `revision_no`),
    UNIQUE KEY `uk_plt_dynamic_form_revision_draft` (`tenant_id`, `template_id`, `draft_marker`),
    UNIQUE KEY `uk_plt_dynamic_form_revision_tenant_row` (`tenant_id`, `id`, `template_id`),
    KEY `idx_plt_dynamic_form_revision_status`
        (`tenant_id`, `template_id`, `status_code`, `revision_no`, `id`),
    KEY `idx_plt_dynamic_form_revision_source`
        (`tenant_id`, `source_revision_id`, `template_id`),
    CONSTRAINT `fk_plt_dynamic_form_revision_template`
        FOREIGN KEY (`tenant_id`, `template_id`)
        REFERENCES `plt_dynamic_form_template` (`tenant_id`, `id`),
    CONSTRAINT `fk_plt_dynamic_form_revision_source`
        FOREIGN KEY (`tenant_id`, `source_revision_id`, `template_id`)
        REFERENCES `plt_dynamic_form_template_revision` (`tenant_id`, `id`, `template_id`),
    CONSTRAINT `chk_plt_dynamic_form_revision_status`
        CHECK (`status_code` IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT `chk_plt_dynamic_form_revision_draft_marker`
        CHECK ((`status_code` = 'DRAFT' AND `draft_marker` = 1)
            OR (`status_code` = 'PUBLISHED' AND `draft_marker` IS NULL)),
    CONSTRAINT `chk_plt_dynamic_form_revision_number`
        CHECK (`revision_no` > 0),
    CONSTRAINT `chk_plt_dynamic_form_revision_json`
        CHECK (JSON_TYPE(`form_conf_json`) = 'OBJECT' AND JSON_TYPE(`form_rules_json`) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PLATFORM动态表单不可变修订';

CREATE TABLE `plt_dynamic_form_instance` (
    `id` BIGINT NOT NULL COMMENT '手工实例ID（应用预分配）',
    `instance_code` VARCHAR(64) NOT NULL COMMENT '租户内实例编码',
    `instance_name` VARCHAR(128) NOT NULL COMMENT '实例名称',
    `owner_context` VARCHAR(32) NOT NULL COMMENT '实例Owner Context',
    `object_type` VARCHAR(64) NOT NULL COMMENT '实例对象类型',
    `object_id` VARCHAR(128) NOT NULL COMMENT '实例对象稳定ID',
    `template_id` BIGINT NOT NULL COMMENT '冻结模板ID',
    `template_revision_id` BIGINT NOT NULL COMMENT '冻结模板修订ID',
    `template_revision_no` INT NOT NULL COMMENT '冻结模板修订号',
    `engine_code` VARCHAR(64) NOT NULL COMMENT '冻结渲染引擎编码',
    `designer_version` VARCHAR(32) NOT NULL COMMENT '冻结设计器版本',
    `renderer_version` VARCHAR(32) NOT NULL COMMENT '冻结渲染器版本',
    `value_json` JSON NOT NULL COMMENT '普通字段值对象',
    `created_by` BIGINT NOT NULL COMMENT '实例创建主体',
    `version` INT NOT NULL DEFAULT 0 COMMENT '实例乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_dynamic_form_instance_code` (`tenant_id`, `instance_code`),
    UNIQUE KEY `uk_plt_dynamic_form_instance_owner`
        (`tenant_id`, `owner_context`, `object_type`, `object_id`),
    KEY `idx_plt_dynamic_form_instance_page` (`tenant_id`, `update_time`, `id`),
    KEY `idx_plt_dynamic_form_instance_revision`
        (`tenant_id`, `template_revision_id`, `template_id`, `id`),
    CONSTRAINT `fk_plt_dynamic_form_instance_revision`
        FOREIGN KEY (`tenant_id`, `template_revision_id`, `template_id`)
        REFERENCES `plt_dynamic_form_template_revision` (`tenant_id`, `id`, `template_id`),
    CONSTRAINT `chk_plt_dynamic_form_instance_value_json`
        CHECK (JSON_TYPE(`value_json`) = 'OBJECT'),
    CONSTRAINT `chk_plt_dynamic_form_instance_revision_number`
        CHECK (`template_revision_no` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PLATFORM手工动态表单实例';

ALTER TABLE `plt_dynamic_form_template`
    ADD CONSTRAINT `fk_plt_dynamic_form_template_published_revision`
        FOREIGN KEY (`tenant_id`, `current_published_revision_id`, `id`)
        REFERENCES `plt_dynamic_form_template_revision` (`tenant_id`, `id`, `template_id`);
