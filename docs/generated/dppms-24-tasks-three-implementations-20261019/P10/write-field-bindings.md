# P10 三种实现的正式写入字段

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。ERP列已按单列授权补齐；源59601行中22组客户编码对应44个不同账号且名称不同，另35行缺名称、4行名称超长。需CRM来源Owner纠正或给出已审核的无损承载方案，不能擅自合并、改码或跳过。

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `cus_customer_master` (`id`, `code`, `name`, `short_name`, `customer_level`, `lifecycle_status`, `source_type`, `source_key`, `source_version`, `sync_status`, `data_as_of`, `reconciliation_pending`, `temporary_reason`, `contact_phone`, `contact_email`, `department_code`, `department_name`, `market_code`, `market_name`, `system_code`, `system_name`, `expend_code`, `expend_name`, `industry_code`, `industry_name`, `address`, `remark`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (:orm_id, :orm_code, :orm_name, :orm_short_name, :orm_customer_level, :orm_lifecycle_status, :orm_source_type, :orm_source_key, :orm_source_version, :orm_sync_status, :orm_data_as_of, :orm_reconciliation_pending, :orm_temporary_reason, :orm_contact_phone, :orm_contact_email, :orm_department_code, :orm_department_name, :orm_market_code, :orm_market_name, :orm_system_code, :orm_system_name, :orm_expend_code, :orm_expend_name, :orm_industry_code, :orm_industry_name, :orm_address, :orm_remark, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_tenant_id);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| cus_customer_master.code | #{row.code} | insertCustomer |
| cus_customer_master.erp_customer_code | #{row.erpCustomerCode} | insertCustomer |
| cus_customer_master.name | #{row.name} | insertCustomer |
| cus_customer_master.short_name | #{row.shortName} | insertCustomer |
| cus_customer_master.contact_phone | #{row.contactPhone} | insertCustomer |
| cus_customer_master.contact_email | #{row.contactEmail} | insertCustomer |
| cus_customer_master.address | #{row.address} | insertCustomer |
| cus_customer_master.department_code | #{row.departmentCode} | insertCustomer |
| cus_customer_master.department_name | #{row.departmentName} | insertCustomer |
| cus_customer_master.market_code | #{row.marketCode} | insertCustomer |
| cus_customer_master.market_name | #{row.marketName} | insertCustomer |
| cus_customer_master.system_code | #{row.systemCode} | insertCustomer |
| cus_customer_master.system_name | #{row.systemName} | insertCustomer |
| cus_customer_master.expend_code | #{row.expendCode} | insertCustomer |
| cus_customer_master.expend_name | #{row.expendName} | insertCustomer |
| cus_customer_master.industry_code | #{row.industryCode} | insertCustomer |
| cus_customer_master.industry_name | #{row.industryName} | insertCustomer |
| cus_customer_master.lifecycle_status | #{row.lifecycleStatus} | insertCustomer |
| cus_customer_master.source_key | #{row.sourceKey} | insertCustomer |
| cus_customer_master.source_version | #{row.sourceVersion} | insertCustomer |
| cus_customer_master.data_as_of | #{row.dataAsOf} | insertCustomer |
| cus_customer_master.source_type | #{row.sourceType} | insertCustomer |
| cus_customer_master.sync_status | #{row.syncStatus} | insertCustomer |
| cus_customer_master.reconciliation_pending | #{row.reconciliationPending} | insertCustomer |
| cus_customer_master.version | 0 | insertCustomer |
| cus_customer_master.tenant_id | #{row.tenantId} | insertCustomer |
| cus_customer_master.deleted | FALSE | insertCustomer |
| cus_customer_external_mapping.customer_id | #{row.customerId} | insertMapping |
| cus_customer_external_mapping.source_system | #{row.sourceSystem} | insertMapping |
| cus_customer_external_mapping.source_key | #{row.sourceKey} | insertMapping |
| cus_customer_external_mapping.source_version | #{row.sourceVersion} | insertMapping |
| cus_customer_external_mapping.effective_from | #{row.effectiveFrom} | insertMapping |
| cus_customer_external_mapping.tenant_id | #{row.tenantId} | insertMapping |
| cus_customer_external_mapping.deleted | FALSE | insertMapping |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
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
