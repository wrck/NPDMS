-- P15 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_order_data_from_erp; dialect=MYSQL
SELECT
  -- pm_order_data_from_erp.id -> com_contract.master_source_record_key; P15当前正式合同映射
  s.`id` AS `raw__master_source_record_key`,
  -- pm_order_data_from_erp.orderNumber -> P15不写该字段; 其他任务/本任务未迁入
  s.`orderNumber` AS `raw__orderNumber`,
  -- pm_order_data_from_erp.contractNo -> com_contract.contract_no; P15当前正式合同映射
  s.`contractNo` AS `raw__contract_no`,
  -- pm_order_data_from_erp.orderExecNumber -> P15不写该字段; 其他任务/本任务未迁入
  s.`orderExecNumber` AS `raw__orderExecNumber`,
  -- pm_order_data_from_erp.orderCreateTime -> P15不写该字段; 其他任务/本任务未迁入
  s.`orderCreateTime` AS `raw__orderCreateTime`,
  -- pm_order_data_from_erp.customerRequireTime -> P15不写该字段; 其他任务/本任务未迁入
  s.`customerRequireTime` AS `raw__customerRequireTime`,
  -- pm_order_data_from_erp.customerCode -> com_contract.customer_code; P15当前正式合同映射
  s.`customerCode` AS `raw__customer_code`,
  -- pm_order_data_from_erp.customerName -> com_contract.customer_name; P15当前正式合同映射
  s.`customerName` AS `raw__customer_name`,
  -- pm_order_data_from_erp.projectName -> P15不写该字段; 其他任务/本任务未迁入
  s.`projectName` AS `raw__projectName`,
  -- pm_order_data_from_erp.orderComment -> P15不写该字段; 其他任务/本任务未迁入
  s.`orderComment` AS `raw__orderComment`,
  -- pm_order_data_from_erp.orderType -> P15不写该字段; 其他任务/本任务未迁入
  s.`orderType` AS `raw__orderType`,
  -- pm_order_data_from_erp.compCode -> com_contract.company_code; P15当前正式合同映射
  s.`compCode` AS `raw__company_code`,
  -- pm_order_data_from_erp.salesType -> P15不写该字段; 其他任务/本任务未迁入
  s.`salesType` AS `raw__salesType`,
  -- pm_order_data_from_erp.source -> P15不写该字段; 其他任务/本任务未迁入
  s.`source` AS `raw__source`,
  -- pm_order_data_from_erp.customInfo -> P15不写该字段; 其他任务/本任务未迁入
  s.`customInfo` AS `raw__customInfo`,
  -- pm_order_data_from_erp.syncTime -> P15不写该字段; 其他任务/本任务未迁入
  s.`syncTime` AS `raw__syncTime`
FROM `pm_order_data_from_erp` s;

