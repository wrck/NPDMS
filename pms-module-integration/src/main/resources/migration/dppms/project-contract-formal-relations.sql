SELECT c.id AS id,
  c.contractNo AS contractNo,
  c.projectGroupCode AS projectGroupCode,
  c.createTime AS createTime,
  c.createBy AS createBy,
  c.updateTime AS updateTime,
  c.updateBy AS updateBy,
  m.id AS member_id,
  m.projectGroupCode AS member_projectGroupCode,
  m.projectCode AS member_projectCode,
  m.mergeBranchMark AS member_mergeBranchMark,
  m.smsProjectCode AS member_smsProjectCode,
  m.createTime AS member_createTime,
  m.createBy AS member_createBy,
  m.updateTime AS member_updateTime,
  m.updateBy AS member_updateBy,
  g.id AS group_id,
  g.projectGroupCode AS group_projectGroupCode,
  g.projectGroupName AS group_projectGroupName,
  g.projectType AS group_projectType,
  g.createTime AS group_createTime,
  g.createBy AS group_createBy,
  g.updateTime AS group_updateTime,
  g.updateBy AS group_updateBy,
  p.projectId AS originalProjectId,
  p.projectCode AS originalProjectCode,
  IF(p.projectId IS NULL,0,1) AS projectCandidateCount,
  CONCAT(c.id,':',COALESCE(m.id,0)) AS relationOriginalKey,
  a.formalGroupSize,
  IF(SUBSTRING(a.createdPick,1,1)='0','ACTIVE','HISTORICAL') AS formalStatus,
  COALESCE(kc.createTime,km.createTime) AS formalCreatedAt,
  COALESCE(NULLIF(kc.createBy,''),NULLIF(km.createBy,'')) AS formalCreator,
  COALESCE(uc.updateTime,um.updateTime) AS formalUpdatedAt,
  COALESCE(NULLIF(uc.updateBy,''),NULLIF(um.updateBy,'')) AS formalUpdater,
  IF(a.formalGroupSize=1,0,(SELECT COUNT(*) FROM pm_project_contract d
  LEFT JOIN pm_project_group dg ON dg.projectGroupCode=d.projectGroupCode AND CAST(dg.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship dm ON dm.projectGroupCode=d.projectGroupCode AND CAST(dm.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  WHERE d.contractNo <=> c.contractNo AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(dg.projectType AS BINARY) <=> CAST(g.projectType AS BINARY)
    AND CAST(REPLACE(dm.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
    AND IF(RIGHT(CAST(dm.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0)=SUBSTRING(a.createdPick,1,1)
    AND COALESCE(d.createTime,dm.createTime) <=> COALESCE(kc.createTime,km.createTime)
    AND NOT (CAST(COALESCE(NULLIF(d.createBy,''),NULLIF(dm.createBy,'')) AS BINARY) <=> CAST(COALESCE(NULLIF(kc.createBy,''),NULLIF(km.createBy,'')) AS BINARY)))) AS formalCreateConflicts,
  IF(a.formalGroupSize=1,0,(SELECT COUNT(*) FROM pm_project_contract d
  LEFT JOIN pm_project_group dg ON dg.projectGroupCode=d.projectGroupCode AND CAST(dg.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship dm ON dm.projectGroupCode=d.projectGroupCode AND CAST(dm.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  WHERE d.contractNo <=> c.contractNo AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(dg.projectType AS BINARY) <=> CAST(g.projectType AS BINARY)
    AND CAST(REPLACE(dm.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
    AND IF(RIGHT(CAST(dm.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0)=SUBSTRING(a.createdPick,1,1)
    AND COALESCE(d.updateTime,dm.updateTime) <=> COALESCE(uc.updateTime,um.updateTime)
    AND NOT (CAST(COALESCE(NULLIF(d.updateBy,''),NULLIF(dm.updateBy,'')) AS BINARY) <=> CAST(COALESCE(NULLIF(uc.updateBy,''),NULLIF(um.updateBy,'')) AS BINARY)))) AS formalUpdateConflicts,
  kc.id AS canonical_id,
  kc.contractNo AS canonical_contractNo,
  kc.projectGroupCode AS canonical_projectGroupCode,
  kc.createTime AS canonical_createTime,
  kc.createBy AS canonical_createBy,
  kc.updateTime AS canonical_updateTime,
  kc.updateBy AS canonical_updateBy,
  km.id AS canonical_member_id,
  km.projectGroupCode AS canonical_member_projectGroupCode,
  km.projectCode AS canonical_member_projectCode,
  km.mergeBranchMark AS canonical_member_mergeBranchMark,
  km.smsProjectCode AS canonical_member_smsProjectCode,
  km.createTime AS canonical_member_createTime,
  km.createBy AS canonical_member_createBy,
  km.updateTime AS canonical_member_updateTime,
  km.updateBy AS canonical_member_updateBy,
  kg.id AS canonical_group_id,
  kg.projectGroupCode AS canonical_group_projectGroupCode,
  kg.projectGroupName AS canonical_group_projectGroupName,
  kg.projectType AS canonical_group_projectType,
  kg.createTime AS canonical_group_createTime,
  kg.createBy AS canonical_group_createBy,
  kg.updateTime AS canonical_group_updateTime,
  kg.updateBy AS canonical_group_updateBy,
  p.projectId AS canonical_originalProjectId,
  p.projectCode AS canonical_originalProjectCode,
  CONCAT(LPAD(kc.id,20,'0'),':',LPAD(COALESCE(km.id,0),20,'0'),':',
    IF(c.id=kc.id AND COALESCE(m.id,0)=COALESCE(km.id,0),0,1),':',
    LPAD(c.id,20,'0'),':',LPAD(COALESCE(m.id,0),20,'0')) AS relationSourceKey
FROM pm_project_contract c
LEFT JOIN pm_project_group g ON g.projectGroupCode=c.projectGroupCode AND CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship m ON m.projectGroupCode=c.projectGroupCode AND CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
JOIN (/* @include valid-canonical-projects */) p
  ON p.projectType=g.projectType AND p.projectCode=REPLACE(m.projectCode,'-his','')
  AND CAST(p.projectType AS BINARY)=CAST(g.projectType AS BINARY)
  AND CAST(p.projectCode AS BINARY)=CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
JOIN (
  -- Aggregate the full formal identity before applying task scope or streaming chunks.
  -- Picks encode status priority (1), timestamp (20), contract ID (20), member ID (20).
  SELECT CAST(g.projectType AS BINARY) AS projectType,CAST(REPLACE(m.projectCode,'-his','') AS BINARY) AS projectCode,
    CAST(c.contractNo AS BINARY) AS contractNo,COUNT(*) AS formalGroupSize,
    MIN(CONCAT(IF(RIGHT(CAST(m.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0),
      COALESCE(DATE_FORMAT(COALESCE(c.createTime,m.createTime),'%Y%m%d%H%i%s%f'),'00000000000000000000'),
      LPAD(c.id,20,'0'),LPAD(COALESCE(m.id,0),20,'0'))) AS createdPick,
    MAX(CONCAT(1-IF(RIGHT(CAST(m.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0),
      COALESCE(DATE_FORMAT(COALESCE(c.updateTime,m.updateTime),'%Y%m%d%H%i%s%f'),'00000000000000000000'),
      LPAD(c.id,20,'0'),LPAD(COALESCE(m.id,0),20,'0'))) AS updatedPick
  FROM pm_project_contract c
  LEFT JOIN pm_project_group g ON g.projectGroupCode=c.projectGroupCode AND CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship m ON m.projectGroupCode=c.projectGroupCode AND CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
  GROUP BY CAST(g.projectType AS BINARY),CAST(REPLACE(m.projectCode,'-his','') AS BINARY),CAST(c.contractNo AS BINARY)
) a ON a.projectType <=> CAST(g.projectType AS BINARY)
  AND a.projectCode <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY) AND a.contractNo <=> CAST(c.contractNo AS BINARY)
LEFT JOIN pm_project_contract kc ON kc.id=CAST(SUBSTRING(a.createdPick,22,20) AS UNSIGNED)
LEFT JOIN pm_project_group_relationship km ON km.id=CAST(SUBSTRING(a.createdPick,42,20) AS UNSIGNED)
LEFT JOIN pm_project_group kg ON kg.projectGroupCode=kc.projectGroupCode AND CAST(kg.projectGroupCode AS BINARY)=CAST(kc.projectGroupCode AS BINARY)
LEFT JOIN pm_project_contract uc ON uc.id=CAST(SUBSTRING(a.updatedPick,22,20) AS UNSIGNED)
LEFT JOIN pm_project_group_relationship um ON um.id=CAST(SUBSTRING(a.updatedPick,42,20) AS UNSIGNED)
WHERE (g.id IS NULL OR FIND_IN_SET(g.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
