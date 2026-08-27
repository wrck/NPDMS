-- =============================================================================
-- F-SOL-003 / PRE-04：需求分析草稿、有效完成版与冻结章节。
-- 仅前向扩展SOL；PRE-02继续只使用current_marker和既有就绪字段。
-- =============================================================================

ALTER TABLE `sol_preparation`
    DROP CHECK `chk_sol_preparation_type`,
    DROP CHECK `chk_sol_preparation_business_version`,
    DROP CHECK `chk_sol_preparation_status`,
    ADD COLUMN `source_preparation_id` BIGINT NULL COMMENT 'PRE-04修订来源完成版ID' AFTER `current_marker`,
    ADD COLUMN `draft_marker` TINYINT NULL COMMENT 'PRE-04当前草稿标记：当前=1，其余=NULL' AFTER `source_preparation_id`,
    ADD COLUMN `effective_marker` TINYINT NULL COMMENT 'PRE-04当前有效完成版标记：当前=1，其余=NULL' AFTER `draft_marker`,
    ADD COLUMN `content_version` INT UNSIGNED NULL COMMENT 'PRE-04正文与附件事实版本' AFTER `snapshot_current`,
    ADD COLUMN `completed_by` BIGINT NULL COMMENT 'PRE-04完成人' AFTER `content_version`,
    ADD COLUMN `completed_at` DATETIME(3) NULL COMMENT 'PRE-04完成时间' AFTER `completed_by`,
    ADD UNIQUE KEY `uk_sol_preparation_draft`
        (`tenant_id`, `project_id`, `preparation_type_code`, `draft_marker`),
    ADD UNIQUE KEY `uk_sol_preparation_effective`
        (`tenant_id`, `project_id`, `preparation_type_code`, `effective_marker`),
    ADD KEY `idx_sol_preparation_source` (`tenant_id`, `source_preparation_id`),
    ADD CONSTRAINT `fk_sol_preparation_source`
        FOREIGN KEY (`tenant_id`, `source_preparation_id`)
        REFERENCES `sol_preparation` (`tenant_id`, `id`),
    ADD CONSTRAINT `chk_sol_preparation_type`
        CHECK (`preparation_type_code` IN ('PRE_02_SITE_SURVEY', 'PRE_04_REQUIREMENT_ANALYSIS')),
    ADD CONSTRAINT `chk_sol_preparation_business_version`
        CHECK (`business_version` > 0 AND `fixed_form_catalog_version` > 0),
    ADD CONSTRAINT `chk_sol_preparation_status`
        CHECK ((`preparation_type_code` = 'PRE_02_SITE_SURVEY'
                    AND `status_code` IN ('DRAFT', 'PENDING_CONFIRMATION', 'CONFIRMED', 'RETURNED'))
            OR (`preparation_type_code` = 'PRE_04_REQUIREMENT_ANALYSIS'
                    AND `status_code` IN ('DRAFT', 'COMPLETED'))),
    ADD CONSTRAINT `chk_sol_preparation_pre04_markers`
        CHECK ((`preparation_type_code` = 'PRE_02_SITE_SURVEY'
                    AND `draft_marker` IS NULL AND `effective_marker` IS NULL
                    AND `source_preparation_id` IS NULL AND `content_version` IS NULL
                    AND `completed_by` IS NULL AND `completed_at` IS NULL)
            OR (`preparation_type_code` = 'PRE_04_REQUIREMENT_ANALYSIS'
                    AND `current_marker` IS NULL AND `content_version` IS NOT NULL
                    AND ((`status_code` = 'DRAFT' AND `draft_marker` = 1
                            AND `effective_marker` IS NULL
                            AND `completed_by` IS NULL AND `completed_at` IS NULL)
                        OR (`status_code` = 'COMPLETED' AND `draft_marker` IS NULL
                            AND (`effective_marker` IS NULL OR `effective_marker` = 1)
                            AND `completed_by` IS NOT NULL AND `completed_at` IS NOT NULL)))),
    ADD CONSTRAINT `chk_sol_preparation_pre04_marker_values`
        CHECK ((`draft_marker` IS NULL OR `draft_marker` = 1)
            AND (`effective_marker` IS NULL OR `effective_marker` = 1));

CREATE TABLE `sol_requirement_analysis_section` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '需求分析冻结章节ID',
    `preparation_id` BIGINT NOT NULL COMMENT 'PRE-04业务版本ID',
    `source_section_id` BIGINT NULL COMMENT '修订草稿的来源完成章节ID',
    `section_code` VARCHAR(64) NOT NULL COMMENT '冻结章节编码',
    `section_name` VARCHAR(128) NOT NULL COMMENT '冻结章节名称',
    `section_kind_code` VARCHAR(16) NOT NULL COMMENT 'CORE或EXTENSION',
    `field_type_code` VARCHAR(32) NOT NULL COMMENT '冻结字段类型',
    `required_flag` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否必填',
    `dictionary_type` VARCHAR(100) NULL COMMENT '冻结选择字典类型',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '冻结排序',
    `schema_snapshot` JSON NOT NULL COMMENT '字段Schema与选项快照',
    `value_snapshot` JSON NOT NULL COMMENT '规范化字段值快照',
    `attachment_reference_snapshot` JSON NOT NULL COMMENT '精确FileArtifact引用数组',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '章节乐观锁版本',
    `creator` VARCHAR(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updater` VARCHAR(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sol_requirement_section_code`
        (`tenant_id`, `preparation_id`, `section_code`),
    UNIQUE KEY `uk_sol_requirement_section_tenant_row` (`tenant_id`, `id`),
    KEY `idx_sol_requirement_section_order`
        (`tenant_id`, `preparation_id`, `sort_order`, `section_code`, `id`),
    KEY `idx_sol_requirement_section_source` (`tenant_id`, `source_section_id`),
    CONSTRAINT `fk_sol_requirement_section_preparation`
        FOREIGN KEY (`tenant_id`, `preparation_id`)
        REFERENCES `sol_preparation` (`tenant_id`, `id`),
    CONSTRAINT `fk_sol_requirement_section_source`
        FOREIGN KEY (`tenant_id`, `source_section_id`)
        REFERENCES `sol_requirement_analysis_section` (`tenant_id`, `id`),
    CONSTRAINT `chk_sol_requirement_section_kind`
        CHECK (`section_kind_code` IN ('CORE', 'EXTENSION')),
    CONSTRAINT `chk_sol_requirement_section_field_type`
        CHECK (`field_type_code` IN
            ('RICH_TEXT', 'TEXT', 'NUMBER', 'BOOLEAN', 'SINGLE_SELECT', 'MULTI_SELECT')),
    CONSTRAINT `chk_sol_requirement_section_dictionary`
        CHECK ((`field_type_code` IN ('SINGLE_SELECT', 'MULTI_SELECT')
                    AND `dictionary_type` IS NOT NULL)
            OR (`field_type_code` NOT IN ('SINGLE_SELECT', 'MULTI_SELECT')
                    AND `dictionary_type` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='SOL PRE-04冻结章节、正文与精确附件事实';
