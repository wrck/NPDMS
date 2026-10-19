-- P24 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [sms_ofst_contract_head_sap] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P24.json
SELECT r.*
FROM sms_ofst_contract_head_sap r
WHERE r.contract_num IS NOT NULL
  AND (:contractNos='' OR FIND_IN_SET(r.contract_num,:contractNos));

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractReceivableMigrationMapper.selectSourceForUpdate; pms-module-commerce/src/main/resources/mapper/migration/ContractReceivableMigrationMapper.xml:4; kind=select
SELECT id, tenant_id, contract_id, contract_no, company_id, company_code, company_name, company_resolution_source, mapping_status, project_name, source_order_no, customer_code, customer_name, contract_amount, delivered_amount, collected_amount, collected_ratio, receivable_amount, overdue_amount, currency_name, market_code, market_name, department_id, department_code, department_name, industry_name, marketing_representative_name, source_batch_code, import_batch_no, project_code, system_source_key, system_code, system_name, industry_code, expend_source_key, expend_code, expend_name, marketing_representative_code, secondary_representative_code, original_division_values, source_effective_from, source_effective_to, contract_create_time, latest_ship_time, source_system, source_record_key, source_sync_time, creator, create_time, updater, update_time, deleted FROM com_contract_receivable
    WHERE tenant_id=#{query.tenantId} AND source_system=#{query.sourceSystem}
      AND source_record_key=#{query.sourceKey} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractReceivableMigrationMapper.selectExactContractIds; pms-module-commerce/src/main/resources/mapper/migration/ContractReceivableMigrationMapper.xml:9; kind=select
