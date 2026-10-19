-- P13 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_property_from_sms; dialect=MYSQL
SELECT
  -- pm_project_property_from_sms.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_property_from_sms.orderExecNumber -> com_crm_execution_order.execution_no; 业务字段/正式关系
  s.`orderExecNumber` AS `raw__execution_no`,
  -- pm_project_property_from_sms.projectCode -> com_crm_execution_order.project_code; 业务字段/正式关系
  s.`projectCode` AS `raw__project_code`,
  -- pm_project_property_from_sms.projectName -> com_crm_execution_order.project_name; 业务字段/正式关系
  s.`projectName` AS `raw__project_name`,
  -- pm_project_property_from_sms.salesManCode -> com_crm_execution_order.sales_rep_code; 业务字段/正式关系
  s.`salesManCode` AS `raw__sales_rep_code`,
  -- pm_project_property_from_sms.salesManName -> com_crm_execution_order.sales_rep_name; 业务字段/正式关系
  s.`salesManName` AS `raw__sales_rep_name`,
  -- pm_project_property_from_sms.marketCode -> com_crm_execution_order.market_code; 业务字段/正式关系
  s.`marketCode` AS `raw__market_code`,
  -- pm_project_property_from_sms.marketName -> com_crm_execution_order.market_name; 业务字段/正式关系
  s.`marketName` AS `raw__market_name`,
  -- pm_project_property_from_sms.systemId -> com_crm_execution_order.system_source_key; 业务字段/正式关系
  s.`systemId` AS `raw__system_source_key`,
  -- pm_project_property_from_sms.systemName -> com_crm_execution_order.system_name; 业务字段/正式关系
  s.`systemName` AS `raw__system_name`,
  -- pm_project_property_from_sms.expendId -> com_crm_execution_order.expend_source_key; 业务字段/正式关系
  s.`expendId` AS `raw__expend_source_key`,
  -- pm_project_property_from_sms.expendName -> com_crm_execution_order.expend_name; 业务字段/正式关系
  s.`expendName` AS `raw__expend_name`,
  -- pm_project_property_from_sms.industryId -> com_crm_execution_order.industry_code; 业务字段/正式关系
  s.`industryId` AS `raw__industry_code`,
  -- pm_project_property_from_sms.industryName -> com_crm_execution_order.industry_name; 业务字段/正式关系
  s.`industryName` AS `raw__industry_name`,
  -- pm_project_property_from_sms.officeCode -> com_crm_execution_order.department_code; 业务字段/正式关系
  s.`officeCode` AS `raw__department_code`,
  -- pm_project_property_from_sms.officeName -> com_crm_execution_order.department_name; 业务字段/正式关系
  s.`officeName` AS `raw__department_name`,
  -- pm_project_property_from_sms.serviceTypeName -> com_crm_execution_order.service_type_name; 业务字段/正式关系
  s.`serviceTypeName` AS `raw__service_type_name`,
  -- pm_project_property_from_sms.channelName -> com_crm_execution_order.channel_name; 业务字段/正式关系
  s.`channelName` AS `raw__channel_name`,
  -- pm_project_property_from_sms.engineeFee -> com_crm_execution_order.engineering_fee_raw；com_crm_execution_order.engineering_fee; 业务字段/正式关系
  s.`engineeFee` AS `raw__engineering_fee_raw`,
  -- pm_project_property_from_sms.objId -> com_crm_execution_order.source_object_id; 业务字段/正式关系
  s.`objId` AS `raw__source_object_id`,
  -- pm_project_property_from_sms.applyType -> com_crm_execution_order.apply_type; 业务字段/正式关系
  s.`applyType` AS `raw__apply_type`,
  -- pm_project_property_from_sms.corporationCode -> com_crm_execution_order.company_code；com_crm_execution_order.company_id; 业务字段/正式关系
  s.`corporationCode` AS `raw__company_code`,
  -- pm_project_property_from_sms.customerProjectName -> com_crm_execution_order.customer_project_name; 业务字段/正式关系
  s.`customerProjectName` AS `raw__customer_project_name`,
  -- pm_project_property_from_sms.finalCustomerName -> com_crm_execution_order.final_customer_name; 业务字段/正式关系
  s.`finalCustomerName` AS `raw__final_customer_name`,
  -- pm_project_property_from_sms.agentName -> com_crm_execution_order.agent_name; 业务字段/正式关系
  s.`agentName` AS `raw__agent_name`,
  -- pm_project_property_from_sms.projectMoney -> com_crm_execution_order.project_amount; 业务字段/正式关系
  s.`projectMoney` AS `raw__project_amount`,
  -- pm_project_property_from_sms.submitTime -> com_crm_execution_order.submit_time; 业务字段/正式关系
  s.`submitTime` AS `raw__submit_time`,
  -- pm_project_property_from_sms.majorProjectLevel -> com_crm_execution_order.major_project_level; 业务字段/正式关系
  s.`majorProjectLevel` AS `raw__major_project_level`,
  -- pm_project_property_from_sms.predBidDate -> com_crm_execution_order.predicted_bid_time; 业务字段/正式关系
  s.`predBidDate` AS `raw__predicted_bid_time`,
  -- pm_project_property_from_sms.linkmanName -> com_crm_execution_order.contact_name; 业务字段/正式关系
  s.`linkmanName` AS `raw__contact_name`,
  -- pm_project_property_from_sms.linkmanTel -> com_crm_execution_order.contact_phone; 业务字段/正式关系
  s.`linkmanTel` AS `raw__contact_phone`,
  -- pm_project_property_from_sms.dataSource -> com_crm_execution_order.source_system; 业务字段/正式关系
  s.`dataSource` AS `raw__source_system`
FROM `pm_project_property_from_sms` s;
