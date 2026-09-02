-- F-CUT-004 P4割接方案revision、步骤和保障安排。
-- 先验证V146/V149现存任务与阶段历史，再前向开放P4/P5/P6；不更新业务行。

DROP PROCEDURE IF EXISTS `fcut004_require_stage_contract`;
DELIMITER $$
CREATE PROCEDURE `fcut004_require_stage_contract`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM `cut_task`
    WHERE CASE WHEN (
      (`task_origin` = 'NEW_PLATFORM' AND (
        (`current_stage` = 'P2' AND `task_status` = 'GRADE_CONFIRMING') OR
        (`current_stage` = 'P3' AND `task_status` = 'SURVEYING') OR
        (`current_stage` = 'P4' AND `task_status` = 'PLAN_DRAFTING')
      )) OR
      (`task_origin` = 'LEGACY_FORWARD' AND `current_stage` IS NULL AND `task_status` = 'LEGACY_UNKNOWN')
    ) THEN 0 ELSE 1 END = 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'F-CUT-004 cut_task stage preflight failed';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `cut_task_stage_history`
    WHERE CASE WHEN (
      (`trigger_type` = 'P1_ACCEPTED' AND `from_stage` = 'P1' AND `to_stage` = 'P2'
        AND `to_status` = 'GRADE_CONFIRMING') OR
      (`trigger_type` = 'P2_ASSESSMENT_SUBMITTED' AND `from_stage` = 'P2'
        AND ((`to_stage` = 'P3' AND `to_status` = 'SURVEYING')
          OR (`to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING'))) OR
      (`trigger_type` = 'P2_ASSESSMENT_INVALIDATED' AND `from_stage` IN ('P3','P4')
        AND `to_stage` = 'P2' AND `to_status` = 'GRADE_CONFIRMING') OR
      (`trigger_type` = 'P3_CHECKLIST_SUBMITTED' AND `from_stage` = 'P3'
        AND `to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING')
    ) THEN 0 ELSE 1 END = 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'F-CUT-004 stage history preflight failed';
  END IF;
END$$
DELIMITER ;

CALL `fcut004_require_stage_contract`();
DROP PROCEDURE IF EXISTS `fcut004_require_stage_contract`;

ALTER TABLE `cut_task`
  DROP CHECK `chk_cut_task_stage`,
  DROP CHECK `chk_cut_task_status`,
  DROP CHECK `chk_cut_task_origin_union`,
  ADD CONSTRAINT `chk_cut_task_stage` CHECK (`current_stage` IS NULL OR `current_stage` IN ('P2','P3','P4','P5','P6')),
  ADD CONSTRAINT `chk_cut_task_status` CHECK (`task_status` IN ('GRADE_CONFIRMING','SURVEYING','PLAN_DRAFTING','APPROVING','CLOSURE_IN_PROGRESS','LEGACY_UNKNOWN')),
  ADD CONSTRAINT `chk_cut_task_origin_union` CHECK (
    COALESCE((`task_origin` = 'NEW_PLATFORM'
      AND `legacy_task_id` IS NULL AND `legacy_cutover_type_raw` IS NULL
      AND `legacy_network_mode_raw` IS NULL AND `legacy_status_value` IS NULL
      AND `legacy_source_version` IS NULL AND `legacy_mapping_version` IS NULL
      AND `owner_user_id` > 0 AND `customer_id` > 0
      AND CHAR_LENGTH(TRIM(`background`)) BETWEEN 1 AND 4000
      AND CHAR_LENGTH(TRIM(`cutover_type`)) BETWEEN 1 AND 32
      AND `configuration_revision_id` > 0
      AND CHAR_LENGTH(TRIM(`configuration_code`)) BETWEEN 1 AND 64
      AND `configuration_revision_no` > 0
      AND ((`current_stage` = 'P2' AND `task_status` = 'GRADE_CONFIRMING')
        OR (`current_stage` = 'P3' AND `task_status` = 'SURVEYING')
        OR (`current_stage` = 'P4' AND `task_status` = 'PLAN_DRAFTING')
        OR (`current_stage` = 'P5' AND `task_status` = 'APPROVING')
        OR (`current_stage` = 'P6' AND `task_status` = 'CLOSURE_IN_PROGRESS'))
      AND `implementation_readiness_snapshot_id` > 0
      AND `implementation_readiness_snapshot_version` >= 0
      AND `project_scope_version` >= 0
      AND `project_context_snapshot` IS NOT NULL AND `device_scope_watermark` IS NOT NULL
      AND `customer_context_snapshot` IS NOT NULL AND `readiness_context_snapshot` IS NOT NULL), FALSE)
    OR COALESCE((`task_origin` = 'LEGACY_FORWARD' AND `intake_source_type` = 'LEGACY_FORWARD'
      AND `task_status` = 'LEGACY_UNKNOWN' AND `legacy_task_id` > 0
      AND CHAR_LENGTH(TRIM(`legacy_cutover_type_raw`)) BETWEEN 1 AND 32
      AND (`legacy_network_mode_raw` IS NULL OR CHAR_LENGTH(TRIM(`legacy_network_mode_raw`)) BETWEEN 1 AND 32)
      AND `legacy_status_value` BETWEEN 0 AND 8 AND `legacy_source_version` >= 0
      AND CHAR_LENGTH(TRIM(`legacy_mapping_version`)) BETWEEN 1 AND 64
      AND `previous_task_id` IS NULL AND `background` IS NULL AND `cutover_type` IS NULL
      AND `network_mode` IS NULL AND `configuration_revision_id` IS NULL
      AND `configuration_code` IS NULL AND `configuration_revision_no` IS NULL
      AND `owner_user_id` IS NULL AND `customer_id` IS NULL AND `current_stage` IS NULL
      AND `implementation_readiness_snapshot_id` IS NULL
      AND `implementation_readiness_snapshot_version` IS NULL
      AND `project_scope_version` IS NULL AND `project_context_snapshot` IS NULL
      AND `device_scope_watermark` IS NULL AND `customer_context_snapshot` IS NULL
      AND `readiness_context_snapshot` IS NULL AND `manual_grade` IS NULL
      AND `current_assessment_id` IS NULL), FALSE)
  );

ALTER TABLE `cut_task_stage_history`
  DROP CHECK `chk_cut_stage_code`,
  DROP CHECK `chk_cut_stage_trigger`,
  ADD CONSTRAINT `chk_cut_stage_code` CHECK (`from_stage` IN ('P1','P2','P3','P4','P5') AND `to_stage` IN ('P2','P3','P4','P5','P6')),
  ADD CONSTRAINT `chk_cut_stage_trigger` CHECK (
    COALESCE((
      (`trigger_type` = 'P1_ACCEPTED' AND `from_stage` = 'P1' AND `to_stage` = 'P2' AND `to_status` = 'GRADE_CONFIRMING') OR
      (`trigger_type` = 'P2_ASSESSMENT_SUBMITTED' AND `from_stage` = 'P2' AND ((`to_stage` = 'P3' AND `to_status` = 'SURVEYING') OR (`to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING'))) OR
      (`trigger_type` = 'P2_ASSESSMENT_INVALIDATED' AND `from_stage` IN ('P3','P4') AND `to_stage` = 'P2' AND `to_status` = 'GRADE_CONFIRMING') OR
      (`trigger_type` = 'P3_CHECKLIST_SUBMITTED' AND `from_stage` = 'P3' AND `to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING') OR
      (`trigger_type` = 'P4_PLAN_SUBMITTED' AND `from_stage` = 'P4' AND `from_status` = 'PLAN_DRAFTING' AND `to_stage` = 'P5' AND `to_status` = 'APPROVING') OR
      (`trigger_type` IN ('P5_SOURCE_INVALIDATED','P5_APPROVAL_REJECTED') AND `from_stage` = 'P5' AND `from_status` = 'APPROVING' AND `to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING') OR
      (`trigger_type` = 'P5_APPROVAL_APPROVED' AND `from_stage` = 'P5' AND `from_status` = 'APPROVING' AND `to_stage` = 'P6' AND `to_status` = 'CLOSURE_IN_PROGRESS')
    ), FALSE) = TRUE
  );

CREATE TABLE `cut_plan_revision` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `cutover_task_id` bigint NOT NULL,
  `revision_no` int NOT NULL, `origin_code` varchar(32) NOT NULL, `edit_mode_code` varchar(32) DEFAULT NULL,
  `grade_code` varchar(8) DEFAULT NULL, `assessment_id` bigint DEFAULT NULL, `assessment_version` int DEFAULT NULL,
  `checklist_id` bigint DEFAULT NULL, `checklist_version` int DEFAULT NULL,
  `configuration_revision_id` bigint DEFAULT NULL, `configuration_code` varchar(64) DEFAULT NULL,
  `configuration_revision_no` int DEFAULT NULL, `template_section_snapshot` json DEFAULT NULL,
  `source_snapshot` json NOT NULL, `content_snapshot` json DEFAULT NULL,
  `file_artifact_id` bigint DEFAULT NULL, `file_version_no` int DEFAULT NULL,
  `file_reference_key` varchar(128) DEFAULT NULL, `file_fact_version` json DEFAULT NULL,
  `file_scope_version` bigint DEFAULT NULL, `file_sha256` char(64) DEFAULT NULL,
  `ownership_confirmed` bit(1) DEFAULT NULL, `status_code` varchar(32) DEFAULT NULL,
  `current_marker` tinyint DEFAULT NULL, `submitted_by` bigint DEFAULT NULL, `submitted_at` datetime(3) DEFAULT NULL,
  `approval_instance_id` bigint DEFAULT NULL, `approval_version` int DEFAULT NULL,
  `source_plan_revision_id` bigint DEFAULT NULL, `revision_reason_code` varchar(32) DEFAULT NULL,
  `invalidated_by` bigint DEFAULT NULL, `invalidated_at` datetime(3) DEFAULT NULL,
  `invalidation_reason_code` varchar(64) DEFAULT NULL, `legacy_plan_id` bigint DEFAULT NULL,
  `legacy_status_raw` tinyint DEFAULT NULL, `legacy_source_version` int DEFAULT NULL,
  `legacy_mapping_version` varchar(64) DEFAULT NULL, `version` int NOT NULL,
  `creator` varchar(64) NOT NULL, `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL, `update_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_plan_revision_no` (`tenant_id`,`cutover_task_id`,`revision_no`),
  UNIQUE KEY `uk_cut_plan_current` (`tenant_id`,`cutover_task_id`,`current_marker`),
  UNIQUE KEY `uk_cut_plan_legacy` (`tenant_id`,`legacy_plan_id`),
  KEY `idx_cut_plan_source_revision` (`tenant_id`,`source_plan_revision_id`),
  CONSTRAINT `fk_cut_plan_task` FOREIGN KEY (`cutover_task_id`) REFERENCES `cut_task` (`id`),
  CONSTRAINT `chk_cut_plan_values` CHECK (`revision_no` > 0 AND `version` >= 0
    AND (`assessment_version` IS NULL OR `assessment_version` > 0)
    AND (`checklist_version` IS NULL OR `checklist_version` > 0)
    AND (`configuration_revision_no` IS NULL OR `configuration_revision_no` > 0)
    AND (`file_version_no` IS NULL OR `file_version_no` > 0)
    AND (`file_scope_version` IS NULL OR `file_scope_version` >= 0)
    AND (`approval_version` IS NULL OR `approval_version` >= 0)
    AND (`legacy_source_version` IS NULL OR `legacy_source_version` >= 0)),
  CONSTRAINT `chk_cut_plan_marker` CHECK (`current_marker` IS NULL OR `current_marker` = 1),
  CONSTRAINT `chk_cut_plan_origin` CHECK (`origin_code` IN ('NEW_PLATFORM','LEGACY_FORWARD')),
  CONSTRAINT `chk_cut_plan_derivation` CHECK (
    COALESCE((`source_plan_revision_id` IS NULL AND `revision_reason_code` IS NULL), FALSE)
    OR COALESCE((`source_plan_revision_id` IS NOT NULL
      AND `revision_reason_code` IN ('APPROVAL_REJECTED','DUTY_CHANGED','SOURCE_REPLACED')), FALSE)),
  CONSTRAINT `chk_cut_plan_union` CHECK (
    COALESCE((`origin_code` = 'LEGACY_FORWARD' AND `edit_mode_code` IS NULL AND `grade_code` IS NULL
      AND `assessment_id` IS NULL AND `assessment_version` IS NULL AND `checklist_id` IS NULL
      AND `checklist_version` IS NULL AND `configuration_revision_id` IS NULL AND `configuration_code` IS NULL
      AND `configuration_revision_no` IS NULL AND `template_section_snapshot` IS NULL
      AND `content_snapshot` IS NULL AND `file_artifact_id` IS NULL AND `file_version_no` IS NULL
      AND `file_reference_key` IS NULL AND `file_fact_version` IS NULL AND `file_scope_version` IS NULL
      AND `file_sha256` IS NULL AND `ownership_confirmed` IS NULL AND `status_code` IS NULL
      AND `current_marker` IS NULL AND `submitted_by` IS NULL AND `submitted_at` IS NULL
      AND `approval_instance_id` IS NULL AND `approval_version` IS NULL
      AND `source_plan_revision_id` IS NULL AND `revision_reason_code` IS NULL
      AND `invalidated_by` IS NULL AND `invalidated_at` IS NULL AND `invalidation_reason_code` IS NULL
      AND `legacy_plan_id` > 0 AND `legacy_status_raw` BETWEEN 0 AND 4
      AND `legacy_source_version` >= 0 AND CHAR_LENGTH(TRIM(`legacy_mapping_version`)) BETWEEN 1 AND 64), FALSE)
    OR COALESCE((`origin_code` = 'NEW_PLATFORM' AND `grade_code` IN ('A','B','C','D')
      AND `assessment_id` > 0 AND `assessment_version` > 0
      AND `configuration_revision_id` > 0 AND CHAR_LENGTH(TRIM(`configuration_code`)) BETWEEN 1 AND 64
      AND `configuration_revision_no` > 0 AND `template_section_snapshot` IS NOT NULL
      AND `legacy_plan_id` IS NULL AND `legacy_status_raw` IS NULL
      AND `legacy_source_version` IS NULL AND `legacy_mapping_version` IS NULL
      AND ((`grade_code` IN ('A','B','C') AND `checklist_id` > 0 AND `checklist_version` > 0)
        OR (`grade_code` = 'D' AND `checklist_id` IS NULL AND `checklist_version` IS NULL))
      AND ((`edit_mode_code` = 'FULL_FILE_UPLOAD' AND `content_snapshot` IS NULL
        AND `file_artifact_id` > 0 AND `file_version_no` > 0
        AND CHAR_LENGTH(TRIM(`file_reference_key`)) BETWEEN 1 AND 128
        AND `file_fact_version` IS NOT NULL AND `file_scope_version` >= 0
        AND `file_sha256` REGEXP '^[0-9a-f]{64}$' AND `ownership_confirmed` = b'1')
        OR ((`edit_mode_code` = 'ONLINE_TEMPLATE_STANDARD' AND `grade_code` IN ('A','B','C')
            OR `edit_mode_code` = 'ONLINE_TEMPLATE_SIMPLE_D' AND `grade_code` = 'D')
          AND `content_snapshot` IS NOT NULL AND `file_artifact_id` IS NULL AND `file_version_no` IS NULL
          AND `file_reference_key` IS NULL AND `file_fact_version` IS NULL AND `file_scope_version` IS NULL
          AND `file_sha256` IS NULL AND `ownership_confirmed` IS NULL))
      AND ((`status_code` = 'DRAFT' AND `current_marker` = 1 AND `submitted_by` IS NULL
        AND `submitted_at` IS NULL AND `approval_instance_id` IS NULL AND `approval_version` IS NULL
        AND `invalidated_by` IS NULL AND `invalidated_at` IS NULL AND `invalidation_reason_code` IS NULL)
        OR (`status_code` = 'SUBMITTED' AND `submitted_by` > 0 AND `submitted_at` IS NOT NULL
          AND `approval_instance_id` > 0 AND `approval_version` >= 0
          AND `invalidated_by` IS NULL AND `invalidated_at` IS NULL AND `invalidation_reason_code` IS NULL)
        OR (`status_code` = 'INVALIDATED' AND `current_marker` IS NULL AND `submitted_by` > 0
          AND `submitted_at` IS NOT NULL AND `approval_instance_id` > 0 AND `approval_version` >= 0
          AND `invalidated_by` > 0 AND `invalidated_at` IS NOT NULL
          AND CHAR_LENGTH(TRIM(`invalidation_reason_code`)) BETWEEN 1 AND 64))), FALSE)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-004 P4方案revision';

CREATE TABLE `cut_step` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `plan_revision_id` bigint NOT NULL,
  `section_code` varchar(32) NOT NULL, `step_no` int NOT NULL, `content` varchar(4000) NOT NULL,
  `version` int NOT NULL, `creator` varchar(64) NOT NULL, `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL, `update_time` datetime(3) NOT NULL, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_cut_step_order` (`tenant_id`,`plan_revision_id`,`section_code`,`step_no`),
  KEY `idx_cut_step_plan` (`tenant_id`,`plan_revision_id`,`section_code`,`step_no`,`id`),
  CONSTRAINT `fk_cut_step_plan` FOREIGN KEY (`plan_revision_id`) REFERENCES `cut_plan_revision` (`id`),
  CONSTRAINT `chk_cut_step_values` CHECK (`step_no` > 0 AND `version` >= 0 AND CHAR_LENGTH(TRIM(`content`)) BETWEEN 1 AND 4000),
  CONSTRAINT `chk_cut_step_section` CHECK (`section_code` IN ('PRE_OPERATION','OPERATION','CLOSING_COLLECTION','POST_BUSINESS_TEST','ROLLBACK','POST_CUTOVER_SUPPORT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-004方案步骤';

CREATE TABLE `cut_cutover_support_arrangement` (
  `id` bigint NOT NULL, `tenant_id` bigint NOT NULL, `plan_revision_id` bigint NOT NULL,
  `role_code` varchar(32) NOT NULL, `person_name` varchar(128) NOT NULL,
  `duty_description` varchar(1000) NOT NULL, `phone` varchar(64) NOT NULL, `arrival_time` datetime(3) NOT NULL,
  `version` int NOT NULL, `creator` varchar(64) NOT NULL, `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL, `update_time` datetime(3) NOT NULL, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_cut_support_role` (`tenant_id`,`plan_revision_id`,`role_code`),
  KEY `idx_cut_support_plan` (`tenant_id`,`plan_revision_id`,`role_code`,`id`),
  CONSTRAINT `fk_cut_support_plan` FOREIGN KEY (`plan_revision_id`) REFERENCES `cut_plan_revision` (`id`),
  CONSTRAINT `chk_cut_support_values` CHECK (`version` >= 0
    AND CHAR_LENGTH(TRIM(`person_name`)) BETWEEN 1 AND 128
    AND CHAR_LENGTH(TRIM(`duty_description`)) BETWEEN 1 AND 1000
    AND CHAR_LENGTH(TRIM(`phone`)) BETWEEN 1 AND 64),
  CONSTRAINT `chk_cut_support_role` CHECK (`role_code` IN ('CUSTOMER','DP_FIRST_LINE','DP_SECOND_LINE','DP_RND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-004割接保障安排';
