-- P23 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_pb_plan_from_sms; dialect=MYSQL
SELECT
  -- pm_pb_plan_from_sms.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_pb_plan_from_sms.contractNo -> com_contract_collection_plan.contract_no; 业务字段/正式关系
  s.`contractNo` AS `raw__contract_no`,
  -- pm_pb_plan_from_sms.batchCode -> com_contract_collection_plan.batch_code; 业务字段/正式关系
  s.`batchCode` AS `raw__batch_code`,
  -- pm_pb_plan_from_sms.basicDataName -> com_contract_collection_plan.collection_item_name; 业务字段/正式关系
  s.`basicDataName` AS `raw__collection_item_name`,
  -- pm_pb_plan_from_sms.referenceEventName -> com_contract_collection_plan.reference_event_name; 业务字段/正式关系
  s.`referenceEventName` AS `raw__reference_event_name`,
  -- pm_pb_plan_from_sms.eventPlanHappenDate -> com_contract_collection_plan.event_planned_at; 业务字段/正式关系
  s.`eventPlanHappenDate` AS `raw__event_planned_at`,
  -- pm_pb_plan_from_sms.afterDaysNum -> com_contract_collection_plan.after_days; 业务字段/正式关系
  s.`afterDaysNum` AS `raw__after_days`,
  -- pm_pb_plan_from_sms.eventActualFinishDate -> com_contract_collection_plan.event_actual_finished_at; 业务字段/正式关系
  s.`eventActualFinishDate` AS `raw__event_actual_finished_at`,
  -- pm_pb_plan_from_sms.marketingFeedback -> com_contract_collection_plan.marketing_feedback; 业务字段/正式关系
  s.`marketingFeedback` AS `raw__marketing_feedback`,
  -- pm_pb_plan_from_sms.createBy -> com_contract_collection_plan.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- pm_pb_plan_from_sms.createTime -> com_contract_collection_plan.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- pm_pb_plan_from_sms.updateBy -> com_contract_collection_plan.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`,
  -- pm_pb_plan_from_sms.updateTime -> com_contract_collection_plan.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`,
  -- pm_pb_plan_from_sms.effectiveFrom -> com_contract_collection_plan.effective_from; 业务字段/正式关系
  s.`effectiveFrom` AS `raw__effective_from`,
  -- pm_pb_plan_from_sms.effectiveTo -> com_contract_collection_plan.effective_to; 业务字段/正式关系
  s.`effectiveTo` AS `raw__effective_to`,
  -- pm_pb_plan_from_sms.dataSource -> com_contract_collection_plan.data_source; 业务字段/正式关系
  s.`dataSource` AS `raw__data_source`
FROM `pm_pb_plan_from_sms` s;
