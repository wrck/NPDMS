# P25 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_warranty_renewal_record.id | #{row.id} | insertRecord |
| ast_device_warranty_renewal_record.tenant_id | #{row.tenantId} | insertRecord |
| ast_device_warranty_renewal_record.device_sn | #{row.deviceSn} | insertRecord |
| ast_device_warranty_renewal_record.warranty_contract_no | #{row.warrantyContractNo} | insertRecord |
| ast_device_warranty_renewal_record.warranty_grade | #{row.warrantyGrade} | insertRecord |
| ast_device_warranty_renewal_record.warranty_start_date | #{row.warrantyStartDate} | insertRecord |
| ast_device_warranty_renewal_record.warranty_end_date | #{row.warrantyEndDate} | insertRecord |
| ast_device_warranty_renewal_record.warranty_months | #{row.warrantyMonths} | insertRecord |
| ast_device_warranty_renewal_record.extended | #{row.extended} | insertRecord |
| ast_device_warranty_renewal_record.is_latest | #{row.isLatest} | insertRecord |
| ast_device_warranty_renewal_record.remark | #{row.remark} | insertRecord |
| ast_device_warranty_renewal_record.version | #{row.version} | insertRecord |
| ast_device_warranty_renewal_record.creator | #{row.creator} | insertRecord |
| ast_device_warranty_renewal_record.create_time | #{row.createTime} | insertRecord |
| ast_device_warranty_renewal_record.updater | #{row.updater} | insertRecord |
| ast_device_warranty_renewal_record.update_time | #{row.updateTime} | insertRecord |
| ast_device_warranty_renewal_record.deleted | #{row.deleted} | insertRecord |
## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_warranty_renewal_record.id | #{row.id} | insertRecord |
| ast_device_warranty_renewal_record.tenant_id | #{row.tenantId} | insertRecord |
| ast_device_warranty_renewal_record.device_sn | #{row.deviceSn} | insertRecord |
| ast_device_warranty_renewal_record.warranty_contract_no | #{row.warrantyContractNo} | insertRecord |
| ast_device_warranty_renewal_record.warranty_grade | #{row.warrantyGrade} | insertRecord |
| ast_device_warranty_renewal_record.warranty_start_date | #{row.warrantyStartDate} | insertRecord |
| ast_device_warranty_renewal_record.warranty_end_date | #{row.warrantyEndDate} | insertRecord |
| ast_device_warranty_renewal_record.warranty_months | #{row.warrantyMonths} | insertRecord |
| ast_device_warranty_renewal_record.extended | #{row.extended} | insertRecord |
| ast_device_warranty_renewal_record.is_latest | #{row.isLatest} | insertRecord |
| ast_device_warranty_renewal_record.remark | #{row.remark} | insertRecord |
| ast_device_warranty_renewal_record.version | #{row.version} | insertRecord |
| ast_device_warranty_renewal_record.creator | #{row.creator} | insertRecord |
| ast_device_warranty_renewal_record.create_time | #{row.createTime} | insertRecord |
| ast_device_warranty_renewal_record.updater | #{row.updater} | insertRecord |
| ast_device_warranty_renewal_record.update_time | #{row.updateTime} | insertRecord |
| ast_device_warranty_renewal_record.deleted | #{row.deleted} | insertRecord |
## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| ast_device_warranty_renewal_record.id | #{row.target.id} | insertWarranty |
| ast_device_warranty_renewal_record.tenant_id | #{row.target.tenantId} | insertWarranty |
| ast_device_warranty_renewal_record.device_sn | #{row.target.deviceSn} | insertWarranty |
| ast_device_warranty_renewal_record.warranty_contract_no | #{row.target.warrantyContractNo} | insertWarranty |
| ast_device_warranty_renewal_record.warranty_grade | #{row.target.warrantyGrade} | insertWarranty |
| ast_device_warranty_renewal_record.warranty_start_date | #{row.target.warrantyStartDate} | insertWarranty |
| ast_device_warranty_renewal_record.warranty_end_date | #{row.target.warrantyEndDate} | insertWarranty |
| ast_device_warranty_renewal_record.warranty_months | #{row.target.warrantyMonths} | insertWarranty |
| ast_device_warranty_renewal_record.extended | #{row.target.extended} | insertWarranty |
| ast_device_warranty_renewal_record.is_latest | #{row.target.isLatest} | insertWarranty |
| ast_device_warranty_renewal_record.remark | #{row.target.remark} | insertWarranty |
| ast_device_warranty_renewal_record.version | #{row.target.version} | insertWarranty |
| ast_device_warranty_renewal_record.creator | #{row.target.creator} | insertWarranty |
| ast_device_warranty_renewal_record.create_time | #{row.target.createTime} | insertWarranty |
| ast_device_warranty_renewal_record.updater | #{row.target.updater} | insertWarranty |
| ast_device_warranty_renewal_record.update_time | #{row.target.updateTime} | insertWarranty |
| ast_device_warranty_renewal_record.deleted | #{row.target.deleted} | insertWarranty |
