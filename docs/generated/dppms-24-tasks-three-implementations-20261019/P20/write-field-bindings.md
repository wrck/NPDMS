# P20 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `proj_project_real_product` (`id`, `tenant_id`, `project_id`, `contract_no`, `project_code`, `execution_no`, `product_category_name`, `product_name`, `product_sub_code`, `product_sub_model`, `product_sub_name`, `quantity`, `memo`, `data_source`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_project_id, :orm_contract_no, :orm_project_code, :orm_execution_no, :orm_product_category_name, :orm_product_name, :orm_product_sub_code, :orm_product_sub_model, :orm_product_sub_name, :orm_quantity, :orm_memo, :orm_data_source, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| proj_project_real_product.id | #{row.id} | insert |
| proj_project_real_product.tenant_id | #{row.tenantId} | insert |
| proj_project_real_product.project_id | #{row.projectId} | insert |
| proj_project_real_product.contract_no | #{row.contractNo} | insert |
| proj_project_real_product.project_code | #{row.projectCode} | insert |
| proj_project_real_product.execution_no | #{row.executionNo} | insert |
| proj_project_real_product.product_category_name | #{row.productCategoryName} | insert |
| proj_project_real_product.product_name | #{row.productName} | insert |
| proj_project_real_product.product_sub_code | #{row.productSubCode} | insert |
| proj_project_real_product.product_sub_model | #{row.productSubModel} | insert |
| proj_project_real_product.product_sub_name | #{row.productSubName} | insert |
| proj_project_real_product.quantity | #{row.quantity} | insert |
| proj_project_real_product.memo | #{row.memo} | insert |
| proj_project_real_product.data_source | #{row.dataSource} | insert |
| proj_project_real_product.creator | #{row.creator} | insert |
| proj_project_real_product.create_time | #{row.createTime} | insert |
| proj_project_real_product.updater | #{row.updater} | insert |
| proj_project_real_product.update_time | #{row.updateTime} | insert |
| proj_project_real_product.deleted | #{row.deleted} | insert |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| proj_project_real_product.id | #{row.target.id} | insertRealProduct |
| proj_project_real_product.tenant_id | #{row.target.tenantId} | insertRealProduct |
| proj_project_real_product.project_id | #{row.target.projectId} | insertRealProduct |
| proj_project_real_product.contract_no | #{row.target.contractNo} | insertRealProduct |
| proj_project_real_product.project_code | #{row.target.projectCode} | insertRealProduct |
| proj_project_real_product.execution_no | #{row.target.executionNo} | insertRealProduct |
| proj_project_real_product.product_category_name | #{row.target.productCategoryName} | insertRealProduct |
| proj_project_real_product.product_name | #{row.target.productName} | insertRealProduct |
| proj_project_real_product.product_sub_code | #{row.target.productSubCode} | insertRealProduct |
| proj_project_real_product.product_sub_model | #{row.target.productSubModel} | insertRealProduct |
| proj_project_real_product.product_sub_name | #{row.target.productSubName} | insertRealProduct |
| proj_project_real_product.quantity | #{row.target.quantity} | insertRealProduct |
| proj_project_real_product.memo | #{row.target.memo} | insertRealProduct |
| proj_project_real_product.data_source | #{row.target.dataSource} | insertRealProduct |
| proj_project_real_product.creator | #{row.target.creator} | insertRealProduct |
| proj_project_real_product.create_time | #{row.target.createTime} | insertRealProduct |
| proj_project_real_product.updater | #{row.target.updater} | insertRealProduct |
| proj_project_real_product.update_time | #{row.target.updateTime} | insertRealProduct |
| proj_project_real_product.deleted | #{row.target.deleted} | insertRealProduct |
