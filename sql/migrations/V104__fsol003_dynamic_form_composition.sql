-- =============================================================================
-- F-SOL-003：PRE-04根切换为PLATFORM动态表单实例承载。
-- 所有已持久化PRE-04版本（包括首次DRAFT）必须精确引用一个实例；PRE-02保持为空。
-- 跨Context只保存逻辑ID，不建立跨模块外键。
-- =============================================================================

ALTER TABLE `sol_preparation`
    DROP CHECK `chk_sol_preparation_pre04_markers`,
    ADD COLUMN `dynamic_form_instance_id` BIGINT NULL COMMENT 'PLATFORM动态表单实例逻辑ID' AFTER `source_preparation_id`,
    ADD UNIQUE KEY `uk_sol_preparation_dynamic_form_instance`
        (`tenant_id`, `dynamic_form_instance_id`),
    ADD CONSTRAINT `chk_sol_preparation_pre04_markers`
        CHECK ((`preparation_type_code` = 'PRE_02_SITE_SURVEY' AND `dynamic_form_instance_id` IS NULL
                    AND `draft_marker` IS NULL AND `effective_marker` IS NULL
                    AND `source_preparation_id` IS NULL AND `content_version` IS NULL
                    AND `completed_by` IS NULL AND `completed_at` IS NULL)
            OR (`preparation_type_code` = 'PRE_04_REQUIREMENT_ANALYSIS' AND `dynamic_form_instance_id` IS NOT NULL
                    AND `current_marker` IS NULL AND `content_version` IS NOT NULL
                    AND ((`status_code` = 'DRAFT' AND `draft_marker` = 1
                            AND `effective_marker` IS NULL
                            AND `completed_by` IS NULL AND `completed_at` IS NULL)
                        OR (`status_code` = 'COMPLETED' AND `draft_marker` IS NULL
                            AND (`effective_marker` IS NULL OR `effective_marker` = 1)
                            AND `completed_by` IS NOT NULL AND `completed_at` IS NOT NULL))));
