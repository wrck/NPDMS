SELECT s.*, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_state s
JOIN (/* @include valid-projects */) p ON p.projectId=s.projectId
WHERE FIND_IN_SET(p.projectType,:projectTypes)
  AND p.projectId=p.canonicalProjectId
