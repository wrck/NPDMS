# P25 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectDuplicateIdentity | 1 | 1 |
| selectWarranty | `id` | source_id |
| selectWarranty | `con_xb` | warranty_contract_no |
| selectWarranty | `barcode` | device_sn |
| selectWarranty | `grade` | warranty_grade |
| selectWarranty | `begin_date` | warranty_start_date |
| selectWarranty | `end_date` | warranty_end_date |
| selectWarranty | `warranty` | warranty_years |
| selectWarranty | `remark` | remark |
| selectWarranty | `isyb` | extended |
| selectWarranty | `state` | is_latest |

| 正式表.列 | 实际写入参数 | INSERT语句 |
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
