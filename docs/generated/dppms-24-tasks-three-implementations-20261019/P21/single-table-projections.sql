-- P21 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_product_lease_line_from_crm; dialect=MYSQL
SELECT
  -- pm_project_product_lease_line_from_crm.projectCode -> proj_project_lease_product.project_code ＋ project_id关联依据; 业务字段/正式关系
  s.`projectCode` AS `raw__project_code`,
  -- pm_project_product_lease_line_from_crm.orderExecNumber -> proj_project_lease_product.execution_no; 业务字段/正式关系
  s.`orderExecNumber` AS `raw__execution_no`,
  -- pm_project_product_lease_line_from_crm.productFirstName -> proj_project_lease_product.product_category; 业务字段/正式关系
  s.`productFirstName` AS `raw__product_category`,
  -- pm_project_product_lease_line_from_crm.productName -> proj_project_lease_product.product_type; 业务字段/正式关系
  s.`productName` AS `raw__product_type`,
  -- pm_project_product_lease_line_from_crm.productSubCode -> proj_project_lease_product.product_code; 业务字段/正式关系
  s.`productSubCode` AS `raw__product_code`,
  -- pm_project_product_lease_line_from_crm.productSubModel -> proj_project_lease_product.product_model; 业务字段/正式关系
  s.`productSubModel` AS `raw__product_model`,
  -- pm_project_product_lease_line_from_crm.productSubName -> proj_project_lease_product.product_name; 业务字段/正式关系
  s.`productSubName` AS `raw__product_name`,
  -- pm_project_product_lease_line_from_crm.num -> proj_project_lease_product.quantity; 业务字段/正式关系
  s.`num` AS `raw__quantity`,
  -- pm_project_product_lease_line_from_crm.memo -> proj_project_lease_product.memo; 业务字段/正式关系
  s.`memo` AS `raw__memo`,
  -- pm_project_product_lease_line_from_crm.leaseDuration -> proj_project_lease_product.lease_duration; 业务字段/正式关系
  s.`leaseDuration` AS `raw__lease_duration`,
  -- pm_project_product_lease_line_from_crm.dataSource -> proj_project_lease_product.data_source; 业务字段/正式关系
  s.`dataSource` AS `raw__data_source`
FROM `pm_project_product_lease_line_from_crm` s;

-- SOURCE pm_project_product_config_level_info_from_crm; dialect=MYSQL
SELECT
  -- pm_project_product_config_level_info_from_crm.projectCode -> proj_project_product_configuration.project_code ＋ project_id关联依据; 业务字段/正式关系
  s.`projectCode` AS `raw__project_code`,
  -- pm_project_product_config_level_info_from_crm.orderExecNumber -> proj_project_product_configuration.execution_no; 业务字段/正式关系
  s.`orderExecNumber` AS `raw__execution_no`,
  -- pm_project_product_config_level_info_from_crm.itemGroup -> proj_project_product_configuration.item_group; 业务字段/正式关系
  s.`itemGroup` AS `raw__item_group`,
  -- pm_project_product_config_level_info_from_crm.itemCode -> proj_project_product_configuration.product_code; 业务字段/正式关系
  s.`itemCode` AS `raw__product_code`,
  -- pm_project_product_config_level_info_from_crm.parentCode -> proj_project_product_configuration.parent_code; 业务字段/正式关系
  s.`parentCode` AS `raw__parent_code`,
  -- pm_project_product_config_level_info_from_crm.quantity -> proj_project_product_configuration.quantity; 业务字段/正式关系
  s.`quantity` AS `raw__quantity`,
  -- pm_project_product_config_level_info_from_crm.bomPaths -> proj_project_product_configuration.bom_path; 业务字段/正式关系
  s.`bomPaths` AS `raw__bom_path`,
  -- pm_project_product_config_level_info_from_crm.itemModel -> proj_project_product_configuration.product_model; 业务字段/正式关系
  s.`itemModel` AS `raw__product_model`,
  -- pm_project_product_config_level_info_from_crm.itemDesc -> proj_project_product_configuration.product_description; 业务字段/正式关系
  s.`itemDesc` AS `raw__product_description`,
  -- pm_project_product_config_level_info_from_crm.level -> proj_project_product_configuration.level; 业务字段/正式关系
  s.`level` AS `raw__level`,
  -- pm_project_product_config_level_info_from_crm.dataSource -> proj_project_product_configuration.data_source; 业务字段/正式关系
  s.`dataSource` AS `raw__data_source`
FROM `pm_project_product_config_level_info_from_crm` s;
