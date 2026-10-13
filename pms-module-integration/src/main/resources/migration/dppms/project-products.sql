SELECT l.*,p.projectCode AS originalProjectCode,p.projectType
FROM pm_project_product_line l
LEFT JOIN pm_project p ON p.projectId=l.projectId
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(l.projectId,:projectIds))
