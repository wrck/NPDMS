ALTER TABLE `cut_cutover_configuration_revision`
    ADD COLUMN `navigation_rule_snapshot` json NULL COMMENT '提交后导航规则快照' AFTER `plan_template_section_snapshot`,
    ADD CONSTRAINT `chk_cut_configuration_navigation_rule` CHECK (
        `navigation_rule_snapshot` IS NULL OR (
            JSON_TYPE(`navigation_rule_snapshot`) = 'OBJECT'
            AND JSON_LENGTH(`navigation_rule_snapshot`) = 1
            AND JSON_CONTAINS_PATH(`navigation_rule_snapshot`, 'one', '$.target') = 1
            AND JSON_TYPE(JSON_EXTRACT(`navigation_rule_snapshot`, '$.target')) = 'STRING'
            AND JSON_UNQUOTE(JSON_EXTRACT(`navigation_rule_snapshot`, '$.target'))
                IN ('CURRENT_STAGE_WORKBENCH', 'TASK_OVERVIEW')
        )
    );
