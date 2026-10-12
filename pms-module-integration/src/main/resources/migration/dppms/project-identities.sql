SELECT original.projectId AS originalProjectId,original.projectType,original.projectCode,
  canonical.projectId AS canonicalProjectId
FROM pm_project original
JOIN (SELECT CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY)) AS projectIdentity,
  MAX(projectId) AS projectId FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
  ON canonical.projectIdentity <=> CONCAT(CAST(original.projectType AS BINARY),0x00,CAST(original.projectCode AS BINARY))
WHERE FIND_IN_SET(original.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))
