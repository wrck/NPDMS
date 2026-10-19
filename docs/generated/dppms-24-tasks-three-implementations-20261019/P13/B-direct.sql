-- P13 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [EXECUTION_ORDER] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT r.id, r.orderExecNumber, r.dataSource, r.projectCode, r.projectName,
  r.salesManCode, r.salesManName, r.marketCode, r.marketName, r.systemId, r.systemName,
  r.expendId, r.expendName, r.industryId, r.industryName, r.officeCode, r.officeName,
  r.serviceTypeName, r.channelName, r.engineeFee, r.objId, r.applyType, r.corporationCode,
  r.customerProjectName, r.finalCustomerName, r.agentName, r.majorProjectLevel,
  r.projectMoney, r.submitTime, r.predBidDate, r.linkmanName, r.linkmanTel,
  latest.id AS canonicalId, latest.orderExecNumber AS canonical_orderExecNumber,
  latest.dataSource AS canonical_dataSource, latest.projectCode AS canonical_projectCode,
  latest.projectName AS canonical_projectName, latest.salesManCode AS canonical_salesManCode,
  latest.salesManName AS canonical_salesManName, latest.marketCode AS canonical_marketCode,
  latest.marketName AS canonical_marketName, latest.systemId AS canonical_systemId,
  latest.systemName AS canonical_systemName, latest.expendId AS canonical_expendId,
  latest.expendName AS canonical_expendName, latest.industryId AS canonical_industryId,
  latest.industryName AS canonical_industryName, latest.officeCode AS canonical_officeCode,
  latest.officeName AS canonical_officeName, latest.serviceTypeName AS canonical_serviceTypeName,
  latest.channelName AS canonical_channelName, latest.engineeFee AS canonical_engineeFee,
  latest.objId AS canonical_objId, latest.applyType AS canonical_applyType,
  latest.corporationCode AS canonical_corporationCode, latest.customerProjectName AS canonical_customerProjectName,
  latest.finalCustomerName AS canonical_finalCustomerName, latest.agentName AS canonical_agentName,
  latest.majorProjectLevel AS canonical_majorProjectLevel, latest.projectMoney AS canonical_projectMoney,
  latest.submitTime AS canonical_submitTime, latest.predBidDate AS canonical_predBidDate,
  latest.linkmanName AS canonical_linkmanName, latest.linkmanTel AS canonical_linkmanTel
FROM pm_project_property_from_sms r
JOIN (
  SELECT dataSource, orderExecNumber, CAST(dataSource AS BINARY) AS systemKey,
    CAST(orderExecNumber AS BINARY) AS executionKey, MAX(id) AS canonicalId
  FROM pm_project_property_from_sms
  GROUP BY dataSource, orderExecNumber, CAST(dataSource AS BINARY), CAST(orderExecNumber AS BINARY)
) selected ON selected.dataSource <=> r.dataSource AND selected.orderExecNumber <=> r.orderExecNumber
  AND selected.systemKey <=> CAST(r.dataSource AS BINARY)
  AND selected.executionKey <=> CAST(r.orderExecNumber AS BINARY)
