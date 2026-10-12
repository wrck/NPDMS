SELECT c.*, (SELECT COUNT(*) FROM pm_project_contract d
  WHERE d.contractNo <=> c.contractNo AND d.projectGroupCode <=> c.projectGroupCode
    AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(d.projectGroupCode AS BINARY) <=> CAST(c.projectGroupCode AS BINARY)
    AND CAST(d.createBy AS BINARY) <=> CAST(c.createBy AS BINARY)
    AND d.updateTime <=> c.updateTime
    AND CAST(d.updateBy AS BINARY) <=> CAST(c.updateBy AS BINARY)) AS duplicateGroupSize,
  (SELECT d.id FROM pm_project_contract d WHERE d.contractNo <=> c.contractNo AND d.projectGroupCode <=> c.projectGroupCode
    AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(d.projectGroupCode AS BINARY) <=> CAST(c.projectGroupCode AS BINARY)
    AND CAST(d.createBy AS BINARY) <=> CAST(c.createBy AS BINARY)
    AND d.updateTime <=> c.updateTime
    AND CAST(d.updateBy AS BINARY) <=> CAST(c.updateBy AS BINARY) ORDER BY d.createTime,d.id LIMIT 1) AS canonicalOriginalId,
  (SELECT MIN(d.createTime) FROM pm_project_contract d WHERE d.contractNo <=> c.contractNo AND d.projectGroupCode <=> c.projectGroupCode
    AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(d.projectGroupCode AS BINARY) <=> CAST(c.projectGroupCode AS BINARY)
    AND CAST(d.createBy AS BINARY) <=> CAST(c.createBy AS BINARY)
    AND d.updateTime <=> c.updateTime
    AND CAST(d.updateBy AS BINARY) <=> CAST(c.updateBy AS BINARY)) AS canonicalCreatedAt,
  (SELECT MIN(d.createTime)=MAX(d.createTime) FROM pm_project_contract d WHERE d.contractNo <=> c.contractNo AND d.projectGroupCode <=> c.projectGroupCode
    AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(d.projectGroupCode AS BINARY) <=> CAST(c.projectGroupCode AS BINARY)
    AND CAST(d.createBy AS BINARY) <=> CAST(c.createBy AS BINARY)
    AND d.updateTime <=> c.updateTime
    AND CAST(d.updateBy AS BINARY) <=> CAST(c.updateBy AS BINARY)) AS duplicateIdentical,
  CONCAT(LPAD((SELECT d.id FROM pm_project_contract d WHERE d.contractNo <=> c.contractNo AND d.projectGroupCode <=> c.projectGroupCode
    AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(d.projectGroupCode AS BINARY) <=> CAST(c.projectGroupCode AS BINARY)
    AND CAST(d.createBy AS BINARY) <=> CAST(c.createBy AS BINARY)
    AND d.updateTime <=> c.updateTime
    AND CAST(d.updateBy AS BINARY) <=> CAST(c.updateBy AS BINARY) ORDER BY d.createTime,d.id LIMIT 1),20,'0'),':',LPAD(COALESCE(m.id,0),20,'0'),':',IF(c.id=(SELECT d.id FROM pm_project_contract d WHERE d.contractNo <=> c.contractNo AND d.projectGroupCode <=> c.projectGroupCode
    AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(d.projectGroupCode AS BINARY) <=> CAST(c.projectGroupCode AS BINARY)
    AND CAST(d.createBy AS BINARY) <=> CAST(c.createBy AS BINARY)
    AND d.updateTime <=> c.updateTime
    AND CAST(d.updateBy AS BINARY) <=> CAST(c.updateBy AS BINARY) ORDER BY d.createTime,d.id LIMIT 1),0,1),':',LPAD(c.id,20,'0')) AS relationSourceKey,
  CONCAT(c.id,':',COALESCE(m.id,0)) AS relationOriginalKey,
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
  p.projectId AS originalProjectId,p.projectCode AS originalProjectCode,
  IF(p.projectId IS NULL,0,1) AS projectCandidateCount
FROM pm_project_contract c
LEFT JOIN pm_project_group g ON g.projectGroupCode=c.projectGroupCode
  AND CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
LEFT JOIN pm_project_group_relationship m ON m.projectGroupCode=c.projectGroupCode
  AND CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
LEFT JOIN (SELECT CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY)) AS projectIdentity,
  MAX(projectId) AS projectId FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
  ON canonical.projectIdentity=CONCAT(CAST(g.projectType AS BINARY),0x00,CAST(REPLACE(m.projectCode,'-his','') AS BINARY))
LEFT JOIN pm_project p ON p.projectId=canonical.projectId
WHERE (g.id IS NULL OR FIND_IN_SET(g.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
