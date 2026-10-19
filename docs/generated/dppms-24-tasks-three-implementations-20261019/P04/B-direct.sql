-- P04 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [t_user] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT u.sourceId,
 CAST(JSON_ARRAY(JSON_OBJECT('object','t_user','fields',JSON_MERGE_PRESERVE(CAST(u.fields AS JSON),JSON_OBJECT('migrationLoginCount',l.loginCount,'migrationLoginEmployeeId',l.empID,'migrationLoginWorkNo',l.workNo,'migrationLoginName',l.name,'migrationEmployeeId',e.empID,'migrationEmployeeWorkNo',e.workNo,'migrationEmployeeName',e.name)))) AS CHAR) AS _sourceRows
FROM (
SELECT 't_user' AS objectName,r.`user_id` AS sourceId,r.user_name AS username,JSON_OBJECT('user_id',r.`user_id`,'user_name',r.`user_name`,'create_by',r.`create_by`,'create_time',r.`create_time`,'update_by',r.`update_by`,'update_time',r.`update_time`,'status',r.`status`,'needChangePwd',CAST(r.needChangePwd AS UNSIGNED),'loginErrorCount',r.`loginErrorCount`,'isSysUser',r.`isSysUser`,'userCustom1',r.`userCustom1`,'userCustom2',r.`userCustom2`,'userCustom3',r.`userCustom3`,'userCustom4',r.`userCustom4`,'userCustom5',r.`userCustom5`) AS fields FROM t_user r
) u
LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name
  FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u.username AS BINARY)
LEFT JOIN ehr_employee e ON e.empID=l.empID;

-- [t_user_info] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT u.sourceId,
 CAST(JSON_ARRAY(JSON_OBJECT('object','t_user_info','fields',CAST(u.fields AS JSON))) AS CHAR) AS _sourceRows
FROM (
SELECT 't_user_info' AS objectName,r.`id` AS sourceId,a.user_name AS username,JSON_OBJECT('id',r.`id`,'workNo',r.`workNo`,'realName',r.`realName`,'eName',r.`eName`,'compID',r.`compID`,'depID',r.`depID`,'jobID',r.`jobID`,'reportTo',r.`reportTo`,'wfreportTo',r.`wfreportTo`,'empStatus',r.`empStatus`,'jobStatus',r.`jobStatus`,'empType',r.`empType`,'sex',r.`sex`,'birthday',r.`birthday`,'email',r.`email`,'mobile',r.`mobile`,'telphone',r.`telphone`,'avatar',r.`avatar`,'remark',r.`remark`,'state',r.`state`,'user_id',r.`user_id`,'custom1',r.`custom1`,'custom2',r.`custom2`,'custom3',r.`custom3`,'custom4',r.`custom4`,'custom5',r.`custom5`,'migrationCompanyCode',r.`migrationCompanyCode`,'migrationDepartmentCode',r.`migrationDepartmentCode`,'migrationPositionCode',r.`migrationPositionCode`,'migrationVerifiedEmployeeId',r.`migrationVerifiedEmployeeId`) AS fields FROM (SELECT u.id,u.workNo,u.realName,u.eName,u.compID,u.depID,u.jobID,u.reportTo,u.wfreportTo,u.empStatus,u.jobStatus,u.empType,u.sex,u.birthday,u.email,u.mobile,u.telphone,u.avatar,u.remark,u.state,u.user_id,u.custom1,u.custom2,u.custom3,u.custom4,u.custom5,NULL AS migrationCompanyCode,NULL AS migrationDepartmentCode,j.jobCode AS migrationPositionCode,e.empID AS migrationVerifiedEmployeeId FROM t_user_info u LEFT JOIN ehr_job j ON j.jobID=u.jobID LEFT JOIN t_user a ON a.user_id=u.user_id LEFT JOIN ehr_employee e ON e.empID=a.userCustom4 AND CAST(e.workNo AS BINARY)=CAST(u.workNo AS BINARY) AND u.workNo<>'') r LEFT JOIN t_user a ON a.user_id=r.user_id
) u;

