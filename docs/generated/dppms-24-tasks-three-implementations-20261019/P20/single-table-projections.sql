-- P20 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_product_line_real; dialect=MYSQL
SELECT
  -- pm_project_product_line_real.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_product_line_real.projectId -> proj_project_real_product.project_id; 业务字段/正式关系
  s.`projectId` AS `raw__project_id`,
  -- pm_project_product_line_real.contractNo -> proj_project_real_product.contract_no; 业务字段/正式关系
  s.`contractNo` AS `raw__contract_no`,
  -- pm_project_product_line_real.projectCode -> proj_project_real_product.project_code; 业务字段/正式关系
  s.`projectCode` AS `raw__project_code`,
  -- pm_project_product_line_real.orderExecNumber -> proj_project_real_product.execution_no; 业务字段/正式关系
  s.`orderExecNumber` AS `raw__execution_no`,
  -- pm_project_product_line_real.productFirstName -> proj_project_real_product.product_category_name; 业务字段/正式关系
  s.`productFirstName` AS `raw__product_category_name`,
  -- pm_project_product_line_real.productName -> proj_project_real_product.product_name; 业务字段/正式关系
  s.`productName` AS `raw__product_name`,
  -- pm_project_product_line_real.productSubCode -> proj_project_real_product.product_sub_code; 业务字段/正式关系
  s.`productSubCode` AS `raw__product_sub_code`,
  -- pm_project_product_line_real.productSubModel -> proj_project_real_product.product_sub_model; 业务字段/正式关系
  s.`productSubModel` AS `raw__product_sub_model`,
  -- pm_project_product_line_real.productSubName -> proj_project_real_product.product_sub_name; 业务字段/正式关系
  s.`productSubName` AS `raw__product_sub_name`,
  -- pm_project_product_line_real.num -> proj_project_real_product.quantity; 业务字段/正式关系
  s.`num` AS `raw__quantity`,
  -- pm_project_product_line_real.memo -> proj_project_real_product.memo; 业务字段/正式关系
  s.`memo` AS `raw__memo`
FROM `pm_project_product_line_real` s;
