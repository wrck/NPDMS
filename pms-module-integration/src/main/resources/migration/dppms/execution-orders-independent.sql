SELECT r.*
FROM pm_project_property_from_sms r
JOIN (
  SELECT CAST(dataSource AS BINARY) AS sourceKey,
    CAST(orderExecNumber AS BINARY) AS executionKey, MAX(id) AS selectedId
  FROM pm_project_property_from_sms
  GROUP BY CAST(dataSource AS BINARY), CAST(orderExecNumber AS BINARY)
) chosen ON chosen.selectedId=r.id
