SELECT l.*, p.projectId AS sourceProjectId, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_product_config_level_info_from_crm l
JOIN (
    SELECT p.projectId,p.projectCode,p.projectType,
      CAST(SUBSTRING_INDEX(p.projectCode,'-',1) AS BINARY) AS queryProjectCode
    FROM (/* @include valid-projects */) p
    WHERE p.projectId=p.canonicalProjectId
) p ON p.queryProjectCode=CAST(l.projectCode AS BINARY)
