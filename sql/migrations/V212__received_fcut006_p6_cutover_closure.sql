-- Selectively received from codex/f-cut-001-matrices@cb5098f36239f70db639f07e0564300033138f16 (sql/migrations/V155__fcut006_p6_cutover_closure.sql).
-- Renumbered after current master migration chain; Feature remains IN_PROGRESS.

-- F-CUT-006 P6割接闭环。现存任务和阶段历史先失败关闭校验，不更新业务行。

DROP PROCEDURE IF EXISTS `fcut006_require_stage_contract`;
DELIMITER $$
CREATE PROCEDURE `fcut006_require_stage_contract`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM `cut_task`
    WHERE CASE WHEN (
      (`task_origin` = 'NEW_PLATFORM' AND (
        (`current_stage` = 'P2' AND `task_status` = 'GRADE_CONFIRMING') OR
        (`current_stage` = 'P3' AND `task_status` = 'SURVEYING') OR
        (`current_stage` = 'P4' AND `task_status` = 'PLAN_DRAFTING') OR
        (`current_stage` = 'P5' AND `task_status` = 'APPROVING') OR
        (`current_stage` = 'P6' AND `task_status` = 'CLOSURE_IN_PROGRESS')
      )) OR
      (`task_origin` = 'LEGACY_FORWARD' AND `current_stage` IS NULL AND `task_status` = 'LEGACY_UNKNOWN')
    ) THEN 0 ELSE 1 END = 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'F-CUT-006 cut_task stage preflight failed';
  END IF;

  IF EXISTS (
    SELECT 1 FROM `cut_task_stage_history`
    WHERE CASE WHEN (
      (`trigger_type` = 'P1_ACCEPTED' AND `from_stage` = 'P1' AND `to_stage` = 'P2' AND `to_status` = 'GRADE_CONFIRMING') OR
      (`trigger_type` = 'P2_ASSESSMENT_SUBMITTED' AND `from_stage` = 'P2' AND ((`to_stage` = 'P3' AND `to_status` = 'SURVEYING') OR (`to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING'))) OR
      (`trigger_type` = 'P2_ASSESSMENT_INVALIDATED' AND `from_stage` IN ('P3','P4') AND `to_stage` = 'P2' AND `to_status` = 'GRADE_CONFIRMING') OR
      (`trigger_type` = 'P3_CHECKLIST_SUBMITTED' AND `from_stage` = 'P3' AND `to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING') OR
      (`trigger_type` = 'P4_PLAN_SUBMITTED' AND `from_stage` = 'P4' AND `from_status` = 'PLAN_DRAFTING' AND `to_stage` = 'P5' AND `to_status` = 'APPROVING') OR
      (`trigger_type` IN ('P5_SOURCE_INVALIDATED','P5_APPROVAL_REJECTED') AND `from_stage` = 'P5' AND `from_status` = 'APPROVING' AND `to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING') OR
      (`trigger_type` = 'P5_APPROVAL_APPROVED' AND `from_stage` = 'P5' AND `from_status` = 'APPROVING' AND `to_stage` = 'P6' AND `to_status` = 'CLOSURE_IN_PROGRESS')
    ) THEN 0 ELSE 1 END = 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'F-CUT-006 stage history preflight failed';
  END IF;
END$$
DELIMITER ;

CALL `fcut006_require_stage_contract`();
DROP PROCEDURE IF EXISTS `fcut006_require_stage_contract`;

ALTER TABLE `cut_task`
  DROP CHECK `chk_cut_task_status`,
  DROP CHECK `chk_cut_task_origin_union`,
  ADD CONSTRAINT `chk_cut_task_status` CHECK (`task_status` IN ('GRADE_CONFIRMING','SURVEYING','PLAN_DRAFTING','APPROVING','CLOSURE_IN_PROGRESS','ARCHIVED','LEGACY_UNKNOWN')),
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
        OR (`current_stage` = 'P6' AND `task_status` IN ('CLOSURE_IN_PROGRESS','ARCHIVED')))
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
  ADD CONSTRAINT `chk_cut_stage_code` CHECK (`from_stage` IN ('P1','P2','P3','P4','P5','P6') AND `to_stage` IN ('P2','P3','P4','P5','P6')),
  ADD CONSTRAINT `chk_cut_stage_trigger` CHECK (
    COALESCE((
      (`trigger_type` = 'P1_ACCEPTED' AND `from_stage` = 'P1' AND `to_stage` = 'P2' AND `to_status` = 'GRADE_CONFIRMING') OR
      (`trigger_type` = 'P2_ASSESSMENT_SUBMITTED' AND `from_stage` = 'P2' AND ((`to_stage` = 'P3' AND `to_status` = 'SURVEYING') OR (`to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING'))) OR
      (`trigger_type` = 'P2_ASSESSMENT_INVALIDATED' AND `from_stage` IN ('P3','P4') AND `to_stage` = 'P2' AND `to_status` = 'GRADE_CONFIRMING') OR
      (`trigger_type` = 'P3_CHECKLIST_SUBMITTED' AND `from_stage` = 'P3' AND `to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING') OR
      (`trigger_type` = 'P4_PLAN_SUBMITTED' AND `from_stage` = 'P4' AND `from_status` = 'PLAN_DRAFTING' AND `to_stage` = 'P5' AND `to_status` = 'APPROVING') OR
      (`trigger_type` IN ('P5_SOURCE_INVALIDATED','P5_APPROVAL_REJECTED') AND `from_stage` = 'P5' AND `from_status` = 'APPROVING' AND `to_stage` = 'P4' AND `to_status` = 'PLAN_DRAFTING') OR
      (`trigger_type` = 'P5_APPROVAL_APPROVED' AND `from_stage` = 'P5' AND `from_status` = 'APPROVING' AND `to_stage` = 'P6' AND `to_status` = 'CLOSURE_IN_PROGRESS') OR
      (`trigger_type` = 'P6_CLOSURE_SUBMITTED' AND `from_stage` = 'P6' AND `from_status` = 'CLOSURE_IN_PROGRESS' AND `to_stage` = 'P6' AND `to_status` = 'ARCHIVED')
    ), FALSE) = TRUE
  );

CREATE TABLE `cut_cutover_closure` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `approval_instance_id` bigint NOT NULL,
  `approval_version` int NOT NULL,
  `plan_revision_id` bigint NOT NULL,
  `plan_revision_no` int NOT NULL,
  `plan_version` int NOT NULL,
  `task_version_at_p6` int NOT NULL,
  `device_scope_watermark` json NOT NULL,
  `status_code` varchar(16) NOT NULL,
  `pre_check_normal` bit(1) DEFAULT NULL,
  `pre_check_detail` text DEFAULT NULL,
  `execution_normal` bit(1) DEFAULT NULL,
  `execution_detail` text DEFAULT NULL,
  `test_normal` bit(1) DEFAULT NULL,
  `test_detail` text DEFAULT NULL,
  `rollback_occurred` bit(1) DEFAULT NULL,
  `rollback_successful` bit(1) DEFAULT NULL,
  `rollback_reason` text DEFAULT NULL,
  `legacy_items` text DEFAULT NULL,
  `final_result_code` varchar(16) DEFAULT NULL,
  `result_ref` varchar(128) DEFAULT NULL,
  `submitted_by` bigint DEFAULT NULL,
  `submitted_at` datetime(3) DEFAULT NULL,
  `archived_at` datetime(3) DEFAULT NULL,
  `version` int NOT NULL,
  `creator` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_closure_task` (`tenant_id`,`task_id`),
  KEY `idx_cut_closure_project_status` (`tenant_id`,`project_id`,`status_code`,`id`),
  CONSTRAINT `fk_cut_closure_task` FOREIGN KEY (`task_id`) REFERENCES `cut_task` (`id`),
  CONSTRAINT `chk_cut_closure_values` CHECK (`id` > 0 AND `project_id` > 0 AND `approval_instance_id` > 0
    AND `approval_version` >= 0 AND `plan_revision_id` > 0 AND `plan_revision_no` > 0
    AND `plan_version` >= 0 AND `task_version_at_p6` >= 0 AND `version` >= 0),
  CONSTRAINT `chk_cut_closure_status` CHECK (`status_code` IN ('DRAFT','SUBMITTED')),
  CONSTRAINT `chk_cut_closure_result_code` CHECK (`final_result_code` IS NULL OR `final_result_code` IN ('SUCCESS','FAILED')),
  CONSTRAINT `chk_cut_closure_details` CHECK (
    (`pre_check_detail` IS NULL OR CHAR_LENGTH(`pre_check_detail`) <= 4000)
    AND (`execution_detail` IS NULL OR CHAR_LENGTH(`execution_detail`) <= 4000)
    AND (`test_detail` IS NULL OR CHAR_LENGTH(`test_detail`) <= 4000)
    AND (`legacy_items` IS NULL OR CHAR_LENGTH(`legacy_items`) <= 4000)
    AND (`pre_check_normal` IS NULL OR `pre_check_normal` = b'1'
      OR COALESCE(CHAR_LENGTH(TRIM(`pre_check_detail`)) BETWEEN 1 AND 4000, FALSE))
    AND (`execution_normal` IS NULL OR `execution_normal` = b'1'
      OR COALESCE(CHAR_LENGTH(TRIM(`execution_detail`)) BETWEEN 1 AND 4000, FALSE))
    AND (`test_normal` IS NULL OR `test_normal` = b'1'
      OR COALESCE(CHAR_LENGTH(TRIM(`test_detail`)) BETWEEN 1 AND 4000, FALSE))),
  CONSTRAINT `chk_cut_closure_rollback` CHECK (
    (`rollback_occurred` IS NULL AND `rollback_successful` IS NULL AND `rollback_reason` IS NULL)
    OR (`rollback_occurred` = b'0' AND `rollback_successful` IS NULL AND `rollback_reason` IS NULL)
    OR COALESCE((`rollback_occurred` = b'1' AND `rollback_successful` IS NOT NULL
      AND CHAR_LENGTH(TRIM(`rollback_reason`)) BETWEEN 1 AND 4000), FALSE)),
  CONSTRAINT `chk_cut_closure_lifecycle` CHECK (
    (`status_code` = 'DRAFT' AND `final_result_code` IS NULL AND `result_ref` IS NULL
      AND `submitted_by` IS NULL AND `submitted_at` IS NULL AND `archived_at` IS NULL)
    OR COALESCE((`status_code` = 'SUBMITTED' AND `pre_check_normal` IS NOT NULL
      AND `execution_normal` IS NOT NULL AND `test_normal` IS NOT NULL AND `rollback_occurred` IS NOT NULL
      AND `final_result_code` IN ('SUCCESS','FAILED') AND `submitted_by` > 0
      AND `submitted_at` IS NOT NULL AND `archived_at` = `submitted_at`
      AND ((`final_result_code` = 'SUCCESS' AND `result_ref` = CONCAT('CUTOVER_CLOSURE:', `id`, ':', `version`))
        OR (`final_result_code` = 'FAILED' AND `result_ref` IS NULL))), FALSE))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-006 P6割接闭环';

CREATE TABLE `cut_cutover_closure_attachment` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `closure_id` bigint NOT NULL,
  `purpose_code` varchar(40) NOT NULL,
  `reference_key` varchar(128) NOT NULL,
  `artifact_id` bigint NOT NULL,
  `file_version_no` int NOT NULL,
  `file_fact_version` json NOT NULL,
  `file_scope_version` bigint NOT NULL,
  `file_hash` char(64) NOT NULL,
  `version` int NOT NULL,
  `creator` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_closure_attachment` (`tenant_id`,`closure_id`,`purpose_code`,`reference_key`),
  KEY `idx_cut_closure_attachment_order` (`tenant_id`,`closure_id`,`purpose_code`,`reference_key`,`id`),
  CONSTRAINT `fk_cut_closure_attachment_root` FOREIGN KEY (`closure_id`) REFERENCES `cut_cutover_closure` (`id`),
  CONSTRAINT `chk_cut_closure_attachment_values` CHECK (`artifact_id` > 0 AND `file_version_no` > 0
    AND `file_scope_version` >= 0 AND `version` >= 0
    AND CHAR_LENGTH(TRIM(`reference_key`)) BETWEEN 1 AND 128 AND `file_hash` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `chk_cut_closure_attachment_purpose` CHECK (`purpose_code` IN
    ('POST_COLLECTION_CHECKLIST','IMPLEMENTATION_COMMITMENT','OTHER_EVIDENCE','MANUAL_COLLECTION_RESULT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-006闭环文件事实';

CREATE TABLE `cut_cutover_collection_evidence` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `closure_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `device_id` bigint NOT NULL,
  `collection_stage_code` varchar(24) NOT NULL,
  `evidence_type_code` varchar(32) NOT NULL,
  `collection_task_id` varchar(128) NOT NULL,
  `callback_event_id` varchar(128) DEFAULT NULL,
  `result_ref` varchar(256) DEFAULT NULL,
  `result_version` varchar(128) DEFAULT NULL,
  `original_failed_collection_task_id` varchar(128) DEFAULT NULL,
  `manual_attachment_id` bigint DEFAULT NULL,
  `dispatch_marker` tinyint GENERATED ALWAYS AS (CASE WHEN `evidence_type_code` IN ('DISPATCH_ACCEPTED','DISPATCH_FAILED') THEN 1 ELSE NULL END) STORED,
  `callback_marker` tinyint GENERATED ALWAYS AS (CASE WHEN `evidence_type_code` IN ('CALLBACK_SUCCEEDED','CALLBACK_FAILED') THEN 1 ELSE NULL END) STORED,
  `manual_marker` tinyint GENERATED ALWAYS AS (CASE WHEN `evidence_type_code` = 'MANUAL_UPLOAD' THEN 1 ELSE NULL END) STORED,
  `occurred_at` datetime(3) NOT NULL,
  `recorded_by` bigint NOT NULL,
  `creator` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_collection_dispatch` (`tenant_id`,`collection_task_id`,`dispatch_marker`),
  UNIQUE KEY `uk_cut_collection_callback` (`tenant_id`,`collection_task_id`,`callback_marker`),
  UNIQUE KEY `uk_cut_collection_manual` (`tenant_id`,`original_failed_collection_task_id`,`manual_marker`),
  UNIQUE KEY `uk_cut_collection_callback_event` (`tenant_id`,`callback_event_id`),
  KEY `idx_cut_collection_closure_order` (`tenant_id`,`closure_id`,`occurred_at`,`id`),
  CONSTRAINT `fk_cut_collection_closure` FOREIGN KEY (`closure_id`) REFERENCES `cut_cutover_closure` (`id`),
  CONSTRAINT `fk_cut_collection_task` FOREIGN KEY (`task_id`) REFERENCES `cut_task` (`id`),
  CONSTRAINT `fk_cut_collection_manual_attachment` FOREIGN KEY (`manual_attachment_id`) REFERENCES `cut_cutover_closure_attachment` (`id`),
  CONSTRAINT `chk_cut_collection_values` CHECK (`project_id` > 0 AND `device_id` > 0 AND `recorded_by` >= 0
    AND CHAR_LENGTH(TRIM(`collection_task_id`)) BETWEEN 1 AND 128),
  CONSTRAINT `chk_cut_collection_stage` CHECK (`collection_stage_code` IN ('PRE_CHECK','EXECUTION','TEST','ROLLBACK','POST_COLLECTION')),
  CONSTRAINT `chk_cut_collection_type` CHECK (`evidence_type_code` IN ('DISPATCH_ACCEPTED','DISPATCH_FAILED','CALLBACK_SUCCEEDED','CALLBACK_FAILED','MANUAL_UPLOAD')),
  CONSTRAINT `chk_cut_collection_union` CHECK (
    (`evidence_type_code` IN ('DISPATCH_ACCEPTED','DISPATCH_FAILED') AND `callback_event_id` IS NULL
      AND `result_ref` IS NULL AND `result_version` IS NULL
      AND `original_failed_collection_task_id` IS NULL AND `manual_attachment_id` IS NULL AND `recorded_by` > 0)
    OR COALESCE((`evidence_type_code` IN ('CALLBACK_SUCCEEDED','CALLBACK_FAILED')
      AND CHAR_LENGTH(TRIM(`callback_event_id`)) BETWEEN 1 AND 128
      AND CHAR_LENGTH(TRIM(`result_ref`)) BETWEEN 1 AND 256
      AND CHAR_LENGTH(TRIM(`result_version`)) BETWEEN 1 AND 128
      AND `original_failed_collection_task_id` IS NULL AND `manual_attachment_id` IS NULL AND `recorded_by` = 0), FALSE)
    OR COALESCE((`evidence_type_code` = 'MANUAL_UPLOAD' AND `callback_event_id` IS NULL
      AND `result_ref` IS NULL AND `result_version` IS NULL
      AND `collection_task_id` = `original_failed_collection_task_id`
      AND `manual_attachment_id` > 0 AND `recorded_by` > 0), FALSE))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-006采集证据追加历史';
