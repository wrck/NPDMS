-- 3.1 施工计划去占比化（Demo 页面9）：各阶段计划起止为逐行直接输入，
-- 阶段配置建议为空时草稿明细计划时间保持为空，提交前由用户补齐。
-- V304 将 plan_start/plan_end 定义为 NOT NULL，建立在占比推算恒有日期的旧前提上；
-- 该前提已随「取消项目工期占比」授权需求失效，放开为可空。
ALTER TABLE sol_stage_plan_item
  MODIFY COLUMN plan_start date DEFAULT NULL COMMENT '计划开始时间（草稿可空，提交时必填）',
  MODIFY COLUMN plan_end date DEFAULT NULL COMMENT '计划结束时间（草稿可空，提交时必填）';
