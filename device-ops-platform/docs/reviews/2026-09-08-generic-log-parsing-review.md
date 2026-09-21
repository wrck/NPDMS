# 采集日志通用解析能力审查

日期：2026-09-08  
范围：Device Ops `device-command-output-1.3.0`、通用结构层及其语义提取复用路径。  
交付性质：审查与增强建议；未修改业务代码、发布规则、黄金结果或运行配置。

## 一、结论

现有“通用结构 + 可选领域投影 + 固定发布版本”方向正确，也已实现键值、缩进树、表格、重复记录、配置、列表和文本七类结构。但当前实现尚不能证明“正确覆盖三类真实日志的大多数内容”。主要问题不只是格式种类不足，而是：

1. 分段和强制模式会遗漏正文；
2. 一些结构只生成外壳，内部没有解析；
3. 表格列识别会生成错误数据；
4. `STRUCTURED` 和黄金测试可能掩盖上述错误；
5. 扩大原文保留和结构提取之前，需要补齐脱敏边界。

最优先增强的是**内容完整性、混合分段、记录内部解析和表格可信识别**，而不是继续堆叠命令名正则或增加业务投影字段。

## 二、样本依据与口径

### 2.1 三类不是三个厂商

仓库设计明确把已上传的三类日志定义为同一采集中的：

| 命令 | 输入行数 | 主要内容 | 当前可确认情况 |
| --- | ---: | --- | --- |
| `show version` | 23 | 软件/平台描述、运行时间、普通 KV、槽位复合字段 | 9 个可靠 KV；说明文本仍需保留 |
| `show run` | 442 | `!` 分段、顶层命令、缩进子配置、重复段 | 100 个配置段；脱敏输入中 162 条非空缩进行 |
| `show tech` | 2207 | 聚合命令、表格、KV、缩进树、重复记录、配置、事件文本 | 50 个有效子命令；输入中 38 个有内容、12 个空 |

主要复验输入：

- `device-ops-platform/parser-releases/device-command-output-1.3.0/input-real-sanitized.json`
- `device-ops-platform/parser-releases/device-command-output-1.3.0/input-tech-only-sanitized.json`

两者分别是三命令脱敏基线与仅聚合命令衍生基线，不应当作不同厂商覆盖样本。1.1/1.2/1.3 中的同名三命令输入是相同内容的版本副本。

来源说明：`docs/superpowers/specs/2026-08-29-generic-structured-content-parsing-design.md:69-124`。另外只读核对了文档明确引用的原始三命令会话及较早的 `show tech.log`，没有扫描无关私人文件，没有连接数据库。原始会话中的厂商/型号指向 DPtech / VPN1000-GA-X，不能据此推断已验证其他厂商。

### 2.2 统计差异说明

- 原始 `show run` 为 164 条非空缩进行；脱敏基线为 162 条。原始第 42、45 行在样本脱敏时失去前导空格，因此本次不把这两个数字差异当作解析器丢两条配置的证据。
- 原始和脱敏基线均可确认 23 个 `Interface ...` 顶层记录标题。
- 按本次明确排除空白、纯星号装饰、末尾提示符的口径，`show tech` 有 1979 条非空正文；旧设计的 1980 条采用不同清理口径。以下不把它换算成已正确解析率。
- `show interface` 占 997 条非空正文，约占聚合正文的一半；嵌套运行配置占 435 条，约占 22%。因此修复接口重复记录，比增加少数设备基础字段更能提高覆盖率。
- 原始三命令会话与较早的 `show tech.log` 均通过 UTF-8 严格解码；后者混用 LF/CRLF，且已经含有 4 个 U+FFFD 替换字符。编码合法不等于原始字符没有损坏。

## 三、需要通用覆盖的日志形状

| 结构族 | 三日志中的例子 | 必须保留/识别的变化 |
| --- | --- | --- |
| 会话与命令边界 | 顶层命令、`********show ...********` | 回显、提示符、空命令、纯装饰线、绝对行号 |
| 普通 KV | version、console、hotbackup、logging | `key: value`、`key=value`、紧凑冒号、空值、单位、重复键 |
| 单行多 KV | 风扇状态、路由汇总 | `status: Normal gear: Low`、`Total:4 Active:4 Inactive:0` |
| 缩进 KV 树 | 会话统计、SSH/Telnet 属性 | 父键自身值与子项并存、2/4/8 空格与 Tab、回退缩进、重复子键 |
| 标题加混合正文 | 内存详情、接口详情 | 无冒号标题、文字状态、KV、续行、小表格共同属于同一对象 |
| 固定宽度表格 | device、process、interface status | 多词表头、连续或分组横线、装饰横线、空单元格、右对齐数字 |
| 小表格/矩阵 | CPU 使用率、ARP、MAC | 一行表头加一行数据、只有表头/零数据、横向指标矩阵 |
| 管道/框线表格 | local-group 等 | 有无表头、空单元格、外框；扩展兼容 `+---+`、Markdown 边框 |
| 多结构连续块 | environment、counters、route summary | 说明/KV 后紧跟表格，两张表连续出现，不依赖空行分隔 |
| 重复记录 | local-user、interface | 标题/分隔线/首键重复识别记录；记录内部仍允许混合结构 |
| 配置段 | show run、running-config | 分隔符、缩进子命令、重复顶层命令；不强行把每条配置解释成 KV |
| 列表和自由文本 | timeout、错误提示、说明、路由行 | 有序/无序项、续行；低置信度保留原文而不是伪造列或键 |
| 事件日志 | operlog/syslog 类命令 | 时间、级别、来源、消息；不是看到冒号就当 KV |

