# P12 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectSupplementProduct | item | product_code |
| selectSupplementProduct | itemname | product_model |
| selectSupplementProduct | describe_ | product_name |
| selectSupplementProduct | describe_ | product_desc |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| ast_product_official_info.product_code | #{row.productCode} | insertSupplementProduct |
| ast_product_official_info.product_model | #{row.productModel} | insertSupplementProduct |
| ast_product_official_info.product_name | #{row.productName} | insertSupplementProduct |
| ast_product_official_info.product_desc | #{row.productDesc} | insertSupplementProduct |
| ast_product_official_info.id | #{row.id} | insertSupplementProduct |
| ast_product_official_info.tenant_id | #{row.tenantId} | insertSupplementProduct |
| ast_product_official_info.source_system | #{row.sourceSystem} | insertSupplementProduct |
| ast_product_official_info.source_key | #{row.sourceKey} | insertSupplementProduct |
| ast_product_official_info.source_version | #{row.sourceVersion} | insertSupplementProduct |
| ast_product_official_info.status | #{row.status} | insertSupplementProduct |
| ast_product_official_info.version | #{row.version} | insertSupplementProduct |
| ast_product_official_info.synced_at | #{row.syncedAt} | insertSupplementProduct |
| ast_product_official_info.creator | #{row.creator} | insertSupplementProduct |
| ast_product_official_info.updater | #{row.updater} | insertSupplementProduct |
| ast_product_official_info.create_time | #{row.createTime} | insertSupplementProduct |
| ast_product_official_info.update_time | #{row.updateTime} | insertSupplementProduct |
| ast_product_official_info.deleted | #{row.deleted} | insertSupplementProduct |
| ast_product_official_info.sync_status | 'SUCCESS' | insertSupplementProduct |
