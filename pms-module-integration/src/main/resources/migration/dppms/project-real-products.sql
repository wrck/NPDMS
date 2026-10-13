SELECT l.*, p.projectCode AS originalProjectCode, p.projectType,
       u.projectCode AS upstream_projectCode, u.orderExecNumber AS upstream_orderExecNumber,
       u.productFirstName AS upstream_productFirstName, u.productName AS upstream_productName,
       u.productSubCode AS upstream_productSubCode, u.productSubModel AS upstream_productSubModel,
       u.productSubName AS upstream_productSubName, u.num AS upstream_num,
       u.memo AS upstream_memo, u.dataSource AS upstream_dataSource,
       (SELECT COUNT(*) FROM pm_project_real_product_line_from_sms m
        WHERE CAST(m.projectCode AS BINARY) <=> CAST(l.projectCode AS BINARY)
          AND CAST(m.orderExecNumber AS BINARY) <=> CAST(l.orderExecNumber AS BINARY)
          AND CAST(m.productFirstName AS BINARY) <=> CAST(l.productFirstName AS BINARY)
          AND CAST(m.productName AS BINARY) <=> CAST(l.productName AS BINARY)
          AND CAST(m.productSubCode AS BINARY) <=> CAST(l.productSubCode AS BINARY)
          AND CAST(m.productSubModel AS BINARY) <=> CAST(l.productSubModel AS BINARY)
          AND CAST(m.productSubName AS BINARY) <=> CAST(l.productSubName AS BINARY)
          AND m.num <=> l.num AND CAST(m.memo AS BINARY) <=> CAST(l.memo AS BINARY)) AS upstreamMatchCount
FROM pm_project_product_line_real l
LEFT JOIN pm_project p ON p.projectId=l.projectId
LEFT JOIN pm_project_real_product_line_from_sms u
  ON CAST(u.projectCode AS BINARY) <=> CAST(l.projectCode AS BINARY)
 AND CAST(u.orderExecNumber AS BINARY) <=> CAST(l.orderExecNumber AS BINARY)
 AND CAST(u.productFirstName AS BINARY) <=> CAST(l.productFirstName AS BINARY)
 AND CAST(u.productName AS BINARY) <=> CAST(l.productName AS BINARY)
 AND CAST(u.productSubCode AS BINARY) <=> CAST(l.productSubCode AS BINARY)
 AND CAST(u.productSubModel AS BINARY) <=> CAST(l.productSubModel AS BINARY)
 AND CAST(u.productSubName AS BINARY) <=> CAST(l.productSubName AS BINARY)
 AND u.num <=> l.num AND CAST(u.memo AS BINARY) <=> CAST(l.memo AS BINARY)
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(l.projectId,:projectIds))