时间 `10:20:30`、MAC、IPv6、URL 等需要作为冒号误识别的负例。CSV/TSV、JSON/JSONL、XML、应用栈跟踪适合增加独立解析插件，但这三类设备命令日志不足以作为这些格式的真实验收证据。

## 四、已复现的主要问题

P1 表示会造成关键内容遗漏、错误结构或敏感内容暴露；P2 表示影响扩展正确性、边界稳定性或质量表达。

### P1-1：纯星号装饰行把会话统计正文切走

位置：

- `device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/DelimitedSectionParser.java:15-20`
- `device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/NestedEvidenceExpander.java:62-75`
- `device-ops-platform/parser-releases/device-command-output-1.3.0/rules/base.json:79`

分段正则允许纯星号行被捕获为命令 `*`。分段器先用它切断前一个命令，后续展开才判断该命令无字母数字而跳过。于是有效正文归属已经丢失。

真实输入 `show tech` 第 1464 行是纯星号装饰；1465-1495 行仍有会话总数、IPv4/IPv6 子统计、速率和错误计数。输出中的 `show session statistic` 却仅覆盖 1462-1463 行，状态 `EMPTY`。因此输入的 12 个空命令被输出为 13 个。

这部分原文仍可在原始采集/旧分段结果中找到，但没有进入正确的通用子单元。应在确立边界之前排除装饰标题，而不是切开后丢弃。

### P1-2：TEXT / FORCE 模式只保留第一次匹配，后续内容静默遗漏

位置：`device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/StructuralSegmenter.java:23-34`。

`TextStructureParser` 遇空行停止；KV、表格等也返回自己的 `nextOffset`。但 TEXT/FORCE 分支只接受一次结果，既不检查剩余输入，也不把尾部继续解析或降级为文本。

合成输入：

```text
Name: alpha

State: up
tail note
```

FORCE KV 只返回第一行，状态 `STRUCTURED`、无 warning、无 omittedLineCount。TEXT 两段文本只返回第一段。

真实基线的 `show tech` 顶层规则为 TEXT，2207 行输入的该通用单元实际只保留第 3 行一行文本。多数正文另有子单元，但这不能替代 TEXT 模式本身的完整性契约，也无法弥补上面的装饰分段漏洞。

### P1-3：23 个接口记录全部只有身份，没有内部结构

位置：`device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/RecordStructureParser.java:150-159`。

记录正文只从 offset 0 调用一次 KV 解析，且子 `sections` 永远为空。真实接口记录的第一条正文是类似 `... state is UP, line state is DOWN` 的描述，不是 KV，因此后面的 Description、MTU、带宽、收发统计也完全没有进入记录字段。

完整 CLI 重放结果：23 条记录全部 `entries=[]`、`sections=[]`；997 条非空正文中除 23 个身份标题外，974 行没有进入记录内部字段。父 section 的 `rawLines` 尚在，但状态仍显示 `STRUCTURED`。

记录应充当容器，正文继续执行受限的混合结构分段；非 KV 行不能中断整个记录的解析。

### P1-4：真实温度表 5 列被错误拆成 52 列

位置：`device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/TableStructureParser.java:230-245`。

`separatorStarts()` 只区分“所有横线段长度都为 1”和其他情况。真实 environment 横线装饰以单横线为主，却夹杂一个双横线，于是引擎把每一小段横线起点都当列起点。

真实输入第 153-155 行的表头应为：Slot ID、Board Name、Board Temperature、CPU Temperature、Switch-chip Temperature。输出却有 52 列，标签为 `Sl`、`ot`、`I`、`D` 等碎片，数值 `32` 也被拆开。

列边界必须交叉验证表头、数据行和分隔线；装饰线不等于列定义。

### P1-5：通用结构复制前的脱敏不覆盖多词值和 PEM 正文

位置：

- `device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/SensitiveEvidenceSanitizer.java:35-43`
- `device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/SensitiveValueRedactor.java:14-32`

