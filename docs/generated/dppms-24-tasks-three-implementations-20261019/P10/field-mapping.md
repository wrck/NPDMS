# P10 原表逐字段承载清单

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。ERP列已按单列授权补齐；源59601行中22组客户编码对应44个不同账号且名称不同，另35行缺名称、4行名称超长。需CRM来源Owner纠正或给出已审核的无损承载方案，不能擅自合并、改码或跳过。

合并组件：P10。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| Account.AccountNumber | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.code | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.Name | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.name | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.Telephone1 | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.contact_phone | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.EMailAddress1 | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.contact_email | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.Address1_Composite | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.address | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_mkt_unit_id | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.department_code | 按下方当前映射的STRING处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_mkt_unit_idName | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.department_name | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_industry_level1_id | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.market_code | 按下方当前映射的STRING处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_industry_level1_idName | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.market_name | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_industry_level2_id | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.system_code | 按下方当前映射的STRING处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_industry_level2_idName | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.system_name | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_industry_level3_id | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.expend_code | 按下方当前映射的STRING处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_industry_level3_idName | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.expend_name | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_industry_level4_id | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.industry_code | 按下方当前映射的STRING处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.new_industry_level4_idName | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.industry_name | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.StateCode | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.lifecycle_status | 按下方当前映射的ENUM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.ModifiedOn | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.source_version | 按下方当前映射的STRING处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.ModifiedOn | 未实时核对SQL Server类型 / 未实时核对 | cus_customer_master.data_as_of | 按下方当前映射的DATETIME处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.migrationIssue | 未实时核对SQL Server类型 / 未实时核对 | 来源预检问题，不写业务列 | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P10 |
| Account.AccountId | 未实时核对SQL Server类型 / 未实时核对 | CUS来源身份＋INT来源绑定 | 使用原CRM身份，不能用客户名称替代；不覆盖正式id。；来源身份 | P10 |
