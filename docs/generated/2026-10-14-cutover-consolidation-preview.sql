-- PREVIEW ONLY：未执行，不在 Flyway 自动迁移目录中。
-- 来源：2026-10-14 用户批准的割接模块收敛清单及当日本地只读复核。
-- 唯一目标：npdms-domain-test-mysql-1 / 127.0.0.1:24306 / npdms_domain_test。
-- 必须先取得本文件数据处置授权；59191 停机/重启另行授权。
-- 在 59191 按批准停止、Quartz 不再运行后执行；48182 无需随之停机。
-- DDL 会隐式提交，不能用事务回滚恢复退役数据。旧模型数据按用户要求删除。
-- 原 Flyway 历史脚本、现行业务历史/快照/审批/配置/审计及任务日志均不改写。

USE npdms_domain_test;

-- A. 执行前只读复核。数量、映射、关联或目标身份变化时中止并刷新预览。
SELECT DATABASE() AS target_database;
SELECT 'pms_cut_task_retired' AS table_name, COUNT(*) AS expected_36 FROM pms_cut_task_retired
UNION ALL SELECT 'pms_cut_plan_retired', COUNT(*) FROM pms_cut_plan_retired
UNION ALL SELECT 'pms_cut_risk_retired', COUNT(*) FROM pms_cut_risk_retired
UNION ALL SELECT 'pms_cut_execution_retired', COUNT(*) FROM pms_cut_execution_retired
UNION ALL SELECT 'pms_cut_observation_retired', COUNT(*) FROM pms_cut_observation_retired;
-- 预期依次为 36、30、30、35、34，共 165。
SELECT COUNT(*) AS expected_0 FROM cut_task
WHERE legacy_task_id IS NOT NULL OR legacy_cutover_type_raw IS NOT NULL
   OR legacy_network_mode_raw IS NOT NULL OR legacy_status_value IS NOT NULL
   OR legacy_source_version IS NOT NULL OR legacy_mapping_version IS NOT NULL;
SELECT COUNT(*) AS expected_0 FROM cut_plan_revision
WHERE legacy_plan_id IS NOT NULL OR legacy_status_raw IS NOT NULL
   OR legacy_source_version IS NOT NULL OR legacy_mapping_version IS NOT NULL
   OR legacy_task_id IS NOT NULL OR legacy_code IS NOT NULL OR legacy_name IS NOT NULL
   OR legacy_grade_code IS NOT NULL OR legacy_remark IS NOT NULL;
SELECT COUNT(*) AS expected_0 FROM cut_task WHERE task_origin_code <> 'NEW_PLATFORM';
SELECT COUNT(*) AS expected_0 FROM cut_plan_revision WHERE origin_code <> 'NEW_PLATFORM';
SELECT table_name, column_name, constraint_name, referenced_table_name
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE() AND referenced_table_name IS NOT NULL
  AND (table_name IN ('pms_cut_task_retired','pms_cut_plan_retired','pms_cut_risk_retired',
                     'pms_cut_execution_retired','pms_cut_observation_retired')
    OR referenced_table_name IN ('pms_cut_task_retired','pms_cut_plan_retired','pms_cut_risk_retired',
                                'pms_cut_execution_retired','pms_cut_observation_retired'));
-- 预期无外键记录。
SELECT id,name,parent_id,path,component,status,deleted FROM system_menu
WHERE id IN (19019,19020,19021,199500,970445001,970445002,992602050001)
   OR parent_id IN (19019,19020,19021,199500,992602050001);
SELECT id,name,handler_name,status,deleted FROM infra_job
WHERE id IN (970445201,970445202,992602010004,992602010006,992602073001,992602073002);
SELECT sched_name,trigger_name,trigger_group,job_name,job_group,trigger_state
FROM QRTZ_TRIGGERS
WHERE job_name IN ('cutoverRuntimeApprovalNotificationJob','cutoverApprovalNotificationJob',
                   'legacyCutoverPlanReconciliationJob','legacyCutoverClosureReconciliationJob');
SELECT COUNT(*) AS expected_0_after_approved_stop FROM QRTZ_FIRED_TRIGGERS
WHERE sched_name = 'schedulerName' AND job_group = 'DEFAULT'
  AND job_name IN ('cutoverRuntimeApprovalNotificationJob','cutoverApprovalNotificationJob',
                   'legacyCutoverPlanReconciliationJob','legacyCutoverClosureReconciliationJob');

