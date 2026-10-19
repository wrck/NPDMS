-- 当前实现优先见 ../base-sync-department-code-preview-20261019.sql 与 .md；本文件以下为早期冻结SQL。
-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P10 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectCustomer] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P10SourceMapper.xml
SELECT TOP (#{limit}) CONVERT(varchar(128),[AccountId]) AS [source_key],
LTRIM(RTRIM([AccountNumber])) AS [code],
LTRIM(RTRIM([erpCustomerCode])) AS [erp_customer_code],
LTRIM(RTRIM([Name])) AS [name],
LTRIM(RTRIM([Telephone1])) AS [contact_phone],
LTRIM(RTRIM([EMailAddress1])) AS [contact_email],
LTRIM(RTRIM([Address1_Composite])) AS [address],
CONVERT(varchar(128),[new_mkt_unit_id]) AS [department_code],
LTRIM(RTRIM([new_mkt_unit_idName])) AS [department_name],
CONVERT(varchar(128),[new_industry_level1_id]) AS [market_code],
LTRIM(RTRIM([new_industry_level1_idName])) AS [market_name],
CONVERT(varchar(128),[new_industry_level2_id]) AS [system_code],
LTRIM(RTRIM([new_industry_level2_idName])) AS [system_name],
CONVERT(varchar(128),[new_industry_level3_id]) AS [expend_code],
LTRIM(RTRIM([new_industry_level3_idName])) AS [expend_name],
CONVERT(varchar(128),[new_industry_level4_id]) AS [industry_code],
LTRIM(RTRIM([new_industry_level4_idName])) AS [industry_name],
CASE StateCode WHEN 0 THEN 'ENABLED' WHEN 1 THEN 'DISABLED' ELSE NULL END AS lifecycle_status, CONVERT(varchar(33),ModifiedOn,126) AS source_version, ModifiedOn AS data_as_of, CASE WHEN Name IS NULL OR LTRIM(RTRIM(Name))='' THEN 'MISSING_CUSTOMER_NAME' WHEN LEN(Name)>128 THEN 'CUSTOMER_NAME_TOO_LONG' WHEN COUNT(*) OVER (PARTITION BY LTRIM(RTRIM(AccountNumber)))>1 THEN 'DUPLICATE_CUSTOMER_CODE' ELSE NULL END AS migration_issue FROM DPtech_v_account ORDER BY AccountId;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P10CrmCustomerSyncTask$TargetMapper.erpColumnReady; pms-module-integration/src/main/resources/mapper/basesync/P10TargetMapper.xml:4; kind=select
SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='cus_customer_master' AND column_name='erp_customer_code';

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P10CrmCustomerSyncTask$TargetMapper.selectMappings; pms-module-integration/src/main/resources/mapper/basesync/P10TargetMapper.xml:5; kind=select
SELECT id,tenant_id,customer_id,source_system,source_key,source_version,creator,updater,effective_from,effective_to,create_time,update_time,deleted FROM cus_customer_external_mapping WHERE tenant_id=#{query.tenantId} AND source_system='CRM' AND deleted=FALSE AND effective_to IS NULL  /* CHOOSE  */ /* WHEN test=query.keys != null and !query.keys.isEmpty() */ AND source_key IN  /* FOREACH collection=query.keys; item=key; open=(; close=); separator=, */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P10CrmCustomerSyncTask$TargetMapper.selectCustomers; pms-module-integration/src/main/resources/mapper/basesync/P10TargetMapper.xml:6; kind=select
SELECT id,tenant_id,version,code,erp_customer_code,name,short_name,customer_level,lifecycle_status,source_type,source_key,source_version,sync_status,temporary_reason,contact_phone,contact_email,department_code,department_name,market_code,market_name,system_code,system_name,expend_code,expend_name,industry_code,industry_name,address,remark,creator,updater,data_as_of,create_time,update_time,reconciliation_pending,deleted FROM cus_customer_master WHERE tenant_id=#{query.tenantId} AND (1=0  /* IF test=query.ids != null and !query.ids.isEmpty() */ OR id IN  /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END IF */  /* IF test=query.codes != null and !query.codes.isEmpty() */ OR code IN  /* FOREACH collection=query.codes; item=code; open=(; close=); separator=, */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ ) /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P10CrmCustomerSyncTask$TargetMapper.insertCustomer; pms-module-integration/src/main/resources/mapper/basesync/P10TargetMapper.xml:7; kind=insert
INSERT INTO cus_customer_master (`id`,`tenant_id`,`version`,`code`,`erp_customer_code`,`name`,`short_name`,`customer_level`,`lifecycle_status`,`source_type`,`source_key`,`source_version`,`sync_status`,`temporary_reason`,`contact_phone`,`contact_email`,`department_code`,`department_name`,`market_code`,`market_name`,`system_code`,`system_name`,`expend_code`,`expend_name`,`industry_code`,`industry_name`,`address`,`remark`,`creator`,`updater`,`data_as_of`,`create_time`,`update_time`,`reconciliation_pending`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.version},#{row.target.code},#{row.target.erpCustomerCode},#{row.target.name},#{row.target.shortName},#{row.target.customerLevel},#{row.target.lifecycleStatus},#{row.target.sourceType},#{row.target.sourceKey},#{row.target.sourceVersion},#{row.target.syncStatus},#{row.target.temporaryReason},#{row.target.contactPhone},#{row.target.contactEmail},#{row.target.departmentCode},#{row.target.departmentName},#{row.target.marketCode},#{row.target.marketName},#{row.target.systemCode},#{row.target.systemName},#{row.target.expendCode},#{row.target.expendName},#{row.target.industryCode},#{row.target.industryName},#{row.target.address},#{row.target.remark},#{row.target.creator},#{row.target.updater},#{row.target.dataAsOf},#{row.target.createTime},#{row.target.updateTime},#{row.target.reconciliationPending},#{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P10CrmCustomerSyncTask$TargetMapper.insertMappings; pms-module-integration/src/main/resources/mapper/basesync/P10TargetMapper.xml:8; kind=insert
INSERT INTO cus_customer_external_mapping (`id`,`tenant_id`,`customer_id`,`source_system`,`source_key`,`source_version`,`creator`,`updater`,`effective_from`,`effective_to`,`create_time`,`update_time`,`deleted`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.mapping.id},#{row.mapping.tenantId},#{row.mapping.customerId},#{row.mapping.sourceSystem},#{row.mapping.sourceKey},#{row.mapping.sourceVersion},#{row.mapping.creator},#{row.mapping.updater},#{row.mapping.effectiveFrom},#{row.mapping.effectiveTo},#{row.mapping.createTime},#{row.mapping.updateTime},#{row.mapping.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P10CrmCustomerSyncTask$TargetMapper.updateCustomer; pms-module-integration/src/main/resources/mapper/basesync/P10TargetMapper.xml:9; kind=update
UPDATE cus_customer_master SET `code`=#{row.target.code},`erp_customer_code`=#{row.target.erpCustomerCode},`name`=#{row.target.name},`short_name`=#{row.target.shortName},`contact_phone`=#{row.target.contactPhone},`contact_email`=#{row.target.contactEmail},`address`=#{row.target.address},`department_code`=#{row.target.departmentCode},`department_name`=#{row.target.departmentName},`market_code`=#{row.target.marketCode},`market_name`=#{row.target.marketName},`system_code`=#{row.target.systemCode},`system_name`=#{row.target.systemName},`expend_code`=#{row.target.expendCode},`expend_name`=#{row.target.expendName},`industry_code`=#{row.target.industryCode},`industry_name`=#{row.target.industryName},`lifecycle_status`=#{row.target.lifecycleStatus},`source_key`=#{row.target.sourceKey},`source_version`=#{row.target.sourceVersion},`data_as_of`=#{row.target.dataAsOf},`version`=#{row.target.version},`updater`=#{row.target.updater},`update_time`=#{row.target.updateTime} WHERE tenant_id=#{row.target.tenantId} AND id=#{row.target.id} AND version=#{row.expectedVersion} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P10CrmCustomerSyncTask$TargetMapper.updateMapping; pms-module-integration/src/main/resources/mapper/basesync/P10TargetMapper.xml:10; kind=update
UPDATE cus_customer_external_mapping SET source_version=#{row.mapping.sourceVersion},updater=#{row.mapping.updater},update_time=#{row.mapping.updateTime} WHERE tenant_id=#{row.mapping.tenantId} AND id=#{row.mapping.id} AND customer_id=#{row.mapping.customerId} AND source_key=#{row.mapping.sourceKey} AND source_system='CRM' AND source_version=#{row.expectedSourceVersion} AND effective_to IS NULL AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
