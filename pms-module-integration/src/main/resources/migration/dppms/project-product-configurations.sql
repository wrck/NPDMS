SELECT l.*, p.projectId AS sourceProjectId, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_product_config_level_info_from_crm l
LEFT JOIN (
    SELECT p.projectId, p.projectCode, p.projectType,
           CAST(SUBSTRING_INDEX(p.projectCode,'-',1) AS BINARY) AS queryProjectCode
    FROM pm_project p
    INNER JOIN (
        SELECT projectType, projectCode, MAX(projectId) AS projectId
        FROM pm_project GROUP BY projectType, projectCode
    ) selected ON selected.projectId=p.projectId
    GROUP BY p.projectId, p.projectCode, p.projectType
) p ON p.queryProjectCode=CAST(l.projectCode AS BINARY)
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
