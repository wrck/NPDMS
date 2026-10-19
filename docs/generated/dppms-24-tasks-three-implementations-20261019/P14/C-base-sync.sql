-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P14 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectOrder] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P14SourceMapper.xml
SELECT SHA2(CAST(JSON_ARRAY(h.source,h.company_code,h.orderType,h.orderNumber) AS CHAR),256) AS source_key,CASE WHEN h.source NOT IN ('SAP','D365','SMS') OR h.source IS NULL THEN 'ERP_SOURCE_UNMAPPED'
 WHEN h.company_code NOT IN ('001','002','003','004') OR h.company_code IS NULL THEN 'DPPMS_COMPANY_CODE_UNMAPPED'
 WHEN h.orderType NOT IN ('0','1') OR h.orderType IS NULL THEN 'ORDER_TYPE_UNMAPPED'
 WHEN h.orderNumber IS NULL OR TRIM(h.orderNumber)='' THEN 'BUSINESS_KEY_MISSING'
 WHEN h.migrationVersion IS NULL THEN 'SOURCE_VERSION_MISSING'
 WHEN h.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT'
 WHEN h.customerCode IS NOT NULL AND h.customerCode<>'' AND (NOT(h.customerCode REGEXP '^[A-Za-z]') OR CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(h.customerCode,2)))>5) THEN 'ERP_CUSTOMER_CODE_FORMAT_INVALID' ELSE NULL END AS issue,
CASE h.`source` WHEN 'SAP' THEN 'SAP' WHEN 'D365' THEN 'D365' WHEN 'SMS' THEN 'SMS' END AS `source_system`,
h.`company_code` AS `company_code`,
h.`orderNumber` AS `order_no`,
CONCAT('DPPMS|',h.source,'|',h.company_code,'|',h.orderType,'|',h.orderNumber) AS `source_record_key`,
DATE_FORMAT(h.migrationVersion,'%Y-%m-%dT%H:%i:%s.%f') AS `source_version`,
h.`migrationVersion` AS `source_updated_at`,
NOW(3) AS `source_sync_time`,
h.`customInfo` AS `custom_info`,
h.`orderType` AS `order_type`,
h.`salesType` AS `sales_type`,
h.`contractNo` AS `contract_no`,
h.`orderExecNumber` AS `execution_no`,
CASE WHEN h.customerCode IS NULL OR h.customerCode='' THEN h.customerCode
 WHEN h.customerCode REGEXP '^[A-Za-z]' AND CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(h.customerCode,2)))<=5
 THEN CONCAT(LEFT(h.customerCode,1),LPAD(TRIM(LEADING '0' FROM SUBSTRING(h.customerCode,2)),5,'0')) ELSE NULL END AS `customer_code`,
h.`customerName` AS `customer_name`,
h.`projectName` AS `source_project_name`,
h.`orderComment` AS `order_comment`,
h.`orderCreateTime` AS `order_create_time`,
h.`customerRequireTime` AS `customer_required_time`,
CASE h.`orderType` WHEN '0' THEN 'ACTIVE' WHEN '1' THEN 'RETURNED' END AS `source_lifecycle_status`,
CASE h.`orderType` WHEN '0' THEN 'ENABLED' WHEN '1' THEN 'DISABLED' END AS `status`
FROM (
SELECT r.source,CASE r.compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE r.compCode END AS company_code,r.orderType,r.orderNumber,
  MAX(r.orderCreateTime) AS orderCreateTime,
  MAX(r.customerRequireTime) AS customerRequireTime,
  MAX(r.customerCode) AS customerCode,
  MAX(r.customerName) AS customerName,
  MAX(r.projectName) AS projectName,
  MAX(r.orderComment) AS orderComment,
  MAX(r.salesType) AS salesType,
  MAX(CAST(r.customInfo AS CHAR)) AS customInfo,
  MAX(r.contractNo) AS contractNo,
  MAX(r.orderExecNumber) AS orderExecNumber,
  MAX(r.syncTime) AS migrationVersion,
  COUNT(DISTINCT CAST(JSON_ARRAY(r.orderCreateTime,r.customerRequireTime,r.customerCode,r.customerName,r.projectName,r.orderComment,r.salesType,r.customInfo,r.contractNo,r.orderExecNumber) AS BINARY)) AS payloadVariants,
  COUNT(*) AS originalRowCount
 FROM pm_order_data_from_erp r
 GROUP BY r.source,company_code,r.orderType,r.orderNumber,CAST(r.source AS BINARY),CAST(r.orderType AS BINARY),CAST(r.orderNumber AS BINARY)
) h

