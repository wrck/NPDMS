-- P01 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [COMPANY] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT c.compID AS sourceId,'COMPANY' AS objectName,HEX(CAST(TRIM(c.compCode) AS BINARY)) AS businessKey,0 AS sortRank,JSON_OBJECT('code',TRIM(c.compCode),'name',TRIM(c.compName),'status',COALESCE(CAST(c.isDisabled AS UNSIGNED),0),'companyCode',TRIM(c.compCode),'parentCode',NULL,'rawDirector',NULL,'sourceId',c.compID,'sourceTable','ehr_company') AS canonicalFields,JSON_OBJECT('table','ehr_company','id',c.compID,'rawFields',JSON_OBJECT('compID',c.compID,'compCode',c.compCode,'compName',c.compName,'isDisabled',CAST(c.isDisabled AS UNSIGNED))) AS sourceAlias FROM ehr_company c;

-- [DEPARTMENT] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT d.depID AS sourceId,'DEPARTMENT' AS objectName,HEX(CAST(d.depCode AS BINARY)) AS businessKey,0 AS sortRank,JSON_OBJECT('code',d.depCode,'name',d.depName,'status',COALESCE(CAST(d.isDisabled AS UNSIGNED),0),'companyCode',TRIM(c.compCode),'parentCode',p.depCode,'rawDirector',d.director,'sourceId',d.depID,'sourceTable','ehr_department','auditRule','OWNER_INGEST_TIME') AS canonicalFields,JSON_OBJECT('table','ehr_department','id',d.depID,'component','EHR_BASE','rawFields',JSON_OBJECT('depID',d.depID,'depCode',d.depCode,'depName',d.depName,'depAbbr',d.depAbbr,'compID',d.compID,'adminID',d.adminID,'depGrade',d.depGrade,'depType',d.depType,'depProperty',d.depProperty,'depCost',d.depCost,'director',d.director,'director2',d.director2,'depEmp',d.depEmp,'depNum',d.depNum,'effectDate',d.effectDate,'xOrder',d.xOrder,'isDisabled',CAST(d.isDisabled AS UNSIGNED),'disabledDate',d.disabledDate,'remark',d.remark,'depCustom1',d.depCustom1,'depCustom2',d.depCustom2,'depCustom3',d.depCustom3,'depCustom4',d.depCustom4,'depCustom5',d.depCustom5)) AS sourceAlias FROM ehr_department d LEFT JOIN ehr_company c ON c.compID=d.compID LEFT JOIN ehr_department p ON p.depID=d.adminID WHERE d.depGrade<='2' AND LENGTH(d.depCode)=6;

-- [fnd_department] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT f.id AS sourceId,'DEPARTMENT' AS objectName,HEX(CAST(f.departmentNum AS BINARY)) AS businessKey,2 AS sortRank,JSON_OBJECT('code',f.departmentNum,'name',f.departmentName,'status',CASE WHEN (f.departmentNum='160700002' AND e.depID=541 OR f.departmentNum='310500001' AND e.depID=391) AND CAST(e.isDisabled AS UNSIGNED)=1 THEN 1 ELSE CASE f.status WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE NULL END END,'companyCode',NULL,'parentCode',CASE WHEN f.departmentNum LIKE '16%' THEN '160000' WHEN f.departmentNum LIKE '31%' THEN '310000' WHEN f.departmentNum='280700' THEN '280000' WHEN f.departmentNum='999999' THEN NULL ELSE NULL END,'rawDirector',NULL,'sourceId',f.id,'sourceTable','fnd_department','rawStatus',f.status,'ehrDepartmentId',e.depID,'ehrDisabled',CAST(e.isDisabled AS UNSIGNED),'createTime',f.createTime,'updateTime',f.updateTime) AS canonicalFields,JSON_OBJECT('table','fnd_department','id',f.id,'rawFields',JSON_OBJECT('id',f.id,'departmentNum',f.departmentNum,'departmentName',f.departmentName,'isparam',f.isparam,'status',f.status,'createTime',f.createTime,'effectiveFrom',f.effectiveFrom,'effectiveTo',f.effectiveTo,'updateTime',f.updateTime)) AS sourceAlias FROM fnd_department f LEFT JOIN ehr_department e ON CAST(e.depCode AS BINARY)=CAST(f.departmentNum AS BINARY) WHERE f.departmentNum IN (:P02_departmentCode0,:P02_departmentCode1,:P02_departmentCode2,:P02_departmentCode3,:P02_departmentCode4,:P02_departmentCode5,:P02_departmentCode6,:P02_departmentCode7,:P02_departmentCode8,:P02_departmentCode9,:P02_departmentCode10,:P02_departmentCode11,:P02_departmentCode12,:P02_departmentCode13,:P02_departmentCode14,:P02_departmentCode15,:P02_departmentCode16,:P02_departmentCode17,:P02_departmentCode18,:P02_departmentCode19,:P02_departmentCode20,:P02_departmentCode21,:P02_departmentCode22,:P02_departmentCode23,:P02_departmentCode24,:P02_departmentCode25,:P02_departmentCode26,:P02_departmentCode27,:P02_departmentCode28);

