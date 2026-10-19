# P13 原表逐字段承载清单

合并组件：P13。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_property_from_sms.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P13 |
| pm_project_property_from_sms.orderExecNumber | varchar(25) / YES | com_crm_execution_order.execution_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.projectCode | varchar(25) / YES | com_crm_execution_order.project_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.projectName | varchar(255) / YES | com_crm_execution_order.project_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.salesManCode | varchar(45) / YES | com_crm_execution_order.sales_rep_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.salesManName | varchar(45) / YES | com_crm_execution_order.sales_rep_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.marketCode | varchar(64) / YES | com_crm_execution_order.market_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.marketName | varchar(255) / YES | com_crm_execution_order.market_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.systemId | varchar(64) / YES | com_crm_execution_order.system_source_key | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.systemName | varchar(255) / YES | com_crm_execution_order.system_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.expendId | varchar(64) / YES | com_crm_execution_order.expend_source_key | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.expendName | varchar(255) / YES | com_crm_execution_order.expend_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.industryId | varchar(64) / YES | com_crm_execution_order.industry_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.industryName | varchar(255) / YES | com_crm_execution_order.industry_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.officeCode | varchar(15) / YES | com_crm_execution_order.department_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.officeName | varchar(15) / YES | com_crm_execution_order.department_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.serviceTypeName | varchar(10) / YES | com_crm_execution_order.service_type_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.channelName | varchar(255) / YES | com_crm_execution_order.channel_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.engineeFee | varchar(25) / YES | com_crm_execution_order.engineering_fee_raw；com_crm_execution_order.engineering_fee | 完整原文与合法金额分开，非数值不冒充金额；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.objId | varchar(64) / YES | com_crm_execution_order.source_object_id | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.applyType | varchar(25) / YES | com_crm_execution_order.apply_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.corporationCode | varchar(25) / YES | com_crm_execution_order.company_code；com_crm_execution_order.company_id | 同四项显式码转换；NULL保持，未知非空码阻断；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.customerProjectName | varchar(255) / YES | com_crm_execution_order.customer_project_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.finalCustomerName | varchar(255) / YES | com_crm_execution_order.final_customer_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.agentName | varchar(500) / YES | com_crm_execution_order.agent_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.projectMoney | decimal(16,2) / YES | com_crm_execution_order.project_amount | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.submitTime | datetime / YES | com_crm_execution_order.submit_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.majorProjectLevel | varchar(255) / YES | com_crm_execution_order.major_project_level | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.predBidDate | datetime / YES | com_crm_execution_order.predicted_bid_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.linkmanName | varchar(255) / YES | com_crm_execution_order.contact_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.linkmanTel | varchar(64) / YES | com_crm_execution_order.contact_phone | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
| pm_project_property_from_sms.dataSource | varchar(25) / YES | com_crm_execution_order.source_system | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P13 |
