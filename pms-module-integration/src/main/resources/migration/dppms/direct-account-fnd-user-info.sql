SELECT u.sourceId,
 CAST(JSON_ARRAY(JSON_OBJECT('object','fnd_user_info','fields',JSON_MERGE_PRESERVE(CAST(u.fields AS JSON),JSON_OBJECT('migrationLoginCount',l.loginCount,'migrationLoginEmployeeId',l.empID,'migrationLoginWorkNo',l.workNo,'migrationLoginName',l.name,'migrationEmployeeId',e.empID,'migrationEmployeeWorkNo',e.workNo,'migrationEmployeeName',e.name))),
 JSON_OBJECT('object','FND_USER_ROLE','fields',JSON_MERGE_PRESERVE(CAST(u.fields AS JSON),JSON_OBJECT('migrationLoginCount',l.loginCount,'migrationLoginEmployeeId',l.empID,'migrationLoginWorkNo',l.workNo,'migrationLoginName',l.name,'migrationEmployeeId',e.empID,'migrationEmployeeWorkNo',e.workNo,'migrationEmployeeName',e.name)))) AS CHAR) AS _sourceRows
FROM (
SELECT 'fnd_user_info' AS objectName,r.`id` AS sourceId,r.username AS username,JSON_OBJECT('id',r.`id`,'username',r.`username`,'email',r.`email`,'dpNo',r.`dpNo`,'realName',r.`realName`,'roleIds',r.`roleIds`,'isemail',r.`isemail`,'status',r.`status`,'defaultPage',r.`defaultPage`,'pwdoverdue',r.`pwdoverdue`,'createBy',r.`createBy`,'createTime',r.`createTime`,'updateBy',r.`updateBy`,'updateTime',r.`updateTime`,'effectiveFrom',r.`effectiveFrom`,'effectiveTo',r.`effectiveTo`,'customInfo',r.`customInfo`) AS fields FROM fnd_user_info r
) u
LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name
  FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u.username AS BINARY)
LEFT JOIN ehr_employee e ON e.empID=l.empID
