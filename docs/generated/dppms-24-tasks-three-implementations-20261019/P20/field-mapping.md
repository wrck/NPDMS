# P20 原表逐字段承载清单

合并组件：P20。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_product_line_real.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P20 |
| pm_project_product_line_real.projectId | int(11) / NO | proj_project_real_product.project_id | 通过P17或本任务已批准的PROJ原项目身份解析正式project_id；不将原数值直接落正式外键。 通过项目绑定精确关联；不以不同的商务projectCode覆盖主档编码；业务字段/正式关系 | P20 |
| pm_project_product_line_real.contractNo | varchar(25) / NO | proj_project_real_product.contract_no | 清单合同号原文及空串保持；业务字段/正式关系 | P20 |
| pm_project_product_line_real.projectCode | varchar(255) / NO | proj_project_real_product.project_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P20 |
| pm_project_product_line_real.orderExecNumber | varchar(255) / NO | proj_project_real_product.execution_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P20 |
| pm_project_product_line_real.productFirstName | varchar(255) / YES | proj_project_real_product.product_category_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P20 |
| pm_project_product_line_real.productName | varchar(128) / YES | proj_project_real_product.product_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P20 |
| pm_project_product_line_real.productSubCode | varchar(255) / NO | proj_project_real_product.product_sub_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P20 |
| pm_project_product_line_real.productSubModel | varchar(255) / NO | proj_project_real_product.product_sub_model | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P20 |
| pm_project_product_line_real.productSubName | varchar(255) / NO | proj_project_real_product.product_sub_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P20 |
| pm_project_product_line_real.num | int(11) / YES | proj_project_real_product.quantity | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P20 |
| pm_project_product_line_real.memo | mediumtext / YES | proj_project_real_product.memo | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P20 |