-- SOURCE sms_ofst_contract_head_sap; dialect=MYSQL
SELECT
  -- sms_ofst_contract_head_sap.id -> 来源排序参考，不单独写合同业务列; 查询归并参考
  s.`id` AS `raw__id`,
  -- sms_ofst_contract_head_sap.contract_num -> com_contract.contract_no; P15当前正式合同映射
  s.`contract_num` AS `raw__contract_no`,
  -- sms_ofst_contract_head_sap.batch_code -> P15不写该字段; 其他任务/本任务未迁入
  s.`batch_code` AS `raw__batch_code`,
  -- sms_ofst_contract_head_sap.project_name -> com_contract.project_name; P15当前正式合同映射
  s.`project_name` AS `raw__project_name`,
  -- sms_ofst_contract_head_sap.order_num -> P15不写该字段; 其他任务/本任务未迁入
  s.`order_num` AS `raw__order_num`,
  -- sms_ofst_contract_head_sap.client_supplier_code -> P15不写该字段; 其他任务/本任务未迁入
  s.`client_supplier_code` AS `raw__client_supplier_code`,
  -- sms_ofst_contract_head_sap.client_supplier_name -> P15不写该字段; 其他任务/本任务未迁入
  s.`client_supplier_name` AS `raw__client_supplier_name`,
  -- sms_ofst_contract_head_sap.contract_money_amount -> com_contract.contract_amount; P15当前正式合同映射
  s.`contract_money_amount` AS `raw__contract_amount`,
  -- sms_ofst_contract_head_sap.delivered_money_amount -> P15不写该字段; 其他任务/本任务未迁入
  s.`delivered_money_amount` AS `raw__delivered_money_amount`,
  -- sms_ofst_contract_head_sap.collected_money_amount -> P15不写该字段; 其他任务/本任务未迁入
  s.`collected_money_amount` AS `raw__collected_money_amount`,
  -- sms_ofst_contract_head_sap.collected_money_ratio -> P15不写该字段; 其他任务/本任务未迁入
  s.`collected_money_ratio` AS `raw__collected_money_ratio`,
  -- sms_ofst_contract_head_sap.receivables_money_amount -> P15不写该字段; 其他任务/本任务未迁入
  s.`receivables_money_amount` AS `raw__receivables_money_amount`,
  -- sms_ofst_contract_head_sap.over_due_money_amount -> P15不写该字段; 其他任务/本任务未迁入
  s.`over_due_money_amount` AS `raw__over_due_money_amount`,
  -- sms_ofst_contract_head_sap.maketing_department_name -> com_contract.market_name; P15当前正式合同映射
  s.`maketing_department_name` AS `raw__market_name`,
  -- sms_ofst_contract_head_sap.office_name -> com_contract.department_name; P15当前正式合同映射
  s.`office_name` AS `raw__department_name`,
  -- sms_ofst_contract_head_sap.industry_name -> com_contract.industry_name; P15当前正式合同映射
  s.`industry_name` AS `raw__industry_name`,
  -- sms_ofst_contract_head_sap.marketing_representative_name -> com_contract.marketing_representative_name; P15当前正式合同映射
  s.`marketing_representative_name` AS `raw__marketing_representative_name`,
  -- sms_ofst_contract_head_sap.currency_name -> com_contract.currency_name；com_contract.currency_code; P15当前正式合同映射
  s.`currency_name` AS `raw__currency_name`,
  -- sms_ofst_contract_head_sap.create_by -> P15不写该字段; 其他任务/本任务未迁入
  s.`create_by` AS `raw__create_by`,
  -- sms_ofst_contract_head_sap.create_time -> P15不写该字段; 其他任务/本任务未迁入
  s.`create_time` AS `raw__create_time`,
  -- sms_ofst_contract_head_sap.update_by -> P15不写该字段; 其他任务/本任务未迁入
  s.`update_by` AS `raw__update_by`,
  -- sms_ofst_contract_head_sap.update_time -> 来源排序参考，不单独写合同业务列; 查询归并参考
  s.`update_time` AS `raw__update_time`,
  -- sms_ofst_contract_head_sap.effective_from -> P15不写该字段; 其他任务/本任务未迁入
  s.`effective_from` AS `raw__effective_from`,
  -- sms_ofst_contract_head_sap.effective_to -> P15不写该字段; 其他任务/本任务未迁入
  s.`effective_to` AS `raw__effective_to`,
  -- sms_ofst_contract_head_sap.import_batch_num -> P15不写该字段; 其他任务/本任务未迁入
  s.`import_batch_num` AS `raw__import_batch_num`,
  -- sms_ofst_contract_head_sap.contract_create_date -> com_contract.contract_create_time; P15当前正式合同映射
  s.`contract_create_date` AS `raw__contract_create_time`,
  -- sms_ofst_contract_head_sap.projectCode -> com_contract.project_code; P15当前正式合同映射
  s.`projectCode` AS `raw__project_code`,
  -- sms_ofst_contract_head_sap.marketCode -> com_contract.market_code; P15当前正式合同映射
  s.`marketCode` AS `raw__market_code`,
  -- sms_ofst_contract_head_sap.systemId -> P15不写该字段; 其他任务/本任务未迁入
  s.`systemId` AS `raw__systemId`,
  -- sms_ofst_contract_head_sap.industryId -> com_contract.industry_code; P15当前正式合同映射
  s.`industryId` AS `raw__industry_code`,
  -- sms_ofst_contract_head_sap.officeCode -> com_contract.department_code; P15当前正式合同映射
  s.`officeCode` AS `raw__department_code`,
  -- sms_ofst_contract_head_sap.expendId -> P15不写该字段; 其他任务/本任务未迁入
  s.`expendId` AS `raw__expendId`,
  -- sms_ofst_contract_head_sap.usernamec -> com_contract.marketing_representative_code; P15当前正式合同映射
  s.`usernamec` AS `raw__marketing_representative_code`,
  -- sms_ofst_contract_head_sap.latest_ship_date -> P15不写该字段; 其他任务/本任务未迁入
  s.`latest_ship_date` AS `raw__latest_ship_date`,
  -- sms_ofst_contract_head_sap.usernamec2 -> com_contract.secondary_representative_code; P15当前正式合同映射
  s.`usernamec2` AS `raw__secondary_representative_code`,
  -- sms_ofst_contract_head_sap.systemid_o -> P15不写该字段; 其他任务/本任务未迁入
  s.`systemid_o` AS `raw__systemid_o`,
  -- sms_ofst_contract_head_sap.expendid_o -> P15不写该字段; 其他任务/本任务未迁入
  s.`expendid_o` AS `raw__expendid_o`,
  -- sms_ofst_contract_head_sap.industry_name_o -> P15不写该字段; 其他任务/本任务未迁入
  s.`industry_name_o` AS `raw__industry_name_o`,
  -- sms_ofst_contract_head_sap.dataSource -> P15不写该字段; 其他任务/本任务未迁入
  s.`dataSource` AS `raw__dataSource`
