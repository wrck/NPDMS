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
) h
