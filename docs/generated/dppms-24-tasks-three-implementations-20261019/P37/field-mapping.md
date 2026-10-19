# P37 原表逐字段承载清单

合并组件：P37。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_license_info_from_license.id | int(11) / NO | int_sync_binding / plt_migration_*（技术追溯） | 原技术ID用于既有INT/PLT来源绑定，不覆盖正式主键；技术追溯不计为业务字段承接；技术身份 | P37 |
| pm_project_license_info_from_license.licenseCode | varchar(256) / NO | ast_device_license_record.license_code_ciphertext | 独立受控AES-256认证加密；密钥DPAPI受控，原值仅内存核对；页面/日志/明文证据禁显；受控加密字段 | P37 |
| pm_project_license_info_from_license.sn | varchar(255) / YES | ast_device_license_record.device_sn | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P37 |
| pm_project_license_info_from_license.specModel | varchar(256) / YES | ast_device_license_record.product_model | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P37 |
| pm_project_license_info_from_license.contract | varchar(32) / YES | ast_device_license_record.contract_no | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P37 |
| pm_project_license_info_from_license.contractType | varchar(16) / YES | ast_device_license_record.contract_type | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P37 |
| pm_project_license_info_from_license.item | varchar(255) / YES | ast_device_license_record.product_code | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P37 |
| pm_project_license_info_from_license.status | varchar(255) / YES | ast_device_license_record.license_status | 同义业务值按本任务转换/Owner规则承接；按正式Owner及租户、权限、并发和来源保护处理；业务字段/正式关系 | P37 |
