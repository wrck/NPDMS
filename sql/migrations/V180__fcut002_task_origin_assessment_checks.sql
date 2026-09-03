-- F-CUT-002 前向补齐任务来源联合与人工评估状态联合约束。
-- V146 已执行，不回改历史迁移；先校验现存行，再以单表 ALTER 增加约束。

DROP PROCEDURE IF EXISTS `fcut002_require_valid_origin_and_assessment`;
DELIMITER $$
CREATE PROCEDURE `fcut002_require_valid_origin_and_assessment`()
BEGIN
  DECLARE invalid_task_count BIGINT DEFAULT 0;
  DECLARE invalid_assessment_count BIGINT DEFAULT 0;

  SELECT COUNT(*) INTO invalid_task_count
  FROM `cut_task`
  WHERE CASE WHEN (
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
      AND `current_stage` IN ('P2','P3','P4') AND `task_status` <> 'LEGACY_UNKNOWN'
      AND `implementation_readiness_snapshot_id` > 0
      AND `implementation_readiness_snapshot_version` >= 0
      AND `project_scope_version` >= 0
      AND `project_context_snapshot` IS NOT NULL AND `device_scope_watermark` IS NOT NULL
      AND `customer_context_snapshot` IS NOT NULL AND `readiness_context_snapshot` IS NOT NULL), FALSE)
    OR
    COALESCE((`task_origin` = 'LEGACY_FORWARD' AND `intake_source_type` = 'LEGACY_FORWARD'
      AND `task_status` = 'LEGACY_UNKNOWN'
      AND `legacy_task_id` > 0
      AND CHAR_LENGTH(TRIM(`legacy_cutover_type_raw`)) BETWEEN 1 AND 32
      AND (`legacy_network_mode_raw` IS NULL
        OR CHAR_LENGTH(TRIM(`legacy_network_mode_raw`)) BETWEEN 1 AND 32)
      AND `legacy_status_value` BETWEEN 0 AND 8
      AND `legacy_source_version` >= 0
      AND CHAR_LENGTH(TRIM(`legacy_mapping_version`)) BETWEEN 1 AND 64
      AND `previous_task_id` IS NULL AND `background` IS NULL
      AND `cutover_type` IS NULL AND `network_mode` IS NULL
      AND `configuration_revision_id` IS NULL AND `configuration_code` IS NULL
      AND `configuration_revision_no` IS NULL AND `owner_user_id` IS NULL
      AND `customer_id` IS NULL AND `current_stage` IS NULL
      AND `implementation_readiness_snapshot_id` IS NULL
      AND `implementation_readiness_snapshot_version` IS NULL
      AND `project_scope_version` IS NULL AND `project_context_snapshot` IS NULL
      AND `device_scope_watermark` IS NULL AND `customer_context_snapshot` IS NULL
      AND `readiness_context_snapshot` IS NULL AND `manual_grade` IS NULL
      AND `current_assessment_id` IS NULL), FALSE)
  ) THEN 0 ELSE 1 END = 1;

  SELECT COUNT(*) INTO invalid_assessment_count
  FROM `cut_assessment`
  WHERE CASE WHEN (
    COALESCE((`assessment_status` = 'DRAFT'
      AND `submitted_by` IS NULL AND `submitted_at` IS NULL
      AND `invalidated_by` IS NULL AND `invalidated_at` IS NULL
      AND `invalidation_reason` IS NULL AND `simple_flow` = b'0'), FALSE)
    OR
    COALESCE((`assessment_status` = 'SUBMITTED'
      AND `manual_grade` IN ('A','B','C','D')
      AND `submitted_by` > 0 AND `submitted_at` IS NOT NULL
      AND `invalidated_by` IS NULL AND `invalidated_at` IS NULL
      AND `invalidation_reason` IS NULL AND `current_marker` = 1
      AND ((`manual_grade` = 'D' AND `simple_flow` = b'1')
        OR (`manual_grade` IN ('A','B','C') AND `simple_flow` = b'0'))
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.businessImportanceLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.operationComplexityLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.hiddenRiskLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.sparePartApplied')) <> 'NULL'
      AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.decision')) = 'READY'
      AND JSON_LENGTH(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.unmetCodes')) = 0
      AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.customerServiceLevel.status')) = 'AVAILABLE'), FALSE)
    OR
    COALESCE((`assessment_status` = 'INVALIDATED'
      AND `manual_grade` IN ('A','B','C','D')
      AND `submitted_by` > 0 AND `submitted_at` IS NOT NULL
      AND `invalidated_by` > 0 AND `invalidated_at` IS NOT NULL
      AND CHAR_LENGTH(TRIM(`invalidation_reason`)) BETWEEN 1 AND 1000
      AND `current_marker` IS NULL
      AND ((`manual_grade` = 'D' AND `simple_flow` = b'1')
        OR (`manual_grade` IN ('A','B','C') AND `simple_flow` = b'0'))
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.businessImportanceLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.operationComplexityLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.hiddenRiskLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.sparePartApplied')) <> 'NULL'
      AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.decision')) = 'READY'
      AND JSON_LENGTH(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.unmetCodes')) = 0
      AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.customerServiceLevel.status')) = 'AVAILABLE'), FALSE)
  ) THEN 0 ELSE 1 END = 1;

  IF invalid_task_count > 0 OR invalid_assessment_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-CUT-002 origin/status union preflight failed';
  END IF;
END$$
DELIMITER ;

CALL `fcut002_require_valid_origin_and_assessment`();
DROP PROCEDURE IF EXISTS `fcut002_require_valid_origin_and_assessment`;

ALTER TABLE `cut_task`
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
      AND `current_stage` IN ('P2','P3','P4') AND `task_status` <> 'LEGACY_UNKNOWN'
      AND `implementation_readiness_snapshot_id` > 0
      AND `implementation_readiness_snapshot_version` >= 0
      AND `project_scope_version` >= 0
      AND `project_context_snapshot` IS NOT NULL AND `device_scope_watermark` IS NOT NULL
      AND `customer_context_snapshot` IS NOT NULL AND `readiness_context_snapshot` IS NOT NULL), FALSE)
    OR
    COALESCE((`task_origin` = 'LEGACY_FORWARD' AND `intake_source_type` = 'LEGACY_FORWARD'
      AND `task_status` = 'LEGACY_UNKNOWN' AND `legacy_task_id` > 0
      AND CHAR_LENGTH(TRIM(`legacy_cutover_type_raw`)) BETWEEN 1 AND 32
      AND (`legacy_network_mode_raw` IS NULL
        OR CHAR_LENGTH(TRIM(`legacy_network_mode_raw`)) BETWEEN 1 AND 32)
      AND `legacy_status_value` BETWEEN 0 AND 8 AND `legacy_source_version` >= 0
      AND CHAR_LENGTH(TRIM(`legacy_mapping_version`)) BETWEEN 1 AND 64
      AND `previous_task_id` IS NULL AND `background` IS NULL
      AND `cutover_type` IS NULL AND `network_mode` IS NULL
      AND `configuration_revision_id` IS NULL AND `configuration_code` IS NULL
      AND `configuration_revision_no` IS NULL AND `owner_user_id` IS NULL
      AND `customer_id` IS NULL AND `current_stage` IS NULL
      AND `implementation_readiness_snapshot_id` IS NULL
      AND `implementation_readiness_snapshot_version` IS NULL
      AND `project_scope_version` IS NULL AND `project_context_snapshot` IS NULL
      AND `device_scope_watermark` IS NULL AND `customer_context_snapshot` IS NULL
      AND `readiness_context_snapshot` IS NULL AND `manual_grade` IS NULL
      AND `current_assessment_id` IS NULL), FALSE)
  );

ALTER TABLE `cut_assessment`
  ADD CONSTRAINT `chk_cut_assessment_status_union` CHECK (
    COALESCE((`assessment_status` = 'DRAFT'
      AND `submitted_by` IS NULL AND `submitted_at` IS NULL
      AND `invalidated_by` IS NULL AND `invalidated_at` IS NULL
      AND `invalidation_reason` IS NULL AND `simple_flow` = b'0'), FALSE)
    OR
    COALESCE((`assessment_status` = 'SUBMITTED'
      AND `manual_grade` IN ('A','B','C','D')
      AND `submitted_by` > 0 AND `submitted_at` IS NOT NULL
      AND `invalidated_by` IS NULL AND `invalidated_at` IS NULL
      AND `invalidation_reason` IS NULL AND `current_marker` = 1
      AND ((`manual_grade` = 'D' AND `simple_flow` = b'1')
        OR (`manual_grade` IN ('A','B','C') AND `simple_flow` = b'0'))
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.businessImportanceLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.operationComplexityLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.hiddenRiskLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.sparePartApplied')) <> 'NULL'
      AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.decision')) = 'READY'
      AND JSON_LENGTH(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.unmetCodes')) = 0
      AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.customerServiceLevel.status')) = 'AVAILABLE'), FALSE)
    OR
    COALESCE((`assessment_status` = 'INVALIDATED'
      AND `manual_grade` IN ('A','B','C','D')
      AND `submitted_by` > 0 AND `submitted_at` IS NOT NULL
      AND `invalidated_by` > 0 AND `invalidated_at` IS NOT NULL
      AND CHAR_LENGTH(TRIM(`invalidation_reason`)) BETWEEN 1 AND 1000
      AND `current_marker` IS NULL
      AND ((`manual_grade` = 'D' AND `simple_flow` = b'1')
        OR (`manual_grade` IN ('A','B','C') AND `simple_flow` = b'0'))
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.businessImportanceLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.operationComplexityLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.hiddenRiskLevel')) <> 'NULL'
      AND JSON_TYPE(JSON_EXTRACT(`answer_snapshot`, '$.sparePartApplied')) <> 'NULL'
      AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.decision')) = 'READY'
      AND JSON_LENGTH(JSON_EXTRACT(`context_snapshot`, '$.implementationReadiness.unmetCodes')) = 0
      AND JSON_UNQUOTE(JSON_EXTRACT(`context_snapshot`, '$.customerServiceLevel.status')) = 'AVAILABLE'), FALSE)
  );
