-- Selectively received from codex/f-cut-001-matrices@799b01873210e04e1e3462a00b37dbf617030b66 (sql/migrations/V147__fcut003_p3_dynamic_checklist.sql).
-- Renumbered after current master migration chain; Feature remains IN_PROGRESS.

-- F-CUT-003：任务冻结配置身份与P3动态采集清单。
-- 旧pms_cut_*保持不变；LEGACY_FORWARD不进入P3，也不补造配置身份。

DROP PROCEDURE IF EXISTS `fcut003_require_unique_task_configuration`;
DELIMITER $$
CREATE PROCEDURE `fcut003_require_unique_task_configuration`()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM `cut_task` t
        LEFT JOIN `cut_cutover_configuration_revision` r
          ON r.`tenant_id` = t.`tenant_id`
         AND r.`deleted` = b'0'
         AND r.`status_code` IN ('PUBLISHED', 'DISABLED')
         AND r.`published_by` IS NOT NULL
         AND r.`published_at` IS NOT NULL
         AND r.`effective_from` IS NOT NULL
         AND r.`effective_from` <= t.`create_time`
         AND (r.`effective_to` IS NULL OR t.`create_time` < r.`effective_to`)
        WHERE t.`task_origin` = 'NEW_PLATFORM'
          AND t.`deleted` = b'0'
        GROUP BY t.`tenant_id`, t.`id`
        HAVING COUNT(r.`id`) <> 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'F-CUT-003 NEW_PLATFORM task configuration history is not unique';
    END IF;
END$$
DELIMITER ;

CALL `fcut003_require_unique_task_configuration`();
DROP PROCEDURE IF EXISTS `fcut003_require_unique_task_configuration`;

ALTER TABLE `cut_task`
    ADD COLUMN `configuration_revision_id` bigint DEFAULT NULL AFTER `current_assessment_id`,
    ADD COLUMN `configuration_code` varchar(64) DEFAULT NULL AFTER `configuration_revision_id`,
    ADD COLUMN `configuration_revision_no` int DEFAULT NULL AFTER `configuration_code`;

UPDATE `cut_task` t
JOIN (
    SELECT t0.`tenant_id`, t0.`id` AS `task_id`,
           MAX(r.`id`) AS `configuration_revision_id`,
           MAX(r.`configuration_code`) AS `configuration_code`,
           MAX(r.`revision_no`) AS `configuration_revision_no`
    FROM `cut_task` t0
    JOIN `cut_cutover_configuration_revision` r
      ON r.`tenant_id` = t0.`tenant_id`
     AND r.`deleted` = b'0'
     AND r.`status_code` IN ('PUBLISHED', 'DISABLED')
     AND r.`published_by` IS NOT NULL
     AND r.`published_at` IS NOT NULL
     AND r.`effective_from` IS NOT NULL
     AND r.`effective_from` <= t0.`create_time`
     AND (r.`effective_to` IS NULL OR t0.`create_time` < r.`effective_to`)
    WHERE t0.`task_origin` = 'NEW_PLATFORM'
      AND t0.`deleted` = b'0'
    GROUP BY t0.`tenant_id`, t0.`id`
    HAVING COUNT(r.`id`) = 1
) resolved
  ON resolved.`tenant_id` = t.`tenant_id`
 AND resolved.`task_id` = t.`id`
SET t.`configuration_revision_id` = resolved.`configuration_revision_id`,
    t.`configuration_code` = resolved.`configuration_code`,
    t.`configuration_revision_no` = resolved.`configuration_revision_no`,
    t.`update_time` = NOW(3);

ALTER TABLE `cut_task`
    ADD KEY `idx_cut_task_configuration` (`tenant_id`, `configuration_revision_id`, `configuration_code`, `configuration_revision_no`),
    ADD CONSTRAINT `chk_cut_task_configuration_identity` CHECK (
        (`task_origin` = 'NEW_PLATFORM'
            AND `configuration_revision_id` IS NOT NULL
            AND NULLIF(TRIM(`configuration_code`), '') IS NOT NULL
            AND `configuration_revision_no` IS NOT NULL
            AND `configuration_revision_no` > 0)
        OR (`task_origin` = 'LEGACY_FORWARD'
            AND `configuration_revision_id` IS NULL
            AND `configuration_code` IS NULL
            AND `configuration_revision_no` IS NULL)
    );

