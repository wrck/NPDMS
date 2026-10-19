-- P08 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE fnd_basic_data_type; dialect=MYSQL
SELECT
  -- fnd_basic_data_type.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- fnd_basic_data_type.dataTypeCode -> system_dict_type.type; 业务字段/正式关系
  s.`dataTypeCode` AS `raw__type`,
  -- fnd_basic_data_type.dataTypeName -> system_dict_type.name; 业务字段/正式关系
  s.`dataTypeName` AS `raw__name`,
  -- fnd_basic_data_type.status -> 不迁入; 已批准排除
  s.`status` AS `raw__status`,
  -- fnd_basic_data_type.createTime -> system_dict_type.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- fnd_basic_data_type.createBy -> system_dict_type.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- fnd_basic_data_type.updateTime -> system_dict_type.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`,
  -- fnd_basic_data_type.updateBy -> system_dict_type.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`,
  -- fnd_basic_data_type.effectiveFrom -> 不迁入; 已批准排除
  s.`effectiveFrom` AS `raw__effectiveFrom`,
  -- fnd_basic_data_type.effectiveTo -> 不迁入; 已批准排除
  s.`effectiveTo` AS `raw__effectiveTo`
FROM `fnd_basic_data_type` s;

-- SOURCE fnd_basic_data; dialect=MYSQL
SELECT
  -- fnd_basic_data.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- fnd_basic_data.dataTypeCode -> system_dict_data.dict_type; 业务字段/正式关系
  s.`dataTypeCode` AS `raw__dict_type`,
  -- fnd_basic_data.basicDataId -> system_dict_data.value; 业务字段/正式关系
  s.`basicDataId` AS `raw__value`,
  -- fnd_basic_data.basicDataName -> system_dict_data.label; 业务字段/正式关系
  s.`basicDataName` AS `raw__label`,
  -- fnd_basic_data.basicDataAttri1 -> 不迁入; 已批准排除
  s.`basicDataAttri1` AS `raw__basicDataAttri1`,
  -- fnd_basic_data.sortId -> system_dict_data.sort; 业务字段/正式关系
  s.`sortId` AS `raw__sort`,
  -- fnd_basic_data.createTime -> system_dict_data.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- fnd_basic_data.createBy -> system_dict_data.creator; 业务字段/正式关系
  s.`createBy` AS `raw__creator`,
  -- fnd_basic_data.updateTime -> system_dict_data.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`,
  -- fnd_basic_data.updateBy -> system_dict_data.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updater`,
  -- fnd_basic_data.effectiveFrom -> 不迁入; 已批准排除
  s.`effectiveFrom` AS `raw__effectiveFrom`,
  -- fnd_basic_data.effectiveTo -> 不迁入; 已批准排除
  s.`effectiveTo` AS `raw__effectiveTo`
FROM `fnd_basic_data` s;
