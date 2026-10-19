-- P04 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [t_user] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json
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
GROUP BY COALESCE(HEX(CAST(u.username AS BINARY)),CONCAT('INVALID:',u.objectName,':',u.sourceId));

-- [t_user_info] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json
-- queryRef=t_user; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [fnd_user_info] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json
-- queryRef=t_user; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [FND_USER_ROLE] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json
-- queryRef=t_user; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [T_ROLE] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json
SELECT code,HEX(CAST(code AS BINARY)) AS businessKey,
 CASE WHEN SUM(CASE WHEN referenceOnly=0 THEN 1 ELSE 0 END)>0 THEN 0 ELSE 1 END AS referenceOnly,
 CASE WHEN COUNT(DISTINCT CASE WHEN referenceOnly=0 THEN CAST(definitionFields AS CHAR) END)=1
 THEN CAST(MIN(CASE WHEN referenceOnly=0 THEN CAST(definitionFields AS CHAR) END) AS JSON) ELSE NULL END AS canonicalFields,
 COUNT(DISTINCT CASE WHEN referenceOnly=0 THEN CAST(definitionFields AS CHAR) END) AS definitionVariants,
 JSON_ARRAYAGG(sourceAlias) AS sourceAliases
FROM (
SELECT CASE CAST(r.role_id AS CHAR) WHEN '1' THEN 'super_admin' WHEN '2' THEN 'user' WHEN '5' THEN 'projectMember' WHEN '6' THEN 'projectAdmin' WHEN '7' THEN 'PROJECT_MANAGER' WHEN '8' THEN 'projectAFQC' WHEN '9' THEN 'projectYFQC' WHEN '10' THEN 'projectSubAdmin' WHEN '11' THEN 'projectAreaManager' WHEN '12' THEN 'projectPreSales' WHEN '13' THEN 'userManager' WHEN '14' THEN 'financialAP' WHEN '15' THEN 'dataViewer' WHEN '16' THEN 'dispatchSettleStaff' ELSE NULL END AS code,(CASE CAST(r.role_id AS CHAR) WHEN '1' THEN 'admin' WHEN '2' THEN 'user' WHEN '5' THEN 'projectMember' WHEN '6' THEN 'projectAdmin' WHEN '7' THEN 'projectManager' WHEN '8' THEN 'projectAFQC' WHEN '9' THEN 'projectYFQC' WHEN '10' THEN 'projectSubAdmin' WHEN '11' THEN 'projectAreaManager' WHEN '12' THEN 'projectPreSales' WHEN '13' THEN 'userManager' WHEN '14' THEN 'financialAP' WHEN '15' THEN 'dataViewer' WHEN '16' THEN 'dispatchSettleStaff' ELSE NULL END) IN ('admin','projectManager') AS referenceOnly,JSON_OBJECT('code',CASE CAST(r.role_id AS CHAR) WHEN '1' THEN 'super_admin' WHEN '2' THEN 'user' WHEN '5' THEN 'projectMember' WHEN '6' THEN 'projectAdmin' WHEN '7' THEN 'PROJECT_MANAGER' WHEN '8' THEN 'projectAFQC' WHEN '9' THEN 'projectYFQC' WHEN '10' THEN 'projectSubAdmin' WHEN '11' THEN 'projectAreaManager' WHEN '12' THEN 'projectPreSales' WHEN '13' THEN 'userManager' WHEN '14' THEN 'financialAP' WHEN '15' THEN 'dataViewer' WHEN '16' THEN 'dispatchSettleStaff' ELSE NULL END,'name',r.role_name_zn,'originalRoleName',r.role_name,'remark',r.remark,'sort',100-r.priority,'rawStatus',r.status,'creatorAccount',r.create_by,'createTime',r.create_time,'updaterAccount',r.update_by,'updateTime',r.update_time) AS definitionFields,JSON_OBJECT('table','t_role','id',r.role_id,'originalRoleName',r.role_name,'originalRoleNameZh',r.role_name_zn,'rawStatus',r.status,'sourceCode',CASE CAST(r.role_id AS CHAR) WHEN '1' THEN 'admin' WHEN '2' THEN 'user' WHEN '5' THEN 'projectMember' WHEN '6' THEN 'projectAdmin' WHEN '7' THEN 'projectManager' WHEN '8' THEN 'projectAFQC' WHEN '9' THEN 'projectYFQC' WHEN '10' THEN 'projectSubAdmin' WHEN '11' THEN 'projectAreaManager' WHEN '12' THEN 'projectPreSales' WHEN '13' THEN 'userManager' WHEN '14' THEN 'financialAP' WHEN '15' THEN 'dataViewer' WHEN '16' THEN 'dispatchSettleStaff' ELSE NULL END) AS sourceAlias FROM t_role r WHERE r.status<>0 OR r.status IS NULL
UNION ALL
SELECT CASE CAST(r.id AS CHAR) WHEN '1' THEN 'admin' WHEN '3' THEN 'user' WHEN '5' THEN 'projectAdmin' WHEN '6' THEN 'projectViewer' WHEN '9' THEN 'areaLeader' WHEN '10' THEN 'engineeManagerLeader' WHEN '11' THEN 'SERVICE_MANAGER' WHEN '12' THEN 'PROJECT_MANAGER' WHEN '13' THEN 'engineeManager' WHEN '14' THEN 'callbackPer' WHEN '15' THEN 'salesPeople' WHEN '16' THEN 'financialStaff' WHEN '17' THEN 'presalesStaff' WHEN '18' THEN 'probAdmin' WHEN '19' THEN 'probSupporter' WHEN '20' THEN 'probRD' WHEN '21' THEN 'warrantyCallbacker' WHEN '22' THEN 'componentAdmin' ELSE NULL END AS code,(CASE CAST(r.id AS CHAR) WHEN '1' THEN 'admin' WHEN '3' THEN 'common' WHEN '5' THEN 'projectAdmin' WHEN '6' THEN 'projectViewer' WHEN '9' THEN 'areaLeader' WHEN '10' THEN 'engineeManagerLeader' WHEN '11' THEN 'serviceManager' WHEN '12' THEN 'programManager' WHEN '13' THEN 'engineeManager' WHEN '14' THEN 'callbackPer' WHEN '15' THEN 'salesPeople' WHEN '16' THEN 'financialStaff' WHEN '17' THEN 'presalesStaff' WHEN '18' THEN 'probAdmin' WHEN '19' THEN 'probSupporter' WHEN '20' THEN 'probRD' WHEN '21' THEN 'warrantyCallbacker' WHEN '22' THEN 'componentAdmin' ELSE NULL END) IN ('common','projectAdmin','serviceManager','programManager') AS referenceOnly,JSON_OBJECT('code',CASE CAST(r.id AS CHAR) WHEN '1' THEN 'admin' WHEN '3' THEN 'user' WHEN '5' THEN 'projectAdmin' WHEN '6' THEN 'projectViewer' WHEN '9' THEN 'areaLeader' WHEN '10' THEN 'engineeManagerLeader' WHEN '11' THEN 'SERVICE_MANAGER' WHEN '12' THEN 'PROJECT_MANAGER' WHEN '13' THEN 'engineeManager' WHEN '14' THEN 'callbackPer' WHEN '15' THEN 'salesPeople' WHEN '16' THEN 'financialStaff' WHEN '17' THEN 'presalesStaff' WHEN '18' THEN 'probAdmin' WHEN '19' THEN 'probSupporter' WHEN '20' THEN 'probRD' WHEN '21' THEN 'warrantyCallbacker' WHEN '22' THEN 'componentAdmin' ELSE NULL END,'name',r.roleName,'remark',r.roleRemark,'sort',100+r.id,'rawStatus',r.status,'creatorAccount',r.createBy,'createTime',r.createTime,'updaterAccount',r.updateBy,'updateTime',r.updateTime) AS definitionFields,JSON_OBJECT('table','fnd_roles','id',r.id,'rawStatus',r.status,'sourceCode',CASE CAST(r.id AS CHAR) WHEN '1' THEN 'admin' WHEN '3' THEN 'common' WHEN '5' THEN 'projectAdmin' WHEN '6' THEN 'projectViewer' WHEN '9' THEN 'areaLeader' WHEN '10' THEN 'engineeManagerLeader' WHEN '11' THEN 'serviceManager' WHEN '12' THEN 'programManager' WHEN '13' THEN 'engineeManager' WHEN '14' THEN 'callbackPer' WHEN '15' THEN 'salesPeople' WHEN '16' THEN 'financialStaff' WHEN '17' THEN 'presalesStaff' WHEN '18' THEN 'probAdmin' WHEN '19' THEN 'probSupporter' WHEN '20' THEN 'probRD' WHEN '21' THEN 'warrantyCallbacker' WHEN '22' THEN 'componentAdmin' ELSE NULL END) AS sourceAlias FROM fnd_roles r WHERE r.status<>0 OR r.status IS NULL
) source_roles
GROUP BY code,CAST(code AS BINARY);

