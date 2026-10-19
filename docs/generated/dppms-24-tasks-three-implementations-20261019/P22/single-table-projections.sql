-- P22 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_product_line; dialect=MYSQL
SELECT
  -- pm_project_product_line.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_product_line.projectId -> com_delivery_scope.project_id; 业务字段/正式关系
  s.`projectId` AS `raw__project_id`,
  -- pm_project_product_line.contractNo -> com_sales_order.contract_no; 业务字段/正式关系
  s.`contractNo` AS `raw__contract_no`,
  -- pm_project_product_line.itemCode -> com_sales_order_line.product_code ＋ com_delivery_scope_detail.product_code; 业务字段/正式关系
  s.`itemCode` AS `raw__product_code`,
  -- pm_project_product_line.itemName -> com_delivery_scope.product_desc; 业务字段/正式关系
  s.`itemName` AS `raw__product_desc`,
  -- pm_project_product_line.projectQuantity -> com_delivery_scope.allocated_qty; 业务字段/正式关系
  s.`projectQuantity` AS `raw__allocated_qty`,
  -- pm_project_product_line.orderQuantity -> com_sales_order_line.order_qty; 业务字段/正式关系
  s.`orderQuantity` AS `raw__order_qty`,
  -- pm_project_product_line.deliverQuantity -> com_delivery_scope.delivered_quantity; 业务字段/正式关系
  s.`deliverQuantity` AS `raw__delivered_quantity`,
  -- pm_project_product_line.openQuantity -> com_delivery_scope.open_quantity; 业务字段/正式关系
  s.`openQuantity` AS `raw__open_quantity`,
  -- pm_project_product_line.orderNumber -> com_sales_order_line.order_no; 业务字段/正式关系
  s.`orderNumber` AS `raw__order_no`,
  -- pm_project_product_line.lineNum -> com_sales_order_line.line_no; 业务字段/正式关系
  s.`lineNum` AS `raw__line_no`
FROM `pm_project_product_line` s;
