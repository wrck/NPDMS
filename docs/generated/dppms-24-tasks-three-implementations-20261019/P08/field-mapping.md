# P08 原表逐字段承载清单

合并组件：P08, P09。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| fnd_basic_data_type.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P08 |
| fnd_basic_data_type.dataTypeCode | varchar(45) / YES | system_dict_type.type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P08 |
| fnd_basic_data_type.dataTypeName | varchar(45) / YES | system_dict_type.name | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P08 |
| fnd_basic_data_type.status | int(11) / YES | 不迁入 | 原status是前台管理标记而非启停；既有结构无同义字段，用户明确三属性不迁入；已批准排除 | P08 |
| fnd_basic_data_type.createTime | datetime / YES | system_dict_type.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P08 |
| fnd_basic_data_type.createBy | varchar(45) / YES | system_dict_type.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P08 |
| fnd_basic_data_type.updateTime | datetime / YES | system_dict_type.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P08 |
| fnd_basic_data_type.updateBy | varchar(45) / YES | system_dict_type.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P08 |
| fnd_basic_data_type.effectiveFrom | datetime / YES | 不迁入 | 原status是前台管理标记而非启停；既有结构无同义字段，用户明确三属性不迁入；已批准排除 | P08 |
| fnd_basic_data_type.effectiveTo | datetime / YES | 不迁入 | 原status是前台管理标记而非启停；既有结构无同义字段，用户明确三属性不迁入；已批准排除 | P08 |
| fnd_basic_data.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P09 |
| fnd_basic_data.dataTypeCode | varchar(45) / YES | system_dict_data.dict_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P09 |
| fnd_basic_data.basicDataId | varchar(255) / YES | system_dict_data.value | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P09 |
| fnd_basic_data.basicDataName | varchar(255) / YES | system_dict_data.label | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P09 |
| fnd_basic_data.basicDataAttri1 | varchar(255) / YES | 不迁入 | 用户明确三属性不迁入，既有结构无同义字段；日期仅用于批准的首次启停转换，不存新的有效期列；已批准排除 | P09 |
| fnd_basic_data.sortId | int(11) / YES | system_dict_data.sort | NULL取0；原升序直接使用；ID114无有效项明确不迁入；业务字段/正式关系 | P09 |
| fnd_basic_data.createTime | datetime / YES | system_dict_data.create_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P09 |
| fnd_basic_data.createBy | varchar(45) / YES | system_dict_data.creator | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P09 |
| fnd_basic_data.updateTime | datetime / YES | system_dict_data.update_time | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P09 |
| fnd_basic_data.updateBy | varchar(45) / YES | system_dict_data.updater | 原审计日期保持；可精确关联人员保存user_id字符串；仅按各表裁决允许NULL更新时间取原创建时间，NULL人员不自动填管理员；业务字段/正式关系 | P09 |
| fnd_basic_data.effectiveFrom | datetime / YES | 不迁入 | 用户明确三属性不迁入，既有结构无同义字段；日期仅用于批准的首次启停转换，不存新的有效期列；已批准排除 | P09 |
| fnd_basic_data.effectiveTo | datetime / YES | 不迁入 | 用户明确三属性不迁入，既有结构无同义字段；日期仅用于批准的首次启停转换，不存新的有效期列；已批准排除 | P09 |
