# Java 设备日志语义解析器

`device-ops-parser-semantic` 是无 Spring 依赖的 Java 25 解析模块。它直接接收采集平台的
`CommandOutputBlock`，生成一个包含标准快照、业务投影、质量报告和证据定位的确定性 JSON。

该模块不连接设备、不保存数据，也不依赖数据库、SSH/Telnet 适配器、定时任务或 Web 服务。

## 构建

从仓库根目录执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.1+8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -f device-ops-platform/pom.xml -pl device-ops-parser-semantic -am clean package
```

生成可独立执行的 JAR：

```text
device-ops-platform/device-ops-parser-semantic/target/device-ops-parser-semantic.jar
```

## Java API

```java
SemanticParser parser = DefaultSemanticParser.bundled();
SemanticParseResult result = parser.parse(
        commandBlocks,
        DefaultSemanticParser.bundledSpecification());
```

`SemanticParser` 无状态且线程安全。调用方也可以构造 `SemanticParserSpecification`，传入冻结的规则
和 projection JSON；解析过程中不会读取远程资源或全局“最新规则”。

版本化调用使用精确坐标编译一次不可变计划：

```java
ParserReleaseBundle release = new ParserReleaseBundleCodec().decode(releaseDirectory);
ParserPlan plan = new ParserPlanCompiler().compile(release);
SemanticParseResult result = new DefaultDynamicSemanticParser().parse(plan, inputSource);
```

## CLI

```powershell
java -jar device-ops-parser-semantic.jar `
  --input command-output-blocks.json `
  --output structured-result.json
```

指定一个冻结发布包进行解析：

```powershell
java -jar device-ops-parser-semantic.jar `
  --release-directory releases/device-show-tech-1.0.0 `
  --input command-output-blocks.json `
  --output structured-result.json
```

发布目录必须包含 `manifest.json`、`rules.json`、`projections.json` 和
`verification-cases.json`。清单固定日志类型、发布版本、输入适配器、引擎/规则/projection 版本及可选
扩展坐标；验证用例引用同目录内的输入与预期结果。调用方显式选择目录，不存在“最新版本”回退。

输入格式：

```json
{
  "schemaVersion": "1.0.0",
  "collectionId": "optional-caller-correlation",
  "commandBlocks": [
    {
      "commandIndex": 1,
      "commandText": "show version",
      "status": "SUCCEEDED",
      "stdout": "Software Release TEST-1.2.3\nSerial Number: SN-001",
      "stderr": "",
      "receivedBytes": 61,
      "pageCount": 1,
      "truncated": false,
      "exitCode": 0
    }
  ]
}
```

`parsedFacts`、`parseWarnings` 和时间字段可以存在，但不会进入语义输入哈希。`collectionId` 仅供输入方
关联，不进入结果，避免任务身份破坏相同证据的确定性。

输出只有一个 JSON，顶层字段包括：

- `schemaVersion`：1.3 / 1.4 引擎输出 `1.1.0`；
- `genericContent`：按命令和内嵌分段保存与领域映射无关的通用结构；
- `snapshot`：包含值、类型、规则、置信度及源命令块/行号的标准事实；
- `projections`：面向业务实体的快捷映射，例如 `deviceBasic.sn` 和 `deviceBasic.softVersion`；
- `quality`：空块、失败、截断、损坏、未解析、脱敏和告警统计；
- `observations`：每个命令块的业务角色、状态和证据范围；
- `inputSha256`、`ruleSha256`、`projectionSha256`：输入和配置指纹。

### 通用结构结果（schema 1.1.0）

`genericContent.units` 同时包含顶层命令和有效的一级内嵌命令。每个 unit 用 `commandIndex`、
`parentCommandIndex` 与一基的 `sectionIndex` 标识，不使用命令文本充当身份；并记录来源行范围、
`structureStatus`、告警、被资源限制省略的行数和按原始顺序排列的 `sections`。

section 类型为 `keyValue`、`keyValueTree`、`table`、`recordList`、`configStanza`、`list` 或
`text`。键值条目保留父节点自身值、子项和重复键顺序；表格保留原列顺序、稳定列 id、空单元格和未解析
行；记录与配置段保留各自身份、行范围和脱敏后的原文。不能可靠识别的内容降级为 `text`，空命令为
`EMPTY`，达到限制时为 `LIMITED`，不会臆造领域字段。

规则 schema `1.1.0` 可在 rule set 顶层声明 `structureRules`：

```json
{
  "structureRuleId": "running-config-stanzas",
  "selectors": [{ "type": "COMMAND_REGEX", "value": "^show\\s+run$" }],
  "mode": "FORCE",
  "type": "configStanza",
  "options": { "configSeparators": ["!"] }
}
```

