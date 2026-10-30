-- 2026-10-15 已批准的前序修订与负责人撤回；不回写已提交内容或批准事实。
-- INVALIDATED 允许记录被前序修订替代的未提交草稿；其提交人、审批引用保持 NULL。
ALTER TABLE cut_cutover_checklist
  ADD COLUMN matching_project_version BIGINT NULL COMMENT '匹配项目主数据维度时锁定的项目版本；历史未消费时为空',
  DROP CHECK chk_cut_checklist_submit,
  ADD CONSTRAINT chk_cut_checklist_submit CHECK (COALESCE(
    (status_code='DRAFT' AND submit_user_id IS NULL AND submit_time IS NULL)
    OR (status_code='SUBMITTED' AND submit_user_id>0 AND submit_time IS NOT NULL)
    OR (status_code='INVALIDATED' AND
      ((submit_user_id>0 AND submit_time IS NOT NULL) OR (submit_user_id IS NULL AND submit_time IS NULL))), FALSE)=TRUE);

ALTER TABLE cut_plan_revision
  DROP CHECK chk_cut_plan_derivation,
  ADD CONSTRAINT chk_cut_plan_derivation CHECK (
    (source_plan_revision_id IS NULL AND revision_reason_code IS NULL)
    OR COALESCE(source_plan_revision_id>0 AND revision_reason_code IN ('APPROVAL_REJECTED','DUTY_CHANGED','SOURCE_REPLACED','USER_REVISION'),FALSE));

-- 已执行专项收敛与只执行 V476 的库具有不同历史分支；仅扩展失效草稿子句，其他约束原样保留。
DELIMITER $$
CREATE PROCEDURE cut478_extend_plan_constraint()
BEGIN
  DECLARE original_clause LONGTEXT;
  DECLARE old_part LONGTEXT;
  DECLARE new_part LONGTEXT;
  SELECT cc.CHECK_CLAUSE INTO original_clause
  FROM information_schema.CHECK_CONSTRAINTS cc
  JOIN information_schema.TABLE_CONSTRAINTS tc
    ON tc.CONSTRAINT_SCHEMA=cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME=cc.CONSTRAINT_NAME
  WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='cut_plan_revision' AND tc.CONSTRAINT_NAME='chk_cut_plan_union';
  -- MySQL 8.4 CHECK_CLAUSE 对 SQL 字符串引号返回反斜杠转义，恢复为可执行的原表达式。
  SET original_clause=REPLACE(original_clause,CONCAT(CHAR(92),CHAR(39)),CHAR(39));
  SET old_part='((`status_code` = _utf8mb4''INVALIDATED'') and (`current_marker` is null) and (`submit_user_id` > 0) and (`submit_time` is not null) and (`approval_instance_id` > 0) and (`approval_version` >= 0)';
  SET new_part='((`status_code` = _utf8mb4''INVALIDATED'') and (`current_marker` is null) and (((`submit_user_id` > 0) and (`submit_time` is not null) and (`approval_instance_id` > 0) and (`approval_version` >= 0)) OR ((`submit_user_id` IS NULL) and (`submit_time` IS NULL) and (`approval_instance_id` IS NULL) and (`approval_version` IS NULL) and (`invalidation_reason_code` = ''USER_REVISION'')))';
  IF original_clause IS NULL OR LOCATE(old_part,original_clause)=0
     OR LOCATE(old_part,original_clause,LOCATE(old_part,original_clause)+CHAR_LENGTH(old_part))>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CUT478_PLAN_CONSTRAINT_SHAPE_UNEXPECTED';
  END IF;
  SET @cut478_plan_ddl=CONCAT('ALTER TABLE cut_plan_revision DROP CHECK chk_cut_plan_union, ADD CONSTRAINT chk_cut_plan_union CHECK (',REPLACE(original_clause,old_part,new_part),')');
  PREPARE cut478_plan_statement FROM @cut478_plan_ddl;
  EXECUTE cut478_plan_statement;
  DEALLOCATE PREPARE cut478_plan_statement;
  SET @cut478_plan_ddl=NULL;
END$$
CALL cut478_extend_plan_constraint()$$
DROP PROCEDURE cut478_extend_plan_constraint$$
DELIMITER ;

