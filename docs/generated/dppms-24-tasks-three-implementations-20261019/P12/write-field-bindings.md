# P12 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_product_official_info` (`id`, `product_code`, `product_model`, `product_name`, `product_desc`, `technical_spec`, `document_id`, `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (:orm_id, :orm_product_code, :orm_product_model, :orm_product_name, :orm_product_desc, :orm_technical_spec, :orm_document_id, :orm_source_system, :orm_source_key, :orm_source_version, :orm_source_updated_at, :orm_synced_at, :orm_sync_status, :orm_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_tenant_id);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_product_official_info.id | #{row.id} | insertProduct |
| ast_product_official_info.tenant_id | #{row.tenantId} | insertProduct |
| ast_product_official_info.product_code | #{row.productCode} | insertProduct |
| ast_product_official_info.product_name | #{row.productName} | insertProduct |
| ast_product_official_info.product_desc | #{row.productDesc} | insertProduct |
| ast_product_official_info.product_model | #{row.productModel} | insertProduct |
| ast_product_official_info.status | #{row.status} | insertProduct |
| ast_product_official_info.version | #{row.version} | insertProduct |
| ast_product_official_info.source_system | #{row.sourceSystem} | insertProduct |
| ast_product_official_info.source_key | #{row.sourceKey} | insertProduct |
| ast_product_official_info.sync_status | #{row.syncStatus} | insertProduct |
| ast_product_official_info.synced_at | #{row.syncedAt} | insertProduct |
| ast_product_official_info.creator | #{row.creator} | insertProduct |
| ast_product_official_info.updater | #{row.updater} | insertProduct |
| ast_product_official_info.deleted | FALSE | insertProduct |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_product_official_info.product_code | #{row.productCode} | insertSupplementProduct |
| ast_product_official_info.product_model | #{row.productModel} | insertSupplementProduct |
| ast_product_official_info.product_name | #{row.productName} | insertSupplementProduct |
| ast_product_official_info.product_desc | #{row.productDesc} | insertSupplementProduct |
| ast_product_official_info.id | #{row.id} | insertSupplementProduct |
| ast_product_official_info.tenant_id | #{row.tenantId} | insertSupplementProduct |
| ast_product_official_info.source_system | #{row.sourceSystem} | insertSupplementProduct |
| ast_product_official_info.source_key | #{row.sourceKey} | insertSupplementProduct |
| ast_product_official_info.source_version | #{row.sourceVersion} | insertSupplementProduct |
| ast_product_official_info.status | #{row.status} | insertSupplementProduct |
| ast_product_official_info.version | #{row.version} | insertSupplementProduct |
| ast_product_official_info.synced_at | #{row.syncedAt} | insertSupplementProduct |
| ast_product_official_info.creator | #{row.creator} | insertSupplementProduct |
| ast_product_official_info.updater | #{row.updater} | insertSupplementProduct |
| ast_product_official_info.create_time | #{row.createTime} | insertSupplementProduct |
| ast_product_official_info.update_time | #{row.updateTime} | insertSupplementProduct |
| ast_product_official_info.deleted | #{row.deleted} | insertSupplementProduct |
| ast_product_official_info.sync_status | 'SUCCESS' | insertSupplementProduct |
