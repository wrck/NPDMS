SELECT r.*, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_related_party r
JOIN pm_project p ON p.projectId=r.projectId
WHERE FIND_IN_SET(p.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(r.projectId,:projectIds))