-- [ehr_department] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT d.depID AS sourceId,'DEPARTMENT' AS objectName,HEX(CAST(d.depCode AS BINARY)) AS businessKey,1 AS sortRank,JSON_OBJECT('code',d.depCode,'name',CASE WHEN d.depCode IN ('167010','167020','167050') THEN f.departmentName ELSE d.depName END,'status',CASE WHEN d.depCode IN ('167010','167020','167050') THEN CASE f.status WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE NULL END ELSE COALESCE(CAST(d.isDisabled AS UNSIGNED),0) END,'companyCode',TRIM(c.compCode),'parentCode',CASE WHEN d.depCode IN ('167010','167020','167050') THEN '160000' ELSE p.depCode END,'rawDirector',d.director,'sourceId',d.depID,'sourceTable','ehr_department','fndSourceId',f.id,'createTime',CASE WHEN d.depCode IN ('167010','167020','167050') THEN f.createTime ELSE NULL END,'updateTime',CASE WHEN d.depCode IN ('167010','167020','167050') THEN f.updateTime ELSE NULL END,'auditRule',CASE WHEN d.depCode IN ('167010','167020','167050') THEN 'FND_HISTORY' ELSE 'OWNER_INGEST_TIME' END) AS canonicalFields,JSON_OBJECT('table','ehr_department','id',d.depID,'component','EHR_COMPLETION','fndId',f.id,'rawFields',JSON_OBJECT('depID',d.depID,'depCode',d.depCode,'depName',d.depName,'depAbbr',d.depAbbr,'compID',d.compID,'adminID',d.adminID,'depGrade',d.depGrade,'depType',d.depType,'depProperty',d.depProperty,'depCost',d.depCost,'director',d.director,'director2',d.director2,'depEmp',d.depEmp,'depNum',d.depNum,'effectDate',d.effectDate,'xOrder',d.xOrder,'isDisabled',CAST(d.isDisabled AS UNSIGNED),'disabledDate',d.disabledDate,'remark',d.remark,'depCustom1',d.depCustom1,'depCustom2',d.depCustom2,'depCustom3',d.depCustom3,'depCustom4',d.depCustom4,'depCustom5',d.depCustom5),'fndRawFields',JSON_OBJECT('id',f.id,'departmentNum',f.departmentNum,'departmentName',f.departmentName,'isparam',f.isparam,'status',f.status,'createTime',f.createTime,'effectiveFrom',f.effectiveFrom,'effectiveTo',f.effectiveTo,'updateTime',f.updateTime)) AS sourceAlias FROM ehr_department d LEFT JOIN fnd_department f ON CAST(f.departmentNum AS BINARY)=CAST(d.depCode AS BINARY) LEFT JOIN ehr_company c ON c.compID=d.compID LEFT JOIN ehr_department p ON p.depID=d.adminID WHERE d.depCode IN (:P03_departmentCode0,:P03_departmentCode1,:P03_departmentCode2,:P03_departmentCode3,:P03_departmentCode4,:P03_departmentCode5,:P03_departmentCode6,:P03_departmentCode7);

-- [COMPANY_REFERENCE] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT id AS sourceCompanyId,compCode AS sourceCompanyCode FROM t_company;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.selectCompanies; pms-module-integration/src/main/resources/mapper/sync/DirectOrganizationMapper.xml:14; kind=select
SELECT id,'COMPANY' AS type,code,name,NULL AS parent_id,NULL AS sort,NULL AS leader_user_id,
      NULL AS phone,NULL AS email,status,version,creator,create_time,updater,update_time
    FROM system_company WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.lockCompanies; pms-module-integration/src/main/resources/mapper/sync/DirectOrganizationMapper.xml:15; kind=select
SELECT id,'COMPANY' AS type,code,name,NULL AS parent_id,NULL AS sort,NULL AS leader_user_id,
      NULL AS phone,NULL AS email,status,version,creator,create_time,updater,update_time
    FROM system_company WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.selectDepartments; pms-module-integration/src/main/resources/mapper/sync/DirectOrganizationMapper.xml:16; kind=select
SELECT id,'DEPARTMENT' AS type,code,name,parent_id,sort,leader_user_id,phone,email,status,version,
      creator,create_time,updater,update_time
    FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.lockDepartments; pms-module-integration/src/main/resources/mapper/sync/DirectOrganizationMapper.xml:17; kind=select
SELECT id,'DEPARTMENT' AS type,code,name,parent_id,sort,leader_user_id,phone,email,status,version,
      creator,create_time,updater,update_time
    FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.selectLeaders; pms-module-integration/src/main/resources/mapper/sync/DirectOrganizationMapper.xml:18; kind=select
SELECT id FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
     /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.insertCompany; pms-module-integration/src/main/resources/mapper/sync/DirectOrganizationMapper.xml:24; kind=insert
INSERT INTO system_company(id,code,name,status,version,creator,create_time,updater,update_time,tenant_id,deleted)
    VALUES(#{write.node.id},#{write.node.code},#{write.node.name},#{write.node.status},#{write.node.version},
      #{write.node.creator},#{write.node.createTime},#{write.node.updater},#{write.node.updateTime},#{write.tenantId},FALSE);

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.insertDepartment; pms-module-integration/src/main/resources/mapper/sync/DirectOrganizationMapper.xml:29; kind=insert
INSERT INTO system_dept(id,code,name,parent_id,sort,leader_user_id,phone,email,status,version,creator,create_time,updater,update_time,tenant_id,deleted)
    VALUES(#{write.node.id},#{write.node.code},#{write.node.name},#{write.node.parentId},#{write.node.sort},
      #{write.node.leaderUserId},#{write.node.phone},#{write.node.email},#{write.node.status},#{write.node.version},
      #{write.node.creator},#{write.node.createTime},#{write.node.updater},#{write.node.updateTime},#{write.tenantId},FALSE);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectOrganizationMapper.completeLeader; pms-module-integration/src/main/resources/mapper/sync/DirectOrganizationMapper.xml:35; kind=update
UPDATE system_dept SET leader_user_id=#{write.after.leaderUserId},version=#{write.after.version},
      updater=#{write.after.updater},update_time=#{write.after.updateTime}
    WHERE tenant_id=#{write.tenantId} AND id=#{write.before.id} AND deleted=FALSE
      AND version=#{write.before.version} AND leader_user_id IS NULL;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