-- [FND_ROLE] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json
-- queryRef=T_ROLE; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [T_USER_ROLE] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json
SELECT r.id AS _groupKey,
 JSON_ARRAY(
  JSON_OBJECT('object','T_USER_ROLE','fields',JSON_OBJECT('id',r.id,'user_id',r.user_id,'role_id',r.role_id,'comp_id',r.comp_id,'username',NULL)),
  JSON_OBJECT('object','t_user_role','fields',JSON_OBJECT('id',r.id,'user_id',r.user_id,'role_id',r.role_id,'comp_id',r.comp_id,'username',NULL,'migrationCompanyCode',NULL))
 ) AS _sourceRows
FROM t_user_role r;

-- [t_user_role] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json
-- queryRef=T_USER_ROLE; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [fnd_user_power] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P04.json
SELECT id,fndUserId,username,areapower,createTime,createBy,updateTime,updateBy,effectiveFrom,effectiveTo FROM fnd_user_power;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectInstallationSelectionPage; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:4; kind=select
SELECT * FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId} AND deleted=FALSE
      AND object_key='INSTALLATION_HISTORY' AND source_object='pm_project_shipment'
     /* CHOOSE  */ /* WHEN test=query.deviceSns != null and !query.deviceSns.isEmpty() */ AND JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$.originalFields.barcode')) IN
       /* FOREACH collection=query.deviceSns; item=sn; open=(; separator=,; close=) */ (#{sn}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
     /* IF test=query.afterId != null */ AND id > #{query.afterId} /* END IF */ 
    ORDER BY id LIMIT 500;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectRetiredSourceKeysForUpdate; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:23; kind=select
SELECT * FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId}
      AND object_key=#{query.object} AND source_object=#{query.sourceObject} AND deleted=TRUE
     /* CHOOSE  */ /* WHEN test=query.sourceKeys != null and !query.sourceKeys.isEmpty() */ AND source_key IN  /* FOREACH collection=query.sourceKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectIdentitySources; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:31; kind=select
SELECT b.* FROM int_sync_binding b
    JOIN int_sync_task t ON t.id=b.task_id AND t.tenant_id=b.tenant_id AND t.deleted=FALSE
    WHERE b.tenant_id=#{query.tenantId} AND b.deleted=FALSE
      AND t.source_system=#{query.sourceSystem} AND t.connection_id=#{query.connectionId}
      AND (t.adapter=#{query.adapter}
         /* IF test=query.originalOnly and query.adapter == 'DPPMS_USERS' */ OR (t.adapter='DPPMS_ACCOUNT_FOUNDATION' AND b.object_key IN ('t_user','t_user_info','fnd_user_info') AND b.source_key NOT LIKE '%:%') /* END IF */ 
         /* IF test=!query.originalOnly */ /* CHOOSE  */ /* WHEN test=query.adapter == 'DPPMS_DEVICE_BOOTSTRAP' */ OR (t.adapter='DPPMS_DEVICE_BOOTSTRAP_DIRECT' AND b.object_key='DEVICE_BASE' AND b.source_object='ast_device_shipment') /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_PROJECT_MASTER' */ OR (t.adapter='DPPMS_PROJECT_MASTER_DIRECT' AND b.object_key IN ('pm_project_header','pm_project_identity')) /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_ORGANIZATION' */ OR (t.adapter='DPPMS_ORGANIZATION_DIRECT' AND b.object_key IN ('COMPANY','DEPARTMENT','fnd_department','ehr_department','COMPANY_REFERENCE')) /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_USERS' */ OR (t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT') AND b.object_key IN ('t_user','t_user_info','fnd_user_info')
                AND b.source_key NOT LIKE '%:%') /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_ROLES' */ OR (t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT') AND b.object_key IN ('T_ROLE','FND_ROLE')) /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_USER_ROLES' */ OR (t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT') AND b.object_key IN ('T_USER_ROLE','FND_USER_ROLE')) /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_USER_SCOPES' */ OR (t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT') AND b.object_key IN ('t_user_info','fnd_user_info','fnd_user_power')
                AND b.source_key LIKE '%:%') /* END WHEN */ /* END CHOOSE */ /* END IF */ 
      ) AND b.source_object=#{query.sourceObject}
     /* CHOOSE  */ /* WHEN test=query.sourceKeys != null and !query.sourceKeys.isEmpty() */ AND b.source_key IN
         /* FOREACH collection=query.sourceKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
       /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY b.id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectEhrEmployeeUsers; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:76; kind=select
SELECT b.* FROM int_sync_binding b
    JOIN int_sync_task t ON t.id=b.task_id AND t.tenant_id=b.tenant_id AND t.deleted=FALSE
    WHERE b.tenant_id=#{query.tenantId} AND b.deleted=FALSE AND t.source_system='DPPMS'
      AND t.connection_id=#{query.connectionId} AND t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT')
       /* IF test=query.originalOnly */ AND t.adapter='DPPMS_ACCOUNT_FOUNDATION' /* END IF */ 
      AND b.object_key IN ('t_user','fnd_user_info') AND b.object_key=b.source_object
      AND b.source_key NOT LIKE '%:%'
     /* CHOOSE  */ /* WHEN test=query.sourceEmployeeIds != null and !query.sourceEmployeeIds.isEmpty() */ AND JSON_UNQUOTE(JSON_EXTRACT(b.fields_json,'$._verifiedEhrEmployeeId')) IN
       /* FOREACH collection=query.sourceEmployeeIds; item=employee; open=(; separator=,; close=) */ (#{employee}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY b.id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectFirstTaskBindingId; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:90; kind=select
SELECT id FROM int_sync_binding
    WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND deleted=FALSE
    LIMIT 1;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectObsoleteOrderLinesForUpdate; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:112; kind=select
SELECT * FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId}
      AND object_key='LINE' AND source_object='pm_order_line_from_erp' AND deleted=FALSE
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$.companyCode')) AS BINARY)=CAST('01' AS BINARY)
     /* CHOOSE  */ /* WHEN test=query.sourceKeys != null and !query.sourceKeys.isEmpty() */ AND source_key IN  /* FOREACH collection=query.sourceKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper.selectCatalogue; yudao-module-system/src/main/resources/mapper/dept/DepartmentMigrationMapper.xml:8; kind=select
SELECT id,code,name,parent_id,sort,leader_user_id,phone,email,status,version,creator,create_time,updater,update_time
        FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper.selectCatalogueForUpdate; yudao-module-system/src/main/resources/mapper/dept/DepartmentMigrationMapper.xml:9; kind=select
SELECT id,code,name,parent_id,sort,leader_user_id,phone,email,status,version,creator,create_time,updater,update_time
        FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper.selectLeaderUserIds; yudao-module-system/src/main/resources/mapper/dept/DepartmentMigrationMapper.xml:10; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMigrationMapper.selectListForMigration; yudao-module-system/src/main/resources/mapper/permission/RoleMigrationMapper.xml:14; kind=select
SELECT * FROM system_role
        WHERE tenant_id = #{query.tenantId} AND deleted = FALSE
        ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMigrationMapper.selectListForMigrationForUpdate; yudao-module-system/src/main/resources/mapper/permission/RoleMigrationMapper.xml:17; kind=select
SELECT * FROM system_role
        WHERE tenant_id = #{query.tenantId} AND deleted = FALSE
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMigrationMapper.selectListHistoricalAuditActorIds; yudao-module-system/src/main/resources/mapper/permission/RoleMigrationMapper.xml:20; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN
                 /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMigrationMapper.selectRelations; yudao-module-system/src/main/resources/mapper/permission/UserRoleMigrationMapper.xml:8; kind=select
SELECT id,user_id AS userId,role_id AS roleId FROM system_user_role
        WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMigrationMapper.selectRelationsForUpdate; yudao-module-system/src/main/resources/mapper/permission/UserRoleMigrationMapper.xml:11; kind=select
SELECT id,user_id AS userId,role_id AS roleId FROM system_user_role
        WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMigrationMapper.selectUserIds; yudao-module-system/src/main/resources/mapper/permission/UserRoleMigrationMapper.xml:21; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMigrationMapper.selectUserIdsForUpdate; yudao-module-system/src/main/resources/mapper/permission/UserRoleMigrationMapper.xml:22; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMigrationMapper.selectRoles; yudao-module-system/src/main/resources/mapper/permission/UserRoleMigrationMapper.xml:30; kind=select
SELECT id,code FROM system_role WHERE tenant_id=#{query.tenantId} AND status=0 AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.roleIds != null and !query.roleIds.isEmpty() */ AND id IN  /* FOREACH collection=query.roleIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMigrationMapper.selectRolesForUpdate; yudao-module-system/src/main/resources/mapper/permission/UserRoleMigrationMapper.xml:31; kind=select
SELECT id,code FROM system_role WHERE tenant_id=#{query.tenantId} AND status=0 AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.roleIds != null and !query.roleIds.isEmpty() */ AND id IN  /* FOREACH collection=query.roleIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectRelations; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:10; kind=select
SELECT id,tenant_id,user_id,company_id,company_code,company_name,department_id,department_code,
               department_name,scope_role,is_primary,effective_from,effective_to,status,version,
               creator,create_time,updater,update_time,deleted
        FROM system_user_company_department_scope WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectRelationsForUpdate; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:11; kind=select
SELECT id,tenant_id,user_id,company_id,company_code,company_name,department_id,department_code,
               department_name,scope_role,is_primary,effective_from,effective_to,status,version,
               creator,create_time,updater,update_time,deleted
        FROM system_user_company_department_scope WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectUserIds; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:18; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectUserIdsForUpdate; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:19; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectCompanies; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:26; kind=select
SELECT id,code,name FROM system_company WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.companyIds != null and !query.companyIds.isEmpty() */ AND id IN  /* FOREACH collection=query.companyIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectCompaniesForUpdate; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:27; kind=select
SELECT id,code,name FROM system_company WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.companyIds != null and !query.companyIds.isEmpty() */ AND id IN  /* FOREACH collection=query.companyIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectDepartments; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:34; kind=select
SELECT id,code,name FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.departmentIds != null and !query.departmentIds.isEmpty() */ AND id IN  /* FOREACH collection=query.departmentIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectDepartmentsForUpdate; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:35; kind=select
SELECT id,code,name FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.departmentIds != null and !query.departmentIds.isEmpty() */ AND id IN  /* FOREACH collection=query.departmentIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectRoles; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:47; kind=select
SELECT ur.user_id,r.code FROM system_user_role ur
        INNER JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=FALSE AND r.status=0
        WHERE ur.tenant_id=#{query.tenantId} AND ur.deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND ur.user_id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
         /* CHOOSE  */ /* WHEN test=query.roleCodes != null and !query.roleCodes.isEmpty() */ AND r.code IN  /* FOREACH collection=query.roleCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY ur.user_id,r.id,ur.id;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.selectRolesForUpdate; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:48; kind=select
SELECT ur.user_id,r.code FROM system_user_role ur
        INNER JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=FALSE AND r.status=0
        WHERE ur.tenant_id=#{query.tenantId} AND ur.deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND ur.user_id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
         /* CHOOSE  */ /* WHEN test=query.roleCodes != null and !query.roleCodes.isEmpty() */ AND r.code IN  /* FOREACH collection=query.roleCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY ur.user_id,r.id,ur.id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.selectProfiles; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:14; kind=select
SELECT id,tenant_id,username,nickname,remark,dept_id,post_ids,email,mobile,sex,avatar,status,
               employee_no,custom_info,creator,create_time,updater,update_time
        FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.selectProfilesForUpdate; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:15; kind=select
SELECT id,tenant_id,username,nickname,remark,dept_id,post_ids,email,mobile,sex,avatar,status,
               employee_no,custom_info,creator,create_time,updater,update_time
        FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.selectDepartmentIds; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:25; kind=select
SELECT id FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.selectPostIds; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:28; kind=select
SELECT id FROM system_post WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.selectHistoricalMemberIdentities; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:31; kind=select
SELECT id,username,employee_no FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.usernames != null and !query.usernames.isEmpty() */ AND username IN  /* FOREACH collection=query.usernames; item=username; open=(; separator=,; close=) */ (#{username}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.selectHistoricalIdentities; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:41; kind=select
SELECT id,username FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.selectHistoricalIdentitiesByUsername; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:44; kind=select
SELECT id,username FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.usernames != null and !query.usernames.isEmpty() */ AND username IN  /* FOREACH collection=query.usernames; item=username; open=(; separator=,; close=) */ (#{username}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMigrationMapper.insertHistoricalRole; yudao-module-system/src/main/resources/mapper/permission/RoleMigrationMapper.xml:31; kind=insert
INSERT INTO system_role
            (id, tenant_id, name, code, sort, status, type, data_scope, remark,
             creator, create_time, updater, update_time, deleted)
        VALUES (#{role.id}, #{role.tenantId}, #{role.name}, #{role.code}, #{role.sort},
                #{role.status}, #{role.type}, #{role.dataScope}, #{role.remark},
                #{audit.creator}, #{audit.createTime}, #{audit.updater}, #{audit.updateTime}, FALSE);

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMigrationMapper.insertHistoricalRelation; yudao-module-system/src/main/resources/mapper/permission/UserRoleMigrationMapper.xml:32; kind=insert
INSERT INTO system_user_role(id,tenant_id,user_id,role_id,creator,create_time,updater,update_time,deleted)
        VALUES(#{relation.id},#{tenantId},#{relation.userId},#{relation.roleId},NULL,NULL,NULL,NULL,FALSE);

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.insertHistoricalRelation; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:49; kind=insert
INSERT INTO system_user_company_department_scope(id,tenant_id,user_id,company_id,company_code,company_name,
            department_id,department_code,department_name,scope_role,is_primary,effective_from,effective_to,status,
            version,creator,create_time,updater,update_time,deleted)
        VALUES(#{relation.id},#{relation.tenantId},#{relation.userId},#{relation.companyId},#{relation.companyCode},#{relation.companyName},
            #{relation.departmentId},#{relation.departmentCode},#{relation.departmentName},#{relation.scopeRole},#{relation.isPrimary},
            #{relation.effectiveFrom},#{relation.effectiveTo},#{relation.status},#{relation.version},#{audit.creator},
            #{audit.createTime},#{audit.updater},#{audit.updateTime},FALSE);

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.insertHistoricalUser; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:54; kind=insert
INSERT INTO system_users(id,tenant_id,username,password,nickname,remark,dept_id,post_ids,email,mobile,
            sex,avatar,status,employee_no,custom_info,creator,create_time,updater,update_time,deleted)
        VALUES(#{user.id},#{user.tenantId},#{user.username},#{user.password},#{user.nickname},#{user.remark},
            #{user.deptId},#{user.postIds,typeHandler=com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler},
            #{user.email},#{user.mobile},#{user.sex},#{user.avatar},#{user.status},#{user.employeeNo},#{user.customInfo},
            #{audit.creator},#{audit.createTime},#{audit.updater},#{audit.updateTime},FALSE);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMigrationMapper.updateHistoricalDefinition; yudao-module-system/src/main/resources/mapper/permission/RoleMigrationMapper.xml:39; kind=update
UPDATE system_role SET name=#{role.name}, sort=#{role.sort}, remark=#{role.remark},
            creator=#{audit.creator}, create_time=#{audit.createTime}, updater=#{audit.updater}, update_time=#{audit.updateTime}
        WHERE id=#{role.id} AND tenant_id=#{role.tenantId} AND code=#{role.code} AND deleted=FALSE;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMigrationMapper.restoreHistoricalAudit; yudao-module-system/src/main/resources/mapper/permission/RoleMigrationMapper.xml:44; kind=update
UPDATE system_role
        SET creator=#{correction.restoredCreator}, updater=#{correction.restoredUpdater}, update_time=#{correction.updateTime}
        WHERE tenant_id=#{correction.tenantId} AND id=#{correction.roleId} AND code=#{correction.code}
          AND update_time=#{correction.updateTime} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=correction.expectedCreator != null */ AND creator=#{correction.expectedCreator} /* END WHEN */ 
             /* OTHERWISE  */ AND creator IS NULL /* END OTHERWISE */ /* END CHOOSE */ 
         /* CHOOSE  */ /* WHEN test=correction.expectedUpdater != null */ AND updater=#{correction.expectedUpdater} /* END WHEN */ 
             /* OTHERWISE  */ AND updater IS NULL /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper.updateHistoricalRelation; yudao-module-system/src/main/resources/mapper/permission/UserScopeMigrationMapper.xml:58; kind=update
UPDATE system_user_company_department_scope SET company_name=#{relation.companyName},department_name=#{relation.departmentName},
            is_primary=#{relation.isPrimary},effective_to=#{relation.effectiveTo},status=#{relation.status},
            creator=#{audit.creator},create_time=#{audit.createTime},updater=#{audit.updater},update_time=#{audit.updateTime},version=#{relation.version}
        WHERE id=#{relation.id} AND tenant_id=#{relation.tenantId} AND version=#{expectedVersion} AND deleted=FALSE;

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.updateHistoricalProfile; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:62; kind=update
UPDATE system_users SET nickname=#{user.nickname},remark=#{user.remark},dept_id=#{user.deptId},
            post_ids=#{user.postIds,typeHandler=com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler},
            email=#{user.email},mobile=#{user.mobile},sex=#{user.sex},avatar=#{user.avatar},status=#{user.status},
            employee_no=#{user.employeeNo},custom_info=#{user.customInfo},creator=#{audit.creator},
            create_time=#{audit.createTime},updater=#{audit.updater},update_time=#{audit.updateTime}
        WHERE id=#{user.id} AND tenant_id=#{user.tenantId} AND username=#{user.username} AND deleted=FALSE;

-- cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper.restoreNullHistoricalUpdater; yudao-module-system/src/main/resources/mapper/user/UserMigrationMapper.xml:70; kind=update
UPDATE system_users SET updater=NULL,update_time=#{correction.restoredUpdateTime}
        WHERE id=#{correction.userId} AND tenant_id=#{correction.tenantId}
          AND username=#{correction.username} AND update_time=#{correction.expectedUpdateTime} AND deleted=FALSE
           /* CHOOSE  */ /* WHEN test=correction.expectedUpdater != null */ AND updater=#{correction.expectedUpdater} /* END WHEN */ 
               /* OTHERWISE  */ AND updater IS NULL /* END OTHERWISE */ /* END CHOOSE */;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