-- B. 菜单及调度配置，同一事务提交。
START TRANSACTION;

-- 复用任务菜单 ID；现有 15 个任务按钮及其授权关系原样保留。
UPDATE system_menu
SET name='割接任务列表', path='tasks', component='pms/cutover/tasks/index',
    component_name='PmsCutoverTaskList', updater='cutover-consolidation', update_time=NOW()
WHERE id=992602050001 AND deleted=b'0' AND parent_id=19263
  AND path='cutover-task' AND component='pms/cutover/cutover-task/index';
-- 预期 1 行。

-- 配置按钮迁入现有配置中心，保留按钮 ID、权限字符串和角色关联。
UPDATE system_menu
SET parent_id=970445002, updater='cutover-consolidation', update_time=NOW()
WHERE id IN (199501,199502,199503,199504) AND parent_id=199500 AND deleted=b'0';
-- 预期 4 行。

-- 旧模型 3 个菜单+11 个按钮，以及旧配置父菜单下线；不动现行权限。
UPDATE system_role_menu
SET deleted=b'1', updater='cutover-consolidation', update_time=NOW()
WHERE menu_id IN (19019,19020,19021,19062,19063,19064,19065,19066,19067,
                  19068,19069,19070,19071,19072,199500) AND deleted=b'0';
-- 预期 14 条有效旧授权下线；旧配置父菜单当前没有显式角色关联。
UPDATE system_menu
SET status=1, visible=b'0', deleted=b'1', updater='cutover-consolidation', update_time=NOW()
WHERE id IN (19019,19020,19021,19062,19063,19064,19065,19066,19067,
             19068,19069,19070,19071,19072,199500) AND deleted=b'0';
-- 预期 15 行。

-- 退役 2 条旧模型核对调度和 1 条已暂停的重复站内通知调度。
UPDATE infra_job
SET status=2, deleted=b'1', updater='cutover-consolidation', update_time=NOW()
WHERE id IN (992602010004,992602010006,992602073001) AND status=2 AND deleted=b'0';
-- 预期 3 行；保留 infra_job_log 原始历史。
UPDATE infra_job
SET handler_name='cutoverApprovalNotificationJob', updater='cutover-consolidation', update_time=NOW()
WHERE id=970445202 AND handler_name='cutoverRuntimeApprovalNotificationJob'
  AND status=1 AND deleted=b'0';
-- 预期 1 行；保留现有启用状态和 cron。
-- cutoverResultDeliveryJob(970445201)保持启用；外部提醒(992602073002)保持暂停。

-- 清除旧 handler 的 Quartz 执行配置。当前仅 Runtime 通知有 1 trigger+1 cron+1 job。
-- 不修改 scheduler 状态表，也不删除任何运行中执行记录。
DELETE c FROM QRTZ_CRON_TRIGGERS c JOIN QRTZ_TRIGGERS t
  ON c.sched_name=t.sched_name AND c.trigger_name=t.trigger_name AND c.trigger_group=t.trigger_group
WHERE t.sched_name='schedulerName' AND t.job_group='DEFAULT'
  AND t.job_name IN ('cutoverRuntimeApprovalNotificationJob','legacyCutoverPlanReconciliationJob','legacyCutoverClosureReconciliationJob');
DELETE c FROM QRTZ_SIMPLE_TRIGGERS c JOIN QRTZ_TRIGGERS t
  ON c.sched_name=t.sched_name AND c.trigger_name=t.trigger_name AND c.trigger_group=t.trigger_group
WHERE t.sched_name='schedulerName' AND t.job_group='DEFAULT'
  AND t.job_name IN ('cutoverRuntimeApprovalNotificationJob','legacyCutoverPlanReconciliationJob','legacyCutoverClosureReconciliationJob');
DELETE c FROM QRTZ_SIMPROP_TRIGGERS c JOIN QRTZ_TRIGGERS t
  ON c.sched_name=t.sched_name AND c.trigger_name=t.trigger_name AND c.trigger_group=t.trigger_group
