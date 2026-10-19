-- P18 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [pm_project_contract] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P18.json
-- 评审稿：用户已确认简化边界；未保存实际配置、未执行。
-- SQL仅按项目业务身份+合同号归并；原记录集合传入现有Owner，正式关系写一次。
-- ACTIVE优先、原创建/更新审计、同刻操作者冲突继续由原服务规则处理。
-- sourceRelations只作为传输/来源证据；不得把固定业务字段新存为业务JSON。
-- 不重读pm_project：按本地P16的projectType+完整projectCode精确筛选，再补originalProjectId。
-- 本地项目范围为空时不发起来源查询；范围筛选先于原审计/冲突校验。
SELECT grouped.projectType,grouped.originalProjectCode,grouped.contractNo,
 SHA2(CAST(JSON_ARRAY(grouped.projectType,grouped.originalProjectCode,grouped.contractNo) AS CHAR),256) AS migrationKey,
 grouped.sourceRelations
FROM (
SELECT g.projectType,REPLACE(m.projectCode,'-his','') AS originalProjectCode,
 c.contractNo,
 JSON_ARRAYAGG(JSON_OBJECT(
  'id',c.id,'contractNo',c.contractNo,'projectGroupCode',c.projectGroupCode,
  'createTime',c.createTime,'createBy',c.createBy,'updateTime',c.updateTime,'updateBy',c.updateBy,
  'member_id',m.id,'member_projectGroupCode',m.projectGroupCode,'member_projectCode',m.projectCode,
  'member_mergeBranchMark',m.mergeBranchMark,'member_smsProjectCode',m.smsProjectCode,
  'member_createTime',m.createTime,'member_createBy',m.createBy,'member_updateTime',m.updateTime,'member_updateBy',m.updateBy,
  'group_id',g.id,'group_projectGroupCode',g.projectGroupCode,'group_projectGroupName',g.projectGroupName,
  'group_projectType',g.projectType,'group_createTime',g.createTime,'group_createBy',g.createBy,
  'group_updateTime',g.updateTime,'group_updateBy',g.updateBy)) AS sourceRelations
FROM pm_project_contract c
JOIN pm_project_group g ON CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
LEFT JOIN pm_project_group_relationship m ON CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
WHERE FIND_IN_SET(g.projectType,:projectTypes)
GROUP BY g.projectType,CAST(g.projectType AS BINARY),REPLACE(m.projectCode,'-his',''),
 CAST(REPLACE(m.projectCode,'-his','') AS BINARY),c.contractNo,CAST(c.contractNo AS BINARY)
) grouped
;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper.selectOrganizationVisibleContractNumbers; pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml:32; kind=select
SELECT f.contract_no FROM (SELECT contract_no, MAX(company_id) AS company_id, MAX(company_name) AS company_name,
          CASE WHEN COUNT(DISTINCT NULLIF(department_code,''))=1 THEN MAX(department_id) END AS department_id,
          CASE WHEN COUNT(DISTINCT NULLIF(department_code,''))=1 THEN MAX(NULLIF(department_code,'')) END AS department_code,
          CASE WHEN COUNT(DISTINCT NULLIF(department_code,''))=1 THEN MAX(NULLIF(department_name,'')) END AS department_name
        FROM (
          SELECT contract_no,company_id,company_code,company_name,NULL AS department_id,department_code,department_name
          FROM com_contract WHERE tenant_id=#{query.tenantId} AND deleted=0
          /* IF test=query.contractNumbers != null */ /* CHOOSE  */ /* WHEN test=!query.contractNumbers.isEmpty() */ AND contract_no IN  /* FOREACH collection=query.contractNumbers; item=number; open=(; close=); separator=, */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ /* END IF */
          UNION ALL
          SELECT contract_no,company_id,company_code,company_name,department_id,department_code,department_name
          FROM com_shipment_contract_reference WHERE tenant_id=#{query.tenantId} AND deleted=0
          /* IF test=query.contractNumbers != null */ /* CHOOSE  */ /* WHEN test=!query.contractNumbers.isEmpty() */ AND contract_no IN  /* FOREACH collection=query.contractNumbers; item=number; open=(; close=); separator=, */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ /* END IF */
        ) ownership
        WHERE contract_no IS NOT NULL AND contract_no <> ''
        GROUP BY contract_no HAVING COUNT(DISTINCT company_id)=1 AND COUNT(DISTINCT NULLIF(company_code,''))=1
          AND SUM(CASE WHEN company_code IS NULL OR company_code='' THEN 1 ELSE 0 END)=0) f
       /* CHOOSE  */ /* WHEN test=query.grants != null and !query.grants.isEmpty() */ WHERE  /* FOREACH collection=query.grants; item=grant; open=(; close=); separator= OR  */ ((f.company_id=#{grant.companyId}
           /* IF test=grant.departmentId != null */ AND (f.department_id=#{grant.departmentId}
               /* IF test=grant.departmentCode != null and grant.departmentCode != '' */ OR CAST(f.department_code AS BINARY)=CAST(#{grant.departmentCode} AS BINARY) /* END IF */ ) /* END IF */ )) /* repeat with separator= OR ; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ WHERE 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper.selectDeviceOrganizations; pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml:44; kind=select
SELECT f.contract_no,f.company_id,f.company_name,f.department_id,f.department_code,f.department_name
      FROM (SELECT contract_no, MAX(company_id) AS company_id, MAX(company_name) AS company_name,
          CASE WHEN COUNT(DISTINCT NULLIF(department_code,''))=1 THEN MAX(department_id) END AS department_id,
          CASE WHEN COUNT(DISTINCT NULLIF(department_code,''))=1 THEN MAX(NULLIF(department_code,'')) END AS department_code,
          CASE WHEN COUNT(DISTINCT NULLIF(department_code,''))=1 THEN MAX(NULLIF(department_name,'')) END AS department_name
        FROM (
          SELECT contract_no,company_id,company_code,company_name,NULL AS department_id,department_code,department_name
          FROM com_contract WHERE tenant_id=#{query.tenantId} AND deleted=0
          /* IF test=query.contractNumbers != null */ /* CHOOSE  */ /* WHEN test=!query.contractNumbers.isEmpty() */ AND contract_no IN  /* FOREACH collection=query.contractNumbers; item=number; open=(; close=); separator=, */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ /* END IF */
          UNION ALL
          SELECT contract_no,company_id,company_code,company_name,department_id,department_code,department_name
          FROM com_shipment_contract_reference WHERE tenant_id=#{query.tenantId} AND deleted=0
          /* IF test=query.contractNumbers != null */ /* CHOOSE  */ /* WHEN test=!query.contractNumbers.isEmpty() */ AND contract_no IN  /* FOREACH collection=query.contractNumbers; item=number; open=(; close=); separator=, */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ /* END IF */
        ) ownership
        WHERE contract_no IS NOT NULL AND contract_no <> ''
        GROUP BY contract_no HAVING COUNT(DISTINCT company_id)=1 AND COUNT(DISTINCT NULLIF(company_code,''))=1
          AND SUM(CASE WHEN company_code IS NULL OR company_code='' THEN 1 ELSE 0 END)=0) f
       /* CHOOSE  */ /* WHEN test=query.contractNumbers != null and !query.contractNumbers.isEmpty() */ WHERE f.contract_no IN  /* FOREACH collection=query.contractNumbers; item=number; open=(; close=); separator=, */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ WHERE 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper.selectVisibleDeviceContractNumbers; pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml:52; kind=select
SELECT c.contract_no
        FROM com_contract c
        JOIN (SELECT contract_no FROM com_contract
              WHERE tenant_id = #{query.tenantId} AND deleted = 0
              /* IF test=query.contractNumbers != null */ /* CHOOSE  */ /* WHEN test=!query.contractNumbers.isEmpty() */ AND contract_no IN  /* FOREACH collection=query.contractNumbers; item=number; open=(; close=); separator=, */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ /* END IF */
              GROUP BY contract_no HAVING COUNT(*) = 1) unique_number
          ON unique_number.contract_no = c.contract_no
        WHERE c.tenant_id = #{query.tenantId} AND c.deleted = 0
          AND c.contract_no IS NOT NULL AND c.contract_no <> ''
          AND NOT EXISTS (SELECT 1 FROM com_shipment_contract_reference shipment
              WHERE shipment.tenant_id=c.tenant_id AND shipment.deleted=0 AND shipment.contract_no=c.contract_no
                AND (shipment.company_code IS NULL OR shipment.company_code=''
                  OR CAST(shipment.company_code AS BINARY) <> CAST(c.company_code AS BINARY)))
         /* CHOOSE  */ /* WHEN test=(query.companyCodes != null and !query.companyCodes.isEmpty()) or (query.projectIds != null and !query.projectIds.isEmpty()) */ AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(c.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_project_contract_relation pcr
                    WHERE pcr.tenant_id = c.tenant_id AND pcr.contract_id = c.id
                      AND pcr.deleted = 0 AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                      AND pcr.project_id IN
                       /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          ) /* END WHEN */ 
           /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper.selectBySourceForUpdate; pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml:87; kind=select
SELECT *
        FROM com_contract
        WHERE tenant_id = #{query.tenantId}
          AND master_source_system = #{query.sourceSystem}
          AND master_source_record_key = #{query.sourceRecordKey}
          AND deleted = 0
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper.selectByCompanyScope; pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml:120; kind=select
SELECT c.*
        FROM com_contract c
        WHERE c.tenant_id = #{query.tenantId}
          AND c.deleted = 0
          /* CHOOSE  */ /* WHEN test=(query.companyCodes != null and !query.companyCodes.isEmpty()) or (query.projectIds != null and !query.projectIds.isEmpty()) */ AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(c.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_project_contract_relation pcr
                    WHERE pcr.tenant_id = c.tenant_id AND pcr.contract_id = c.id
                      AND pcr.deleted = 0 AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                      AND pcr.project_id IN
                       /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          ) /* END WHEN */ 
           /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */
         /* IF test=query.contractNo != null and query.contractNo != '' */ AND contract_no LIKE CONCAT('%', #{query.contractNo}, '%') /* END IF */ 
         /* IF test=query.companyCode != null and query.companyCode != '' */ AND CAST(company_code AS BINARY) = CAST(#{query.companyCode} AS BINARY) /* END IF */ 
         /* IF test=query.contractType != null and query.contractType != '' */ AND contract_type = #{query.contractType} /* END IF */ 
         /* IF test=query.customerKeyword != null and query.customerKeyword != '' */ AND (customer_code LIKE CONCAT('%', #{query.customerKeyword}, '%')
            OR customer_name LIKE CONCAT('%', #{query.customerKeyword}, '%')) /* END IF */ 
         /* IF test=query.sourceSystem != null and query.sourceSystem != '' */ AND master_source_system = #{query.sourceSystem} /* END IF */ 
         /* IF test=query.status != null and query.status != '' */ AND status = #{query.status} /* END IF */ 
        ORDER BY c.contract_no, c.id
        LIMIT #{query.offset}, #{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper.selectDetailByCompanyScope; pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml:150; kind=select
SELECT c.*
        FROM com_contract c
        WHERE c.tenant_id = #{query.tenantId}
          AND c.id = #{query.contractId}
          AND c.deleted = 0
          /* CHOOSE  */ /* WHEN test=(query.companyCodes != null and !query.companyCodes.isEmpty()) or (query.projectIds != null and !query.projectIds.isEmpty()) */ AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(c.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_project_contract_relation pcr
                    WHERE pcr.tenant_id = c.tenant_id AND pcr.contract_id = c.id
                      AND pcr.deleted = 0 AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                      AND pcr.project_id IN
                       /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          ) /* END WHEN */ 
           /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */
        LIMIT 1;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper.selectCountByCompanyScope; pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml:161; kind=select
SELECT COUNT(*)
        FROM com_contract c
        WHERE c.tenant_id = #{query.tenantId}
          AND c.deleted = 0
          /* CHOOSE  */ /* WHEN test=(query.companyCodes != null and !query.companyCodes.isEmpty()) or (query.projectIds != null and !query.projectIds.isEmpty()) */ AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(c.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_project_contract_relation pcr
                    WHERE pcr.tenant_id = c.tenant_id AND pcr.contract_id = c.id
                      AND pcr.deleted = 0 AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                      AND pcr.project_id IN
                       /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          ) /* END WHEN */ 
           /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */
         /* IF test=query.contractNo != null and query.contractNo != '' */ AND contract_no LIKE CONCAT('%', #{query.contractNo}, '%') /* END IF */ 
         /* IF test=query.companyCode != null and query.companyCode != '' */ AND CAST(company_code AS BINARY) = CAST(#{query.companyCode} AS BINARY) /* END IF */ 
         /* IF test=query.contractType != null and query.contractType != '' */ AND contract_type = #{query.contractType} /* END IF */ 
         /* IF test=query.customerKeyword != null and query.customerKeyword != '' */ AND (customer_code LIKE CONCAT('%', #{query.customerKeyword}, '%')
            OR customer_name LIKE CONCAT('%', #{query.customerKeyword}, '%')) /* END IF */ 
         /* IF test=query.sourceSystem != null and query.sourceSystem != '' */ AND master_source_system = #{query.sourceSystem} /* END IF */ 
         /* IF test=query.status != null and query.status != '' */ AND status = #{query.status} /* END IF */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper.selectByIdForUpdate; pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml:188; kind=select
SELECT *
        FROM com_contract
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.contractId}
          AND deleted = 0
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper.selectRootReadPage; pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml:197; kind=select
SELECT c.* FROM com_contract c
        WHERE c.tenant_id = #{query.tenantId} AND c.deleted = 0
        /* CHOOSE  */ /* WHEN test=(query.companyCodes != null and !query.companyCodes.isEmpty()) or (query.projectIds != null and !query.projectIds.isEmpty()) */ AND (
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 */ CAST(c.company_code AS BINARY) IN
             /* FOREACH collection=query.companyCodes; item=companyCode; open=(; separator=,; close=) */ (CAST(#{companyCode} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END IF */ 
           /* IF test=query.companyCodes != null and query.companyCodes.size() > 0 and query.projectIds != null and query.projectIds.size() > 0 */ OR /* END IF */ 
           /* IF test=query.projectIds != null and query.projectIds.size() > 0 */ EXISTS (SELECT 1 FROM com_project_contract_relation pcr
                    WHERE pcr.tenant_id = c.tenant_id AND pcr.contract_id = c.id
                      AND pcr.deleted = 0 AND pcr.status = 'ACTIVE' AND pcr.effective_to IS NULL
                      AND pcr.project_id IN
                       /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ ) /* END IF */ 
          ) /* END WHEN */ 
           /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */
         /* IF test=query.afterId != null */ AND (c.contract_no > #{query.afterCode}
            OR (c.contract_no = #{query.afterCode} AND c.id > #{query.afterId})) /* END IF */ 
        ORDER BY c.contract_no, c.id
        LIMIT #{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ProjectContractRelationMigrationMapper.selectIdentity; pms-module-commerce/src/main/resources/mapper/migration/ProjectContractRelationMigrationMapper.xml:12; kind=select
SELECT id, tenant_id, project_id, contract_id, relation_role, source_system, source_table,
               source_record_key, effective_from, effective_to, status, version,
               creator, create_time, updater, update_time, deleted
        FROM com_project_contract_relation
        WHERE tenant_id = #{query.tenantId} AND project_id = #{query.projectId}
          AND contract_id = #{query.contractId} AND relation_role = 'RELATED';

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ProjectContractRelationMigrationMapper.selectIdentityForUpdate; pms-module-commerce/src/main/resources/mapper/migration/ProjectContractRelationMigrationMapper.xml:15; kind=select
SELECT id, tenant_id, project_id, contract_id, relation_role, source_system, source_table,
               source_record_key, effective_from, effective_to, status, version,
               creator, create_time, updater, update_time, deleted
        FROM com_project_contract_relation
        WHERE tenant_id = #{query.tenantId} AND project_id = #{query.projectId}
          AND contract_id = #{query.contractId} AND relation_role = 'RELATED' FOR UPDATE;

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

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSource; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:4; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSourceForUpdate; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:8; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectProjectSources; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:12; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} ORDER BY id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSnapshot; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:15; kind=select
SELECT * FROM proj_legacy_detail_snapshot WHERE tenant_id=#{query.tenantId} AND id=#{query.id};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectBatch; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:18; kind=select
SELECT * FROM proj_legacy_detail_snapshot WHERE tenant_id=#{query.tenantId} AND source_id=#{query.sourceId} AND batch_key=#{query.batchKey};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.countRecords; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:42; kind=select
SELECT COUNT(*) FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectRecords; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:45; kind=select
SELECT * FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */ ORDER BY source_key,id
    LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSourceLicenseRecords; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:49; kind=select
SELECT r.* FROM proj_legacy_detail_record r
    JOIN proj_legacy_detail_snapshot s ON s.id=r.snapshot_id AND s.tenant_id=r.tenant_id
    WHERE r.tenant_id=#{query.tenantId} AND s.tenant_id=#{query.tenantId} AND s.source_id=#{query.sourceId}
      AND r.domain_code='licenses'
     /* IF test=query.afterId != null */ AND r.id>#{query.afterId} /* END IF */ 
    ORDER BY r.id LIMIT #{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectIdentity; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:4; kind=select
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectIdentityForUpdate; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:7; kind=select
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND deleted=b'0' FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectExactCodeIds; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:10; kind=select
SELECT id FROM proj_project WHERE tenant_id=#{query.tenantId} AND project_code=#{query.projectCode}
    ORDER BY id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectRestorationIdentityForUpdate; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:147; kind=select
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectRestorationFingerprint; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:150; kind=select
SELECT SHA2(CAST(JSON_OBJECT(
      'id',p.`id`,
      'project_code',p.`project_code`,
      'code_root_id',p.`code_root_id`,
      'project_sequence',p.`project_sequence`,
      'code_rule_version',p.`code_rule_version`,
      'project_name',p.`project_name`,
      'parent_id',p.`parent_id`,
      'root_id',p.`root_id`,
      'tree_path',p.`tree_path`,
      'tree_depth',p.`tree_depth`,
      'tree_sort',p.`tree_sort`,
      'business_level_code',p.`business_level_code`,
      'business_level_name',p.`business_level_name`,
      'customer_id',p.`customer_id`,
      'customer_code',p.`customer_code`,
      'customer_name',p.`customer_name`,
      'manager_id',p.`manager_id`,
      'manager_employee_no',p.`manager_employee_no`,
      'manager_name',p.`manager_name`,
      'company_id',p.`company_id`,
      'company_code',p.`company_code`,
      'company_name',p.`company_name`,
      'department_id',p.`department_id`,
      'department_code',p.`department_code`,
      'department_name',p.`department_name`,
      'project_type',p.`project_type`,
      'signing_method',p.`signing_method`,
      'project_category',p.`project_category`,
      'implementation_mode',p.`implementation_mode`,
      'major_project_level',p.`major_project_level`,
      'market_code',p.`market_code`,
      'market_name',p.`market_name`,
      'system_code',p.`system_code`,
      'system_name',p.`system_name`,
      'expend_code',p.`expend_code`,
      'expend_name',p.`expend_name`,
      'industry_code',p.`industry_code`,
      'industry_name',p.`industry_name`,
      'customer_project_name',p.`customer_project_name`,
      'sales_type',p.`sales_type`,
      'business_type',p.`business_type`,
      'service_level_code',p.`service_level_code`,
      'not_track_reason',p.`not_track_reason`,
      'contract_no',p.`contract_no`,
      'implementation_location',p.`implementation_location`,
      'location_resolution_status',p.`location_resolution_status`,
      'creation_reason',p.`creation_reason`,
      'lifecycle_template_id',p.`lifecycle_template_id`,
      'lifecycle_template_revision_no',p.`lifecycle_template_revision_no`,
      'lifecycle_template_revision_id',p.`lifecycle_template_revision_id`,
      'template_load_method',p.`template_load_method`,
      'process_definition_key',p.`process_definition_key`,
      'process_definition_version',p.`process_definition_version`,
      'project_start_time',p.`project_start_time`,
      'project_refresh_time',p.`project_refresh_time`,
      'project_close_time',p.`project_close_time`,
      'source_type',p.`source_type`,
      'status',p.`status`,
      'progress',p.`progress`,
      'aggregation_weight',p.`aggregation_weight`,
      'weight_source',p.`weight_source`,
      'version',p.`version`,
      'creator',p.`creator`,
      'create_time',p.`create_time`,
      'updater',p.`updater`,
      'update_time',p.`update_time`,
      'deleted',CAST(p.`deleted` AS UNSIGNED),
      'deleted_time',p.`deleted_time`,
      'tenant_id',p.`tenant_id`,
      'lifecycle_status',p.`lifecycle_status`,
      'current_stage',p.`current_stage`,
      'assignment_status',p.`assignment_status`,
      'task_tree_version',p.`task_tree_version`,
      'task_progress_version',p.`task_progress_version`,
      'project_end_date',p.`project_end_date`,
      'closure_policy_snapshot',p.`closure_policy_snapshot`,
      'closure_type',p.`closure_type`,
      'closed_from_stage',p.`closed_from_stage`,
      'closed_at',p.`closed_at`,
      'active_plan_version_id',p.`active_plan_version_id`,
      'custom_info',p.`custom_info`,
      'order_create_time',p.`order_create_time`,
      'disabled',CAST(p.`disabled` AS UNSIGNED),
      'effective_from',p.`effective_from`,
      'effective_to',p.`effective_to`) AS CHAR),256)
    FROM proj_project p WHERE p.tenant_id=#{query.tenantId} AND p.id=#{query.projectId}
      AND p.deleted=b'1' AND p.source_type='MIGRATION' AND p.parent_id IS NULL
      AND p.root_id=p.id AND p.code_root_id=p.id AND p.assignment_status='UNASSIGNED'
      AND p.active_plan_version_id IS NULL AND p.lifecycle_template_id IS NULL AND p.manager_id IS NULL;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectRestorationReferenceCount; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:157; kind=select
SELECT (SELECT COUNT(*) FROM `proj_business_result_channel` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_legacy_detail_source` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_node_overdue_completion` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_preparation_confirmation` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_company_department_relation` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_deliverable` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_exit_record` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_gate` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_governance_action` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_lease_product` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_member_assignment` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_milestone` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_node_execution` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_party` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_plan_version` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_portfolio_member` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_product_configuration` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_progress_fact` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_progress_snapshot` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_real_product` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_risk` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_site` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_stage` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_stage_execution_contract` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_stage_snapshot` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_stage_transition` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_task` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_task_responsible` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_template_match_history` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_result_evidence_item` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_result_evidence_scan` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_result_subscription` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_result_subscription_candidate` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_task_business_link` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_task_dependency` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_task_tree_path` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_team_batch_change_item` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM proj_project WHERE tenant_id=#{query.tenantId} AND parent_id=#{query.projectId});

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectEndDateForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:17; kind=select
SELECT * FROM proj_project WHERE id = #{query.projectId} AND tenant_id = #{query.tenantId}
          AND deleted = b'0' FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectListByIdsForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:28; kind=select
SELECT *
        FROM proj_project
        WHERE id IN
         /* FOREACH collection=ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ 
          AND deleted = b'0'
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectVisiblePage; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:104; kind=select
SELECT p.*
        FROM proj_project p
        WHERE p.tenant_id = #{query.tenantId}
          AND p.deleted = b'0'
          AND p.id IN
           /* FOREACH collection=query.visibleProjectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
           /* IF test=query.projectNameKeyword != null and query.projectNameKeyword != '' */ AND p.project_name LIKE CONCAT('%', #{query.projectNameKeyword}, '%') /* END IF */ 
           /* IF test=query.projectCodePrefix != null and query.projectCodePrefix != '' */ AND p.project_code LIKE CONCAT(#{query.projectCodePrefix}, '%') /* END IF */ 
           /* CHOOSE  */ /* WHEN test=query.status != null and query.status != '' and query.status.matches('S[0-6]') */ AND p.current_stage = #{query.status}
                  AND p.lifecycle_status = 'ACTIVE' /* END WHEN */ 
               /* OTHERWISE  */ /* IF test=query.status != null and query.status != '' */ AND p.lifecycle_status = #{query.status} /* END IF */ /* END OTHERWISE */ /* END CHOOSE */ 
           /* IF test=query.signingMethod != null and query.signingMethod != '' */ AND p.signing_method = #{query.signingMethod} /* END IF */ 
           /* IF test=query.projectCategory != null and query.projectCategory != '' */ AND p.project_category = #{query.projectCategory} /* END IF */ 
           /* IF test=query.implementationMode != null and query.implementationMode != '' */ AND p.implementation_mode = #{query.implementationMode} /* END IF */ 
           /* IF test=query.majorProjectLevel != null and query.majorProjectLevel != '' */ AND p.major_project_level = #{query.majorProjectLevel} /* END IF */ 
           /* IF test=query.contractNoKeyword != null and query.contractNoKeyword != '' */ AND p.contract_no LIKE CONCAT('%', #{query.contractNoKeyword}, '%') /* END IF */ 
           /* IF test=query.departmentId != null */ AND p.department_id = #{query.departmentId} /* END IF */ 
           /* IF test=query.companyId != null */ AND p.company_id = #{query.companyId} /* END IF */ 
           /* IF test=query.createTimeStart != null */ AND p.create_time >= #{query.createTimeStart} /* END IF */ 
           /* IF test=query.createTimeEnd != null */ AND p.create_time < #{query.createTimeEnd} /* END IF */ 
           /* IF test=query.closeTimeStart != null */ AND p.project_close_time >= #{query.closeTimeStart} /* END IF */ 
           /* IF test=query.closeTimeEnd != null */ AND p.project_close_time < #{query.closeTimeEnd} /* END IF */ 
           /* IF test=query.refreshTimeStart != null */ AND p.project_refresh_time >= #{query.refreshTimeStart} /* END IF */ 
           /* IF test=query.refreshTimeEnd != null */ AND p.project_refresh_time < #{query.refreshTimeEnd} /* END IF */ 
           /* IF test=query.agentServiceProviderKeyword != null and query.agentServiceProviderKeyword != '' */ AND EXISTS (
                  SELECT 1 FROM proj_project_party pp
                  WHERE pp.project_id = p.id
                    AND pp.tenant_id = #{query.tenantId}
                    AND pp.deleted = b'0'
                    AND pp.status = 'ACTIVE'
                    AND (pp.effective_from IS NULL OR pp.effective_from <= #{query.effectiveAt})
                    AND (pp.effective_to IS NULL OR pp.effective_to > #{query.effectiveAt})
                    AND pp.party_role IN ('AGENT', 'SERVICE_PROVIDER')
                    AND (pp.party_name LIKE CONCAT('%', #{query.agentServiceProviderKeyword}, '%')
                      OR pp.party_code LIKE CONCAT('%', #{query.agentServiceProviderKeyword}, '%'))
              ) /* END IF */ 
        ORDER BY p.id DESC;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectCurrentResponsibilityForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:5; kind=select
SELECT *
        FROM proj_project_member_assignment
        WHERE project_id = #{query.projectId}
          AND member_role = #{query.memberRole}
           /* CHOOSE  */ /* WHEN test=query.assignmentType == "PRIMARY" */ AND (assignment_type = 'PRIMARY' OR assignment_type IS NULL) /* END WHEN */ 
               /* OTHERWISE  */ AND assignment_type = #{query.assignmentType} /* END OTHERWISE */ /* END CHOOSE */ 
          AND ((site_id = #{query.siteId}) OR (site_id IS NULL AND #{query.siteId} IS NULL))
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectActiveByUserForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:28; kind=select
SELECT *
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND user_id = #{query.userId}
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectResponsibilityNodeCount; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:55; kind=select
SELECT COUNT(*)
        FROM proj_project p
        WHERE p.tenant_id = #{query.tenantId}
        AND p.id IN
         /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
         /* IF test=query.filterProjectId != null */ AND p.id = #{query.filterProjectId} /* END IF */ 
        AND p.deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectResponsibilityNodePage; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:61; kind=select
SELECT p.id, p.project_code, p.project_name, p.parent_id, p.tree_path, p.tree_depth,
               p.tree_sort, p.assignment_status
        FROM proj_project p
        WHERE p.tenant_id = #{query.tenantId}
        AND p.id IN
         /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
         /* IF test=query.filterProjectId != null */ AND p.id = #{query.filterProjectId} /* END IF */ 
        AND p.deleted = b'0'
        ORDER BY p.tree_path, p.tree_sort, p.id
        LIMIT #{query.limit} OFFSET #{query.offset};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectCurrentServiceManagerAssignments; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:71; kind=select
SELECT id, project_id, user_id, employee_no, member_name, department_id, department_code,
               department_name, member_role, assignment_type, site_id, change_reason, effective_from
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id IN
           /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
          AND member_role IN ('SERVICE_MANAGER_L1', 'SERVICE_MANAGER_L2')
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY project_id, member_role, site_id, department_id,
                 CASE WHEN assignment_type = 'PRIMARY' OR assignment_type IS NULL THEN 0 ELSE 1 END,
                 id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectParticipantFacts; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:91; kind=select
SELECT id, project_id, user_id, member_role, assignment_type, version, tenant_id
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
           /* IF test=query.subjectUserId != null */ AND user_id = #{query.subjectUserId} /* END IF */ 
           /* CHOOSE  */ /* WHEN test=query.requiredRoleCodes != null and !query.requiredRoleCodes.isEmpty() */ AND member_role IN
                   /* FOREACH collection=query.requiredRoleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */ 
          AND (member_role = 'PROJECT_MANAGER' OR assignment_type = 'PRIMARY' OR assignment_type IS NULL)
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY user_id, member_role, id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectParticipantFactsForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:119; kind=select
SELECT id, project_id, user_id, member_role, assignment_type, version, tenant_id
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND user_id = #{query.userId}
           /* CHOOSE  */ /* WHEN test=query.requiredRoleCodes != null and !query.requiredRoleCodes.isEmpty() */ AND member_role IN
                   /* FOREACH collection=query.requiredRoleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */ 
          AND (member_role = 'PROJECT_MANAGER' OR assignment_type = 'PRIMARY' OR assignment_type IS NULL)
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= CURRENT_TIMESTAMP(3))
          AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP(3))
        ORDER BY user_id, member_role, id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectProjectManagersForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:163; kind=select
SELECT * FROM proj_project_member_assignment
        WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND member_role='PROJECT_MANAGER' AND authorization_confirmed = b'1' AND status='ACTIVE' AND deleted=b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectActiveMemberIdentityForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:172; kind=select
SELECT * FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId} AND project_id = #{query.projectId}
          AND user_id = #{query.userId}
          AND member_role IN
           /* FOREACH collection=query.roleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ 
          AND authorization_confirmed = b'1' AND status = 'ACTIVE' AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.selectLatestActive; pms-module-project/src/main/resources/mapper/projecttree/ProjectTreeVersionMapper.xml:12; kind=select
SELECT id, root_project_id, tree_version, status, change_batch_id, node_count,
        path_count, activated_at, failed_reason, version,
        creator, create_time, updater, update_time, deleted, tenant_id
        FROM proj_project_tree_version
        WHERE root_project_id = #{rootProjectId}
          AND status = 'ACTIVE'
          AND deleted = b'0'
        ORDER BY tree_version DESC, id DESC
        LIMIT 1;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.selectLatest; pms-module-project/src/main/resources/mapper/projecttree/ProjectTreeVersionMapper.xml:23; kind=select
SELECT id, root_project_id, tree_version, status, change_batch_id, node_count,
        path_count, activated_at, failed_reason, version,
        creator, create_time, updater, update_time, deleted, tenant_id
        FROM proj_project_tree_version
        WHERE root_project_id = #{rootProjectId}
          AND deleted = b'0'
        ORDER BY tree_version DESC, id DESC
        LIMIT 1;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.selectLatestActiveForUpdate; pms-module-project/src/main/resources/mapper/projecttree/ProjectTreeVersionMapper.xml:33; kind=select
SELECT id, root_project_id, tree_version, status, change_batch_id, node_count,
        path_count, activated_at, failed_reason, version,
        creator, create_time, updater, update_time, deleted, tenant_id
        FROM proj_project_tree_version
        WHERE root_project_id = #{rootProjectId}
          AND status = 'ACTIVE'
          AND deleted = b'0'
        ORDER BY tree_version DESC, id DESC
        LIMIT 1
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.selectLatestForUpdate; pms-module-project/src/main/resources/mapper/projecttree/ProjectTreeVersionMapper.xml:45; kind=select
SELECT id, root_project_id, tree_version, status, change_batch_id, node_count,
        path_count, activated_at, failed_reason, version,
        creator, create_time, updater, update_time, deleted, tenant_id
        FROM proj_project_tree_version
        WHERE root_project_id = #{rootProjectId}
          AND deleted = b'0'
        ORDER BY tree_version DESC, id DESC
        LIMIT 1
        FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ProjectContractRelationMigrationMapper.insertHistorical; pms-module-commerce/src/main/resources/mapper/migration/ProjectContractRelationMigrationMapper.xml:18; kind=insert
INSERT INTO com_project_contract_relation
        (id,tenant_id,project_id,contract_id,relation_role,source_system,source_table,source_record_key,
         effective_from,effective_to,status,version,creator,create_time,updater,update_time,deleted)
        VALUES (#{row.id},#{row.tenantId},#{row.projectId},#{row.contractId},#{row.relationRole},#{row.sourceSystem},
                #{row.sourceTable},#{row.sourceRecordKey},#{row.effectiveFrom},#{row.effectiveTo},#{row.status},
                #{row.version},#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},#{row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ProjectContractRelationMigrationMapper.correctHistorical; pms-module-commerce/src/main/resources/mapper/migration/ProjectContractRelationMigrationMapper.xml:27; kind=update
UPDATE com_project_contract_relation
        SET creator=#{write.after.creator}, create_time=#{write.after.createTime},
            updater=#{write.after.updater}, update_time=#{write.after.updateTime},
            effective_from=#{write.after.effectiveFrom}, version=#{write.after.version}
        WHERE tenant_id=#{write.before.tenantId} AND id=#{write.before.id} AND version=#{write.before.version}
          AND deleted=0;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ProjectContractRelationMigrationMapper.restoreWithdrawn; pms-module-commerce/src/main/resources/mapper/migration/ProjectContractRelationMigrationMapper.xml:36; kind=update
UPDATE com_project_contract_relation
        SET deleted=0, version=#{write.after.version}, update_time=update_time
        WHERE tenant_id=#{write.before.tenantId} AND id=#{write.before.id}
          AND version=#{write.before.version} AND deleted=1;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
