-- Approved CUT storage switch: canonical names, structured business constraints and frozen history.
-- Apply only after V474/V475 in the same approved deployment window; old binaries must not write here.
-- Historical JSON bytes, existing states, versions and audit columns remain unchanged.
-- MySQL column/generated expression renaming: https://dev.mysql.com/doc/refman/8.4/en/alter-table.html

ALTER TABLE `cut_cutover_configuration_revision`
  DROP CHECK `chk_cut_configuration_navigation_rule`,
  RENAME COLUMN `effective_from` TO `effective_start_time`,
  RENAME COLUMN `effective_to` TO `effective_end_time`,
  RENAME COLUMN `published_by` TO `publish_user_id`,
  RENAME COLUMN `published_at` TO `publish_time`,
  RENAME COLUMN `disabled_by` TO `disable_user_id`,
  RENAME COLUMN `disabled_at` TO `disable_time`,
  MODIFY COLUMN `dictionary_snapshot` JSON NULL DEFAULT NULL COMMENT '旧配置字典快照原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `dimension_definition_snapshot` JSON NULL DEFAULT NULL COMMENT '旧配置维度定义原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `plan_template_section_snapshot` JSON NULL DEFAULT NULL COMMENT '旧配置方案章节模板原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `validation_result_snapshot` JSON NULL DEFAULT NULL COMMENT '旧配置完整性校验结果原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `navigation_rule_snapshot` JSON NULL DEFAULT NULL COMMENT '旧提交后导航规则快照原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_configuration_navigation_rule` CHECK ((navigation_target_code IS NULL OR navigation_target_code IN ('CURRENT_STAGE_WORKBENCH','TASK_OVERVIEW')) = TRUE);

ALTER TABLE `cut_cutover_checklist_item_definition_revision`
  MODIFY COLUMN `interface_schema` JSON NULL DEFAULT NULL COMMENT '旧采集项字段结构原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `external_source_config` JSON NULL DEFAULT NULL COMMENT '旧外部来源配置原件；新业务写入NULL，禁止更改历史原值';

ALTER TABLE `cut_cutover_checklist_binding_rule_revision`
  RENAME COLUMN `item_definition_id` TO `item_definition_revision_id`,
  RENAME COLUMN `required_result` TO `required_result_flag`,
  MODIFY COLUMN `dimension_condition_snapshot` JSON NULL DEFAULT NULL COMMENT '旧规则维度条件原件；新业务写入NULL，禁止更改历史原值';

