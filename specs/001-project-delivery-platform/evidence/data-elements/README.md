# 数据元结构化基线

## 目的

本目录是 `需求/数据元.xlsx` 的机器可读基线。后续 AI、设计、迁移和评审任务默认读取本目录，不重复解析 Excel。

数据元与旧库、正式设计及当前实现业务对象的审查结论见[`../../appendices/legacy-data-element-business-object-mapping.md`](../../appendices/legacy-data-element-business-object-mapping.md)。

只有出现以下情况之一，才允许回溯原始 Excel：

1. `manifest.json` 记录的 SHA-256 与当前 Excel 不一致；
2. 结构化记录无法证明某个字段、公式或原始单元格内容；
3. 需要核验仅由 Excel 格式、批注、合并关系或显示属性表达的语义。

## 读取顺序

1. 先读 `manifest.json`，确认来源哈希、工作表范围和记录数。
2. 业务数据元映射优先查 `semantic-elements.jsonl`。
3. 旧库表和字段映射优先查 `schema-records.jsonl`。
4. ITR 对象和字段优先查 `itr-elements.jsonl`。
5. 规范化记录证据不足时，按 `sheet`、`row` 查 `source-rows.jsonl`。
6. 上述结构化证据仍不足，或源文件哈希变化时，才直接读取 Excel。

示例：

```powershell
rg -n '"name":"项目编码"' semantic-elements.jsonl
rg -n '"tableName":"pm_order_line_from_erp"' schema-records.jsonl
rg -n '"sheet":"项目管理","row":' source-rows.jsonl
```

## 文件说明

| 文件 | 用途 | 是否日常优先读取 |
| --- | --- | --- |
| `manifest.json` | 来源哈希、提取方法、工作表范围和记录数 | 是 |
| `semantic-elements.jsonl` | “数据元”“项目”工作表中的业务数据元 | 是 |
| `schema-records.jsonl` | “项目管理”“系统支撑”“备件”中的物理字段及业务数据元行 | 是 |
| `itr-elements.jsonl` | “ITR”“ITR-bak”中的业务字段 | 是 |
| `source-rows.jsonl` | 各工作表逐行原始值、公式和单元格定位 | 证据不足时读取 |

## 证据与边界

- 本基线通过 XLSX 单元格和公式直接导入生成，没有使用截图或 OCR。
- 提取覆盖每个工作表已使用范围内的全部列，包含隐藏列中的值。
- `项目管理`、`系统支撑`、`备件`用于当前旧库结构比对；名称含“备份”以及 `ITR-bak` 的工作表只作为历史补充证据。
- 结构化基线保留 `sheet`、`row`、`cell` 或 `range`，可以定位回原始单元格。
- 物理结构发生冲突时，以当前只读数据库结构为准；业务语义发生冲突时，提交规格评审，不由实现自行选择。
- 结构化证据保留敏感字段的名称、类型、约束和业务含义，但旧密码、密码哈希、密钥和令牌值必须替换为明确的`<REDACTED_...>`占位符；旧密码不进入新平台，也不提交到Git。

## 更新规则

更新 Excel 后必须重新生成本目录全部 JSON/JSONL 文件，并同时更新：

1. `manifest.json` 的来源哈希、文件大小、修改时间和生成时间；
2. 数据元—旧库—当前业务对象映射文档；
3. 表覆盖率、字段覆盖率和漂移清单；
4. 与本次更新相关的规格、迁移规则和验收证据。

禁止只修改某一条结构化记录而不更新来源哈希和全量校验结果。
