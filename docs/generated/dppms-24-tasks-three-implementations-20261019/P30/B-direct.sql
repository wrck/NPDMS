-- P30 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [DEVICE_BASE] OWNER_OR_CONTROLLED_INPUT; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
-- readMode=LOCAL_OWNER; object=DEVICE_BASE; 本地/受控Owner输入，实际Mapper查询及Java调用见本方式的查询和处理逻辑。;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDeviceBaseMapper.selectLatestShipments; pms-module-integration/src/main/resources/mapper/sync/DirectDeviceBaseMapper.xml:4; kind=select
SELECT ranked.* FROM (
      SELECT s.*,ROW_NUMBER() OVER(PARTITION BY CAST(device_sn AS BINARY) ORDER BY shipment_time DESC,id DESC) AS latest_rank
      FROM ast_device_shipment s WHERE tenant_id=#{query.tenantId} AND deleted=0
       /* CHOOSE  */ /* WHEN test=query.deviceSns != null and !query.deviceSns.isEmpty() */ AND device_sn IN  /* FOREACH collection=query.deviceSns; item=sn; open=(; separator=,; close=) */ (#{sn}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
         /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ) ranked WHERE latest_rank=1;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDeviceBaseMapper.selectIdentityForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectDeviceBaseMapper.xml:16; kind=select
SELECT * FROM ast_device WHERE tenant_id=#{query.tenantId} AND sn=#{query.deviceSn} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDeviceBaseMapper.selectShipmentProducts; pms-module-integration/src/main/resources/mapper/sync/DirectDeviceBaseMapper.xml:19; kind=select
SELECT id,tenant_id,product_code,product_name,product_model FROM ast_product_official_info WHERE tenant_id=#{query.tenantId} AND deleted=0
         /* CHOOSE  */ /* WHEN test=query.productCodes != null and !query.productCodes.isEmpty() */ AND product_code IN
         /* FOREACH collection=query.productCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectDeviceBaseMapper.insert; pms-module-integration/src/main/resources/mapper/sync/DirectDeviceBaseMapper.xml:24; kind=insert
INSERT INTO ast_device (id,tenant_id,version,sn,name,product_name,product_model,product_code,package_no,contract_no,status,source_system,source_key,source_version,sync_status,remark,shipment_time,synced_at,shipment_record_id,project_assignment_version,customer_assignment_version,creator,create_time,updater,update_time,deleted) VALUES (#{row.id},#{row.tenantId},#{row.version},#{row.sn},#{row.name},#{row.productName},#{row.productModel},#{row.productCode},#{row.packageNo},#{row.contractNo},#{row.status},#{row.sourceSystem},#{row.sourceKey},#{row.sourceVersion},#{row.syncStatus},#{row.remark},#{row.shipmentTime},#{row.syncedAt},#{row.shipmentRecordId},#{row.projectAssignmentVersion},#{row.customerAssignmentVersion},#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},#{row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
