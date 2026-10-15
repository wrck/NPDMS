SELECT CAST(p.projectId AS CHAR) AS _groupKey,
  CAST(JSON_MERGE_PRESERVE(
    JSON_ARRAY(JSON_OBJECT('object','pm_project_header','fields',JSON_OBJECT(
      'projectId',p.projectId,
      'projectType',p.projectType,
      'projectCode',p.projectCode,
      'projectName',p.projectName,
      'projectState',p.projectState,
      'isback',p.isback,
      'column001',p.column001,
      'column002',p.column002,
      'column003',p.column003,
      'column004',p.column004,
      'column005',p.column005,
      'column006',p.column006,
      'column007',p.column007,
      'column008',p.column008,
      'column009',p.column009,
      'column010',p.column010,
      'column011',p.column011,
      'column012',p.column012,
      'columno12_readonly',p.columno12_readonly,
      'column013',p.column013,
      'column014',p.column014,
      'customerProjectName',p.customerProjectName,
      'salesType',p.salesType,
      'majorProjectLevel',p.majorProjectLevel,
      'compId',p.compId,
      'createTime',p.createTime,
      'createBy',p.createBy,
      'updateTime',p.updateTime,
      'updateBy',p.updateBy,
      'effectiveFrom',p.effectiveFrom,
      'effectiveTo',p.effectiveTo,
      'disabled',CAST(p.disabled AS UNSIGNED),
      'projectStartTime',p.projectStartTime,
      'projectRefreshTime',p.projectRefreshTime,
      'projectCloseTime',p.projectCloseTime,
      'customInfo',p.customInfo,
      'customConfig',p.customConfig,
      'creatorFndSourceKey',MAX(CASE WHEN audit_role.kind=0 THEN actors.fndId END),
      'creatorTSourceKey',MAX(CASE WHEN audit_role.kind=0 THEN actors.tId END),
      'updaterFndSourceKey',MAX(CASE WHEN audit_role.kind=1 THEN actors.fndId END),
      'updaterTSourceKey',MAX(CASE WHEN audit_role.kind=1 THEN actors.tId END),
      'creatorFndMatchCount',COALESCE(MAX(CASE WHEN audit_role.kind=0 THEN actors.fndCount END),0),
      'creatorTMatchCount',COALESCE(MAX(CASE WHEN audit_role.kind=0 THEN actors.tCount END),0),
      'updaterFndMatchCount',COALESCE(MAX(CASE WHEN audit_role.kind=1 THEN actors.fndCount END),0),
      'updaterTMatchCount',COALESCE(MAX(CASE WHEN audit_role.kind=1 THEN actors.tCount END),0),
      'creatorFndSourceKeys',MAX(CASE WHEN audit_role.kind=0 THEN actors.fndIds END),
      'updaterFndSourceKeys',MAX(CASE WHEN audit_role.kind=1 THEN actors.fndIds END),
      'migrationCompanyCode',MAX(c.compCode)))),
    CAST(MAX(CAST(canonical.originalRows AS CHAR)) AS JSON)
  ) AS CHAR) AS _sourceRows
FROM pm_project p
JOIN (
  SELECT MAX(projectId) AS projectId,
    JSON_ARRAYAGG(CAST(CASE WHEN disabled=0 AND effectiveTo IS NULL THEN
      JSON_OBJECT('object','pm_project_identity','fields',JSON_OBJECT(
        'originalProjectId',projectId,'projectType',projectType,'projectCode',projectCode))
      ELSE 'null' END AS JSON)) AS originalRows
  FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)
) canonical ON canonical.projectId=p.projectId
LEFT JOIN t_company c ON c.id=p.compId
JOIN (SELECT 0 AS kind UNION ALL SELECT 1) audit_role
LEFT JOIN (
  SELECT loginKey,
    SUM(origin='FND') AS fndCount,SUM(origin='T') AS tCount,
    CASE WHEN SUM(origin='FND')=1 THEN MAX(CASE WHEN origin='FND' THEN actorId END) END AS fndId,
    CASE WHEN SUM(origin='T')=1 THEN MAX(CASE WHEN origin='T' THEN actorId END) END AS tId,
    GROUP_CONCAT(CASE WHEN origin='FND' THEN actorId END ORDER BY actorId) AS fndIds
  FROM (
    SELECT CAST(username AS BINARY) AS loginKey,'FND' AS origin,id AS actorId
    FROM fnd_user_info WHERE NOT FIND_IN_SET(id,:excludedFndActorIds)
    UNION ALL
    SELECT CAST(user_name AS BINARY),'T',user_id FROM t_user
  ) source_actors GROUP BY loginKey
) actors ON actors.loginKey=CAST(CASE WHEN audit_role.kind=0 THEN p.createBy ELSE p.updateBy END AS BINARY)
WHERE p.disabled=0 AND p.effectiveTo IS NULL
  AND FIND_IN_SET(p.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
GROUP BY p.projectId
