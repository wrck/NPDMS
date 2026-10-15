SELECT p.*,fc.actorId AS creatorFndSourceKey,tc.actorId AS creatorTSourceKey,
  fu.actorId AS updaterFndSourceKey,tu.actorId AS updaterTSourceKey,
  COALESCE(fc.matchCount,0) AS creatorFndMatchCount,COALESCE(tc.matchCount,0) AS creatorTMatchCount,
  COALESCE(fu.matchCount,0) AS updaterFndMatchCount,COALESCE(tu.matchCount,0) AS updaterTMatchCount,
  fc.actorIds AS creatorFndSourceKeys,fu.actorIds AS updaterFndSourceKeys
FROM pm_pb_plan_from_sms p
JOIN (/* @include valid-contracts */) scope ON scope.contractNo=p.contractNo
LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey,COUNT(*) AS matchCount,
  CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId,GROUP_CONCAT(u.id ORDER BY u.id) AS actorIds
  FROM fnd_user_info u GROUP BY CAST(u.username AS BINARY)) fc ON fc.loginKey=CAST(p.createBy AS BINARY)
LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey,COUNT(*) AS matchCount,
  CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId
  FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tc ON tc.loginKey=CAST(p.createBy AS BINARY)
LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey,COUNT(*) AS matchCount,
  CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId,GROUP_CONCAT(u.id ORDER BY u.id) AS actorIds
  FROM fnd_user_info u GROUP BY CAST(u.username AS BINARY)) fu ON fu.loginKey=CAST(p.updateBy AS BINARY)
LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey,COUNT(*) AS matchCount,
  CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId
  FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tu ON tu.loginKey=CAST(p.updateBy AS BINARY)
WHERE (:planIds='' OR FIND_IN_SET(p.id,:planIds))