仅使用合成数据验证：无引号 `Password: SYNTHETIC_FIRST SYNTHETIC_SECOND ...` 只替换首 token，后续 token 仍进入 `genericContent.rawLines`；合成 `BEGIN PRIVATE KEY` / `END PRIVATE KEY` 中间正文也被原样保留。

未展示或验证任何真实私钥内容，也不据此断言历史数据已发生泄露。这里确认的是安全处理能力缺口。需要按敏感字段上下文及跨行块状态脱敏，同时保持行数/缩进或显式记录替换跨度，不能通过不再脱敏来保住列对齐。

### P2-1：表格边界、紧凑 KV 和短表格互相误判

位置：

- `device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/TableStructureParser.java:108-170`
- `device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/KeyValueStructureParser.java:77-95`

复现结果：

- 表格后没有空行的 `Total: 1`、`Status: healthy` 被切成表格数据；甚至把单词截断。
- 冒号后无空格的 `State:up` 不被识别为 KV。
- 同行 `Input: 10 Output: 20` 只得到 Input，剩余全部进入它的值。
- 真实 route summary 的 `Total:4 Active:4 Inactive:0` 被当作表头，真正的 `Route Source / Active / Inactive` 成了第一条数据。
- 一行表头加一行数据的 CPU、ARP/MAC 小表格无法可靠识别；CPU 退成列表。
- 单 Tab 分隔、整体缩进表格及 `+---+` 框线表格的合成用例也不能正确识别。

这些能力应以格式规则增强，不宜为每条 show 命令写专用分支。

### P2-2：层级/记录容器会阻断原有语义提取

位置：`device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/FactExtractor.java:164-175`。

同一输入中的两组 `Name/State` 被通用层归为 recordList 后，原有 KEY_VALUE(State) 不再查找记录的 entries。合成复现为旧路径返回两个事实，通用结构路径返回零事实且无警告。应提供统一结构遍历器，按显式作用域和基数提取，不让包装类型意外改变字段可达性。

### P2-3：配置前导识别会误删合法配置

位置：`device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/ConfigStanzaStructureParser.java:30-35`。

首个 `!` 之前只要有空行，就把整个前缀跳过。合成的 `hostname + 空行 + interface A + ! + interface B` 只保留 B，前缀连 section 原文也未保留。当前真实 fixture 的前缀确属说明文字，因此这条是通用扩展边界错误，不能把它误报为该 fixture 已丢合法配置。

### P2-4：预算限制不独立，也没有完整递归覆盖

位置：`device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/StructureBudget.java:32-36,63-70,91-99`。

合成复现：表格行数达到上限后，粘性的 limited 标志使后续本来仍有节点预算的 A/B/C 三条 KV 只保留 A；另一方面 recordList 内部的 KV 又绕过节点预算。另有事实候选先创建全部对象、后检查 maxFacts 的检查时机问题。

预算应区分单元、结构、行/节点/深度/字节维度，并在分配和递归之前检查；LIMITED 必须对应准确的未处理源范围。

### P2-5：表格事实转换存在字段名冲突覆盖

位置：`device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/FactExtractor.java:204-212`。

表头 `Name / Name / column1` 中，重复 Name 回退为 column1/column2，第三列唯一标签恰为 column1，最终 Map.put 覆盖第一列。通用表本身三列完整，但 TABLE 事实只剩两列。最终字段名需要统一消歧，稳定列 ID 不能与展示标签混用。

### P2-6：FORCE list 的空项能导致整个解析失败

位置：`device-ops-platform/device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/internal/ListStructureParser.java:47-48,58-71`。

`- first` 后跟 `-   `，正则接受空格后 strip 成空字符串，ListItem 构造抛出 IllegalArgumentException。AUTO 外层可捕获，FORCE 路径不会正常降级。应保留该行并报告格式不匹配，而不是让一条空列表项使整个任务失败。

## 五、推荐增强方向

### 5.1 保留当前两层架构，强化通用结构层

建议稳定流水线为：

```text
原始输入与来源
  → 编码/终端控制清理（保留来源映射）
  → 上下文敏感脱敏
  → 有效命令与块边界
  → 混合结构候选识别、评分和区间分配
  → 通用结构树（有序、保留原文及未解析部分）
  → 显式规则映射到领域对象
```

通用层识别“这是表格/树/记录”，领域规则才解释“这是接口/路由/设备版本”。不根据字段名字直接臆造业务对象，不执行外部任意脚本。

### 5.2 结构解析器应共享的约束

