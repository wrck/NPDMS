# P11 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectCrmProduct | LTRIM(RTRIM(t.[itemCode])) | product_code |
| selectCrmProduct | t.[itemModel] | product_model |
| selectCrmProduct | t.[itemDesc] | product_name |
| selectCrmProduct | t.[itemDesc] | product_desc |
| selectCrmProduct | t.[remark] | technical_spec |
| selectCrmProduct | t.[productCode] | product_category |
| selectCrmProduct | t.[productSubCode] | product_type |
| selectCrmProduct | t.[listPrice] | list_price |
| selectCrmProduct | t.[status] | crm_status |
| selectCrmProduct | t.[BU] | business_unit |
| selectCrmProduct | t.[productLine] | product_line |
| selectCrmProduct | t.[orgId] | org_id |
| selectCrmProduct | t.[createTime] | source_created_at |
| selectCrmProduct | t.[updateTime] | source_updated_at |
| selectCrmProduct | t.[statecode] | crm_state_code |
| selectCrmProduct | t.[statuscode] | crm_status_code |
| selectCrmProduct | t.[productStage] | product_stage |
| selectCrmProduct | t.[endOfSaleDate] | end_of_sale_date |
| selectCrmProduct | t.[endOfSupportDate] | end_of_support_date |
| selectCrmProduct | t.[endOfLifeDate] | end_of_life_date |
| selectCrmProduct | t.[lastRenewalDate] | last_renewal_date |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| ast_product_official_info.product_code | #{row.productCode} | insertCrmProduct |
| ast_product_official_info.product_model | #{row.productModel} | insertCrmProduct |
| ast_product_official_info.product_name | #{row.productName} | insertCrmProduct |
| ast_product_official_info.product_desc | #{row.productDesc} | insertCrmProduct |
| ast_product_official_info.technical_spec | #{row.technicalSpec} | insertCrmProduct |
| ast_product_official_info.product_category | #{row.productCategory} | insertCrmProduct |
| ast_product_official_info.product_type | #{row.productType} | insertCrmProduct |
| ast_product_official_info.list_price | #{row.listPrice} | insertCrmProduct |
| ast_product_official_info.crm_status | #{row.crmStatus} | insertCrmProduct |
| ast_product_official_info.business_unit | #{row.businessUnit} | insertCrmProduct |
| ast_product_official_info.product_line | #{row.productLine} | insertCrmProduct |
| ast_product_official_info.org_id | #{row.orgId} | insertCrmProduct |
| ast_product_official_info.source_created_at | #{row.sourceCreatedAt} | insertCrmProduct |
| ast_product_official_info.source_updated_at | #{row.sourceUpdatedAt} | insertCrmProduct |
| ast_product_official_info.crm_state_code | #{row.crmStateCode} | insertCrmProduct |
| ast_product_official_info.crm_status_code | #{row.crmStatusCode} | insertCrmProduct |
| ast_product_official_info.product_stage | #{row.productStage} | insertCrmProduct |
| ast_product_official_info.end_of_sale_date | #{row.endOfSaleDate} | insertCrmProduct |
| ast_product_official_info.end_of_support_date | #{row.endOfSupportDate} | insertCrmProduct |
| ast_product_official_info.end_of_life_date | #{row.endOfLifeDate} | insertCrmProduct |
| ast_product_official_info.last_renewal_date | #{row.lastRenewalDate} | insertCrmProduct |
| ast_product_official_info.id | #{row.id} | insertCrmProduct |
| ast_product_official_info.tenant_id | #{context.tenantId} | insertCrmProduct |
| ast_product_official_info.source_system | 'CRM' | insertCrmProduct |
| ast_product_official_info.source_key | #{row.productCode} | insertCrmProduct |
| ast_product_official_info.source_version | #{row.sourceVersion} | insertCrmProduct |
| ast_product_official_info.status | CASE WHEN #{row.crmStateCode}=0 THEN 'ACTIVE' ELSE 'INACTIVE' END | insertCrmProduct |
| ast_product_official_info.version | 0 | insertCrmProduct |
| ast_product_official_info.synced_at | #{context.startedAt} | insertCrmProduct |
| ast_product_official_info.creator | #{context.actorId} | insertCrmProduct |
| ast_product_official_info.updater | #{context.actorId} | insertCrmProduct |
| ast_product_official_info.create_time | #{context.startedAt} | insertCrmProduct |
| ast_product_official_info.update_time | #{context.startedAt} | insertCrmProduct |
| ast_product_official_info.deleted | FALSE | insertCrmProduct |
| ast_product_official_info.sync_status | 'SUCCESS' | insertCrmProduct |
