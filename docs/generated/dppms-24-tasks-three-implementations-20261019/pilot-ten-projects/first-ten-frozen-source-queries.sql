-- 最初十项目及共享前置的选定冻结版本来源SQL
-- 历史定义摘录；2026-10-19未执行SQL、预览、导入或任何后处理。
-- 参数仅描述，不包含LOOKUP原值、前值明细、凭据或许可原文；参数SQL不能整体直接执行。

-- H001/t_user task=2108524822998773762 version=0 adapter=DPPMS_USERS
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-users-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT u.user_id,u.user_name,u.create_by,u.create_time,u.update_by,u.update_time,u.status,u.needChangePwd,u.loginErrorCount,u.isSysUser,u.userCustom1,u.userCustom2,u.userCustom3,u.userCustom4,u.userCustom5,l.loginCount AS migrationLoginCount,l.empID AS migrationLoginEmployeeId,l.workNo AS migrationLoginWorkNo,l.name AS migrationLoginName,e.empID AS migrationEmployeeId,e.workNo AS migrationEmployeeWorkNo,e.name AS migrationEmployeeName FROM t_user u LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u.user_name AS BINARY) LEFT JOIN ehr_employee e ON e.empID=l.empID;

-- H001/t_user_info task=2108524822998773762 version=0 adapter=DPPMS_USERS
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-users-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT u.id,u.workNo,u.realName,u.eName,u.compID,u.depID,u.jobID,u.reportTo,u.wfreportTo,u.empStatus,u.jobStatus,u.empType,u.sex,u.birthday,u.email,u.mobile,u.telphone,u.avatar,u.remark,u.state,u.user_id,u.custom1,u.custom2,u.custom3,u.custom4,u.custom5,c.compCode AS migrationCompanyCode,d.depCode AS migrationDepartmentCode,j.jobCode AS migrationPositionCode,e.empID AS migrationVerifiedEmployeeId FROM t_user_info u LEFT JOIN t_company c ON c.id=u.compID LEFT JOIN ehr_department d ON d.depID=u.depID LEFT JOIN ehr_job j ON j.jobID=u.jobID LEFT JOIN t_user a ON a.user_id=u.user_id LEFT JOIN ehr_employee e ON e.empID=a.userCustom4 AND CAST(e.workNo AS BINARY)=CAST(u.workNo AS BINARY) AND u.workNo<>'';

-- H001/fnd_user_info task=2108524822998773762 version=0 adapter=DPPMS_USERS
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-users-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT u.id,u.username,u.email,u.dpNo,u.realName,u.roleIds,u.isemail,u.status,u.defaultPage,u.pwdoverdue,u.createBy,u.createTime,u.updateBy,u.updateTime,u.effectiveFrom,u.effectiveTo,u.customInfo,l.loginCount AS migrationLoginCount,l.empID AS migrationLoginEmployeeId,l.workNo AS migrationLoginWorkNo,l.name AS migrationLoginName,e.empID AS migrationEmployeeId,e.workNo AS migrationEmployeeWorkNo,e.name AS migrationEmployeeName FROM fnd_user_info u LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u.username AS BINARY) LEFT JOIN ehr_employee e ON e.empID=l.empID;

-- H002/T_ROLE task=2108554583850524673 version=0 adapter=DPPMS_ROLES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-roles-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT r.*, 100-r.priority AS migrationSort, COALESCE(r.update_time,r.create_time) AS migrationVersion, (SELECT CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END FROM t_user u WHERE u.user_name=r.create_by) AS creatorSourceKey, (SELECT CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END FROM t_user u WHERE u.user_name=r.update_by) AS updaterSourceKey FROM t_role r WHERE r.status<>0 OR r.status IS NULL;

-- H002/FND_ROLE task=2108554583850524673 version=0 adapter=DPPMS_ROLES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-roles-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT r.*, 100+r.id AS migrationSort, COALESCE(r.updateTime,r.createTime) AS migrationVersion, (SELECT CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END FROM fnd_user_info u WHERE u.username=r.createBy AND NOT (u.username='c00733' AND u.id=233) AND NOT (u.username='w02459' AND u.id=118)) AS creatorSourceKey, (SELECT CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END FROM fnd_user_info u WHERE u.username=r.updateBy AND NOT (u.username='c00733' AND u.id=233) AND NOT (u.username='w02459' AND u.id=118)) AS updaterSourceKey FROM fnd_roles r WHERE r.status<>0 OR r.status IS NULL;

