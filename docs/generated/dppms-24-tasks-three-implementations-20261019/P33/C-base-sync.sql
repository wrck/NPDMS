-- 最新原ID/用户名规则及P18/P22/源范围SQL变更见 ../base-sync-original-identifiers-preview-20261019.md 与 .sql；以下保留初次汇总SQL。
-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P33 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectInstallation] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P33SourceMapper.xml
SELECT `id` AS sourceId,`projectId` AS originalProjectId,`barcode` AS barcode,`itemCode` AS itemCode,`itemModel` AS itemModel,`itemName` AS itemName,`receiveName` AS receiveName,`emsNum` AS emsNum,`emsCompany` AS emsCompany,`packdate` AS packdate,`contractNo` AS contractNo,`installAddress` AS installAddress,`chProjectId` AS chProjectId,`chContractNo` AS chContractNo,`transferProjectId` AS transferProjectId,`transferContractNo` AS transferContractNo,`transferFlag` AS transferFlag,`createTime` AS createTime,`createBy` AS source_creator,`updateTime` AS updateTime,`updateBy` AS source_updater FROM pm_project_shipment  /* CHOOSE  */ /* WHEN test=projectKeys != null and !projectKeys.isEmpty() */ WHERE projectId IN  /* FOREACH collection=projectKeys; item=key; open=(; close=); separator=, */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ WHERE 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P33InstallationSyncTask$TargetMapper.selectDevices; pms-module-integration/src/main/resources/mapper/basesync/P33TargetMapper.xml:3; kind=select
SELECT id,sn FROM ast_device WHERE tenant_id=#{query.tenantId} AND deleted=FALSE  /* CHOOSE  */ /* WHEN test=query.serials != null and !query.serials.isEmpty() */ AND sn IN  /* FOREACH collection=query.serials; item=sn; open=(; close=); separator=, */ (#{sn}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P33InstallationSyncTask$TargetMapper.selectExisting; pms-module-integration/src/main/resources/mapper/basesync/P33TargetMapper.xml:4; kind=select
SELECT `id`,`project_id`,`code`,`equipment_id`,`install_location`,`address_id`,`address_version`,`site_id`,`site_version`,`site_location_id`,`site_location_version`,`location_resolution_status`,`address_snapshot`,`location_snapshot`,`effective_from`,`effective_to`,`current_equipment_id`,`install_time`,`installer_user_id`,`environment_check`,`spec_check`,`photo_url`,`result`,`status`,`remark`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id` FROM imp_eng_installation WHERE tenant_id=#{query.tenantId} AND (1=0  /* IF test=query.projectIds != null and !query.projectIds.isEmpty() */ OR project_id IN  /* FOREACH collection=query.projectIds; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END IF */  /* IF test=query.equipmentIds != null and !query.equipmentIds.isEmpty() */ OR equipment_id IN  /* FOREACH collection=query.equipmentIds; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END IF */ ) ORDER BY id LIMIT #{query.limit} /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P33InstallationSyncTask$TargetMapper.insertInstallation; pms-module-integration/src/main/resources/mapper/basesync/P33TargetMapper.xml:5; kind=insert
INSERT INTO imp_eng_installation (`id`,`project_id`,`code`,`equipment_id`,`install_location`,`address_id`,`address_version`,`site_id`,`site_version`,`site_location_id`,`site_location_version`,`location_resolution_status`,`address_snapshot`,`location_snapshot`,`effective_from`,`effective_to`,`install_time`,`installer_user_id`,`environment_check`,`spec_check`,`photo_url`,`result`,`status`,`remark`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.projectId},#{row.target.code},#{row.target.equipmentId},#{row.target.installLocation},#{row.target.addressId},#{row.target.addressVersion},#{row.target.siteId},#{row.target.siteVersion},#{row.target.siteLocationId},#{row.target.siteLocationVersion},#{row.target.locationResolutionStatus},#{row.target.addressSnapshot},#{row.target.locationSnapshot},#{row.target.effectiveFrom},#{row.target.effectiveTo},#{row.target.installTime},#{row.target.installerUserId},#{row.target.environmentCheck},#{row.target.specCheck},#{row.target.photoUrl},#{row.target.result},#{row.target.status},#{row.target.remark},#{row.target.version},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted},#{row.target.tenantId}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
