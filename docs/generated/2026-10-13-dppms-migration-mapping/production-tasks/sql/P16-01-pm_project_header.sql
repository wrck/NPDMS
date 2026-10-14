-- P16 / pm_project_header | 原任务 2108608417343934465 v10
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT p.*, c.compCode AS migrationCompanyCode, fc.actorId AS creatorFndSourceKey, tc.actorId AS creatorTSourceKey, fu.actorId AS updaterFndSourceKey, tu.actorId AS updaterTSourceKey, COALESCE(fc.matchCount,0) AS creatorFndMatchCount, COALESCE(tc.matchCount,0) AS creatorTMatchCount, COALESCE(fu.matchCount,0) AS updaterFndMatchCount, COALESCE(tu.matchCount,0) AS updaterTMatchCount,fc.actorIds AS creatorFndSourceKeys,fu.actorIds AS updaterFndSourceKeys
FROM pm_project p
JOIN (SELECT MAX(projectId) AS projectId FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical ON canonical.projectId=p.projectId
LEFT JOIN t_company c ON c.id=p.compId
LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey,COUNT(*) AS matchCount,CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId,GROUP_CONCAT(u.id ORDER BY u.id) AS actorIds FROM fnd_user_info u WHERE NOT FIND_IN_SET(u.id,:excludedFndActorIds) GROUP BY CAST(u.username AS BINARY)) fc ON fc.loginKey=CAST(p.createBy AS BINARY)
LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey,COUNT(*) AS matchCount,CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tc ON tc.loginKey=CAST(p.createBy AS BINARY)
LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey,COUNT(*) AS matchCount,CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId,GROUP_CONCAT(u.id ORDER BY u.id) AS actorIds FROM fnd_user_info u WHERE NOT FIND_IN_SET(u.id,:excludedFndActorIds) GROUP BY CAST(u.username AS BINARY)) fu ON fu.loginKey=CAST(p.updateBy AS BINARY)
LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey,COUNT(*) AS matchCount,CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tu ON tu.loginKey=CAST(p.updateBy AS BINARY)
WHERE FIND_IN_SET(p.projectType,:projectTypes) AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds));