-- [fnd_user_info] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT u.sourceId,
 CAST(JSON_ARRAY(JSON_OBJECT('object','fnd_user_info','fields',JSON_MERGE_PRESERVE(CAST(u.fields AS JSON),JSON_OBJECT('migrationLoginCount',l.loginCount,'migrationLoginEmployeeId',l.empID,'migrationLoginWorkNo',l.workNo,'migrationLoginName',l.name,'migrationEmployeeId',e.empID,'migrationEmployeeWorkNo',e.workNo,'migrationEmployeeName',e.name))),
 JSON_OBJECT('object','FND_USER_ROLE','fields',JSON_MERGE_PRESERVE(CAST(u.fields AS JSON),JSON_OBJECT('migrationLoginCount',l.loginCount,'migrationLoginEmployeeId',l.empID,'migrationLoginWorkNo',l.workNo,'migrationLoginName',l.name,'migrationEmployeeId',e.empID,'migrationEmployeeWorkNo',e.workNo,'migrationEmployeeName',e.name)))) AS CHAR) AS _sourceRows
FROM (
SELECT 'fnd_user_info' AS objectName,r.`id` AS sourceId,r.username AS username,JSON_OBJECT('id',r.`id`,'username',r.`username`,'email',r.`email`,'dpNo',r.`dpNo`,'realName',r.`realName`,'roleIds',r.`roleIds`,'isemail',r.`isemail`,'status',r.`status`,'defaultPage',r.`defaultPage`,'pwdoverdue',r.`pwdoverdue`,'createBy',r.`createBy`,'createTime',r.`createTime`,'updateBy',r.`updateBy`,'updateTime',r.`updateTime`,'effectiveFrom',r.`effectiveFrom`,'effectiveTo',r.`effectiveTo`,'customInfo',r.`customInfo`) AS fields FROM fnd_user_info r
) u
LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name
  FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u.username AS BINARY)
LEFT JOIN ehr_employee e ON e.empID=l.empID;

-- [FND_USER_ROLE] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
-- queryRef=fnd_user_info; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [T_ROLE] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT r.role_id,r.role_name,r.role_name_zn,r.home_page,r.priority,r.remark,r.status,r.create_by,r.create_time,r.update_by,r.update_time,100-r.priority AS migrationSort,COALESCE(r.update_time,r.create_time) AS migrationVersion,NULL AS creatorSourceKey,NULL AS updaterSourceKey FROM t_role r WHERE r.status<>0 OR r.status IS NULL;

-- [FND_ROLE] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT r.id,r.roleName,r.roleRemark,r.defaultPage,r.status,r.effectiveFrom,r.effectiveTo,r.createBy,r.createTime,r.updateBy,r.updateTime,100+r.id AS migrationSort,COALESCE(r.updateTime,r.createTime) AS migrationVersion,NULL AS creatorSourceKey,NULL AS updaterSourceKey FROM fnd_roles r WHERE r.status<>0 OR r.status IS NULL;

-- [T_USER_ROLE] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT r.id AS _groupKey,
 JSON_ARRAY(
  JSON_OBJECT('object','T_USER_ROLE','fields',JSON_OBJECT('id',r.id,'user_id',r.user_id,'role_id',r.role_id,'comp_id',r.comp_id,'username',NULL)),
  JSON_OBJECT('object','t_user_role','fields',JSON_OBJECT('id',r.id,'user_id',r.user_id,'role_id',r.role_id,'comp_id',r.comp_id,'username',NULL,'migrationCompanyCode',NULL))
 ) AS _sourceRows
FROM t_user_role r;

