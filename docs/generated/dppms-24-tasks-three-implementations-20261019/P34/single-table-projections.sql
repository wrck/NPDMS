-- P34 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE fb_soft_version; dialect=MYSQL
SELECT
  -- fb_soft_version.serial_number -> ast_device_factory_version_record.device_sn; 业务字段/正式关系
  s.`serial_number` AS `raw__device_sn`,
  -- fb_soft_version.conp -> ast_device_factory_version_record.conp_version; 业务字段/正式关系
  s.`conp` AS `raw__conp_version`,
  -- fb_soft_version.cpld -> ast_device_factory_version_record.cpld_version; 业务字段/正式关系
  s.`cpld` AS `raw__cpld_version`,
  -- fb_soft_version.boot -> ast_device_factory_version_record.boot_version; 业务字段/正式关系
  s.`boot` AS `raw__boot_version`,
  -- fb_soft_version.pcb -> ast_device_factory_version_record.pcb_version; 业务字段/正式关系
  s.`pcb` AS `raw__pcb_version`
FROM `fb_soft_version` s;
