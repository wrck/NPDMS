SELECT l.*,p.projectCode AS originalProjectCode,p.projectType
FROM pm_project_product_line l
JOIN (/* @include valid-projects */) p ON p.projectId=l.projectId
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
