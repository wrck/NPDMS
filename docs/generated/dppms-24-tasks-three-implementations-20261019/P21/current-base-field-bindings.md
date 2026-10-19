# P21 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectLease | `projectCode` | project_code |
| selectLease | `orderExecNumber` | execution_no |
| selectLease | `productFirstName` | product_category |
| selectLease | `productName` | product_type |
| selectLease | `productSubCode` | product_code |
| selectLease | `productSubModel` | product_model |
| selectLease | `productSubName` | product_name |
| selectLease | `num` | quantity |
| selectLease | `memo` | memo |
| selectLease | `leaseDuration` | lease_duration |
| selectLease | `dataSource` | data_source |
| selectConfiguration | `projectCode` | project_code |
| selectConfiguration | `orderExecNumber` | execution_no |
| selectConfiguration | `itemGroup` | item_group |
| selectConfiguration | `itemCode` | product_code |
| selectConfiguration | `parentCode` | parent_code |
| selectConfiguration | `quantity` | quantity |
| selectConfiguration | `bomPaths` | bom_path |
| selectConfiguration | `itemModel` | product_model |
| selectConfiguration | `itemDesc` | product_description |
| selectConfiguration | `level` | level |
| selectConfiguration | `dataSource` | data_source |

| 正式表.列 | 实际写入参数 | INSERT语句 |
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
