SELECT s.*, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_state s
JOIN pm_project p ON p.projectId=s.projectId
WHERE FIND_IN_SET(p.projectType,:projectTypes)
  AND p.projectId=(SELECT MAX(c.projectId) FROM pm_project c
                  WHERE c.projectType=p.projectType AND c.projectCode=p.projectCode)
  AND (:projectIds='' OR FIND_IN_SET(s.projectId,:projectIds))
