-- P14 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_order_data_from_erp; dialect=MYSQL
SELECT
  -- pm_order_data_from_erp.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_order_data_from_erp.orderNumber -> com_sales_order.order_no; 业务字段/正式关系
  s.`orderNumber` AS `raw__order_no`,
  -- pm_order_data_from_erp.contractNo -> com_sales_order.contract_no; 业务字段/正式关系
  s.`contractNo` AS `raw__contract_no`,
  -- pm_order_data_from_erp.orderExecNumber -> com_sales_order.execution_no; 业务字段/正式关系
  s.`orderExecNumber` AS `raw__execution_no`,
  -- pm_order_data_from_erp.orderCreateTime -> com_sales_order.order_create_time; 业务字段/正式关系
  s.`orderCreateTime` AS `raw__order_create_time`,
  -- pm_order_data_from_erp.customerRequireTime -> com_sales_order.customer_required_time; 业务字段/正式关系
  s.`customerRequireTime` AS `raw__customer_required_time`,
  -- pm_order_data_from_erp.customerCode -> com_sales_order.customer_code; 业务字段/正式关系
  s.`customerCode` AS `raw__customer_code`,
  -- pm_order_data_from_erp.customerName -> com_sales_order.customer_name; 业务字段/正式关系
  s.`customerName` AS `raw__customer_name`,
  -- pm_order_data_from_erp.projectName -> com_sales_order.source_project_name; 业务字段/正式关系
  s.`projectName` AS `raw__source_project_name`,
  -- pm_order_data_from_erp.orderComment -> com_sales_order.order_comment; 业务字段/正式关系
  s.`orderComment` AS `raw__order_comment`,
  -- pm_order_data_from_erp.orderType -> com_sales_order.order_type; 业务字段/正式关系
  s.`orderType` AS `raw__order_type`,
  -- pm_order_data_from_erp.compCode -> com_sales_order.company_code；com_sales_order.company_id; 业务字段/正式关系
  s.`compCode` AS `raw__company_code`,
  -- pm_order_data_from_erp.salesType -> com_sales_order.sales_type; 业务字段/正式关系
  s.`salesType` AS `raw__sales_type`,
  -- pm_order_data_from_erp.source -> com_sales_order.source_system; 业务字段/正式关系
  s.`source` AS `raw__source_system`,
  -- pm_order_data_from_erp.customInfo -> com_sales_order.custom_info; 业务字段/正式关系
  s.`customInfo` AS `raw__custom_info`,
  -- pm_order_data_from_erp.syncTime -> com_sales_order.source_updated_at; 业务字段/正式关系
  s.`syncTime` AS `raw__source_updated_at`
FROM `pm_order_data_from_erp` s;

-- SOURCE pm_order_line_from_erp; dialect=MYSQL
SELECT
  -- pm_order_line_from_erp.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_order_line_from_erp.orderNumber -> com_sales_order_line.order_no; 业务字段/正式关系
  s.`orderNumber` AS `raw__order_no`,
  -- pm_order_line_from_erp.lineNum -> com_sales_order_line.line_no; 业务字段/正式关系
  s.`lineNum` AS `raw__line_no`,
  -- pm_order_line_from_erp.itemCode -> com_sales_order_line.product_code; 业务字段/正式关系
  s.`itemCode` AS `raw__product_code`,
  -- pm_order_line_from_erp.itemDesc -> com_sales_order_line.product_desc; 业务字段/正式关系
  s.`itemDesc` AS `raw__product_desc`,
  -- pm_order_line_from_erp.orderQuantity -> com_sales_order_line.order_qty; 业务字段/正式关系
  s.`orderQuantity` AS `raw__order_qty`,
  -- pm_order_line_from_erp.openQuantity -> com_sales_order_line.open_qty; 业务字段/正式关系
  s.`openQuantity` AS `raw__open_qty`,
  -- pm_order_line_from_erp.bundleCode -> com_sales_order_line.bundle_code; 业务字段/正式关系
  s.`bundleCode` AS `raw__bundle_code`,
  -- pm_order_line_from_erp.warrantyMonth -> com_sales_order_line.warranty_month; 业务字段/正式关系
  s.`warrantyMonth` AS `raw__warranty_month`,
  -- pm_order_line_from_erp.lineType -> com_sales_order_line.line_type; 业务字段/正式关系
  s.`lineType` AS `raw__line_type`,
  -- pm_order_line_from_erp.compCode -> com_sales_order_line.company_code；com_sales_order_line.company_id; 业务字段/正式关系
  s.`compCode` AS `raw__company_code`,
  -- pm_order_line_from_erp.profitCenter -> com_sales_order_line.profit_center; 业务字段/正式关系
  s.`profitCenter` AS `raw__profit_center`,
  -- pm_order_line_from_erp.realOrderExecNumber -> com_sales_order_line.real_execution_no; 业务字段/正式关系
  s.`realOrderExecNumber` AS `raw__real_execution_no`,
  -- pm_order_line_from_erp.source -> com_sales_order_line.source_system; 业务字段/正式关系
  s.`source` AS `raw__source_system`,
  -- pm_order_line_from_erp.customInfo -> com_sales_order_line.custom_info; 业务字段/正式关系
  s.`customInfo` AS `raw__custom_info`,
  -- pm_order_line_from_erp.syncTime -> com_sales_order_line.source_updated_at; 业务字段/正式关系
  s.`syncTime` AS `raw__source_updated_at`
FROM `pm_order_line_from_erp` s;
