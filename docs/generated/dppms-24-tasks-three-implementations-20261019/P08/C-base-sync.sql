-- 最新原ID/用户名规则及P18/P22/源范围SQL变更见 ../base-sync-original-identifiers-preview-20261019.md 与 .sql；以下保留初次汇总SQL。
-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P08 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectDictionaryType] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P08SourceMapper.xml
SELECT id AS raw_id,dataTypeCode AS data_type_code,dataTypeName AS data_type_name,status AS raw_status,createTime AS create_time,createBy AS raw_creator,updateTime AS update_time,updateBy AS raw_updater,effectiveFrom AS effective_from,effectiveTo AS effective_to,0 AS definition_missing,1 AS dictionary_type FROM fnd_basic_data_type LIMIT #{limit};

-- [selectDictionaryItem] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P08SourceMapper.xml
SELECT id AS raw_id,dataTypeCode AS data_type_code,basicDataId AS basic_data_id,basicDataName AS basic_data_name,basicDataAttri1 AS basic_data_attri1,sortId AS sort_id,createTime AS create_time,createBy AS raw_creator,updateTime AS update_time,updateBy AS raw_updater,effectiveFrom AS effective_from,effectiveTo AS effective_to FROM fnd_basic_data ORDER BY id LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P08DictionarySyncTask$TargetMapper.selectTypes; pms-module-integration/src/main/resources/mapper/basesync/P08TargetMapper.xml:4; kind=select
SELECT `id`,`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time` FROM system_dict_type WHERE (deleted=FALSE AND (1=0  /* IF test=query.codes != null and !query.codes.isEmpty() */ OR `type` IN  /* FOREACH collection=query.codes; item=code; open=(; close=); separator=, */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */  /* IF test=query.names != null and !query.names.isEmpty() */ OR name IN  /* FOREACH collection=query.names; item=name; open=(; close=); separator=, */ (#{name}) /* repeat with separator=,; END FOREACH */ /* END IF */ ))  /* IF test=query.ids != null and !query.ids.isEmpty() */ OR id IN  /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END IF */  ORDER BY id  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P08DictionarySyncTask$TargetMapper.selectItems; pms-module-integration/src/main/resources/mapper/basesync/P08TargetMapper.xml:5; kind=select
SELECT `id`,`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM system_dict_data WHERE (deleted=FALSE AND (1=0  /* IF test=query.codes != null and !query.codes.isEmpty() */ OR `dict_type` IN  /* FOREACH collection=query.codes; item=code; open=(; close=); separator=, */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ ))  /* IF test=query.ids != null and !query.ids.isEmpty() */ OR id IN  /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END IF */  ORDER BY id  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P08DictionarySyncTask$TargetMapper.insertDictionaryType; pms-module-integration/src/main/resources/mapper/basesync/P08TargetMapper.xml:6; kind=insert
INSERT INTO system_dict_type (`id`,`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.name},#{row.target.type},#{row.target.status},#{row.target.remark},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted},#{row.target.deletedTime}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P08DictionarySyncTask$TargetMapper.insertDictionaryItem; pms-module-integration/src/main/resources/mapper/basesync/P08TargetMapper.xml:7; kind=insert
INSERT INTO system_dict_data (`id`,`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.sort},#{row.target.label},#{row.target.value},#{row.target.dictType},#{row.target.status},#{row.target.colorType},#{row.target.cssClass},#{row.target.remark},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
