-- P37 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_license_info_from_license; dialect=MYSQL
SELECT
  -- pm_project_license_info_from_license.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_license_info_from_license.licenseCode -> ast_device_license_record.license_code_ciphertext; 受控加密字段
  s.`licenseCode` AS `raw__license_code_ciphertext`,
  -- pm_project_license_info_from_license.sn -> ast_device_license_record.device_sn; 业务字段/正式关系
  s.`sn` AS `raw__device_sn`,
  -- pm_project_license_info_from_license.specModel -> ast_device_license_record.product_model; 业务字段/正式关系
  s.`specModel` AS `raw__product_model`,
  -- pm_project_license_info_from_license.contract -> ast_device_license_record.contract_no; 业务字段/正式关系
  s.`contract` AS `raw__contract_no`,
  -- pm_project_license_info_from_license.contractType -> ast_device_license_record.contract_type; 业务字段/正式关系
  s.`contractType` AS `raw__contract_type`,
  -- pm_project_license_info_from_license.item -> ast_device_license_record.product_code; 业务字段/正式关系
  s.`item` AS `raw__product_code`,
  -- pm_project_license_info_from_license.status -> ast_device_license_record.license_status; 业务字段/正式关系
  s.`status` AS `raw__license_status`
FROM `pm_project_license_info_from_license` s;
