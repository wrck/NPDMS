# P14 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_sales_order` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `authority_status`, `source_system`, `company_code`, `order_no`, `source_record_key`, `source_version`, `source_updated_at`, `source_sync_time`, `custom_info`, `order_type`, `sales_type`, `contract_no`, `execution_no`, `customer_code`, `customer_name`, `source_project_name`, `order_comment`, `order_create_time`, `customer_required_time`, `source_lifecycle_status`, `status`, `company_id`, `company_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :authority_status, :source_system, :company_code, :order_no, :source_record_key, :source_version, :source_updated_at, :source_sync_time, :custom_info, :order_type, :sales_type, :contract_no, :execution_no, :customer_code, :customer_name, :source_project_name, :order_comment, :order_create_time, :customer_required_time, :source_lifecycle_status, :status, :company_id, :company_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_sales_order_line` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `unit_scale`, `quantity_status`, `source_system`, `company_code`, `order_no`, `source_record_key`, `source_version`, `source_updated_at`, `source_sync_time`, `custom_info`, `order_type`, `line_type`, `line_no`, `product_code`, `product_desc`, `bundle_code`, `profit_center`, `real_execution_no`, `warranty_month`, `order_qty`, `open_qty`, `delivered_qty`, `source_lifecycle_status`, `status`, `order_id`, `company_id`, `company_name`, `customer_id`, `customer_code`, `customer_name`, `product_id`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :unit_scale, :quantity_status, :source_system, :company_code, :order_no, :source_record_key, :source_version, :source_updated_at, :source_sync_time, :custom_info, :order_type, :line_type, :line_no, :product_code, :product_desc, :bundle_code, :profit_center, :real_execution_no, :warranty_month, :order_qty, :open_qty, :delivered_qty, :source_lifecycle_status, :status, :order_id, :company_id, :company_name, :customer_id, :customer_code, :customer_name, :product_id);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_sales_order` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `authority_status`, `source_system`, `company_code`, `order_no`, `source_record_key`, `source_version`, `source_updated_at`, `source_sync_time`, `custom_info`, `order_type`, `sales_type`, `contract_no`, `execution_no`, `customer_code`, `customer_name`, `source_project_name`, `order_comment`, `order_create_time`, `customer_required_time`, `source_lifecycle_status`, `status`, `company_id`, `company_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :authority_status, :source_system, :company_code, :order_no, :source_record_key, :source_version, :source_updated_at, :source_sync_time, :custom_info, :order_type, :sales_type, :contract_no, :execution_no, :customer_code, :customer_name, :source_project_name, :order_comment, :order_create_time, :customer_required_time, :source_lifecycle_status, :status, :company_id, :company_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_sales_order_line` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `unit_scale`, `quantity_status`, `source_system`, `company_code`, `order_no`, `source_record_key`, `source_version`, `source_updated_at`, `source_sync_time`, `custom_info`, `order_type`, `line_type`, `line_no`, `product_code`, `product_desc`, `bundle_code`, `profit_center`, `real_execution_no`, `warranty_month`, `order_qty`, `open_qty`, `delivered_qty`, `source_lifecycle_status`, `status`, `order_id`, `company_id`, `customer_id`, `company_name`, `customer_code`, `customer_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :unit_scale, :quantity_status, :source_system, :company_code, :order_no, :source_record_key, :source_version, :source_updated_at, :source_sync_time, :custom_info, :order_type, :line_type, :line_no, :product_code, :product_desc, :bundle_code, :profit_center, :real_execution_no, :warranty_month, :order_qty, :open_qty, :delivered_qty, :source_lifecycle_status, :status, :order_id, :company_id, :customer_id, :company_name, :customer_code, :customer_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| com_sales_order.id | #{row.order.id} | insertOrder |
| com_sales_order.tenant_id | #{row.order.tenantId} | insertOrder |
| com_sales_order.source_system | #{row.order.sourceSystem} | insertOrder |
| com_sales_order.source_record_key | #{row.order.sourceRecordKey} | insertOrder |
| com_sales_order.source_version | #{row.order.sourceVersion} | insertOrder |
| com_sales_order.company_id | #{row.order.companyId} | insertOrder |
| com_sales_order.company_code | #{row.order.companyCode} | insertOrder |
| com_sales_order.company_name | #{row.order.companyName} | insertOrder |
| com_sales_order.order_type | #{row.order.orderType} | insertOrder |
| com_sales_order.order_no | #{row.order.orderNo} | insertOrder |
| com_sales_order.sales_type | #{row.order.salesType} | insertOrder |
| com_sales_order.contract_no | #{row.order.contractNo} | insertOrder |
| com_sales_order.execution_no | #{row.order.executionNo} | insertOrder |
| com_sales_order.customer_id | #{row.order.customerId} | insertOrder |
| com_sales_order.customer_code | #{row.order.customerCode} | insertOrder |
| com_sales_order.customer_name | #{row.order.customerName} | insertOrder |
| com_sales_order.source_project_name | #{row.order.sourceProjectName} | insertOrder |
| com_sales_order.order_comment | #{row.order.orderComment} | insertOrder |
| com_sales_order.order_amount | #{row.order.orderAmount} | insertOrder |
| com_sales_order.currency_code | #{row.order.currencyCode} | insertOrder |
| com_sales_order.authority_status | #{row.order.authorityStatus} | insertOrder |
| com_sales_order.source_lifecycle_status | #{row.order.sourceLifecycleStatus} | insertOrder |
| com_sales_order.order_create_time | #{row.order.orderCreateTime} | insertOrder |
| com_sales_order.customer_required_time | #{row.order.customerRequiredTime} | insertOrder |
| com_sales_order.custom_info | #{row.order.customInfo} | insertOrder |
| com_sales_order.source_sync_time | #{row.order.sourceSyncTime} | insertOrder |
| com_sales_order.source_updated_at | #{row.order.sourceUpdatedAt} | insertOrder |
| com_sales_order.status | #{row.order.status} | insertOrder |
| com_sales_order.version | #{row.order.version} | insertOrder |
| com_sales_order.creator | #{row.order.creator} | insertOrder |
| com_sales_order.create_time | #{row.order.createTime} | insertOrder |
| com_sales_order.updater | #{row.order.updater} | insertOrder |
| com_sales_order.update_time | #{row.order.updateTime} | insertOrder |
| com_sales_order.deleted | #{row.order.deleted} | insertOrder |
| com_sales_order_line.id | #{row.line.id} | insertLine |
| com_sales_order_line.tenant_id | #{row.line.tenantId} | insertLine |
| com_sales_order_line.order_id | #{row.line.orderId} | insertLine |
| com_sales_order_line.source_system | #{row.line.sourceSystem} | insertLine |
| com_sales_order_line.source_record_key | #{row.line.sourceRecordKey} | insertLine |
| com_sales_order_line.source_version | #{row.line.sourceVersion} | insertLine |
| com_sales_order_line.company_id | #{row.line.companyId} | insertLine |
| com_sales_order_line.company_code | #{row.line.companyCode} | insertLine |
| com_sales_order_line.company_name | #{row.line.companyName} | insertLine |
| com_sales_order_line.order_type | #{row.line.orderType} | insertLine |
| com_sales_order_line.order_no | #{row.line.orderNo} | insertLine |
| com_sales_order_line.line_no | #{row.line.lineNo} | insertLine |
| com_sales_order_line.line_type | #{row.line.lineType} | insertLine |
| com_sales_order_line.product_code | #{row.line.productCode} | insertLine |
| com_sales_order_line.product_model | #{row.line.productModel} | insertLine |
| com_sales_order_line.product_desc | #{row.line.productDesc} | insertLine |
| com_sales_order_line.custom_info | #{row.line.customInfo} | insertLine |
| com_sales_order_line.product_id | #{row.line.productId} | insertLine |
| com_sales_order_line.bundle_code | #{row.line.bundleCode} | insertLine |
| com_sales_order_line.profit_center | #{row.line.profitCenter} | insertLine |
| com_sales_order_line.real_execution_no | #{row.line.realExecutionNo} | insertLine |
| com_sales_order_line.warranty_month | #{row.line.warrantyMonth} | insertLine |
| com_sales_order_line.customer_id | #{row.line.customerId} | insertLine |
| com_sales_order_line.customer_code | #{row.line.customerCode} | insertLine |
| com_sales_order_line.customer_name | #{row.line.customerName} | insertLine |
| com_sales_order_line.order_qty | #{row.line.orderQty} | insertLine |
| com_sales_order_line.open_qty | #{row.line.openQty} | insertLine |
| com_sales_order_line.delivered_qty | #{row.line.deliveredQty} | insertLine |
| com_sales_order_line.unit_code | #{row.line.unitCode} | insertLine |
| com_sales_order_line.unit_scale | #{row.line.unitScale} | insertLine |
| com_sales_order_line.quantity_status | #{row.line.quantityStatus} | insertLine |
| com_sales_order_line.source_lifecycle_status | #{row.line.sourceLifecycleStatus} | insertLine |
| com_sales_order_line.source_sync_time | #{row.line.sourceSyncTime} | insertLine |
| com_sales_order_line.source_updated_at | #{row.line.sourceUpdatedAt} | insertLine |
| com_sales_order_line.status | #{row.line.status} | insertLine |
| com_sales_order_line.version | #{row.line.version} | insertLine |
| com_sales_order_line.creator | #{row.line.creator} | insertLine |
| com_sales_order_line.create_time | #{row.line.createTime} | insertLine |
| com_sales_order_line.updater | #{row.line.updater} | insertLine |
| com_sales_order_line.update_time | #{row.line.updateTime} | insertLine |
| com_sales_order_line.deleted | #{row.line.deleted} | insertLine |
