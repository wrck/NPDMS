# P33 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `imp_eng_installation` (`id`, `project_id`, `code`, `equipment_id`, `install_location`, `address_id`, `address_version`, `site_id`, `site_version`, `site_location_id`, `site_location_version`, `location_resolution_status`, `address_snapshot`, `location_snapshot`, `effective_from`, `effective_to`, `install_time`, `installer_user_id`, `environment_check`, `spec_check`, `photo_url`, `result`, `status`, `remark`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (:orm_id, :orm_project_id, :orm_code, :orm_equipment_id, :orm_install_location, :orm_address_id, :orm_address_version, :orm_site_id, :orm_site_version, :orm_site_location_id, :orm_site_location_version, :orm_location_resolution_status, :orm_address_snapshot, :orm_location_snapshot, :orm_effective_from, :orm_effective_to, :orm_install_time, :orm_installer_user_id, :orm_environment_check, :orm_spec_check, :orm_photo_url, :orm_result, :orm_status, :orm_remark, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_tenant_id);
```

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_device_location` (`id`, `device_sn`, `site_id`, `site_location_id`, `resolution_status`, `location_snapshot`, `effective_from`, `effective_to`, `installation_id`, `source_system`, `source_key`, `source_version`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (:orm_id, :orm_device_sn, :orm_site_id, :orm_site_location_id, :orm_resolution_status, :orm_location_snapshot, :orm_effective_from, :orm_effective_to, :orm_installation_id, :orm_source_system, :orm_source_key, :orm_source_version, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_tenant_id);
```

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_address` (`id`, `tenant_id`, `country_code`, `country_name`, `province_code`, `province_name`, `city_code`, `city_name`, `district_code`, `district_name`, `detail_address`, `full_address`, `longitude`, `latitude`, `normalized_address`, `address_fingerprint`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_country_code, :orm_country_name, :orm_province_code, :orm_province_name, :orm_city_code, :orm_city_name, :orm_district_code, :orm_district_name, :orm_detail_address, :orm_full_address, :orm_longitude, :orm_latitude, :orm_normalized_address, :orm_address_fingerprint, :orm_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);
```

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_device` (`id`, `sn`, `name`, `product_code`, `product_model`, `product_name`, `product_desc`, `shipment_time`, `package_no`, `contract_no`, `shipment_record_id`, `project_id`, `project_assignment_version`, `customer_id`, `customer_assignment_version`, `site_id`, `site_location_id`, `location_resolution_status`, `location_snapshot`, `location_effective_from`, `location_record_id`, `warranty_start_date`, `warranty_end_date`, `warranty_status`, `conp_version`, `conp_type`, `conp_series`, `conp_mark`, `status`, `remark`, `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`, `internal_serial_no`, `secondary_sn`, `secondary_item`, `company_id`, `company_name`, `department_id`, `department_code`, `department_name`, `organization_source`, `organization_updated_at`)
VALUES (:orm_id, :orm_sn, :orm_name, :orm_product_code, :orm_product_model, :orm_product_name, :orm_product_desc, :orm_shipment_time, :orm_package_no, :orm_contract_no, :orm_shipment_record_id, :orm_project_id, :orm_project_assignment_version, :orm_customer_id, :orm_customer_assignment_version, :orm_site_id, :orm_site_location_id, :orm_location_resolution_status, :orm_location_snapshot, :orm_location_effective_from, :orm_location_record_id, :orm_warranty_start_date, :orm_warranty_end_date, :orm_warranty_status, :orm_conp_version, :orm_conp_type, :orm_conp_series, :orm_conp_mark, :orm_status, :orm_remark, :orm_source_system, :orm_source_key, :orm_source_version, :orm_source_updated_at, :orm_synced_at, :orm_sync_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_tenant_id, :orm_internal_serial_no, :orm_secondary_sn, :orm_secondary_item, :orm_company_id, :orm_company_name, :orm_department_id, :orm_department_code, :orm_department_name, :orm_organization_source, :orm_organization_updated_at);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| imp_eng_installation.id | #{record.id} | insertHistorical |
| imp_eng_installation.tenant_id | #{record.tenantId} | insertHistorical |
| imp_eng_installation.project_id | #{record.projectId} | insertHistorical |
| imp_eng_installation.code | #{record.code} | insertHistorical |
| imp_eng_installation.equipment_id | #{record.equipmentId} | insertHistorical |
| imp_eng_installation.install_location | #{record.installLocation} | insertHistorical |
| imp_eng_installation.address_id | #{record.addressId} | insertHistorical |
| imp_eng_installation.address_version | #{record.addressVersion} | insertHistorical |
| imp_eng_installation.site_id | #{record.siteId} | insertHistorical |
| imp_eng_installation.site_version | #{record.siteVersion} | insertHistorical |
| imp_eng_installation.site_location_id | #{record.siteLocationId} | insertHistorical |
| imp_eng_installation.site_location_version | #{record.siteLocationVersion} | insertHistorical |
| imp_eng_installation.location_resolution_status | #{record.locationResolutionStatus} | insertHistorical |
| imp_eng_installation.address_snapshot | #{record.addressSnapshot} | insertHistorical |
| imp_eng_installation.location_snapshot | #{record.locationSnapshot} | insertHistorical |
| imp_eng_installation.effective_from | #{record.effectiveFrom} | insertHistorical |
| imp_eng_installation.effective_to | #{record.effectiveTo} | insertHistorical |
| imp_eng_installation.install_time | #{record.installTime} | insertHistorical |
| imp_eng_installation.installer_user_id | #{record.installerUserId} | insertHistorical |
| imp_eng_installation.environment_check | #{record.environmentCheck} | insertHistorical |
| imp_eng_installation.spec_check | #{record.specCheck} | insertHistorical |
| imp_eng_installation.photo_url | #{record.photoUrl} | insertHistorical |
| imp_eng_installation.result | #{record.result} | insertHistorical |
| imp_eng_installation.status | #{record.status} | insertHistorical |
| imp_eng_installation.remark | #{record.remark} | insertHistorical |
| imp_eng_installation.version | #{record.version} | insertHistorical |
| imp_eng_installation.creator | #{record.creator} | insertHistorical |
| imp_eng_installation.create_time | #{record.createTime} | insertHistorical |
| imp_eng_installation.updater | #{record.updater} | insertHistorical |
| imp_eng_installation.update_time | #{record.updateTime} | insertHistorical |
| imp_eng_installation.deleted | #{record.deleted} | insertHistorical |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| imp_eng_installation.id | #{row.target.id} | insertInstallation |
| imp_eng_installation.project_id | #{row.target.projectId} | insertInstallation |
| imp_eng_installation.code | #{row.target.code} | insertInstallation |
| imp_eng_installation.equipment_id | #{row.target.equipmentId} | insertInstallation |
| imp_eng_installation.install_location | #{row.target.installLocation} | insertInstallation |
| imp_eng_installation.address_id | #{row.target.addressId} | insertInstallation |
| imp_eng_installation.address_version | #{row.target.addressVersion} | insertInstallation |
| imp_eng_installation.site_id | #{row.target.siteId} | insertInstallation |
| imp_eng_installation.site_version | #{row.target.siteVersion} | insertInstallation |
| imp_eng_installation.site_location_id | #{row.target.siteLocationId} | insertInstallation |
| imp_eng_installation.site_location_version | #{row.target.siteLocationVersion} | insertInstallation |
| imp_eng_installation.location_resolution_status | #{row.target.locationResolutionStatus} | insertInstallation |
| imp_eng_installation.address_snapshot | #{row.target.addressSnapshot} | insertInstallation |
| imp_eng_installation.location_snapshot | #{row.target.locationSnapshot} | insertInstallation |
| imp_eng_installation.effective_from | #{row.target.effectiveFrom} | insertInstallation |
| imp_eng_installation.effective_to | #{row.target.effectiveTo} | insertInstallation |
| imp_eng_installation.install_time | #{row.target.installTime} | insertInstallation |
| imp_eng_installation.installer_user_id | #{row.target.installerUserId} | insertInstallation |
| imp_eng_installation.environment_check | #{row.target.environmentCheck} | insertInstallation |
| imp_eng_installation.spec_check | #{row.target.specCheck} | insertInstallation |
| imp_eng_installation.photo_url | #{row.target.photoUrl} | insertInstallation |
| imp_eng_installation.result | #{row.target.result} | insertInstallation |
| imp_eng_installation.status | #{row.target.status} | insertInstallation |
| imp_eng_installation.remark | #{row.target.remark} | insertInstallation |
| imp_eng_installation.version | #{row.target.version} | insertInstallation |
| imp_eng_installation.creator | #{row.target.creator} | insertInstallation |
| imp_eng_installation.create_time | #{row.target.createTime} | insertInstallation |
| imp_eng_installation.updater | #{row.target.updater} | insertInstallation |
| imp_eng_installation.update_time | #{row.target.updateTime} | insertInstallation |
| imp_eng_installation.deleted | #{row.target.deleted} | insertInstallation |
| imp_eng_installation.tenant_id | #{row.target.tenantId} | insertInstallation |
