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
 LEFT JOIN (/* @include valid-contracts */) scope ON scope.contractNo=r.contractNo
 GROUP BY r.source,company_code,r.orderType,r.orderNumber
) h
WHERE h.originalRowCount>0
ORDER BY h.source,h.company_code,h.orderType,h.orderNumber