FROM `sms_ofst_contract_head_sap` s;

-- SOURCE fb_contract; dialect=MYSQL
SELECT
  -- fb_contract.contract_id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`contract_id` AS `raw__contract_id`,
  -- fb_contract.contract_code -> com_shipment_contract_reference.contract_no; 业务字段/正式关系
  s.`contract_code` AS `raw__contract_no`,
  -- fb_contract.office_code -> com_shipment_contract_reference.department_code; 业务字段/正式关系
  s.`office_code` AS `raw__department_code`,
  -- fb_contract.contract_type -> com_shipment_contract_reference.contract_type; 业务字段/正式关系
  s.`contract_type` AS `raw__contract_type`,
  -- fb_contract.customer_name -> com_shipment_contract_reference.customer_name; 业务字段/正式关系
  s.`customer_name` AS `raw__customer_name`,
  -- fb_contract.project_name -> com_shipment_contract_reference.project_name; 业务字段/正式关系
  s.`project_name` AS `raw__project_name`,
  -- fb_contract.warranty -> com_shipment_contract_reference.warranty_years; 业务字段/正式关系
  s.`warranty` AS `raw__warranty_years`,
  -- fb_contract.marketCode -> com_shipment_contract_reference.market_code; 业务字段/正式关系
  s.`marketCode` AS `raw__market_code`,
  -- fb_contract.marketName -> com_shipment_contract_reference.market_name; 业务字段/正式关系
  s.`marketName` AS `raw__market_name`,
  -- fb_contract.systemId -> com_shipment_contract_reference.system_source_key; 业务字段/正式关系
  s.`systemId` AS `raw__system_source_key`,
  -- fb_contract.systemName -> com_shipment_contract_reference.system_name; 业务字段/正式关系
  s.`systemName` AS `raw__system_name`,
  -- fb_contract.remark -> com_shipment_contract_reference.remark; 业务字段/正式关系
  s.`remark` AS `raw__remark`
FROM `fb_contract` s;

-- SOURCE fb_shipment; dialect=MYSQL
SELECT
  -- fb_shipment.packlist_id -> com_shipment_package.package_no; 业务字段/正式关系
  s.`packlist_id` AS `raw__package_no`,
  -- fb_shipment.con_id -> com_shipment_package.shipment_contract_ref_id; 业务字段/正式关系
  s.`con_id` AS `raw__shipment_contract_ref_id`,
  -- fb_shipment.packdate -> com_shipment_package.shipment_time; 业务字段/正式关系
  s.`packdate` AS `raw__shipment_time`,
  -- fb_shipment.warrantyStartTime -> com_shipment_package.warranty_start_time; 业务字段/正式关系
  s.`warrantyStartTime` AS `raw__warranty_start_time`,
  -- fb_shipment.warrantyEndTime -> com_shipment_package.warranty_end_time; 业务字段/正式关系
  s.`warrantyEndTime` AS `raw__warranty_end_time`,
  -- fb_shipment.receiveName -> com_shipment_package.receiver_name; 业务字段/正式关系
  s.`receiveName` AS `raw__receiver_name`,
  -- fb_shipment.emsNum -> com_shipment_package.express_no; 业务字段/正式关系
  s.`emsNum` AS `raw__express_no`,
  -- fb_shipment.emsCompany -> com_shipment_package.carrier_code；com_shipment_package.carrier_name; 业务字段/正式关系
  s.`emsCompany` AS `raw__carrier_code`
FROM `fb_shipment` s;

-- SOURCE view_shipment_ems_4_pm; dialect=MYSQL
SELECT
  -- view_shipment_ems_4_pm.contract_code -> com_shipment_contract_reference.contract_no; 业务字段/正式关系
  s.`contract_code` AS `raw__contract_no`,
  -- view_shipment_ems_4_pm.receiveName -> com_shipment_package.receiver_name; 业务字段/正式关系
  s.`receiveName` AS `raw__receiver_name`,
  -- view_shipment_ems_4_pm.emsNum -> com_shipment_package.express_no; 业务字段/正式关系
  s.`emsNum` AS `raw__express_no`,
  -- view_shipment_ems_4_pm.packdate -> com_shipment_package.shipment_time; 业务字段/正式关系
  s.`packdate` AS `raw__shipment_time`,
  -- view_shipment_ems_4_pm.emsCompany -> com_shipment_package.carrier_code; 业务字段/正式关系
  s.`emsCompany` AS `raw__carrier_code`,
  -- view_shipment_ems_4_pm.packId -> com_shipment_package.package_no; 业务字段/正式关系
  s.`packId` AS `raw__package_no`
FROM `view_shipment_ems_4_pm` s;

