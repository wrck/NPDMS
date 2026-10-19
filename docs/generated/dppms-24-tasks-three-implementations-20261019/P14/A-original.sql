-- P14 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [ORDER] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P14.json
SELECT h.*, SHA2(CAST(JSON_ARRAY(h.source,h.company_code,h.orderType,h.orderNumber) AS CHAR),256) AS migrationSourceKey,
 CONCAT('DPPMS|',h.source,'|',h.company_code,'|',h.orderType,'|',h.orderNumber) AS sourceRecordKey,
 DATE_FORMAT(h.migrationVersion,'%Y-%m-%dT%H:%i:%s.%f') AS sourceVersion,
 NOW(3) AS syncReceivedAt, CASE WHEN h.source NOT IN ('SAP','D365','SMS') OR h.source IS NULL THEN 'ERP_SOURCE_UNMAPPED'
 WHEN h.company_code NOT IN ('001','002','003','004') OR h.company_code IS NULL THEN 'DPPMS_COMPANY_CODE_UNMAPPED'
 WHEN h.orderType NOT IN ('0','1') OR h.orderType IS NULL THEN 'ORDER_TYPE_UNMAPPED'
 WHEN h.orderNumber IS NULL OR TRIM(h.orderNumber)='' THEN 'BUSINESS_KEY_MISSING'
 WHEN h.migrationVersion IS NULL THEN 'SOURCE_VERSION_MISSING'
 WHEN h.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT'
 WHEN h.customerCode IS NOT NULL AND h.customerCode<>'' AND (NOT(h.customerCode REGEXP '^[A-Za-z]') OR CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(h.customerCode,2)))>5) THEN 'ERP_CUSTOMER_CODE_FORMAT_INVALID' ELSE NULL END AS migrationIssue, CASE WHEN h.customerCode IS NULL OR h.customerCode='' THEN h.customerCode
 WHEN h.customerCode REGEXP '^[A-Za-z]' AND CHAR_LENGTH(TRIM(LEADING '0' FROM SUBSTRING(h.customerCode,2)))<=5
 THEN CONCAT(LEFT(h.customerCode,1),LPAD(TRIM(LEADING '0' FROM SUBSTRING(h.customerCode,2)),5,'0')) ELSE NULL END AS migrationCustomerCode
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
  COUNT(*) AS originalRowCount,
  CAST(JSON_ARRAYAGG(JSON_OBJECT('id',r.id,'orderNumber',r.orderNumber,'contractNo',r.contractNo,'orderExecNumber',r.orderExecNumber,'orderCreateTime',r.orderCreateTime,'customerRequireTime',r.customerRequireTime,'customerCode',r.customerCode,'customerName',r.customerName,'projectName',r.projectName,'orderComment',r.orderComment,'orderType',r.orderType,'compCode',r.compCode,'salesType',r.salesType,'source',r.source,'customInfo',r.customInfo,'syncTime',r.syncTime)) AS CHAR) AS originalRows
 FROM pm_order_data_from_erp r
 GROUP BY r.source,company_code,r.orderType,r.orderNumber
) h;

