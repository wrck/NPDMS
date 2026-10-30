-- ============================================================================
-- V311: 下线已被新实现替代的旧 pms 页面菜单入口（codex/domain-migration 退役专项）
--
-- 背景：pms_* 旧域已随 V310 更名 *_retired 并冻结只读（INSERT/UPDATE/DELETE 被触发器拒绝）。
-- 旧页面中已有新等价页面承载业务的，其菜单入口统一下线（status=1 停用）；
-- 页面代码与其专用 API 保留冻结（与旧项目详情界面同等待遇，仅存量只读）。
--
-- 下线清单（均有已挂菜单的新实现页面）：
--   18011  project          旧项目列表   -> 18067 projects / 18071 project-master-detail
--   18013  project-team     旧项目团队   -> 项目详情成员面板 / 统一成员（proj_project_member_assignment）
--   18014  project-task     旧项目任务   -> 项目详情任务面板 / 任务维护（project-tasks 业务API）
--   18016  project-phase    旧项目阶段   -> 项目详情阶段面板（stages/{stageCode}/business）
--   18018  project-panoramic 旧项目全景  -> 项目详情进度/执行历史（projects/{id}/node-executions）
--   19019  cut-task         旧切换任务   -> 992602040001 cutover-task（cut_task 新载体）
--   19021  cut-plan         旧切换计划   -> cutover-task 内嵌方案（cut_plan_revision/cut_step）
--   19095  acceptance       旧V17验收单  -> 930920 acceptance-reports（验收活动/报告版本制）
--
-- 保留只读入口（无新实现载体，读旧 *_retired 表仍可用，写被数据库拒绝）：
--   18017 project-risk、19020 cut-risk、19024~19027 srv-task/srv-rule/srv-report/srv-issue、
--   19151 plan-change、18020 project-detail（旧项目详情界面，退役决策明确豁免）。
-- ============================================================================

UPDATE system_menu SET status = 1
WHERE id IN (18011, 18013, 18014, 18016, 18018, 19019, 19021, 19095)
  AND deleted = 0;
