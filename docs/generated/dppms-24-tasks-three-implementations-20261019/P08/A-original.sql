-- P08 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [fnd_basic_data_type] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P08.json
SELECT HEX(CAST(typeCode AS BINARY)) AS _groupKey,
  CAST(JSON_MERGE_PRESERVE(
    CAST(CASE WHEN SUM(objectName='fnd_basic_data_type')=0 THEN
      JSON_ARRAY(JSON_OBJECT('object','fnd_basic_data_type','fields',JSON_OBJECT('dataTypeCode',typeCode,'id',NULL,'dataTypeName',typeCode,'status',NULL,'createTime',NULL,'createBy',NULL,'updateTime',NULL,'updateBy',NULL,'effectiveFrom',NULL,'effectiveTo',NULL,'definitionMissing',1))) ELSE JSON_ARRAY() END AS JSON),
    JSON_ARRAYAGG(JSON_OBJECT('object',objectName,'fields',CAST(fields AS JSON)))) AS CHAR) AS _sourceRows
FROM (
 SELECT r.dataTypeCode AS typeCode,'fnd_basic_data_type' AS objectName,JSON_OBJECT('dataTypeCode',r.`dataTypeCode`,'id',r.`id`,'dataTypeName',r.`dataTypeName`,'status',r.`status`,'createTime',r.`createTime`,'createBy',r.`createBy`,'updateTime',r.`updateTime`,'updateBy',r.`updateBy`,'effectiveFrom',r.`effectiveFrom`,'effectiveTo',r.`effectiveTo`,'definitionMissing',0) AS fields FROM fnd_basic_data_type r
 UNION ALL
 SELECT r.dataTypeCode AS typeCode,'fnd_basic_data' AS objectName,JSON_OBJECT('id',r.`id`,'dataTypeCode',r.`dataTypeCode`,'basicDataId',r.`basicDataId`,'basicDataName',r.`basicDataName`,'basicDataAttri1',r.`basicDataAttri1`,'sortId',r.`sortId`,'createTime',r.`createTime`,'createBy',r.`createBy`,'updateTime',r.`updateTime`,'updateBy',r.`updateBy`,'effectiveFrom',r.`effectiveFrom`,'effectiveTo',r.`effectiveTo`) AS fields FROM fnd_basic_data r
) combined
GROUP BY typeCode,CAST(typeCode AS BINARY);

-- [fnd_basic_data] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P08.json
-- queryRef=fnd_basic_data_type; 复用该来源结果/分组，不重复执行一份SELECT。;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper.selectMaintenance; pms-module-integration/src/main/resources/mapper/sync/SyncRunMapper.xml:8; kind=select
SELECT id, status, config_snapshot FROM int_sync_run WHERE tenant_id=#{tenantId} AND deleted=FALSE
      AND (cache_pending=TRUE OR (status IN ('QUEUED','READING','VALIDATING','APPLYING') AND started_at < #{now}))
      ORDER BY id LIMIT 100;

-- cn.iocoder.yudao.module.system.dal.mysql.dict.DictionaryDataMigrationMapper.selectConflictingValuesForUpdate; yudao-module-system/src/main/resources/mapper/dict/DictionaryDataMigrationMapper.xml:5; kind=select
SELECT id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted
        FROM system_dict_data
        WHERE (deleted=b'0' AND dict_type=#{query.dictType} AND value=#{query.value})
         /* IF test=query.targetId != null */ OR id=#{query.targetId} /* END IF */ 
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.dict.DictionaryDataMigrationMapper.selectTypesForUpdate; yudao-module-system/src/main/resources/mapper/dict/DictionaryDataMigrationMapper.xml:12; kind=select
SELECT id,name,type,status,remark,creator,create_time,updater,update_time,deleted,deleted_time
        FROM system_dict_type WHERE type=#{query.type} AND deleted=b'0' ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.dict.DictionaryTypeMigrationMapper.selectConflictingDefinitionsForUpdate; yudao-module-system/src/main/resources/mapper/dict/DictionaryTypeMigrationMapper.xml:5; kind=select
SELECT id,name,type,status,remark,creator,create_time,updater,update_time,deleted,deleted_time
        FROM system_dict_type
        WHERE (deleted=b'0' AND (type=#{query.type} OR name=#{query.name}))
         /* IF test=query.targetId != null */ OR id=#{query.targetId} /* END IF */ 
        ORDER BY id FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.system.dal.mysql.dict.DictionaryDataMigrationMapper.insertHistorical; yudao-module-system/src/main/resources/mapper/dict/DictionaryDataMigrationMapper.xml:16; kind=insert
INSERT INTO system_dict_data
            (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
        VALUES (#{command.row.id},#{command.row.sort},#{command.row.label},#{command.row.value},#{command.row.dictType},
            #{command.row.status},#{command.row.colorType},#{command.row.cssClass},#{command.row.remark},#{command.row.creator},
            #{command.row.createTime},#{command.row.updater},#{command.row.updateTime},#{command.row.deleted});

-- cn.iocoder.yudao.module.system.dal.mysql.dict.DictionaryTypeMigrationMapper.insertHistorical; yudao-module-system/src/main/resources/mapper/dict/DictionaryTypeMigrationMapper.xml:12; kind=insert
INSERT INTO system_dict_type
            (id,name,type,status,remark,creator,create_time,updater,update_time,deleted,deleted_time)
        VALUES (#{command.row.id},#{command.row.name},#{command.row.type},#{command.row.status},#{command.row.remark},
            #{command.row.creator},#{command.row.createTime},#{command.row.updater},#{command.row.updateTime},
            #{command.row.deleted},#{command.row.deletedTime});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
