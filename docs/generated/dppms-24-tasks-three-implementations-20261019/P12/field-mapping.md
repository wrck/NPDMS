# P12 原表逐字段承载清单

合并组件：P12。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| fb_items.id | int(11) / YES | 不迁入业务字段 | 原技术id没有产品业务含义；来源按item绑定，不覆盖正式ID。；已批准排除 | P12 |
| fb_items.item | varchar(25) / YES | ast_product_official_info.product_code | 租户＋产品编码唯一解析，已删除/多义不自动复活或任选。；最新裁决；实现待调整 | P12 |
| fb_items.describe_ | varchar(255) / YES | ast_product_official_info.product_name；ast_product_official_info.product_desc | 仅缺失产品填写名称和描述；同编码以CRM为准，异名不转存扩展、备注或其他字段。；最新裁决；实现待调整 | P12 |
| fb_items.itemname | varchar(255) / YES | ast_product_official_info.product_model | 仅缺失产品按来源填写；已有正式字段保持，不再写产品目录扩展。；最新裁决；实现待调整 | P12 |
