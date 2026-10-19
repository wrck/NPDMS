# P20 新虚基类实际读写字段衔接

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p20)；本页早期流程描述按该修订解释。

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectRealProduct | l.id | sourceId |
| selectRealProduct | l.projectId | originalProjectId |
| selectRealProduct | canonical.projectId | canonicalProjectId |
| selectRealProduct | p.projectCode | originalProjectCode |
| selectRealProduct | p.projectType | projectType |
| selectRealProduct | l.contractNo | contractNo |
| selectRealProduct | l.projectCode | projectCode |
| selectRealProduct | l.orderExecNumber | executionNo |
| selectRealProduct | l.productFirstName | productCategoryName |
| selectRealProduct | l.productName | productName |
| selectRealProduct | l.productSubCode | productSubCode |
| selectRealProduct | l.productSubModel | productSubModel |
| selectRealProduct | l.productSubName | productSubName |
| selectRealProduct | l.num | quantity |
| selectRealProduct | l.memo | memo |
| selectRealProduct | u.dataSource | dataSource |
| selectRealProduct | COALESCE(u.matchCount,0) | upstreamMatchCount |
| selectRealProduct | u.projectCode | upstreamProjectCode |
| selectRealProduct | u.orderExecNumber | upstreamExecutionNo |
| selectRealProduct | u.productFirstName | upstreamProductCategoryName |
| selectRealProduct | u.productName | upstreamProductName |
| selectRealProduct | u.productSubCode | upstreamProductSubCode |
| selectRealProduct | u.productSubModel | upstreamProductSubModel |
| selectRealProduct | u.productSubName | upstreamProductSubName |
| selectRealProduct | u.num | upstreamQuantity |
| selectRealProduct | u.memo | upstreamMemo |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| proj_project_real_product.id | #{row.target.id} | insertRealProduct |
| proj_project_real_product.tenant_id | #{row.target.tenantId} | insertRealProduct |
| proj_project_real_product.project_id | #{row.target.projectId} | insertRealProduct |
| proj_project_real_product.contract_no | #{row.target.contractNo} | insertRealProduct |
| proj_project_real_product.project_code | #{row.target.projectCode} | insertRealProduct |
| proj_project_real_product.execution_no | #{row.target.executionNo} | insertRealProduct |
| proj_project_real_product.product_category_name | #{row.target.productCategoryName} | insertRealProduct |
| proj_project_real_product.product_name | #{row.target.productName} | insertRealProduct |
| proj_project_real_product.product_sub_code | #{row.target.productSubCode} | insertRealProduct |
| proj_project_real_product.product_sub_model | #{row.target.productSubModel} | insertRealProduct |
| proj_project_real_product.product_sub_name | #{row.target.productSubName} | insertRealProduct |
| proj_project_real_product.quantity | #{row.target.quantity} | insertRealProduct |
| proj_project_real_product.memo | #{row.target.memo} | insertRealProduct |
| proj_project_real_product.data_source | #{row.target.dataSource} | insertRealProduct |
| proj_project_real_product.creator | #{row.target.creator} | insertRealProduct |
| proj_project_real_product.create_time | #{row.target.createTime} | insertRealProduct |
| proj_project_real_product.updater | #{row.target.updater} | insertRealProduct |
| proj_project_real_product.update_time | #{row.target.updateTime} | insertRealProduct |
| proj_project_real_product.deleted | #{row.target.deleted} | insertRealProduct |
