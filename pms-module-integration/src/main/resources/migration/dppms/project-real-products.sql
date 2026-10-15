SELECT l.*, p.projectCode AS originalProjectCode, p.projectType,
       u.projectCode AS upstream_projectCode, u.orderExecNumber AS upstream_orderExecNumber,
       u.productFirstName AS upstream_productFirstName, u.productName AS upstream_productName,
       u.productSubCode AS upstream_productSubCode, u.productSubModel AS upstream_productSubModel,
       u.productSubName AS upstream_productSubName, u.num AS upstream_num,
       u.memo AS upstream_memo, u.dataSource AS upstream_dataSource,
       COALESCE(u.matchCount,0) AS upstreamMatchCount
FROM pm_project_product_line_real l
JOIN (/* @include valid-projects */) p ON p.projectId=l.projectId
LEFT JOIN (
  SELECT MIN(projectCode) AS projectCode, MIN(orderExecNumber) AS orderExecNumber,
    MIN(productFirstName) AS productFirstName, MIN(productName) AS productName,
    MIN(productSubCode) AS productSubCode, MIN(productSubModel) AS productSubModel,
    MIN(productSubName) AS productSubName, MIN(num) AS num, MIN(memo) AS memo,
    CASE WHEN COUNT(*)=1 THEN MAX(dataSource) ELSE NULL END AS dataSource, COUNT(*) AS matchCount
  FROM pm_project_real_product_line_from_sms
  GROUP BY CAST(projectCode AS BINARY), CAST(orderExecNumber AS BINARY),
    CAST(productFirstName AS BINARY), CAST(productName AS BINARY), CAST(productSubCode AS BINARY),
    CAST(productSubModel AS BINARY), CAST(productSubName AS BINARY), num, CAST(memo AS BINARY)
) u ON u.projectCode <=> l.projectCode
 AND CAST(u.projectCode AS BINARY) <=> CAST(l.projectCode AS BINARY)
 AND CAST(u.orderExecNumber AS BINARY) <=> CAST(l.orderExecNumber AS BINARY)
 AND CAST(u.productFirstName AS BINARY) <=> CAST(l.productFirstName AS BINARY)
 AND CAST(u.productName AS BINARY) <=> CAST(l.productName AS BINARY)
 AND CAST(u.productSubCode AS BINARY) <=> CAST(l.productSubCode AS BINARY)
 AND CAST(u.productSubModel AS BINARY) <=> CAST(l.productSubModel AS BINARY)
 AND CAST(u.productSubName AS BINARY) <=> CAST(l.productSubName AS BINARY)
 AND u.num <=> l.num AND CAST(u.memo AS BINARY) <=> CAST(l.memo AS BINARY)
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
