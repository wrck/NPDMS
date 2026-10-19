-- P14 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [ORDER] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P14-creation.json
SELECT SHA2(CAST(JSON_ARRAY(h.source,h.company_code,h.orderType,h.orderNumber) AS CHAR),256) AS _groupKey,
 CONCAT('[',CAST(JSON_OBJECT('object','ORDER','fields',JSON_OBJECT('migrationSourceKey',SHA2(CAST(JSON_ARRAY(h.source,h.company_code,h.orderType,h.orderNumber) AS CHAR),256),'sourceRecordKey',CONCAT('DPPMS|',h.source,'|',h.company_code,'|',h.orderType,'|',h.orderNumber),'source',h.source,'company_code',h.company_code,'orderNumber',h.orderNumber,'orderType',h.orderType,'orderCreateTime',h.orderCreateTime,'customerRequireTime',h.customerRequireTime,'customerCode',h.customerCode,'customerName',h.customerName,'projectName',h.projectName,'orderComment',h.orderComment,'salesType',h.salesType,'customInfo',h.customInfo,'contractNo',h.contractNo,'orderExecNumber',h.orderExecNumber,'migrationVersion',h.migrationVersion,'sourceVersion',DATE_FORMAT(h.migrationVersion,'%Y-%m-%dT%H:%i:%s.%f'),'syncReceivedAt',NOW(3),'migrationIssue',CASE WHEN h.source NOT IN ('SAP','D365','SMS') OR h.source IS NULL THEN 'ERP_SOURCE_UNMAPPED' WHEN h.company_code NOT IN ('001','002','003','004') OR h.company_code IS NULL THEN 'DPPMS_COMPANY_CODE_UNMAPPED' WHEN h.orderType NOT IN ('0','1') OR h.orderType IS NULL THEN 'ORDER_TYPE_UNMAPPED' WHEN h.orderNumber IS NULL OR TRIM(h.orderNumber)='' THEN 'BUSINESS_KEY_MISSING' WHEN h.migrationVersion IS NULL THEN 'SOURCE_VERSION_MISSING' WHEN h.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT' ELSE NULL END,'originalRowCount',h.originalRowCount,'originalRows',h.originalRows)) AS CHAR),
   COALESCE(CONCAT(',',SUBSTRING((SELECT CAST(JSON_ARRAYAGG(JSON_OBJECT('object','LINE','fields',JSON_OBJECT('migrationSourceKey',SHA2(CAST(JSON_ARRAY(l.source,l.company_code,l.lineType,l.orderNumber,l.lineNum) AS CHAR),256),'sourceRecordKey',CONCAT('DPPMS|',l.source,'|',l.company_code,'|',l.lineType,'|',l.orderNumber,'|',l.lineNum),'source',l.source,'company_code',l.company_code,'orderNumber',l.orderNumber,'lineType',l.lineType,'itemCode',l.itemCode,'itemDesc',l.itemDesc,'orderQuantity',l.orderQuantity,'openQuantity',l.openQuantity,'bundleCode',l.bundleCode,'warrantyMonth',l.warrantyMonth,'profitCenter',l.profitCenter,'realOrderExecNumber',l.realOrderExecNumber,'customInfo',l.customInfo,'lineNum',l.lineNum,'deliveredQuantity',l.orderQuantity-l.openQuantity,'migrationVersion',l.migrationVersion,'sourceVersion',DATE_FORMAT(l.migrationVersion,'%Y-%m-%dT%H:%i:%s.%f'),'syncReceivedAt',NOW(3),'migrationIssue',CASE WHEN h.payloadVariants>1 THEN 'PARENT_SOURCE_BUSINESS_KEY_CONFLICT' WHEN (CASE WHEN h.source NOT IN ('SAP','D365','SMS') OR h.source IS NULL THEN 'ERP_SOURCE_UNMAPPED' WHEN h.company_code NOT IN ('001','002','003','004') OR h.company_code IS NULL THEN 'DPPMS_COMPANY_CODE_UNMAPPED' WHEN h.orderType NOT IN ('0','1') OR h.orderType IS NULL THEN 'ORDER_TYPE_UNMAPPED' WHEN h.orderNumber IS NULL OR TRIM(h.orderNumber)='' THEN 'BUSINESS_KEY_MISSING' WHEN h.migrationVersion IS NULL THEN 'SOURCE_VERSION_MISSING' WHEN h.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT' ELSE NULL END) IS NOT NULL THEN 'PARENT_SOURCE_INVALID' WHEN l.lineType='0' AND (l.orderQuantity<0 OR l.openQuantity<0 OR l.orderQuantity-l.openQuantity<0) THEN 'ACTIVE_QUANTITY_NEGATIVE' WHEN l.warrantyMonth<0 THEN 'WARRANTY_MONTH_NEGATIVE' ELSE (CASE WHEN l.source NOT IN ('SAP','D365','SMS') OR l.source IS NULL THEN 'ERP_SOURCE_UNMAPPED' WHEN l.company_code NOT IN ('001','002','003','004') OR l.company_code IS NULL THEN 'DPPMS_COMPANY_CODE_UNMAPPED' WHEN l.lineType NOT IN ('0','1') OR l.lineType IS NULL THEN 'ORDER_TYPE_UNMAPPED' WHEN l.orderNumber IS NULL OR TRIM(l.orderNumber)='' OR l.lineNum IS NULL OR TRIM(l.lineNum)='' THEN 'BUSINESS_KEY_MISSING' WHEN l.migrationVersion IS NULL THEN 'SOURCE_VERSION_MISSING' WHEN l.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT' ELSE NULL END) END,'originalRowCount',l.originalRowCount,'originalRows',l.originalRows))) AS CHAR)
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
    ) l WHERE l.source <=> h.source AND l.company_code <=> h.company_code
      AND l.lineType <=> h.orderType AND l.orderNumber <=> h.orderNumber),2)),']')) AS _sourceRows
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
  SUM(CASE WHEN scope.contractNo IS NOT NULL THEN 1 ELSE 0 END) AS originalRowCount,
  CAST(JSON_ARRAYAGG(CASE WHEN scope.contractNo IS NOT NULL THEN JSON_OBJECT('id',r.id,'orderNumber',r.orderNumber,'contractNo',r.contractNo,'orderExecNumber',r.orderExecNumber,'orderCreateTime',r.orderCreateTime,'customerRequireTime',r.customerRequireTime,'customerCode',r.customerCode,'customerName',r.customerName,'projectName',r.projectName,'orderComment',r.orderComment,'orderType',r.orderType,'compCode',r.compCode,'salesType',r.salesType,'source',r.source,'customInfo',r.customInfo,'syncTime',r.syncTime) ELSE JSON_EXTRACT('null','$') END) AS CHAR) AS originalRows
 FROM pm_order_data_from_erp r
 LEFT JOIN (SELECT DISTINCT pc.contractNo
FROM pm_project_group_relationship m
JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
  AND CAST(g.projectGroupCode AS BINARY)=CAST(m.projectGroupCode AS BINARY)
JOIN (SELECT DISTINCT scoped.projectId,scoped.projectCode,scoped.projectType
FROM (SELECT original.*,canonical.projectId AS canonicalProjectId
FROM pm_project original
JOIN (
  SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,
    MAX(projectId) AS projectId
  FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)
) canonical ON canonical.typeKey <=> CAST(original.projectType AS BINARY)
  AND canonical.codeKey <=> CAST(original.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE current_project.disabled=0 AND current_project.effectiveTo IS NULL
  AND original.disabled=0 AND original.effectiveTo IS NULL
  AND FIND_IN_SET(current_project.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) scoped
WHERE scoped.projectId=scoped.canonicalProjectId) p
  ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
  AND CAST(p.projectCode AS BINARY)=CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
  AND CAST(p.projectType AS BINARY)=CAST(g.projectType AS BINARY)
JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
  AND CAST(pc.projectGroupCode AS BINARY)=CAST(g.projectGroupCode AS BINARY)) scope ON scope.contractNo=r.contractNo
 GROUP BY r.source,company_code,r.orderType,r.orderNumber
) h
WHERE h.originalRowCount>0
ORDER BY h.source,h.company_code,h.orderType,h.orderNumber;

-- [LINE] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P14-creation.json
-- queryRef=ORDER; 复用该来源结果/分组，不重复执行一份SELECT。;

-- ===== 2. 目标/辅助查询与前置 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- B:GenericSyncJdbcStore.insertBatch:ORDER:com_sales_order; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_sales_order` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `authority_status`, `source_system`, `company_code`, `order_no`, `source_record_key`, `source_version`, `source_updated_at`, `source_sync_time`, `custom_info`, `order_type`, `sales_type`, `contract_no`, `execution_no`, `customer_code`, `customer_name`, `source_project_name`, `order_comment`, `order_create_time`, `customer_required_time`, `source_lifecycle_status`, `status`, `company_id`, `company_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :authority_status, :source_system, :company_code, :order_no, :source_record_key, :source_version, :source_updated_at, :source_sync_time, :custom_info, :order_type, :sales_type, :contract_no, :execution_no, :customer_code, :customer_name, :source_project_name, :order_comment, :order_create_time, :customer_required_time, :source_lifecycle_status, :status, :company_id, :company_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- B:GenericSyncJdbcStore.insertBatch:LINE:com_sales_order_line; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_sales_order_line` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `unit_scale`, `quantity_status`, `source_system`, `company_code`, `order_no`, `source_record_key`, `source_version`, `source_updated_at`, `source_sync_time`, `custom_info`, `order_type`, `line_type`, `line_no`, `product_code`, `product_desc`, `bundle_code`, `profit_center`, `real_execution_no`, `warranty_month`, `order_qty`, `open_qty`, `delivered_qty`, `source_lifecycle_status`, `status`, `order_id`, `company_id`, `customer_id`, `company_name`, `customer_code`, `customer_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :unit_scale, :quantity_status, :source_system, :company_code, :order_no, :source_record_key, :source_version, :source_updated_at, :source_sync_time, :custom_info, :order_type, :line_type, :line_no, :product_code, :product_desc, :bundle_code, :profit_center, :real_execution_no, :warranty_month, :order_qty, :open_qty, :delivered_qty, :source_lifecycle_status, :status, :order_id, :company_id, :customer_id, :company_name, :customer_code, :customer_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- B:GenericSyncJdbcStore.updateBatch:ORDER:com_sales_order; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_sales_order` SET `source_version` = CASE `id` WHEN :id THEN :source_version ELSE `source_version` END, `source_updated_at` = CASE `id` WHEN :id THEN :source_updated_at ELSE `source_updated_at` END, `source_sync_time` = CASE `id` WHEN :id THEN :source_sync_time ELSE `source_sync_time` END, `custom_info` = CASE `id` WHEN :id THEN :custom_info ELSE `custom_info` END, `sales_type` = CASE `id` WHEN :id THEN :sales_type ELSE `sales_type` END, `contract_no` = CASE `id` WHEN :id THEN :contract_no ELSE `contract_no` END, `execution_no` = CASE `id` WHEN :id THEN :execution_no ELSE `execution_no` END, `customer_code` = CASE `id` WHEN :id THEN :customer_code ELSE `customer_code` END, `customer_name` = CASE `id` WHEN :id THEN :customer_name ELSE `customer_name` END, `source_project_name` = CASE `id` WHEN :id THEN :source_project_name ELSE `source_project_name` END, `order_comment` = CASE `id` WHEN :id THEN :order_comment ELSE `order_comment` END, `order_create_time` = CASE `id` WHEN :id THEN :order_create_time ELSE `order_create_time` END, `customer_required_time` = CASE `id` WHEN :id THEN :customer_required_time ELSE `customer_required_time` END, `source_lifecycle_status` = CASE `id` WHEN :id THEN :source_lifecycle_status ELSE `source_lifecycle_status` END, `status` = CASE `id` WHEN :id THEN :status ELSE `status` END, `company_id` = CASE `id` WHEN :id THEN :company_id ELSE `company_id` END, `company_name` = CASE `id` WHEN :id THEN :company_name ELSE `company_name` END, version = CASE id WHEN :id THEN :before_version_plus_bumps ELSE version END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- B:GenericSyncJdbcStore.updateBatch:LINE:com_sales_order_line; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_sales_order_line` SET `source_version` = CASE `id` WHEN :id THEN :source_version ELSE `source_version` END, `source_updated_at` = CASE `id` WHEN :id THEN :source_updated_at ELSE `source_updated_at` END, `source_sync_time` = CASE `id` WHEN :id THEN :source_sync_time ELSE `source_sync_time` END, `custom_info` = CASE `id` WHEN :id THEN :custom_info ELSE `custom_info` END, `line_type` = CASE `id` WHEN :id THEN :line_type ELSE `line_type` END, `product_code` = CASE `id` WHEN :id THEN :product_code ELSE `product_code` END, `product_desc` = CASE `id` WHEN :id THEN :product_desc ELSE `product_desc` END, `bundle_code` = CASE `id` WHEN :id THEN :bundle_code ELSE `bundle_code` END, `profit_center` = CASE `id` WHEN :id THEN :profit_center ELSE `profit_center` END, `real_execution_no` = CASE `id` WHEN :id THEN :real_execution_no ELSE `real_execution_no` END, `warranty_month` = CASE `id` WHEN :id THEN :warranty_month ELSE `warranty_month` END, `order_qty` = CASE `id` WHEN :id THEN :order_qty ELSE `order_qty` END, `open_qty` = CASE `id` WHEN :id THEN :open_qty ELSE `open_qty` END, `delivered_qty` = CASE `id` WHEN :id THEN :delivered_qty ELSE `delivered_qty` END, `source_lifecycle_status` = CASE `id` WHEN :id THEN :source_lifecycle_status ELSE `source_lifecycle_status` END, `status` = CASE `id` WHEN :id THEN :status ELSE `status` END, `company_id` = CASE `id` WHEN :id THEN :company_id ELSE `company_id` END, `company_name` = CASE `id` WHEN :id THEN :company_name ELSE `company_name` END, `customer_id` = CASE `id` WHEN :id THEN :customer_id ELSE `customer_id` END, `customer_code` = CASE `id` WHEN :id THEN :customer_code ELSE `customer_code` END, `customer_name` = CASE `id` WHEN :id THEN :customer_name ELSE `customer_name` END, version = CASE id WHEN :id THEN :before_version_plus_bumps ELSE version END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
