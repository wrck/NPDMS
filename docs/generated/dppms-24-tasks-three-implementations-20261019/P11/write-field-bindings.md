# P11 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_product_official_info` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `product_code`, `product_model`, `product_name`, `product_desc`, `technical_spec`, `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`, `status`, `product_category`, `product_type`, `list_price`, `crm_status`, `business_unit`, `product_line`, `org_id`, `source_created_at`, `crm_state_code`, `crm_status_code`, `product_stage`, `end_of_sale_date`, `end_of_support_date`, `end_of_life_date`, `last_renewal_date`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :product_code, :product_model, :product_name, :product_desc, :technical_spec, :source_system, :source_key, :source_version, :source_updated_at, :synced_at, :sync_status, :status, :product_category, :product_type, :list_price, :crm_status, :business_unit, :product_line, :org_id, :source_created_at, :crm_state_code, :crm_status_code, :product_stage, :end_of_sale_date, :end_of_support_date, :end_of_life_date, :last_renewal_date);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_product_official_info` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `product_code`, `product_model`, `product_name`, `product_desc`, `technical_spec`, `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`, `status`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :product_code, :product_model, :product_name, :product_desc, :technical_spec, :source_system, :source_key, :source_version, :source_updated_at, :synced_at, :sync_status, :status);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_product_official_info.product_code | #{row.productCode} | insertCrmProduct |
| ast_product_official_info.product_model | #{row.productModel} | insertCrmProduct |
| ast_product_official_info.product_name | #{row.productName} | insertCrmProduct |
| ast_product_official_info.product_desc | #{row.productDesc} | insertCrmProduct |
| ast_product_official_info.technical_spec | #{row.technicalSpec} | insertCrmProduct |
| ast_product_official_info.product_category | #{row.productCategory} | insertCrmProduct |
| ast_product_official_info.product_type | #{row.productType} | insertCrmProduct |
| ast_product_official_info.list_price | #{row.listPrice} | insertCrmProduct |
| ast_product_official_info.crm_status | #{row.crmStatus} | insertCrmProduct |
| ast_product_official_info.business_unit | #{row.businessUnit} | insertCrmProduct |
| ast_product_official_info.product_line | #{row.productLine} | insertCrmProduct |
| ast_product_official_info.org_id | #{row.orgId} | insertCrmProduct |
| ast_product_official_info.source_created_at | #{row.sourceCreatedAt} | insertCrmProduct |
| ast_product_official_info.source_updated_at | #{row.sourceUpdatedAt} | insertCrmProduct |
| ast_product_official_info.crm_state_code | #{row.crmStateCode} | insertCrmProduct |
| ast_product_official_info.crm_status_code | #{row.crmStatusCode} | insertCrmProduct |
| ast_product_official_info.product_stage | #{row.productStage} | insertCrmProduct |
| ast_product_official_info.end_of_sale_date | #{row.endOfSaleDate} | insertCrmProduct |
| ast_product_official_info.end_of_support_date | #{row.endOfSupportDate} | insertCrmProduct |
| ast_product_official_info.end_of_life_date | #{row.endOfLifeDate} | insertCrmProduct |
| ast_product_official_info.last_renewal_date | #{row.lastRenewalDate} | insertCrmProduct |
| ast_product_official_info.id | #{row.id} | insertCrmProduct |
| ast_product_official_info.tenant_id | #{context.tenantId} | insertCrmProduct |
| ast_product_official_info.source_system | 'CRM' | insertCrmProduct |
| ast_product_official_info.source_key | #{row.productCode} | insertCrmProduct |
| ast_product_official_info.source_version | #{row.sourceVersion} | insertCrmProduct |
| ast_product_official_info.status | CASE WHEN #{row.crmStateCode}=0 THEN 'ACTIVE' ELSE 'INACTIVE' END | insertCrmProduct |
| ast_product_official_info.version | 0 | insertCrmProduct |
| ast_product_official_info.synced_at | #{context.startedAt} | insertCrmProduct |
| ast_product_official_info.creator | #{context.actorId} | insertCrmProduct |
| ast_product_official_info.updater | #{context.actorId} | insertCrmProduct |
| ast_product_official_info.create_time | #{context.startedAt} | insertCrmProduct |
| ast_product_official_info.update_time | #{context.startedAt} | insertCrmProduct |
| ast_product_official_info.deleted | FALSE | insertCrmProduct |
| ast_product_official_info.sync_status | 'SUCCESS' | insertCrmProduct |