WHERE t.sched_name='schedulerName' AND t.job_group='DEFAULT'
  AND t.job_name IN ('cutoverRuntimeApprovalNotificationJob','legacyCutoverPlanReconciliationJob','legacyCutoverClosureReconciliationJob');
DELETE c FROM QRTZ_BLOB_TRIGGERS c JOIN QRTZ_TRIGGERS t
  ON c.sched_name=t.sched_name AND c.trigger_name=t.trigger_name AND c.trigger_group=t.trigger_group
WHERE t.sched_name='schedulerName' AND t.job_group='DEFAULT'
  AND t.job_name IN ('cutoverRuntimeApprovalNotificationJob','legacyCutoverPlanReconciliationJob','legacyCutoverClosureReconciliationJob');
DELETE FROM QRTZ_TRIGGERS
WHERE sched_name='schedulerName' AND job_group='DEFAULT'
  AND job_name IN ('cutoverRuntimeApprovalNotificationJob','legacyCutoverPlanReconciliationJob','legacyCutoverClosureReconciliationJob');
DELETE FROM QRTZ_JOB_DETAILS
WHERE sched_name='schedulerName' AND job_group='DEFAULT'
  AND job_name IN ('cutoverRuntimeApprovalNotificationJob','legacyCutoverPlanReconciliationJob','legacyCutoverClosureReconciliationJob');
-- 新版启动时由 CutoverJobRegistrar -> JobApi 创建唯一有效站内通知调度。

COMMIT;

-- C. 旧模型结构删除。CHECK 在同一 ALTER 内替换，仅删除旧模型分支及旧空字段条件。
-- 现行 NEW_PLATFORM 的状态组合、就绪分支、配置、必填、文件和审批约束逐项原样保留。
-- 9 个替换条件已通过只读 SELECT 核查：当前 5 个任务、3 个方案均无违反项。
-- 旧模型结构删除。不会转换/新增现行任务，不改现行方案、审批与历史内容。
ALTER TABLE cut_task
  DROP CHECK `chk_cut_task_origin`,
  DROP CHECK `chk_cut_task_source_type`,
  DROP CHECK `chk_cut_task_status`,
  DROP CHECK `chk_cut_task_configuration_identity`,
  DROP CHECK `chk_cut_task_source_union`,
  DROP CHECK `chk_cut_task_origin_union`,
  ADD CONSTRAINT `chk_cut_task_origin` CHECK ((task_origin_code = 'NEW_PLATFORM') = TRUE),
  ADD CONSTRAINT `chk_cut_task_source_type` CHECK ((intake_source_type_code IN ('SELF_CREATED','ITR','PROJECT_EVENT')) = TRUE),
  ADD CONSTRAINT `chk_cut_task_status` CHECK ((task_status_code IN ('GRADE_CONFIRMING','SURVEYING','PLAN_DRAFTING','APPROVING','CLOSURE_IN_PROGRESS','ARCHIVED')) = TRUE),
  ADD CONSTRAINT `chk_cut_task_configuration_identity` CHECK (((task_origin_code = 'NEW_PLATFORM') AND configuration_revision_id IS NOT NULL AND NULLIF(TRIM(configuration_code),'') IS NOT NULL AND configuration_revision_no IS NOT NULL AND configuration_revision_no > 0) = TRUE),
  ADD CONSTRAINT `chk_cut_task_source_union` CHECK ((((intake_source_type_code='SELF_CREATED' AND task_origin_code='NEW_PLATFORM' AND source_system IS NULL AND source_business_no IS NULL AND business_event_id IS NULL) OR (intake_source_type_code='ITR' AND task_origin_code='NEW_PLATFORM' AND source_system IS NOT NULL AND source_business_no IS NOT NULL AND business_event_id IS NULL) OR (intake_source_type_code='PROJECT_EVENT' AND task_origin_code='NEW_PLATFORM' AND source_system IS NULL AND source_business_no IS NULL AND business_event_id IS NOT NULL))) = TRUE),
  ADD CONSTRAINT `chk_cut_task_origin_union` CHECK (COALESCE(((`task_origin_code` = _utf8mb4'NEW_PLATFORM') and (`owner_user_id` > 0) and ((`customer_id` is null) or (`customer_id` > 0)) and (char_length(trim(`background`)) between 1 and 4000) and (char_length(trim(`cutover_type_code`)) between 1 and 32) and (`configuration_revision_id` > 0) and (char_length(trim(`configuration_code`)) between 1 and 64) and (`configuration_revision_no` > 0) and (((`current_stage_code` = _utf8mb4'P2') and (`task_status_code` = _utf8mb4'GRADE_CONFIRMING')) or ((`current_stage_code` = _utf8mb4'P3') and (`task_status_code` = _utf8mb4'SURVEYING')) or ((`current_stage_code` = _utf8mb4'P4') and (`task_status_code` = _utf8mb4'PLAN_DRAFTING')) or ((`current_stage_code` = _utf8mb4'P5') and (`task_status_code` = _utf8mb4'APPROVING')) or ((`current_stage_code` = _utf8mb4'P6') and (`task_status_code` in (_utf8mb4'CLOSURE_IN_PROGRESS',_utf8mb4'ARCHIVED')))) and (((`implementation_readiness_snapshot_id` > 0) and (`implementation_readiness_snapshot_version` >= 0)) or ((`intake_source_type_code` in (_utf8mb4'SELF_CREATED',_utf8mb4'ITR')) and (`implementation_readiness_snapshot_id` is null) and (`implementation_readiness_snapshot_version` is null) and (`readiness_decision_code` = _utf8mb4'NOT_APPLICABLE'))) and (`project_scope_version` >= 0) and (`project_version` is not null) and (`project_scope_version` is not null) and (`customer_fact_status_code` is not null) and (`readiness_decision_code` is not null)), FALSE) = TRUE),
  DROP INDEX uk_cut_task_legacy,
  DROP COLUMN legacy_task_id,
  DROP COLUMN legacy_cutover_type_raw,
  DROP COLUMN legacy_network_mode_raw,
  DROP COLUMN legacy_status_value,
  DROP COLUMN legacy_source_version,
  DROP COLUMN legacy_mapping_version;
