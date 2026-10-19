-- P13 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectExecutionOrder] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P13SourceMapper.xml
SELECT r.id AS sourceId,r.projectCode AS rawProjectCode, latest.id AS canonicalSourceId,
latest.`dataSource` AS sourceSystem,
latest.`orderExecNumber` AS executionNo,
latest.`projectCode` AS projectCode,
latest.`projectName` AS projectName,
latest.`salesManCode` AS salesRepCode,
latest.`salesManName` AS salesRepName,
latest.`marketCode` AS marketCode,
latest.`marketName` AS marketName,
latest.`systemId` AS systemSourceKey,
latest.`systemName` AS systemName,
latest.`expendId` AS expendSourceKey,
latest.`expendName` AS expendName,
latest.`industryId` AS industryCode,
latest.`industryName` AS industryName,
latest.`officeCode` AS departmentCode,
latest.`officeName` AS departmentName,
latest.`serviceTypeName` AS serviceTypeName,
latest.`channelName` AS channelName,
latest.`engineeFee` AS engineeringFeeRaw,
latest.`objId` AS sourceObjectId,
latest.`applyType` AS applyType,
latest.`corporationCode` AS companyCode,
latest.`customerProjectName` AS customerProjectName,
latest.`finalCustomerName` AS finalCustomerName,
latest.`agentName` AS agentName,
latest.`majorProjectLevel` AS majorProjectLevel,
latest.`projectMoney` AS projectAmount,
latest.`submitTime` AS submitTime,
latest.`predBidDate` AS predictedBidTime,
latest.`linkmanName` AS contactName,
latest.`linkmanTel` AS contactPhone
FROM pm_project_property_from_sms r
JOIN (SELECT CAST(dataSource AS BINARY) AS systemKey,CAST(orderExecNumber AS BINARY) AS executionKey,MAX(id) AS canonicalId
 FROM pm_project_property_from_sms GROUP BY CAST(dataSource AS BINARY),CAST(orderExecNumber AS BINARY)) chosen
 ON chosen.systemKey <=> CAST(r.dataSource AS BINARY) AND chosen.executionKey <=> CAST(r.orderExecNumber AS BINARY)
JOIN pm_project_property_from_sms latest ON latest.id=chosen.canonicalId
ORDER BY r.id LIMIT #{limit};

