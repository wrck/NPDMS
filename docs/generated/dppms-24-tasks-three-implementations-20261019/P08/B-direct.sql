-- P08 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [fnd_basic_data_type] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT t.id,t.dataTypeCode,t.dataTypeName,t.status,t.createTime,t.createBy,t.updateTime,t.updateBy,t.effectiveFrom,t.effectiveTo,0 AS definitionMissing FROM fnd_basic_data_type t;

-- [fnd_basic_data_missing_type] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT m.id AS sourceId,NULL AS id,m.dataTypeCode,m.dataTypeCode AS dataTypeName,NULL AS status,NULL AS createTime,NULL AS createBy,NULL AS updateTime,NULL AS updateBy,NULL AS effectiveFrom,NULL AS effectiveTo,1 AS definitionMissing,JSON_OBJECT('table','fnd_basic_data','id',m.id,'rawFields',JSON_OBJECT('id',m.`id`,'dataTypeCode',m.`dataTypeCode`,'basicDataId',m.`basicDataId`,'basicDataName',m.`basicDataName`,'basicDataAttri1',m.`basicDataAttri1`,'sortId',m.`sortId`,'createTime',m.`createTime`,'createBy',m.`createBy`,'updateTime',m.`updateTime`,'updateBy',m.`updateBy`,'effectiveFrom',m.`effectiveFrom`,'effectiveTo',m.`effectiveTo`)) AS sourceAlias FROM (SELECT MIN(id) AS sourceId FROM fnd_basic_data GROUP BY dataTypeCode) g JOIN fnd_basic_data m ON m.id=g.sourceId LEFT JOIN fnd_basic_data_type t ON t.dataTypeCode=m.dataTypeCode WHERE t.id IS NULL;

-- [fnd_basic_data] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT * FROM fnd_basic_data ORDER BY id;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDictionaryDataMapper.selectConflictingValuesForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectDictionaryDataMapper.xml:5; kind=select
SELECT id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted
        FROM system_dict_data
        WHERE (deleted=FALSE AND dict_type=#{query.dictType} AND value=#{query.value})
         /* IF test=query.targetId != null */ OR id=#{query.targetId} /* END IF */ 
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDictionaryDataMapper.selectTypesForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectDictionaryDataMapper.xml:12; kind=select
SELECT id,name,type,status,remark,creator,create_time,updater,update_time,deleted,deleted_time
        FROM system_dict_type WHERE type=#{query.type} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDictionaryTypeMapper.selectConflictingDefinitionsForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectDictionaryTypeMapper.xml:5; kind=select
SELECT id,name,type,status,remark,creator,create_time,updater,update_time,deleted,deleted_time
        FROM system_dict_type
        WHERE (deleted=FALSE AND (type=#{query.type} OR name=#{query.name}))
         /* IF test=query.targetId != null */ OR id=#{query.targetId} /* END IF */ 
        ORDER BY id FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDictionaryDataMapper.insertHistorical; pms-module-integration/src/main/resources/mapper/sync/DirectDictionaryDataMapper.xml:16; kind=insert
INSERT INTO system_dict_data
            (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,create_time,updater,update_time,deleted)
        VALUES (#{command.row.id},#{command.row.sort},#{command.row.label},#{command.row.value},#{command.row.dictType},
            #{command.row.status},#{command.row.colorType},#{command.row.cssClass},#{command.row.remark},#{command.row.creator},
            #{command.row.createTime},#{command.row.updater},#{command.row.updateTime},#{command.row.deleted});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDictionaryTypeMapper.insertHistorical; pms-module-integration/src/main/resources/mapper/sync/DirectDictionaryTypeMapper.xml:12; kind=insert
INSERT INTO system_dict_type
            (id,name,type,status,remark,creator,create_time,updater,update_time,deleted,deleted_time)
        VALUES (#{command.row.id},#{command.row.name},#{command.row.type},#{command.row.status},#{command.row.remark},
            #{command.row.creator},#{command.row.createTime},#{command.row.updater},#{command.row.updateTime},
            #{command.row.deleted},#{command.row.deletedTime});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