ALTER TABLE `cut_task`
  DROP CHECK `chk_cut_task_configuration_identity`,
  DROP CHECK `chk_cut_task_grade`,
  DROP CHECK `chk_cut_task_origin`,
  DROP CHECK `chk_cut_task_origin_union`,
  DROP CHECK `chk_cut_task_source_type`,
  DROP CHECK `chk_cut_task_source_union`,
  DROP CHECK `chk_cut_task_stage`,
  DROP CHECK `chk_cut_task_status`,
  RENAME COLUMN `cutover_type` TO `cutover_type_code`,
  RENAME COLUMN `network_mode` TO `network_mode_code`,
  RENAME COLUMN `scheduled_time` TO `schedule_time`,
  RENAME COLUMN `task_origin` TO `task_origin_code`,
  RENAME COLUMN `intake_source_type` TO `intake_source_type_code`,
  RENAME COLUMN `current_stage` TO `current_stage_code`,
  RENAME COLUMN `task_status` TO `task_status_code`,
  RENAME COLUMN `manual_grade` TO `manual_grade_code`,
  MODIFY COLUMN `project_context_snapshot` JSON NULL DEFAULT NULL COMMENT '旧项目上下文原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `device_scope_watermark` JSON NULL DEFAULT NULL COMMENT '旧设备范围水位原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `customer_context_snapshot` JSON NULL DEFAULT NULL COMMENT '旧客户等级来源事实原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `readiness_context_snapshot` JSON NULL DEFAULT NULL COMMENT '旧准入就绪来源事实原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_task_configuration_identity` CHECK (((((`task_origin_code` = _utf8mb4'NEW_PLATFORM') and (`configuration_revision_id` is not null) and (nullif(trim(`configuration_code`),_utf8mb4'') is not null) and (`configuration_revision_no` is not null) and (`configuration_revision_no` > 0)) or ((`task_origin_code` = _utf8mb4'LEGACY_FORWARD') and (`configuration_revision_id` is null) and (`configuration_code` is null) and (`configuration_revision_no` is null)))) = TRUE),
  ADD CONSTRAINT `chk_cut_task_grade` CHECK ((((`manual_grade_code` is null) or (`manual_grade_code` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C',_utf8mb4'D')))) = TRUE),
  ADD CONSTRAINT `chk_cut_task_origin` CHECK (((`task_origin_code` in (_utf8mb4'NEW_PLATFORM',_utf8mb4'LEGACY_FORWARD'))) = TRUE),
  ADD CONSTRAINT `chk_cut_task_origin_union` CHECK ((((0 <> coalesce(((`task_origin_code` = _utf8mb4'NEW_PLATFORM') and (`legacy_task_id` is null) and (`legacy_cutover_type_raw` is null) and (`legacy_network_mode_raw` is null) and (`legacy_status_value` is null) and (`legacy_source_version` is null) and (`legacy_mapping_version` is null) and (`owner_user_id` > 0) and ((`customer_id` is null) or (`customer_id` > 0)) and (char_length(trim(`background`)) between 1 and 4000) and (char_length(trim(`cutover_type_code`)) between 1 and 32) and (`configuration_revision_id` > 0) and (char_length(trim(`configuration_code`)) between 1 and 64) and (`configuration_revision_no` > 0) and (((`current_stage_code` = _utf8mb4'P2') and (`task_status_code` = _utf8mb4'GRADE_CONFIRMING')) or ((`current_stage_code` = _utf8mb4'P3') and (`task_status_code` = _utf8mb4'SURVEYING')) or ((`current_stage_code` = _utf8mb4'P4') and (`task_status_code` = _utf8mb4'PLAN_DRAFTING')) or ((`current_stage_code` = _utf8mb4'P5') and (`task_status_code` = _utf8mb4'APPROVING')) or ((`current_stage_code` = _utf8mb4'P6') and (`task_status_code` in (_utf8mb4'CLOSURE_IN_PROGRESS',_utf8mb4'ARCHIVED')))) and (((`implementation_readiness_snapshot_id` > 0) and (`implementation_readiness_snapshot_version` >= 0)) or ((`intake_source_type_code` in (_utf8mb4'SELF_CREATED',_utf8mb4'ITR')) and (`implementation_readiness_snapshot_id` is null) and (`implementation_readiness_snapshot_version` is null) and (`readiness_decision_code` = _utf8mb4'NOT_APPLICABLE'))) and (`project_scope_version` >= 0) and (`project_version` is not null) and (`project_scope_version` is not null) and (`customer_fact_status_code` is not null) and (`readiness_decision_code` is not null)),false)) or (0 <> coalesce(((`task_origin_code` = _utf8mb4'LEGACY_FORWARD') and (`intake_source_type_code` = _utf8mb4'LEGACY_FORWARD') and (`task_status_code` = _utf8mb4'LEGACY_UNKNOWN') and (`legacy_task_id` > 0) and (char_length(trim(`legacy_cutover_type_raw`)) between 1 and 32) and ((`legacy_network_mode_raw` is null) or (char_length(trim(`legacy_network_mode_raw`)) between 1 and 32)) and (`legacy_status_value` between 0 and 8) and (`legacy_source_version` >= 0) and (char_length(trim(`legacy_mapping_version`)) between 1 and 64) and (`previous_task_id` is null) and (`background` is null) and (`cutover_type_code` is null) and (`network_mode_code` is null) and (`configuration_revision_id` is null) and (`configuration_code` is null) and (`configuration_revision_no` is null) and (`owner_user_id` is null) and (`customer_id` is null) and (`current_stage_code` is null) and (`implementation_readiness_snapshot_id` is null) and (`implementation_readiness_snapshot_version` is null) and (`project_scope_version` is null) and (`project_version` is null) and (`project_scope_version` is null) and (`customer_fact_status_code` is null) and (`readiness_decision_code` is null) and (`manual_grade_code` is null) and (`current_assessment_id` is null)),false)))) = TRUE),
  ADD CONSTRAINT `chk_cut_task_source_type` CHECK (((`intake_source_type_code` in (_utf8mb4'SELF_CREATED',_utf8mb4'ITR',_utf8mb4'PROJECT_EVENT',_utf8mb4'LEGACY_FORWARD'))) = TRUE),
  ADD CONSTRAINT `chk_cut_task_source_union` CHECK (((((`intake_source_type_code` = _utf8mb4'SELF_CREATED') and (`task_origin_code` = _utf8mb4'NEW_PLATFORM') and (`source_system` is null) and (`source_business_no` is null) and (`business_event_id` is null)) or ((`intake_source_type_code` = _utf8mb4'ITR') and (`task_origin_code` = _utf8mb4'NEW_PLATFORM') and (`source_system` is not null) and (`source_business_no` is not null) and (`business_event_id` is null)) or ((`intake_source_type_code` = _utf8mb4'PROJECT_EVENT') and (`task_origin_code` = _utf8mb4'NEW_PLATFORM') and (`source_system` is null) and (`source_business_no` is null) and (`business_event_id` is not null)) or ((`intake_source_type_code` = _utf8mb4'LEGACY_FORWARD') and (`task_origin_code` = _utf8mb4'LEGACY_FORWARD') and (`source_system` is null) and (`source_business_no` is null) and (`business_event_id` is null)))) = TRUE),
  ADD CONSTRAINT `chk_cut_task_stage` CHECK ((((`current_stage_code` is null) or (`current_stage_code` in (_utf8mb4'P2',_utf8mb4'P3',_utf8mb4'P4',_utf8mb4'P5',_utf8mb4'P6')))) = TRUE),
  ADD CONSTRAINT `chk_cut_task_status` CHECK (((`task_status_code` in (_utf8mb4'GRADE_CONFIRMING',_utf8mb4'SURVEYING',_utf8mb4'PLAN_DRAFTING',_utf8mb4'APPROVING',_utf8mb4'CLOSURE_IN_PROGRESS',_utf8mb4'ARCHIVED',_utf8mb4'LEGACY_UNKNOWN'))) = TRUE);

ALTER TABLE `cut_task_device_scope`
  DROP CHECK `chk_cut_task_device_serial`,
  DROP CHECK `chk_cut_task_device_type_snapshot`,
  RENAME COLUMN `cutover_task_id` TO `task_id`,
  RENAME COLUMN `serial_number_snapshot` TO `sn`,
  RENAME COLUMN `device_type_code_snapshot` TO `device_type_code`,
  RENAME COLUMN `device_type_source_version_snapshot` TO `device_type_source_version`,
  ADD CONSTRAINT `chk_cut_task_device_serial` CHECK (((char_length(trim(`sn`)) between 1 and 128)) = TRUE),
  ADD CONSTRAINT `chk_cut_task_device_type_snapshot` CHECK (((((`device_type_code` is null) and (`device_type_source_version` is null)) or ((`device_type_code` is not null) and (`device_type_source_version` is not null) and (char_length(`device_type_code`) = char_length(trim(`device_type_code`))) and (char_length(`device_type_code`) between 1 and 64) and (char_length(`device_type_source_version`) = char_length(trim(`device_type_source_version`))) and (char_length(`device_type_source_version`) between 1 and 128)))) = TRUE);

ALTER TABLE `cut_task_stage_history`
  DROP CHECK `chk_cut_stage_code`,
  DROP CHECK `chk_cut_stage_trigger`,
  DROP CHECK `chk_cut_stage_values`,
  RENAME COLUMN `cutover_task_id` TO `task_id`,
  RENAME COLUMN `from_stage` TO `from_stage_code`,
  RENAME COLUMN `to_stage` TO `to_stage_code`,
  RENAME COLUMN `from_status` TO `from_status_code`,
  RENAME COLUMN `to_status` TO `to_status_code`,
  RENAME COLUMN `actor_id` TO `actor_user_id`,
  RENAME COLUMN `occurred_at` TO `occur_time`,
  ADD CONSTRAINT `chk_cut_stage_code` CHECK ((((`from_stage_code` in (_utf8mb4'P1',_utf8mb4'P2',_utf8mb4'P3',_utf8mb4'P4',_utf8mb4'P5',_utf8mb4'P6')) and (`to_stage_code` in (_utf8mb4'P2',_utf8mb4'P3',_utf8mb4'P4',_utf8mb4'P5',_utf8mb4'P6')))) = TRUE),
  ADD CONSTRAINT `chk_cut_stage_trigger` CHECK (((coalesce((((`trigger_type` = _utf8mb4'P1_ACCEPTED') and (`from_stage_code` = _utf8mb4'P1') and (`to_stage_code` = _utf8mb4'P2') and (`to_status_code` = _utf8mb4'GRADE_CONFIRMING')) or ((`trigger_type` = _utf8mb4'P2_ASSESSMENT_SUBMITTED') and (`from_stage_code` = _utf8mb4'P2') and (((`to_stage_code` = _utf8mb4'P3') and (`to_status_code` = _utf8mb4'SURVEYING')) or ((`to_stage_code` = _utf8mb4'P4') and (`to_status_code` = _utf8mb4'PLAN_DRAFTING')))) or ((`trigger_type` = _utf8mb4'P2_ASSESSMENT_INVALIDATED') and (`from_stage_code` in (_utf8mb4'P3',_utf8mb4'P4')) and (`to_stage_code` = _utf8mb4'P2') and (`to_status_code` = _utf8mb4'GRADE_CONFIRMING')) or ((`trigger_type` = _utf8mb4'P3_CHECKLIST_SUBMITTED') and (`from_stage_code` = _utf8mb4'P3') and (`to_stage_code` = _utf8mb4'P4') and (`to_status_code` = _utf8mb4'PLAN_DRAFTING')) or ((`trigger_type` = _utf8mb4'P4_PLAN_SUBMITTED') and (`from_stage_code` = _utf8mb4'P4') and (`from_status_code` = _utf8mb4'PLAN_DRAFTING') and (`to_stage_code` = _utf8mb4'P5') and (`to_status_code` = _utf8mb4'APPROVING')) or ((`trigger_type` in (_utf8mb4'P5_SOURCE_INVALIDATED',_utf8mb4'P5_APPROVAL_REJECTED')) and (`from_stage_code` = _utf8mb4'P5') and (`from_status_code` = _utf8mb4'APPROVING') and (`to_stage_code` = _utf8mb4'P4') and (`to_status_code` = _utf8mb4'PLAN_DRAFTING')) or ((`trigger_type` = _utf8mb4'P5_APPROVAL_APPROVED') and (`from_stage_code` = _utf8mb4'P5') and (`from_status_code` = _utf8mb4'APPROVING') and (`to_stage_code` = _utf8mb4'P6') and (`to_status_code` = _utf8mb4'CLOSURE_IN_PROGRESS')) or ((`trigger_type` = _utf8mb4'P6_CLOSURE_SUBMITTED') and (`from_stage_code` = _utf8mb4'P6') and (`from_status_code` = _utf8mb4'CLOSURE_IN_PROGRESS') and (`to_stage_code` = _utf8mb4'P6') and (`to_status_code` = _utf8mb4'ARCHIVED'))),false) = true)) = TRUE),
  ADD CONSTRAINT `chk_cut_stage_values` CHECK ((((`sequence_no` > 0) and (`actor_user_id` >= 0))) = TRUE);

ALTER TABLE `cut_assessment`
  DROP CHECK `chk_cut_assessment_grade`,
  DROP CHECK `chk_cut_assessment_status`,
  DROP CHECK `chk_cut_assessment_status_union`,
  RENAME COLUMN `cutover_task_id` TO `task_id`,
  RENAME COLUMN `assessment_status` TO `assessment_status_code`,
  RENAME COLUMN `manual_grade` TO `manual_grade_code`,
  RENAME COLUMN `simple_flow` TO `simple_flow_flag`,
  RENAME COLUMN `submitted_by` TO `submit_user_id`,
  RENAME COLUMN `submitted_at` TO `submit_time`,
  RENAME COLUMN `invalidated_by` TO `invalidate_user_id`,
  RENAME COLUMN `invalidated_at` TO `invalidate_time`,
  MODIFY COLUMN `answer_snapshot` JSON NULL DEFAULT NULL COMMENT '旧四项评估问卷答案原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `context_snapshot` JSON NULL DEFAULT NULL COMMENT '旧评估来源上下文原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_assessment_grade` CHECK ((((`manual_grade_code` is null) or (`manual_grade_code` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C',_utf8mb4'D')))) = TRUE),
  ADD CONSTRAINT `chk_cut_assessment_status` CHECK (((`assessment_status_code` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED',_utf8mb4'INVALIDATED'))) = TRUE),
  ADD CONSTRAINT `chk_cut_assessment_status_union` CHECK ((COALESCE((
 (assessment_status_code='DRAFT' AND submit_user_id IS NULL AND submit_time IS NULL AND invalidate_user_id IS NULL
  AND invalidate_time IS NULL AND invalidation_reason IS NULL AND simple_flow_flag=b'0')
 OR (assessment_status_code IN ('SUBMITTED','INVALIDATED') AND manual_grade_code IN ('A','B','C','D')
  AND submit_user_id>0 AND submit_time IS NOT NULL AND
  ((manual_grade_code='D' AND simple_flow_flag=b'1') OR (manual_grade_code IN ('A','B','C') AND simple_flow_flag=b'0'))
  AND `business_importance_level` IS NOT NULL AND `operation_complexity_level` IS NOT NULL AND `hidden_risk_level` IS NOT NULL AND `spare_part_applied_flag` IS NOT NULL AND ((readiness_decision_code='READY' AND implementation_readiness_snapshot_id>0 AND implementation_readiness_snapshot_version>=0)
 OR (intake_source_type_code IN ('SELF_CREATED','ITR') AND readiness_decision_code='NOT_APPLICABLE'
     AND implementation_readiness_snapshot_id IS NULL AND implementation_readiness_snapshot_version IS NULL))
  AND ((assessment_status_code='SUBMITTED' AND invalidate_user_id IS NULL AND invalidate_time IS NULL
        AND invalidation_reason IS NULL AND current_marker=1)
    OR (assessment_status_code='INVALIDATED' AND invalidate_user_id>0 AND invalidate_time IS NOT NULL
        AND CHAR_LENGTH(TRIM(invalidation_reason)) BETWEEN 1 AND 1000 AND current_marker IS NULL)))),FALSE)) = TRUE);

-- Rebuild renamed columns with their exact original foreign-key semantics.

ALTER TABLE `cut_cutover_checklist` DROP FOREIGN KEY `fk_cut_checklist_task`;

ALTER TABLE `cut_cutover_checklist`
  DROP CHECK `chk_cut_checklist_invalidation`,
  DROP CHECK `chk_cut_checklist_submit`,
  RENAME COLUMN `cutover_task_id` TO `task_id`,
  RENAME COLUMN `submitted_by` TO `submit_user_id`,
  RENAME COLUMN `submitted_at` TO `submit_time`,
  RENAME COLUMN `invalidated_at` TO `invalidate_time`,
  CHANGE COLUMN `current_marker` `current_marker` tinyint GENERATED ALWAYS AS ((case when (`invalidate_time` is null) then 1 else NULL end)) STORED COMMENT '',
  MODIFY COLUMN `input_snapshot` JSON NULL DEFAULT NULL COMMENT '旧清单匹配输入原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `config_revision_snapshot` JSON NULL DEFAULT NULL COMMENT '旧清单配置修订事实原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `match_trace` JSON NULL DEFAULT NULL COMMENT '旧清单规则匹配轨迹原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `config_gap_snapshot` JSON NULL DEFAULT NULL COMMENT '旧配置缺口事实原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_checklist_invalidation` CHECK (((((`status_code` <> _utf8mb4'INVALIDATED') and (`invalidate_time` is null) and (`invalidated_reason` is null)) or ((`status_code` = _utf8mb4'INVALIDATED') and (`invalidate_time` is not null) and (nullif(trim(`invalidated_reason`),_utf8mb4'') is not null)))) = TRUE),
  ADD CONSTRAINT `chk_cut_checklist_submit` CHECK (((((`status_code` = _utf8mb4'DRAFT') and (`submit_user_id` is null) and (`submit_time` is null)) or ((`status_code` in (_utf8mb4'SUBMITTED',_utf8mb4'INVALIDATED')) and (`submit_user_id` is not null) and (`submit_time` is not null)))) = TRUE);

ALTER TABLE `cut_cutover_checklist` ADD CONSTRAINT `fk_cut_checklist_task` FOREIGN KEY (`task_id`) REFERENCES `cut_task` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `cut_cutover_checklist_item`
  DROP CHECK `chk_cut_checklist_item_source`,
  RENAME COLUMN `item_definition_id` TO `item_definition_revision_id`,
  RENAME COLUMN `matched_rule_id` TO `binding_rule_revision_id`,
  RENAME COLUMN `matched_rule_version` TO `binding_rule_version`,
  MODIFY COLUMN `interface_schema_snapshot` JSON NULL DEFAULT NULL COMMENT '旧冻结界面与输入Schema原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `display_condition_snapshot` JSON NULL DEFAULT NULL COMMENT '旧冻结显示条件原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_checklist_item_source` CHECK (((((`source_code` = _utf8mb4'SYSTEM_MATCHED') and (`item_definition_revision_id` is not null) and (`item_definition_version` is not null) and (`item_definition_version` > 0) and (`custom_creator_user_id` is null)) or ((`source_code` = _utf8mb4'CUSTOM') and (`item_definition_revision_id` is null) and (`item_definition_version` is null) and (`binding_rule_revision_id` is null) and (`binding_rule_version` is null) and (`custom_creator_user_id` is not null)))) = TRUE);

ALTER TABLE `cut_cutover_checklist_item_result`
  DROP CHECK `chk_cut_checklist_manual_fact`,
  DROP CHECK `chk_cut_checklist_result_interval`,
  DROP CHECK `chk_cut_checklist_result_values`,
  RENAME COLUMN `queried_at` TO `query_time`,
  RENAME COLUMN `selection_started_at` TO `selection_start_time`,
  RENAME COLUMN `selection_ended_at` TO `selection_end_time`,
  RENAME COLUMN `selected_by` TO `select_user_id`,
  RENAME COLUMN `created_by` TO `create_user_id`,
  RENAME COLUMN `created_at` TO `create_time`,
  CHANGE COLUMN `current_marker` `current_marker` tinyint GENERATED ALWAYS AS ((case when (`selection_end_time` is null) then 1 else NULL end)) STORED COMMENT '',
  MODIFY COLUMN `answer_snapshot` JSON NULL DEFAULT NULL COMMENT '旧采集结果答案原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `query_condition_snapshot` JSON NULL DEFAULT NULL COMMENT '旧外部查询条件原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_checklist_manual_fact` CHECK ((manual_evidence_file_reference IS NULL OR (result_source_code='MANUAL' AND NULLIF(TRIM(manual_evidence_file_reference),'') IS NOT NULL)) = TRUE),
  ADD CONSTRAINT `chk_cut_checklist_result_interval` CHECK ((((`selection_end_time` is null) or (`selection_end_time` >= `selection_start_time`))) = TRUE),
  ADD CONSTRAINT `chk_cut_checklist_result_values` CHECK ((((`result_version` > 0) and (`select_user_id` > 0) and (`create_user_id` > 0))) = TRUE);

-- Rebuild renamed columns with their exact original foreign-key semantics.

ALTER TABLE `cut_plan_revision` DROP FOREIGN KEY `fk_cut_plan_task`;

ALTER TABLE `cut_plan_revision`
  DROP CHECK `chk_cut_plan_union`,
  RENAME COLUMN `cutover_task_id` TO `task_id`,
  RENAME COLUMN `ownership_confirmed` TO `ownership_confirmed_flag`,
  RENAME COLUMN `submitted_by` TO `submit_user_id`,
  RENAME COLUMN `submitted_at` TO `submit_time`,
  RENAME COLUMN `invalidated_by` TO `invalidate_user_id`,
  RENAME COLUMN `invalidated_at` TO `invalidate_time`,
  MODIFY COLUMN `template_section_snapshot` JSON NULL DEFAULT NULL COMMENT '旧方案采用的模板章节原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `source_snapshot` JSON NULL DEFAULT NULL COMMENT '旧方案来源事实原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `content_snapshot` JSON NULL DEFAULT NULL COMMENT '旧方案正文原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `file_fact_version` JSON NULL DEFAULT NULL COMMENT '旧文件三项事实水位原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_plan_union` CHECK ((((0 <> coalesce(((`origin_code` = _utf8mb4'LEGACY_FORWARD') and (`edit_mode_code` is null) and (`grade_code` is null) and (`assessment_id` is null) and (`assessment_version` is null) and (`checklist_id` is null) and (`checklist_version` is null) and (`configuration_revision_id` is null) and (`configuration_code` is null) and (`configuration_revision_no` is null) and (`configuration_revision_id` is null) and (`project_description` IS NULL AND `rollback_description` IS NULL AND `customer_contact_id` IS NULL AND `customer_contact_version` IS NULL) and (`file_artifact_id` is null) and (`file_version_no` is null) and (`file_reference_key` is null) and (`file_artifact_fact_version` IS NULL AND `file_reference_fact_version` IS NULL AND `file_availability_fact_version` IS NULL) and (`file_scope_version` is null) and (`file_sha256` is null) and (`ownership_confirmed_flag` is null) and (`status_code` is null) and (`current_marker` is null) and (`submit_user_id` is null) and (`submit_time` is null) and (`approval_instance_id` is null) and (`approval_version` is null) and (`source_plan_revision_id` is null) and (`revision_reason_code` is null) and (`invalidate_user_id` is null) and (`invalidate_time` is null) and (`invalidation_reason_code` is null) and (`legacy_plan_id` > 0) and (`legacy_status_raw` between 0 and 4) and (`legacy_source_version` >= 0) and (char_length(trim(`legacy_mapping_version`)) between 1 and 64)),false)) or (0 <> coalesce(((`origin_code` = _utf8mb4'NEW_PLATFORM') and (`grade_code` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C',_utf8mb4'D')) and (`assessment_id` > 0) and (`assessment_version` > 0) and (`configuration_revision_id` > 0) and (char_length(trim(`configuration_code`)) between 1 and 64) and (`configuration_revision_no` > 0) and (`configuration_revision_id` is not null) and (`legacy_plan_id` is null) and (`legacy_status_raw` is null) and (`legacy_source_version` is null) and (`legacy_mapping_version` is null) and (((`grade_code` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C')) and (`checklist_id` > 0) and (`checklist_version` > 0)) or ((`grade_code` = _utf8mb4'D') and (`checklist_id` is null) and (`checklist_version` is null))) and (((`edit_mode_code` = _utf8mb4'FULL_FILE_UPLOAD') and (`project_description` IS NULL AND `rollback_description` IS NULL AND `customer_contact_id` IS NULL AND `customer_contact_version` IS NULL) and (`file_artifact_id` > 0) and (`file_version_no` > 0) and (char_length(trim(`file_reference_key`)) between 1 and 128) and (`file_artifact_fact_version` IS NOT NULL AND `file_reference_fact_version` IS NOT NULL AND `file_availability_fact_version` IS NOT NULL AND `file_artifact_fact_version`>=0 AND `file_reference_fact_version`>=0 AND `file_availability_fact_version`>=0) and (`file_scope_version` >= 0) and regexp_like(`file_sha256`,_utf8mb4'^[0-9a-f]{64}$') and (`ownership_confirmed_flag` = 0x01)) or ((((`edit_mode_code` = _utf8mb4'ONLINE_TEMPLATE_STANDARD') and (`grade_code` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C'))) or ((`edit_mode_code` = _utf8mb4'ONLINE_TEMPLATE_SIMPLE_D') and (`grade_code` = _utf8mb4'D'))) and (`edit_mode_code` IN ('ONLINE_TEMPLATE_STANDARD','ONLINE_TEMPLATE_SIMPLE_D')) and (`file_artifact_id` is null) and (`file_version_no` is null) and (`file_reference_key` is null) and (`file_artifact_fact_version` IS NULL AND `file_reference_fact_version` IS NULL AND `file_availability_fact_version` IS NULL) and (`file_scope_version` is null) and (`file_sha256` is null) and (`ownership_confirmed_flag` is null))) and (((`status_code` = _utf8mb4'DRAFT') and (`current_marker` = 1) and (`submit_user_id` is null) and (`submit_time` is null) and (`approval_instance_id` is null) and (`approval_version` is null) and (`invalidate_user_id` is null) and (`invalidate_time` is null) and (`invalidation_reason_code` is null)) or ((`status_code` = _utf8mb4'SUBMITTED') and (`submit_user_id` > 0) and (`submit_time` is not null) and (`approval_instance_id` > 0) and (`approval_version` >= 0) and (`invalidate_user_id` is null) and (`invalidate_time` is null) and (`invalidation_reason_code` is null)) or ((`status_code` = _utf8mb4'INVALIDATED') and (`current_marker` is null) and (`submit_user_id` > 0) and (`submit_time` is not null) and (`approval_instance_id` > 0) and (`approval_version` >= 0) and (`invalidate_user_id` > 0) and (`invalidate_time` is not null) and (char_length(trim(`invalidation_reason_code`)) between 1 and 64)))),false)))) = TRUE);

ALTER TABLE `cut_plan_revision` ADD CONSTRAINT `fk_cut_plan_task` FOREIGN KEY (`task_id`) REFERENCES `cut_task` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `cut_cutover_support_arrangement`
  RENAME COLUMN `arrival_time` TO `arrive_time`;

ALTER TABLE `cut_approval_instance`
  DROP CHECK `chk_cut_approval_decision`,
  DROP CHECK `chk_cut_approval_lead_time`,
  RENAME COLUMN `decision_at` TO `decide_time`,
  RENAME COLUMN `lead_time_enabled` TO `lead_time_enabled_flag`,
  MODIFY COLUMN `source_snapshot` JSON NULL DEFAULT NULL COMMENT '旧审批提交来源事实原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `route_snapshot` JSON NULL DEFAULT NULL COMMENT '旧审批路由原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `lead_time_snapshot` JSON NULL DEFAULT NULL COMMENT '旧提前时间规则及计算事实原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_approval_decision` CHECK ((((0 <> coalesce(((`status_code` = _utf8mb4'APPROVED') and (`decide_time` is not null) and (`rejection_reason` is null)),false)) or (0 <> coalesce(((`status_code` = _utf8mb4'REJECTED') and (`decide_time` is not null) and (char_length(trim(`rejection_reason`)) between 1 and 1000)),false)) or (0 <> coalesce(((`status_code` in (_utf8mb4'PENDING',_utf8mb4'PAUSED_SOURCE_INVALIDATED')) and (`decide_time` is null) and (`rejection_reason` is null)),false)))) = TRUE),
  ADD CONSTRAINT `chk_cut_approval_lead_time` CHECK (((lead_time_enabled_flag=b'0' AND `lead_rule_version` IS NULL AND `lead_timezone_id` IS NULL AND `lead_plan_submit_time` IS NULL AND `lead_required_days` IS NULL AND `lead_actual_natural_days` IS NULL AND `lead_late_submission_flag` IS NULL) OR COALESCE((lead_time_enabled_flag=b'1' AND grade_code IN ('A','B') AND `lead_rule_version` IS NOT NULL AND `lead_timezone_id` IS NOT NULL AND `lead_plan_submit_time` IS NOT NULL AND `lead_required_days` IS NOT NULL AND `lead_actual_natural_days` IS NOT NULL AND `lead_late_submission_flag` IS NOT NULL AND source_schedule_time IS NOT NULL),FALSE)) = TRUE);

ALTER TABLE `cut_approval_node`
  DROP CHECK `chk_cut_approval_node_approver`,
  DROP CHECK `chk_cut_approval_node_decision`,
  RENAME COLUMN `decision_at` TO `decide_time`,
  MODIFY COLUMN `candidate_fact_snapshot` JSON NULL DEFAULT NULL COMMENT '旧原办理资格事实原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_approval_node_approver` CHECK ((COALESCE((
   (approval_mode_code='ROLE' AND candidate_role_id>0 AND candidate_group_key=CONCAT('ROLE:',candidate_role_id)
    AND original_approver_user_id IS NULL
    AND ((node_code='SECOND_LINE' AND candidate_role_code='probSupporter') OR (node_code='RND' AND candidate_role_code='probRD'))
    AND ((status_code IN ('WAITING','PENDING','CANCELLED') AND current_approver_user_id IS NULL)
      OR (status_code IN ('APPROVED','REJECTED') AND current_approver_user_id>0 AND decision_actor_user_id=current_approver_user_id)))
   OR (approval_mode_code='PERSONAL' AND candidate_role_id IS NULL AND candidate_role_code IS NULL AND candidate_group_key IS NULL
    AND ((original_approver_user_id IS NULL AND current_approver_user_id IS NULL)
       OR (original_approver_user_id>0 AND current_approver_user_id>0)))),FALSE)) = TRUE),
  ADD CONSTRAINT `chk_cut_approval_node_decision` CHECK ((((0 <> coalesce(((`status_code` in (_utf8mb4'APPROVED',_utf8mb4'REJECTED')) and (`decide_time` is not null) and (char_length(trim(`feedback`)) between 1 and 1000)),false)) or ((`status_code` in (_utf8mb4'WAITING',_utf8mb4'PENDING',_utf8mb4'CANCELLED')) and (`decide_time` is null) and (`feedback` is null)))) = TRUE);

ALTER TABLE `cut_approval_reassignment`
  DROP CHECK `chk_cut_approval_reassignment_values`,
  RENAME COLUMN `operated_by` TO `operate_user_id`,
  RENAME COLUMN `operated_at` TO `operate_time`,
  MODIFY COLUMN `candidate_fact_snapshot` JSON NULL DEFAULT NULL COMMENT '旧原办理资格事实原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_approval_reassignment_values` CHECK ((((`reassignment_no` > 0) and (`to_approver_user_id` > 0) and (`operate_user_id` > 0) and (char_length(trim(`reason`)) between 1 and 1000) and ((`from_approver_user_id` is null) or ((`from_approver_user_id` > 0) and (`from_approver_user_id` <> `to_approver_user_id`))))) = TRUE);

ALTER TABLE `cut_approval_notification`
  DROP CHECK `chk_cut_approval_notification_status`,
  RENAME COLUMN `next_retry_at` TO `next_retry_time`,
  RENAME COLUMN `sent_at` TO `send_time`,
  RENAME COLUMN `last_attempt_at` TO `last_attempt_time`,
  ADD CONSTRAINT `chk_cut_approval_notification_status` CHECK (((((`channel_code` = _utf8mb4'IN_PLATFORM') and (`provider_reference_id` is null) and (`last_attempt_time` is null) and (((`status_code` = _utf8mb4'PENDING') and (`message_id` is null) and (`next_retry_time` is null) and (`last_error_code` is null) and (`send_time` is null)) or (0 <> coalesce(((`status_code` = _utf8mb4'PENDING_RETRY') and (`message_id` is null) and (`next_retry_time` is not null) and (char_length(trim(`last_error_code`)) between 1 and 64) and (`send_time` is null)),false)) or (0 <> coalesce(((`status_code` = _utf8mb4'SENT') and (`message_id` > 0) and (`next_retry_time` is null) and (`last_error_code` is null) and (`send_time` is not null)),false)))) or ((`channel_code` in (_utf8mb4'SMS',_utf8mb4'EMAIL',_utf8mb4'DINGTALK')) and (`message_id` is null) and (`send_time` is null) and (((`status_code` = _utf8mb4'PENDING') and (`retry_count` = 0) and (`provider_reference_id` is null) and (`next_retry_time` is null) and (`last_error_code` is null) and (`last_attempt_time` is null)) or (0 <> coalesce(((`status_code` = _utf8mb4'PENDING_RETRY') and (`retry_count` > 0) and (`provider_reference_id` is null) and (`next_retry_time` is not null) and (`last_attempt_time` is not null) and (char_length(trim(`last_error_code`)) between 1 and 64)),false)) or (0 <> coalesce(((`status_code` = _utf8mb4'ACCEPTED') and (char_length(trim(`provider_reference_id`)) between 1 and 128) and (`next_retry_time` is null) and (`last_error_code` is null) and (`last_attempt_time` is not null)),false)) or ((`status_code` = _utf8mb4'DELIVERY_UNKNOWN') and (`next_retry_time` is null) and (`last_error_code` is null) and (`last_attempt_time` is not null)))))) = TRUE);

ALTER TABLE `cut_cutover_closure`
  DROP CHECK `chk_cut_closure_details`,
  DROP CHECK `chk_cut_closure_lifecycle`,
  DROP CHECK `chk_cut_closure_rollback`,
  RENAME COLUMN `pre_check_normal` TO `pre_check_normal_flag`,
  RENAME COLUMN `execution_normal` TO `execution_normal_flag`,
  RENAME COLUMN `test_normal` TO `test_normal_flag`,
  RENAME COLUMN `rollback_occurred` TO `rollback_occurred_flag`,
  RENAME COLUMN `rollback_successful` TO `rollback_successful_flag`,
  RENAME COLUMN `submitted_by` TO `submit_user_id`,
  RENAME COLUMN `submitted_at` TO `submit_time`,
  RENAME COLUMN `archived_at` TO `archive_time`,
  MODIFY COLUMN `device_scope_watermark` JSON NULL DEFAULT NULL COMMENT '旧设备范围水位原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_closure_details` CHECK (((((`pre_check_detail` is null) or (char_length(`pre_check_detail`) <= 4000)) and ((`execution_detail` is null) or (char_length(`execution_detail`) <= 4000)) and ((`test_detail` is null) or (char_length(`test_detail`) <= 4000)) and ((`legacy_items` is null) or (char_length(`legacy_items`) <= 4000)) and ((`pre_check_normal_flag` is null) or (`pre_check_normal_flag` = 0x01) or (0 <> coalesce((char_length(trim(`pre_check_detail`)) between 1 and 4000),false))) and ((`execution_normal_flag` is null) or (`execution_normal_flag` = 0x01) or (0 <> coalesce((char_length(trim(`execution_detail`)) between 1 and 4000),false))) and ((`test_normal_flag` is null) or (`test_normal_flag` = 0x01) or (0 <> coalesce((char_length(trim(`test_detail`)) between 1 and 4000),false))))) = TRUE),
  ADD CONSTRAINT `chk_cut_closure_lifecycle` CHECK (((((`status_code` = _utf8mb4'DRAFT') and (`final_result_code` is null) and (`result_ref` is null) and (`submit_user_id` is null) and (`submit_time` is null) and (`archive_time` is null)) or (0 <> coalesce(((`status_code` = _utf8mb4'SUBMITTED') and (`pre_check_normal_flag` is not null) and (`execution_normal_flag` is not null) and (`test_normal_flag` is not null) and (`rollback_occurred_flag` is not null) and (`final_result_code` in (_utf8mb4'SUCCESS',_utf8mb4'FAILED')) and (`submit_user_id` > 0) and (`submit_time` is not null) and (`archive_time` = `submit_time`) and (((`final_result_code` = _utf8mb4'SUCCESS') and (cast(`result_ref` as char charset binary) = cast(concat(_utf8mb4'CUTOVER_CLOSURE:',`id`,_utf8mb4':',`version`) as char charset binary))) or ((`final_result_code` = _utf8mb4'FAILED') and (`result_ref` is null)))),false)))) = TRUE),
  ADD CONSTRAINT `chk_cut_closure_rollback` CHECK (((((`rollback_occurred_flag` is null) and (`rollback_successful_flag` is null) and (`rollback_reason` is null)) or ((`rollback_occurred_flag` = 0x00) and (`rollback_successful_flag` is null) and (`rollback_reason` is null)) or (0 <> coalesce(((`rollback_occurred_flag` = 0x01) and (`rollback_successful_flag` is not null) and (char_length(trim(`rollback_reason`)) between 1 and 4000)),false)))) = TRUE);

ALTER TABLE `cut_cutover_closure_attachment`
  DROP CHECK `chk_cut_closure_attachment_values`,
  RENAME COLUMN `reference_key` TO `file_reference_key`,
  RENAME COLUMN `artifact_id` TO `file_artifact_id`,
  RENAME COLUMN `file_hash` TO `file_sha256`,
  MODIFY COLUMN `file_fact_version` JSON NULL DEFAULT NULL COMMENT '旧文件三项事实水位原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_closure_attachment_values` CHECK ((((`file_artifact_id` > 0) and (`file_version_no` > 0) and (`file_scope_version` >= 0) and (`version` >= 0) and (char_length(trim(`file_reference_key`)) between 1 and 128) and regexp_like(`file_sha256`,_ascii'^[0-9a-f]{64}$',_utf8mb4'c'))) = TRUE);

ALTER TABLE `cut_cutover_collection_evidence`
  DROP CHECK `chk_cut_collection_union`,
  DROP CHECK `chk_cut_collection_values`,
  RENAME COLUMN `occurred_at` TO `occur_time`,
  RENAME COLUMN `recorded_by` TO `record_user_id`,
  ADD CONSTRAINT `chk_cut_collection_union` CHECK (((((`evidence_type_code` in (_utf8mb4'DISPATCH_ACCEPTED',_utf8mb4'DISPATCH_FAILED')) and (`callback_event_id` is null) and (`result_ref` is null) and (`result_version` is null) and (`original_failed_collection_task_id` is null) and (`manual_attachment_id` is null) and (`record_user_id` > 0)) or (0 <> coalesce(((`evidence_type_code` in (_utf8mb4'CALLBACK_SUCCEEDED',_utf8mb4'CALLBACK_FAILED')) and (char_length(trim(`callback_event_id`)) between 1 and 128) and (char_length(trim(`result_ref`)) between 1 and 256) and (char_length(trim(`result_version`)) between 1 and 128) and (`original_failed_collection_task_id` is null) and (`manual_attachment_id` is null) and (`record_user_id` = 0)),false)) or (0 <> coalesce(((`evidence_type_code` = _utf8mb4'MANUAL_UPLOAD') and (`callback_event_id` is null) and (`result_ref` is null) and (`result_version` is null) and (cast(`collection_task_id` as char charset binary) = cast(`original_failed_collection_task_id` as char charset binary)) and (`manual_attachment_id` > 0) and (`record_user_id` > 0)),false)))) = TRUE),
  ADD CONSTRAINT `chk_cut_collection_values` CHECK ((((`project_id` > 0) and (`device_id` > 0) and (`record_user_id` >= 0) and (char_length(trim(`collection_task_id`)) between 1 and 128))) = TRUE);

ALTER TABLE `cut_collection_log`
  RENAME COLUMN `entry` TO `entry_code`,
  MODIFY COLUMN `result_snapshot` JSON NULL DEFAULT NULL COMMENT '旧平台采集回执原件；新业务写入NULL，禁止更改历史原值';

-- Rebuild renamed columns with their exact original foreign-key semantics.

ALTER TABLE `cut_spare_application_reference` DROP FOREIGN KEY `fk_cut_spare_application_task`;

ALTER TABLE `cut_spare_application_reference`
  DROP CHECK `chk_cut_spare_application_json`,
  DROP CHECK `chk_cut_spare_application_status`,
  DROP CHECK `chk_cut_spare_pending_binding`,
  DROP CHECK `chk_cut_spare_referenced_status`,
  RENAME COLUMN `cutover_task_id` TO `task_id`,
  RENAME COLUMN `integration_status` TO `integration_status_code`,
  RENAME COLUMN `last_attempt_at` TO `last_attempt_time`,
  MODIFY COLUMN `need_snapshot` JSON NULL DEFAULT NULL COMMENT '旧备件需求来源原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `request_context_snapshot` JSON NULL DEFAULT NULL COMMENT '旧备件请求上下文原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_spare_application_json` CHECK ((COALESCE((need_required_flag IS NOT NULL AND task_no IS NOT NULL AND source_task_version>=0 AND correlation_id IS NOT NULL),FALSE)) = TRUE),
  ADD CONSTRAINT `chk_cut_spare_application_status` CHECK (((`integration_status_code` in (_utf8mb4'REQUEST_PENDING',_utf8mb4'EXTERNAL_REFERENCED',_utf8mb4'RETRY_PENDING'))) = TRUE),
  ADD CONSTRAINT `chk_cut_spare_pending_binding` CHECK ((((`integration_status_code` <> _utf8mb4'REQUEST_PENDING') or (`external_application_no` is null))) = TRUE),
  ADD CONSTRAINT `chk_cut_spare_referenced_status` CHECK ((((`integration_status_code` <> _utf8mb4'EXTERNAL_REFERENCED') or ((`external_request_id` is not null) and (`external_application_no` is not null)))) = TRUE);

ALTER TABLE `cut_spare_application_reference` ADD CONSTRAINT `fk_cut_spare_application_task` FOREIGN KEY (`task_id`) REFERENCES `cut_task` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `cut_spare_status_revision`
  DROP CHECK `chk_cut_spare_status_snapshot`,
  DROP CHECK `chk_cut_spare_status_source`,
  DROP CHECK `chk_cut_spare_status_values`,
  RENAME COLUMN `source_type` TO `source_type_code`,
  RENAME COLUMN `observed_at` TO `observe_time`,
  RENAME COLUMN `external_occurred_at` TO `external_occur_time`,
  RENAME COLUMN `created_by` TO `create_user_id`,
  RENAME COLUMN `created_at` TO `create_time`,
  MODIFY COLUMN `status_snapshot` JSON NULL DEFAULT NULL COMMENT '旧外部备件状态载荷原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_spare_status_snapshot` CHECK ((source_payload_digest IS NULL OR REGEXP_LIKE(source_payload_digest,'^[0-9a-f]{64}$','c')) = TRUE),
  ADD CONSTRAINT `chk_cut_spare_status_source` CHECK (((`source_type_code` in (_utf8mb4'INITIATE_RESPONSE',_utf8mb4'CALLBACK',_utf8mb4'REFRESH'))) = TRUE),
  ADD CONSTRAINT `chk_cut_spare_status_values` CHECK ((((`status_version` > 0) and (`create_user_id` >= 0))) = TRUE);

-- Rebuild renamed columns with their exact original foreign-key semantics.

ALTER TABLE `cut_spare_manual_evidence` DROP FOREIGN KEY `fk_cut_spare_manual_task`;

ALTER TABLE `cut_spare_manual_evidence`
  DROP CHECK `chk_cut_spare_manual_file_fact`,
  DROP CHECK `chk_cut_spare_manual_values`,
  RENAME COLUMN `cutover_task_id` TO `task_id`,
  RENAME COLUMN `uploaded_by` TO `upload_user_id`,
  RENAME COLUMN `created_at` TO `create_time`,
  MODIFY COLUMN `file_fact_version` JSON NULL DEFAULT NULL COMMENT '旧文件三项事实水位原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_cut_spare_manual_file_fact` CHECK ((COALESCE((`file_artifact_fact_version` IS NOT NULL AND `file_reference_fact_version` IS NOT NULL AND `file_availability_fact_version` IS NOT NULL AND `file_artifact_fact_version`>=0 AND `file_reference_fact_version`>=0 AND `file_availability_fact_version`>=0),FALSE)) = TRUE),
  ADD CONSTRAINT `chk_cut_spare_manual_values` CHECK ((((`file_artifact_id` > 0) and (`file_version_no` > 0) and (`file_scope_version` >= 0) and (`upload_user_id` > 0))) = TRUE);

ALTER TABLE `cut_spare_manual_evidence` ADD CONSTRAINT `fk_cut_spare_manual_task` FOREIGN KEY (`task_id`) REFERENCES `cut_task` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION;

ALTER TABLE `imp_cutover_readiness_snapshot`
  DROP CHECK `chk_imp_cutover_readiness_decision`,
  RENAME COLUMN `decision` TO `readiness_decision_code`,
  RENAME COLUMN `evaluated_by` TO `evaluate_user_id`,
  MODIFY COLUMN `context_json` JSON NULL DEFAULT NULL COMMENT '旧IMP就绪来源上下文原件；新业务写入NULL，禁止更改历史原值',
  MODIFY COLUMN `unmet_codes_json` JSON NULL DEFAULT NULL COMMENT '旧IMP未满足原因集合原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_imp_cutover_readiness_decision` CHECK (((`readiness_decision_code` in (_utf8mb4'READY',_utf8mb4'NOT_READY'))) = TRUE);

ALTER TABLE `ast_device_cutover_result`
  DROP CHECK `chk_ast_cutover_refresh`,
  RENAME COLUMN `serial_number` TO `sn`,
  RENAME COLUMN `cutover_at` TO `cutover_time`,
  RENAME COLUMN `refresh_status` TO `refresh_status_code`,
  MODIFY COLUMN `result_snapshot` JSON NULL DEFAULT NULL COMMENT '旧CUT成功结果原件；新业务写入NULL，禁止更改历史原值',
  ADD CONSTRAINT `chk_ast_cutover_refresh` CHECK (((`refresh_status_code` in (_utf8mb4'REFRESHED',_utf8mb4'HISTORICAL_SCOPE_CHANGED'))) = TRUE);

ALTER TABLE `cut_cutover_closure_attachment` ADD CONSTRAINT `chk_cut_structured_file_58` CHECK (COALESCE((`file_artifact_fact_version` IS NOT NULL AND `file_reference_fact_version` IS NOT NULL AND `file_availability_fact_version` IS NOT NULL AND `file_artifact_fact_version`>=0 AND `file_reference_fact_version`>=0 AND `file_availability_fact_version`>=0),FALSE) = TRUE);

ALTER TABLE `cut_checklist_result_file` ADD CONSTRAINT `chk_cut_structured_file_23` CHECK (COALESCE((`file_artifact_fact_version` IS NOT NULL AND `file_reference_fact_version` IS NOT NULL AND `file_availability_fact_version` IS NOT NULL AND `file_artifact_fact_version`>=0 AND `file_reference_fact_version`>=0 AND `file_availability_fact_version`>=0),FALSE) = TRUE);

ALTER TABLE `cut_plan_file` ADD CONSTRAINT `chk_cut_structured_file_47` CHECK (COALESCE((`file_artifact_fact_version` IS NOT NULL AND `file_reference_fact_version` IS NOT NULL AND `file_availability_fact_version` IS NOT NULL AND `file_artifact_fact_version`>=0 AND `file_reference_fact_version`>=0 AND `file_availability_fact_version`>=0),FALSE) = TRUE);

ALTER TABLE `cut_checklist_field_option` ADD CONSTRAINT `chk_cut_structured_scalar_9` CHECK ((value_type_code='STRING' AND `value_text` IS NOT NULL AND `value_number` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='NUMBER' AND `value_number` IS NOT NULL AND `value_text` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='BOOLEAN' AND `value_boolean` IS NOT NULL AND `value_text` IS NULL AND `value_number` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='DATETIME' AND `value_datetime` IS NOT NULL AND `value_text` IS NULL AND `value_number` IS NULL AND `value_boolean` IS NULL) OR (value_type_code='NULL' AND `value_text` IS NULL AND `value_number` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL));

ALTER TABLE `cut_checklist_binding_condition` ADD CONSTRAINT `chk_cut_structured_scalar_10` CHECK ((value_type_code='STRING' AND `value_text` IS NOT NULL AND `value_number` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='NUMBER' AND `value_number` IS NOT NULL AND `value_text` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='BOOLEAN' AND `value_boolean` IS NOT NULL AND `value_text` IS NULL AND `value_number` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='DATETIME' AND `value_datetime` IS NOT NULL AND `value_text` IS NULL AND `value_number` IS NULL AND `value_boolean` IS NULL) OR (value_type_code='NULL' AND `value_text` IS NULL AND `value_number` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL));

ALTER TABLE `cut_checklist_input_dimension` ADD CONSTRAINT `chk_cut_structured_scalar_20` CHECK ((value_type_code='STRING' AND `value_text` IS NOT NULL AND `value_number` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='NUMBER' AND `value_number` IS NOT NULL AND `value_text` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='BOOLEAN' AND `value_boolean` IS NOT NULL AND `value_text` IS NULL AND `value_number` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='DATETIME' AND `value_datetime` IS NOT NULL AND `value_text` IS NULL AND `value_number` IS NULL AND `value_boolean` IS NULL) OR (value_type_code='NULL' AND `value_text` IS NULL AND `value_number` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL));

ALTER TABLE `cut_checklist_external_query_parameter` ADD CONSTRAINT `chk_cut_structured_scalar_25` CHECK ((value_type_code='STRING' AND `value_text` IS NOT NULL AND `value_number` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='NUMBER' AND `value_number` IS NOT NULL AND `value_text` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='BOOLEAN' AND `value_boolean` IS NOT NULL AND `value_text` IS NULL AND `value_number` IS NULL AND `value_datetime` IS NULL) OR (value_type_code='DATETIME' AND `value_datetime` IS NOT NULL AND `value_text` IS NULL AND `value_number` IS NULL AND `value_boolean` IS NULL) OR (value_type_code='NULL' AND `value_text` IS NULL AND `value_number` IS NULL AND `value_boolean` IS NULL AND `value_datetime` IS NULL));

DELIMITER $$

CREATE TRIGGER `trg_cut_structured_1_i` BEFORE INSERT ON `cut_cutover_configuration_revision` FOR EACH ROW
BEGIN
  IF NEW.`dictionary_snapshot` IS NOT NULL OR NEW.`dimension_definition_snapshot` IS NOT NULL OR NEW.`plan_template_section_snapshot` IS NOT NULL OR NEW.`validation_result_snapshot` IS NOT NULL OR NEW.`navigation_rule_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_1_u` BEFORE UPDATE ON `cut_cutover_configuration_revision` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`dictionary_snapshot` AS BINARY) <=> CAST(OLD.`dictionary_snapshot` AS BINARY)) OR NOT(CAST(NEW.`dimension_definition_snapshot` AS BINARY) <=> CAST(OLD.`dimension_definition_snapshot` AS BINARY)) OR NOT(CAST(NEW.`plan_template_section_snapshot` AS BINARY) <=> CAST(OLD.`plan_template_section_snapshot` AS BINARY)) OR NOT(CAST(NEW.`validation_result_snapshot` AS BINARY) <=> CAST(OLD.`validation_result_snapshot` AS BINARY)) OR NOT(CAST(NEW.`navigation_rule_snapshot` AS BINARY) <=> CAST(OLD.`navigation_rule_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_configuration_revision';
  END IF;
  IF (OLD.status_code<>'DRAFT') AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`configuration_code` AS BINARY) <=> CAST(OLD.`configuration_code` AS BINARY)) OR NOT(CAST(NEW.`configuration_name` AS BINARY) <=> CAST(OLD.`configuration_name` AS BINARY)) OR NOT(CAST(NEW.`revision_no` AS BINARY) <=> CAST(OLD.`revision_no` AS BINARY)) OR NOT(CAST(NEW.`effective_start_time` AS BINARY) <=> CAST(OLD.`effective_start_time` AS BINARY)) OR NOT(CAST(NEW.`dictionary_snapshot` AS BINARY) <=> CAST(OLD.`dictionary_snapshot` AS BINARY)) OR NOT(CAST(NEW.`dimension_definition_snapshot` AS BINARY) <=> CAST(OLD.`dimension_definition_snapshot` AS BINARY)) OR NOT(CAST(NEW.`plan_template_section_snapshot` AS BINARY) <=> CAST(OLD.`plan_template_section_snapshot` AS BINARY)) OR NOT(CAST(NEW.`validation_result_snapshot` AS BINARY) <=> CAST(OLD.`validation_result_snapshot` AS BINARY)) OR NOT(CAST(NEW.`change_summary` AS BINARY) <=> CAST(OLD.`change_summary` AS BINARY)) OR NOT(CAST(NEW.`publish_user_id` AS BINARY) <=> CAST(OLD.`publish_user_id` AS BINARY)) OR NOT(CAST(NEW.`publish_time` AS BINARY) <=> CAST(OLD.`publish_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`navigation_rule_snapshot` AS BINARY) <=> CAST(OLD.`navigation_rule_snapshot` AS BINARY)) OR NOT(CAST(NEW.`navigation_target_code` AS BINARY) <=> CAST(OLD.`navigation_target_code` AS BINARY)) OR NOT(CAST(NEW.`validation_valid_flag` AS BINARY) <=> CAST(OLD.`validation_valid_flag` AS BINARY)) OR NOT(CAST(NEW.`current_validation_no` AS BINARY) <=> CAST(OLD.`current_validation_no` AS BINARY)) OR NOT(CAST(NEW.`validate_time` AS BINARY) <=> CAST(OLD.`validate_time` AS BINARY)) OR NOT(CAST(NEW.`validate_user_id` AS BINARY) <=> CAST(OLD.`validate_user_id` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_configuration_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_1_d` BEFORE DELETE ON `cut_cutover_configuration_revision` FOR EACH ROW
BEGIN
  IF OLD.status_code<>'DRAFT' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_configuration_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_2_i` BEFORE INSERT ON `cut_cutover_checklist_item_definition_revision` FOR EACH ROW
BEGIN
  IF NEW.`interface_schema` IS NOT NULL OR NEW.`external_source_config` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_item_definition_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_2_u` BEFORE UPDATE ON `cut_cutover_checklist_item_definition_revision` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`interface_schema` AS BINARY) <=> CAST(OLD.`interface_schema` AS BINARY)) OR NOT(CAST(NEW.`external_source_config` AS BINARY) <=> CAST(OLD.`external_source_config` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item_definition_revision';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_item_definition_revision';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_item_definition_revision';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item_definition_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_2_d` BEFORE DELETE ON `cut_cutover_checklist_item_definition_revision` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_item_definition_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_3_i` BEFORE INSERT ON `cut_cutover_checklist_binding_rule_revision` FOR EACH ROW
BEGIN
  IF NEW.`dimension_condition_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_binding_rule_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_3_u` BEFORE UPDATE ON `cut_cutover_checklist_binding_rule_revision` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`dimension_condition_snapshot` AS BINARY) <=> CAST(OLD.`dimension_condition_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_binding_rule_revision';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_binding_rule_revision';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_binding_rule_revision';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_binding_rule_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_3_d` BEFORE DELETE ON `cut_cutover_checklist_binding_rule_revision` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_binding_rule_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_4_i` BEFORE INSERT ON `cut_configuration_dimension` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_dimension';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_4_u` BEFORE UPDATE ON `cut_configuration_dimension` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_dimension';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_dimension';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_configuration_dimension';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_4_d` BEFORE DELETE ON `cut_configuration_dimension` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_dimension';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_5_i` BEFORE INSERT ON `cut_configuration_dictionary_value` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_dictionary_value';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_5_u` BEFORE UPDATE ON `cut_configuration_dictionary_value` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_dictionary_value';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_dictionary_value';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_configuration_dictionary_value';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_5_d` BEFORE DELETE ON `cut_configuration_dictionary_value` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_dictionary_value';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_6_i` BEFORE INSERT ON `cut_configuration_plan_section` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_plan_section';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_6_u` BEFORE UPDATE ON `cut_configuration_plan_section` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_plan_section';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_plan_section';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_configuration_plan_section';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_6_d` BEFORE DELETE ON `cut_configuration_plan_section` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_configuration_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.configuration_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_plan_section';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_7_i` BEFORE INSERT ON `cut_configuration_plan_section_scope` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_configuration_plan_section s JOIN cut_cutover_configuration_revision p ON p.tenant_id=s.tenant_id AND p.id=s.configuration_revision_id WHERE s.tenant_id=NEW.tenant_id AND s.id=NEW.plan_section_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_plan_section_scope';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_7_u` BEFORE UPDATE ON `cut_configuration_plan_section_scope` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_configuration_plan_section s JOIN cut_cutover_configuration_revision p ON p.tenant_id=s.tenant_id AND p.id=s.configuration_revision_id WHERE s.tenant_id=OLD.tenant_id AND s.id=OLD.plan_section_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_plan_section_scope';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_configuration_plan_section s JOIN cut_cutover_configuration_revision p ON p.tenant_id=s.tenant_id AND p.id=s.configuration_revision_id WHERE s.tenant_id=NEW.tenant_id AND s.id=NEW.plan_section_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_plan_section_scope';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_configuration_plan_section_scope';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_7_d` BEFORE DELETE ON `cut_configuration_plan_section_scope` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_configuration_plan_section s JOIN cut_cutover_configuration_revision p ON p.tenant_id=s.tenant_id AND p.id=s.configuration_revision_id WHERE s.tenant_id=OLD.tenant_id AND s.id=OLD.plan_section_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_configuration_plan_section_scope';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_8_u` BEFORE UPDATE ON `cut_configuration_validation_issue` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`configuration_revision_id` AS BINARY) <=> CAST(OLD.`configuration_revision_id` AS BINARY)) OR NOT(CAST(NEW.`validation_no` AS BINARY) <=> CAST(OLD.`validation_no` AS BINARY)) OR NOT(CAST(NEW.`issue_no` AS BINARY) <=> CAST(OLD.`issue_no` AS BINARY)) OR NOT(CAST(NEW.`location` AS BINARY) <=> CAST(OLD.`location` AS BINARY)) OR NOT(CAST(NEW.`message` AS BINARY) <=> CAST(OLD.`message` AS BINARY)) OR NOT(CAST(NEW.`validate_time` AS BINARY) <=> CAST(OLD.`validate_time` AS BINARY)) OR NOT(CAST(NEW.`validate_user_id` AS BINARY) <=> CAST(OLD.`validate_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_configuration_validation_issue';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_8_d` BEFORE DELETE ON `cut_configuration_validation_issue` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_configuration_validation_issue';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_9_i` BEFORE INSERT ON `cut_checklist_field_definition` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item_definition_revision d
       JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id
       WHERE d.tenant_id=NEW.tenant_id AND d.id=NEW.item_definition_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0')
    AND NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item i
       JOIN cut_cutover_checklist c ON c.tenant_id=i.tenant_id AND c.id=i.checklist_id
       WHERE i.tenant_id=NEW.tenant_id AND i.id=NEW.custom_checklist_item_id AND c.status_code='DRAFT' AND c.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: field definition';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_9_u` BEFORE UPDATE ON `cut_checklist_field_definition` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item_definition_revision d
       JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id
       WHERE d.tenant_id=OLD.tenant_id AND d.id=OLD.item_definition_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0')
    AND NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item i
       JOIN cut_cutover_checklist c ON c.tenant_id=i.tenant_id AND c.id=i.checklist_id
       WHERE i.tenant_id=OLD.tenant_id AND i.id=OLD.custom_checklist_item_id AND c.status_code='DRAFT' AND c.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: field definition';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item_definition_revision d
       JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id
       WHERE d.tenant_id=NEW.tenant_id AND d.id=NEW.item_definition_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0')
    AND NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item i
       JOIN cut_cutover_checklist c ON c.tenant_id=i.tenant_id AND c.id=i.checklist_id
       WHERE i.tenant_id=NEW.tenant_id AND i.id=NEW.custom_checklist_item_id AND c.status_code='DRAFT' AND c.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: field definition';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_9_d` BEFORE DELETE ON `cut_checklist_field_definition` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item_definition_revision d
       JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id
       WHERE d.tenant_id=OLD.tenant_id AND d.id=OLD.item_definition_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0')
    AND NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item i
       JOIN cut_cutover_checklist c ON c.tenant_id=i.tenant_id AND c.id=i.checklist_id
       WHERE i.tenant_id=OLD.tenant_id AND i.id=OLD.custom_checklist_item_id AND c.status_code='DRAFT' AND c.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: field definition';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_10_i` BEFORE INSERT ON `cut_checklist_field_option` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_checklist_field_definition f
 LEFT JOIN cut_cutover_checklist_item_definition_revision d ON d.tenant_id=f.tenant_id AND d.id=f.item_definition_revision_id
 LEFT JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id
 LEFT JOIN cut_cutover_checklist_item i ON i.tenant_id=f.tenant_id AND i.id=f.custom_checklist_item_id
 LEFT JOIN cut_cutover_checklist c ON c.tenant_id=i.tenant_id AND c.id=i.checklist_id WHERE f.tenant_id=NEW.tenant_id AND f.id=NEW.field_definition_id AND ((p.id IS NOT NULL AND p.status_code='DRAFT' AND p.deleted=b'0') OR (c.id IS NOT NULL AND c.status_code='DRAFT' AND c.deleted=b'0'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_field_option';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_10_u` BEFORE UPDATE ON `cut_checklist_field_option` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_checklist_field_definition f
 LEFT JOIN cut_cutover_checklist_item_definition_revision d ON d.tenant_id=f.tenant_id AND d.id=f.item_definition_revision_id
 LEFT JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id
 LEFT JOIN cut_cutover_checklist_item i ON i.tenant_id=f.tenant_id AND i.id=f.custom_checklist_item_id
 LEFT JOIN cut_cutover_checklist c ON c.tenant_id=i.tenant_id AND c.id=i.checklist_id WHERE f.tenant_id=OLD.tenant_id AND f.id=OLD.field_definition_id AND ((p.id IS NOT NULL AND p.status_code='DRAFT' AND p.deleted=b'0') OR (c.id IS NOT NULL AND c.status_code='DRAFT' AND c.deleted=b'0'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_field_option';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_checklist_field_definition f
 LEFT JOIN cut_cutover_checklist_item_definition_revision d ON d.tenant_id=f.tenant_id AND d.id=f.item_definition_revision_id
 LEFT JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id
 LEFT JOIN cut_cutover_checklist_item i ON i.tenant_id=f.tenant_id AND i.id=f.custom_checklist_item_id
 LEFT JOIN cut_cutover_checklist c ON c.tenant_id=i.tenant_id AND c.id=i.checklist_id WHERE f.tenant_id=NEW.tenant_id AND f.id=NEW.field_definition_id AND ((p.id IS NOT NULL AND p.status_code='DRAFT' AND p.deleted=b'0') OR (c.id IS NOT NULL AND c.status_code='DRAFT' AND c.deleted=b'0'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_field_option';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_field_option';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_10_d` BEFORE DELETE ON `cut_checklist_field_option` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_checklist_field_definition f
 LEFT JOIN cut_cutover_checklist_item_definition_revision d ON d.tenant_id=f.tenant_id AND d.id=f.item_definition_revision_id
 LEFT JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id
 LEFT JOIN cut_cutover_checklist_item i ON i.tenant_id=f.tenant_id AND i.id=f.custom_checklist_item_id
 LEFT JOIN cut_cutover_checklist c ON c.tenant_id=i.tenant_id AND c.id=i.checklist_id WHERE f.tenant_id=OLD.tenant_id AND f.id=OLD.field_definition_id AND ((p.id IS NOT NULL AND p.status_code='DRAFT' AND p.deleted=b'0') OR (c.id IS NOT NULL AND c.status_code='DRAFT' AND c.deleted=b'0'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_field_option';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_11_i` BEFORE INSERT ON `cut_checklist_binding_condition` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_binding_rule_revision d JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id WHERE d.tenant_id=NEW.tenant_id AND d.id=NEW.binding_rule_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_binding_condition';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_11_u` BEFORE UPDATE ON `cut_checklist_binding_condition` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_binding_rule_revision d JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id WHERE d.tenant_id=OLD.tenant_id AND d.id=OLD.binding_rule_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_binding_condition';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_binding_rule_revision d JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id WHERE d.tenant_id=NEW.tenant_id AND d.id=NEW.binding_rule_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_binding_condition';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_binding_condition';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_11_d` BEFORE DELETE ON `cut_checklist_binding_condition` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_binding_rule_revision d JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id WHERE d.tenant_id=OLD.tenant_id AND d.id=OLD.binding_rule_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_binding_condition';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_12_i` BEFORE INSERT ON `cut_checklist_external_mapping` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item_definition_revision d JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id WHERE d.tenant_id=NEW.tenant_id AND d.id=NEW.item_definition_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_external_mapping';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_12_u` BEFORE UPDATE ON `cut_checklist_external_mapping` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item_definition_revision d JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id WHERE d.tenant_id=OLD.tenant_id AND d.id=OLD.item_definition_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_external_mapping';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item_definition_revision d JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id WHERE d.tenant_id=NEW.tenant_id AND d.id=NEW.item_definition_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_external_mapping';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_external_mapping';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_12_d` BEFORE DELETE ON `cut_checklist_external_mapping` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist_item_definition_revision d JOIN cut_cutover_configuration_revision p ON p.tenant_id=d.tenant_id AND p.id=d.configuration_revision_id WHERE d.tenant_id=OLD.tenant_id AND d.id=OLD.item_definition_revision_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_external_mapping';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_13_i` BEFORE INSERT ON `cut_task` FOR EACH ROW
BEGIN
  IF NEW.`project_context_snapshot` IS NOT NULL OR NEW.`device_scope_watermark` IS NOT NULL OR NEW.`customer_context_snapshot` IS NOT NULL OR NEW.`readiness_context_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_13_u` BEFORE UPDATE ON `cut_task` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`project_context_snapshot` AS BINARY) <=> CAST(OLD.`project_context_snapshot` AS BINARY)) OR NOT(CAST(NEW.`device_scope_watermark` AS BINARY) <=> CAST(OLD.`device_scope_watermark` AS BINARY)) OR NOT(CAST(NEW.`customer_context_snapshot` AS BINARY) <=> CAST(OLD.`customer_context_snapshot` AS BINARY)) OR NOT(CAST(NEW.`readiness_context_snapshot` AS BINARY) <=> CAST(OLD.`readiness_context_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_task';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_14_u` BEFORE UPDATE ON `cut_task_device_scope` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`project_id` AS BINARY) <=> CAST(OLD.`project_id` AS BINARY)) OR NOT(CAST(NEW.`device_id` AS BINARY) <=> CAST(OLD.`device_id` AS BINARY)) OR NOT(CAST(NEW.`sn` AS BINARY) <=> CAST(OLD.`sn` AS BINARY)) OR NOT(CAST(NEW.`project_assignment_version` AS BINARY) <=> CAST(OLD.`project_assignment_version` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`device_type_code` AS BINARY) <=> CAST(OLD.`device_type_code` AS BINARY)) OR NOT(CAST(NEW.`device_type_source_version` AS BINARY) <=> CAST(OLD.`device_type_source_version` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_task_device_scope';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_14_d` BEFORE DELETE ON `cut_task_device_scope` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_task_device_scope';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_15_u` BEFORE UPDATE ON `cut_task_stage_history` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`sequence_no` AS BINARY) <=> CAST(OLD.`sequence_no` AS BINARY)) OR NOT(CAST(NEW.`from_stage_code` AS BINARY) <=> CAST(OLD.`from_stage_code` AS BINARY)) OR NOT(CAST(NEW.`to_stage_code` AS BINARY) <=> CAST(OLD.`to_stage_code` AS BINARY)) OR NOT(CAST(NEW.`from_status_code` AS BINARY) <=> CAST(OLD.`from_status_code` AS BINARY)) OR NOT(CAST(NEW.`to_status_code` AS BINARY) <=> CAST(OLD.`to_status_code` AS BINARY)) OR NOT(CAST(NEW.`trigger_type` AS BINARY) <=> CAST(OLD.`trigger_type` AS BINARY)) OR NOT(CAST(NEW.`trigger_reference_id` AS BINARY) <=> CAST(OLD.`trigger_reference_id` AS BINARY)) OR NOT(CAST(NEW.`actor_user_id` AS BINARY) <=> CAST(OLD.`actor_user_id` AS BINARY)) OR NOT(CAST(NEW.`correlation_id` AS BINARY) <=> CAST(OLD.`correlation_id` AS BINARY)) OR NOT(CAST(NEW.`occur_time` AS BINARY) <=> CAST(OLD.`occur_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_task_stage_history';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_15_d` BEFORE DELETE ON `cut_task_stage_history` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_task_stage_history';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_16_i` BEFORE INSERT ON `cut_assessment` FOR EACH ROW
BEGIN
  IF NEW.`answer_snapshot` IS NOT NULL OR NEW.`context_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_16_u` BEFORE UPDATE ON `cut_assessment` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`answer_snapshot` AS BINARY) <=> CAST(OLD.`answer_snapshot` AS BINARY)) OR NOT(CAST(NEW.`context_snapshot` AS BINARY) <=> CAST(OLD.`context_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_assessment';
  END IF;
  IF (OLD.assessment_status_code<>'DRAFT') AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`assessment_version` AS BINARY) <=> CAST(OLD.`assessment_version` AS BINARY)) OR NOT(CAST(NEW.`questionnaire_template_code` AS BINARY) <=> CAST(OLD.`questionnaire_template_code` AS BINARY)) OR NOT(CAST(NEW.`questionnaire_template_version` AS BINARY) <=> CAST(OLD.`questionnaire_template_version` AS BINARY)) OR NOT(CAST(NEW.`answer_snapshot` AS BINARY) <=> CAST(OLD.`answer_snapshot` AS BINARY)) OR NOT(CAST(NEW.`context_snapshot` AS BINARY) <=> CAST(OLD.`context_snapshot` AS BINARY)) OR NOT(CAST(NEW.`manual_grade_code` AS BINARY) <=> CAST(OLD.`manual_grade_code` AS BINARY)) OR NOT(CAST(NEW.`simple_flow_flag` AS BINARY) <=> CAST(OLD.`simple_flow_flag` AS BINARY)) OR NOT(CAST(NEW.`submit_user_id` AS BINARY) <=> CAST(OLD.`submit_user_id` AS BINARY)) OR NOT(CAST(NEW.`submit_time` AS BINARY) <=> CAST(OLD.`submit_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`business_importance_level` AS BINARY) <=> CAST(OLD.`business_importance_level` AS BINARY)) OR NOT(CAST(NEW.`operation_complexity_level` AS BINARY) <=> CAST(OLD.`operation_complexity_level` AS BINARY)) OR NOT(CAST(NEW.`hidden_risk_level` AS BINARY) <=> CAST(OLD.`hidden_risk_level` AS BINARY)) OR NOT(CAST(NEW.`spare_part_applied_flag` AS BINARY) <=> CAST(OLD.`spare_part_applied_flag` AS BINARY)) OR NOT(CAST(NEW.`intake_source_type_code` AS BINARY) <=> CAST(OLD.`intake_source_type_code` AS BINARY)) OR NOT(CAST(NEW.`implementation_readiness_snapshot_id` AS BINARY) <=> CAST(OLD.`implementation_readiness_snapshot_id` AS BINARY)) OR NOT(CAST(NEW.`implementation_readiness_snapshot_version` AS BINARY) <=> CAST(OLD.`implementation_readiness_snapshot_version` AS BINARY)) OR NOT(CAST(NEW.`readiness_decision_code` AS BINARY) <=> CAST(OLD.`readiness_decision_code` AS BINARY)) OR NOT(CAST(NEW.`project_id` AS BINARY) <=> CAST(OLD.`project_id` AS BINARY)) OR NOT(CAST(NEW.`project_version` AS BINARY) <=> CAST(OLD.`project_version` AS BINARY)) OR NOT(CAST(NEW.`project_code` AS BINARY) <=> CAST(OLD.`project_code` AS BINARY)) OR NOT(CAST(NEW.`project_name` AS BINARY) <=> CAST(OLD.`project_name` AS BINARY)) OR NOT(CAST(NEW.`customer_id` AS BINARY) <=> CAST(OLD.`customer_id` AS BINARY)) OR NOT(CAST(NEW.`customer_code` AS BINARY) <=> CAST(OLD.`customer_code` AS BINARY)) OR NOT(CAST(NEW.`customer_name` AS BINARY) <=> CAST(OLD.`customer_name` AS BINARY)) OR NOT(CAST(NEW.`department_id` AS BINARY) <=> CAST(OLD.`department_id` AS BINARY)) OR NOT(CAST(NEW.`department_code` AS BINARY) <=> CAST(OLD.`department_code` AS BINARY)) OR NOT(CAST(NEW.`department_name` AS BINARY) <=> CAST(OLD.`department_name` AS BINARY)) OR NOT(CAST(NEW.`project_scope_version` AS BINARY) <=> CAST(OLD.`project_scope_version` AS BINARY)) OR NOT(CAST(NEW.`customer_fact_status_code` AS BINARY) <=> CAST(OLD.`customer_fact_status_code` AS BINARY)) OR NOT(CAST(NEW.`service_level_revision_id` AS BINARY) <=> CAST(OLD.`service_level_revision_id` AS BINARY)) OR NOT(CAST(NEW.`service_level_code` AS BINARY) <=> CAST(OLD.`service_level_code` AS BINARY)) OR NOT(CAST(NEW.`customer_fact_version` AS BINARY) <=> CAST(OLD.`customer_fact_version` AS BINARY)) OR NOT(CAST(NEW.`service_level_effective_start_time` AS BINARY) <=> CAST(OLD.`service_level_effective_start_time` AS BINARY)) OR NOT(CAST(NEW.`service_level_effective_end_time` AS BINARY) <=> CAST(OLD.`service_level_effective_end_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_assessment';
  END IF;
  IF NEW.assessment_status_code='SUBMITTED' AND OLD.assessment_status_code='DRAFT'
 AND NOT EXISTS(SELECT 1 FROM cut_assessment_device d WHERE d.tenant_id=NEW.tenant_id AND d.assessment_id=NEW.id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_ASSESSMENT_REQUIRES_FROZEN_DEVICES';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_16_d` BEFORE DELETE ON `cut_assessment` FOR EACH ROW
BEGIN
  IF OLD.assessment_status_code<>'DRAFT' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_assessment';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_17_i` BEFORE INSERT ON `cut_assessment_device` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_assessment p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.assessment_id AND p.assessment_status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_assessment_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_17_u` BEFORE UPDATE ON `cut_assessment_device` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_assessment p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.assessment_id AND p.assessment_status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_assessment_device';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_assessment p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.assessment_id AND p.assessment_status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_assessment_device';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_assessment_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_17_d` BEFORE DELETE ON `cut_assessment_device` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_assessment p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.assessment_id AND p.assessment_status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_assessment_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_18_i` BEFORE INSERT ON `cut_cutover_checklist` FOR EACH ROW
BEGIN
  IF NEW.`input_snapshot` IS NOT NULL OR NEW.`config_revision_snapshot` IS NOT NULL OR NEW.`match_trace` IS NOT NULL OR NEW.`config_gap_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_18_u` BEFORE UPDATE ON `cut_cutover_checklist` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`input_snapshot` AS BINARY) <=> CAST(OLD.`input_snapshot` AS BINARY)) OR NOT(CAST(NEW.`config_revision_snapshot` AS BINARY) <=> CAST(OLD.`config_revision_snapshot` AS BINARY)) OR NOT(CAST(NEW.`match_trace` AS BINARY) <=> CAST(OLD.`match_trace` AS BINARY)) OR NOT(CAST(NEW.`config_gap_snapshot` AS BINARY) <=> CAST(OLD.`config_gap_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist';
  END IF;
  IF (OLD.status_code<>'DRAFT') AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`assessment_id` AS BINARY) <=> CAST(OLD.`assessment_id` AS BINARY)) OR NOT(CAST(NEW.`assessment_version` AS BINARY) <=> CAST(OLD.`assessment_version` AS BINARY)) OR NOT(CAST(NEW.`checklist_version` AS BINARY) <=> CAST(OLD.`checklist_version` AS BINARY)) OR NOT(CAST(NEW.`input_snapshot` AS BINARY) <=> CAST(OLD.`input_snapshot` AS BINARY)) OR NOT(CAST(NEW.`input_snapshot_hash` AS BINARY) <=> CAST(OLD.`input_snapshot_hash` AS BINARY)) OR NOT(CAST(NEW.`config_revision_snapshot` AS BINARY) <=> CAST(OLD.`config_revision_snapshot` AS BINARY)) OR NOT(CAST(NEW.`match_trace` AS BINARY) <=> CAST(OLD.`match_trace` AS BINARY)) OR NOT(CAST(NEW.`config_gap_snapshot` AS BINARY) <=> CAST(OLD.`config_gap_snapshot` AS BINARY)) OR NOT(CAST(NEW.`submit_user_id` AS BINARY) <=> CAST(OLD.`submit_user_id` AS BINARY)) OR NOT(CAST(NEW.`submit_time` AS BINARY) <=> CAST(OLD.`submit_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`source_task_version` AS BINARY) <=> CAST(OLD.`source_task_version` AS BINARY)) OR NOT(CAST(NEW.`project_scope_version` AS BINARY) <=> CAST(OLD.`project_scope_version` AS BINARY)) OR NOT(CAST(NEW.`configuration_revision_id` AS BINARY) <=> CAST(OLD.`configuration_revision_id` AS BINARY)) OR NOT(CAST(NEW.`configuration_code` AS BINARY) <=> CAST(OLD.`configuration_code` AS BINARY)) OR NOT(CAST(NEW.`configuration_revision_no` AS BINARY) <=> CAST(OLD.`configuration_revision_no` AS BINARY)) OR NOT(CAST(NEW.`config_gap_reason_code` AS BINARY) <=> CAST(OLD.`config_gap_reason_code` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist';
  END IF;
  IF NEW.status_code='SUBMITTED' AND OLD.status_code='DRAFT' AND EXISTS(
 SELECT 1 FROM cut_cutover_checklist_item i JOIN cut_cutover_checklist_item_result r ON r.tenant_id=i.tenant_id AND r.checklist_item_id=i.id
 WHERE i.tenant_id=NEW.tenant_id AND i.checklist_id=NEW.id AND r.current_marker=1 AND r.result_source_code='MANUAL'
 AND NOT EXISTS(SELECT 1 FROM cut_checklist_result_file f WHERE f.tenant_id=r.tenant_id AND f.checklist_result_id=r.id AND f.file_role_code='MANUAL_EVIDENCE')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_MANUAL_RESULT_REQUIRES_EXACT_FILE';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_18_d` BEFORE DELETE ON `cut_cutover_checklist` FOR EACH ROW
BEGIN
  IF OLD.status_code<>'DRAFT' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_19_i` BEFORE INSERT ON `cut_cutover_checklist_item` FOR EACH ROW
BEGIN
  IF NEW.`interface_schema_snapshot` IS NOT NULL OR NEW.`display_condition_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_item';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_19_u` BEFORE UPDATE ON `cut_cutover_checklist_item` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`interface_schema_snapshot` AS BINARY) <=> CAST(OLD.`interface_schema_snapshot` AS BINARY)) OR NOT(CAST(NEW.`display_condition_snapshot` AS BINARY) <=> CAST(OLD.`display_condition_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_item';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_item';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_19_d` BEFORE DELETE ON `cut_cutover_checklist_item` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_checklist_item';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_20_i` BEFORE INSERT ON `cut_cutover_checklist_item_result` FOR EACH ROW
BEGIN
  IF NEW.`answer_snapshot` IS NOT NULL OR NEW.`query_condition_snapshot` IS NOT NULL OR NEW.`manual_evidence_file_reference` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_20_u` BEFORE UPDATE ON `cut_cutover_checklist_item_result` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`answer_snapshot` AS BINARY) <=> CAST(OLD.`answer_snapshot` AS BINARY)) OR NOT(CAST(NEW.`query_condition_snapshot` AS BINARY) <=> CAST(OLD.`query_condition_snapshot` AS BINARY)) OR NOT(CAST(NEW.`manual_evidence_file_reference` AS BINARY) <=> CAST(OLD.`manual_evidence_file_reference` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item_result';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_item_id` AS BINARY) <=> CAST(OLD.`checklist_item_id` AS BINARY)) OR NOT(CAST(NEW.`result_version` AS BINARY) <=> CAST(OLD.`result_version` AS BINARY)) OR NOT(CAST(NEW.`result_source_code` AS BINARY) <=> CAST(OLD.`result_source_code` AS BINARY)) OR NOT(CAST(NEW.`answer_snapshot` AS BINARY) <=> CAST(OLD.`answer_snapshot` AS BINARY)) OR NOT(CAST(NEW.`fact_description` AS BINARY) <=> CAST(OLD.`fact_description` AS BINARY)) OR NOT(CAST(NEW.`collection_task_id` AS BINARY) <=> CAST(OLD.`collection_task_id` AS BINARY)) OR NOT(CAST(NEW.`collection_result_reference_id` AS BINARY) <=> CAST(OLD.`collection_result_reference_id` AS BINARY)) OR NOT(CAST(NEW.`collection_result_version` AS BINARY) <=> CAST(OLD.`collection_result_version` AS BINARY)) OR NOT(CAST(NEW.`external_source_code` AS BINARY) <=> CAST(OLD.`external_source_code` AS BINARY)) OR NOT(CAST(NEW.`query_condition_snapshot` AS BINARY) <=> CAST(OLD.`query_condition_snapshot` AS BINARY)) OR NOT(CAST(NEW.`query_time` AS BINARY) <=> CAST(OLD.`query_time` AS BINARY)) OR NOT(CAST(NEW.`load_failure_code` AS BINARY) <=> CAST(OLD.`load_failure_code` AS BINARY)) OR NOT(CAST(NEW.`manual_evidence_file_reference` AS BINARY) <=> CAST(OLD.`manual_evidence_file_reference` AS BINARY)) OR NOT(CAST(NEW.`selection_start_time` AS BINARY) <=> CAST(OLD.`selection_start_time` AS BINARY)) OR NOT(CAST(NEW.`select_user_id` AS BINARY) <=> CAST(OLD.`select_user_id` AS BINARY)) OR NOT(CAST(NEW.`selection_reason_code` AS BINARY) <=> CAST(OLD.`selection_reason_code` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`field_definition_id` AS BINARY) <=> CAST(OLD.`field_definition_id` AS BINARY)) OR NOT(CAST(NEW.`base_checklist_result_id` AS BINARY) <=> CAST(OLD.`base_checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`device_id` AS BINARY) <=> CAST(OLD.`device_id` AS BINARY)) OR NOT(CAST(NEW.`command_template_id` AS BINARY) <=> CAST(OLD.`command_template_id` AS BINARY)) OR NOT(CAST(NEW.`collection_log_id` AS BINARY) <=> CAST(OLD.`collection_log_id` AS BINARY)) OR NOT(CAST(NEW.`collection_status_code` AS BINARY) <=> CAST(OLD.`collection_status_code` AS BINARY)) OR NOT(CAST(NEW.`collection_failure_code` AS BINARY) <=> CAST(OLD.`collection_failure_code` AS BINARY)) OR NOT(CAST(NEW.`original_failed_collection_task_id` AS BINARY) <=> CAST(OLD.`original_failed_collection_task_id` AS BINARY)) OR NOT(CAST(NEW.`original_failed_result_version` AS BINARY) <=> CAST(OLD.`original_failed_result_version` AS BINARY)) OR NOT(CAST(NEW.`original_failure_code` AS BINARY) <=> CAST(OLD.`original_failure_code` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item_result';
  END IF;
  IF OLD.selection_end_time IS NOT NULL AND NOT(NEW.selection_end_time <=> OLD.selection_end_time) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_RESULT_SELECTION_ALREADY_CLOSED';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_20_d` BEFORE DELETE ON `cut_cutover_checklist_item_result` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_checklist_item_result';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_21_i` BEFORE INSERT ON `cut_checklist_input_dimension` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_input_dimension';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_21_u` BEFORE UPDATE ON `cut_checklist_input_dimension` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_input_dimension';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_input_dimension';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_input_dimension';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_21_d` BEFORE DELETE ON `cut_checklist_input_dimension` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_input_dimension';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_22_i` BEFORE INSERT ON `cut_checklist_match_candidate` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_match_candidate';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_22_u` BEFORE UPDATE ON `cut_checklist_match_candidate` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_match_candidate';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_match_candidate';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_match_candidate';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_22_d` BEFORE DELETE ON `cut_checklist_match_candidate` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_match_candidate';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_23_i` BEFORE INSERT ON `cut_checklist_device_context` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_device_context';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_23_u` BEFORE UPDATE ON `cut_checklist_device_context` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_device_context';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_device_context';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_device_context';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_23_d` BEFORE DELETE ON `cut_checklist_device_context` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_checklist p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.checklist_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_checklist_device_context';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_24_u` BEFORE UPDATE ON `cut_checklist_result_file` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`custom_result_row_id` AS BINARY) <=> CAST(OLD.`custom_result_row_id` AS BINARY)) OR NOT(CAST(NEW.`field_definition_id` AS BINARY) <=> CAST(OLD.`field_definition_id` AS BINARY)) OR NOT(CAST(NEW.`file_role_code` AS BINARY) <=> CAST(OLD.`file_role_code` AS BINARY)) OR NOT(CAST(NEW.`file_no` AS BINARY) <=> CAST(OLD.`file_no` AS BINARY)) OR NOT(CAST(NEW.`file_artifact_id` AS BINARY) <=> CAST(OLD.`file_artifact_id` AS BINARY)) OR NOT(CAST(NEW.`file_version_no` AS BINARY) <=> CAST(OLD.`file_version_no` AS BINARY)) OR NOT(CAST(NEW.`file_reference_key` AS BINARY) <=> CAST(OLD.`file_reference_key` AS BINARY)) OR NOT(CAST(NEW.`file_artifact_fact_version` AS BINARY) <=> CAST(OLD.`file_artifact_fact_version` AS BINARY)) OR NOT(CAST(NEW.`file_reference_fact_version` AS BINARY) <=> CAST(OLD.`file_reference_fact_version` AS BINARY)) OR NOT(CAST(NEW.`file_availability_fact_version` AS BINARY) <=> CAST(OLD.`file_availability_fact_version` AS BINARY)) OR NOT(CAST(NEW.`file_scope_version` AS BINARY) <=> CAST(OLD.`file_scope_version` AS BINARY)) OR NOT(CAST(NEW.`file_sha256` AS BINARY) <=> CAST(OLD.`file_sha256` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_result_file';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_24_d` BEFORE DELETE ON `cut_checklist_result_file` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_result_file';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_25_u` BEFORE UPDATE ON `cut_checklist_external_query` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_item_id` AS BINARY) <=> CAST(OLD.`checklist_item_id` AS BINARY)) OR NOT(CAST(NEW.`item_definition_revision_id` AS BINARY) <=> CAST(OLD.`item_definition_revision_id` AS BINARY)) OR NOT(CAST(NEW.`device_id` AS BINARY) <=> CAST(OLD.`device_id` AS BINARY)) OR NOT(CAST(NEW.`interface_code` AS BINARY) <=> CAST(OLD.`interface_code` AS BINARY)) OR NOT(CAST(NEW.`query_no` AS BINARY) <=> CAST(OLD.`query_no` AS BINARY)) OR NOT(CAST(NEW.`version_context_code` AS BINARY) <=> CAST(OLD.`version_context_code` AS BINARY)) OR NOT(CAST(NEW.`queried_software_version` AS BINARY) <=> CAST(OLD.`queried_software_version` AS BINARY)) OR NOT(CAST(NEW.`product_model` AS BINARY) <=> CAST(OLD.`product_model` AS BINARY)) OR NOT(CAST(NEW.`request_id` AS BINARY) <=> CAST(OLD.`request_id` AS BINARY)) OR NOT(CAST(NEW.`query_user_id` AS BINARY) <=> CAST(OLD.`query_user_id` AS BINARY)) OR NOT(CAST(NEW.`start_time` AS BINARY) <=> CAST(OLD.`start_time` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_external_query';
  END IF;
  IF (OLD.finish_time IS NOT NULL) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_item_id` AS BINARY) <=> CAST(OLD.`checklist_item_id` AS BINARY)) OR NOT(CAST(NEW.`item_definition_revision_id` AS BINARY) <=> CAST(OLD.`item_definition_revision_id` AS BINARY)) OR NOT(CAST(NEW.`device_id` AS BINARY) <=> CAST(OLD.`device_id` AS BINARY)) OR NOT(CAST(NEW.`interface_code` AS BINARY) <=> CAST(OLD.`interface_code` AS BINARY)) OR NOT(CAST(NEW.`query_no` AS BINARY) <=> CAST(OLD.`query_no` AS BINARY)) OR NOT(CAST(NEW.`version_context_code` AS BINARY) <=> CAST(OLD.`version_context_code` AS BINARY)) OR NOT(CAST(NEW.`queried_software_version` AS BINARY) <=> CAST(OLD.`queried_software_version` AS BINARY)) OR NOT(CAST(NEW.`product_model` AS BINARY) <=> CAST(OLD.`product_model` AS BINARY)) OR NOT(CAST(NEW.`request_id` AS BINARY) <=> CAST(OLD.`request_id` AS BINARY)) OR NOT(CAST(NEW.`query_user_id` AS BINARY) <=> CAST(OLD.`query_user_id` AS BINARY)) OR NOT(CAST(NEW.`start_time` AS BINARY) <=> CAST(OLD.`start_time` AS BINARY)) OR NOT(CAST(NEW.`finish_time` AS BINARY) <=> CAST(OLD.`finish_time` AS BINARY)) OR NOT(CAST(NEW.`succeeded_flag` AS BINARY) <=> CAST(OLD.`succeeded_flag` AS BINARY)) OR NOT(CAST(NEW.`failure_code` AS BINARY) <=> CAST(OLD.`failure_code` AS BINARY)) OR NOT(CAST(NEW.`failure_detail` AS BINARY) <=> CAST(OLD.`failure_detail` AS BINARY)) OR NOT(CAST(NEW.`returned_row_count` AS BINARY) <=> CAST(OLD.`returned_row_count` AS BINARY)) OR NOT(CAST(NEW.`loaded_checklist_result_id` AS BINARY) <=> CAST(OLD.`loaded_checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_external_query';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_25_d` BEFORE DELETE ON `cut_checklist_external_query` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_external_query';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_26_u` BEFORE UPDATE ON `cut_checklist_external_query_parameter` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`external_query_id` AS BINARY) <=> CAST(OLD.`external_query_id` AS BINARY)) OR NOT(CAST(NEW.`mapping_id` AS BINARY) <=> CAST(OLD.`mapping_id` AS BINARY)) OR NOT(CAST(NEW.`parameter_name` AS BINARY) <=> CAST(OLD.`parameter_name` AS BINARY)) OR NOT(CAST(NEW.`value_type_code` AS BINARY) <=> CAST(OLD.`value_type_code` AS BINARY)) OR NOT(CAST(NEW.`value_text` AS BINARY) <=> CAST(OLD.`value_text` AS BINARY)) OR NOT(CAST(NEW.`value_number` AS BINARY) <=> CAST(OLD.`value_number` AS BINARY)) OR NOT(CAST(NEW.`value_boolean` AS BINARY) <=> CAST(OLD.`value_boolean` AS BINARY)) OR NOT(CAST(NEW.`value_datetime` AS BINARY) <=> CAST(OLD.`value_datetime` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_external_query_parameter';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_26_d` BEFORE DELETE ON `cut_checklist_external_query_parameter` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_external_query_parameter';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_27_u` BEFORE UPDATE ON `cut_checklist_result_query_reference` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`external_query_id` AS BINARY) <=> CAST(OLD.`external_query_id` AS BINARY)) OR NOT(CAST(NEW.`reference_purpose_code` AS BINARY) <=> CAST(OLD.`reference_purpose_code` AS BINARY)) OR NOT(CAST(NEW.`sort_order` AS BINARY) <=> CAST(OLD.`sort_order` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_result_query_reference';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_27_d` BEFORE DELETE ON `cut_checklist_result_query_reference` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_checklist_result_query_reference';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_28_u` BEFORE UPDATE ON `cut_result_cutover_background` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`solves_online_issue_flag` AS BINARY) <=> CAST(OLD.`solves_online_issue_flag` AS BINARY)) OR NOT(CAST(NEW.`issue_ticket_no` AS BINARY) <=> CAST(OLD.`issue_ticket_no` AS BINARY)) OR NOT(CAST(NEW.`issue_handler` AS BINARY) <=> CAST(OLD.`issue_handler` AS BINARY)) OR NOT(CAST(NEW.`repeat_cutover_flag` AS BINARY) <=> CAST(OLD.`repeat_cutover_flag` AS BINARY)) OR NOT(CAST(NEW.`first_cutover_owner` AS BINARY) <=> CAST(OLD.`first_cutover_owner` AS BINARY)) OR NOT(CAST(NEW.`background_description` AS BINARY) <=> CAST(OLD.`background_description` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_cutover_background';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_28_d` BEFORE DELETE ON `cut_result_cutover_background` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_cutover_background';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_29_u` BEFORE UPDATE ON `cut_result_business_summary` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`business_description` AS BINARY) <=> CAST(OLD.`business_description` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_business_summary';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_29_d` BEFORE DELETE ON `cut_result_business_summary` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_business_summary';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_30_u` BEFORE UPDATE ON `cut_result_impact_scope` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`impact_scope_code` AS BINARY) <=> CAST(OLD.`impact_scope_code` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_impact_scope';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_30_d` BEFORE DELETE ON `cut_result_impact_scope` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_impact_scope';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_31_u` BEFORE UPDATE ON `cut_result_continuity_requirement` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`continuity_requirement_code` AS BINARY) <=> CAST(OLD.`continuity_requirement_code` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_continuity_requirement';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_31_d` BEFORE DELETE ON `cut_result_continuity_requirement` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_continuity_requirement';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_32_u` BEFORE UPDATE ON `cut_result_interruption` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`interruption_count` AS BINARY) <=> CAST(OLD.`interruption_count` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_interruption';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_32_d` BEFORE DELETE ON `cut_result_interruption` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_interruption';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_33_u` BEFORE UPDATE ON `cut_result_check_feedback` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`check_normal_flag` AS BINARY) <=> CAST(OLD.`check_normal_flag` AS BINARY)) OR NOT(CAST(NEW.`executed_flag` AS BINARY) <=> CAST(OLD.`executed_flag` AS BINARY)) OR NOT(CAST(NEW.`remark` AS BINARY) <=> CAST(OLD.`remark` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_check_feedback';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_33_d` BEFORE DELETE ON `cut_result_check_feedback` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_check_feedback';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_34_u` BEFORE UPDATE ON `cut_result_spare_reference` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`major_cutover_flag` AS BINARY) <=> CAST(OLD.`major_cutover_flag` AS BINARY)) OR NOT(CAST(NEW.`spare_reference_text` AS BINARY) <=> CAST(OLD.`spare_reference_text` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_spare_reference';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_34_d` BEFORE DELETE ON `cut_result_spare_reference` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_spare_reference';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_35_u` BEFORE UPDATE ON `cut_result_bulletin_summary` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`has_bulletins_flag` AS BINARY) <=> CAST(OLD.`has_bulletins_flag` AS BINARY)) OR NOT(CAST(NEW.`manual_query_basis` AS BINARY) <=> CAST(OLD.`manual_query_basis` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_bulletin_summary';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_35_d` BEFORE DELETE ON `cut_result_bulletin_summary` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_bulletin_summary';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_36_u` BEFORE UPDATE ON `cut_result_device_location` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`table_field_definition_id` AS BINARY) <=> CAST(OLD.`table_field_definition_id` AS BINARY)) OR NOT(CAST(NEW.`row_no` AS BINARY) <=> CAST(OLD.`row_no` AS BINARY)) OR NOT(CAST(NEW.`device_type` AS BINARY) <=> CAST(OLD.`device_type` AS BINARY)) OR NOT(CAST(NEW.`sn` AS BINARY) <=> CAST(OLD.`sn` AS BINARY)) OR NOT(CAST(NEW.`device_name` AS BINARY) <=> CAST(OLD.`device_name` AS BINARY)) OR NOT(CAST(NEW.`location` AS BINARY) <=> CAST(OLD.`location` AS BINARY)) OR NOT(CAST(NEW.`extension_values` AS BINARY) <=> CAST(OLD.`extension_values` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_device_location';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_36_d` BEFORE DELETE ON `cut_result_device_location` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_device_location';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_37_u` BEFORE UPDATE ON `cut_result_interface_plan` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`table_field_definition_id` AS BINARY) <=> CAST(OLD.`table_field_definition_id` AS BINARY)) OR NOT(CAST(NEW.`row_no` AS BINARY) <=> CAST(OLD.`row_no` AS BINARY)) OR NOT(CAST(NEW.`local_device` AS BINARY) <=> CAST(OLD.`local_device` AS BINARY)) OR NOT(CAST(NEW.`local_interface` AS BINARY) <=> CAST(OLD.`local_interface` AS BINARY)) OR NOT(CAST(NEW.`remote_interface` AS BINARY) <=> CAST(OLD.`remote_interface` AS BINARY)) OR NOT(CAST(NEW.`interface_type` AS BINARY) <=> CAST(OLD.`interface_type` AS BINARY)) OR NOT(CAST(NEW.`cable_model` AS BINARY) <=> CAST(OLD.`cable_model` AS BINARY)) OR NOT(CAST(NEW.`remark` AS BINARY) <=> CAST(OLD.`remark` AS BINARY)) OR NOT(CAST(NEW.`extension_values` AS BINARY) <=> CAST(OLD.`extension_values` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_interface_plan';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_37_d` BEFORE DELETE ON `cut_result_interface_plan` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_interface_plan';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_38_u` BEFORE UPDATE ON `cut_result_ip_plan` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`table_field_definition_id` AS BINARY) <=> CAST(OLD.`table_field_definition_id` AS BINARY)) OR NOT(CAST(NEW.`row_no` AS BINARY) <=> CAST(OLD.`row_no` AS BINARY)) OR NOT(CAST(NEW.`device_name` AS BINARY) <=> CAST(OLD.`device_name` AS BINARY)) OR NOT(CAST(NEW.`interface_name` AS BINARY) <=> CAST(OLD.`interface_name` AS BINARY)) OR NOT(CAST(NEW.`ip_address` AS BINARY) <=> CAST(OLD.`ip_address` AS BINARY)) OR NOT(CAST(NEW.`subnet_mask` AS BINARY) <=> CAST(OLD.`subnet_mask` AS BINARY)) OR NOT(CAST(NEW.`vrrp_ip` AS BINARY) <=> CAST(OLD.`vrrp_ip` AS BINARY)) OR NOT(CAST(NEW.`gateway` AS BINARY) <=> CAST(OLD.`gateway` AS BINARY)) OR NOT(CAST(NEW.`remark` AS BINARY) <=> CAST(OLD.`remark` AS BINARY)) OR NOT(CAST(NEW.`extension_values` AS BINARY) <=> CAST(OLD.`extension_values` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_ip_plan';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_38_d` BEFORE DELETE ON `cut_result_ip_plan` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_ip_plan';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_39_u` BEFORE UPDATE ON `cut_result_performance` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`table_field_definition_id` AS BINARY) <=> CAST(OLD.`table_field_definition_id` AS BINARY)) OR NOT(CAST(NEW.`row_no` AS BINARY) <=> CAST(OLD.`row_no` AS BINARY)) OR NOT(CAST(NEW.`sn` AS BINARY) <=> CAST(OLD.`sn` AS BINARY)) OR NOT(CAST(NEW.`concurrent_connections` AS BINARY) <=> CAST(OLD.`concurrent_connections` AS BINARY)) OR NOT(CAST(NEW.`new_connections` AS BINARY) <=> CAST(OLD.`new_connections` AS BINARY)) OR NOT(CAST(NEW.`cpu_usage` AS BINARY) <=> CAST(OLD.`cpu_usage` AS BINARY)) OR NOT(CAST(NEW.`memory_usage` AS BINARY) <=> CAST(OLD.`memory_usage` AS BINARY)) OR NOT(CAST(NEW.`extension_values` AS BINARY) <=> CAST(OLD.`extension_values` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_performance';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_39_d` BEFORE DELETE ON `cut_result_performance` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_performance';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_40_u` BEFORE UPDATE ON `cut_result_technical_bulletin` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`table_field_definition_id` AS BINARY) <=> CAST(OLD.`table_field_definition_id` AS BINARY)) OR NOT(CAST(NEW.`row_no` AS BINARY) <=> CAST(OLD.`row_no` AS BINARY)) OR NOT(CAST(NEW.`external_query_id` AS BINARY) <=> CAST(OLD.`external_query_id` AS BINARY)) OR NOT(CAST(NEW.`source_bulletin_row_id` AS BINARY) <=> CAST(OLD.`source_bulletin_row_id` AS BINARY)) OR NOT(CAST(NEW.`external_record_id` AS BINARY) <=> CAST(OLD.`external_record_id` AS BINARY)) OR NOT(CAST(NEW.`external_record_version` AS BINARY) <=> CAST(OLD.`external_record_version` AS BINARY)) OR NOT(CAST(NEW.`bulletin_number` AS BINARY) <=> CAST(OLD.`bulletin_number` AS BINARY)) OR NOT(CAST(NEW.`bulletin_subject` AS BINARY) <=> CAST(OLD.`bulletin_subject` AS BINARY)) OR NOT(CAST(NEW.`bulletin_description` AS BINARY) <=> CAST(OLD.`bulletin_description` AS BINARY)) OR NOT(CAST(NEW.`bulletin_solution` AS BINARY) <=> CAST(OLD.`bulletin_solution` AS BINARY)) OR NOT(CAST(NEW.`involved_flag` AS BINARY) <=> CAST(OLD.`involved_flag` AS BINARY)) OR NOT(CAST(NEW.`measures` AS BINARY) <=> CAST(OLD.`measures` AS BINARY)) OR NOT(CAST(NEW.`extension_values` AS BINARY) <=> CAST(OLD.`extension_values` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_technical_bulletin';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_40_d` BEFORE DELETE ON `cut_result_technical_bulletin` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_technical_bulletin';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_41_u` BEFORE UPDATE ON `cut_result_custom_value` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`field_definition_id` AS BINARY) <=> CAST(OLD.`field_definition_id` AS BINARY)) OR NOT(CAST(NEW.`extension_values` AS BINARY) <=> CAST(OLD.`extension_values` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_custom_value';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_41_d` BEFORE DELETE ON `cut_result_custom_value` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_custom_value';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_42_u` BEFORE UPDATE ON `cut_result_custom_row` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`table_field_definition_id` AS BINARY) <=> CAST(OLD.`table_field_definition_id` AS BINARY)) OR NOT(CAST(NEW.`row_no` AS BINARY) <=> CAST(OLD.`row_no` AS BINARY)) OR NOT(CAST(NEW.`parent_custom_row_id` AS BINARY) <=> CAST(OLD.`parent_custom_row_id` AS BINARY)) OR NOT(CAST(NEW.`extension_values` AS BINARY) <=> CAST(OLD.`extension_values` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_custom_row';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_42_d` BEFORE DELETE ON `cut_result_custom_row` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_result_custom_row';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_43_i` BEFORE INSERT ON `cut_plan_revision` FOR EACH ROW
BEGIN
  IF NEW.`template_section_snapshot` IS NOT NULL OR NEW.`source_snapshot` IS NOT NULL OR NEW.`content_snapshot` IS NOT NULL OR NEW.`file_fact_version` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_43_u` BEFORE UPDATE ON `cut_plan_revision` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`template_section_snapshot` AS BINARY) <=> CAST(OLD.`template_section_snapshot` AS BINARY)) OR NOT(CAST(NEW.`source_snapshot` AS BINARY) <=> CAST(OLD.`source_snapshot` AS BINARY)) OR NOT(CAST(NEW.`content_snapshot` AS BINARY) <=> CAST(OLD.`content_snapshot` AS BINARY)) OR NOT(CAST(NEW.`file_fact_version` AS BINARY) <=> CAST(OLD.`file_fact_version` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_revision';
  END IF;
  IF (OLD.status_code<>'DRAFT' OR OLD.origin_code='LEGACY_FORWARD') AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`revision_no` AS BINARY) <=> CAST(OLD.`revision_no` AS BINARY)) OR NOT(CAST(NEW.`origin_code` AS BINARY) <=> CAST(OLD.`origin_code` AS BINARY)) OR NOT(CAST(NEW.`edit_mode_code` AS BINARY) <=> CAST(OLD.`edit_mode_code` AS BINARY)) OR NOT(CAST(NEW.`grade_code` AS BINARY) <=> CAST(OLD.`grade_code` AS BINARY)) OR NOT(CAST(NEW.`assessment_id` AS BINARY) <=> CAST(OLD.`assessment_id` AS BINARY)) OR NOT(CAST(NEW.`assessment_version` AS BINARY) <=> CAST(OLD.`assessment_version` AS BINARY)) OR NOT(CAST(NEW.`checklist_id` AS BINARY) <=> CAST(OLD.`checklist_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_version` AS BINARY) <=> CAST(OLD.`checklist_version` AS BINARY)) OR NOT(CAST(NEW.`configuration_revision_id` AS BINARY) <=> CAST(OLD.`configuration_revision_id` AS BINARY)) OR NOT(CAST(NEW.`configuration_code` AS BINARY) <=> CAST(OLD.`configuration_code` AS BINARY)) OR NOT(CAST(NEW.`configuration_revision_no` AS BINARY) <=> CAST(OLD.`configuration_revision_no` AS BINARY)) OR NOT(CAST(NEW.`template_section_snapshot` AS BINARY) <=> CAST(OLD.`template_section_snapshot` AS BINARY)) OR NOT(CAST(NEW.`source_snapshot` AS BINARY) <=> CAST(OLD.`source_snapshot` AS BINARY)) OR NOT(CAST(NEW.`content_snapshot` AS BINARY) <=> CAST(OLD.`content_snapshot` AS BINARY)) OR NOT(CAST(NEW.`file_artifact_id` AS BINARY) <=> CAST(OLD.`file_artifact_id` AS BINARY)) OR NOT(CAST(NEW.`file_version_no` AS BINARY) <=> CAST(OLD.`file_version_no` AS BINARY)) OR NOT(CAST(NEW.`file_reference_key` AS BINARY) <=> CAST(OLD.`file_reference_key` AS BINARY)) OR NOT(CAST(NEW.`file_fact_version` AS BINARY) <=> CAST(OLD.`file_fact_version` AS BINARY)) OR NOT(CAST(NEW.`file_scope_version` AS BINARY) <=> CAST(OLD.`file_scope_version` AS BINARY)) OR NOT(CAST(NEW.`file_sha256` AS BINARY) <=> CAST(OLD.`file_sha256` AS BINARY)) OR NOT(CAST(NEW.`ownership_confirmed_flag` AS BINARY) <=> CAST(OLD.`ownership_confirmed_flag` AS BINARY)) OR NOT(CAST(NEW.`submit_user_id` AS BINARY) <=> CAST(OLD.`submit_user_id` AS BINARY)) OR NOT(CAST(NEW.`submit_time` AS BINARY) <=> CAST(OLD.`submit_time` AS BINARY)) OR NOT(CAST(NEW.`approval_instance_id` AS BINARY) <=> CAST(OLD.`approval_instance_id` AS BINARY)) OR NOT(CAST(NEW.`source_plan_revision_id` AS BINARY) <=> CAST(OLD.`source_plan_revision_id` AS BINARY)) OR NOT(CAST(NEW.`revision_reason_code` AS BINARY) <=> CAST(OLD.`revision_reason_code` AS BINARY)) OR NOT(CAST(NEW.`legacy_plan_id` AS BINARY) <=> CAST(OLD.`legacy_plan_id` AS BINARY)) OR NOT(CAST(NEW.`legacy_status_raw` AS BINARY) <=> CAST(OLD.`legacy_status_raw` AS BINARY)) OR NOT(CAST(NEW.`legacy_source_version` AS BINARY) <=> CAST(OLD.`legacy_source_version` AS BINARY)) OR NOT(CAST(NEW.`legacy_mapping_version` AS BINARY) <=> CAST(OLD.`legacy_mapping_version` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`source_task_version` AS BINARY) <=> CAST(OLD.`source_task_version` AS BINARY)) OR NOT(CAST(NEW.`source_project_id` AS BINARY) <=> CAST(OLD.`source_project_id` AS BINARY)) OR NOT(CAST(NEW.`source_project_version` AS BINARY) <=> CAST(OLD.`source_project_version` AS BINARY)) OR NOT(CAST(NEW.`source_project_scope_version` AS BINARY) <=> CAST(OLD.`source_project_scope_version` AS BINARY)) OR NOT(CAST(NEW.`source_cutover_type_code` AS BINARY) <=> CAST(OLD.`source_cutover_type_code` AS BINARY)) OR NOT(CAST(NEW.`legacy_task_id` AS BINARY) <=> CAST(OLD.`legacy_task_id` AS BINARY)) OR NOT(CAST(NEW.`legacy_code` AS BINARY) <=> CAST(OLD.`legacy_code` AS BINARY)) OR NOT(CAST(NEW.`legacy_name` AS BINARY) <=> CAST(OLD.`legacy_name` AS BINARY)) OR NOT(CAST(NEW.`legacy_grade_code` AS BINARY) <=> CAST(OLD.`legacy_grade_code` AS BINARY)) OR NOT(CAST(NEW.`legacy_remark` AS BINARY) <=> CAST(OLD.`legacy_remark` AS BINARY)) OR NOT(CAST(NEW.`project_description` AS BINARY) <=> CAST(OLD.`project_description` AS BINARY)) OR NOT(CAST(NEW.`rollback_description` AS BINARY) <=> CAST(OLD.`rollback_description` AS BINARY)) OR NOT(CAST(NEW.`customer_contact_id` AS BINARY) <=> CAST(OLD.`customer_contact_id` AS BINARY)) OR NOT(CAST(NEW.`customer_contact_version` AS BINARY) <=> CAST(OLD.`customer_contact_version` AS BINARY)) OR NOT(CAST(NEW.`file_artifact_fact_version` AS BINARY) <=> CAST(OLD.`file_artifact_fact_version` AS BINARY)) OR NOT(CAST(NEW.`file_reference_fact_version` AS BINARY) <=> CAST(OLD.`file_reference_fact_version` AS BINARY)) OR NOT(CAST(NEW.`file_availability_fact_version` AS BINARY) <=> CAST(OLD.`file_availability_fact_version` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_43_d` BEFORE DELETE ON `cut_plan_revision` FOR EACH ROW
BEGIN
  IF OLD.status_code<>'DRAFT' OR OLD.origin_code='LEGACY_FORWARD' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_44_i` BEFORE INSERT ON `cut_step` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_step';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_44_u` BEFORE UPDATE ON `cut_step` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_step';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_step';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_step';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_44_d` BEFORE DELETE ON `cut_step` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_step';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_45_u` BEFORE UPDATE ON `cut_cutover_support_arrangement` FOR EACH ROW
BEGIN
  IF (EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code<>'DRAFT')) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`plan_revision_id` AS BINARY) <=> CAST(OLD.`plan_revision_id` AS BINARY)) OR NOT(CAST(NEW.`role_code` AS BINARY) <=> CAST(OLD.`role_code` AS BINARY)) OR NOT(CAST(NEW.`person_name` AS BINARY) <=> CAST(OLD.`person_name` AS BINARY)) OR NOT(CAST(NEW.`duty_description` AS BINARY) <=> CAST(OLD.`duty_description` AS BINARY)) OR NOT(CAST(NEW.`phone` AS BINARY) <=> CAST(OLD.`phone` AS BINARY)) OR NOT(CAST(NEW.`arrive_time` AS BINARY) <=> CAST(OLD.`arrive_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_support_arrangement';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_45_d` BEFORE DELETE ON `cut_cutover_support_arrangement` FOR EACH ROW
BEGIN
  IF EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code<>'DRAFT') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_support_arrangement';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_46_i` BEFORE INSERT ON `cut_plan_schedule` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_schedule';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_46_u` BEFORE UPDATE ON `cut_plan_schedule` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_schedule';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_schedule';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_schedule';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_46_d` BEFORE DELETE ON `cut_plan_schedule` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_schedule';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_47_i` BEFORE INSERT ON `cut_plan_device` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_47_u` BEFORE UPDATE ON `cut_plan_device` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_device';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_device';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_47_d` BEFORE DELETE ON `cut_plan_device` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_48_i` BEFORE INSERT ON `cut_plan_file` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_file';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_48_u` BEFORE UPDATE ON `cut_plan_file` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_file';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_file';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_file';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_48_d` BEFORE DELETE ON `cut_plan_file` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_file';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_49_i` BEFORE INSERT ON `cut_plan_risk_mitigation` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_risk_mitigation';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_49_u` BEFORE UPDATE ON `cut_plan_risk_mitigation` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_risk_mitigation';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_risk_mitigation';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_risk_mitigation';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_49_d` BEFORE DELETE ON `cut_plan_risk_mitigation` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_plan_revision p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.plan_revision_id AND p.status_code='DRAFT' AND p.current_marker=1 AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_plan_risk_mitigation';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_50_u` BEFORE UPDATE ON `cut_plan_support_contact_change` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`support_arrangement_id` AS BINARY) <=> CAST(OLD.`support_arrangement_id` AS BINARY)) OR NOT(CAST(NEW.`change_no` AS BINARY) <=> CAST(OLD.`change_no` AS BINARY)) OR NOT(CAST(NEW.`previous_change_id` AS BINARY) <=> CAST(OLD.`previous_change_id` AS BINARY)) OR NOT(CAST(NEW.`before_person_name` AS BINARY) <=> CAST(OLD.`before_person_name` AS BINARY)) OR NOT(CAST(NEW.`before_phone` AS BINARY) <=> CAST(OLD.`before_phone` AS BINARY)) OR NOT(CAST(NEW.`before_arrive_time` AS BINARY) <=> CAST(OLD.`before_arrive_time` AS BINARY)) OR NOT(CAST(NEW.`before_customer_contact_id` AS BINARY) <=> CAST(OLD.`before_customer_contact_id` AS BINARY)) OR NOT(CAST(NEW.`before_customer_contact_version` AS BINARY) <=> CAST(OLD.`before_customer_contact_version` AS BINARY)) OR NOT(CAST(NEW.`after_person_name` AS BINARY) <=> CAST(OLD.`after_person_name` AS BINARY)) OR NOT(CAST(NEW.`after_phone` AS BINARY) <=> CAST(OLD.`after_phone` AS BINARY)) OR NOT(CAST(NEW.`after_arrive_time` AS BINARY) <=> CAST(OLD.`after_arrive_time` AS BINARY)) OR NOT(CAST(NEW.`after_customer_contact_id` AS BINARY) <=> CAST(OLD.`after_customer_contact_id` AS BINARY)) OR NOT(CAST(NEW.`after_customer_contact_version` AS BINARY) <=> CAST(OLD.`after_customer_contact_version` AS BINARY)) OR NOT(CAST(NEW.`reason` AS BINARY) <=> CAST(OLD.`reason` AS BINARY)) OR NOT(CAST(NEW.`operate_user_id` AS BINARY) <=> CAST(OLD.`operate_user_id` AS BINARY)) OR NOT(CAST(NEW.`operate_time` AS BINARY) <=> CAST(OLD.`operate_time` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_support_contact_change';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_50_d` BEFORE DELETE ON `cut_plan_support_contact_change` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_support_contact_change';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_51_i` BEFORE INSERT ON `cut_approval_instance` FOR EACH ROW
BEGIN
  IF NEW.`source_snapshot` IS NOT NULL OR NEW.`route_snapshot` IS NOT NULL OR NEW.`lead_time_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_51_u` BEFORE UPDATE ON `cut_approval_instance` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`source_snapshot` AS BINARY) <=> CAST(OLD.`source_snapshot` AS BINARY)) OR NOT(CAST(NEW.`route_snapshot` AS BINARY) <=> CAST(OLD.`route_snapshot` AS BINARY)) OR NOT(CAST(NEW.`lead_time_snapshot` AS BINARY) <=> CAST(OLD.`lead_time_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_instance';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`project_id` AS BINARY) <=> CAST(OLD.`project_id` AS BINARY)) OR NOT(CAST(NEW.`source_snapshot_version` AS BINARY) <=> CAST(OLD.`source_snapshot_version` AS BINARY)) OR NOT(CAST(NEW.`source_snapshot` AS BINARY) <=> CAST(OLD.`source_snapshot` AS BINARY)) OR NOT(CAST(NEW.`lead_time_enabled_flag` AS BINARY) <=> CAST(OLD.`lead_time_enabled_flag` AS BINARY)) OR NOT(CAST(NEW.`lead_time_snapshot` AS BINARY) <=> CAST(OLD.`lead_time_snapshot` AS BINARY)) OR NOT(CAST(NEW.`source_task_version` AS BINARY) <=> CAST(OLD.`source_task_version` AS BINARY)) OR NOT(CAST(NEW.`source_plan_version` AS BINARY) <=> CAST(OLD.`source_plan_version` AS BINARY)) OR NOT(CAST(NEW.`source_cutover_type_code` AS BINARY) <=> CAST(OLD.`source_cutover_type_code` AS BINARY)) OR NOT(CAST(NEW.`source_network_mode_code` AS BINARY) <=> CAST(OLD.`source_network_mode_code` AS BINARY)) OR NOT(CAST(NEW.`source_schedule_time` AS BINARY) <=> CAST(OLD.`source_schedule_time` AS BINARY)) OR NOT(CAST(NEW.`lead_rule_version` AS BINARY) <=> CAST(OLD.`lead_rule_version` AS BINARY)) OR NOT(CAST(NEW.`lead_timezone_id` AS BINARY) <=> CAST(OLD.`lead_timezone_id` AS BINARY)) OR NOT(CAST(NEW.`lead_plan_submit_time` AS BINARY) <=> CAST(OLD.`lead_plan_submit_time` AS BINARY)) OR NOT(CAST(NEW.`lead_required_days` AS BINARY) <=> CAST(OLD.`lead_required_days` AS BINARY)) OR NOT(CAST(NEW.`lead_actual_natural_days` AS BINARY) <=> CAST(OLD.`lead_actual_natural_days` AS BINARY)) OR NOT(CAST(NEW.`lead_late_submission_flag` AS BINARY) <=> CAST(OLD.`lead_late_submission_flag` AS BINARY)) OR NOT(CAST(NEW.`project_version` AS BINARY) <=> CAST(OLD.`project_version` AS BINARY)) OR NOT(CAST(NEW.`project_code` AS BINARY) <=> CAST(OLD.`project_code` AS BINARY)) OR NOT(CAST(NEW.`project_name` AS BINARY) <=> CAST(OLD.`project_name` AS BINARY)) OR NOT(CAST(NEW.`customer_id` AS BINARY) <=> CAST(OLD.`customer_id` AS BINARY)) OR NOT(CAST(NEW.`customer_code` AS BINARY) <=> CAST(OLD.`customer_code` AS BINARY)) OR NOT(CAST(NEW.`customer_name` AS BINARY) <=> CAST(OLD.`customer_name` AS BINARY)) OR NOT(CAST(NEW.`department_id` AS BINARY) <=> CAST(OLD.`department_id` AS BINARY)) OR NOT(CAST(NEW.`department_code` AS BINARY) <=> CAST(OLD.`department_code` AS BINARY)) OR NOT(CAST(NEW.`department_name` AS BINARY) <=> CAST(OLD.`department_name` AS BINARY)) OR NOT(CAST(NEW.`project_scope_version` AS BINARY) <=> CAST(OLD.`project_scope_version` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`plan_revision_id` AS BINARY) <=> CAST(OLD.`plan_revision_id` AS BINARY)) OR NOT(CAST(NEW.`plan_revision_no` AS BINARY) <=> CAST(OLD.`plan_revision_no` AS BINARY)) OR NOT(CAST(NEW.`assessment_id` AS BINARY) <=> CAST(OLD.`assessment_id` AS BINARY)) OR NOT(CAST(NEW.`assessment_version` AS BINARY) <=> CAST(OLD.`assessment_version` AS BINARY)) OR NOT(CAST(NEW.`checklist_id` AS BINARY) <=> CAST(OLD.`checklist_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_version` AS BINARY) <=> CAST(OLD.`checklist_version` AS BINARY)) OR NOT(CAST(NEW.`grade_code` AS BINARY) <=> CAST(OLD.`grade_code` AS BINARY)) OR NOT(CAST(NEW.`initiator_user_id` AS BINARY) <=> CAST(OLD.`initiator_user_id` AS BINARY)) OR NOT(CAST(NEW.`initiator_project_scope_version` AS BINARY) <=> CAST(OLD.`initiator_project_scope_version` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_instance';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_51_d` BEFORE DELETE ON `cut_approval_instance` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_instance';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_52_i` BEFORE INSERT ON `cut_approval_node` FOR EACH ROW
BEGIN
  IF NEW.`candidate_fact_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_52_u` BEFORE UPDATE ON `cut_approval_node` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`candidate_fact_snapshot` AS BINARY) <=> CAST(OLD.`candidate_fact_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_node';
  END IF;
  IF (OLD.status_code IN ('APPROVED','REJECTED','CANCELLED')) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`approval_instance_id` AS BINARY) <=> CAST(OLD.`approval_instance_id` AS BINARY)) OR NOT(CAST(NEW.`node_no` AS BINARY) <=> CAST(OLD.`node_no` AS BINARY)) OR NOT(CAST(NEW.`node_code` AS BINARY) <=> CAST(OLD.`node_code` AS BINARY)) OR NOT(CAST(NEW.`status_code` AS BINARY) <=> CAST(OLD.`status_code` AS BINARY)) OR NOT(CAST(NEW.`original_approver_user_id` AS BINARY) <=> CAST(OLD.`original_approver_user_id` AS BINARY)) OR NOT(CAST(NEW.`current_approver_user_id` AS BINARY) <=> CAST(OLD.`current_approver_user_id` AS BINARY)) OR NOT(CAST(NEW.`candidate_fact_snapshot` AS BINARY) <=> CAST(OLD.`candidate_fact_snapshot` AS BINARY)) OR NOT(CAST(NEW.`project_scope_version` AS BINARY) <=> CAST(OLD.`project_scope_version` AS BINARY)) OR NOT(CAST(NEW.`assessment_review_decision_code` AS BINARY) <=> CAST(OLD.`assessment_review_decision_code` AS BINARY)) OR NOT(CAST(NEW.`assessment_review_reason` AS BINARY) <=> CAST(OLD.`assessment_review_reason` AS BINARY)) OR NOT(CAST(NEW.`feedback` AS BINARY) <=> CAST(OLD.`feedback` AS BINARY)) OR NOT(CAST(NEW.`decide_time` AS BINARY) <=> CAST(OLD.`decide_time` AS BINARY)) OR NOT(CAST(NEW.`version` AS BINARY) <=> CAST(OLD.`version` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`updater` AS BINARY) <=> CAST(OLD.`updater` AS BINARY)) OR NOT(CAST(NEW.`update_time` AS BINARY) <=> CAST(OLD.`update_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`approval_mode_code` AS BINARY) <=> CAST(OLD.`approval_mode_code` AS BINARY)) OR NOT(CAST(NEW.`candidate_role_id` AS BINARY) <=> CAST(OLD.`candidate_role_id` AS BINARY)) OR NOT(CAST(NEW.`candidate_role_code` AS BINARY) <=> CAST(OLD.`candidate_role_code` AS BINARY)) OR NOT(CAST(NEW.`candidate_group_key` AS BINARY) <=> CAST(OLD.`candidate_group_key` AS BINARY)) OR NOT(CAST(NEW.`qualification_kind_code` AS BINARY) <=> CAST(OLD.`qualification_kind_code` AS BINARY)) OR NOT(CAST(NEW.`qualification_outcome_code` AS BINARY) <=> CAST(OLD.`qualification_outcome_code` AS BINARY)) OR NOT(CAST(NEW.`qualification_role_code` AS BINARY) <=> CAST(OLD.`qualification_role_code` AS BINARY)) OR NOT(CAST(NEW.`qualification_project_version` AS BINARY) <=> CAST(OLD.`qualification_project_version` AS BINARY)) OR NOT(CAST(NEW.`qualification_participant_fact_version` AS BINARY) <=> CAST(OLD.`qualification_participant_fact_version` AS BINARY)) OR NOT(CAST(NEW.`qualification_required_action_code` AS BINARY) <=> CAST(OLD.`qualification_required_action_code` AS BINARY)) OR NOT(CAST(NEW.`qualification_allowed_flag` AS BINARY) <=> CAST(OLD.`qualification_allowed_flag` AS BINARY)) OR NOT(CAST(NEW.`qualification_check_time` AS BINARY) <=> CAST(OLD.`qualification_check_time` AS BINARY)) OR NOT(CAST(NEW.`decision_actor_user_id` AS BINARY) <=> CAST(OLD.`decision_actor_user_id` AS BINARY)) OR NOT(CAST(NEW.`decision_role_id` AS BINARY) <=> CAST(OLD.`decision_role_id` AS BINARY)) OR NOT(CAST(NEW.`decision_membership_version` AS BINARY) <=> CAST(OLD.`decision_membership_version` AS BINARY)) OR NOT(CAST(NEW.`decision_user_status_version` AS BINARY) <=> CAST(OLD.`decision_user_status_version` AS BINARY)) OR NOT(CAST(NEW.`decision_check_time` AS BINARY) <=> CAST(OLD.`decision_check_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_node';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`approval_instance_id` AS BINARY) <=> CAST(OLD.`approval_instance_id` AS BINARY)) OR NOT(CAST(NEW.`node_no` AS BINARY) <=> CAST(OLD.`node_no` AS BINARY)) OR NOT(CAST(NEW.`node_code` AS BINARY) <=> CAST(OLD.`node_code` AS BINARY)) OR NOT(CAST(NEW.`approval_mode_code` AS BINARY) <=> CAST(OLD.`approval_mode_code` AS BINARY)) OR NOT(CAST(NEW.`candidate_role_id` AS BINARY) <=> CAST(OLD.`candidate_role_id` AS BINARY)) OR NOT(CAST(NEW.`candidate_role_code` AS BINARY) <=> CAST(OLD.`candidate_role_code` AS BINARY)) OR NOT(CAST(NEW.`candidate_group_key` AS BINARY) <=> CAST(OLD.`candidate_group_key` AS BINARY)) OR NOT(CAST(NEW.`original_approver_user_id` AS BINARY) <=> CAST(OLD.`original_approver_user_id` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_node';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_52_d` BEFORE DELETE ON `cut_approval_node` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_node';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_53_u` BEFORE UPDATE ON `cut_approval_review_item` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`approval_instance_id` AS BINARY) <=> CAST(OLD.`approval_instance_id` AS BINARY)) OR NOT(CAST(NEW.`approval_node_id` AS BINARY) <=> CAST(OLD.`approval_node_id` AS BINARY)) OR NOT(CAST(NEW.`item_code` AS BINARY) <=> CAST(OLD.`item_code` AS BINARY)) OR NOT(CAST(NEW.`decision_code` AS BINARY) <=> CAST(OLD.`decision_code` AS BINARY)) OR NOT(CAST(NEW.`unreasonable_reason` AS BINARY) <=> CAST(OLD.`unreasonable_reason` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`updater` AS BINARY) <=> CAST(OLD.`updater` AS BINARY)) OR NOT(CAST(NEW.`update_time` AS BINARY) <=> CAST(OLD.`update_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_review_item';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_53_d` BEFORE DELETE ON `cut_approval_review_item` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_review_item';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_54_i` BEFORE INSERT ON `cut_approval_reassignment` FOR EACH ROW
BEGIN
  IF NEW.`candidate_fact_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_54_u` BEFORE UPDATE ON `cut_approval_reassignment` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`candidate_fact_snapshot` AS BINARY) <=> CAST(OLD.`candidate_fact_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_reassignment';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`approval_instance_id` AS BINARY) <=> CAST(OLD.`approval_instance_id` AS BINARY)) OR NOT(CAST(NEW.`approval_node_id` AS BINARY) <=> CAST(OLD.`approval_node_id` AS BINARY)) OR NOT(CAST(NEW.`reassignment_no` AS BINARY) <=> CAST(OLD.`reassignment_no` AS BINARY)) OR NOT(CAST(NEW.`from_approver_user_id` AS BINARY) <=> CAST(OLD.`from_approver_user_id` AS BINARY)) OR NOT(CAST(NEW.`to_approver_user_id` AS BINARY) <=> CAST(OLD.`to_approver_user_id` AS BINARY)) OR NOT(CAST(NEW.`reason` AS BINARY) <=> CAST(OLD.`reason` AS BINARY)) OR NOT(CAST(NEW.`candidate_fact_snapshot` AS BINARY) <=> CAST(OLD.`candidate_fact_snapshot` AS BINARY)) OR NOT(CAST(NEW.`operate_user_id` AS BINARY) <=> CAST(OLD.`operate_user_id` AS BINARY)) OR NOT(CAST(NEW.`operate_time` AS BINARY) <=> CAST(OLD.`operate_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`updater` AS BINARY) <=> CAST(OLD.`updater` AS BINARY)) OR NOT(CAST(NEW.`update_time` AS BINARY) <=> CAST(OLD.`update_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`qualification_kind_code` AS BINARY) <=> CAST(OLD.`qualification_kind_code` AS BINARY)) OR NOT(CAST(NEW.`qualification_outcome_code` AS BINARY) <=> CAST(OLD.`qualification_outcome_code` AS BINARY)) OR NOT(CAST(NEW.`qualification_role_code` AS BINARY) <=> CAST(OLD.`qualification_role_code` AS BINARY)) OR NOT(CAST(NEW.`qualification_project_version` AS BINARY) <=> CAST(OLD.`qualification_project_version` AS BINARY)) OR NOT(CAST(NEW.`qualification_participant_fact_version` AS BINARY) <=> CAST(OLD.`qualification_participant_fact_version` AS BINARY)) OR NOT(CAST(NEW.`qualification_required_action_code` AS BINARY) <=> CAST(OLD.`qualification_required_action_code` AS BINARY)) OR NOT(CAST(NEW.`qualification_allowed_flag` AS BINARY) <=> CAST(OLD.`qualification_allowed_flag` AS BINARY)) OR NOT(CAST(NEW.`qualification_check_time` AS BINARY) <=> CAST(OLD.`qualification_check_time` AS BINARY)) OR NOT(CAST(NEW.`project_scope_version` AS BINARY) <=> CAST(OLD.`project_scope_version` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_reassignment';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_54_d` BEFORE DELETE ON `cut_approval_reassignment` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_reassignment';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_57_u` BEFORE UPDATE ON `cut_approval_result_reference` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`approval_instance_id` AS BINARY) <=> CAST(OLD.`approval_instance_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_item_id` AS BINARY) <=> CAST(OLD.`checklist_item_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_result_id` AS BINARY) <=> CAST(OLD.`checklist_result_id` AS BINARY)) OR NOT(CAST(NEW.`result_version` AS BINARY) <=> CAST(OLD.`result_version` AS BINARY)) OR NOT(CAST(NEW.`required_flag` AS BINARY) <=> CAST(OLD.`required_flag` AS BINARY)) OR NOT(CAST(NEW.`sort_order` AS BINARY) <=> CAST(OLD.`sort_order` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_result_reference';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_57_d` BEFORE DELETE ON `cut_approval_result_reference` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_approval_result_reference';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_58_i` BEFORE INSERT ON `cut_cutover_closure` FOR EACH ROW
BEGIN
  IF NEW.`device_scope_watermark` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_58_u` BEFORE UPDATE ON `cut_cutover_closure` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`device_scope_watermark` AS BINARY) <=> CAST(OLD.`device_scope_watermark` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_closure';
  END IF;
  IF (OLD.status_code='SUBMITTED') AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`project_id` AS BINARY) <=> CAST(OLD.`project_id` AS BINARY)) OR NOT(CAST(NEW.`approval_instance_id` AS BINARY) <=> CAST(OLD.`approval_instance_id` AS BINARY)) OR NOT(CAST(NEW.`approval_version` AS BINARY) <=> CAST(OLD.`approval_version` AS BINARY)) OR NOT(CAST(NEW.`plan_revision_id` AS BINARY) <=> CAST(OLD.`plan_revision_id` AS BINARY)) OR NOT(CAST(NEW.`plan_revision_no` AS BINARY) <=> CAST(OLD.`plan_revision_no` AS BINARY)) OR NOT(CAST(NEW.`plan_version` AS BINARY) <=> CAST(OLD.`plan_version` AS BINARY)) OR NOT(CAST(NEW.`task_version_at_p6` AS BINARY) <=> CAST(OLD.`task_version_at_p6` AS BINARY)) OR NOT(CAST(NEW.`device_scope_watermark` AS BINARY) <=> CAST(OLD.`device_scope_watermark` AS BINARY)) OR NOT(CAST(NEW.`status_code` AS BINARY) <=> CAST(OLD.`status_code` AS BINARY)) OR NOT(CAST(NEW.`pre_check_normal_flag` AS BINARY) <=> CAST(OLD.`pre_check_normal_flag` AS BINARY)) OR NOT(CAST(NEW.`pre_check_detail` AS BINARY) <=> CAST(OLD.`pre_check_detail` AS BINARY)) OR NOT(CAST(NEW.`execution_normal_flag` AS BINARY) <=> CAST(OLD.`execution_normal_flag` AS BINARY)) OR NOT(CAST(NEW.`execution_detail` AS BINARY) <=> CAST(OLD.`execution_detail` AS BINARY)) OR NOT(CAST(NEW.`test_normal_flag` AS BINARY) <=> CAST(OLD.`test_normal_flag` AS BINARY)) OR NOT(CAST(NEW.`test_detail` AS BINARY) <=> CAST(OLD.`test_detail` AS BINARY)) OR NOT(CAST(NEW.`rollback_occurred_flag` AS BINARY) <=> CAST(OLD.`rollback_occurred_flag` AS BINARY)) OR NOT(CAST(NEW.`rollback_successful_flag` AS BINARY) <=> CAST(OLD.`rollback_successful_flag` AS BINARY)) OR NOT(CAST(NEW.`rollback_reason` AS BINARY) <=> CAST(OLD.`rollback_reason` AS BINARY)) OR NOT(CAST(NEW.`legacy_items` AS BINARY) <=> CAST(OLD.`legacy_items` AS BINARY)) OR NOT(CAST(NEW.`final_result_code` AS BINARY) <=> CAST(OLD.`final_result_code` AS BINARY)) OR NOT(CAST(NEW.`result_ref` AS BINARY) <=> CAST(OLD.`result_ref` AS BINARY)) OR NOT(CAST(NEW.`submit_user_id` AS BINARY) <=> CAST(OLD.`submit_user_id` AS BINARY)) OR NOT(CAST(NEW.`submit_time` AS BINARY) <=> CAST(OLD.`submit_time` AS BINARY)) OR NOT(CAST(NEW.`archive_time` AS BINARY) <=> CAST(OLD.`archive_time` AS BINARY)) OR NOT(CAST(NEW.`version` AS BINARY) <=> CAST(OLD.`version` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`updater` AS BINARY) <=> CAST(OLD.`updater` AS BINARY)) OR NOT(CAST(NEW.`update_time` AS BINARY) <=> CAST(OLD.`update_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_closure';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_58_d` BEFORE DELETE ON `cut_cutover_closure` FOR EACH ROW
BEGIN
  IF OLD.status_code='SUBMITTED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_closure';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_59_i` BEFORE INSERT ON `cut_cutover_closure_attachment` FOR EACH ROW
BEGIN
  IF NEW.`file_fact_version` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_closure p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.closure_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_closure_attachment';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_59_u` BEFORE UPDATE ON `cut_cutover_closure_attachment` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`file_fact_version` AS BINARY) <=> CAST(OLD.`file_fact_version` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_closure_attachment';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_closure p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.closure_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_closure_attachment';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_closure p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.closure_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_closure_attachment';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_closure_attachment';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_59_d` BEFORE DELETE ON `cut_cutover_closure_attachment` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_closure p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.closure_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_cutover_closure_attachment';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_60_u` BEFORE UPDATE ON `cut_cutover_collection_evidence` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`closure_id` AS BINARY) <=> CAST(OLD.`closure_id` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`project_id` AS BINARY) <=> CAST(OLD.`project_id` AS BINARY)) OR NOT(CAST(NEW.`device_id` AS BINARY) <=> CAST(OLD.`device_id` AS BINARY)) OR NOT(CAST(NEW.`collection_stage_code` AS BINARY) <=> CAST(OLD.`collection_stage_code` AS BINARY)) OR NOT(CAST(NEW.`evidence_type_code` AS BINARY) <=> CAST(OLD.`evidence_type_code` AS BINARY)) OR NOT(CAST(NEW.`collection_task_id` AS BINARY) <=> CAST(OLD.`collection_task_id` AS BINARY)) OR NOT(CAST(NEW.`callback_event_id` AS BINARY) <=> CAST(OLD.`callback_event_id` AS BINARY)) OR NOT(CAST(NEW.`result_ref` AS BINARY) <=> CAST(OLD.`result_ref` AS BINARY)) OR NOT(CAST(NEW.`result_version` AS BINARY) <=> CAST(OLD.`result_version` AS BINARY)) OR NOT(CAST(NEW.`original_failed_collection_task_id` AS BINARY) <=> CAST(OLD.`original_failed_collection_task_id` AS BINARY)) OR NOT(CAST(NEW.`manual_attachment_id` AS BINARY) <=> CAST(OLD.`manual_attachment_id` AS BINARY)) OR NOT(CAST(NEW.`occur_time` AS BINARY) <=> CAST(OLD.`occur_time` AS BINARY)) OR NOT(CAST(NEW.`record_user_id` AS BINARY) <=> CAST(OLD.`record_user_id` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_collection_evidence';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_60_d` BEFORE DELETE ON `cut_cutover_collection_evidence` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_cutover_collection_evidence';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_61_i` BEFORE INSERT ON `cut_collection_log` FOR EACH ROW
BEGIN
  IF NEW.`result_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_61_u` BEFORE UPDATE ON `cut_collection_log` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`result_snapshot` AS BINARY) <=> CAST(OLD.`result_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_collection_log';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`entry_code` AS BINARY) <=> CAST(OLD.`entry_code` AS BINARY)) OR NOT(CAST(NEW.`object_id` AS BINARY) <=> CAST(OLD.`object_id` AS BINARY)) OR NOT(CAST(NEW.`execution_id` AS BINARY) <=> CAST(OLD.`execution_id` AS BINARY)) OR NOT(CAST(NEW.`platform_task_id` AS BINARY) <=> CAST(OLD.`platform_task_id` AS BINARY)) OR NOT(CAST(NEW.`result_version` AS BINARY) <=> CAST(OLD.`result_version` AS BINARY)) OR NOT(CAST(NEW.`file_version_id` AS BINARY) <=> CAST(OLD.`file_version_id` AS BINARY)) OR NOT(CAST(NEW.`device_id` AS BINARY) <=> CAST(OLD.`device_id` AS BINARY)) OR NOT(CAST(NEW.`result_snapshot` AS BINARY) <=> CAST(OLD.`result_snapshot` AS BINARY)) OR NOT(CAST(NEW.`version` AS BINARY) <=> CAST(OLD.`version` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`updater` AS BINARY) <=> CAST(OLD.`updater` AS BINARY)) OR NOT(CAST(NEW.`update_time` AS BINARY) <=> CAST(OLD.`update_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`source_context_code` AS BINARY) <=> CAST(OLD.`source_context_code` AS BINARY)) OR NOT(CAST(NEW.`source_object_type_code` AS BINARY) <=> CAST(OLD.`source_object_type_code` AS BINARY)) OR NOT(CAST(NEW.`project_id` AS BINARY) <=> CAST(OLD.`project_id` AS BINARY)) OR NOT(CAST(NEW.`actor_user_id` AS BINARY) <=> CAST(OLD.`actor_user_id` AS BINARY)) OR NOT(CAST(NEW.`protocol_code` AS BINARY) <=> CAST(OLD.`protocol_code` AS BINARY)) OR NOT(CAST(NEW.`external_status_code` AS BINARY) <=> CAST(OLD.`external_status_code` AS BINARY)) OR NOT(CAST(NEW.`failure_category_code` AS BINARY) <=> CAST(OLD.`failure_category_code` AS BINARY)) OR NOT(CAST(NEW.`command_text` AS BINARY) <=> CAST(OLD.`command_text` AS BINARY)) OR NOT(CAST(NEW.`template_name` AS BINARY) <=> CAST(OLD.`template_name` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_collection_log';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_61_d` BEFORE DELETE ON `cut_collection_log` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_collection_log';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_62_i` BEFORE INSERT ON `cut_closure_device` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_closure p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.closure_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_closure_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_62_u` BEFORE UPDATE ON `cut_closure_device` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_closure p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.closure_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_closure_device';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_closure p WHERE p.tenant_id=NEW.tenant_id AND p.id=NEW.closure_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_closure_device';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_closure_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_62_d` BEFORE DELETE ON `cut_closure_device` FOR EACH ROW
BEGIN
  IF NOT EXISTS(SELECT 1 FROM cut_cutover_closure p WHERE p.tenant_id=OLD.tenant_id AND p.id=OLD.closure_id AND p.status_code='DRAFT' AND p.deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_PARENT_FROZEN: cut_closure_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_63_i` BEFORE INSERT ON `cut_spare_application_reference` FOR EACH ROW
BEGIN
  IF NEW.`need_snapshot` IS NOT NULL OR NEW.`request_context_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_63_u` BEFORE UPDATE ON `cut_spare_application_reference` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`need_snapshot` AS BINARY) <=> CAST(OLD.`need_snapshot` AS BINARY)) OR NOT(CAST(NEW.`request_context_snapshot` AS BINARY) <=> CAST(OLD.`request_context_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_application_reference';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_64_i` BEFORE INSERT ON `cut_spare_status_revision` FOR EACH ROW
BEGIN
  IF NEW.`status_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_64_u` BEFORE UPDATE ON `cut_spare_status_revision` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`status_snapshot` AS BINARY) <=> CAST(OLD.`status_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_status_revision';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`application_reference_id` AS BINARY) <=> CAST(OLD.`application_reference_id` AS BINARY)) OR NOT(CAST(NEW.`status_version` AS BINARY) <=> CAST(OLD.`status_version` AS BINARY)) OR NOT(CAST(NEW.`external_status_raw` AS BINARY) <=> CAST(OLD.`external_status_raw` AS BINARY)) OR NOT(CAST(NEW.`status_snapshot` AS BINARY) <=> CAST(OLD.`status_snapshot` AS BINARY)) OR NOT(CAST(NEW.`source_type_code` AS BINARY) <=> CAST(OLD.`source_type_code` AS BINARY)) OR NOT(CAST(NEW.`observe_time` AS BINARY) <=> CAST(OLD.`observe_time` AS BINARY)) OR NOT(CAST(NEW.`external_occur_time` AS BINARY) <=> CAST(OLD.`external_occur_time` AS BINARY)) OR NOT(CAST(NEW.`event_id` AS BINARY) <=> CAST(OLD.`event_id` AS BINARY)) OR NOT(CAST(NEW.`correlation_id` AS BINARY) <=> CAST(OLD.`correlation_id` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`source_payload_digest` AS BINARY) <=> CAST(OLD.`source_payload_digest` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_status_revision';
  END IF;
  IF OLD.current_marker IS NULL AND NEW.current_marker IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_SPARE_HISTORY_CANNOT_BECOME_CURRENT';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_64_d` BEFORE DELETE ON `cut_spare_status_revision` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_status_revision';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_65_i` BEFORE INSERT ON `cut_spare_manual_evidence` FOR EACH ROW
BEGIN
  IF NEW.`file_fact_version` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_65_u` BEFORE UPDATE ON `cut_spare_manual_evidence` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`file_fact_version` AS BINARY) <=> CAST(OLD.`file_fact_version` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_manual_evidence';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`application_reference_id` AS BINARY) <=> CAST(OLD.`application_reference_id` AS BINARY)) OR NOT(CAST(NEW.`file_artifact_id` AS BINARY) <=> CAST(OLD.`file_artifact_id` AS BINARY)) OR NOT(CAST(NEW.`file_reference_key` AS BINARY) <=> CAST(OLD.`file_reference_key` AS BINARY)) OR NOT(CAST(NEW.`file_version_no` AS BINARY) <=> CAST(OLD.`file_version_no` AS BINARY)) OR NOT(CAST(NEW.`file_fact_version` AS BINARY) <=> CAST(OLD.`file_fact_version` AS BINARY)) OR NOT(CAST(NEW.`file_scope_version` AS BINARY) <=> CAST(OLD.`file_scope_version` AS BINARY)) OR NOT(CAST(NEW.`description` AS BINARY) <=> CAST(OLD.`description` AS BINARY)) OR NOT(CAST(NEW.`upload_user_id` AS BINARY) <=> CAST(OLD.`upload_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`file_artifact_fact_version` AS BINARY) <=> CAST(OLD.`file_artifact_fact_version` AS BINARY)) OR NOT(CAST(NEW.`file_reference_fact_version` AS BINARY) <=> CAST(OLD.`file_reference_fact_version` AS BINARY)) OR NOT(CAST(NEW.`file_availability_fact_version` AS BINARY) <=> CAST(OLD.`file_availability_fact_version` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_manual_evidence';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_65_d` BEFORE DELETE ON `cut_spare_manual_evidence` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_manual_evidence';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_66_u` BEFORE UPDATE ON `cut_spare_need_source` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`application_reference_id` AS BINARY) <=> CAST(OLD.`application_reference_id` AS BINARY)) OR NOT(CAST(NEW.`source_type_code` AS BINARY) <=> CAST(OLD.`source_type_code` AS BINARY)) OR NOT(CAST(NEW.`assessment_id` AS BINARY) <=> CAST(OLD.`assessment_id` AS BINARY)) OR NOT(CAST(NEW.`assessment_version` AS BINARY) <=> CAST(OLD.`assessment_version` AS BINARY)) OR NOT(CAST(NEW.`checklist_item_id` AS BINARY) <=> CAST(OLD.`checklist_item_id` AS BINARY)) OR NOT(CAST(NEW.`checklist_item_version` AS BINARY) <=> CAST(OLD.`checklist_item_version` AS BINARY)) OR NOT(CAST(NEW.`spare_part_applied_flag` AS BINARY) <=> CAST(OLD.`spare_part_applied_flag` AS BINARY)) OR NOT(CAST(NEW.`applicable_flag` AS BINARY) <=> CAST(OLD.`applicable_flag` AS BINARY)) OR NOT(CAST(NEW.`sort_order` AS BINARY) <=> CAST(OLD.`sort_order` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_need_source';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_66_d` BEFORE DELETE ON `cut_spare_need_source` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_need_source';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_67_u` BEFORE UPDATE ON `cut_spare_request_device` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`application_reference_id` AS BINARY) <=> CAST(OLD.`application_reference_id` AS BINARY)) OR NOT(CAST(NEW.`device_id` AS BINARY) <=> CAST(OLD.`device_id` AS BINARY)) OR NOT(CAST(NEW.`sn` AS BINARY) <=> CAST(OLD.`sn` AS BINARY)) OR NOT(CAST(NEW.`project_assignment_version` AS BINARY) <=> CAST(OLD.`project_assignment_version` AS BINARY)) OR NOT(CAST(NEW.`sort_order` AS BINARY) <=> CAST(OLD.`sort_order` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_request_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_67_d` BEFORE DELETE ON `cut_spare_request_device` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_spare_request_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_68_i` BEFORE INSERT ON `imp_cutover_readiness_snapshot` FOR EACH ROW
BEGIN
  IF NEW.`context_json` IS NOT NULL OR NEW.`unmet_codes_json` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_68_u` BEFORE UPDATE ON `imp_cutover_readiness_snapshot` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`context_json` AS BINARY) <=> CAST(OLD.`context_json` AS BINARY)) OR NOT(CAST(NEW.`unmet_codes_json` AS BINARY) <=> CAST(OLD.`unmet_codes_json` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_snapshot';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`project_id` AS BINARY) <=> CAST(OLD.`project_id` AS BINARY)) OR NOT(CAST(NEW.`device_scope_key` AS BINARY) <=> CAST(OLD.`device_scope_key` AS BINARY)) OR NOT(CAST(NEW.`snapshot_no` AS BINARY) <=> CAST(OLD.`snapshot_no` AS BINARY)) OR NOT(CAST(NEW.`readiness_decision_code` AS BINARY) <=> CAST(OLD.`readiness_decision_code` AS BINARY)) OR NOT(CAST(NEW.`context_json` AS BINARY) <=> CAST(OLD.`context_json` AS BINARY)) OR NOT(CAST(NEW.`unmet_codes_json` AS BINARY) <=> CAST(OLD.`unmet_codes_json` AS BINARY)) OR NOT(CAST(NEW.`evaluate_user_id` AS BINARY) <=> CAST(OLD.`evaluate_user_id` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`updater` AS BINARY) <=> CAST(OLD.`updater` AS BINARY)) OR NOT(CAST(NEW.`update_time` AS BINARY) <=> CAST(OLD.`update_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`project_scope_version` AS BINARY) <=> CAST(OLD.`project_scope_version` AS BINARY)) OR NOT(CAST(NEW.`approved_plan_id` AS BINARY) <=> CAST(OLD.`approved_plan_id` AS BINARY)) OR NOT(CAST(NEW.`approved_plan_version` AS BINARY) <=> CAST(OLD.`approved_plan_version` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_snapshot';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_68_d` BEFORE DELETE ON `imp_cutover_readiness_snapshot` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_snapshot';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_69_u` BEFORE UPDATE ON `imp_cutover_readiness_device` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`snapshot_id` AS BINARY) <=> CAST(OLD.`snapshot_id` AS BINARY)) OR NOT(CAST(NEW.`device_id` AS BINARY) <=> CAST(OLD.`device_id` AS BINARY)) OR NOT(CAST(NEW.`sn` AS BINARY) <=> CAST(OLD.`sn` AS BINARY)) OR NOT(CAST(NEW.`project_assignment_version` AS BINARY) <=> CAST(OLD.`project_assignment_version` AS BINARY)) OR NOT(CAST(NEW.`sort_order` AS BINARY) <=> CAST(OLD.`sort_order` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_69_d` BEFORE DELETE ON `imp_cutover_readiness_device` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_device';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_70_u` BEFORE UPDATE ON `imp_cutover_readiness_source` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`snapshot_id` AS BINARY) <=> CAST(OLD.`snapshot_id` AS BINARY)) OR NOT(CAST(NEW.`source_code` AS BINARY) <=> CAST(OLD.`source_code` AS BINARY)) OR NOT(CAST(NEW.`completion_status_code` AS BINARY) <=> CAST(OLD.`completion_status_code` AS BINARY)) OR NOT(CAST(NEW.`fact_version` AS BINARY) <=> CAST(OLD.`fact_version` AS BINARY)) OR NOT(CAST(NEW.`reopened_flag` AS BINARY) <=> CAST(OLD.`reopened_flag` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_70_d` BEFORE DELETE ON `imp_cutover_readiness_source` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_71_u` BEFORE UPDATE ON `imp_cutover_readiness_source_object` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`source_fact_id` AS BINARY) <=> CAST(OLD.`source_fact_id` AS BINARY)) OR NOT(CAST(NEW.`source_object_id` AS BINARY) <=> CAST(OLD.`source_object_id` AS BINARY)) OR NOT(CAST(NEW.`sort_order` AS BINARY) <=> CAST(OLD.`sort_order` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source_object';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_71_d` BEFORE DELETE ON `imp_cutover_readiness_source_object` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source_object';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_72_u` BEFORE UPDATE ON `imp_cutover_readiness_source_watermark` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`source_fact_id` AS BINARY) <=> CAST(OLD.`source_fact_id` AS BINARY)) OR NOT(CAST(NEW.`axis_code` AS BINARY) <=> CAST(OLD.`axis_code` AS BINARY)) OR NOT(CAST(NEW.`object_id` AS BINARY) <=> CAST(OLD.`object_id` AS BINARY)) OR NOT(CAST(NEW.`fact_version` AS BINARY) <=> CAST(OLD.`fact_version` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source_watermark';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_72_d` BEFORE DELETE ON `imp_cutover_readiness_source_watermark` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_source_watermark';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_73_u` BEFORE UPDATE ON `imp_cutover_readiness_unmet` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`snapshot_id` AS BINARY) <=> CAST(OLD.`snapshot_id` AS BINARY)) OR NOT(CAST(NEW.`unmet_code` AS BINARY) <=> CAST(OLD.`unmet_code` AS BINARY)) OR NOT(CAST(NEW.`sort_order` AS BINARY) <=> CAST(OLD.`sort_order` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_unmet';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_73_d` BEFORE DELETE ON `imp_cutover_readiness_unmet` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: imp_cutover_readiness_unmet';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_74_i` BEFORE INSERT ON `ast_device_cutover_result` FOR EACH ROW
BEGIN
  IF NEW.`result_snapshot` IS NOT NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_LEGACY_ORIGINAL_READ_ONLY';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_74_u` BEFORE UPDATE ON `ast_device_cutover_result` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`result_snapshot` AS BINARY) <=> CAST(OLD.`result_snapshot` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: ast_device_cutover_result';
  END IF;
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`project_id` AS BINARY) <=> CAST(OLD.`project_id` AS BINARY)) OR NOT(CAST(NEW.`device_id` AS BINARY) <=> CAST(OLD.`device_id` AS BINARY)) OR NOT(CAST(NEW.`sn` AS BINARY) <=> CAST(OLD.`sn` AS BINARY)) OR NOT(CAST(NEW.`assignment_version` AS BINARY) <=> CAST(OLD.`assignment_version` AS BINARY)) OR NOT(CAST(NEW.`task_id` AS BINARY) <=> CAST(OLD.`task_id` AS BINARY)) OR NOT(CAST(NEW.`closure_id` AS BINARY) <=> CAST(OLD.`closure_id` AS BINARY)) OR NOT(CAST(NEW.`closure_version` AS BINARY) <=> CAST(OLD.`closure_version` AS BINARY)) OR NOT(CAST(NEW.`event_id` AS BINARY) <=> CAST(OLD.`event_id` AS BINARY)) OR NOT(CAST(NEW.`result_ref` AS BINARY) <=> CAST(OLD.`result_ref` AS BINARY)) OR NOT(CAST(NEW.`cutover_time` AS BINARY) <=> CAST(OLD.`cutover_time` AS BINARY)) OR NOT(CAST(NEW.`refresh_status_code` AS BINARY) <=> CAST(OLD.`refresh_status_code` AS BINARY)) OR NOT(CAST(NEW.`result_snapshot` AS BINARY) <=> CAST(OLD.`result_snapshot` AS BINARY)) OR NOT(CAST(NEW.`creator` AS BINARY) <=> CAST(OLD.`creator` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY)) OR NOT(CAST(NEW.`updater` AS BINARY) <=> CAST(OLD.`updater` AS BINARY)) OR NOT(CAST(NEW.`update_time` AS BINARY) <=> CAST(OLD.`update_time` AS BINARY)) OR NOT(CAST(NEW.`deleted` AS BINARY) <=> CAST(OLD.`deleted` AS BINARY)) OR NOT(CAST(NEW.`task_no` AS BINARY) <=> CAST(OLD.`task_no` AS BINARY)) OR NOT(CAST(NEW.`task_name` AS BINARY) <=> CAST(OLD.`task_name` AS BINARY)) OR NOT(CAST(NEW.`submit_user_id` AS BINARY) <=> CAST(OLD.`submit_user_id` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: ast_device_cutover_result';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_74_d` BEFORE DELETE ON `ast_device_cutover_result` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: ast_device_cutover_result';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_75_u` BEFORE UPDATE ON `ast_device_cutover_result_file` FOR EACH ROW
BEGIN
  IF (TRUE) AND (NOT(CAST(NEW.`id` AS BINARY) <=> CAST(OLD.`id` AS BINARY)) OR NOT(CAST(NEW.`tenant_id` AS BINARY) <=> CAST(OLD.`tenant_id` AS BINARY)) OR NOT(CAST(NEW.`device_cutover_result_id` AS BINARY) <=> CAST(OLD.`device_cutover_result_id` AS BINARY)) OR NOT(CAST(NEW.`file_source_code` AS BINARY) <=> CAST(OLD.`file_source_code` AS BINARY)) OR NOT(CAST(NEW.`collection_stage_code` AS BINARY) <=> CAST(OLD.`collection_stage_code` AS BINARY)) OR NOT(CAST(NEW.`actor_user_id` AS BINARY) <=> CAST(OLD.`actor_user_id` AS BINARY)) OR NOT(CAST(NEW.`platform_task_id` AS BINARY) <=> CAST(OLD.`platform_task_id` AS BINARY)) OR NOT(CAST(NEW.`result_version` AS BINARY) <=> CAST(OLD.`result_version` AS BINARY)) OR NOT(CAST(NEW.`file_version_id` AS BINARY) <=> CAST(OLD.`file_version_id` AS BINARY)) OR NOT(CAST(NEW.`file_artifact_id` AS BINARY) <=> CAST(OLD.`file_artifact_id` AS BINARY)) OR NOT(CAST(NEW.`file_version_no` AS BINARY) <=> CAST(OLD.`file_version_no` AS BINARY)) OR NOT(CAST(NEW.`file_reference_key` AS BINARY) <=> CAST(OLD.`file_reference_key` AS BINARY)) OR NOT(CAST(NEW.`file_sha256` AS BINARY) <=> CAST(OLD.`file_sha256` AS BINARY)) OR NOT(CAST(NEW.`sort_order` AS BINARY) <=> CAST(OLD.`sort_order` AS BINARY)) OR NOT(CAST(NEW.`create_user_id` AS BINARY) <=> CAST(OLD.`create_user_id` AS BINARY)) OR NOT(CAST(NEW.`create_time` AS BINARY) <=> CAST(OLD.`create_time` AS BINARY))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: ast_device_cutover_result_file';
  END IF;
END$$

CREATE TRIGGER `trg_cut_structured_75_d` BEFORE DELETE ON `ast_device_cutover_result_file` FOR EACH ROW
BEGIN
  IF TRUE THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: ast_device_cutover_result_file';
  END IF;
END$$

DELIMITER ;