ALTER TABLE cut_approval_instance
  ADD COLUMN withdraw_user_id BIGINT NULL COMMENT '任务负责人撤回身份',
  ADD COLUMN withdraw_time DATETIME(3) NULL COMMENT '负责人撤回时间',
  ADD COLUMN withdraw_reason VARCHAR(1000) NULL COMMENT '负责人撤回原因',
  DROP CHECK chk_cut_approval_status,
  DROP CHECK chk_cut_approval_decision,
  ADD CONSTRAINT chk_cut_approval_status CHECK (status_code IN ('PENDING','PAUSED_SOURCE_INVALIDATED','WITHDRAWN','APPROVED','REJECTED')),
  ADD CONSTRAINT chk_cut_approval_decision CHECK (COALESCE(
    (status_code='APPROVED' AND decide_time IS NOT NULL AND rejection_reason IS NULL)
    OR (status_code='REJECTED' AND decide_time IS NOT NULL AND CHAR_LENGTH(TRIM(rejection_reason)) BETWEEN 1 AND 1000)
    OR (status_code IN ('PENDING','PAUSED_SOURCE_INVALIDATED','WITHDRAWN') AND decide_time IS NULL AND rejection_reason IS NULL),FALSE)=TRUE),
  ADD CONSTRAINT chk_cut_approval_withdrawal CHECK (COALESCE(
    (status_code='WITHDRAWN' AND withdraw_user_id>0 AND withdraw_time IS NOT NULL AND CHAR_LENGTH(TRIM(withdraw_reason)) BETWEEN 1 AND 1000)
    OR (status_code<>'WITHDRAWN' AND withdraw_user_id IS NULL AND withdraw_time IS NULL AND withdraw_reason IS NULL),FALSE)=TRUE);

ALTER TABLE cut_task_stage_history
  DROP CHECK chk_cut_stage_trigger,
  ADD CONSTRAINT chk_cut_stage_trigger CHECK (COALESCE(
    (trigger_type='P1_ACCEPTED' AND from_stage_code='P1' AND to_stage_code='P2' AND to_status_code='GRADE_CONFIRMING')
    OR (trigger_type='P2_ASSESSMENT_SUBMITTED' AND from_stage_code='P2' AND ((to_stage_code='P3' AND to_status_code='SURVEYING') OR (to_stage_code='P4' AND to_status_code='PLAN_DRAFTING')))
    OR (trigger_type='P2_ASSESSMENT_INVALIDATED' AND from_stage_code IN ('P3','P4') AND to_stage_code='P2' AND to_status_code='GRADE_CONFIRMING')
    OR (trigger_type='P3_CHECKLIST_SUBMITTED' AND from_stage_code='P3' AND to_stage_code='P4' AND to_status_code='PLAN_DRAFTING')
    OR (trigger_type='P4_PLAN_SUBMITTED' AND from_stage_code='P4' AND from_status_code='PLAN_DRAFTING' AND to_stage_code='P5' AND to_status_code='APPROVING')
    OR (trigger_type IN ('P5_SOURCE_INVALIDATED','P5_APPROVAL_REJECTED') AND from_stage_code='P5' AND from_status_code='APPROVING' AND to_stage_code='P4' AND to_status_code='PLAN_DRAFTING')
    OR (trigger_type='P5_APPROVAL_APPROVED' AND from_stage_code='P5' AND from_status_code='APPROVING' AND to_stage_code='P6' AND to_status_code='CLOSURE_IN_PROGRESS')
    OR (trigger_type='P6_CLOSURE_SUBMITTED' AND from_stage_code='P6' AND from_status_code='CLOSURE_IN_PROGRESS' AND to_stage_code='P6' AND to_status_code='ARCHIVED')
    OR (trigger_type='TASK_REVISION' AND
      ((from_stage_code='P3' AND from_status_code='SURVEYING' AND to_stage_code='P2' AND to_status_code='GRADE_CONFIRMING')
       OR (from_stage_code='P4' AND from_status_code='PLAN_DRAFTING' AND ((to_stage_code='P2' AND to_status_code='GRADE_CONFIRMING') OR (to_stage_code='P3' AND to_status_code='SURVEYING')))
       OR (from_stage_code='P5' AND from_status_code='APPROVING' AND ((to_stage_code='P2' AND to_status_code='GRADE_CONFIRMING') OR (to_stage_code='P3' AND to_status_code='SURVEYING') OR (to_stage_code='P4' AND to_status_code='PLAN_DRAFTING'))))),FALSE)=TRUE);