SELECT id FROM com_contract WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
      AND contract_no=#{query.contractNo}
      AND CAST(contract_no AS BINARY)=CAST(#{query.contractNo} AS BINARY) ORDER BY id;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractReceivableMigrationMapper.selectContractIdentities; pms-module-commerce/src/main/resources/mapper/migration/ContractReceivableMigrationMapper.xml:14; kind=select
SELECT id,contract_no FROM com_contract WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
     /* CHOOSE  */ /* WHEN test=query.contractNos != null and !query.contractNos.isEmpty() */ AND contract_no IN  /* FOREACH collection=query.contractNos; item=no; open=(; separator=,; close=) */ (#{no}) /* repeat with separator=,; END FOREACH */ 
      AND CAST(contract_no AS BINARY) IN  /* FOREACH collection=query.contractNos; item=no; open=(; separator=,; close=) */ (CAST(#{no} AS BINARY)) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper.selectMaintenance; pms-module-integration/src/main/resources/mapper/sync/SyncRunMapper.xml:8; kind=select
SELECT id, status, config_snapshot FROM int_sync_run WHERE tenant_id=#{tenantId} AND deleted=FALSE
      AND (cache_pending=TRUE OR (status IN ('QUEUED','READING','VALIDATING','APPLYING') AND started_at < #{now}))
      ORDER BY id LIMIT 100;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractReceivableMigrationMapper.insertReceipt; pms-module-commerce/src/main/resources/mapper/migration/ContractReceivableMigrationMapper.xml:22; kind=insert
INSERT INTO com_contract_receivable (id, tenant_id, contract_id, contract_no, company_id, company_code, company_name, company_resolution_source, mapping_status, project_name, source_order_no, customer_code, customer_name, contract_amount, delivered_amount, collected_amount, collected_ratio, receivable_amount, overdue_amount, currency_name, market_code, market_name, department_id, department_code, department_name, industry_name, marketing_representative_name, source_batch_code, import_batch_no, project_code, system_source_key, system_code, system_name, industry_code, expend_source_key, expend_code, expend_name, marketing_representative_code, secondary_representative_code, original_division_values, source_effective_from, source_effective_to, contract_create_time, latest_ship_time, source_system, source_record_key, source_sync_time, creator, create_time, updater, update_time, deleted)
    VALUES (#{row.id}, #{row.tenantId}, #{row.contractId}, #{row.contractNo}, #{row.companyId}, #{row.companyCode}, #{row.companyName}, #{row.companyResolutionSource}, #{row.mappingStatus}, #{row.projectName}, #{row.sourceOrderNo}, #{row.customerCode}, #{row.customerName}, #{row.contractAmount}, #{row.deliveredAmount}, #{row.collectedAmount}, #{row.collectedRatio}, #{row.receivableAmount}, #{row.overdueAmount}, #{row.currencyName}, #{row.marketCode}, #{row.marketName}, #{row.departmentId}, #{row.departmentCode}, #{row.departmentName}, #{row.industryName}, #{row.marketingRepresentativeName}, #{row.sourceBatchCode}, #{row.importBatchNo}, #{row.projectCode}, #{row.systemSourceKey}, #{row.systemCode}, #{row.systemName}, #{row.industryCode}, #{row.expendSourceKey}, #{row.expendCode}, #{row.expendName}, #{row.marketingRepresentativeCode}, #{row.secondaryRepresentativeCode}, #{row.originalDivisionValues}, #{row.sourceEffectiveFrom}, #{row.sourceEffectiveTo}, #{row.contractCreateTime}, #{row.latestShipTime}, #{row.sourceSystem}, #{row.sourceRecordKey}, #{row.sourceSyncTime}, #{row.creator}, #{row.createTime}, #{row.updater}, #{row.updateTime}, #{row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractReceivableMigrationMapper.updateReceipt; pms-module-commerce/src/main/resources/mapper/migration/ContractReceivableMigrationMapper.xml:26; kind=update
UPDATE com_contract_receivable SET contract_id=#{row.contractId}, contract_no=#{row.contractNo}, company_id=#{row.companyId}, company_code=#{row.companyCode}, company_name=#{row.companyName}, company_resolution_source=#{row.companyResolutionSource}, mapping_status=#{row.mappingStatus}, project_name=#{row.projectName}, source_order_no=#{row.sourceOrderNo}, customer_code=#{row.customerCode}, customer_name=#{row.customerName}, contract_amount=#{row.contractAmount}, delivered_amount=#{row.deliveredAmount}, collected_amount=#{row.collectedAmount}, collected_ratio=#{row.collectedRatio}, receivable_amount=#{row.receivableAmount}, overdue_amount=#{row.overdueAmount}, currency_name=#{row.currencyName}, market_code=#{row.marketCode}, market_name=#{row.marketName}, department_id=#{row.departmentId}, department_code=#{row.departmentCode}, department_name=#{row.departmentName}, industry_name=#{row.industryName}, marketing_representative_name=#{row.marketingRepresentativeName}, source_batch_code=#{row.sourceBatchCode}, import_batch_no=#{row.importBatchNo}, project_code=#{row.projectCode}, system_source_key=#{row.systemSourceKey}, system_code=#{row.systemCode}, system_name=#{row.systemName}, industry_code=#{row.industryCode}, expend_source_key=#{row.expendSourceKey}, expend_code=#{row.expendCode}, expend_name=#{row.expendName}, marketing_representative_code=#{row.marketingRepresentativeCode}, secondary_representative_code=#{row.secondaryRepresentativeCode}, original_division_values=#{row.originalDivisionValues}, source_effective_from=#{row.sourceEffectiveFrom}, source_effective_to=#{row.sourceEffectiveTo}, contract_create_time=#{row.contractCreateTime}, latest_ship_time=#{row.latestShipTime}, source_system=#{row.sourceSystem}, source_record_key=#{row.sourceRecordKey}, source_sync_time=#{row.sourceSyncTime}, creator=#{row.creator}, create_time=#{row.createTime}, updater=#{row.updater}, update_time=#{row.updateTime}, deleted=#{row.deleted}
    WHERE tenant_id=#{row.tenantId} AND id=#{row.id} AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
