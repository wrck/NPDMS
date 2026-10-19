-- P10 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [CUSTOMER] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT AccountId,AccountNumber,erpCustomerCode,Name,Telephone1,EMailAddress1,Address1_Composite,new_mkt_unit_id,new_mkt_unit_idName,new_industry_level1_id,new_industry_level1_idName,new_industry_level2_id,new_industry_level2_idName,new_industry_level3_id,new_industry_level3_idName,new_industry_level4_id,new_industry_level4_idName,StateCode,ModifiedOn,CASE WHEN Name IS NULL OR LTRIM(RTRIM(Name))='' THEN 'MISSING_CUSTOMER_NAME' WHEN LEN(Name)>128 THEN 'CUSTOMER_NAME_TOO_LONG' WHEN COUNT(*) OVER (PARTITION BY LTRIM(RTRIM(AccountNumber)))>1 THEN 'DUPLICATE_CUSTOMER_CODE' ELSE NULL END AS migrationIssue FROM DPtech_v_account;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectCustomerMapper.selectCurrentMapping; pms-module-integration/src/main/resources/mapper/sync/DirectCustomerMapper.xml:4; kind=select
SELECT * FROM cus_customer_external_mapping WHERE tenant_id=#{query.tenantId} AND source_system='CRM'
      AND source_key=#{query.sourceKey} AND effective_to IS NULL AND deleted=FALSE FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectCustomerMapper.selectCustomerForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectCustomerMapper.xml:8; kind=select
SELECT code,erp_customer_code,name,short_name,customer_level,lifecycle_status,source_type,source_key,source_version,sync_status,data_as_of,reconciliation_pending,temporary_reason,contact_phone,contact_email,department_code,department_name,market_code,market_name,system_code,system_name,expend_code,expend_name,industry_code,industry_name,address,remark,id,tenant_id,version,creator,create_time,updater,update_time,deleted FROM cus_customer_master WHERE tenant_id=#{query.tenantId} AND id=#{query.customerId} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectCustomerMapper.selectCodeForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectCustomerMapper.xml:11; kind=select
SELECT code,erp_customer_code,name,short_name,customer_level,lifecycle_status,source_type,source_key,source_version,sync_status,data_as_of,reconciliation_pending,temporary_reason,contact_phone,contact_email,department_code,department_name,market_code,market_name,system_code,system_name,expend_code,expend_name,industry_code,industry_name,address,remark,id,tenant_id,version,creator,create_time,updater,update_time,deleted FROM cus_customer_master WHERE tenant_id=#{query.tenantId} AND code=#{query.code} FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectCustomerMapper.insertCustomer; pms-module-integration/src/main/resources/mapper/sync/DirectCustomerMapper.xml:14; kind=insert
INSERT INTO cus_customer_master (code,erp_customer_code,name,short_name,contact_phone,contact_email,address,department_code,department_name,market_code,market_name,system_code,system_name,expend_code,expend_name,industry_code,industry_name,lifecycle_status,source_key,source_version,data_as_of,source_type,sync_status,reconciliation_pending,version,tenant_id,deleted)
    VALUES (#{row.code},#{row.erpCustomerCode},#{row.name},#{row.shortName},#{row.contactPhone},#{row.contactEmail},#{row.address},#{row.departmentCode},#{row.departmentName},#{row.marketCode},#{row.marketName},#{row.systemCode},#{row.systemName},#{row.expendCode},#{row.expendName},#{row.industryCode},#{row.industryName},#{row.lifecycleStatus},#{row.sourceKey},#{row.sourceVersion},#{row.dataAsOf},#{row.sourceType},#{row.syncStatus},#{row.reconciliationPending},0,#{row.tenantId},FALSE);

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectCustomerMapper.insertMapping; pms-module-integration/src/main/resources/mapper/sync/DirectCustomerMapper.xml:18; kind=insert
INSERT INTO cus_customer_external_mapping(customer_id,source_system,source_key,source_version,effective_from,tenant_id,deleted)
    VALUES(#{row.customerId},#{row.sourceSystem},#{row.sourceKey},#{row.sourceVersion},#{row.effectiveFrom},#{row.tenantId},FALSE);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectCustomerMapper.updateCustomer; pms-module-integration/src/main/resources/mapper/sync/DirectCustomerMapper.xml:22; kind=update
UPDATE cus_customer_master  SET /* dynamic SET trims trailing commas */ /* IF test=command.row.code != null */ code=#{command.row.code}, /* END IF */ 
      erp_customer_code=#{command.row.erpCustomerCode},
       /* IF test=command.row.name != null */ name=#{command.row.name}, /* END IF */ 
       /* IF test=command.row.shortName != null */ short_name=#{command.row.shortName}, /* END IF */ 
       /* IF test=command.row.contactPhone != null */ contact_phone=#{command.row.contactPhone}, /* END IF */ 
       /* IF test=command.row.contactEmail != null */ contact_email=#{command.row.contactEmail}, /* END IF */ 
       /* IF test=command.row.address != null */ address=#{command.row.address}, /* END IF */ 
       /* IF test=command.row.departmentCode != null */ department_code=#{command.row.departmentCode}, /* END IF */ 
       /* IF test=command.row.departmentName != null */ department_name=#{command.row.departmentName}, /* END IF */ 
       /* IF test=command.row.marketCode != null */ market_code=#{command.row.marketCode}, /* END IF */ 
       /* IF test=command.row.marketName != null */ market_name=#{command.row.marketName}, /* END IF */ 
       /* IF test=command.row.systemCode != null */ system_code=#{command.row.systemCode}, /* END IF */ 
       /* IF test=command.row.systemName != null */ system_name=#{command.row.systemName}, /* END IF */ 
       /* IF test=command.row.expendCode != null */ expend_code=#{command.row.expendCode}, /* END IF */ 
       /* IF test=command.row.expendName != null */ expend_name=#{command.row.expendName}, /* END IF */ 
       /* IF test=command.row.industryCode != null */ industry_code=#{command.row.industryCode}, /* END IF */ 
       /* IF test=command.row.industryName != null */ industry_name=#{command.row.industryName}, /* END IF */ 
       /* IF test=command.row.lifecycleStatus != null */ lifecycle_status=#{command.row.lifecycleStatus}, /* END IF */ 
       /* IF test=command.row.sourceKey != null */ source_key=#{command.row.sourceKey}, /* END IF */ 
       /* IF test=command.row.sourceVersion != null */ source_version=#{command.row.sourceVersion}, /* END IF */ 
       /* IF test=command.row.dataAsOf != null */ data_as_of=#{command.row.dataAsOf}, /* END IF */ 
      version=version+1 WHERE tenant_id=#{command.row.tenantId} AND id=#{command.row.id} AND version=#{command.expectedVersion} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectCustomerMapper.updateMapping; pms-module-integration/src/main/resources/mapper/sync/DirectCustomerMapper.xml:48; kind=update
UPDATE cus_customer_external_mapping SET source_version=#{command.newVersion}
    WHERE tenant_id=#{command.tenantId} AND id=#{command.mappingId} AND source_system='CRM'
      AND source_version=#{command.expectedVersion} AND effective_to IS NULL AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
