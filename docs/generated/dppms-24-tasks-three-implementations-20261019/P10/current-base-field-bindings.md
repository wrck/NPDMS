# P10 新虚基类实际读写字段衔接

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。ERP列已按单列授权补齐；源59601行中22组客户编码对应44个不同账号且名称不同，另35行缺名称、4行名称超长。需CRM来源Owner纠正或给出已审核的无损承载方案，不能擅自合并、改码或跳过。

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectCustomer | CONVERT(varchar(128),[AccountId]) | source_key |
| selectCustomer | LTRIM(RTRIM([AccountNumber])) | code |
| selectCustomer | LTRIM(RTRIM([erpCustomerCode])) | erp_customer_code |
| selectCustomer | LTRIM(RTRIM([Name])) | name |
| selectCustomer | LTRIM(RTRIM([Telephone1])) | contact_phone |
| selectCustomer | LTRIM(RTRIM([EMailAddress1])) | contact_email |
| selectCustomer | LTRIM(RTRIM([Address1_Composite])) | address |
| selectCustomer | CONVERT(varchar(128),[new_mkt_unit_id]) | department_code |
| selectCustomer | LTRIM(RTRIM([new_mkt_unit_idName])) | department_name |
| selectCustomer | CONVERT(varchar(128),[new_industry_level1_id]) | market_code |
| selectCustomer | LTRIM(RTRIM([new_industry_level1_idName])) | market_name |
| selectCustomer | CONVERT(varchar(128),[new_industry_level2_id]) | system_code |
| selectCustomer | LTRIM(RTRIM([new_industry_level2_idName])) | system_name |
| selectCustomer | CONVERT(varchar(128),[new_industry_level3_id]) | expend_code |
| selectCustomer | LTRIM(RTRIM([new_industry_level3_idName])) | expend_name |
| selectCustomer | CONVERT(varchar(128),[new_industry_level4_id]) | industry_code |
| selectCustomer | LTRIM(RTRIM([new_industry_level4_idName])) | industry_name |
| selectCustomer | CASE StateCode WHEN 0 THEN 'ENABLED' WHEN 1 THEN 'DISABLED' ELSE NULL END | lifecycle_status |
| selectCustomer | CONVERT(varchar(33),ModifiedOn,126) | source_version |
| selectCustomer | ModifiedOn | data_as_of |
| selectCustomer | CASE WHEN Name IS NULL OR LTRIM(RTRIM(Name))='' THEN 'MISSING_CUSTOMER_NAME' WHEN LEN(Name)>128 THEN 'CUSTOMER_NAME_TOO_LONG' WHEN COUNT(*) OVER (PARTITION BY LTRIM(RTRIM(AccountNumber)))>1 THEN 'DUPLICATE_CUSTOMER_CODE' ELSE NULL END | migration_issue |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| cus_customer_master.id | #{row.target.id} | insertCustomer |
| cus_customer_master.tenant_id | #{row.target.tenantId} | insertCustomer |
| cus_customer_master.version | #{row.target.version} | insertCustomer |
| cus_customer_master.code | #{row.target.code} | insertCustomer |
| cus_customer_master.erp_customer_code | #{row.target.erpCustomerCode} | insertCustomer |
| cus_customer_master.name | #{row.target.name} | insertCustomer |
| cus_customer_master.short_name | #{row.target.shortName} | insertCustomer |
| cus_customer_master.customer_level | #{row.target.customerLevel} | insertCustomer |
| cus_customer_master.lifecycle_status | #{row.target.lifecycleStatus} | insertCustomer |
| cus_customer_master.source_type | #{row.target.sourceType} | insertCustomer |
| cus_customer_master.source_key | #{row.target.sourceKey} | insertCustomer |
| cus_customer_master.source_version | #{row.target.sourceVersion} | insertCustomer |
| cus_customer_master.sync_status | #{row.target.syncStatus} | insertCustomer |
| cus_customer_master.temporary_reason | #{row.target.temporaryReason} | insertCustomer |
| cus_customer_master.contact_phone | #{row.target.contactPhone} | insertCustomer |
| cus_customer_master.contact_email | #{row.target.contactEmail} | insertCustomer |
| cus_customer_master.department_code | #{row.target.departmentCode} | insertCustomer |
| cus_customer_master.department_name | #{row.target.departmentName} | insertCustomer |
| cus_customer_master.market_code | #{row.target.marketCode} | insertCustomer |
| cus_customer_master.market_name | #{row.target.marketName} | insertCustomer |
| cus_customer_master.system_code | #{row.target.systemCode} | insertCustomer |
| cus_customer_master.system_name | #{row.target.systemName} | insertCustomer |
| cus_customer_master.expend_code | #{row.target.expendCode} | insertCustomer |
| cus_customer_master.expend_name | #{row.target.expendName} | insertCustomer |
| cus_customer_master.industry_code | #{row.target.industryCode} | insertCustomer |
| cus_customer_master.industry_name | #{row.target.industryName} | insertCustomer |
| cus_customer_master.address | #{row.target.address} | insertCustomer |
| cus_customer_master.remark | #{row.target.remark} | insertCustomer |
| cus_customer_master.creator | #{row.target.creator} | insertCustomer |
| cus_customer_master.updater | #{row.target.updater} | insertCustomer |
| cus_customer_master.data_as_of | #{row.target.dataAsOf} | insertCustomer |
| cus_customer_master.create_time | #{row.target.createTime} | insertCustomer |
| cus_customer_master.update_time | #{row.target.updateTime} | insertCustomer |
| cus_customer_master.reconciliation_pending | #{row.target.reconciliationPending} | insertCustomer |
| cus_customer_master.deleted | #{row.target.deleted} | insertCustomer |
| cus_customer_external_mapping.id | #{row.mapping.id} | insertMappings |
| cus_customer_external_mapping.tenant_id | #{row.mapping.tenantId} | insertMappings |
| cus_customer_external_mapping.customer_id | #{row.mapping.customerId} | insertMappings |
| cus_customer_external_mapping.source_system | #{row.mapping.sourceSystem} | insertMappings |
| cus_customer_external_mapping.source_key | #{row.mapping.sourceKey} | insertMappings |
| cus_customer_external_mapping.source_version | #{row.mapping.sourceVersion} | insertMappings |
| cus_customer_external_mapping.creator | #{row.mapping.creator} | insertMappings |
| cus_customer_external_mapping.updater | #{row.mapping.updater} | insertMappings |
| cus_customer_external_mapping.effective_from | #{row.mapping.effectiveFrom} | insertMappings |
| cus_customer_external_mapping.effective_to | #{row.mapping.effectiveTo} | insertMappings |
| cus_customer_external_mapping.create_time | #{row.mapping.createTime} | insertMappings |
| cus_customer_external_mapping.update_time | #{row.mapping.updateTime} | insertMappings |
| cus_customer_external_mapping.deleted | #{row.mapping.deleted} | insertMappings |
