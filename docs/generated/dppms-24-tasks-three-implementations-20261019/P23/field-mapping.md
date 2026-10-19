# P23 原表逐字段承载清单

合并组件：P23。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_pb_plan_from_sms.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P23 |
| pm_pb_plan_from_sms.contractNo | varchar(25) / YES | com_contract_collection_plan.contract_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.batchCode | varchar(10) / YES | com_contract_collection_plan.batch_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.basicDataName | varchar(20) / YES | com_contract_collection_plan.collection_item_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.referenceEventName | varchar(20) / YES | com_contract_collection_plan.reference_event_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.eventPlanHappenDate | datetime / YES | com_contract_collection_plan.event_planned_at | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.afterDaysNum | int(11) / YES | com_contract_collection_plan.after_days | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.eventActualFinishDate | datetime / YES | com_contract_collection_plan.event_actual_finished_at | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.marketingFeedback | varchar(2000) / YES | com_contract_collection_plan.marketing_feedback | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.createBy | varchar(45) / YES | com_contract_collection_plan.creator | 可核实人员关联user_id；CrmInterfaceUser/crmapp及已批准l01663保留原编码；历史日期不变；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.createTime | datetime / YES | com_contract_collection_plan.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.updateBy | varchar(45) / YES | com_contract_collection_plan.updater | 可核实人员关联user_id；CrmInterfaceUser/crmapp及已批准l01663保留原编码；历史日期不变；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.updateTime | datetime / YES | com_contract_collection_plan.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.effectiveFrom | datetime / YES | com_contract_collection_plan.effective_from | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.effectiveTo | datetime / YES | com_contract_collection_plan.effective_to | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
| pm_pb_plan_from_sms.dataSource | varchar(25) / YES | com_contract_collection_plan.data_source | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P23 |