-- H003/T_USER_ROLE task=2108568095503093761 version=0 adapter=DPPMS_USER_ROLES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-user-roles-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT r.id,r.user_id,r.role_id,r.comp_id,u.user_name AS username FROM t_user_role r LEFT JOIN t_user u ON u.user_id=r.user_id;

-- H003/FND_USER_ROLE task=2108568095503093761 version=0 adapter=DPPMS_USER_ROLES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-user-roles-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT id,username,email,dpNo,realName,roleIds,isemail,status,defaultPage,pwdoverdue,createBy,createTime,updateBy,updateTime,effectiveFrom,effectiveTo,customInfo FROM fnd_user_info;

-- H004/t_user task=2108569376783273985 version=0 adapter=DPPMS_USER_SCOPES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-user-scopes-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT u.user_id,u.user_name,u.create_by,u.create_time,u.update_by,u.update_time,u.status,u.needChangePwd,u.loginErrorCount,u.isSysUser,u.userCustom1,u.userCustom2,u.userCustom3,u.userCustom4,u.userCustom5,l.loginCount AS migrationLoginCount,l.empID AS migrationLoginEmployeeId,l.workNo AS migrationLoginWorkNo,l.name AS migrationLoginName,e.empID AS migrationEmployeeId,e.workNo AS migrationEmployeeWorkNo,e.name AS migrationEmployeeName FROM t_user u LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u.user_name AS BINARY) LEFT JOIN ehr_employee e ON e.empID=l.empID;

-- H004/t_user_info task=2108569376783273985 version=0 adapter=DPPMS_USER_SCOPES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-user-scopes-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT u.id,u.workNo,u.realName,u.eName,u.compID,u.depID,u.jobID,u.reportTo,u.wfreportTo,u.empStatus,u.jobStatus,u.empType,u.sex,u.birthday,u.email,u.mobile,u.telphone,u.avatar,u.remark,u.state,u.user_id,u.custom1,u.custom2,u.custom3,u.custom4,u.custom5,c.compCode AS migrationCompanyCode,d.depCode AS migrationDepartmentCode,j.jobCode AS migrationPositionCode,e.empID AS migrationVerifiedEmployeeId FROM t_user_info u LEFT JOIN t_company c ON c.id=u.compID LEFT JOIN ehr_department d ON d.depID=u.depID LEFT JOIN ehr_job j ON j.jobID=u.jobID LEFT JOIN t_user a ON a.user_id=u.user_id LEFT JOIN ehr_employee e ON e.empID=a.userCustom4 AND CAST(e.workNo AS BINARY)=CAST(u.workNo AS BINARY) AND u.workNo<>'';

-- H004/fnd_user_info task=2108569376783273985 version=0 adapter=DPPMS_USER_SCOPES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-user-scopes-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT u.id,u.username,u.email,u.dpNo,u.realName,u.roleIds,u.isemail,u.status,u.defaultPage,u.pwdoverdue,u.createBy,u.createTime,u.updateBy,u.updateTime,u.effectiveFrom,u.effectiveTo,u.customInfo,l.loginCount AS migrationLoginCount,l.empID AS migrationLoginEmployeeId,l.workNo AS migrationLoginWorkNo,l.name AS migrationLoginName,e.empID AS migrationEmployeeId,e.workNo AS migrationEmployeeWorkNo,e.name AS migrationEmployeeName FROM fnd_user_info u LEFT JOIN (SELECT account,COUNT(*) AS loginCount,MIN(empID) AS empID,MIN(workNo) AS workNo,MIN(name) AS name FROM ehr_login GROUP BY account) l ON CAST(l.account AS BINARY)=CAST(u.username AS BINARY) LEFT JOIN ehr_employee e ON e.empID=l.empID;

