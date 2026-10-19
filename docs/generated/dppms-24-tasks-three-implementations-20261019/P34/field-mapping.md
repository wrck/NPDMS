# P34 原表逐字段承载清单

合并组件：P34。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| fb_soft_version.serial_number | varchar(100) / YES | ast_device_factory_version_record.device_sn | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本；业务字段/正式关系 | P34 |
| fb_soft_version.conp | varchar(100) / YES | ast_device_factory_version_record.conp_version | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本；业务字段/正式关系 | P34 |
| fb_soft_version.cpld | varchar(100) / YES | ast_device_factory_version_record.cpld_version | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本；业务字段/正式关系 | P34 |
| fb_soft_version.boot | varchar(100) / YES | ast_device_factory_version_record.boot_version | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本；业务字段/正式关系 | P34 |
| fb_soft_version.pcb | varchar(100) / YES | ast_device_factory_version_record.pcb_version | 全部重复组合保留正式记录；唯一主档冲突不自动覆盖，原无日期不猜最终版本；业务字段/正式关系 | P34 |