ALTER TABLE cut_plan_revision
  DROP CHECK `chk_cut_plan_origin`,
  DROP CHECK `chk_cut_plan_values`,
  DROP CHECK `chk_cut_plan_union`,
  ADD CONSTRAINT `chk_cut_plan_origin` CHECK (origin_code = 'NEW_PLATFORM'),
  ADD CONSTRAINT `chk_cut_plan_values` CHECK (((`revision_no` > 0) and (`version` >= 0) and ((`assessment_version` is null) or (`assessment_version` > 0)) and ((`checklist_version` is null) or (`checklist_version` > 0)) and ((`configuration_revision_no` is null) or (`configuration_revision_no` > 0)) and ((`file_version_no` is null) or (`file_version_no` > 0)) and ((`file_scope_version` is null) or (`file_scope_version` >= 0)) and ((`approval_version` is null) or (`approval_version` >= 0)))),
  ADD CONSTRAINT `chk_cut_plan_union` CHECK (COALESCE(((`origin_code` = _utf8mb4'NEW_PLATFORM') and (`grade_code` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C',_utf8mb4'D')) and (`assessment_id` > 0) and (`assessment_version` > 0) and (`configuration_revision_id` > 0) and (char_length(trim(`configuration_code`)) between 1 and 64) and (`configuration_revision_no` > 0) and (`configuration_revision_id` is not null) and (((`grade_code` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C')) and (`checklist_id` > 0) and (`checklist_version` > 0)) or ((`grade_code` = _utf8mb4'D') and (`checklist_id` is null) and (`checklist_version` is null))) and (((`edit_mode_code` = _utf8mb4'FULL_FILE_UPLOAD') and (`project_description` is null) and (`rollback_description` is null) and (`customer_contact_id` is null) and (`customer_contact_version` is null) and (`file_artifact_id` > 0) and (`file_version_no` > 0) and (char_length(trim(`file_reference_key`)) between 1 and 128) and (`file_artifact_fact_version` is not null) and (`file_reference_fact_version` is not null) and (`file_availability_fact_version` is not null) and (`file_artifact_fact_version` >= 0) and (`file_reference_fact_version` >= 0) and (`file_availability_fact_version` >= 0) and (`file_scope_version` >= 0) and regexp_like(`file_sha256`,_utf8mb4'^[0-9a-f]{64}$') and (`ownership_confirmed_flag` = 0x01)) or ((((`edit_mode_code` = _utf8mb4'ONLINE_TEMPLATE_STANDARD') and (`grade_code` in (_utf8mb4'A',_utf8mb4'B',_utf8mb4'C'))) or ((`edit_mode_code` = _utf8mb4'ONLINE_TEMPLATE_SIMPLE_D') and (`grade_code` = _utf8mb4'D'))) and (`edit_mode_code` in (_utf8mb4'ONLINE_TEMPLATE_STANDARD',_utf8mb4'ONLINE_TEMPLATE_SIMPLE_D')) and (`file_artifact_id` is null) and (`file_version_no` is null) and (`file_reference_key` is null) and (`file_artifact_fact_version` is null) and (`file_reference_fact_version` is null) and (`file_availability_fact_version` is null) and (`file_scope_version` is null) and (`file_sha256` is null) and (`ownership_confirmed_flag` is null))) and (((`status_code` = _utf8mb4'DRAFT') and (`current_marker` = 1) and (`submit_user_id` is null) and (`submit_time` is null) and (`approval_instance_id` is null) and (`approval_version` is null) and (`invalidate_user_id` is null) and (`invalidate_time` is null) and (`invalidation_reason_code` is null)) or ((`status_code` = _utf8mb4'SUBMITTED') and (`submit_user_id` > 0) and (`submit_time` is not null) and (`approval_instance_id` > 0) and (`approval_version` >= 0) and (`invalidate_user_id` is null) and (`invalidate_time` is null) and (`invalidation_reason_code` is null)) or ((`status_code` = _utf8mb4'INVALIDATED') and (`current_marker` is null) and (`submit_user_id` > 0) and (`submit_time` is not null) and (`approval_instance_id` > 0) and (`approval_version` >= 0) and (`invalidate_user_id` > 0) and (`invalidate_time` is not null) and (char_length(trim(`invalidation_reason_code`)) between 1 and 64)))), FALSE) = TRUE),
  DROP INDEX uk_cut_plan_legacy,
  DROP COLUMN legacy_plan_id,
  DROP COLUMN legacy_status_raw,
  DROP COLUMN legacy_source_version,
  DROP COLUMN legacy_mapping_version,
  DROP COLUMN legacy_task_id,
  DROP COLUMN legacy_code,
  DROP COLUMN legacy_name,
  DROP COLUMN legacy_grade_code,
  DROP COLUMN legacy_remark;
