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
) h
