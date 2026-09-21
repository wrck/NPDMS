# device-command-output 1.4.0

本发布启用引擎 1.4.0，输入 schema 1.0.0，输出 schema 1.1.0。它独立于既有1.0.0/1.0.1/1.1.0/1.2.0/1.3.0制品；不修改历史结果。

主要增强：装饰边界过滤，TEXT/FORCE完整分段，记录内混合内容，紧凑/单行多KV与缩进树，短表/Tab/框线/连续表格，递归事实提取与列名消歧，多词/缩进/PEM敏感内容脱敏，独立预算。

`verification-cases.json` 包含三命令和仅show-tech两份脱敏输入。黄金结果通过语义断言后生成，并在单测中三次重复验证字节稳定性。

从device-ops-platform目录运行：

```powershell
java -jar device-ops-parser-semantic/target/device-ops-parser-semantic.jar `
  --release-directory parser-releases/device-command-output-1.4.0 `
  --input parser-releases/device-command-output-1.4.0/input-real-sanitized.json `
  --output device-ops-parser-semantic/target/result-1.4.0.json
```

在线启用须走已有保存草稿、验证、发布、激活流程，或在请求中显式绑定已发布的新releaseId。目录存在不代表运行中环境已激活；本次开发未修改运行数据库。

受限说明：字段/表头不明确时保留文本；仅表头表格和任意厂商配置语法不是本版本保证范围。未提供新的结构规则选项或CSV/JSONL插件。保留输入字节/输出大小限制，结构解析不是无限流式处理。
