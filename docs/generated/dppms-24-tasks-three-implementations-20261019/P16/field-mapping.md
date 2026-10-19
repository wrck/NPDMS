# P16 原表逐字段承载清单

合并组件：P16, P17。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project.projectId | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P16 |
| pm_project.projectType | varchar(45) / NO | proj_project.project_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.projectCode | varchar(45) / NO | proj_project.project_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.projectName | varchar(200) / YES | proj_project.project_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.projectState | varchar(11) / YES | proj_project.lifecycle_status；proj_project.current_stage；proj_project.closed_at | 按类型及原状态已批准初始化：用服100正常闭环S6、20异常闭环、30/31/32沿规则；关闭时间缺失保持NULL，不生成任务/审批完成；业务字段/正式关系 | P16 |
| pm_project.isback | varchar(11) / YES | 不迁入 | 用户明确删除回退/退回镜像，不新增业务列；来源证据留存但不是业务承接；已批准排除 | P16 |
| pm_project.column001 | varchar(255) / YES | proj_project.department_id；proj_project.department_code；proj_project.department_name | 精确正式目录；原NULL可保留，未知非空编码阻断；不授区域全部项目权限；业务字段/正式关系 | P16 |
| pm_project.column002 | varchar(255) / YES | com_sales_order.customer_code；com_sales_order.customer_id | 仅经正式合同/订单链精确核对；不加项目ERP客户镜像，不能据名称猜匹配；业务字段/正式关系 | P16 |
| pm_project.column003 | varchar(255) / YES | com_sales_order.customer_name | 同一精确订单客户事实，不替代最终用户；全历史链路尚未完成逐行验证；业务字段/正式关系 | P16 |
| pm_project.column004 | varchar(255) / YES | proj_project.market_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.column005 | varchar(255) / YES | proj_project.system_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.column006 | varchar(255) / YES | proj_project.expend_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.column007 | varchar(255) / YES | proj_project.industry_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.column008 | varchar(255) / YES | proj_project.not_track_reason | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.column009 | datetime / YES | proj_project.order_create_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.column010 | varchar(10) / YES | proj_project.project_category | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.column011 | varchar(10) / YES | proj_project.signing_method | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.column012 | varchar(2) / YES | proj_project.implementation_mode | 原空NULL/空串优先Readonly回退；0/1/3沿既有转换，2=DIRECT_SUBCONTRACT、4=FACTORY_INTEGRATION，两项均承接；业务字段/正式关系 | P16 |
| pm_project.columno12_readonly | int(2) / YES | proj_project.implementation_mode（仅主值缺失时） | 用户明确同名Readonly回退；不存第二份控制字段；业务字段/正式关系 | P16 |
| pm_project.column013 | varchar(255) / YES | proj_project_party.party_name（party_role=FINAL_CUSTOMER） | 实际最终产品用户，经PROJ相关方Owner承接；不覆盖购货方；业务字段/正式关系 | P16 |
| pm_project.column014 | text / YES | 不迁入 | 用户明确删除回退/退回镜像，不新增业务列；来源证据留存但不是业务承接；已批准排除 | P16 |
| pm_project.customerProjectName | varchar(255) / YES | proj_project.customer_project_name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.salesType | varchar(25) / YES | proj_project.sales_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.majorProjectLevel | varchar(255) / YES | proj_project.major_project_level | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.compId | int(2) / YES | proj_project.company_id；proj_project.company_code；proj_project.company_name | 原公司外键→实际公司编码→01/02/03/04转001/002/003/004；不将旧ID当新ID，原NULL按裁决保留；业务字段/正式关系 | P16 |
| pm_project.createTime | datetime / YES | proj_project.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P16 |
| pm_project.createBy | varchar(45) / YES | proj_project.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P16 |
| pm_project.updateTime | datetime / YES | proj_project.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P16 |
| pm_project.updateBy | varchar(45) / YES | proj_project.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P16 |
| pm_project.effectiveFrom | datetime / YES | proj_project.effective_from | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.effectiveTo | datetime / YES | proj_project.effective_to | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.disabled | bit(1) / YES | proj_project.disabled | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.projectStartTime | datetime / YES | proj_project.project_start_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.projectRefreshTime | datetime / YES | proj_project.project_refresh_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.projectCloseTime | datetime / YES | proj_project.project_close_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.customInfo | json / YES | proj_project.custom_info | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P16 |
| pm_project.customConfig | json / YES | — | 已审查来源全NULL，没有实际业务值；全历史遇非空无批准同义承接点时阻断；无值未落业务字段 | P16 |
| pm_project.projectId | int(11) / NO | int_sync_binding.source_key；int_sync_binding.target_id | 每个原projectId绑定同类型同码规范正式项目；目标id经Owner解析。；身份桥接 | P17 |
| pm_project.projectType | varchar(45) / NO | 正式项目身份核验及绑定字段 | 类型＋编码核实同一规范项目；不重复写项目业务字段。；身份核验 | P17 |
| pm_project.projectCode | varchar(45) / NO | 正式项目身份核验及绑定字段 | 类型＋编码核实同一规范项目；不重复写项目业务字段。；身份核验 | P17 |
| pm_project.projectName | varchar(200) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.projectState | varchar(11) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.isback | varchar(11) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column001 | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column002 | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column003 | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column004 | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column005 | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column006 | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column007 | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column008 | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column009 | datetime / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column010 | varchar(10) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column011 | varchar(10) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column012 | varchar(2) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.columno12_readonly | int(2) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column013 | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.column014 | text / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.customerProjectName | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.salesType | varchar(25) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.majorProjectLevel | varchar(255) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.compId | int(2) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.createTime | datetime / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.createBy | varchar(45) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.updateTime | datetime / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.updateBy | varchar(45) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.effectiveFrom | datetime / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.effectiveTo | datetime / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.disabled | bit(1) / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.projectStartTime | datetime / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.projectRefreshTime | datetime / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.projectCloseTime | datetime / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.customInfo | json / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
| pm_project.customConfig | json / YES | 本任务不写；主档资料由P16承接 | P17只迁身份，原其他列不重复写项目主档。；其他任务/参考 | P17 |
