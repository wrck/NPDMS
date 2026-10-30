-- 创建时仅冻结项目、设备、配置及评估身份，类型/背景在后续既有结构化入口填写。
-- 保留原Owner、状态组合、来源、就绪及项目版本约束，既有业务数据和历史不回写。
ALTER TABLE cut_task DROP CHECK chk_cut_task_origin_union,
  ADD CONSTRAINT chk_cut_task_origin_union CHECK ((coalesce(((`task_origin_code` = _utf8mb4'NEW_PLATFORM') and (`owner_user_id` > 0) and ((`customer_id` is null) or (`customer_id` > 0)) and ((`background` is null) or (char_length(trim(`background`)) between 1 and 4000)) and ((`cutover_type_code` is null) or (char_length(trim(`cutover_type_code`)) between 1 and 32)) and (`configuration_revision_id` > 0) and (char_length(trim(`configuration_code`)) between 1 and 64) and (`configuration_revision_no` > 0) and (((`current_stage_code` = _utf8mb4'P2') and (`task_status_code` = _utf8mb4'GRADE_CONFIRMING')) or ((`current_stage_code` = _utf8mb4'P3') and (`task_status_code` = _utf8mb4'SURVEYING')) or ((`current_stage_code` = _utf8mb4'P4') and (`task_status_code` = _utf8mb4'PLAN_DRAFTING')) or ((`current_stage_code` = _utf8mb4'P5') and (`task_status_code` = _utf8mb4'APPROVING')) or ((`current_stage_code` = _utf8mb4'P6') and (`task_status_code` in (_utf8mb4'CLOSURE_IN_PROGRESS',_utf8mb4'ARCHIVED')))) and (((`implementation_readiness_snapshot_id` > 0) and (`implementation_readiness_snapshot_version` >= 0)) or ((`intake_source_type_code` in (_utf8mb4'SELF_CREATED',_utf8mb4'ITR')) and (`implementation_readiness_snapshot_id` is null) and (`implementation_readiness_snapshot_version` is null) and (`readiness_decision_code` = _utf8mb4'NOT_APPLICABLE'))) and (`project_scope_version` >= 0) and (`project_version` is not null) and (`project_scope_version` is not null) and (`customer_fact_status_code` is not null) and (`readiness_decision_code` is not null)),false) = true));

-- 全部方案模式共用单个计划割接时间，不再依赖任务创建时填写。
ALTER TABLE cut_plan_revision ADD COLUMN scheduled_time DATETIME(3) NULL COMMENT '方案页单个计划割接时间';

-- 已有正文、批准版本和任务计划时间不回写；用户在草稿方案页明确填写后才产生新正文版本。
DELIMITER $$
CREATE TRIGGER trg_cut_plan_scheduled_time_immutable BEFORE UPDATE ON cut_plan_revision FOR EACH ROW
BEGIN
  IF OLD.status_code <> 'DRAFT' AND NOT (NEW.scheduled_time <=> OLD.scheduled_time) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CUT_IMMUTABLE_HISTORY: cut_plan_revision.scheduled_time';
  END IF;
END$$
DELIMITER ;
