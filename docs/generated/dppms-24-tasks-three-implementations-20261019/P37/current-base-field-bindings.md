# P37 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectLicenseHistory | id | source_id |
| selectLicenseHistory | sn | sn |
| selectLicenseHistory | specModel | spec_model |
| selectLicenseHistory | `contract` | contract |
| selectLicenseHistory | contractType | contract_type |
| selectLicenseHistory | item | item |
| selectLicenseHistory | status | status |
| selectLicenseHistory | licenseCode | license_code |

| 正式表.列 | 实际写入参数 | INSERT语句 |
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
