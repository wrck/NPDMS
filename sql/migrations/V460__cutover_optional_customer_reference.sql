-- CUT-01 and amendment 014: a CRM customer reference is optional for a CUT task.
-- Retain the project/device scope, readiness, lifecycle and legacy-origin constraints.
ALTER TABLE `cut_task`
  DROP CHECK `chk_cut_task_origin_union`,
  ADD CONSTRAINT `chk_cut_task_origin_union` CHECK (
    COALESCE((`task_origin` = 'NEW_PLATFORM'
      AND `legacy_task_id` IS NULL AND `legacy_cutover_type_raw` IS NULL
      AND `legacy_network_mode_raw` IS NULL AND `legacy_status_value` IS NULL
      AND `legacy_source_version` IS NULL AND `legacy_mapping_version` IS NULL
      AND `owner_user_id` > 0 AND (`customer_id` IS NULL OR `customer_id` > 0)
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
      AND ((`implementation_readiness_snapshot_id` > 0
            AND `implementation_readiness_snapshot_version` >= 0)
        OR (`intake_source_type` IN ('SELF_CREATED','ITR')
            AND `implementation_readiness_snapshot_id` IS NULL
            AND `implementation_readiness_snapshot_version` IS NULL
            AND JSON_UNQUOTE(JSON_EXTRACT(`readiness_context_snapshot`, '$.decision')) = 'NOT_APPLICABLE'))
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