-- SOURCE fb_shipment_barcode; dialect=MYSQL
SELECT
  -- fb_shipment_barcode.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- fb_shipment_barcode.pack_id -> ast_device_shipment.package_no；ast_device_shipment.legacy_package_key; 业务字段/正式关系
  s.`pack_id` AS `raw__package_no`,
  -- fb_shipment_barcode.item -> ast_device_shipment.product_code; 业务字段/正式关系
  s.`item` AS `raw__product_code`,
  -- fb_shipment_barcode.barcode -> ast_device_shipment.device_sn; 业务字段/正式关系
  s.`barcode` AS `raw__device_sn`,
  -- fb_shipment_barcode.com_barcode -> ast_device_shipment.internal_serial_no; 业务字段/正式关系
  s.`com_barcode` AS `raw__internal_serial_no`,
  -- fb_shipment_barcode.rma_no -> ast_device_shipment.rma_no; 业务字段/正式关系
  s.`rma_no` AS `raw__rma_no`,
  -- fb_shipment_barcode.isRMA -> ast_device_shipment.is_rma; 业务字段/正式关系
  s.`isRMA` AS `raw__is_rma`,
  -- fb_shipment_barcode.item2 -> ast_device_shipment.secondary_item; 业务字段/正式关系
  s.`item2` AS `raw__secondary_item`,
  -- fb_shipment_barcode.barcode2 -> ast_device_shipment.secondary_sn; 业务字段/正式关系
  s.`barcode2` AS `raw__secondary_sn`,
  -- fb_shipment_barcode.orderNumber -> ast_device_shipment.order_no; 业务字段/正式关系
  s.`orderNumber` AS `raw__order_no`,
  -- fb_shipment_barcode.lineNum -> ast_device_shipment.line_no; 业务字段/正式关系
  s.`lineNum` AS `raw__line_no`,
  -- fb_shipment_barcode.profitCenter -> ast_device_shipment.profit_center; 业务字段/正式关系
  s.`profitCenter` AS `raw__profit_center`,
  -- fb_shipment_barcode.soleAgentSuffix -> ast_device_shipment.sole_agent_suffix; 业务字段/正式关系
  s.`soleAgentSuffix` AS `raw__sole_agent_suffix`,
  -- fb_shipment_barcode.warrantyStartDate -> ast_device_shipment.warranty_start_date; 业务字段/正式关系
  s.`warrantyStartDate` AS `raw__warranty_start_date`,
  -- fb_shipment_barcode.warrantyMonth -> ast_device_shipment.warranty_months; 业务字段/正式关系
  s.`warrantyMonth` AS `raw__warranty_months`,
  -- fb_shipment_barcode.rmaBarcode -> ast_device_shipment.rma_related_sn; 业务字段/正式关系
  s.`rmaBarcode` AS `raw__rma_related_sn`,
  -- fb_shipment_barcode.updateTime -> ast_device_shipment.source_updated_at; 业务字段/正式关系
  s.`updateTime` AS `raw__source_updated_at`,
  -- fb_shipment_barcode.syncTime -> ast_device_shipment.sync_time; 业务字段/正式关系
  s.`syncTime` AS `raw__sync_time`,
  -- fb_shipment_barcode.uuid -> ast_device_shipment.uuid; 业务字段/正式关系
  s.`uuid` AS `raw__uuid`
FROM `fb_shipment_barcode` s;

-- SOURCE fb_shipment_barcode_relation; dialect=MYSQL
SELECT
  -- fb_shipment_barcode_relation.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- fb_shipment_barcode_relation.sn1 -> ast_device_relationship.source_device_sn; 业务字段/正式关系
  s.`sn1` AS `raw__source_device_sn`,
  -- fb_shipment_barcode_relation.item1 -> ast_device_relationship.source_item; 业务字段/正式关系
  s.`item1` AS `raw__source_item`,
  -- fb_shipment_barcode_relation.sn2 -> ast_device_relationship.target_device_sn; 业务字段/正式关系
  s.`sn2` AS `raw__target_device_sn`,
  -- fb_shipment_barcode_relation.item2 -> ast_device_relationship.target_item; 业务字段/正式关系
  s.`item2` AS `raw__target_item`,
  -- fb_shipment_barcode_relation.contract -> ast_device_relationship.contract_no; 业务字段/正式关系
  s.`contract` AS `raw__contract_no`,
  -- fb_shipment_barcode_relation.createtime -> ast_device_relationship.create_time；ast_device_relationship.effective_from; 业务字段/正式关系
  s.`createtime` AS `raw__create_time`,
  -- fb_shipment_barcode_relation.updatetime -> ast_device_relationship.update_time；ast_device_relationship.source_version; 业务字段/正式关系
  s.`updatetime` AS `raw__update_time`
FROM `fb_shipment_barcode_relation` s;
