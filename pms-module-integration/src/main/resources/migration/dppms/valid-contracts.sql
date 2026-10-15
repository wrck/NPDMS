SELECT DISTINCT pc.contractNo
FROM pm_project_group_relationship m
JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
  AND CAST(g.projectGroupCode AS BINARY)=CAST(m.projectGroupCode AS BINARY)
JOIN (/* @include valid-canonical-projects */) p
  ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
  AND CAST(p.projectCode AS BINARY)=CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
  AND CAST(p.projectType AS BINARY)=CAST(g.projectType AS BINARY)
JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
  AND CAST(pc.projectGroupCode AS BINARY)=CAST(g.projectGroupCode AS BINARY)
