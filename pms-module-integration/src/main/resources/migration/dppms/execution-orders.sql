SELECT r.id, r.orderExecNumber, r.dataSource, r.projectCode, r.projectName,
  r.salesManCode, r.salesManName, r.marketCode, r.marketName, r.systemId, r.systemName,
  r.expendId, r.expendName, r.industryId, r.industryName, r.officeCode, r.officeName,
  r.serviceTypeName, r.channelName, r.engineeFee, r.objId, r.applyType, r.corporationCode,
  r.customerProjectName, r.finalCustomerName, r.agentName, r.majorProjectLevel,
  r.projectMoney, r.submitTime, r.predBidDate, r.linkmanName, r.linkmanTel,
  latest.id AS canonicalId, latest.orderExecNumber AS canonical_orderExecNumber,
  latest.dataSource AS canonical_dataSource, latest.projectCode AS canonical_projectCode,
  latest.projectName AS canonical_projectName, latest.salesManCode AS canonical_salesManCode,
  latest.salesManName AS canonical_salesManName, latest.marketCode AS canonical_marketCode,
  latest.marketName AS canonical_marketName, latest.systemId AS canonical_systemId,
  latest.systemName AS canonical_systemName, latest.expendId AS canonical_expendId,
  latest.expendName AS canonical_expendName, latest.industryId AS canonical_industryId,
  latest.industryName AS canonical_industryName, latest.officeCode AS canonical_officeCode,
  latest.officeName AS canonical_officeName, latest.serviceTypeName AS canonical_serviceTypeName,
  latest.channelName AS canonical_channelName, latest.engineeFee AS canonical_engineeFee,
  latest.objId AS canonical_objId, latest.applyType AS canonical_applyType,
  latest.corporationCode AS canonical_corporationCode, latest.customerProjectName AS canonical_customerProjectName,
  latest.finalCustomerName AS canonical_finalCustomerName, latest.agentName AS canonical_agentName,
  latest.majorProjectLevel AS canonical_majorProjectLevel, latest.projectMoney AS canonical_projectMoney,
  latest.submitTime AS canonical_submitTime, latest.predBidDate AS canonical_predBidDate,
  latest.linkmanName AS canonical_linkmanName, latest.linkmanTel AS canonical_linkmanTel
FROM pm_project_property_from_sms r
JOIN (
  SELECT dataSource, orderExecNumber, CAST(dataSource AS BINARY) AS systemKey,
    CAST(orderExecNumber AS BINARY) AS executionKey, MAX(id) AS canonicalId
  FROM pm_project_property_from_sms
  GROUP BY dataSource, orderExecNumber, CAST(dataSource AS BINARY), CAST(orderExecNumber AS BINARY)
) selected ON selected.dataSource <=> r.dataSource AND selected.orderExecNumber <=> r.orderExecNumber
  AND selected.systemKey <=> CAST(r.dataSource AS BINARY)
  AND selected.executionKey <=> CAST(r.orderExecNumber AS BINARY)
JOIN pm_project_property_from_sms latest ON latest.id=selected.canonicalId
LEFT JOIN (
  SELECT MIN(SUBSTRING_INDEX(projectCode,'-',1)) AS projectCode
  FROM (/* @include valid-projects */) valid_projects
  WHERE (:projectTypes='' OR FIND_IN_SET(projectType,:projectTypes))
    AND (:projectIds='' OR FIND_IN_SET(projectId,:projectIds))
  GROUP BY CAST(SUBSTRING_INDEX(projectCode,'-',1) AS BINARY)
) project_scope ON project_scope.projectCode=r.projectCode
  AND CAST(project_scope.projectCode AS BINARY)=CAST(r.projectCode AS BINARY)
WHERE project_scope.projectCode IS NOT NULL
