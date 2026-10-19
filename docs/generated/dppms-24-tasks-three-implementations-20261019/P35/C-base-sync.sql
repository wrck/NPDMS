-- 当前实现优先见 ../base-sync-department-code-preview-20261019.sql 与 .md；本文件以下为早期冻结SQL。
-- 最新原ID/用户名规则及P18/P22/源范围SQL变更见 ../base-sync-original-identifiers-preview-20261019.md 与 .sql；以下保留初次汇总SQL。
-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P35 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectSoftwareBatch] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P35SourceMapper.xml
SELECT s.id AS sourceId,s.projectId AS originalProjectId,s.`changeVersion`,s.`changeRemark`,s.`latest`,s.createTime,s.createBy AS source_creator,s.updateTime,s.updateBy AS source_updater FROM pm_project_soft_change_logs s  /* CHOOSE  */ /* WHEN test=projectKeys != null and !projectKeys.isEmpty() */ WHERE s.projectId IN  /* FOREACH collection=projectKeys; item=key; open=(; close=); separator=, */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ WHERE 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY s.id LIMIT #{limit};

-- [selectSoftwareObservation] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P35SourceMapper.xml
SELECT s.id AS sourceId,s.projectId AS originalProjectId,s.`logId`,s.`contractNo`,s.`itemCode`,s.`barCode`,s.`conp`,s.`conpType`,s.`conpSeries`,s.`conpMark`,s.`conpBak`,s.`conpChange`,s.`cpld`,s.`cpldBak`,s.`cpldChange`,s.`boot`,s.`bootBak`,s.`bootChange`,s.`pcb`,s.`pcbBak`,s.`pcbChange`,s.`executeTime`,s.`datastate`,s.`customInfo`,s.createTime,s.createBy AS source_creator,s.updateTime,s.updateBy AS source_updater FROM pm_project_soft_version s  /* CHOOSE  */ /* WHEN test=projectKeys != null and !projectKeys.isEmpty() */ WHERE s.projectId IN  /* FOREACH collection=projectKeys; item=key; open=(; close=); separator=, */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ WHERE 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY s.id LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$TargetMapper.selectBatches; pms-module-integration/src/main/resources/mapper/basesync/P35TargetMapper.xml:3; kind=select
SELECT `id`,`tenant_id`,`project_id`,`change_version`,`change_remark`,`latest`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM ast_device_software_change_batch WHERE tenant_id=#{query.tenantId}  /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$TargetMapper.selectObservations; pms-module-integration/src/main/resources/mapper/basesync/P35TargetMapper.xml:5; kind=select
SELECT `id`,`tenant_id`,`device_id`,`device_sn`,`project_id`,`previous_record_id`,`conp_version`,`conp_type`,`conp_series`,`conp_mark`,`boot_version`,`cpld_version`,`pcb_version`,`customized`,`release_date`,`version_desc`,`recorded_at`,`data_source`,`parsed_fact_id`,`request_key`,`change_batch_id`,`contract_no`,`product_code`,`execute_time`,`data_state`,`custom_info`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM ast_device_software_version_record WHERE tenant_id=#{query.tenantId}  /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$TargetMapper.insertSoftwareBatch; pms-module-integration/src/main/resources/mapper/basesync/P35TargetMapper.xml:4; kind=insert
INSERT INTO ast_device_software_change_batch (`id`,`tenant_id`,`project_id`,`change_version`,`change_remark`,`latest`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.batch.id},#{row.batch.tenantId},#{row.batch.projectId},#{row.batch.changeVersion},#{row.batch.changeRemark},#{row.batch.latest},#{row.batch.creator},#{row.batch.createTime},#{row.batch.updater},#{row.batch.updateTime},#{row.batch.deleted}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$TargetMapper.insertSoftwareObservation; pms-module-integration/src/main/resources/mapper/basesync/P35TargetMapper.xml:6; kind=insert
INSERT INTO ast_device_software_version_record (`id`,`tenant_id`,`device_id`,`device_sn`,`project_id`,`previous_record_id`,`conp_version`,`conp_type`,`conp_series`,`conp_mark`,`boot_version`,`cpld_version`,`pcb_version`,`customized`,`release_date`,`version_desc`,`recorded_at`,`data_source`,`parsed_fact_id`,`request_key`,`change_batch_id`,`contract_no`,`product_code`,`execute_time`,`data_state`,`custom_info`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.observation.id},#{row.observation.tenantId},#{row.observation.deviceId},#{row.observation.deviceSn},#{row.observation.projectId},#{row.observation.previousRecordId},#{row.observation.conpVersion},#{row.observation.conpType},#{row.observation.conpSeries},#{row.observation.conpMark},#{row.observation.bootVersion},#{row.observation.cpldVersion},#{row.observation.pcbVersion},#{row.observation.customized},#{row.observation.releaseDate},#{row.observation.versionDesc},#{row.observation.recordedAt},#{row.observation.dataSource},#{row.observation.parsedFactId},#{row.observation.requestKey},#{row.observation.changeBatchId},#{row.observation.contractNo},#{row.observation.productCode},#{row.observation.executeTime},#{row.observation.dataState},#{row.observation.customInfo},#{row.observation.creator},#{row.observation.createTime},#{row.observation.updater},#{row.observation.updateTime},#{row.observation.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
