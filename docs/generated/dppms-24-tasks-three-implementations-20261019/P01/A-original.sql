-- P01 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [COMPANY] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P01.json
SELECT CONCAT(objectName,':',businessKey) AS _groupKey,objectName,businessKey,CAST(SUBSTRING(MIN(CONCAT(sortRank,CAST(canonicalFields AS CHAR))),2) AS JSON) AS canonicalFields,
 JSON_ARRAYAGG(sourceAlias) AS sourceAliases
FROM (
SELECT 'COMPANY' AS objectName,HEX(CAST(TRIM(c.compCode) AS BINARY)) AS businessKey,0 AS sortRank,JSON_OBJECT('code',TRIM(c.compCode),'name',TRIM(c.compName),'status',COALESCE(CAST(c.isDisabled AS UNSIGNED),0),'companyCode',TRIM(c.compCode),'parentCode',NULL,'rawDirector',NULL,'sourceId',c.compID,'sourceTable','ehr_company') AS canonicalFields,JSON_OBJECT('table','ehr_company','id',c.compID,'rawFields',JSON_OBJECT('compID',c.compID,'compCode',c.compCode,'compName',c.compName,'isDisabled',CAST(c.isDisabled AS UNSIGNED))) AS sourceAlias FROM ehr_company c
UNION ALL
SELECT 'DEPARTMENT' AS objectName,HEX(CAST(d.depCode AS BINARY)) AS businessKey,0 AS sortRank,JSON_OBJECT('code',d.depCode,'name',d.depName,'status',COALESCE(CAST(d.isDisabled AS UNSIGNED),0),'companyCode',TRIM(c.compCode),'parentCode',p.depCode,'rawDirector',d.director,'sourceId',d.depID,'sourceTable','ehr_department','auditRule','OWNER_INGEST_TIME') AS canonicalFields,JSON_OBJECT('table','ehr_department','id',d.depID,'component','EHR_BASE','rawFields',JSON_OBJECT('depID',d.depID,'depCode',d.depCode,'depName',d.depName,'depAbbr',d.depAbbr,'compID',d.compID,'adminID',d.adminID,'depGrade',d.depGrade,'depType',d.depType,'depProperty',d.depProperty,'depCost',d.depCost,'director',d.director,'director2',d.director2,'depEmp',d.depEmp,'depNum',d.depNum,'effectDate',d.effectDate,'xOrder',d.xOrder,'isDisabled',CAST(d.isDisabled AS UNSIGNED),'disabledDate',d.disabledDate,'remark',d.remark,'depCustom1',d.depCustom1,'depCustom2',d.depCustom2,'depCustom3',d.depCustom3,'depCustom4',d.depCustom4,'depCustom5',d.depCustom5)) AS sourceAlias FROM ehr_department d LEFT JOIN ehr_company c ON c.compID=d.compID LEFT JOIN ehr_department p ON p.depID=d.adminID WHERE d.depGrade<='2' AND LENGTH(d.depCode)=6
UNION ALL
SELECT 'DEPARTMENT' AS objectName,HEX(CAST(f.departmentNum AS BINARY)) AS businessKey,2 AS sortRank,JSON_OBJECT('code',f.departmentNum,'name',f.departmentName,'status',CASE f.status WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE NULL END,'companyCode',NULL,'parentCode','160000','rawDirector',NULL,'sourceId',f.id,'sourceTable','fnd_department','rawStatus',f.status,'createTime',f.createTime,'updateTime',f.updateTime) AS canonicalFields,JSON_OBJECT('table','fnd_department','id',f.id,'rawFields',JSON_OBJECT('id',f.id,'departmentNum',f.departmentNum,'departmentName',f.departmentName,'isparam',f.isparam,'status',f.status,'createTime',f.createTime,'effectiveFrom',f.effectiveFrom,'effectiveTo',f.effectiveTo,'updateTime',f.updateTime)) AS sourceAlias FROM fnd_department f WHERE f.departmentNum=:P02_departmentCode0
UNION ALL
SELECT 'DEPARTMENT' AS objectName,HEX(CAST(d.depCode AS BINARY)) AS businessKey,1 AS sortRank,JSON_OBJECT('code',d.depCode,'name',CASE WHEN d.depCode IN ('167010','167020','167050') THEN f.departmentName ELSE d.depName END,'status',CASE WHEN d.depCode IN ('167010','167020','167050') THEN CASE f.status WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE NULL END ELSE COALESCE(CAST(d.isDisabled AS UNSIGNED),0) END,'companyCode',TRIM(c.compCode),'parentCode',CASE WHEN d.depCode IN ('167010','167020','167050') THEN '160000' ELSE p.depCode END,'rawDirector',d.director,'sourceId',d.depID,'sourceTable','ehr_department','fndSourceId',f.id,'createTime',CASE WHEN d.depCode IN ('167010','167020','167050') THEN f.createTime ELSE NULL END,'updateTime',CASE WHEN d.depCode IN ('167010','167020','167050') THEN f.updateTime ELSE NULL END,'auditRule',CASE WHEN d.depCode IN ('167010','167020','167050') THEN 'FND_HISTORY' ELSE 'OWNER_INGEST_TIME' END) AS canonicalFields,JSON_OBJECT('table','ehr_department','id',d.depID,'component','EHR_COMPLETION','fndId',f.id,'rawFields',JSON_OBJECT('depID',d.depID,'depCode',d.depCode,'depName',d.depName,'depAbbr',d.depAbbr,'compID',d.compID,'adminID',d.adminID,'depGrade',d.depGrade,'depType',d.depType,'depProperty',d.depProperty,'depCost',d.depCost,'director',d.director,'director2',d.director2,'depEmp',d.depEmp,'depNum',d.depNum,'effectDate',d.effectDate,'xOrder',d.xOrder,'isDisabled',CAST(d.isDisabled AS UNSIGNED),'disabledDate',d.disabledDate,'remark',d.remark,'depCustom1',d.depCustom1,'depCustom2',d.depCustom2,'depCustom3',d.depCustom3,'depCustom4',d.depCustom4,'depCustom5',d.depCustom5),'fndRawFields',JSON_OBJECT('id',f.id,'departmentNum',f.departmentNum,'departmentName',f.departmentName,'isparam',f.isparam,'status',f.status,'createTime',f.createTime,'effectiveFrom',f.effectiveFrom,'effectiveTo',f.effectiveTo,'updateTime',f.updateTime)) AS sourceAlias FROM ehr_department d LEFT JOIN fnd_department f ON CAST(f.departmentNum AS BINARY)=CAST(d.depCode AS BINARY) LEFT JOIN ehr_company c ON c.compID=d.compID LEFT JOIN ehr_department p ON p.depID=d.adminID WHERE d.depCode IN (:P03_departmentCode0,:P03_departmentCode1,:P03_departmentCode2,:P03_departmentCode3,:P03_departmentCode4,:P03_departmentCode5,:P03_departmentCode6,:P03_departmentCode7)
) canonical_sources
GROUP BY objectName,businessKey;