-- [LINE] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P14.json
SELECT h.*, SHA2(CAST(JSON_ARRAY(h.source,h.company_code,h.lineType,h.orderNumber,h.lineNum) AS CHAR),256) AS migrationSourceKey,
 CONCAT('DPPMS|',h.source,'|',h.company_code,'|',h.lineType,'|',h.orderNumber,'|',h.lineNum) AS sourceRecordKey,
 DATE_FORMAT(h.migrationVersion,'%Y-%m-%dT%H:%i:%s.%f') AS sourceVersion,
 NOW(3) AS syncReceivedAt, CASE WHEN h.source NOT IN ('SAP','D365','SMS') OR h.source IS NULL THEN 'ERP_SOURCE_UNMAPPED'
 WHEN h.company_code NOT IN ('001','002','003','004') OR h.company_code IS NULL THEN 'DPPMS_COMPANY_CODE_UNMAPPED'
 WHEN h.lineType NOT IN ('0','1') OR h.lineType IS NULL THEN 'ORDER_TYPE_UNMAPPED'
 WHEN h.orderNumber IS NULL OR TRIM(h.orderNumber)='' THEN 'BUSINESS_KEY_MISSING'
 WHEN h.migrationVersion IS NULL THEN 'SOURCE_VERSION_MISSING'
 WHEN h.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT'
 WHEN h.lineNum IS NULL OR TRIM(h.lineNum)='' THEN 'BUSINESS_KEY_MISSING'
 WHEN h.lineType='0' AND (h.orderQuantity<0 OR h.openQuantity<0 OR h.orderQuantity-h.openQuantity<0) THEN 'ACTIVE_QUANTITY_NEGATIVE'
 WHEN h.warrantyMonth<0 THEN 'WARRANTY_MONTH_NEGATIVE' ELSE NULL END AS migrationIssue, h.orderQuantity-h.openQuantity AS deliveredQuantity
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
  COUNT(*) AS originalRowCount,CAST(JSON_ARRAYAGG(JSON_OBJECT('id',r.id,'orderNumber',r.orderNumber,'lineNum',r.lineNum,'itemCode',r.itemCode,'itemDesc',r.itemDesc,'orderQuantity',r.orderQuantity,'openQuantity',r.openQuantity,'bundleCode',r.bundleCode,'warrantyMonth',r.warrantyMonth,'lineType',r.lineType,'compCode',r.compCode,'profitCenter',r.profitCenter,'realOrderExecNumber',r.realOrderExecNumber,'source',r.source,'customInfo',r.customInfo,'syncTime',r.syncTime)) AS CHAR) AS originalRows
 FROM pm_order_line_from_erp r
 GROUP BY r.source,company_code,r.lineType,r.orderNumber,r.lineNum
) h;

