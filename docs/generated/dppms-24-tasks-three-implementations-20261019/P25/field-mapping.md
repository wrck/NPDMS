# P25 原表逐字段承载清单

合并组件：P25。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| fb_service.id | varchar(64) / YES | int_sync_binding.source_key | 与原SN组成复合技术身份，保留同ID不同SN；正式记录ID由Owner生成；技术绑定 | P25 |
| fb_service.con_xb | varchar(25) / YES | ast_device_warranty_renewal_record.warranty_contract_no | 原续保合同号原样保存，不替换项目销售合同关系；正式字段承接 | P25 |
| fb_service.barcode | varchar(50) / YES | ast_device_warranty_renewal_record.device_sn | 原SN原样保存；可为空且不强制设备主档外键，空SN不参与项目/设备查询；正式字段承接 | P25 |
| fb_service.grade | varchar(15) / YES | ast_device_warranty_renewal_record.warranty_grade | 保存原等级编码；等级目录的完整承接仍单列待处理；正式字段承接 | P25 |
| fb_service.begin_date | datetime / YES | ast_device_warranty_renewal_record.warranty_start_date | 实际开始日期原值；原非零时分秒阻断，不能无声截断；正式字段承接 | P25 |
| fb_service.end_date | datetime / YES | ast_device_warranty_renewal_record.warranty_end_date | 实际结束日期原值，不以参考月数重算；正式字段承接 | P25 |
| fb_service.warranty | char(1) / YES | ast_device_warranty_renewal_record.warranty_months | 原年数乘12；空值/空串为NULL，0保留0，仅作参考；正式字段承接 | P25 |
| fb_service.remark | text / YES | ast_device_warranty_renewal_record.remark | 原TEXT及NULL原样保存；正式字段承接 | P25 |
| fb_service.isyb | int(11) / YES | ast_device_warranty_renewal_record.extended | 1为true，0为false，NULL保持；不据此生成当前完成事实；正式字段承接 | P25 |
| fb_service.state | int(11) / YES | ast_device_warranty_renewal_record.is_latest | 1为true，NULL保持；仅原最新选择标记，不是当前在保状态；正式字段承接 | P25 |
