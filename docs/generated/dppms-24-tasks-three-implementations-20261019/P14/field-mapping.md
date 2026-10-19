# P14 原表逐字段承载清单

合并组件：P14。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_order_data_from_erp.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P14 |
| pm_order_data_from_erp.orderNumber | varchar(25) / YES | com_sales_order.order_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.contractNo | varchar(50) / YES | com_sales_order.contract_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.orderExecNumber | varchar(50) / YES | com_sales_order.execution_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.orderCreateTime | datetime / YES | com_sales_order.order_create_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.customerRequireTime | datetime / YES | com_sales_order.customer_required_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.customerCode | varchar(55) / YES | com_sales_order.customer_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.customerName | varchar(255) / YES | com_sales_order.customer_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.projectName | varchar(255) / YES | com_sales_order.source_project_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.orderComment | varchar(2048) / YES | com_sales_order.order_comment | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.orderType | int(11) / YES | com_sales_order.order_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.compCode | varchar(25) / YES | com_sales_order.company_code；com_sales_order.company_id | 01→001、02→002、03→003、04→004；未知码阻断；按租户＋公司＋系统＋业务键匹配；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.salesType | varchar(25) / YES | com_sales_order.sales_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.source | varchar(25) / YES | com_sales_order.source_system | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.customInfo | json / YES | com_sales_order.custom_info | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_data_from_erp.syncTime | datetime / YES | com_sales_order.source_updated_at | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P14 |
| pm_order_line_from_erp.orderNumber | varchar(25) / YES | com_sales_order_line.order_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.lineNum | varchar(25) / YES | com_sales_order_line.line_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.itemCode | varchar(25) / YES | com_sales_order_line.product_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.itemDesc | varchar(255) / YES | com_sales_order_line.product_desc | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.orderQuantity | int(11) / YES | com_sales_order_line.order_qty | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.openQuantity | int(11) / YES | com_sales_order_line.open_qty | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.bundleCode | varchar(25) / YES | com_sales_order_line.bundle_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.warrantyMonth | int(11) / YES | com_sales_order_line.warranty_month | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.lineType | int(11) / YES | com_sales_order_line.line_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.compCode | varchar(25) / YES | com_sales_order_line.company_code；com_sales_order_line.company_id | 01→001、02→002、03→003、04→004；未知码阻断；按租户＋公司＋系统＋业务键匹配；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.profitCenter | varchar(25) / YES | com_sales_order_line.profit_center | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.realOrderExecNumber | varchar(25) / YES | com_sales_order_line.real_execution_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.source | varchar(25) / YES | com_sales_order_line.source_system | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.customInfo | json / YES | com_sales_order_line.custom_info | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
| pm_order_line_from_erp.syncTime | datetime / YES | com_sales_order_line.source_updated_at | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P14 |
