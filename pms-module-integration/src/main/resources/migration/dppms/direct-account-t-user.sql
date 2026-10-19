SELECT u.sourceId,
 CAST(JSON_ARRAY(JSON_OBJECT('object','t_user','fields',JSON_MERGE_PRESERVE(CAST(u.fields AS JSON),JSON_OBJECT('migrationLoginCount',l.loginCount,'migrationLoginEmployeeId',l.empID,'migrationLoginWorkNo',l.workNo,'migrationLoginName',l.name,'migrationEmployeeId',e.empID,'migrationEmployeeWorkNo',e.workNo,'migrationEmployeeName',e.name)))) AS CHAR) AS _sourceRows
FROM (
SELECT 't_user' AS objectName,r.`user_id` AS sourceId,r.user_name AS username,JSON_OBJECT('user_id',r.`user_id`,'user_name',r.`user_name`,'create_by',r.`create_by`,'create_time',r.`create_time`,'update_by',r.`update_by`,'update_time',r.`update_time`,'status',r.`status`,'needChangePwd',CAST(r.needChangePwd AS UNSIGNED),'loginErrorCount',r.`loginErrorCount`,'isSysUser',r.`isSysUser`,'userCustom1',r.`userCustom1`,'userCustom2',r.`userCustom2`,'userCustom3',r.`userCustom3`,'userCustom4',r.`userCustom4`,'userCustom5',r.`userCustom5`) AS fields FROM t_user r
) u
LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name
  FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u.username AS BINARY)
LEFT JOIN ehr_employee e ON e.empID=l.empID
