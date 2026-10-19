-- P24 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE sms_ofst_contract_head_sap; dialect=MYSQL
SELECT
  -- sms_ofst_contract_head_sap.id -> com_contract_receivable.source_record_key; 业务字段/正式关系
  s.`id` AS `raw__source_record_key`,
  -- sms_ofst_contract_head_sap.contract_num -> com_contract_receivable.contract_no; 业务字段/正式关系
  s.`contract_num` AS `raw__contract_no`,
  -- sms_ofst_contract_head_sap.batch_code -> com_contract_receivable.source_batch_code; 业务字段/正式关系
  s.`batch_code` AS `raw__source_batch_code`,
  -- sms_ofst_contract_head_sap.project_name -> com_contract_receivable.project_name; 业务字段/正式关系
  s.`project_name` AS `raw__project_name`,
  -- sms_ofst_contract_head_sap.order_num -> com_contract_receivable.source_order_no; 业务字段/正式关系
  s.`order_num` AS `raw__source_order_no`,
  -- sms_ofst_contract_head_sap.client_supplier_code -> com_contract_receivable.customer_code; 业务字段/正式关系
  s.`client_supplier_code` AS `raw__customer_code`,
  -- sms_ofst_contract_head_sap.client_supplier_name -> com_contract_receivable.customer_name; 业务字段/正式关系
  s.`client_supplier_name` AS `raw__customer_name`,
  -- sms_ofst_contract_head_sap.contract_money_amount -> com_contract_receivable.contract_amount; 业务字段/正式关系
  s.`contract_money_amount` AS `raw__contract_amount`,
  -- sms_ofst_contract_head_sap.delivered_money_amount -> com_contract_receivable.delivered_amount; 业务字段/正式关系
  s.`delivered_money_amount` AS `raw__delivered_amount`,
  -- sms_ofst_contract_head_sap.collected_money_amount -> com_contract_receivable.collected_amount; 业务字段/正式关系
  s.`collected_money_amount` AS `raw__collected_amount`,
  -- sms_ofst_contract_head_sap.collected_money_ratio -> com_contract_receivable.collected_ratio; 业务字段/正式关系
  s.`collected_money_ratio` AS `raw__collected_ratio`,
  -- sms_ofst_contract_head_sap.receivables_money_amount -> com_contract_receivable.receivable_amount; 业务字段/正式关系
  s.`receivables_money_amount` AS `raw__receivable_amount`,
  -- sms_ofst_contract_head_sap.over_due_money_amount -> com_contract_receivable.overdue_amount; 业务字段/正式关系
  s.`over_due_money_amount` AS `raw__overdue_amount`,
  -- sms_ofst_contract_head_sap.maketing_department_name -> com_contract_receivable.market_name; 业务字段/正式关系
  s.`maketing_department_name` AS `raw__market_name`,
  -- sms_ofst_contract_head_sap.office_name -> com_contract_receivable.department_name; 业务字段/正式关系
  s.`office_name` AS `raw__department_name`,
  -- sms_ofst_contract_head_sap.industry_name -> com_contract_receivable.industry_name; 业务字段/正式关系
  s.`industry_name` AS `raw__industry_name`,
  -- sms_ofst_contract_head_sap.marketing_representative_name -> com_contract_receivable.marketing_representative_name; 业务字段/正式关系
  s.`marketing_representative_name` AS `raw__marketing_representative_name`,
  -- sms_ofst_contract_head_sap.currency_name -> com_contract_receivable.currency_name; 业务字段/正式关系
  s.`currency_name` AS `raw__currency_name`,
  -- sms_ofst_contract_head_sap.create_by -> com_contract_receivable.creator; 业务字段/正式关系
  s.`create_by` AS `raw__creator`,
  -- sms_ofst_contract_head_sap.create_time -> com_contract_receivable.create_time; 业务字段/正式关系
  s.`create_time` AS `raw__create_time`,
  -- sms_ofst_contract_head_sap.update_by -> com_contract_receivable.updater; 业务字段/正式关系
  s.`update_by` AS `raw__updater`,
  -- sms_ofst_contract_head_sap.update_time -> com_contract_receivable.update_time；com_contract_receivable.source_sync_time; 业务字段/正式关系
  s.`update_time` AS `raw__update_time`,
  -- sms_ofst_contract_head_sap.effective_from -> com_contract_receivable.source_effective_from; 业务字段/正式关系
  s.`effective_from` AS `raw__source_effective_from`,
  -- sms_ofst_contract_head_sap.effective_to -> com_contract_receivable.source_effective_to; 业务字段/正式关系
  s.`effective_to` AS `raw__source_effective_to`,
  -- sms_ofst_contract_head_sap.import_batch_num -> com_contract_receivable.import_batch_no; 业务字段/正式关系
  s.`import_batch_num` AS `raw__import_batch_no`,
  -- sms_ofst_contract_head_sap.contract_create_date -> com_contract_receivable.contract_create_time; 业务字段/正式关系
  s.`contract_create_date` AS `raw__contract_create_time`,
  -- sms_ofst_contract_head_sap.projectCode -> com_contract_receivable.project_code; 业务字段/正式关系
  s.`projectCode` AS `raw__project_code`,
  -- sms_ofst_contract_head_sap.marketCode -> com_contract_receivable.market_code; 业务字段/正式关系
  s.`marketCode` AS `raw__market_code`,
  -- sms_ofst_contract_head_sap.systemId -> com_contract_receivable.system_source_key; 业务字段/正式关系
  s.`systemId` AS `raw__system_source_key`,
  -- sms_ofst_contract_head_sap.industryId -> com_contract_receivable.industry_code; 业务字段/正式关系
  s.`industryId` AS `raw__industry_code`,
  -- sms_ofst_contract_head_sap.officeCode -> com_contract_receivable.department_code; 业务字段/正式关系
  s.`officeCode` AS `raw__department_code`,
  -- sms_ofst_contract_head_sap.expendId -> com_contract_receivable.expend_source_key; 业务字段/正式关系
  s.`expendId` AS `raw__expend_source_key`,
  -- sms_ofst_contract_head_sap.usernamec -> com_contract_receivable.marketing_representative_code; 业务字段/正式关系
  s.`usernamec` AS `raw__marketing_representative_code`,
  -- sms_ofst_contract_head_sap.latest_ship_date -> com_contract_receivable.latest_ship_time; 业务字段/正式关系
  s.`latest_ship_date` AS `raw__latest_ship_time`,
  -- sms_ofst_contract_head_sap.usernamec2 -> com_contract_receivable.secondary_representative_code; 业务字段/正式关系
  s.`usernamec2` AS `raw__secondary_representative_code`,
  -- sms_ofst_contract_head_sap.systemid_o -> com_contract_receivable.original_division_values.systemSourceKey; 业务字段/正式关系
  s.`systemid_o` AS `raw__systemid_o`,
  -- sms_ofst_contract_head_sap.expendid_o -> com_contract_receivable.original_division_values.expendSourceKey; 业务字段/正式关系
  s.`expendid_o` AS `raw__expendid_o`,
  -- sms_ofst_contract_head_sap.industry_name_o -> com_contract_receivable.original_division_values.industryName; 业务字段/正式关系
  s.`industry_name_o` AS `raw__industry_name_o`,
  -- sms_ofst_contract_head_sap.dataSource -> com_contract_receivable.source_system; 业务字段/正式关系
  s.`dataSource` AS `raw__source_system`
FROM `sms_ofst_contract_head_sap` s;