-- ===== 2. 目标/辅助查询与前置 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- A:GenericSyncJdbcStore.insertBatch:ORDER:com_sales_order; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_sales_order` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `authority_status`, `source_system`, `company_code`, `order_no`, `source_record_key`, `source_version`, `source_updated_at`, `source_sync_time`, `custom_info`, `order_type`, `sales_type`, `contract_no`, `execution_no`, `customer_code`, `customer_name`, `source_project_name`, `order_comment`, `order_create_time`, `customer_required_time`, `source_lifecycle_status`, `status`, `company_id`, `company_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :authority_status, :source_system, :company_code, :order_no, :source_record_key, :source_version, :source_updated_at, :source_sync_time, :custom_info, :order_type, :sales_type, :contract_no, :execution_no, :customer_code, :customer_name, :source_project_name, :order_comment, :order_create_time, :customer_required_time, :source_lifecycle_status, :status, :company_id, :company_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- A:GenericSyncJdbcStore.insertBatch:LINE:com_sales_order_line; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_sales_order_line` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `unit_scale`, `quantity_status`, `source_system`, `company_code`, `order_no`, `source_record_key`, `source_version`, `source_updated_at`, `source_sync_time`, `custom_info`, `order_type`, `line_type`, `line_no`, `product_code`, `product_desc`, `bundle_code`, `profit_center`, `real_execution_no`, `warranty_month`, `order_qty`, `open_qty`, `delivered_qty`, `source_lifecycle_status`, `status`, `order_id`, `company_id`, `company_name`, `customer_id`, `customer_code`, `customer_name`, `product_id`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :unit_scale, :quantity_status, :source_system, :company_code, :order_no, :source_record_key, :source_version, :source_updated_at, :source_sync_time, :custom_info, :order_type, :line_type, :line_no, :product_code, :product_desc, :bundle_code, :profit_center, :real_execution_no, :warranty_month, :order_qty, :open_qty, :delivered_qty, :source_lifecycle_status, :status, :order_id, :company_id, :company_name, :customer_id, :customer_code, :customer_name, :product_id);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- A:GenericSyncJdbcStore.updateBatch:ORDER:com_sales_order; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_sales_order` SET `source_version` = CASE `id` WHEN :id THEN :source_version ELSE `source_version` END, `source_updated_at` = CASE `id` WHEN :id THEN :source_updated_at ELSE `source_updated_at` END, `source_sync_time` = CASE `id` WHEN :id THEN :source_sync_time ELSE `source_sync_time` END, `custom_info` = CASE `id` WHEN :id THEN :custom_info ELSE `custom_info` END, `sales_type` = CASE `id` WHEN :id THEN :sales_type ELSE `sales_type` END, `contract_no` = CASE `id` WHEN :id THEN :contract_no ELSE `contract_no` END, `execution_no` = CASE `id` WHEN :id THEN :execution_no ELSE `execution_no` END, `customer_code` = CASE `id` WHEN :id THEN :customer_code ELSE `customer_code` END, `customer_name` = CASE `id` WHEN :id THEN :customer_name ELSE `customer_name` END, `source_project_name` = CASE `id` WHEN :id THEN :source_project_name ELSE `source_project_name` END, `order_comment` = CASE `id` WHEN :id THEN :order_comment ELSE `order_comment` END, `order_create_time` = CASE `id` WHEN :id THEN :order_create_time ELSE `order_create_time` END, `customer_required_time` = CASE `id` WHEN :id THEN :customer_required_time ELSE `customer_required_time` END, `source_lifecycle_status` = CASE `id` WHEN :id THEN :source_lifecycle_status ELSE `source_lifecycle_status` END, `status` = CASE `id` WHEN :id THEN :status ELSE `status` END, `company_id` = CASE `id` WHEN :id THEN :company_id ELSE `company_id` END, `company_name` = CASE `id` WHEN :id THEN :company_name ELSE `company_name` END, version = CASE id WHEN :id THEN :before_version_plus_bumps ELSE version END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- A:GenericSyncJdbcStore.updateBatch:LINE:com_sales_order_line; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_sales_order_line` SET `source_version` = CASE `id` WHEN :id THEN :source_version ELSE `source_version` END, `source_updated_at` = CASE `id` WHEN :id THEN :source_updated_at ELSE `source_updated_at` END, `source_sync_time` = CASE `id` WHEN :id THEN :source_sync_time ELSE `source_sync_time` END, `custom_info` = CASE `id` WHEN :id THEN :custom_info ELSE `custom_info` END, `line_type` = CASE `id` WHEN :id THEN :line_type ELSE `line_type` END, `product_code` = CASE `id` WHEN :id THEN :product_code ELSE `product_code` END, `product_desc` = CASE `id` WHEN :id THEN :product_desc ELSE `product_desc` END, `bundle_code` = CASE `id` WHEN :id THEN :bundle_code ELSE `bundle_code` END, `profit_center` = CASE `id` WHEN :id THEN :profit_center ELSE `profit_center` END, `real_execution_no` = CASE `id` WHEN :id THEN :real_execution_no ELSE `real_execution_no` END, `warranty_month` = CASE `id` WHEN :id THEN :warranty_month ELSE `warranty_month` END, `order_qty` = CASE `id` WHEN :id THEN :order_qty ELSE `order_qty` END, `open_qty` = CASE `id` WHEN :id THEN :open_qty ELSE `open_qty` END, `delivered_qty` = CASE `id` WHEN :id THEN :delivered_qty ELSE `delivered_qty` END, `source_lifecycle_status` = CASE `id` WHEN :id THEN :source_lifecycle_status ELSE `source_lifecycle_status` END, `status` = CASE `id` WHEN :id THEN :status ELSE `status` END, `company_id` = CASE `id` WHEN :id THEN :company_id ELSE `company_id` END, `company_name` = CASE `id` WHEN :id THEN :company_name ELSE `company_name` END, `customer_id` = CASE `id` WHEN :id THEN :customer_id ELSE `customer_id` END, `customer_code` = CASE `id` WHEN :id THEN :customer_code ELSE `customer_code` END, `customer_name` = CASE `id` WHEN :id THEN :customer_name ELSE `customer_name` END, `order_id` = CASE `id` WHEN :id THEN :order_id ELSE `order_id` END, `product_id` = CASE `id` WHEN :id THEN :product_id ELSE `product_id` END, version = CASE id WHEN :id THEN :before_version_plus_bumps ELSE version END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
