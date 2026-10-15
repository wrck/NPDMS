-- Existing order identities only. The task supplies reviewed fingerprints and the exact approved source scope.
-- Every selected original keeps its full sixteen source columns. Conflicting originals stay in separate evidence.
SELECT r.*, g.migrationVersion,
       CASE WHEN g.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT' ELSE NULL END AS migrationIssue,
       SHA2(CAST(JSON_ARRAY(r.id,r.orderNumber,r.contractNo,r.orderExecNumber,r.orderCreateTime,
           r.customerRequireTime,r.customerCode,r.customerName,r.projectName,r.orderComment,r.orderType,
           r.compCode,r.salesType,r.source,r.customInfo,r.syncTime) AS CHAR),256) AS originalSourceFingerprint,
       JSON_OBJECT('id',r.id,'orderNumber',r.orderNumber,'contractNo',r.contractNo,'orderExecNumber',r.orderExecNumber,
           'orderCreateTime',r.orderCreateTime,'customerRequireTime',r.customerRequireTime,'customerCode',r.customerCode,
           'customerName',r.customerName,'projectName',r.projectName,'orderComment',r.orderComment,'orderType',r.orderType,
           'compCode',r.compCode,'salesType',r.salesType,'source',r.source,'customInfo',r.customInfo,'syncTime',r.syncTime) AS originalSourceFields
FROM pm_order_data_from_erp r
JOIN (/* @include valid-contracts */) scope ON scope.contractNo=r.contractNo
JOIN (SELECT source,compCode,orderType,orderNumber,MAX(syncTime) AS migrationVersion,
             COUNT(DISTINCT JSON_ARRAY(orderCreateTime,customerRequireTime,customerCode,customerName,projectName,
                 orderComment,salesType,customInfo,contractNo,orderExecNumber)) AS payloadVariants
      FROM pm_order_data_from_erp GROUP BY source,compCode,orderType,orderNumber) g
  ON r.source <=> g.source AND r.compCode <=> g.compCode AND r.orderType <=> g.orderType AND r.orderNumber <=> g.orderNumber
WHERE (:reviewedSourceIds='' OR FIND_IN_SET(r.id,:reviewedSourceIds))
  AND (:unreviewedSourceIds='' OR NOT FIND_IN_SET(r.id,:unreviewedSourceIds))