-- [t_user_role] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
-- queryRef=T_USER_ROLE; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [fnd_user_power] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT id,fndUserId,username,areapower,createTime,createBy,updateTime,updateBy,effectiveFrom,effectiveTo FROM fnd_user_power;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper.selectAccountLimit; pms-module-integration/src/main/resources/mapper/sync/DirectAccountAuxiliaryMapper.xml:4; kind=select
SELECT account_count FROM system_tenant WHERE id=#{query.tenantId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper.selectPosts; pms-module-integration/src/main/resources/mapper/sync/DirectAccountAuxiliaryMapper.xml:7; kind=select
SELECT post_id FROM system_user_post WHERE tenant_id=#{query.tenantId} AND user_id=#{query.userId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper.selectAccessTokens; pms-module-integration/src/main/resources/mapper/sync/DirectAccountAuxiliaryMapper.xml:20; kind=select
SELECT access_token FROM system_oauth2_access_token WHERE tenant_id=#{query.tenantId} AND user_id=#{query.userId} AND user_type=2 AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountMembershipMapper.selectRelations; pms-module-integration/src/main/resources/mapper/sync/DirectAccountMembershipMapper.xml:8; kind=select
SELECT id,user_id AS userId,role_id AS roleId FROM system_user_role
        WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountMembershipMapper.selectRelationsForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountMembershipMapper.xml:11; kind=select
SELECT id,user_id AS userId,role_id AS roleId FROM system_user_role
        WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountMembershipMapper.selectUserIds; pms-module-integration/src/main/resources/mapper/sync/DirectAccountMembershipMapper.xml:21; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountMembershipMapper.selectUserIdsForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountMembershipMapper.xml:22; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountMembershipMapper.selectRoles; pms-module-integration/src/main/resources/mapper/sync/DirectAccountMembershipMapper.xml:30; kind=select
SELECT id,code FROM system_role WHERE tenant_id=#{query.tenantId} AND status=0 AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.roleIds != null and !query.roleIds.isEmpty() */ AND id IN  /* FOREACH collection=query.roleIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountMembershipMapper.selectRolesForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountMembershipMapper.xml:31; kind=select
SELECT id,code FROM system_role WHERE tenant_id=#{query.tenantId} AND status=0 AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.roleIds != null and !query.roleIds.isEmpty() */ AND id IN  /* FOREACH collection=query.roleIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRoleMapper.selectListForMigration; pms-module-integration/src/main/resources/mapper/sync/DirectAccountRoleMapper.xml:14; kind=select
SELECT * FROM system_role
        WHERE tenant_id = #{query.tenantId} AND deleted = FALSE
        ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRoleMapper.selectListForMigrationForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountRoleMapper.xml:17; kind=select
SELECT * FROM system_role
        WHERE tenant_id = #{query.tenantId} AND deleted = FALSE
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRoleMapper.selectListHistoricalAuditActorIds; pms-module-integration/src/main/resources/mapper/sync/DirectAccountRoleMapper.xml:20; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN
                 /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectRelations; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:10; kind=select
SELECT id,tenant_id,user_id,company_id,company_code,company_name,department_id,department_code,
               department_name,scope_role,is_primary,effective_from,effective_to,status,version,
               creator,create_time,updater,update_time,deleted
        FROM system_user_company_department_scope WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectRelationsForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:11; kind=select
SELECT id,tenant_id,user_id,company_id,company_code,company_name,department_id,department_code,
               department_name,scope_role,is_primary,effective_from,effective_to,status,version,
               creator,create_time,updater,update_time,deleted
        FROM system_user_company_department_scope WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectUserIds; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:18; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectUserIdsForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:19; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectCompanies; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:26; kind=select
