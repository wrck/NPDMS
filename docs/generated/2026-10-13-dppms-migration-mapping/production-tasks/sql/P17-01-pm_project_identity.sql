-- P17 / pm_project_identity | 原任务 2109622534918856706 v6
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT original.projectId AS originalProjectId,original.projectType,original.projectCode,
  canonical.projectId AS canonicalProjectId
FROM pm_project original
JOIN (SELECT CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY)) AS projectIdentity,
  MAX(projectId) AS projectId FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
  ON canonical.projectIdentity <=> CONCAT(CAST(original.projectType AS BINARY),0x00,CAST(original.projectCode AS BINARY))
WHERE FIND_IN_SET(original.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds));
