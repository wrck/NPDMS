# P35 原表逐字段承载清单

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。八个软件历史扩展已暂缓，不读写其属性或定义；基础批次/版本历史预览现停在原项目当前身份冲突。正式父软件批次仍只用本任务独立来源身份和本次规划，不接管旧任务记录。

合并组件：P35, P36。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_soft_change_logs.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P35 |
| pm_project_soft_change_logs.projectId | int(11) / YES | ast_device_software_change_batch.project_id | 通过P17或本任务已批准的PROJ原项目身份解析正式project_id；不将原数值直接落正式外键。；业务字段/正式关系 | P35 |
| pm_project_soft_change_logs.changeVersion | varchar(10) / YES | ast_device_software_change_batch.change_version | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P35 |
| pm_project_soft_change_logs.changeRemark | varchar(255) / YES | ast_device_software_change_batch.change_remark | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P35 |
| pm_project_soft_change_logs.latest | int(11) / YES | ast_device_software_change_batch.latest | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P35 |
| pm_project_soft_change_logs.createBy | varchar(25) / YES | ast_device_software_change_batch.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P35 |
| pm_project_soft_change_logs.createTime | datetime / YES | ast_device_software_change_batch.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P35 |
| pm_project_soft_change_logs.updateBy | varchar(25) / YES | ast_device_software_change_batch.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P35 |
| pm_project_soft_change_logs.updateTime | datetime / YES | ast_device_software_change_batch.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P35 |
| pm_project_soft_version.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P36 |
| pm_project_soft_version.projectId | int(11) / YES | ast_device_software_version_record.project_id | 通过P17或本任务已批准的PROJ原项目身份解析正式project_id；不将原数值直接落正式外键。 由P17原项目身份解析正式项目；历史不要求当前设备仍属于本项目。；业务字段/正式关系 | P36 |
| pm_project_soft_version.logId | int(11) / YES | ast_device_software_version_record.change_batch_id | 原logId非空时必须经P35来源绑定解析正式批次且属于同项目；不得直接写旧logId。；业务字段/正式关系 | P36 |
| pm_project_soft_version.contractNo | varchar(100) / YES | ast_device_software_version_record.contract_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.itemCode | varchar(25) / YES | ast_device_software_version_record.product_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.barCode | varchar(25) / YES | ast_device_software_version_record.device_sn | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.conp | varchar(100) / YES | ast_device_software_version_record.conp_version | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.conpType | varchar(100) / YES | ast_device_software_version_record.conp_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.conpSeries | varchar(100) / YES | ast_device_software_version_record.conp_series | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.conpMark | varchar(255) / YES | ast_device_software_version_record.conp_mark | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.conpBak | varchar(255) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.conp_bak（SoftwareVersionRecord） | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P36 |
| pm_project_soft_version.conpChange | int(11) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.conp_change（SoftwareVersionRecord） | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P36 |
| pm_project_soft_version.cpld | varchar(100) / YES | ast_device_software_version_record.cpld_version | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.cpldBak | varchar(255) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.cpld_bak（SoftwareVersionRecord） | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P36 |
| pm_project_soft_version.cpldChange | int(11) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.cpld_change（SoftwareVersionRecord） | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P36 |
| pm_project_soft_version.boot | varchar(100) / YES | ast_device_software_version_record.boot_version | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.bootBak | varchar(255) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.boot_bak（SoftwareVersionRecord） | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P36 |
| pm_project_soft_version.bootChange | int(11) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.boot_change（SoftwareVersionRecord） | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P36 |
| pm_project_soft_version.pcb | varchar(100) / YES | ast_device_software_version_record.pcb_version | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.pcbBak | varchar(255) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.pcb_bak（SoftwareVersionRecord） | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P36 |
| pm_project_soft_version.pcbChange | int(11) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.pcb_change（SoftwareVersionRecord） | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P36 |
| pm_project_soft_version.executeTime | date / YES | ast_device_software_version_record.execute_time | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.datastate | int(11) / YES | ast_device_software_version_record.data_state | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
| pm_project_soft_version.createTime | datetime / YES | ast_device_software_version_record.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P36 |
| pm_project_soft_version.createBy | varchar(25) / YES | ast_device_software_version_record.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P36 |
| pm_project_soft_version.updateTime | datetime / YES | ast_device_software_version_record.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P36 |
| pm_project_soft_version.updateBy | varchar(25) / YES | ast_device_software_version_record.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P36 |
| pm_project_soft_version.customInfo | json / YES | ast_device_software_version_record.custom_info | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P36 |