-- [DEPARTMENT] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P01.json
-- queryRef=COMPANY; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [fnd_department] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P01.json
-- queryRef=COMPANY; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [ehr_department] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P01.json
-- queryRef=COMPANY; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [COMPANY_REFERENCE] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P01.json
SELECT id AS sourceCompanyId,compCode AS sourceCompanyCode FROM t_company;

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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper.selectMaintenance; pms-module-integration/src/main/resources/mapper/sync/SyncRunMapper.xml:8; kind=select
SELECT id, status, config_snapshot FROM int_sync_run WHERE tenant_id=#{tenantId} AND deleted=FALSE
      AND (cache_pending=TRUE OR (status IN ('QUEUED','READING','VALIDATING','APPLYING') AND started_at < #{now}))
      ORDER BY id LIMIT 100;

-- cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper.selectCatalogue; yudao-module-system/src/main/resources/mapper/dept/DepartmentMigrationMapper.xml:8; kind=select
SELECT id,code,name,parent_id,sort,leader_user_id,phone,email,status,version,creator,create_time,updater,update_time
        FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper.selectCatalogueForUpdate; yudao-module-system/src/main/resources/mapper/dept/DepartmentMigrationMapper.xml:9; kind=select
SELECT id,code,name,parent_id,sort,leader_user_id,phone,email,status,version,creator,create_time,updater,update_time
        FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper.selectLeaderUserIds; yudao-module-system/src/main/resources/mapper/dept/DepartmentMigrationMapper.xml:10; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
         /* CHOOSE  */ /* WHEN test=query.userIds != null and !query.userIds.isEmpty() */ AND id IN  /* FOREACH collection=query.userIds; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper.selectCompaniesForUpdate; yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:4; kind=select
SELECT * FROM system_company WHERE tenant_id=#{tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper.selectAllCompaniesForUpdate; yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:7; kind=select
SELECT * FROM system_company WHERE tenant_id=#{tenantId} ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper.selectAllDepartmentsForUpdate; yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:10; kind=select
SELECT * FROM system_dept WHERE tenant_id=#{tenantId} ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper.selectDepartmentsForUpdate; yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:13; kind=select
SELECT * FROM system_dept WHERE tenant_id=#{tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper.selectCompanyForUpdate; yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:16; kind=select
SELECT * FROM system_company WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=FALSE FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper.selectDepartmentForUpdate; yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:19; kind=select
SELECT * FROM system_dept WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=FALSE FOR UPDATE;

-- cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper.countClearReferences; yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:22; kind=select
SELECT
      (SELECT COUNT(*) FROM system_users u JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=u.tenant_id
       WHERE u.tenant_id=#{tenantId} AND u.deleted=FALSE)
      + (SELECT COUNT(*) FROM system_user_company_department_scope s
         WHERE s.tenant_id=#{tenantId} AND s.deleted=FALSE AND
           (EXISTS (SELECT 1 FROM system_company c WHERE c.tenant_id=s.tenant_id AND c.id=s.company_id)
            OR EXISTS (SELECT 1 FROM system_dept d WHERE d.tenant_id=s.tenant_id AND d.id=s.department_id)))
      + (SELECT COUNT(*) FROM system_role r WHERE r.tenant_id=#{tenantId} AND r.deleted=FALSE
         AND r.data_scope=2 AND r.data_scope_dept_ids IS NOT NULL AND r.data_scope_dept_ids NOT IN ('','[]'));

-- cn.iocoder.yudao.module.system.dal.mysql.organization.OrganizationOwnershipMapper.selectForUpdate; yudao-module-system/src/main/resources/mapper/organization/OrganizationOwnershipMapper.xml:4; kind=select
SELECT * FROM system_organization_ownership
    WHERE tenant_id=#{tenantId} AND object_type=#{objectType} AND target_id=#{targetId} AND deleted=FALSE FOR UPDATE;

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

-- cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper.insertHistoricalDepartment; yudao-module-system/src/main/resources/mapper/dept/DepartmentMigrationMapper.xml:22; kind=insert
INSERT INTO system_dept(id,code,name,parent_id,sort,leader_user_id,phone,email,status,version,
            creator,create_time,updater,update_time,deleted,tenant_id)
        VALUES(#{department.id},#{department.code},#{department.name},#{department.parentId},#{department.sort},
            #{department.leaderUserId},#{department.phone},#{department.email},#{department.status},#{department.version},
            #{department.creator},#{department.createTime},#{department.updater},#{department.updateTime},FALSE,#{tenantId});

-- BaseMapper.insert:system_company; yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/company/CompanyDO.java:1; kind=orm_template
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `system_company` (`id`, `tenant_id`, `code`, `name`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_code, :orm_name, :orm_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper.completeHistoricalLeader; yudao-module-system/src/main/resources/mapper/dept/DepartmentMigrationMapper.xml:16; kind=update
UPDATE system_dept SET leader_user_id=#{after.leaderUserId},version=#{after.version},
            updater=#{after.updater},update_time=#{after.updateTime}
        WHERE id=#{before.id} AND tenant_id=#{tenantId} AND deleted=FALSE
            AND version=#{before.version} AND leader_user_id IS NULL;

-- cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper.deleteCompanies; yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:33; kind=delete
DELETE FROM system_company WHERE tenant_id=#{tenantId};

-- cn.iocoder.yudao.module.system.dal.mysql.organization.ManagedOrganizationMapper.deleteDepartments; yudao-module-system/src/main/resources/mapper/organization/ManagedOrganizationMapper.xml:34; kind=delete
DELETE FROM system_dept WHERE tenant_id=#{tenantId};

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