SELECT id,code,name FROM system_company WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.companyIds != null and !query.companyIds.isEmpty() */ AND id IN  /* FOREACH collection=query.companyIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectCompaniesForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:27; kind=select
SELECT id,code,name FROM system_company WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.companyIds != null and !query.companyIds.isEmpty() */ AND id IN  /* FOREACH collection=query.companyIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectDepartments; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:34; kind=select
SELECT id,code,name FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.departmentIds != null and !query.departmentIds.isEmpty() */ AND id IN  /* FOREACH collection=query.departmentIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectDepartmentsForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:35; kind=select
SELECT id,code,name FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.departmentIds != null and !query.departmentIds.isEmpty() */ AND id IN  /* FOREACH collection=query.departmentIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectRoles; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:47; kind=select
SELECT ur.user_id,r.code FROM system_user_role ur
        INNER JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=FALSE AND r.status=0
        WHERE ur.tenant_id=#{query.tenantId} AND ur.deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND ur.user_id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
         /* CHOOSE  */ /* WHEN test=query.roleCodes != null and !query.roleCodes.isEmpty() */ AND r.code IN  /* FOREACH collection=query.roleCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY ur.user_id,r.id,ur.id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.selectRolesForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:48; kind=select
SELECT ur.user_id,r.code FROM system_user_role ur
        INNER JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=FALSE AND r.status=0
        WHERE ur.tenant_id=#{query.tenantId} AND ur.deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND ur.user_id IN  /* FOREACH collection=query.userIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
         /* CHOOSE  */ /* WHEN test=query.roleCodes != null and !query.roleCodes.isEmpty() */ AND r.code IN  /* FOREACH collection=query.roleCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY ur.user_id,r.id,ur.id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountUserMapper.selectProfiles; pms-module-integration/src/main/resources/mapper/sync/DirectAccountUserMapper.xml:14; kind=select
SELECT id,tenant_id,username,nickname,remark,dept_id,post_ids,email,mobile,sex,avatar,status,
               employee_no,custom_info,creator,create_time,updater,update_time
        FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountUserMapper.selectProfilesForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectAccountUserMapper.xml:15; kind=select
SELECT id,tenant_id,username,nickname,remark,dept_id,post_ids,email,mobile,sex,avatar,status,
               employee_no,custom_info,creator,create_time,updater,update_time
        FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountUserMapper.selectDepartmentIds; pms-module-integration/src/main/resources/mapper/sync/DirectAccountUserMapper.xml:25; kind=select
SELECT id FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountUserMapper.selectPostIds; pms-module-integration/src/main/resources/mapper/sync/DirectAccountUserMapper.xml:28; kind=select
SELECT id FROM system_post WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper.insertPost; pms-module-integration/src/main/resources/mapper/sync/DirectAccountAuxiliaryMapper.xml:16; kind=insert
INSERT INTO system_user_post(id,tenant_id,user_id,post_id,creator,create_time,updater,update_time,deleted)
        VALUES(#{write.id},#{write.scope.tenantId},#{write.scope.userId},#{write.postId},#{write.actor},#{write.time},#{write.actor},#{write.time},FALSE);

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountMembershipMapper.insertHistoricalRelation; pms-module-integration/src/main/resources/mapper/sync/DirectAccountMembershipMapper.xml:32; kind=insert
INSERT INTO system_user_role(id,tenant_id,user_id,role_id,creator,create_time,updater,update_time,deleted)
        VALUES(#{write.relation.id},#{write.tenantId},#{write.relation.userId},#{write.relation.roleId},NULL,NULL,NULL,NULL,FALSE);

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRoleMapper.insertHistoricalRole; pms-module-integration/src/main/resources/mapper/sync/DirectAccountRoleMapper.xml:31; kind=insert
INSERT INTO system_role
            (id, tenant_id, name, code, sort, status, type, data_scope, remark,
             creator, create_time, updater, update_time, deleted)
        VALUES (#{write.role.id}, #{write.role.tenantId}, #{write.role.name}, #{write.role.code}, #{write.role.sort},
                #{write.role.status}, #{write.role.type}, #{write.role.dataScope}, #{write.role.remark},
                #{write.audit.creator}, #{write.audit.createTime}, #{write.audit.updater}, #{write.audit.updateTime}, FALSE);

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.insertHistoricalRelation; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:49; kind=insert
INSERT INTO system_user_company_department_scope(id,tenant_id,user_id,company_id,company_code,company_name,
            department_id,department_code,department_name,scope_role,is_primary,effective_from,effective_to,status,
            version,creator,create_time,updater,update_time,deleted)
        VALUES(#{write.relation.id},#{write.relation.tenantId},#{write.relation.userId},#{write.relation.companyId},#{write.relation.companyCode},#{write.relation.companyName},
            #{write.relation.departmentId},#{write.relation.departmentCode},#{write.relation.departmentName},#{write.relation.scopeRole},#{write.relation.isPrimary},
            #{write.relation.effectiveFrom},#{write.relation.effectiveTo},#{write.relation.status},#{write.relation.version},#{write.audit.creator},
            #{write.audit.createTime},#{write.audit.updater},#{write.audit.updateTime},FALSE);

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountUserMapper.insertHistoricalUser; pms-module-integration/src/main/resources/mapper/sync/DirectAccountUserMapper.xml:31; kind=insert
INSERT INTO system_users(id,tenant_id,username,password,nickname,remark,dept_id,post_ids,email,mobile,
            sex,avatar,status,employee_no,custom_info,creator,create_time,updater,update_time,deleted)
        VALUES(#{write.user.id},#{write.user.tenantId},#{write.user.username},#{write.user.password},#{write.user.nickname},#{write.user.remark},
            #{write.user.deptId},#{write.user.postIds,typeHandler=com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler},
            #{write.user.email},#{write.user.mobile},#{write.user.sex},#{write.user.avatar},#{write.user.status},#{write.user.employeeNo},#{write.user.customInfo},
            #{write.audit.creator},#{write.audit.createTime},#{write.audit.updater},#{write.audit.updateTime},FALSE);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper.deletePosts; pms-module-integration/src/main/resources/mapper/sync/DirectAccountAuxiliaryMapper.xml:10; kind=update
UPDATE system_user_post SET deleted=TRUE WHERE tenant_id=#{write.scope.tenantId} AND user_id=#{write.scope.userId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=write.postIds != null and !write.postIds.isEmpty() */ AND post_id IN  /* FOREACH collection=write.postIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRoleMapper.updateHistoricalDefinition; pms-module-integration/src/main/resources/mapper/sync/DirectAccountRoleMapper.xml:39; kind=update
UPDATE system_role SET name=#{write.role.name}, sort=#{write.role.sort}, remark=#{write.role.remark},
            creator=#{write.audit.creator}, create_time=#{write.audit.createTime}, updater=#{write.audit.updater}, update_time=#{write.audit.updateTime}
        WHERE id=#{write.role.id} AND tenant_id=#{write.role.tenantId} AND code=#{write.role.code} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountScopeMapper.updateHistoricalRelation; pms-module-integration/src/main/resources/mapper/sync/DirectAccountScopeMapper.xml:58; kind=update
UPDATE system_user_company_department_scope SET company_name=#{write.relation.companyName},department_name=#{write.relation.departmentName},
            is_primary=#{write.relation.isPrimary},effective_to=#{write.relation.effectiveTo},status=#{write.relation.status},
            creator=#{write.audit.creator},create_time=#{write.audit.createTime},updater=#{write.audit.updater},update_time=#{write.audit.updateTime},version=#{write.relation.version}
        WHERE id=#{write.relation.id} AND tenant_id=#{write.relation.tenantId} AND version=#{write.expectedVersion} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountUserMapper.updateHistoricalProfile; pms-module-integration/src/main/resources/mapper/sync/DirectAccountUserMapper.xml:39; kind=update
UPDATE system_users SET nickname=#{write.user.nickname},remark=#{write.user.remark},dept_id=#{write.user.deptId},
            post_ids=#{write.user.postIds,typeHandler=com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler},
            email=#{write.user.email},mobile=#{write.user.mobile},sex=#{write.user.sex},avatar=#{write.user.avatar},status=#{write.user.status},
            employee_no=#{write.user.employeeNo},custom_info=#{write.user.customInfo},creator=#{write.audit.creator},
            create_time=#{write.audit.createTime},updater=#{write.audit.updater},update_time=#{write.audit.updateTime}
        WHERE id=#{write.user.id} AND tenant_id=#{write.user.tenantId} AND username=#{write.user.username} AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