CREATE TABLE `cut_cutover_checklist` (
    `id` bigint NOT NULL,
    `tenant_id` bigint NOT NULL,
    `cutover_task_id` bigint NOT NULL,
    `assessment_id` bigint NOT NULL,
    `assessment_version` int NOT NULL,
    `checklist_version` int NOT NULL,
    `status_code` varchar(16) NOT NULL,
    `input_snapshot` json NOT NULL,
    `input_snapshot_hash` varchar(64) NOT NULL,
    `config_revision_snapshot` json NOT NULL,
    `match_trace` json NOT NULL,
    `config_gap_snapshot` json DEFAULT NULL,
    `submitted_by` bigint DEFAULT NULL,
    `submitted_at` datetime(3) DEFAULT NULL,
    `invalidated_at` datetime(3) DEFAULT NULL,
    `invalidated_reason` varchar(1000) DEFAULT NULL,
    `current_marker` tinyint GENERATED ALWAYS AS (
        CASE WHEN `invalidated_at` IS NULL THEN 1 ELSE NULL END
    ) STORED,
    `version` int NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cut_checklist_version` (`tenant_id`, `cutover_task_id`, `checklist_version`),
    UNIQUE KEY `uk_cut_checklist_current` (`tenant_id`, `cutover_task_id`, `current_marker`),
    KEY `idx_cut_checklist_assessment` (`tenant_id`, `assessment_id`, `assessment_version`),
    CONSTRAINT `fk_cut_checklist_task` FOREIGN KEY (`cutover_task_id`) REFERENCES `cut_task` (`id`),
    CONSTRAINT `chk_cut_checklist_values` CHECK (`assessment_version` > 0 AND `checklist_version` > 0 AND `version` >= 0),
    CONSTRAINT `chk_cut_checklist_status` CHECK (`status_code` IN ('DRAFT', 'SUBMITTED', 'INVALIDATED')),
    CONSTRAINT `chk_cut_checklist_submit` CHECK (
        (`status_code` = 'DRAFT' AND `submitted_by` IS NULL AND `submitted_at` IS NULL)
        OR (`status_code` IN ('SUBMITTED', 'INVALIDATED') AND `submitted_by` IS NOT NULL AND `submitted_at` IS NOT NULL)
    ),
    CONSTRAINT `chk_cut_checklist_invalidation` CHECK (
        (`status_code` <> 'INVALIDATED' AND `invalidated_at` IS NULL AND `invalidated_reason` IS NULL)
        OR (`status_code` = 'INVALIDATED' AND `invalidated_at` IS NOT NULL AND NULLIF(TRIM(`invalidated_reason`), '') IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-003 P3动态采集清单';

CREATE TABLE `cut_cutover_checklist_item` (
    `id` bigint NOT NULL,
    `tenant_id` bigint NOT NULL,
    `checklist_id` bigint NOT NULL,
    `stable_item_key` varchar(96) NOT NULL,
    `item_definition_id` bigint DEFAULT NULL,
    `item_definition_version` int DEFAULT NULL,
    `item_type_code` varchar(32) NOT NULL,
    `item_name` varchar(255) NOT NULL,
    `item_description` text DEFAULT NULL,
    `interface_format_code` varchar(32) NOT NULL,
    `interface_schema_snapshot` json NOT NULL,
    `display_condition_snapshot` json DEFAULT NULL,
    `work_mode_code` varchar(32) NOT NULL,
    `required_flag` bit(1) NOT NULL,
    `source_code` varchar(16) NOT NULL,
    `device_id` bigint DEFAULT NULL,
    `command_template_id` bigint DEFAULT NULL,
    `matched_rule_id` bigint DEFAULT NULL,
    `matched_rule_version` int DEFAULT NULL,
    `applicable_flag` bit(1) NOT NULL,
    `custom_creator_user_id` bigint DEFAULT NULL,
    `sort_order` int NOT NULL,
    `version` int NOT NULL,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(3) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cut_checklist_item_key` (`tenant_id`, `checklist_id`, `stable_item_key`),
    KEY `idx_cut_checklist_item_order` (`tenant_id`, `checklist_id`, `item_type_code`, `applicable_flag`, `sort_order`, `id`),
    CONSTRAINT `fk_cut_checklist_item_root` FOREIGN KEY (`checklist_id`) REFERENCES `cut_cutover_checklist` (`id`),
    CONSTRAINT `chk_cut_checklist_item_source` CHECK (
        (`source_code` = 'SYSTEM_MATCHED'
            AND `item_definition_id` IS NOT NULL
            AND `item_definition_version` IS NOT NULL
            AND `item_definition_version` > 0
            AND `custom_creator_user_id` IS NULL)
        OR (`source_code` = 'CUSTOM'
            AND `item_definition_id` IS NULL
            AND `item_definition_version` IS NULL
            AND `matched_rule_id` IS NULL
            AND `matched_rule_version` IS NULL
            AND `custom_creator_user_id` IS NOT NULL)
    ),
    CONSTRAINT `chk_cut_checklist_item_values` CHECK (`sort_order` >= 0 AND `version` >= 0),
    CONSTRAINT `chk_cut_checklist_item_flags` CHECK (`required_flag` IN (b'0', b'1') AND `applicable_flag` IN (b'0', b'1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-003 P3动态采集清单项';

CREATE TABLE `cut_cutover_checklist_item_result` (
    `id` bigint NOT NULL,
    `tenant_id` bigint NOT NULL,
    `checklist_item_id` bigint NOT NULL,
    `result_version` int NOT NULL,
    `result_source_code` varchar(16) NOT NULL,
    `answer_snapshot` json NOT NULL,
    `fact_description` varchar(2000) DEFAULT NULL,
    `collection_task_id` bigint DEFAULT NULL,
    `collection_result_reference_id` bigint DEFAULT NULL,
    `collection_result_version` bigint DEFAULT NULL,
    `external_source_code` varchar(64) DEFAULT NULL,
    `query_condition_snapshot` json DEFAULT NULL,
    `queried_at` datetime(3) DEFAULT NULL,
    `load_failure_code` varchar(64) DEFAULT NULL,
    `manual_evidence_file_reference` varchar(512) DEFAULT NULL,
    `selection_started_at` datetime(3) NOT NULL,
    `selection_ended_at` datetime(3) DEFAULT NULL,
    `selected_by` bigint NOT NULL,
    `selection_reason_code` varchar(64) NOT NULL,
    `current_marker` tinyint GENERATED ALWAYS AS (
        CASE WHEN `selection_ended_at` IS NULL THEN 1 ELSE NULL END
    ) STORED,
    `created_by` bigint NOT NULL,
    `created_at` datetime(3) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cut_checklist_result_version` (`tenant_id`, `checklist_item_id`, `result_version`),
    UNIQUE KEY `uk_cut_checklist_result_current` (`tenant_id`, `checklist_item_id`, `current_marker`),
    KEY `idx_cut_checklist_result_collection` (`tenant_id`, `collection_task_id`),
    CONSTRAINT `fk_cut_checklist_result_item` FOREIGN KEY (`checklist_item_id`) REFERENCES `cut_cutover_checklist_item` (`id`),
    CONSTRAINT `chk_cut_checklist_result_values` CHECK (`result_version` > 0 AND `selected_by` > 0 AND `created_by` > 0),
    CONSTRAINT `chk_cut_checklist_result_source` CHECK (`result_source_code` IN ('DIRECT', 'COLLECTION', 'EXTERNAL', 'MANUAL')),
    CONSTRAINT `chk_cut_checklist_result_interval` CHECK (`selection_ended_at` IS NULL OR `selection_ended_at` >= `selection_started_at`),
    CONSTRAINT `chk_cut_checklist_manual_fact` CHECK (
        (`result_source_code` = 'MANUAL' AND NULLIF(TRIM(`manual_evidence_file_reference`), '') IS NOT NULL)
        OR (`result_source_code` <> 'MANUAL' AND `manual_evidence_file_reference` IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-003 P3动态采集清单项结果';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992602050006, '保存割接采集清单', 'pms:cutover-task:save-checklist', 3, 50, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602050007, '发起割接设备采集', 'pms:cutover-task:request-collection', 3, 60, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602050008, '提交割接采集清单', 'pms:cutover-task:submit-checklist', 3, 70, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
 `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`),
 `icon`=VALUES(`icon`), `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
 `status`=0, `visible`=b'1', `keep_alive`=VALUES(`keep_alive`),
 `always_show`=VALUES(`always_show`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';
