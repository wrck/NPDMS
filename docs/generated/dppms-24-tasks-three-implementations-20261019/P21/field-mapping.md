# P21 原表逐字段承载清单

合并组件：P21。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_product_lease_line_from_crm.projectCode | varchar(255) / NO | proj_project_lease_product.project_code ＋ project_id关联依据 | 旧查询用主码首段，共享首段不能自行指派项目；需精确规范项目绑定，不仅迁页面最大执行单；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.orderExecNumber | varchar(255) / YES | proj_project_lease_product.execution_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.productFirstName | varchar(255) / NO | proj_project_lease_product.product_category | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.productName | varchar(128) / NO | proj_project_lease_product.product_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.productSubCode | varchar(255) / NO | proj_project_lease_product.product_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.productSubModel | varchar(255) / YES | proj_project_lease_product.product_model | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.productSubName | varchar(255) / YES | proj_project_lease_product.product_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.num | int(11) / NO | proj_project_lease_product.quantity | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.memo | mediumtext / YES | proj_project_lease_product.memo | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.leaseDuration | decimal(16,2) / YES | proj_project_lease_product.lease_duration | DECIMAL(16,2)，单位租赁月数；不截成整数或按天；业务字段/正式关系 | P21 |
| pm_project_product_lease_line_from_crm.dataSource | varchar(25) / YES | proj_project_lease_product.data_source | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.projectCode | varchar(100) / YES | proj_project_product_configuration.project_code ＋ project_id关联依据 | 旧查询用主码首段，共享首段不能自行指派项目；需精确规范项目绑定，不仅迁页面最大执行单；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.orderExecNumber | varchar(255) / YES | proj_project_product_configuration.execution_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.itemGroup | int(11) / YES | proj_project_product_configuration.item_group | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.itemCode | varchar(100) / YES | proj_project_product_configuration.product_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.parentCode | varchar(1000) / YES | proj_project_product_configuration.parent_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.quantity | int(11) / YES | proj_project_product_configuration.quantity | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.bomPaths | varchar(1000) / YES | proj_project_product_configuration.bom_path | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.itemModel | varchar(100) / YES | proj_project_product_configuration.product_model | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.itemDesc | varchar(500) / YES | proj_project_product_configuration.product_description | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.level | int(11) / YES | proj_project_product_configuration.level | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
| pm_project_product_config_level_info_from_crm.dataSource | varchar(25) / YES | proj_project_product_configuration.data_source | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P21 |
