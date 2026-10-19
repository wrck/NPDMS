-- P30 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectDeviceBase] ACTUAL_LOCAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/mapper/basesync/P30TargetMapper.xml
SELECT MIN(device_sn) AS device_sn FROM ast_device_shipment WHERE tenant_id=#{tenantId} AND deleted=FALSE AND source_system='DPPMS' GROUP BY CAST(device_sn AS BINARY) ORDER BY CAST(device_sn AS BINARY) LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P30DeviceBaseSyncTask$TargetMapper.selectDeviceBase; pms-module-integration/src/main/resources/mapper/basesync/P30TargetMapper.xml:4; kind=select
SELECT MIN(device_sn) AS device_sn FROM ast_device_shipment WHERE tenant_id=#{tenantId} AND deleted=FALSE AND source_system='DPPMS' GROUP BY CAST(device_sn AS BINARY) ORDER BY CAST(device_sn AS BINARY) LIMIT #{limit};

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P30DeviceBaseSyncTask$TargetMapper.selectLatestShipments; pms-module-integration/src/main/resources/mapper/basesync/P30TargetMapper.xml:5; kind=select
SELECT id,tenant_id,device_sn,source_system,source_key,contract_no,package_no,shipment_time,product_code FROM (SELECT s.*,ROW_NUMBER() OVER(PARTITION BY CAST(device_sn AS BINARY) ORDER BY shipment_time DESC,id DESC) latest_rank FROM ast_device_shipment s WHERE tenant_id=#{query.tenantId} AND deleted=FALSE  /* CHOOSE  */ /* WHEN test=query.serials != null and !query.serials.isEmpty() */ AND device_sn IN  /* FOREACH collection=query.serials; item=sn; open=(; close=); separator=, */ (#{sn}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ ) ranked WHERE latest_rank=1;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P30DeviceBaseSyncTask$TargetMapper.selectProducts; pms-module-integration/src/main/resources/mapper/basesync/P30TargetMapper.xml:6; kind=select
SELECT product_code,product_model,product_name FROM ast_product_official_info WHERE tenant_id=#{query.tenantId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P30DeviceBaseSyncTask$TargetMapper.selectExisting; pms-module-integration/src/main/resources/mapper/basesync/P30TargetMapper.xml:7; kind=select
SELECT `id`,`tenant_id`,`version`,`shipment_record_id`,`project_assignment_version`,`customer_assignment_version`,`sn`,`name`,`product_name`,`product_model`,`product_code`,`package_no`,`contract_no`,`status`,`source_system`,`source_key`,`source_version`,`sync_status`,`remark`,`creator`,`updater`,`shipment_time`,`synced_at`,`create_time`,`update_time`,`deleted` FROM ast_device WHERE tenant_id=#{query.tenantId}  /* CHOOSE  */ /* WHEN test=query.serials != null and !query.serials.isEmpty() */ AND sn IN  /* FOREACH collection=query.serials; item=sn; open=(; close=); separator=, */ (#{sn}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P30DeviceBaseSyncTask$TargetMapper.insertDeviceBase; pms-module-integration/src/main/resources/mapper/basesync/P30TargetMapper.xml:8; kind=insert
INSERT INTO ast_device (`id`,`tenant_id`,`version`,`shipment_record_id`,`project_assignment_version`,`customer_assignment_version`,`sn`,`name`,`product_name`,`product_model`,`product_code`,`package_no`,`contract_no`,`status`,`source_system`,`source_key`,`source_version`,`sync_status`,`remark`,`creator`,`updater`,`shipment_time`,`synced_at`,`create_time`,`update_time`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.version},#{row.target.shipmentRecordId},#{row.target.projectAssignmentVersion},#{row.target.customerAssignmentVersion},#{row.target.sn},#{row.target.name},#{row.target.productName},#{row.target.productModel},#{row.target.productCode},#{row.target.packageNo},#{row.target.contractNo},#{row.target.status},#{row.target.sourceSystem},#{row.target.sourceKey},#{row.target.sourceVersion},#{row.target.syncStatus},#{row.target.remark},#{row.target.creator},#{row.target.updater},#{row.target.shipmentTime},#{row.target.syncedAt},#{row.target.createTime},#{row.target.updateTime},#{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