`mode` 只允许 `AUTO`、`FORCE`、`TEXT`。`FORCE` 的 type 使用上述七种 section 类型；options 只允许
`recordList.recordStartPattern`、`table.columnNames` 和 `configStanza.configSeparators`。结构提取器的结果
也由 `KEY_VALUE`、`TABLE`、`CONFIG_STANZA` 等领域提取器复用，避免对同一证据重复解析。

通用结构只读取统一脱敏后的证据。1.4 引擎进一步保护无引号多词敏感值、缩进敏感值及完整/未闭合
PEM 私钥块，并保留源行数和缩进。未知字段仍需按安全审查扩展脱敏规则；1.3 的已知脱敏边界不会被
静默改写，处理新输入应选择 1.4。解析器对输入、结构规模及输出设置限制，结构截断报告
`LIMITED`/`omittedLineCount`。`device-command-output-1.0.x` 至 `1.2.0` 的冻结输出 schema 仍为
`1.0.0`，没有 `genericContent`；旧发布和已保存的历史结果不会被补算或改写。

### 1.4 正确性增强

`parser-releases/device-command-output-1.4.0` 显式选择增强路径，默认 Java 构造和旧发布仍保留原算法：

- 纯装饰标题不再切断有效子命令；TEXT/FORCE 连续处理所有段落并保留不匹配文本。
- 重复记录以 `record.sections` 保存有序混合正文，`entries` 仅供旧记录兼容；前端展开后按结构渲染。
- 支持紧凑冒号、同行多 KV、受限续行、单行数据表、Tab/整体缩进、框线表及连续表格边界。
- 语义提取递归访问记录子结构；列 ID 与最终事实字段名消歧，不因重复表头丢值。
- 表格/节点预算互不污染，记录子节点参与预算；KV 深度上限 64、混合记录容器上限 16。
- 真实三日志保留 50 个子命令、12 个空结果；会话统计不空，温度表为 5 列，23 条接口记录有正文结构。

模糊的自由文本仍回退为 text；仅表头、无法可靠判断的续行不强行生成表格。当前结构规则选项白名单
保持不变，未新增 CSV/JSONL 或任意插件能力。结构预算在 section 构造后裁剪，DELIMITED_SECTION
仍先分段，因此不是流式无限输入解析器；输入字节上限和输出上限仍必须保留。

新增发布目录不会自动入库、发布或激活。在线使用须通过既有发布控制流程验证、发布并显式选择/激活
新 release；不要用现有 releaseId 替换制品。历史任务重试仍使用原坐标，需要新结果时创建重解析任务。

## 确定性与安全

- 命令块按 `commandIndex` 排序，CRLF/CR 统一为 LF；
- 结果不包含当前时间、执行耗时、随机数、绝对路径或临时目录；
- 相同输入、规则和 projection 的输出字节及 SHA-256 完全一致；
- ANSI 控制序列、NUL 和退格覆盖序列按通用终端规则移除并记录告警；
- 敏感键和值在事实生成前拦截，不进入结果或错误消息；
- 外部规则只支持白名单 selector、extractor 和 transform，不执行任何代码；
- 输入、规则、正则、事实数和结果大小均有限制。

CLI 成功退出码为 `0`。失败退出码为：`2` 参数错误、`3` 输入或文件错误、`4` 规则错误、
`5` projection 错误、`6` 资源限制、`1` 未分类内部错误。失败不会覆盖已有完整输出。

## 扩展映射

规则位于 `src/main/resources/semantic/show-tech-semantic-rules.json`，projection 位于
`src/main/resources/semantic/projection-profiles.json`。厂商差异只能通过这些受控规则表达，Java
实现不以特定命令判断分页完整性或执行完成状态。

`command-output-block/v1` 用于采集平台生成的 `CommandOutputBlock[]`；`line-log/v1` 用于逐行日志，
`section-text/v1` 用于已完成内容块切分的文本。原始设备日志的边界识别应由对应输入适配器完成，规则本身
只负责稳定的语义提取与业务投影。

## 与采集平台的闭环

独立解析器与在线解析使用同一发布包、输入适配器、编译器和执行引擎。在线流程在采集事务完成时保存
命令块 payload，并把 `releaseId`、`ParserCoordinate`、输入格式和结果接收目标冻结到解析任务；工作节点
只能加载该精确发布。完成后生成 `SemanticParseResult` 和 `ResultEnvelope`，再通过唯一的
`PARSER_RESULT_READY` outbox 事件投递给集成方。重新解析会创建新任务并记录 `sourceResultId`，不会修改
原结果。

因此，同一发布目录和相同输入通过 Java API、CLI 或在线 worker 执行时，`SemanticParseResult` 字节一致；
任务 ID、结果 ID、时间戳和回传状态只属于外层运行信封，不影响解析确定性。
