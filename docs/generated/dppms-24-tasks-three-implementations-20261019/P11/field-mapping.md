# P11 原表逐字段承载清单

合并组件：P11。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| DPtech_v_product_info_single.itemCode | 未实时核对SQL Server类型 / 未实时核对 | ast_product_official_info.product_code | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P11 |
| DPtech_v_product_info_single.itemModel | 未实时核对SQL Server类型 / 未实时核对 | ast_product_official_info.product_model | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P11 |
| DPtech_v_product_info_single.itemDesc | 未实时核对SQL Server类型 / 未实时核对 | ast_product_official_info.product_name | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P11 |
| DPtech_v_product_info_single.itemDesc | 未实时核对SQL Server类型 / 未实时核对 | ast_product_official_info.product_desc | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P11 |
| DPtech_v_product_info_single.remark | 未实时核对SQL Server类型 / 未实时核对 | ast_product_official_info.technical_spec | 按下方当前映射的TRIM处理；SQL表达式见原查询。；已保存查询/映射 | P11 |
| DPtech_v_product_info_single.updateTime | 未实时核对SQL Server类型 / 未实时核对 | ast_product_official_info.source_version | 按下方当前映射的STRING处理；SQL表达式见原查询。；已保存查询/映射 | P11 |
| DPtech_v_product_info_single.updateTime | 未实时核对SQL Server类型 / 未实时核对 | ast_product_official_info.source_updated_at | 按下方当前映射的DATETIME处理；SQL表达式见原查询。；已保存查询/映射 | P11 |
| DPtech_v_product_info_single.updateTime | 未实时核对SQL Server类型 / 未实时核对 | ast_product_official_info.synced_at | 按下方当前映射的DATETIME处理；SQL表达式见原查询。；已保存查询/映射 | P11 |
| DPtech_v_product_info_single.statecode | 未实时核对SQL Server类型 / 未实时核对 | ast_product_official_info.status | 按下方当前映射的ENUM处理；SQL表达式见原查询。；已保存查询/映射 | P11 |
| DPtech_v_product_info_single.createTime | 未实时核对SQL Server类型 / 未实时核对 | 来源窗口排序参考 | updateTime相同后按createTime、itemModel倒序，非单独落库映射。；来源选择参考 | P11 |
