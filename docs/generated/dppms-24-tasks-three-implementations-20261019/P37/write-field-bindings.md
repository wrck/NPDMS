# P37 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_device_license_record` (`id`, `tenant_id`, `device_id`, `device_sn`, `contract_id`, `contract_no`, `contract_type`, `product_code`, `product_model`, `license_status`, `license_code_ciphertext`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_device_id, :orm_device_sn, :orm_contract_id, :orm_contract_no, :orm_contract_type, :orm_product_code, :orm_product_model, :orm_license_status, :orm_license_code_ciphertext, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_license_record.id | #{row.id} | insert |
| ast_device_license_record.tenant_id | #{row.tenantId} | insert |
| ast_device_license_record.device_id | #{row.deviceId} | insert |
| ast_device_license_record.device_sn | #{row.deviceSn} | insert |
| ast_device_license_record.contract_id | #{row.contractId} | insert |
| ast_device_license_record.contract_no | #{row.contractNo} | insert |
| ast_device_license_record.contract_type | #{row.contractType} | insert |
| ast_device_license_record.product_code | #{row.productCode} | insert |
| ast_device_license_record.product_model | #{row.productModel} | insert |
| ast_device_license_record.license_status | #{row.licenseStatus} | insert |
| ast_device_license_record.license_code_ciphertext | #{row.licenseCodeCiphertext} | insert |
| ast_device_license_record.creator | #{row.creator} | insert |
| ast_device_license_record.create_time | #{row.createTime} | insert |
| ast_device_license_record.updater | #{row.updater} | insert |
| ast_device_license_record.update_time | #{row.updateTime} | insert |
| ast_device_license_record.deleted | #{row.deleted} | insert |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_license_record.id | #{row.target.id} | insertLicenseHistory |
| ast_device_license_record.tenant_id | #{row.target.tenantId} | insertLicenseHistory |
| ast_device_license_record.device_id | #{row.target.deviceId} | insertLicenseHistory |
| ast_device_license_record.contract_id | #{row.target.contractId} | insertLicenseHistory |
| ast_device_license_record.device_sn | #{row.target.deviceSn} | insertLicenseHistory |
| ast_device_license_record.contract_no | #{row.target.contractNo} | insertLicenseHistory |
| ast_device_license_record.contract_type | #{row.target.contractType} | insertLicenseHistory |
| ast_device_license_record.product_code | #{row.target.productCode} | insertLicenseHistory |
| ast_device_license_record.product_model | #{row.target.productModel} | insertLicenseHistory |
| ast_device_license_record.license_status | #{row.target.licenseStatus} | insertLicenseHistory |
| ast_device_license_record.creator | #{row.target.creator} | insertLicenseHistory |
| ast_device_license_record.updater | #{row.target.updater} | insertLicenseHistory |
| ast_device_license_record.create_time | #{row.target.createTime} | insertLicenseHistory |
| ast_device_license_record.update_time | #{row.target.updateTime} | insertLicenseHistory |
| ast_device_license_record.deleted | #{row.target.deleted} | insertLicenseHistory |
| ast_device_license_record.license_code_ciphertext | #{row.target.licenseCodeCiphertext} | insertLicenseHistory |