1. **完整性**：每个有效正文范围必须归属结构、文本、明确忽略类别或受限遗漏，不能因首次命中就丢尾。
2. **记录容器**：record 内允许 text/KV/tree/table/list 等有序子结构；与聚合命令只展开一层是不同的嵌套概念。
3. **可信识别**：候选必须返回消费范围、识别依据、警告和未解析行；表格需多行一致性，KV 需冒号上下文判断。
4. **证据定位**：字段、记录、表格行/必要时单元格保留源范围；缩进、Tab 展开和脱敏后仍能定位原行。
5. **重复与空值**：重复键不能覆盖；空串、缺列、未采集、脱敏值需要可区分；父节点可同时有值与子项。
6. **质量**：记录只有身份不能算正文已结构化；有 unparsedLines 应为 PARTIAL；确实无正文才是 EMPTY。
7. **性能**：识别与构造阶段即应用预算；避免在每个文本偏移重复扫描到块末尾。

### 5.3 扩展机制需要从“整命令强制类型”走向“局部格式提示”

当前结构选项基本只有 recordStartPattern、columnNames、configSeparators，且 AUTO 不允许选项；第一条命中的结构规则作用于整个命令。这不足以表达真实混合日志。

推荐增加受白名单约束的声明式能力：

- 表格：区域起止、表头行数、分隔风格、固定列跨度、Tab 宽度、续行策略、重复表头、零/单数据行；
- KV：允许分隔符、紧凑冒号、多 KV、键边界、续行策略、无冒号父标题；
- 记录：开始/结束模式、身份提取、正文 AUTO 子解析；
- 边界：装饰行、段标题、提示符身份、分页痕迹，不把它们粗暴当普通数据；
- 优先级：局部显式提示优先于自动识别；AUTO 保持可回退，不因强制提示失败而丢弃尾部。

继续使用不可变发布和确定性输出。新能力通过新版本发布、显式重解析产生新结果，不改写既有 1.3 黄金制品和历史业务结果。

## 六、验收与优先级

### 第一优先级：安全和不丢内容

- 修复多词敏感值/私钥块脱敏；只使用合成秘密测试。
- 纯星号装饰不再切走正文；真实 session statistic 有结构，空命令恢复为 12。
- TEXT/FORCE 覆盖全部正文，残余内容必须回退或显式标记。
- 接口记录必须有内部结构及未解析段，不能 23 个全空壳仍标 STRUCTURED。

### 第二优先级：覆盖占比最高的结构

- 温度表断言恰为 5 列且具体单元格不碎裂。
- 接口正文覆盖标题、说明、KV、缩进计数器和续行。
- route summary 拆出三个紧凑统计 KV，再识别真正表头。
- CPU/ARP/MAC 单行数据表；列空白、Tab、缩进、边框、连续表格和表尾 KV。
- 配置内容的有序保留，脱敏样本不破坏原缩进形状。

### 第三优先级：通用扩展与可观测性

- 递归结构遍历、语义提取兼容、稳定列 ID、独立预算与解析异常降级。
- 扩展新的厂商和应用日志 corpus 后再接入 CSV/JSONL/事件/堆栈等插件。

验收不应只检查“生成了多少 section / 输出字节是否稳定”。至少分别报告：

- 正文保留率：结构原文、文本回退和显式遗漏是否守恒；
- 正确结构化覆盖率：经人工标注的正文中多少正确进入字段/表/树，而非仅挂在 rawLines；
- 误识别率：被强行拆成错误列、错误键或空壳记录的比例；
- 关键结构的列数、字段值、父子关系、重复顺序与行号准确性；
- 脱敏、截断、限制、失败/空命令状态准确性。

三日志可以作为首批回归基线，但不能据此承诺“任意日志”覆盖率，也不建议在尚无人工标注时给出一个看似精确的总体百分比。

## 七、本次验证与边界

已执行：

```text
mvn -pl device-ops-parser-semantic -am test -Dstyle.color=never
mvn -pl device-ops-parser-semantic -am package -DskipTests -Dstyle.color=never
java -jar device-ops-parser-semantic/target/device-ops-parser-semantic.jar \
  --release-directory parser-releases/device-command-output-1.3.0 \
  --input parser-releases/device-command-output-1.3.0/input-real-sanitized.json \
  --output device-ops-parser-semantic/target/review-probes/replayed-real.json
```

- 核心模块 49 项、语义解析模块 76 项测试通过，共 125 项。
- 重新构建后的完整 CLI 输出与现有 expected-real.json 字节完全一致，结果为 595610 字节；上述真实问题均能在此当前输出中复验。
- 独立合成探针验证了文本/强制分段、表格、KV、记录、配置、预算、事实转换和脱敏边界。
- 黄金测试目前只验证部分数量，并直接断言错误的 13 个 EMPTY、session statistic 为 EMPTY。因此“测试全绿”不构成正确覆盖证明。
- 未运行全平台后端打包、前端测试、浏览器验收、真实设备重采或在线重解析；这些不属于本次只读解析审查范围。
- 业务代码和发布制品未改动；只新增本审查文档。临时复验文件位于被 Git 忽略的 target/，未写入版本化 fixtures。