DROP TABLE pms_cut_observation_retired, pms_cut_execution_retired, pms_cut_risk_retired,
           pms_cut_plan_retired, pms_cut_task_retired;
-- DROP TABLE 同时移除属于这 5 张表的 15 个只读触发器。

-- D. 执行后复核；还需新版真实浏览器验收，不以这些查询替代业务验收。
SELECT COUNT(*) AS expected_0 FROM information_schema.tables WHERE table_schema=DATABASE()
  AND table_name IN ('pms_cut_task_retired','pms_cut_plan_retired','pms_cut_risk_retired',
                     'pms_cut_execution_retired','pms_cut_observation_retired');
SELECT COUNT(*) AS expected_0 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name IN ('cut_task','cut_plan_revision') AND column_name LIKE 'legacy\_%';
SELECT task_origin_code,COUNT(*) AS rows_count FROM cut_task GROUP BY task_origin_code;
SELECT origin_code,COUNT(*) AS rows_count FROM cut_plan_revision GROUP BY origin_code;
-- 本次只读基线分别为 NEW_PLATFORM 5 个任务、3 个方案。
SELECT id,name,path,component,component_name FROM system_menu
WHERE id IN (970445001,970445002,992602050001) AND deleted=b'0';
SELECT COUNT(*) AS expected_15 FROM system_menu WHERE parent_id=992602050001 AND type=3 AND deleted=b'0';
SELECT COUNT(*) AS expected_4 FROM system_menu WHERE id IN (199501,199502,199503,199504)
  AND parent_id=970445002 AND deleted=b'0';
SELECT id,handler_name,status FROM infra_job
WHERE id IN (970445201,970445202,992602073002) AND deleted=b'0';
