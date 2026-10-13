-- Aggregate the whole source before applying the task's frozen range selection.
SELECT r.*,g.migrationVersion,
       CASE WHEN g.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT' ELSE NULL END AS migrationIssue,
       SHA2(CAST(JSON_ARRAY(r.id,r.orderNumber,r.lineNum,r.itemCode,r.itemDesc,r.orderQuantity,r.openQuantity,
           r.bundleCode,r.warrantyMonth,r.lineType,r.compCode,r.profitCenter,r.realOrderExecNumber,r.source,r.customInfo,r.syncTime) AS CHAR),256) AS originalSourceFingerprint,
       JSON_OBJECT('id',r.id,'orderNumber',r.orderNumber,'lineNum',r.lineNum,'itemCode',r.itemCode,'itemDesc',r.itemDesc,
           'orderQuantity',r.orderQuantity,'openQuantity',r.openQuantity,'bundleCode',r.bundleCode,'warrantyMonth',r.warrantyMonth,
           'lineType',r.lineType,'compCode',r.compCode,'profitCenter',r.profitCenter,'realOrderExecNumber',r.realOrderExecNumber,
           'source',r.source,'customInfo',r.customInfo,'syncTime',r.syncTime) AS originalSourceFields
FROM pm_order_line_from_erp r
JOIN (SELECT source,compCode,lineType,orderNumber,lineNum,MAX(syncTime) AS migrationVersion,
             COUNT(DISTINCT JSON_ARRAY(itemCode,itemDesc,orderQuantity,openQuantity,bundleCode,warrantyMonth,
                 profitCenter,realOrderExecNumber,customInfo)) AS payloadVariants
      FROM pm_order_line_from_erp GROUP BY source,compCode,lineType,orderNumber,lineNum) g
  ON r.source <=> g.source AND r.compCode <=> g.compCode AND r.lineType <=> g.lineType
     AND r.orderNumber <=> g.orderNumber AND r.lineNum <=> g.lineNum
