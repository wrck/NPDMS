-- P25 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE fb_service; dialect=MYSQL
SELECT
  -- fb_service.id -> int_sync_binding.source_key; 技术绑定
  s.`id` AS `raw__id`,
  -- fb_service.con_xb -> ast_device_warranty_renewal_record.warranty_contract_no; 正式字段承接
  s.`con_xb` AS `raw__warranty_contract_no`,
  -- fb_service.barcode -> ast_device_warranty_renewal_record.device_sn; 正式字段承接
  s.`barcode` AS `raw__device_sn`,
  -- fb_service.grade -> ast_device_warranty_renewal_record.warranty_grade; 正式字段承接
  s.`grade` AS `raw__warranty_grade`,
  -- fb_service.begin_date -> ast_device_warranty_renewal_record.warranty_start_date; 正式字段承接
  s.`begin_date` AS `raw__warranty_start_date`,
  -- fb_service.end_date -> ast_device_warranty_renewal_record.warranty_end_date; 正式字段承接
  s.`end_date` AS `raw__warranty_end_date`,
  -- fb_service.warranty -> ast_device_warranty_renewal_record.warranty_months; 正式字段承接
  s.`warranty` AS `raw__warranty_months`,
  -- fb_service.remark -> ast_device_warranty_renewal_record.remark; 正式字段承接
  s.`remark` AS `raw__remark`,
  -- fb_service.isyb -> ast_device_warranty_renewal_record.extended; 正式字段承接
  s.`isyb` AS `raw__extended`,
  -- fb_service.state -> ast_device_warranty_renewal_record.is_latest; 正式字段承接
  s.`state` AS `raw__is_latest`
FROM `fb_service` s;