-- H004/t_user_role task=2108569376783273985 version=0 adapter=DPPMS_USER_SCOPES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-user-scopes-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT r.id,r.user_id,r.role_id,r.comp_id,u.user_name AS username,c.compCode AS migrationCompanyCode FROM t_user_role r LEFT JOIN t_user u ON u.user_id=r.user_id LEFT JOIN t_company c ON c.id=r.comp_id;

-- H004/fnd_user_power task=2108569376783273985 version=0 adapter=DPPMS_USER_SCOPES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-user-scopes-apply.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT id,fndUserId,username,areapower,createTime,createBy,updateTime,updateBy,effectiveFrom,effectiveTo FROM fnd_user_power;

-- H005/fnd_department task=2108524501517955073 version=0 adapter=DPPMS_DEPARTMENTS
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-departments-apply.json
-- parameter-summary={"count": 28, "namePatterns": {"departmentCode<n>": 28}, "scopeParameters": {}}
SELECT d.id,d.departmentNum,d.departmentName,d.isparam,d.status,d.createTime,d.effectiveFrom,d.effectiveTo,d.updateTime,e.depID AS migrationEhrDepartmentId,CAST(e.isDisabled AS UNSIGNED) AS migrationEhrDisabled FROM fnd_department d LEFT JOIN ehr_department e ON CAST(e.depCode AS BINARY)=CAST(d.departmentNum AS BINARY) WHERE d.departmentNum IN (:departmentCode0,:departmentCode1,:departmentCode2,:departmentCode3,:departmentCode4,:departmentCode5,:departmentCode6,:departmentCode7,:departmentCode8,:departmentCode9,:departmentCode10,:departmentCode11,:departmentCode12,:departmentCode13,:departmentCode14,:departmentCode15,:departmentCode16,:departmentCode17,:departmentCode18,:departmentCode19,:departmentCode20,:departmentCode21,:departmentCode22,:departmentCode23,:departmentCode24,:departmentCode25,:departmentCode26,:departmentCode27);

-- H006/ehr_department task=2108538095265562625 version=0 adapter=DPPMS_EHR_DEPARTMENT_COMPLETION
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-sync-ehr-departments-apply.json
-- parameter-summary={"count": 8, "namePatterns": {"departmentCode<n>": 8}, "scopeParameters": {}}
SELECT d.depID,d.depCode,d.depName,d.depAbbr,d.compID,d.adminID,d.depGrade,d.depType,d.depProperty,d.depCost,d.director,d.director2,d.depEmp,d.depNum,d.effectDate,d.xOrder,CAST(d.isDisabled AS UNSIGNED) AS isDisabled,d.disabledDate,d.remark,d.depCustom1,d.depCustom2,d.depCustom3,d.depCustom4,d.depCustom5,f.id AS migrationFnd_id,f.departmentNum AS migrationFnd_departmentNum,f.departmentName AS migrationFnd_departmentName,f.isparam AS migrationFnd_isparam,f.status AS migrationFnd_status,f.createTime AS migrationFnd_createTime,f.effectiveFrom AS migrationFnd_effectiveFrom,f.effectiveTo AS migrationFnd_effectiveTo,f.updateTime AS migrationFnd_updateTime,p.depCode AS migrationParentCode,l.loginCount AS migrationDirectorLoginCount,l.account AS migrationDirectorAccount,fu.matchCount AS migrationDirectorFndCount,fu.id AS migrationDirectorFndId,tu.matchCount AS migrationDirectorTCount,tu.id AS migrationDirectorTId FROM ehr_department d LEFT JOIN fnd_department f ON CAST(f.departmentNum AS BINARY)=CAST(d.depCode AS BINARY) LEFT JOIN ehr_department p ON p.depID=d.adminID LEFT JOIN (SELECT empID,COUNT(*) AS loginCount,MIN(account) AS account FROM ehr_login GROUP BY empID) l ON l.empID=d.director LEFT JOIN (SELECT username,COUNT(*) AS matchCount,MIN(id) AS id FROM fnd_user_info GROUP BY username) fu ON CAST(fu.username AS BINARY)=CAST(l.account AS BINARY) LEFT JOIN (SELECT user_name,COUNT(*) AS matchCount,MIN(user_id) AS id FROM t_user GROUP BY user_name) tu ON CAST(tu.user_name AS BINARY)=CAST(l.account AS BINARY) WHERE d.depCode IN (:departmentCode0,:departmentCode1,:departmentCode2,:departmentCode3,:departmentCode4,:departmentCode5,:departmentCode6,:departmentCode7);