LIMIT #{limit};

-- [selectLine] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P14SourceMapper.xml
SELECT SHA2(CAST(JSON_ARRAY(h.source,h.company_code,h.lineType,h.orderNumber,h.lineNum) AS CHAR),256) AS source_key,CASE WHEN h.source NOT IN ('SAP','D365','SMS') OR h.source IS NULL THEN 'ERP_SOURCE_UNMAPPED'
 WHEN h.company_code NOT IN ('001','002','003','004') OR h.company_code IS NULL THEN 'DPPMS_COMPANY_CODE_UNMAPPED'
 WHEN h.lineType NOT IN ('0','1') OR h.lineType IS NULL THEN 'ORDER_TYPE_UNMAPPED'
 WHEN h.orderNumber IS NULL OR TRIM(h.orderNumber)='' THEN 'BUSINESS_KEY_MISSING'
 WHEN h.migrationVersion IS NULL THEN 'SOURCE_VERSION_MISSING'
 WHEN h.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT'
 WHEN h.lineNum IS NULL OR TRIM(h.lineNum)='' THEN 'BUSINESS_KEY_MISSING'
 WHEN h.lineType='0' AND (h.orderQuantity<0 OR h.openQuantity<0 OR h.orderQuantity-h.openQuantity<0) THEN 'ACTIVE_QUANTITY_NEGATIVE'
 WHEN h.warrantyMonth<0 THEN 'WARRANTY_MONTH_NEGATIVE' ELSE NULL END AS issue,
CASE h.`source` WHEN 'SAP' THEN 'SAP' WHEN 'D365' THEN 'D365' WHEN 'SMS' THEN 'SMS' END AS `source_system`,
h.`company_code` AS `company_code`,
h.`orderNumber` AS `order_no`,
CONCAT('DPPMS|',h.source,'|',h.company_code,'|',h.lineType,'|',h.orderNumber,'|',h.lineNum) AS `source_record_key`,
DATE_FORMAT(h.migrationVersion,'%Y-%m-%dT%H:%i:%s.%f') AS `source_version`,
h.`migrationVersion` AS `source_updated_at`,
NOW(3) AS `source_sync_time`,
h.`customInfo` AS `custom_info`,
h.`lineType` AS `order_type`,
h.`lineType` AS `line_type`,
h.`lineNum` AS `line_no`,
h.`itemCode` AS `product_code`,
h.`itemDesc` AS `product_desc`,
h.`bundleCode` AS `bundle_code`,
h.`profitCenter` AS `profit_center`,
h.`realOrderExecNumber` AS `real_execution_no`,
h.`warrantyMonth` AS `warranty_month`,
h.`orderQuantity` AS `order_qty`,
h.`openQuantity` AS `open_qty`,
(h.orderQuantity-h.openQuantity) AS `delivered_qty`,
CASE h.`lineType` WHEN '0' THEN 'ACTIVE' WHEN '1' THEN 'RETURNED' END AS `source_lifecycle_status`,
CASE h.`lineType` WHEN '0' THEN 'ENABLED' WHEN '1' THEN 'DISABLED' END AS `status`
FROM (
SELECT r.source,CASE r.compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE r.compCode END AS company_code,r.lineType,r.orderNumber,r.lineNum,
  MAX(r.itemCode) AS itemCode,
  MAX(r.itemDesc) AS itemDesc,
  MAX(r.orderQuantity) AS orderQuantity,
  MAX(r.openQuantity) AS openQuantity,
  MAX(r.bundleCode) AS bundleCode,
  MAX(r.warrantyMonth) AS warrantyMonth,
  MAX(r.profitCenter) AS profitCenter,
  MAX(r.realOrderExecNumber) AS realOrderExecNumber,
  MAX(CAST(r.customInfo AS CHAR)) AS customInfo,
  MAX(r.syncTime) AS migrationVersion,
  COUNT(DISTINCT CAST(JSON_ARRAY(r.itemCode,r.itemDesc,r.orderQuantity,r.openQuantity,r.bundleCode,r.warrantyMonth,r.profitCenter,r.realOrderExecNumber,r.customInfo) AS BINARY)) AS payloadVariants,
  COUNT(*) AS originalRowCount
 FROM pm_order_line_from_erp r
 GROUP BY r.source,company_code,r.lineType,r.orderNumber,r.lineNum,CAST(r.source AS BINARY),CAST(r.lineType AS BINARY),CAST(r.orderNumber AS BINARY),CAST(r.lineNum AS BINARY)
) h

LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P14SalesOrderSyncTask$TargetMapper.selectOrders; pms-module-integration/src/main/resources/mapper/basesync/P14TargetMapper.xml:4; kind=select
SELECT `id`,`tenant_id`,`source_system`,`source_record_key`,`source_version`,`company_id`,`company_code`,`company_name`,`order_type`,`order_no`,`sales_type`,`contract_no`,`execution_no`,`customer_id`,`customer_code`,`customer_name`,`source_project_name`,`order_comment`,`order_amount`,`currency_code`,`authority_status`,`source_lifecycle_status`,`order_create_time`,`customer_required_time`,`custom_info`,`source_sync_time`,`source_updated_at`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM com_sales_order WHERE tenant_id=#{query.tenantId}
 /* CHOOSE  */ /* WHEN test=query.keys != null and !query.keys.isEmpty() */ AND ( /* FOREACH collection=query.keys; item=key; separator= OR  */ (source_system=#{key.sourceSystem} AND company_code=#{key.companyCode} AND order_type=#{key.orderType} AND order_no=#{key.orderNo}) /* repeat with separator= OR ; END FOREACH */ ) /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P14SalesOrderSyncTask$TargetMapper.selectLines; pms-module-integration/src/main/resources/mapper/basesync/P14TargetMapper.xml:8; kind=select
SELECT `id`,`tenant_id`,`order_id`,`source_system`,`source_record_key`,`source_version`,`company_id`,`company_code`,`company_name`,`order_type`,`order_no`,`line_no`,`line_type`,`product_code`,`product_model`,`product_desc`,`custom_info`,`product_id`,`bundle_code`,`profit_center`,`real_execution_no`,`warranty_month`,`customer_id`,`customer_code`,`customer_name`,`order_qty`,`open_qty`,`delivered_qty`,`unit_code`,`unit_scale`,`quantity_status`,`source_lifecycle_status`,`source_sync_time`,`source_updated_at`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM com_sales_order_line WHERE tenant_id=#{query.tenantId}
 /* CHOOSE  */ /* WHEN test=query.keys != null and !query.keys.isEmpty() */ AND ( /* FOREACH collection=query.keys; item=key; separator= OR  */ (source_system=#{key.sourceSystem} AND company_code=#{key.companyCode} AND order_type=#{key.orderType} AND order_no=#{key.orderNo} AND line_no=#{key.lineNo}) /* repeat with separator= OR ; END FOREACH */ ) /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P14SalesOrderSyncTask$TargetMapper.insertOrder; pms-module-integration/src/main/resources/mapper/basesync/P14TargetMapper.xml:6; kind=insert
INSERT INTO com_sales_order (`id`,`tenant_id`,`source_system`,`source_record_key`,`source_version`,`company_id`,`company_code`,`company_name`,`order_type`,`order_no`,`sales_type`,`contract_no`,`execution_no`,`customer_id`,`customer_code`,`customer_name`,`source_project_name`,`order_comment`,`order_amount`,`currency_code`,`authority_status`,`source_lifecycle_status`,`order_create_time`,`customer_required_time`,`custom_info`,`source_sync_time`,`source_updated_at`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.order.id},#{row.order.tenantId},#{row.order.sourceSystem},#{row.order.sourceRecordKey},#{row.order.sourceVersion},#{row.order.companyId},#{row.order.companyCode},#{row.order.companyName},#{row.order.orderType},#{row.order.orderNo},#{row.order.salesType},#{row.order.contractNo},#{row.order.executionNo},#{row.order.customerId},#{row.order.customerCode},#{row.order.customerName},#{row.order.sourceProjectName},#{row.order.orderComment},#{row.order.orderAmount},#{row.order.currencyCode},#{row.order.authorityStatus},#{row.order.sourceLifecycleStatus},#{row.order.orderCreateTime},#{row.order.customerRequiredTime},#{row.order.customInfo},#{row.order.sourceSyncTime},#{row.order.sourceUpdatedAt},#{row.order.status},#{row.order.version},#{row.order.creator},#{row.order.createTime},#{row.order.updater},#{row.order.updateTime},#{row.order.deleted}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P14SalesOrderSyncTask$TargetMapper.insertLine; pms-module-integration/src/main/resources/mapper/basesync/P14TargetMapper.xml:10; kind=insert
INSERT INTO com_sales_order_line (`id`,`tenant_id`,`order_id`,`source_system`,`source_record_key`,`source_version`,`company_id`,`company_code`,`company_name`,`order_type`,`order_no`,`line_no`,`line_type`,`product_code`,`product_model`,`product_desc`,`custom_info`,`product_id`,`bundle_code`,`profit_center`,`real_execution_no`,`warranty_month`,`customer_id`,`customer_code`,`customer_name`,`order_qty`,`open_qty`,`delivered_qty`,`unit_code`,`unit_scale`,`quantity_status`,`source_lifecycle_status`,`source_sync_time`,`source_updated_at`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.line.id},#{row.line.tenantId},#{row.line.orderId},#{row.line.sourceSystem},#{row.line.sourceRecordKey},#{row.line.sourceVersion},#{row.line.companyId},#{row.line.companyCode},#{row.line.companyName},#{row.line.orderType},#{row.line.orderNo},#{row.line.lineNo},#{row.line.lineType},#{row.line.productCode},#{row.line.productModel},#{row.line.productDesc},#{row.line.customInfo},#{row.line.productId},#{row.line.bundleCode},#{row.line.profitCenter},#{row.line.realExecutionNo},#{row.line.warrantyMonth},#{row.line.customerId},#{row.line.customerCode},#{row.line.customerName},#{row.line.orderQty},#{row.line.openQty},#{row.line.deliveredQty},#{row.line.unitCode},#{row.line.unitScale},#{row.line.quantityStatus},#{row.line.sourceLifecycleStatus},#{row.line.sourceSyncTime},#{row.line.sourceUpdatedAt},#{row.line.status},#{row.line.version},#{row.line.creator},#{row.line.createTime},#{row.line.updater},#{row.line.updateTime},#{row.line.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P14SalesOrderSyncTask$TargetMapper.updateOrder; pms-module-integration/src/main/resources/mapper/basesync/P14TargetMapper.xml:7; kind=update
UPDATE com_sales_order SET `source_version`=#{row.order.sourceVersion},`sales_type`=#{row.order.salesType},`contract_no`=#{row.order.contractNo},`execution_no`=#{row.order.executionNo},`customer_code`=#{row.order.customerCode},`customer_name`=#{row.order.customerName},`source_project_name`=#{row.order.sourceProjectName},`order_comment`=#{row.order.orderComment},`source_lifecycle_status`=#{row.order.sourceLifecycleStatus},`order_create_time`=#{row.order.orderCreateTime},`customer_required_time`=#{row.order.customerRequiredTime},`custom_info`=#{row.order.customInfo},`source_sync_time`=#{row.order.sourceSyncTime},`source_updated_at`=#{row.order.sourceUpdatedAt},`status`=#{row.order.status},`version`=#{row.order.version},`updater`=#{row.order.updater},`update_time`=#{row.order.updateTime} WHERE tenant_id=#{context.tenantId} AND id=#{row.order.id} AND version=#{row.expectedVersion} AND deleted=0;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P14SalesOrderSyncTask$TargetMapper.updateLine; pms-module-integration/src/main/resources/mapper/basesync/P14TargetMapper.xml:11; kind=update
UPDATE com_sales_order_line SET `source_version`=#{row.line.sourceVersion},`line_type`=#{row.line.lineType},`product_code`=#{row.line.productCode},`product_desc`=#{row.line.productDesc},`custom_info`=#{row.line.customInfo},`bundle_code`=#{row.line.bundleCode},`profit_center`=#{row.line.profitCenter},`real_execution_no`=#{row.line.realExecutionNo},`warranty_month`=#{row.line.warrantyMonth},`customer_code`=#{row.line.customerCode},`customer_name`=#{row.line.customerName},`order_qty`=#{row.line.orderQty},`open_qty`=#{row.line.openQty},`delivered_qty`=#{row.line.deliveredQty},`source_lifecycle_status`=#{row.line.sourceLifecycleStatus},`source_sync_time`=#{row.line.sourceSyncTime},`source_updated_at`=#{row.line.sourceUpdatedAt},`status`=#{row.line.status},`version`=#{row.line.version},`updater`=#{row.line.updater},`update_time`=#{row.line.updateTime} WHERE tenant_id=#{context.tenantId} AND id=#{row.line.id} AND version=#{row.expectedVersion} AND deleted=0;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
