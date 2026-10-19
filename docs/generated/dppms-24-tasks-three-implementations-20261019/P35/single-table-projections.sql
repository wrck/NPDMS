-- P35 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_soft_change_logs; dialect=MYSQL
SELECT
  -- pm_project_soft_change_logs.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_soft_change_logs.projectId -> ast_device_software_change_batch.project_id; 业务字段/正式关系
  s.`projectId` AS `raw__project_id`,
  -- pm_project_soft_change_logs.changeVersion -> ast_device_software_change_batch.change_version; 业务字段/正式关系
  s.`changeVersion` AS `raw__change_version`,
  -- pm_project_soft_change_logs.changeRemark -> ast_device_software_change_batch.change_remark; 业务字段/正式关系
  s.`changeRemark` AS `raw__change_remark`,
  -- pm_project_soft_change_logs.latest -> ast_device_software_change_batch.latest; 业务字段/正式关系
  s.`latest` AS `raw__latest`,
  -- pm_project_soft_change_logs.createBy -> ast_device_software_change_batch.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- pm_project_soft_change_logs.createTime -> ast_device_software_change_batch.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- pm_project_soft_change_logs.updateBy -> ast_device_software_change_batch.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`,
  -- pm_project_soft_change_logs.updateTime -> ast_device_software_change_batch.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`
FROM `pm_project_soft_change_logs` s;

-- SOURCE pm_project_soft_version; dialect=MYSQL
SELECT
  -- pm_project_soft_version.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_soft_version.projectId -> ast_device_software_version_record.project_id; 业务字段/正式关系
  s.`projectId` AS `raw__project_id`,
  -- pm_project_soft_version.logId -> ast_device_software_version_record.change_batch_id; 业务字段/正式关系
  s.`logId` AS `raw__change_batch_id`,
  -- pm_project_soft_version.contractNo -> ast_device_software_version_record.contract_no; 业务字段/正式关系
  s.`contractNo` AS `raw__contract_no`,
  -- pm_project_soft_version.itemCode -> ast_device_software_version_record.product_code; 业务字段/正式关系
  s.`itemCode` AS `raw__product_code`,
  -- pm_project_soft_version.barCode -> ast_device_software_version_record.device_sn; 业务字段/正式关系
  s.`barCode` AS `raw__device_sn`,
  -- pm_project_soft_version.conp -> ast_device_software_version_record.conp_version; 业务字段/正式关系
  s.`conp` AS `raw__conp_version`,
  -- pm_project_soft_version.conpType -> ast_device_software_version_record.conp_type; 业务字段/正式关系
  s.`conpType` AS `raw__conp_type`,
  -- pm_project_soft_version.conpSeries -> ast_device_software_version_record.conp_series; 业务字段/正式关系
  s.`conpSeries` AS `raw__conp_series`,
  -- pm_project_soft_version.conpMark -> ast_device_software_version_record.conp_mark; 业务字段/正式关系
  s.`conpMark` AS `raw__conp_mark`,
  -- pm_project_soft_version.conpBak -> plt_entity_extension_value.values_json.conp_bak（SoftwareVersionRecord）; 已批准正式实体扩展
  s.`conpBak` AS `raw__conpBak`,
  -- pm_project_soft_version.conpChange -> plt_entity_extension_value.values_json.conp_change（SoftwareVersionRecord）; 已批准正式实体扩展
  s.`conpChange` AS `raw__conpChange`,
  -- pm_project_soft_version.cpld -> ast_device_software_version_record.cpld_version; 业务字段/正式关系
  s.`cpld` AS `raw__cpld_version`,
  -- pm_project_soft_version.cpldBak -> plt_entity_extension_value.values_json.cpld_bak（SoftwareVersionRecord）; 已批准正式实体扩展
  s.`cpldBak` AS `raw__cpldBak`,
  -- pm_project_soft_version.cpldChange -> plt_entity_extension_value.values_json.cpld_change（SoftwareVersionRecord）; 已批准正式实体扩展
  s.`cpldChange` AS `raw__cpldChange`,
  -- pm_project_soft_version.boot -> ast_device_software_version_record.boot_version; 业务字段/正式关系
  s.`boot` AS `raw__boot_version`,
  -- pm_project_soft_version.bootBak -> plt_entity_extension_value.values_json.boot_bak（SoftwareVersionRecord）; 已批准正式实体扩展
  s.`bootBak` AS `raw__bootBak`,
  -- pm_project_soft_version.bootChange -> plt_entity_extension_value.values_json.boot_change（SoftwareVersionRecord）; 已批准正式实体扩展
  s.`bootChange` AS `raw__bootChange`,
  -- pm_project_soft_version.pcb -> ast_device_software_version_record.pcb_version; 业务字段/正式关系
  s.`pcb` AS `raw__pcb_version`,
  -- pm_project_soft_version.pcbBak -> plt_entity_extension_value.values_json.pcb_bak（SoftwareVersionRecord）; 已批准正式实体扩展
  s.`pcbBak` AS `raw__pcbBak`,
  -- pm_project_soft_version.pcbChange -> plt_entity_extension_value.values_json.pcb_change（SoftwareVersionRecord）; 已批准正式实体扩展
  s.`pcbChange` AS `raw__pcbChange`,
  -- pm_project_soft_version.executeTime -> ast_device_software_version_record.execute_time; 业务字段/正式关系
  s.`executeTime` AS `raw__execute_time`,
  -- pm_project_soft_version.datastate -> ast_device_software_version_record.data_state; 业务字段/正式关系
  s.`datastate` AS `raw__data_state`,
  -- pm_project_soft_version.createTime -> ast_device_software_version_record.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- pm_project_soft_version.createBy -> ast_device_software_version_record.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- pm_project_soft_version.updateTime -> ast_device_software_version_record.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`,
  -- pm_project_soft_version.updateBy -> ast_device_software_version_record.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`,
  -- pm_project_soft_version.customInfo -> ast_device_software_version_record.custom_info; 业务字段/正式关系
  s.`customInfo` AS `raw__custom_info`
FROM `pm_project_soft_version` s;
