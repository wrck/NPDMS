SELECT COALESCE(HEX(CAST(u.username AS BINARY)),CONCAT('INVALID:',u.objectName,':',u.sourceId)) AS businessKey,
 u.username,
 JSON_ARRAYAGG(JSON_OBJECT('sourceTable',u.objectName,'sourceId',u.sourceId,'fields',CAST(u.fields AS JSON))) AS sourceAliases,
 CASE WHEN SUM(CASE WHEN u.objectName='fnd_user_info' AND NOT (u.username='c00733' AND u.sourceId='233') AND NOT (u.username='w02459' AND u.sourceId='118') THEN 1 ELSE 0 END)=1
 THEN CAST(MAX(CASE WHEN u.objectName='fnd_user_info' AND NOT (u.username='c00733' AND u.sourceId='233') AND NOT (u.username='w02459' AND u.sourceId='118') THEN CAST(u.fields AS CHAR) END) AS JSON) ELSE NULL END AS primaryFnd,
 CASE WHEN SUM(CASE WHEN u.objectName='t_user' THEN 1 ELSE 0 END)=1
 THEN CAST(MAX(CASE WHEN u.objectName='t_user' THEN CAST(u.fields AS CHAR) END) AS JSON) ELSE NULL END AS primaryT,
 JSON_ARRAYAGG(CASE WHEN u.objectName='t_user_info' THEN CAST(u.fields AS JSON) ELSE NULL END) AS employmentRows,
 SUM(CASE WHEN u.objectName='fnd_user_info' AND NOT (u.username='c00733' AND u.sourceId='233') AND NOT (u.username='w02459' AND u.sourceId='118') THEN 1 ELSE 0 END) AS primaryFndCount,
 SUM(CASE WHEN u.objectName='t_user' THEN 1 ELSE 0 END) AS primaryTCount,
 JSON_OBJECT('loginCount',MAX(l.loginCount),'employeeId',MAX(l.empID),'workNo',MAX(l.workNo),'name',MAX(l.name),'verifiedEmployeeId',MAX(e.empID),'verifiedWorkNo',MAX(e.workNo),'verifiedName',MAX(e.name)) AS ehrVerification

FROM (
SELECT 't_user' AS objectName,CAST(r.`user_id` AS CHAR) AS sourceId,r.user_name AS username,JSON_OBJECT('user_id',r.`user_id`,'user_name',r.`user_name`,'create_by',r.`create_by`,'create_time',r.`create_time`,'update_by',r.`update_by`,'update_time',r.`update_time`,'status',r.`status`,'needChangePwd',CAST(r.needChangePwd AS UNSIGNED),'loginErrorCount',r.`loginErrorCount`,'isSysUser',r.`isSysUser`,'userCustom1',r.`userCustom1`,'userCustom2',r.`userCustom2`,'userCustom3',r.`userCustom3`,'userCustom4',r.`userCustom4`,'userCustom5',r.`userCustom5`) AS fields FROM t_user r
UNION ALL
SELECT 't_user_info' AS objectName,CAST(r.`id` AS CHAR) AS sourceId,a.user_name AS username,JSON_OBJECT('id',r.`id`,'workNo',r.`workNo`,'realName',r.`realName`,'eName',r.`eName`,'compID',r.`compID`,'depID',r.`depID`,'jobID',r.`jobID`,'reportTo',r.`reportTo`,'wfreportTo',r.`wfreportTo`,'empStatus',r.`empStatus`,'jobStatus',r.`jobStatus`,'empType',r.`empType`,'sex',r.`sex`,'birthday',r.`birthday`,'email',r.`email`,'mobile',r.`mobile`,'telphone',r.`telphone`,'avatar',r.`avatar`,'remark',r.`remark`,'state',r.`state`,'user_id',r.`user_id`,'custom1',r.`custom1`,'custom2',r.`custom2`,'custom3',r.`custom3`,'custom4',r.`custom4`,'custom5',r.`custom5`,'migrationCompanyCode',r.`migrationCompanyCode`,'migrationDepartmentCode',r.`migrationDepartmentCode`,'migrationPositionCode',r.`migrationPositionCode`,'migrationVerifiedEmployeeId',r.`migrationVerifiedEmployeeId`) AS fields FROM (SELECT u.id,u.workNo,u.realName,u.eName,u.compID,u.depID,u.jobID,u.reportTo,u.wfreportTo,u.empStatus,u.jobStatus,u.empType,u.sex,u.birthday,u.email,u.mobile,u.telphone,u.avatar,u.remark,u.state,u.user_id,u.custom1,u.custom2,u.custom3,u.custom4,u.custom5,NULL AS migrationCompanyCode,NULL AS migrationDepartmentCode,j.jobCode AS migrationPositionCode,e.empID AS migrationVerifiedEmployeeId FROM t_user_info u LEFT JOIN ehr_job j ON j.jobID=u.jobID LEFT JOIN t_user a ON a.user_id=u.user_id LEFT JOIN ehr_employee e ON e.empID=a.userCustom4 AND CAST(e.workNo AS BINARY)=CAST(u.workNo AS BINARY) AND u.workNo<>'') r LEFT JOIN t_user a ON a.user_id=r.user_id
UNION ALL
SELECT 'fnd_user_info' AS objectName,CAST(r.`id` AS CHAR) AS sourceId,r.username AS username,JSON_OBJECT('id',r.`id`,'username',r.`username`,'email',r.`email`,'dpNo',r.`dpNo`,'realName',r.`realName`,'roleIds',r.`roleIds`,'isemail',r.`isemail`,'status',r.`status`,'defaultPage',r.`defaultPage`,'pwdoverdue',r.`pwdoverdue`,'createBy',r.`createBy`,'createTime',r.`createTime`,'updateBy',r.`updateBy`,'updateTime',r.`updateTime`,'effectiveFrom',r.`effectiveFrom`,'effectiveTo',r.`effectiveTo`,'customInfo',r.`customInfo`) AS fields FROM fnd_user_info r
) u
LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name
  FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u.username AS BINARY)
LEFT JOIN ehr_employee e ON e.empID=l.empID
GROUP BY COALESCE(HEX(CAST(u.username AS BINARY)),CONCAT('INVALID:',u.objectName,':',u.sourceId))