JOIN pm_project_property_from_sms latest ON latest.id=selected.canonicalId
LEFT JOIN (
  SELECT MIN(SUBSTRING_INDEX(projectCode,'-',1)) AS projectCode
  FROM (SELECT original.*,canonical.projectId AS canonicalProjectId
FROM pm_project original
JOIN (
  SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,
    MAX(projectId) AS projectId
  FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)
) canonical ON canonical.typeKey <=> CAST(original.projectType AS BINARY)
  AND canonical.codeKey <=> CAST(original.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE current_project.disabled=0 AND current_project.effectiveTo IS NULL
  AND original.disabled=0 AND original.effectiveTo IS NULL
  AND FIND_IN_SET(current_project.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) valid_projects
  WHERE (:projectTypes='' OR FIND_IN_SET(projectType,:projectTypes))
    AND (:projectIds='' OR FIND_IN_SET(projectId,:projectIds))
  GROUP BY CAST(SUBSTRING_INDEX(projectCode,'-',1) AS BINARY)
) project_scope ON project_scope.projectCode=r.projectCode
  AND CAST(project_scope.projectCode AS BINARY)=CAST(r.projectCode AS BINARY)
WHERE project_scope.projectCode IS NOT NULL;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectExecutionOrderMapper.selectIncoming; pms-module-integration/src/main/resources/mapper/sync/DirectExecutionOrderMapper.xml:4; kind=select
SELECT id, tenant_id, version, creator, updater, create_time, update_time, deleted, source_system, execution_no, project_code, project_name, primary_project_id, sales_rep_code, sales_rep_name, sales_rep_phone, market_code, market_name, system_source_key, system_code, system_name, expend_source_key, expend_code, expend_name, industry_code, industry_name, department_id, department_code, department_name, service_type_name, channel_name, engineering_fee_raw, engineering_fee, source_object_id, apply_type, company_id, company_code, company_name, customer_project_name, final_customer_name, agent_name, project_manager_code, project_manager_name, decision_path, required_in_date, receiver_name, receiver_contact, receiver_address, loan_reason, project_type, major_project_level, project_amount, af_project_amount, submit_time, predicted_bid_time, contact_name, contact_phone, af_evidence_status, source_sync_time, status FROM com_crm_execution_order
WHERE tenant_id=#{query.tenantId} AND source_system=#{query.sourceSystem}
 /* CHOOSE  */ /* WHEN test=query.executionNumbers != null and !query.executionNumbers.isEmpty() */ AND execution_no IN
 /* FOREACH collection=query.executionNumbers; item=number; open=(; separator=,; close=) */ (#{number}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectScopeLookupMapper.selectById; pms-module-integration/src/main/resources/mapper/sync/DirectScopeLookupMapper.xml:4; kind=select
SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId} AND id=#{query.id} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectScopeLookupMapper.current; pms-module-integration/src/main/resources/mapper/sync/DirectScopeLookupMapper.xml:8; kind=select
SELECT * FROM com_delivery_scope_project_version WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectScopeLookupMapper.lockExisting; pms-module-integration/src/main/resources/mapper/sync/DirectScopeLookupMapper.xml:9; kind=select
SELECT * FROM com_delivery_scope_project_version WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=FALSE FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectExecutionOrderMapper.insertRow; pms-module-integration/src/main/resources/mapper/sync/DirectExecutionOrderMapper.xml:11; kind=insert
INSERT INTO com_crm_execution_order (tenant_id,version,creator,updater,create_time,update_time,deleted,source_system,execution_no,project_code,project_name,sales_rep_code,sales_rep_name,market_code,market_name,system_source_key,system_name,expend_source_key,expend_name,industry_code,industry_name,department_id,department_code,department_name,service_type_name,channel_name,engineering_fee_raw,engineering_fee,source_object_id,apply_type,company_id,company_code,company_name,customer_project_name,final_customer_name,agent_name,major_project_level,project_amount,submit_time,predicted_bid_time,contact_name,contact_phone,source_sync_time,status,af_evidence_status)
VALUES (#{row.tenantId},0,#{row.creator},#{row.updater},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE,#{row.sourceSystem},#{row.executionNo},#{row.projectCode},#{row.projectName},#{row.salesRepCode},#{row.salesRepName},#{row.marketCode},#{row.marketName},#{row.systemSourceKey},#{row.systemName},#{row.expendSourceKey},#{row.expendName},#{row.industryCode},#{row.industryName},#{row.departmentId},#{row.departmentCode},#{row.departmentName},#{row.serviceTypeName},#{row.channelName},#{row.engineeringFeeRaw},#{row.engineeringFee},#{row.sourceObjectId},#{row.applyType},#{row.companyId},#{row.companyCode},#{row.companyName},#{row.customerProjectName},#{row.finalCustomerName},#{row.agentName},#{row.majorProjectLevel},#{row.projectAmount},#{row.submitTime},#{row.predictedBidTime},#{row.contactName},#{row.contactPhone},#{row.sourceSyncTime},#{row.status},#{row.afEvidenceStatus});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectExecutionOrderMapper.updateRow; pms-module-integration/src/main/resources/mapper/sync/DirectExecutionOrderMapper.xml:15; kind=update
UPDATE com_crm_execution_order  SET /* dynamic SET trims trailing commas */ /* IF test=row.sourceSystem != null */ source_system=#{row.sourceSystem}, /* END IF */ 
 /* IF test=row.executionNo != null */ execution_no=#{row.executionNo}, /* END IF */ 
 /* IF test=row.projectCode != null */ project_code=#{row.projectCode}, /* END IF */ 
 /* IF test=row.projectName != null */ project_name=#{row.projectName}, /* END IF */ 
 /* IF test=row.salesRepCode != null */ sales_rep_code=#{row.salesRepCode}, /* END IF */ 
 /* IF test=row.salesRepName != null */ sales_rep_name=#{row.salesRepName}, /* END IF */ 
 /* IF test=row.marketCode != null */ market_code=#{row.marketCode}, /* END IF */ 
 /* IF test=row.marketName != null */ market_name=#{row.marketName}, /* END IF */ 
 /* IF test=row.systemSourceKey != null */ system_source_key=#{row.systemSourceKey}, /* END IF */ 
 /* IF test=row.systemName != null */ system_name=#{row.systemName}, /* END IF */ 
 /* IF test=row.expendSourceKey != null */ expend_source_key=#{row.expendSourceKey}, /* END IF */ 
 /* IF test=row.expendName != null */ expend_name=#{row.expendName}, /* END IF */ 
 /* IF test=row.industryCode != null */ industry_code=#{row.industryCode}, /* END IF */ 
 /* IF test=row.industryName != null */ industry_name=#{row.industryName}, /* END IF */ 
 /* IF test=row.departmentId != null */ department_id=#{row.departmentId}, /* END IF */ 
 /* IF test=row.departmentCode != null */ department_code=#{row.departmentCode}, /* END IF */ 
 /* IF test=row.departmentName != null */ department_name=#{row.departmentName}, /* END IF */ 
 /* IF test=row.serviceTypeName != null */ service_type_name=#{row.serviceTypeName}, /* END IF */ 
 /* IF test=row.channelName != null */ channel_name=#{row.channelName}, /* END IF */ 
 /* IF test=row.engineeringFeeRaw != null */ engineering_fee_raw=#{row.engineeringFeeRaw}, /* END IF */ 
 /* IF test=row.engineeringFee != null */ engineering_fee=#{row.engineeringFee}, /* END IF */ 
 /* IF test=row.sourceObjectId != null */ source_object_id=#{row.sourceObjectId}, /* END IF */ 
 /* IF test=row.applyType != null */ apply_type=#{row.applyType}, /* END IF */ 
 /* IF test=row.companyId != null */ company_id=#{row.companyId}, /* END IF */ 
 /* IF test=row.companyCode != null */ company_code=#{row.companyCode}, /* END IF */ 
 /* IF test=row.companyName != null */ company_name=#{row.companyName}, /* END IF */ 
 /* IF test=row.customerProjectName != null */ customer_project_name=#{row.customerProjectName}, /* END IF */ 
 /* IF test=row.finalCustomerName != null */ final_customer_name=#{row.finalCustomerName}, /* END IF */ 
 /* IF test=row.agentName != null */ agent_name=#{row.agentName}, /* END IF */ 
 /* IF test=row.majorProjectLevel != null */ major_project_level=#{row.majorProjectLevel}, /* END IF */ 
 /* IF test=row.projectAmount != null */ project_amount=#{row.projectAmount}, /* END IF */ 
 /* IF test=row.submitTime != null */ submit_time=#{row.submitTime}, /* END IF */ 
 /* IF test=row.predictedBidTime != null */ predicted_bid_time=#{row.predictedBidTime}, /* END IF */ 
 /* IF test=row.contactName != null */ contact_name=#{row.contactName}, /* END IF */ 
 /* IF test=row.contactPhone != null */ contact_phone=#{row.contactPhone}, /* END IF */ 
 /* IF test=row.sourceSyncTime != null */ source_sync_time=#{row.sourceSyncTime}, /* END IF */ 
version=version+1,updater=#{row.updater},update_time=CURRENT_TIMESTAMP WHERE tenant_id=#{row.tenantId} AND id=#{row.id} AND version=#{row.version} AND deleted=FALSE
AND source_system=#{row.sourceSystem} AND execution_no=#{row.executionNo};

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