-- H007/fb_items task=2108534088732168194 version=0 adapter=DPPMS_PRODUCT_CATALOG
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-product-sync-state-20261009.json
-- parameter-summary={"count": 105, "namePatterns": {"migrationDefinitionRevisionId": 1, "productCode<n>": 104}, "scopeParameters": {}}
SELECT i.id,i.item,i.itemname,i.describe_,:migrationDefinitionRevisionId AS migrationDefinitionRevisionId FROM fb_items i WHERE CAST(i.item AS BINARY) IN (:productCode0,:productCode1,:productCode2,:productCode3,:productCode4,:productCode5,:productCode6,:productCode7,:productCode8,:productCode9,:productCode10,:productCode11,:productCode12,:productCode13,:productCode14,:productCode15,:productCode16,:productCode17,:productCode18,:productCode19,:productCode20,:productCode21,:productCode22,:productCode23,:productCode24,:productCode25,:productCode26,:productCode27,:productCode28,:productCode29,:productCode30,:productCode31,:productCode32,:productCode33,:productCode34,:productCode35,:productCode36,:productCode37,:productCode38,:productCode39,:productCode40,:productCode41,:productCode42,:productCode43,:productCode44,:productCode45,:productCode46,:productCode47,:productCode48,:productCode49,:productCode50,:productCode51,:productCode52,:productCode53,:productCode54,:productCode55,:productCode56,:productCode57,:productCode58,:productCode59,:productCode60,:productCode61,:productCode62,:productCode63,:productCode64,:productCode65,:productCode66,:productCode67,:productCode68,:productCode69,:productCode70,:productCode71,:productCode72,:productCode73,:productCode74,:productCode75,:productCode76,:productCode77,:productCode78,:productCode79,:productCode80,:productCode81,:productCode82,:productCode83,:productCode84,:productCode85,:productCode86,:productCode87,:productCode88,:productCode89,:productCode90,:productCode91,:productCode92,:productCode93,:productCode94,:productCode95,:productCode96,:productCode97,:productCode98,:productCode99,:productCode100,:productCode101,:productCode102,:productCode103);

-- H008/SHIPMENT_CONTRACT task=2108799116748152833 version=0 adapter=DPPMS_SHIPMENT_CONTRACT_WARRANTY_CORRECTION
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-contract-years-sync-state-20261010.json
-- parameter-summary={"count": 2, "namePatterns": {"contract<n>": 2}, "scopeParameters": {}}
SELECT * FROM fb_contract WHERE contract_id IN (:contract1,:contract2) ORDER BY contract_id;

-- H009/pm_project_header task=2108608417343934465 version=1 adapter=DPPMS_PROJECT_MASTER
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/approved-project-master-pilot-state-20261010.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT p.*, c.compCode AS migrationCompanyCode, fc.actorId AS creatorFndSourceKey, tc.actorId AS creatorTSourceKey, fu.actorId AS updaterFndSourceKey, tu.actorId AS updaterTSourceKey, COALESCE(fc.matchCount,0) AS creatorFndMatchCount, COALESCE(tc.matchCount,0) AS creatorTMatchCount, COALESCE(fu.matchCount,0) AS updaterFndMatchCount, COALESCE(tu.matchCount,0) AS updaterTMatchCount FROM pm_project_header p LEFT JOIN t_company c ON c.id=p.compId LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey, COUNT(*) AS matchCount, CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId FROM fnd_user_info u WHERE NOT (u.username='c00733' AND u.id=233) AND NOT (u.username='w02459' AND u.id=118) GROUP BY CAST(u.username AS BINARY)) fc ON fc.loginKey=CAST(p.createBy AS BINARY) LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey, COUNT(*) AS matchCount, CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tc ON tc.loginKey=CAST(p.createBy AS BINARY) LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey, COUNT(*) AS matchCount, CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId FROM fnd_user_info u WHERE NOT (u.username='c00733' AND u.id=233) AND NOT (u.username='w02459' AND u.id=118) GROUP BY CAST(u.username AS BINARY)) fu ON fu.loginKey=CAST(p.updateBy AS BINARY) LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey, COUNT(*) AS matchCount, CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tu ON tu.loginKey=CAST(p.updateBy AS BINARY)  WHERE p.projectId IN (50370,51304,51922,52443,52564,53487,53546,53614,53649,69300);

