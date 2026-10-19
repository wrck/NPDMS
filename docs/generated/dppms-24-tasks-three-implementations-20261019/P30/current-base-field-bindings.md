# P30 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectDeviceBase | MIN(device_sn) | device_sn |

| 正式表.列 | 实际写入参数 | INSERT语句 |
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
