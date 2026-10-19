# P30 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_project_relationship.device_sn | #{deviceSn} | insertProjectRelationship |
| ast_device_project_relationship.project_id | #{projectId} | insertProjectRelationship |
| ast_device_project_relationship.relationship_type | #{relationshipType} | insertProjectRelationship |
| ast_device_project_relationship.effective_from | #{effectiveFrom} | insertProjectRelationship |
| ast_device_project_relationship.effective_to | #{effectiveTo} | insertProjectRelationship |
| ast_device_project_relationship.assignment_version | #{assignmentVersion} | insertProjectRelationship |
| ast_device_project_relationship.reason | #{reason} | insertProjectRelationship |
| ast_device_project_relationship.operation_id | #{operationId} | insertProjectRelationship |
| ast_device_project_relationship.source_system | #{sourceSystem} | insertProjectRelationship |
| ast_device_project_relationship.source_key | #{sourceKey} | insertProjectRelationship |
| ast_device_project_relationship.source_version | #{sourceVersion} | insertProjectRelationship |
| ast_device_project_relationship.version | #{version} | insertProjectRelationship |
| ast_device_project_relationship.tenant_id | #{tenantId} | insertProjectRelationship |

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_device` (`id`, `sn`, `name`, `product_code`, `product_model`, `product_name`, `product_desc`, `shipment_time`, `package_no`, `contract_no`, `shipment_record_id`, `project_id`, `project_assignment_version`, `customer_id`, `customer_assignment_version`, `site_id`, `site_location_id`, `location_resolution_status`, `location_snapshot`, `location_effective_from`, `location_record_id`, `warranty_start_date`, `warranty_end_date`, `warranty_status`, `conp_version`, `conp_type`, `conp_series`, `conp_mark`, `status`, `remark`, `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`, `internal_serial_no`, `secondary_sn`, `secondary_item`, `company_id`, `company_name`, `department_id`, `department_code`, `department_name`, `organization_source`, `organization_updated_at`)
VALUES (:orm_id, :orm_sn, :orm_name, :orm_product_code, :orm_product_model, :orm_product_name, :orm_product_desc, :orm_shipment_time, :orm_package_no, :orm_contract_no, :orm_shipment_record_id, :orm_project_id, :orm_project_assignment_version, :orm_customer_id, :orm_customer_assignment_version, :orm_site_id, :orm_site_location_id, :orm_location_resolution_status, :orm_location_snapshot, :orm_location_effective_from, :orm_location_record_id, :orm_warranty_start_date, :orm_warranty_end_date, :orm_warranty_status, :orm_conp_version, :orm_conp_type, :orm_conp_series, :orm_conp_mark, :orm_status, :orm_remark, :orm_source_system, :orm_source_key, :orm_source_version, :orm_source_updated_at, :orm_synced_at, :orm_sync_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_tenant_id, :orm_internal_serial_no, :orm_secondary_sn, :orm_secondary_item, :orm_company_id, :orm_company_name, :orm_department_id, :orm_department_code, :orm_department_name, :orm_organization_source, :orm_organization_updated_at);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device.id | #{row.id} | insert |
| ast_device.tenant_id | #{row.tenantId} | insert |
| ast_device.version | #{row.version} | insert |
| ast_device.sn | #{row.sn} | insert |
| ast_device.name | #{row.name} | insert |
| ast_device.product_name | #{row.productName} | insert |
| ast_device.product_model | #{row.productModel} | insert |
| ast_device.product_code | #{row.productCode} | insert |
| ast_device.package_no | #{row.packageNo} | insert |
| ast_device.contract_no | #{row.contractNo} | insert |
| ast_device.status | #{row.status} | insert |
| ast_device.source_system | #{row.sourceSystem} | insert |
| ast_device.source_key | #{row.sourceKey} | insert |
| ast_device.source_version | #{row.sourceVersion} | insert |
| ast_device.sync_status | #{row.syncStatus} | insert |
| ast_device.remark | #{row.remark} | insert |
| ast_device.shipment_time | #{row.shipmentTime} | insert |
| ast_device.synced_at | #{row.syncedAt} | insert |
| ast_device.shipment_record_id | #{row.shipmentRecordId} | insert |
| ast_device.project_assignment_version | #{row.projectAssignmentVersion} | insert |
| ast_device.customer_assignment_version | #{row.customerAssignmentVersion} | insert |
| ast_device.creator | #{row.creator} | insert |
| ast_device.create_time | #{row.createTime} | insert |
| ast_device.updater | #{row.updater} | insert |
| ast_device.update_time | #{row.updateTime} | insert |
| ast_device.deleted | #{row.deleted} | insert |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device.id | #{row.target.id} | insertDeviceBase |
| ast_device.tenant_id | #{row.target.tenantId} | insertDeviceBase |
| ast_device.version | #{row.target.version} | insertDeviceBase |
| ast_device.shipment_record_id | #{row.target.shipmentRecordId} | insertDeviceBase |
| ast_device.project_assignment_version | #{row.target.projectAssignmentVersion} | insertDeviceBase |
| ast_device.customer_assignment_version | #{row.target.customerAssignmentVersion} | insertDeviceBase |
| ast_device.sn | #{row.target.sn} | insertDeviceBase |
| ast_device.name | #{row.target.name} | insertDeviceBase |
| ast_device.product_name | #{row.target.productName} | insertDeviceBase |
| ast_device.product_model | #{row.target.productModel} | insertDeviceBase |
| ast_device.product_code | #{row.target.productCode} | insertDeviceBase |
| ast_device.package_no | #{row.target.packageNo} | insertDeviceBase |
| ast_device.contract_no | #{row.target.contractNo} | insertDeviceBase |
| ast_device.status | #{row.target.status} | insertDeviceBase |
| ast_device.source_system | #{row.target.sourceSystem} | insertDeviceBase |
| ast_device.source_key | #{row.target.sourceKey} | insertDeviceBase |
| ast_device.source_version | #{row.target.sourceVersion} | insertDeviceBase |
| ast_device.sync_status | #{row.target.syncStatus} | insertDeviceBase |
| ast_device.remark | #{row.target.remark} | insertDeviceBase |
| ast_device.creator | #{row.target.creator} | insertDeviceBase |
| ast_device.updater | #{row.target.updater} | insertDeviceBase |
| ast_device.shipment_time | #{row.target.shipmentTime} | insertDeviceBase |
| ast_device.synced_at | #{row.target.syncedAt} | insertDeviceBase |
| ast_device.create_time | #{row.target.createTime} | insertDeviceBase |
| ast_device.update_time | #{row.target.updateTime} | insertDeviceBase |
| ast_device.deleted | #{row.target.deleted} | insertDeviceBase |