-- H010/pm_project_identity task=2109622534918856706 version=None adapter=DPPMS_PROJECT_IDENTITY
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/project-identity-task-pilot-state-20261012.json
-- parameter-summary={"count": 2, "namePatterns": {"projectTypes": 1, "projectIds": 1}, "scopeParameters": {"projectTypes": "10", "projectIds": "50370,51304,51922,52443,52564,53487,53546,53614,53649,69300"}}
SELECT original.projectId AS originalProjectId,original.projectType,original.projectCode,
  canonical.projectId AS canonicalProjectId
FROM pm_project original
JOIN (SELECT CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY)) AS projectIdentity,
  MAX(projectId) AS projectId FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
  ON canonical.projectIdentity <=> CONCAT(CAST(original.projectType AS BINARY),0x00,CAST(original.projectCode AS BINARY))
WHERE FIND_IN_SET(original.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds));

-- H011/pm_pb_plan_from_sms task=2108574253093683202 version=3 adapter=DPPMS_CONTRACT_COLLECTION_PLAN
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/collection-whole-task-upgrade-state-20261012.json
-- parameter-summary={"count": 1, "namePatterns": {"planIds": 1}, "scopeParameters": {"planIds": "10725,11495,14943,16806,20618,22134,28926,29361,31337,32454"}}
SELECT p.*,fc.actorId AS creatorFndSourceKey,tc.actorId AS creatorTSourceKey,
  fu.actorId AS updaterFndSourceKey,tu.actorId AS updaterTSourceKey,
  COALESCE(fc.matchCount,0) AS creatorFndMatchCount,COALESCE(tc.matchCount,0) AS creatorTMatchCount,
  COALESCE(fu.matchCount,0) AS updaterFndMatchCount,COALESCE(tu.matchCount,0) AS updaterTMatchCount,
  fc.actorIds AS creatorFndSourceKeys,fu.actorIds AS updaterFndSourceKeys
FROM pm_pb_plan_from_sms p
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
WHERE (:planIds='' OR FIND_IN_SET(p.id,:planIds));

-- H012/pm_project_contract task=2109363204544983041 version=1 adapter=DPPMS_PROJECT_CONTRACT_RELATION
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/contract-whole-task-upgrade-state-20261012.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "50370,51304,51922,52443,52564,53487,53546,53614,53649,69300", "projectTypes": "10"}}
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
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds));

-- H013/fnd_basic_data_type task=2109351154645135362 version=0 adapter=DPPMS_DICTIONARY_TYPES
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/dictionary-type-approved-sync-state-20261012.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT t.id,t.dataTypeCode,t.dataTypeName,t.status,t.createTime,t.createBy,t.updateTime,t.updateBy,t.effectiveFrom,t.effectiveTo,0 AS definitionMissing FROM fnd_basic_data_type t UNION ALL SELECT NULL,g.dataTypeCode,g.dataTypeCode,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1 AS definitionMissing FROM (SELECT DISTINCT dataTypeCode FROM fnd_basic_data) g LEFT JOIN fnd_basic_data_type t ON t.dataTypeCode=g.dataTypeCode WHERE t.id IS NULL;

-- H014/fnd_basic_data task=2109371486114320385 version=0 adapter=DPPMS_DICTIONARY_DATA
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/dictionary-data-approved-sync-state-20261012.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT * FROM fnd_basic_data ORDER BY id;

-- H015/pm_project_product_line task=2109829769958453249 version=1 adapter=DPPMS_PROJECT_PRODUCT_SCOPE
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/project-scope-whole-task-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "50370,51304,51922,52443,52564,53487,53546,53614,53649,69300", "projectTypes": "10"}}
SELECT l.*,p.projectCode AS originalProjectCode,p.projectType
FROM pm_project_product_line l
LEFT JOIN pm_project p ON p.projectId=l.projectId
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(l.projectId,:projectIds));
