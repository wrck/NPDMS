# P21 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `proj_project_lease_product` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `project_code`, `execution_no`, `product_category`, `product_type`, `product_code`, `product_model`, `product_name`, `quantity`, `memo`, `lease_duration`, `data_source`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :project_code, :execution_no, :product_category, :product_type, :product_code, :product_model, :product_name, :quantity, :memo, :lease_duration, :data_source);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `proj_project_product_configuration` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `project_code`, `execution_no`, `item_group`, `product_code`, `parent_code`, `quantity`, `bom_path`, `product_model`, `product_description`, `level`, `data_source`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :project_code, :execution_no, :item_group, :product_code, :parent_code, :quantity, :bom_path, :product_model, :product_description, :level, :data_source);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `proj_project_lease_product` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `project_code`, `execution_no`, `product_category`, `product_type`, `product_code`, `product_model`, `product_name`, `quantity`, `memo`, `lease_duration`, `data_source`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :project_code, :execution_no, :product_category, :product_type, :product_code, :product_model, :product_name, :quantity, :memo, :lease_duration, :data_source);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `proj_project_product_configuration` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `project_code`, `execution_no`, `item_group`, `product_code`, `parent_code`, `quantity`, `bom_path`, `product_model`, `product_description`, `level`, `data_source`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :project_code, :execution_no, :item_group, :product_code, :parent_code, :quantity, :bom_path, :product_model, :product_description, :level, :data_source);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| proj_project_lease_product.id | #{row.id} | insertLease |
| proj_project_lease_product.tenant_id | #{row.tenantId} | insertLease |
| proj_project_lease_product.project_code | #{row.projectCode} | insertLease |
| proj_project_lease_product.execution_no | #{row.executionNo} | insertLease |
| proj_project_lease_product.product_category | #{row.productCategory} | insertLease |
| proj_project_lease_product.product_type | #{row.productType} | insertLease |
| proj_project_lease_product.product_code | #{row.productCode} | insertLease |
| proj_project_lease_product.product_model | #{row.productModel} | insertLease |
| proj_project_lease_product.product_name | #{row.productName} | insertLease |
| proj_project_lease_product.quantity | #{row.quantity} | insertLease |
| proj_project_lease_product.memo | #{row.memo} | insertLease |
| proj_project_lease_product.lease_duration | #{row.leaseDuration} | insertLease |
| proj_project_lease_product.data_source | #{row.dataSource} | insertLease |
| proj_project_lease_product.creator | #{row.creator} | insertLease |
| proj_project_lease_product.create_time | #{row.createTime} | insertLease |
| proj_project_lease_product.updater | #{row.updater} | insertLease |
| proj_project_lease_product.update_time | #{row.updateTime} | insertLease |
| proj_project_lease_product.deleted | #{row.deleted} | insertLease |
| proj_project_product_configuration.id | #{row.id} | insertConfiguration |
| proj_project_product_configuration.tenant_id | #{row.tenantId} | insertConfiguration |
| proj_project_product_configuration.project_code | #{row.projectCode} | insertConfiguration |
| proj_project_product_configuration.execution_no | #{row.executionNo} | insertConfiguration |
| proj_project_product_configuration.item_group | #{row.itemGroup} | insertConfiguration |
| proj_project_product_configuration.product_code | #{row.productCode} | insertConfiguration |
| proj_project_product_configuration.parent_code | #{row.parentCode} | insertConfiguration |
| proj_project_product_configuration.quantity | #{row.quantity} | insertConfiguration |
| proj_project_product_configuration.bom_path | #{row.bomPath} | insertConfiguration |
| proj_project_product_configuration.product_model | #{row.productModel} | insertConfiguration |
| proj_project_product_configuration.product_description | #{row.productDescription} | insertConfiguration |
| proj_project_product_configuration.level | #{row.level} | insertConfiguration |
| proj_project_product_configuration.data_source | #{row.dataSource} | insertConfiguration |
| proj_project_product_configuration.creator | #{row.creator} | insertConfiguration |
| proj_project_product_configuration.create_time | #{row.createTime} | insertConfiguration |
| proj_project_product_configuration.updater | #{row.updater} | insertConfiguration |
| proj_project_product_configuration.update_time | #{row.updateTime} | insertConfiguration |
| proj_project_product_configuration.deleted | #{row.deleted} | insertConfiguration |
