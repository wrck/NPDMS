SELECT r.*, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_related_party r
JOIN (/* @include valid-projects */) p ON p.projectId=r.projectId
WHERE FIND_IN_SET(p.projectType,:projectTypes)
