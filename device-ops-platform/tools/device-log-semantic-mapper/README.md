# 设备日志语义映射器

该工具把采集平台生成的结构化命令日志转换为稳定的设备实体、内容块业务语义和可直接供业务系统使用的扁平投影。工具不依赖 Spring 或设备厂商 SDK，可单独运行，也可后续封装为平台解析插件。

## 使用

需要 Node.js 20 或更高版本，无第三方依赖。

```powershell
cd device-ops-platform/tools/device-log-semantic-mapper
node src/cli.mjs `
  --input "C:\Users\user\Desktop\临时\show-tech-structured\show-tech.structured.json" `
  --output ".\output\show-tech-semantic"
```

可选参数 `--rules` 和 `--projections` 分别指定自定义规则目录和投影配置。生成结果固定放在当前工具项目的 `output/` 下，该目录不会提交真实设备数据。生成目录包含六个文件：语义映射、标准设备快照、投影结果、质量报告、字段字典 Schema 和使用说明。

## 快速取数

`projection-profiles.json` 中的 `results.deviceBasic` 可直接得到：

```json
{
  "sn": "设备序列号",
  "softVersion": "软件版本",
  "platformVersion": "平台版本",
  "cpuModel": "CPU 型号",
  "memoryBytes": 8589934592
}
```

新增业务字段别名时，只修改 `projections/projection-profiles.json` 的 `fields`，其值指向标准 `semanticKey`，不需要重复编写日志解析逻辑。新增日志格式时，在 `rules/show-tech-semantic-rules.json` 增加角色选择器、提取器、类型转换和目标字段。

## 状态与冲突

- `OBSERVED`：已采集内容；`NO_DATA`：命令成功但内容为空；`NOT_ENABLED`：内容明确表明能力未启用。
- `SOURCE_CORRUPTED`：源内容含替换字符；`UNPARSED`：没有达到通用规则置信度；两者不会静默伪造值。
- 单值字段按置信度优先、同置信度按较后源行决胜，并在质量报告记录冲突；多值字段按源顺序去重保留。
- 事实保留 `source.blockIndex`、`source.lineStart`、`ruleId` 和 `confidence`，可以回溯到原内容块。

## 敏感值

通用的 `password`、`passphrase`、`secret`、`community`、`private key` 和 `credential` 赋值在证据写出前统一替换为 `[REDACTED]`。已经被掩码的候选值不会进入标准事实或业务投影。

后续接入平台时，可增加一个 `SEMANTIC_RULES` 类型的 `OutputParser` 适配器调用本契约；本次保持独立工具边界，不改变现有采集执行流程。