-- [selectProjectPrefixes] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P13SourceMapper.xml
SELECT DISTINCT SUBSTRING_INDEX(p.projectCode,'-',1)
FROM pm_project p
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS canonicalId
 FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) chosen
 ON chosen.canonicalId=p.projectId
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND p.projectType='10' AND p.projectCode IS NOT NULL
LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P13ExecutionOrderSyncTask$TargetMapper.selectExisting; pms-module-integration/src/main/resources/mapper/basesync/P13TargetMapper.xml:4; kind=select
SELECT id, tenant_id, version, creator, updater, create_time, update_time, deleted, source_system, execution_no, project_code, project_name, primary_project_id, sales_rep_code, sales_rep_name, sales_rep_phone, market_code, market_name, system_source_key, system_code, system_name, expend_source_key, expend_code, expend_name, industry_code, industry_name, department_id, department_code, department_name, service_type_name, channel_name, engineering_fee_raw, engineering_fee, source_object_id, apply_type, company_id, company_code, company_name, customer_project_name, final_customer_name, agent_name, project_manager_code, project_manager_name, decision_path, required_in_date, receiver_name, receiver_contact, receiver_address, loan_reason, project_type, major_project_level, project_amount, af_project_amount, submit_time, predicted_bid_time, contact_name, contact_phone, af_evidence_status, source_sync_time, status FROM com_crm_execution_order WHERE tenant_id=#{query.tenantId}
 /* CHOOSE  */ /* WHEN test=query.keys != null and !query.keys.isEmpty() */ AND ( /* FOREACH collection=query.keys; item=key; separator= OR  */ (source_system=#{key.sourceSystem} AND execution_no=#{key.executionNo}) /* repeat with separator= OR ; END FOREACH */ ) /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
 /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P13ExecutionOrderSyncTask$TargetMapper.oneSourcePerTargetIndex; pms-module-integration/src/main/resources/mapper/basesync/P13TargetMapper.xml:50; kind=select
SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='int_base_sync_binding' AND index_name='uk_int_base_sync_binding_target' AND non_unique=0;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P13ExecutionOrderSyncTask$TargetMapper.insertExecutionOrder; pms-module-integration/src/main/resources/mapper/basesync/P13TargetMapper.xml:7; kind=insert
INSERT INTO com_crm_execution_order (id,tenant_id,version,creator,updater,create_time,update_time,deleted,source_system,execution_no,project_code,project_name,sales_rep_code,sales_rep_name,market_code,market_name,system_source_key,system_name,expend_source_key,expend_name,industry_code,industry_name,department_id,department_code,department_name,service_type_name,channel_name,engineering_fee_raw,engineering_fee,source_object_id,apply_type,company_id,company_code,company_name,customer_project_name,final_customer_name,agent_name,major_project_level,project_amount,submit_time,predicted_bid_time,contact_name,contact_phone,source_sync_time,status,af_evidence_status) VALUES
 /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},0,#{row.target.creator},#{row.target.updater},#{row.target.createTime},#{row.target.createTime},FALSE,#{row.target.sourceSystem},#{row.target.executionNo},#{row.target.projectCode},#{row.target.projectName},#{row.target.salesRepCode},#{row.target.salesRepName},#{row.target.marketCode},#{row.target.marketName},#{row.target.systemSourceKey},#{row.target.systemName},#{row.target.expendSourceKey},#{row.target.expendName},#{row.target.industryCode},#{row.target.industryName},#{row.target.departmentId},#{row.target.departmentCode},#{row.target.departmentName},#{row.target.serviceTypeName},#{row.target.channelName},#{row.target.engineeringFeeRaw},#{row.target.engineeringFee},#{row.target.sourceObjectId},#{row.target.applyType},#{row.target.companyId},#{row.target.companyCode},#{row.target.companyName},#{row.target.customerProjectName},#{row.target.finalCustomerName},#{row.target.agentName},#{row.target.majorProjectLevel},#{row.target.projectAmount},#{row.target.submitTime},#{row.target.predictedBidTime},#{row.target.contactName},#{row.target.contactPhone},#{row.target.sourceSyncTime},#{row.target.status},#{row.target.afEvidenceStatus}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P13ExecutionOrderSyncTask$TargetMapper.updateExecutionOrder; pms-module-integration/src/main/resources/mapper/basesync/P13TargetMapper.xml:9; kind=update
UPDATE com_crm_execution_order  SET /* dynamic SET trims trailing commas */ /* IF test=row.target.sourceSystem != null */ source_system=#{row.target.sourceSystem}, /* END IF */ 
 /* IF test=row.target.executionNo != null */ execution_no=#{row.target.executionNo}, /* END IF */ 
 /* IF test=row.target.projectCode != null */ project_code=#{row.target.projectCode}, /* END IF */ 
 /* IF test=row.target.projectName != null */ project_name=#{row.target.projectName}, /* END IF */ 
 /* IF test=row.target.salesRepCode != null */ sales_rep_code=#{row.target.salesRepCode}, /* END IF */ 
 /* IF test=row.target.salesRepName != null */ sales_rep_name=#{row.target.salesRepName}, /* END IF */ 
 /* IF test=row.target.marketCode != null */ market_code=#{row.target.marketCode}, /* END IF */ 
 /* IF test=row.target.marketName != null */ market_name=#{row.target.marketName}, /* END IF */ 
 /* IF test=row.target.systemSourceKey != null */ system_source_key=#{row.target.systemSourceKey}, /* END IF */ 
 /* IF test=row.target.systemName != null */ system_name=#{row.target.systemName}, /* END IF */ 
 /* IF test=row.target.expendSourceKey != null */ expend_source_key=#{row.target.expendSourceKey}, /* END IF */ 
 /* IF test=row.target.expendName != null */ expend_name=#{row.target.expendName}, /* END IF */ 
 /* IF test=row.target.industryCode != null */ industry_code=#{row.target.industryCode}, /* END IF */ 
 /* IF test=row.target.industryName != null */ industry_name=#{row.target.industryName}, /* END IF */ 
 /* IF test=row.target.departmentId != null */ department_id=#{row.target.departmentId}, /* END IF */ 
 /* IF test=row.target.departmentCode != null */ department_code=#{row.target.departmentCode}, /* END IF */ 
 /* IF test=row.target.departmentName != null */ department_name=#{row.target.departmentName}, /* END IF */ 
 /* IF test=row.target.serviceTypeName != null */ service_type_name=#{row.target.serviceTypeName}, /* END IF */ 
 /* IF test=row.target.channelName != null */ channel_name=#{row.target.channelName}, /* END IF */ 
 /* IF test=row.target.engineeringFeeRaw != null */ engineering_fee_raw=#{row.target.engineeringFeeRaw}, /* END IF */ 
 /* IF test=row.target.engineeringFee != null */ engineering_fee=#{row.target.engineeringFee}, /* END IF */ 
 /* IF test=row.target.sourceObjectId != null */ source_object_id=#{row.target.sourceObjectId}, /* END IF */ 
 /* IF test=row.target.applyType != null */ apply_type=#{row.target.applyType}, /* END IF */ 
 /* IF test=row.target.companyId != null */ company_id=#{row.target.companyId}, /* END IF */ 
 /* IF test=row.target.companyCode != null */ company_code=#{row.target.companyCode}, /* END IF */ 
 /* IF test=row.target.companyName != null */ company_name=#{row.target.companyName}, /* END IF */ 
 /* IF test=row.target.customerProjectName != null */ customer_project_name=#{row.target.customerProjectName}, /* END IF */ 
 /* IF test=row.target.finalCustomerName != null */ final_customer_name=#{row.target.finalCustomerName}, /* END IF */ 
 /* IF test=row.target.agentName != null */ agent_name=#{row.target.agentName}, /* END IF */ 
 /* IF test=row.target.majorProjectLevel != null */ major_project_level=#{row.target.majorProjectLevel}, /* END IF */ 
 /* IF test=row.target.projectAmount != null */ project_amount=#{row.target.projectAmount}, /* END IF */ 
 /* IF test=row.target.submitTime != null */ submit_time=#{row.target.submitTime}, /* END IF */ 
 /* IF test=row.target.predictedBidTime != null */ predicted_bid_time=#{row.target.predictedBidTime}, /* END IF */ 
 /* IF test=row.target.contactName != null */ contact_name=#{row.target.contactName}, /* END IF */ 
 /* IF test=row.target.contactPhone != null */ contact_phone=#{row.target.contactPhone}, /* END IF */ 
 /* IF test=row.target.sourceSyncTime != null */ source_sync_time=#{row.target.sourceSyncTime}, /* END IF */ 
version=#{row.target.version},updater=#{row.target.updater},update_time=#{row.target.updateTime} WHERE tenant_id=#{row.target.tenantId} AND id=#{row.target.id} AND version=#{row.expectedVersion} AND deleted=FALSE
AND source_system=#{row.target.sourceSystem} AND execution_no=#{row.target.executionNo};

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
