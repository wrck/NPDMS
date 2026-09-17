# 项目模板与执行解耦优化：详细设计基线

> 文档编号：NPDMS-TED-DB-1.0  
> 日期：2026-09-17  
> 状态：设计定稿建议，供评审与实施基线固化；不是代码完成或运行验收声明  
> 配套文件：[详细实施计划](../plans/2026-09-17-template-execution-decoupling-implementation-plan-v1.md)  
> 目标定位：在既有模板、规则引擎、正式状态命令及业务实现上演进，不建设第二套业务状态机或项目执行引擎  
> 仓库定位线索：`wrck/NPDMS` / `codex/liteflow-remediation`；本文件未重新读取仓库，实际实施基准 HEAD 必须由 P0 核实

## D00. 文档依据、效力与适用边界

### D00.1 依据分类

| 标识 | 依据与使用方式 |
|---|---|
| SRC-01 | 用户上传的 372 行 Markdown，首节为“结论”，本文简称《调整方案》。附件 SHA-256：`d4beb73c450314f8fe924b76864d1c7b20c352d686e1b0a7c967651c2f0672c7`。核心职责见 L19–46，三条路径见 L65–116，证据见 L118–150，哈希/注册/事实见 L152–194，推进与验收见 L321–364。 |
| SRC-02 | 本会话对《调整方案》的上一轮评审意见：架构方向认可；实施定稿需澄清全入口受控例外、服务端路径判定、契约引用、证据政策、新旧权威写入及交付范围。 |
| DES | 本文为消除上述歧义新增的设计决策，包括字段、枚举、协议、状态、幂等键、兼容矩阵和验收约束。这些是拟采用的规范，不是附件中已经存在的实现事实。 |
| EXT-01～03 | 文末列出的 Spring/AWS 官方资料，仅核验代理、事务事件、Outbox 的工程边界，不要求采用资料页面所显示的框架版本，也不构成升级依赖建议。 |

附件提到的历史代码基准 `9ff2f5b…`、另一分支领域映射、十三个 Owner、旧表清单，均只作为来源中的背景描述。本文不证明其当前状态，也不将另一分支迁移计划自动加入本轮范围。类名和旧路径仅作为 P0 的定位线索；正式改动清单必须以当前源码为准。

本文“必须／不得”表示建议固化后的约束；“首批”表示本轮支持集合；“示例”不得作为运行默认配置。若当前实现无法满足某个必需能力，应拒绝启用相应契约并登记缺口，不能用假 Provider、默认 true 或文档状态代替实现。

### D00.2 对原方案一处文义的明确化〔DES-01〕

“业务职责独立”不等于“所有业务操作都允许在没有项目上下文时执行”。

普通独立操作必须仅凭业务自己的配置、授权、数据状态合法运行；显式全入口受控操作则按服务端登记的控制范围要求执行上下文。只订阅结果的能力不必先迁移同一 Owner 下的全部写操作；一个业务单元通过验收也不代表其整个领域完成独立化。

依据：SRC-01 L67–81、L108–116、L323–331；SRC-02 对独立性前置条件的修正。

## D01. 目标、非目标与完成口径

### D01.1 目标

业务 Owner 拥有业务命令、配置来源、权限、不变量、对象状态和权威结果；项目执行中心拥有节点资格、项目附加约束、证据采纳及正式推进；视图组合交互能力；待办展示当前执行义务。项目模板编排业务结果，不接管业务内容合法性。

本轮必须实现以下不变量：

| 编号 | 解耦不变量 |
|---|---|
| INV-01 | 普通独立操作不因没有活动任务、阶段未激活或项目待办已完成而默认被拒绝。 |
| INV-02 | 项目规则不能补足业务权限，也不能取消业务校验；可显示页面不代表可执行业务操作。 |
| INV-03 | 操作绑定、结果订阅、证据政策、视图绑定可分别存在；纯订阅节点不需要页面、命令处理器或虚拟任务。 |
| INV-04 | 业务对象、业务结果、项目证据拥有不同身份；同对象的新结果不得因对象曾被关联而剔除。 |
| INV-05 | 业务提交成功与项目节点完成是两个事实；项目等待或消费失败不改报业务回滚。 |
| INV-06 | PRE/POST 是同步操作约束；节点完成/准出在独立项目事务判断；POST 不在业务提交后执行。 |
| INV-07 | 已发布执行语义使用完整版本化指纹；改变准入/准出等实际规则必须改变新执行指纹。 |
| INV-08 | 模板不在运行时选择“最新”能力、规则、预设或证据政策。 |
| INV-09 | 普通独立、明确项目办理、全入口受控由服务端入口和部署注册确定，不由请求缺省字段选择。 |
| INV-10 | 新实体或操作版本通过受信能力注册接入，不修改公共控制器/宿主中的业务实体分支。 |
| INV-11 | 事件重复、乱序、早到、迟到、返工与换版按结果身份、证据政策和执行身份处理。 |
| INV-12 | 每个业务实例在任一时刻只有明确的权威写路径；未知请求结果必须先恢复，不能换键重办。 |
| INV-13 | 业务物理位置改变而公开语义未变，不要求模板、订阅或证据政策重写。 |
| INV-14 | 真实权限、对象状态和结果有效性是运行事实，不得冻结成永久授权。 |
| INV-15 | 新旧兼容不成为校验失败后的自动降级通道，不静默重写旧快照或历史证据。 |

### D01.2 非目标与范围保护

本轮不默认实施十三领域全量重迁、全表复制、微服务拆分、换数据库、升级框架、重建规则语言、建设通用 BPMN 平台或让任意 SQL/Bean/URL 进入模板。不开启全仓库“顺手整改”。

业务整改集合记为 C0：由 P0 在当前分支核实本会话目标的实际直接消费者、普通路径和回调后冻结。C0 不能仅因名称出现在另一分支文档而扩张，也不能以一个演示业务通过而缩减已确认的直接消费者。

涉及旧行为改变时，优先在明确的新契约/新承接路径实施；既有独立能力优先复用。是否必须复制某个类/页面，依据本轮已确认的保护清单，不导入其他会话的全模块复制配额。

### D01.3 两种完成状态必须分开

`CODE_AND_TESTS_READY`：阶段所需代码、自动化测试源码、兼容处理和记录齐全，可形成该阶段提交。它不保证构建或真实验收通过。

`VERIFIED`：针对确切 HEAD、测试集合和环境有真实执行证据；不能从旧分支、替身测试、纯函数检查或“测试文件存在”推导。

全链路投入使用必须满足实际范围 C0 和相应验收条件；未实现路径或未关闭的确定性缺陷，不得归类为“仅差真实环境验证”。

## D02. 职责、依赖与调用架构

### D02.1 职责矩阵

| 对象 | 权威写入/判定 | 禁止承担 |
|---|---|---|
| 业务 Owner | 自身配置、命令、权限、版本、不变量、结果身份及结果状态 | 因仅持有 projectId 就自动查找可写项目节点；解释别的 Owner 的内部表 |
| 能力适配/集成边界 | 类型转换、精确注册、执行上下文接入、事务资源检查 | 复制业务状态机、补造事实、绕过 Owner 授权 |
| 项目执行中心 | 节点资格、证据政策、正式完成/准出、计划/轮次竞争 | 判断工勘正文是否合法、复制附件内部结构、直接调用业务 Mapper |
| 规则编译/求值 | 受限类型事实、精确程序闭包、三态条件求值 | 执行业务命令、任意查询、隐式采用最新程序 |
| 视图宿主/工作台 | 选对象、调用已登记动作、分轴展示能力、保护编辑状态 | 依据 HTTP 200、按钮回调或页面打开直接完成节点 |
| 待办投影 | 展示执行义务、责任主体、等待原因及入口 | 拥有独立业务完成状态机或成为操作授权事实源 |
| Outbox/投递层 | 提交后可靠投递、重试、传输幂等 | 决定证据适用性或节点完成 |

依据：SRC-01 L19–46、L83–106。以上物理组件边界是逻辑约束，不强制新增同名服务。

### D02.2 依赖方向

```text
普通业务入口 ────────────────> 业务公开应用边界 ─> 业务数据/权威结果
                                      ↑
项目办理入口 -> 受信注册/执行器 -> 类型化业务适配
                    │                 │
                    └─ PRE/POST       └─ 业务提交事务中的结果记录/Outbox

已提交结果 -> 订阅匹配 -> 证据判定 -> 项目正式状态命令 -> 待办投影

业务视图宿主 = 业务可见性/权限 + 项目资格 + 视图状态 + 逐操作规则观察
```

普通业务核心不依赖项目运行轮次、活动任务、待办状态和项目页面注册。允许在同一 Maven 模块内实现上述逻辑边界；跨界依赖只能经过公开契约。架构测试检查实际依赖，不以改包名作为独立化证据。

### D02.3 注解与 Scope 的位置〔DES-02〕

执行注解是受信命令的接入元数据，不是第二份可编辑规则源。注解只声明稳定操作身份、精确版本及控制范围；实际前后置规则来自被冻结的操作绑定契约。

`ProjectOwnerOperationScope` 或等价现有机制仅用于已验证同步事务内的实际业务回调，关联源对象与 Owner 成功创建的派生对象。它不是普通实体必须携带的项目协议，不传入消息、不序列化为客户端许可、不跨线程复用、不替代事务。

全入口受控必须在真正拥有写权限的应用边界强制执行。不能只检查项目 Controller 或靠代理外观证明覆盖；同类自调用、原始对象调用等绕过路径必须有架构/集成测试。Spring 代理存在自调用绕过这一工程边界〔EXT-01〕。

## D03. 标识体系与版本约定〔DES-03〕

以下名称是新增逻辑契约，最终 Java/TypeScript 类型名可按仓库规范映射，语义不得丢失。

| 标识 | 组成/要求 | 不得混用 |
|---|---|---|
| CapabilityRef | `capabilityKey + capabilityVersion`；精确版本 | 不是 Java 类名、Maven 模块或页面名称 |
| OperationRef | `capabilityRef + operationCode + operationVersion` | 不是权限码或任意 API URL |
| BusinessObjectRef | `tenant + owner + objectType + objectId` | 不是 taskId、resultId 或业务修订号 |
| BusinessRevisionRef | 由 Owner 定义的 `objectRef + revisionId/revisionNo` | 修订序号与并发行版本分别保存 |
| BusinessResultRef | `tenant + owner + resultType + resultId + resultSchemaVersion` | 事件 ID、命令 ID 不能代替结果 ID |
| ResultVersion | 不可变结果内容版本/哈希；含可验证来源 | 不等于当前有效状态版本 |
| ResultStatusVersion | 撤销、替代、过期等状态变更的单调版本 | 不按消息到达时间覆盖 |
| EventRef | `producerNamespace + eventId + eventSchemaVersion` | 同一结果可关联多次不同事件 |
| ExecutionRef | `projectId + planVersionId + nodeKind + nodeInstanceId + executionId + roundNo + contractId/Version` | 阶段不伪造成任务；运行版本与定义版本分离 |
| SubscriptionRef | `executionRef + subscriptionKey + generation` | 不能只使用项目 ID 或 source eventId |
| EvidenceRef | `subscriptionRef + policyHash + businessResultRef + identityVersion` | 同一结果可供多个节点分别采纳 |
| CommandRef | `tenant + actor + authorityScope + operationRef + idempotencyKey` | 项目重评事件不算新的业务命令 |

ID 在 JSON 中使用字符串或明确的无损编码。不会把超过安全整数范围的 ID 转成 JavaScript Number；各 Owner 仍可在内部使用自己的合法 ID 类型。

版本分三层：契约 Schema、能力/操作语义、运行对象并发版本。支持一个 Schema 版本不代表支持该能力所有语义版本。兼容转换器须登记源/目标精确版本并经过回归；未知版本拒绝解释。

所有运行身份和租户/主体均由服务端建立或复核。请求携带的版本是并发预期，不是授权断言。

## D04. 五类契约与编译产物

### D04.1 总体封套〔DES-04〕

建议为新语义引入显式子契约，名称暂定 `executionSemanticsVersion=DECOUPLED_1`。它不直接改写历史外层 Schema，也不假设当前仓库已有某个可复用数字版本。P0 必须登记旧格式到旧解释器的精确映射。

新冻结产物包含：

| 字段 | 说明 |
|---|---|
| executionSemanticsVersion / compilerVersion | 语义解释器与编译产物版本 |
| hashProfile | 完整哈希算法与规范化规则版本 |
| templateIdentity | 已发布模板/版本身份；发布记录元数据与执行语义分开 |
| nodeContracts | 阶段/任务共用模型，保留 nodeKind 差异 |
| operationBindings | 可选集合，显式 `NONE` 或 `CONFIGURED` |
| resultSubscriptions | 可选集合，独立于 operationBindings |
| evidencePolicies | 被订阅/完成条件精确引用的政策 |
| presentationBindings | 可选集合，不成为业务结果真值来源 |
| ruleClosure | 所有执行引用的精确程序递归闭包 |
| capabilityPins | 所有实际依赖能力的精确语义版本/指纹 |
| executionSemanticHash | 执行行为语义指纹 |
| presentationHash | 纯呈现配置指纹 |
| frozenArtifactHash | 整个冻结产物的完整性摘要，不等同执行语义摘要 |

在 `DECOUPLED_1` 中缺少必需的字段是错误；不以空字段或 NULL 恢复旧解释。旧格式缺少新字段则由旧解释器处理。数组内重复键、同一节点重复主绑定、无效引用、未知 mode/version 均拒绝编译。

### D04.2 节点执行契约

| 字段 | 必需性与规则 |
|---|---|
| nodeKey / nodeKind | 稳定设计身份；TASK 或 STAGE，不从视图推断 |
| dependencies | 明确前驱和依赖含义；按既有引擎能力校验环与可达性 |
| admissionCondition | `NONE` 或 `RULE(ref)`；仍须满足正式生命周期基本约束 |
| completionCondition | 必须显式给出受支持条件，不以操作成功默认 true |
| exitCondition | `NONE` 或 `RULE(ref)`；是否需要收口按既有正式状态机 |
| bindingRefs | 分别引用操作、订阅、证据与呈现；不存在的可选职责显式 NONE |
| executionRestrictions | 当前节点额外资格/分派限制，不能替代 Owner 权限 |
| reworkPolicyRef | 返工/换版对轮次与证据重评的精确政策 |

原生手工任务、审批任务等既有能力保留原解释，不为了“统一”强行变成业务结果实体。纯阶段汇总允许没有业务操作、没有页面、没有 taskId。

### D04.3 操作绑定契约

| 字段 | 规范 |
|---|---|
| operationRef | 已部署且精确匹配的受信操作 |
| objectSelectorRef | Owner 支持的对象选择方式；必须重新核对所选对象范围 |
| businessConfigSelection | 可显式传递已验证业务配置版本；不能强迫业务临时从活动节点取配置 |
| controlScope | `PROJECT_ENTRY_ONLY` 或 `ALL_ENTRIES`；必须符合部署注册，不由模板升级权限范围 |
| pre / post | 各自明确 `NONE` 或 `RULE(ref)`，不省略为默认放行 |
| expectedInputSchema | 精确输入类型；未知可执行字段、主体字段、模式开关拒绝 |
| resultShapeRef | 命令返回的业务结果/对象摘要类型，不自动绑定完成条件 |
| transactionProfile | 允许使用的事务保证；不支持原子 POST 的操作不能声明原子约束 |

模板不能把 `PROJECT_ENTRY_ONLY` 操作静默升级为 ALL_ENTRIES。业务部署层改变全入口控制属于新版本能力发布和显式迁移，不通过编辑某个项目模板隐式改变其他调用方。

### D04.4 结果订阅契约

订阅字段包括 `subscriptionKey`、精确 `resultCapabilityRef`、`resultTypes`、Owner 可执行的范围选择器、历史读取策略、`evidencePolicyRef`、基数/聚合方式、匹配必要的事实输入以及结果变化兴趣。

选择器只读取声明过的字段和范围，不运行 arbitrary SQL/脚本。对象 ID、修订 ID 或项目归属必须有 Owner 侧授权/系统事实读取协议核对。订阅不是业务写授权。

首批聚合语义：
`EXACT_ONE`、`ANY_MATCHING`、`ALL_EXPECTED`。
要求选出一项具体结果时，可显式采用 `EXPLICIT_RESULT` 或 `OWNER_ORDERED_LATEST`，后者只允许 Owner 提供可信顺序的结果集合。禁止默取第一条。
`ALL_EXPECTED` 必须引用冻结的预期对象集合；若允许动态增减集合，必须版本化改变集合并重评，不能把一次分页结果当作“全部”。

### D04.5 证据政策与呈现契约

证据政策定义“哪个结果为什么能供这一轮使用”，见 D10；呈现定义页面/表单精确版本、动作映射、选对象方式和只读规则，见 D12。两者互不推导。

纯展示标签、颜色、布局仅进入 presentationHash；若配置改变业务选择器、可执行操作、事实访问边界或状态判断，则必须归入对应执行契约及 executionSemanticHash，不能以“前端配置”名义漏出指纹。

### D04.6 发布能力矩阵

| 节点配置 | 必需能力 | 非必需能力 |
|---|---|---|
| 只等待业务结果 | 结果查询/变化读取、选择器、证据政策和完成条件解释 | 命令处理器、业务编辑页 |
| 只办理且不自动完成 | 类型化操作、权限、事务/前后置能力及节点资格 | 结果订阅；可选其他正式完成条件 |
| 办理后等结果 | 上述操作能力 + 独立订阅能力 | “操作成功即完成”的隐式耦合 |
| 原生手工节点 | 原生正式办理协议与配置 | 业务 Owner 伪实体 |
| 汇总阶段 | 依赖/结果聚合/正式完成及准出 | 虚拟任务与页面 |
| 历史只读呈现 | 已冻结展示描述及安全读取能力 | 当前业务写权限 |

草稿可保存合法但部署暂不可用的引用，并显示不可发布原因；正式发布/计划生效必须校验本节点实际使用的能力。不能以“操作处理器未部署”拒绝一个没有操作绑定的纯订阅节点。


## D05. 编译、冻结与完整执行语义哈希

### D05.1 编译管线〔DES-05〕

```text
读取明确格式版本
  -> Schema/未知字段校验
  -> 受信能力与精确版本解析
  -> 五类契约交叉引用及可用性校验
  -> 规则/选择器/政策递归闭包
  -> 类型、检查点、事务与事实能力校验
  -> 规范化语义投影
  -> 执行指纹 + 呈现指纹 + 冻结产物摘要
  -> 不可变发布产物
```

校验与生成必须对同一份逻辑输入进行，不能在校验和冻结之间重新解析“最新”规则或目录。发布需要对被依赖版本进行复核；复核失败应终止此次发布，不替换为另一个版本。

程序闭包从准入、完成、准出、模板适用、项目收口、操作 PRE/POST、订阅选择/过滤、证据政策中的规则引用出发递归遍历。决策表、被引用规则、字段类型、比较器、默认分支、未知值策略、语言/编译语义版本均进入闭包。缺失引用、非法循环、同键异义及不支持的依赖明确失败。

未被执行引用的编辑草稿规则不必改变 executionSemanticHash，但仍进入 frozenArtifactHash，以保证完整产物可核对。引用标识本身属于契约身份；首批不承诺“任意规则改名仍保持相同执行哈希”。

### D05.2 新哈希剖面与内容

新哈希剖面暂定 `PROJ_EXEC_SEM_1`，使用 SHA-256 对版本化规范投影的 UTF-8 字节求摘要。名称和字段为本设计定义；实际与旧版本不冲突的编码由 P0 固化。

| 必须纳入执行指纹 | 不作为永久执行事实冻结 |
|---|---|
| 完整节点身份、依赖、生命周期语义限制 | 当前用户是否有权限、当前任务状态 |
| 所有执行规则引用及其递归程序闭包 | 本次输入、本次业务结果值、当前有效性查询值 |
| 操作版本、控制范围、对象选择器、配置约束 | 命令 receipt、eventId、运行 executionId |
| 订阅范围、匹配基数、结果类型/版本 | 订阅处理游标的当前数值 |
| 证据复用模式、基线选择政策、有效性及失效处理 | 每轮实际基线 token、实时风险观察 |
| 影响解释的编译器/事实 Schema/能力语义版本 | 审计时间、布局、纯展示名称、物理 Java 包位置 |

业务配置选择若影响执行解释，应纳入执行契约；业务独立配置的实际值和版本仍由 Owner 管理，在创建时明确解析并留存来源。不要通过“所有配置都只算呈现”漏掉真实行为。

规范化要求：按 Schema 分类对象字段；无序集合按稳定键排序，有序列表保留顺序；区分缺失/NULL/空串/0/false；数字使用类型约定的精确形式，拒绝 NaN/Infinity；ID 不做有损转换；时间使用明确时区/精度；字符串不随意 trim、改大小写或重写业务键。规范化规则改变时升级 hashProfile。

新增可执行字段必须登记为执行语义或明确非语义。测试应枚举 Schema 的相关字段，防止“增加了运行字段但忘记加入投影”。未知字段不能默默被旧编译器丢弃。

### D05.3 历史算法与防降级

P0 留存已发布旧格式的原始字节、原指纹、原编译器/格式组合及固定金样。旧实例继续选择对应旧验证器；缺少新 hashProfile 的历史记录，只允许通过已登记的旧格式判别，不能对任意文档推断为 legacy。

新产物必须有明确版本。未知版本、声明新版本却缺字段、引用新字段后强行切换旧算法都拒绝。禁止“先试新哈希，失败再试旧哈希”的通用回退。

旧算法完整性不足的事实必须保留：旧指纹相等不能证明完整新语义相等。旧实例迁入新契约必须从受信旧原文形成显式迁移草稿，重新审查遗漏字段、编译新产物、建立映射后生效；不能重算后覆盖旧记录并声称历史天然可信。

### D05.4 指纹用途边界

executionSemanticHash 用于比较/核验被冻结的执行语义，不是签名、授权令牌或事实有效性证明。frozenArtifactHash 用于字节/完整产物核验，不用它将纯布局变化误判成业务契约改变。

每个运行实例保存其解释版本与精确模板/计划引用，不能依赖当前应用的默认编译器重新构建后覆盖历史。新算法的可用性检查不等同操作运行适配已完成。

## D06. 受信能力目录与通用集成

### D06.1 部署注册描述〔DES-06〕

一个能力可只提供查询/结果，也可提供命令、事实和视图。不得要求所有能力实现同一套空接口。

| 注册内容 | 具体要求 |
|---|---|
| capabilityKey / version / semanticDigest | 稳定公开身份，精确版本不可原地改义 |
| Owner / objectTypes | 权威领域与允许对象类型；不根据 operationCode 前缀猜测 |
| operationDescriptors | 操作身份/版本、输入输出类型、独立入口支持、控制范围 |
| commandHandler | 代码登记的类型化处理器；模板只引用操作，不选实现类 |
| authorizationProvider | 按主体和真实目标检查业务读/写权限；禁止以旧 Controller 注解为唯一检查 |
| configResolver | 业务创建/办理的明确配置来源、精确选择与兼容策略 |
| factDescriptors | 字段类型、可用检查点、敏感级别、一致性及结果来源 |
| resultProvider | 历史/指定结果读取、当前状态断言、稳定游标、变化流及支持政策 |
| transactionCapabilities | 本地同事务、只读外部断言或不支持原子 POST 等能力 |
| viewDescriptors | 视图 Schema/版本、动作与 OperationRef 映射、只读/文件动作 |
| compatibilityAdapters | 明确源/目标版本、语义约束和测试证据，不自动选最新 |

注册时校验重复 OperationRef、一个引用匹配多个处理器、声明却不存在的类型/方法，以及无法实现的事务能力。启动时不能静默覆盖重复注册；隔离出错能力时应明确不可用，不能换用另一业务处理器。

公开控制器只负责协议、安全封套和目录分派。公共宿主根据精确动作描述呈现，不写 `if Owner=...`、硬编码实体动作表或 `version=1` 兜底。具体业务字段仍由业务组件/类型化适配器表达，不能把“去掉中央分支”变成通用 JSON 任意业务写入器。

### D06.2 业务配置独立性

普通业务的表单/修订/业务策略来源由 Owner 配置解析器确定，可来自明确已发布配置、实例冻结配置或业务自身版本化默认策略。不得从“当前可写任务”隐式寻找唯一配置来源。

项目模板可以传入显式业务配置选择，但 Owner 必须验证其类型、版本、对象范围及业务适用性；选择成功后形成业务配置引用和来源记录。业务后续合法修改不要求原节点继续可写。

一项能力声明 `supportsIndependent=false` 时，不能宣称普通独立路径已经实现。只对明确 ALL_ENTRIES 的操作允许缺少无项目写入口；它的结果读取仍应遵守独立 Owner 协议，而非要求伪造任务身份。

### D06.3 命令与结果接口形状

以下为语义接口，不是声称仓库已存在这些类。所有读取、断言、游标及写入调用都必须携带或从受信调用上下文取得租户、主体和范围；参数简写不表示可以省略授权：

```text
CapabilityCatalog.resolve(exactRef) -> trustedDescriptor

BusinessConfigResolver.resolve(actor, objectOrCreateScope, exactSelection?) -> businessConfigRef
BusinessAuthorization.authorize(actor, operationRef, actualObjectRefOrScope) -> authorizationObservation

TypedCommandHandler.execute(OwnerCommandContext, TypedInput) -> BusinessCommandOutcome
BusinessResultProvider.read(resultRef, authorizedReadContext) -> immutableResult
BusinessResultProvider.assertStatus(resultRef, expectedStatusVersion, consistencyProfile) -> statusAssertion
BusinessResultProvider.readSnapshot(selector, watermark, pageCursor) -> resultPage
BusinessResultProvider.readChanges(selector, afterCursor, throughWatermark?) -> changePage

ProjectOperationService.execute(trustedIngress, commandEnvelope) -> committedBusinessReceipt
ProjectEvidenceService.evaluate(subscriptionRef, candidateResultRef) -> evidenceDecision
```

后台项目消费者使用受限的系统事实读取契约，只能读取订阅所需事实摘要；不得冒充当年操作者、管理员或绕过业务数据范围访问正文。用户视图用当前用户授权，不将后台读取权限赋予浏览器。

## D07. 三条运行路径、控制范围与事务

### D07.1 服务端路径判定〔DES-07〕

路径分类是入口协议和注册能力的结果，而非请求可写字段。

| 实际入口/能力 | 判定 | 缺失或异常处理 |
|---|---|---|
| 历史入口对应历史实例 | LEGACY_COMPAT，使用已冻结解释 | 不导入新模板默认值；未知迁移状态拒绝 |
| 新独立入口 + PROJECT_ENTRY_ONLY 操作 | INDEPENDENT | 不查活动任务；Owner 业务条件不满足照常拒绝 |
| 新项目办理入口 | PROJECT_BOUND | 执行身份必需；省略、过期、多匹配拒绝，不降级 |
| 部署注册为 ALL_ENTRIES 的操作 | ALL_ENTRIES_CONTROLLED | 业务统一写边界每条入口强制要求有效上下文；没有匹配政策不能默认为无约束 |

客户端禁止声明 `skipCheck`、`writable=true`、`controlScope=INDEPENDENT`、`verifiedFrame`。节点与对象选择可作为预期输入，但均需服务端复核。恰有一个活动任务也不构成“可自动取第一条”的授权理由。

### D07.2 普通业务路径

```text
认证主体 -> 确认实例权威写路径
  -> Owner 配置解析/对象范围/业务权限
  -> 业务状态与版本检查
  -> 业务命令
  -> 同事务记录权威业务结果及 Outbox
  -> 业务提交回执
```

新独立命令的内部依赖也不得回到活动 WorkBinding、节点许可或待办状态。只在外层 Scope 中直接调用旧业务服务不算完成独立化。原来源快照可用于追溯或明确的业务配置读取，不自动恢复其项目写许可含义。

### D07.3 项目办理事务

进入事务之前可以做轻量结构/认证检查，但关键资格不能只在事务外观察一次。推荐序列如下，具体锁顺序由 P0 与现有正式写入路径统一：

1. 认证真实主体，解析受信 OperationRef，检查实例权威写路径，校验命令封套与请求摘要。
2. 安全幂等查询：同键同摘要的已成功结果在当前重放读取授权允许时返回原回执；不同摘要冲突，执行中返回可恢复状态。
3. 对新工作取得统一锁/并发协议：权威写入栅栏、项目有效计划、目标轮次/契约、实际业务对象或创建范围。
4. 重新核对操作与节点绑定、主体职责、Owner 动作、实际目标、计划/轮次/对象预期版本及配置。
5. 用冻结规则与本事务可信事实求值 PRE；不满足或 UNKNOWN 拒绝，不执行副作用。
6. 按现有正式接口开始必要的阶段/任务办理；开始动作若更新观察版本，形成内部新执行向量，但原请求摘要和幂等身份不变。
7. 执行类型化 Owner 命令，保留原业务校验；Owner 形成结果摘要和事务内结果记录/Outbox。
8. 重新获取可能变化的事实，执行 POST；失败将业务数据、结果记录、成功幂等记录与 Outbox 一并回滚。
9. 提交后返回业务回执；项目证据与节点完成在其他事务处理。

同一个业务事实只由一个权威生产机制生成：优先 Owner 原生结果；旧原生消息通过单一受信适配器转换。项目执行器不能再独立生成第二份含义相同但 resultId 不同的“成功事实”。底层重试沿用同一业务结果身份。

可失败的外部不可逆操作、未加入本地事务的服务调用不得放进要求原子 POST 的处理器。首批严格 POST 只支持能证明同一事务参与的资源；外部操作需要单独能力语义和补偿设计时，超出本轮默认范围。

### D07.4 前置、后置、完成、准出

| 检查点 | 输入/事实 | 失败效果 |
|---|---|---|
| ADMISSION | 节点依赖、当前证据和准入规则 | 不激活该节点，不反向阻止普通独立业务 |
| PRE | 节点资格、Owner 事实、已验证命令输入、事务快照 | 本次业务不执行 |
| POST | 本事务业务结果摘要和一致事实 | 同事务回滚；不是提交后的完成规则 |
| COMPLETION | 已提交权威结果、证据政策、完成条件 | 业务成功保留，项目等待或重试 |
| EXIT | 已满足完成条件后的正式准出/收口检查 | 不推进项目，不否定已提交业务 |

规则条件只有 `MATCHED / NOT_MATCHED / UNKNOWN` 三态；执行异常以错误类别附加，不能吞掉当成 NOT_MATCHED。`NONE` 是显式没有该附加规则，不表示没有业务权限或节点资格要求。

已完成节点重放原业务回执不能再次执行 PRE/POST 或再次产生结果；但仍须验证当前主体可读取相应回执。对象已删除时只能通过 Owner 的安全回执/墓碑读取协议确认原请求，不因此泄露原正文。

### D07.5 幂等及不确定响应

命令摘要包括实际操作及版本、最初提交的目标/执行预期、业务配置选择、Schema 化输入和业务版本。它不因服务器生成 correlationId、内部开始阶段后的版本改变或 JSON 键顺序而变化。摘要规范本身版本化，不能由调用者更换。

客户端在第一个 await 前冻结操作、目标、输入、预期版本及幂等键。请求已发出而网络/响应格式不确定时，保留原封套和键；不重新取得新轮次、新业务版本后拼成“重试”。恢复被拒绝不证明先前未提交。

成功回执必须满足结果 Schema，不能把 `{}`、未解包信封或 HTTP 200 当成业务成功。当前主体、租户或权威写路径改变后不自动重放；提供明确恢复失败及人工核对入口。若实现只保存内存恢复信息，不能声称跨刷新/跨会话恢复已支持；发布前应至少提供服务端按原 CommandRef 查询安全回执的协议。

### D07.6 派生对象、同线程 Scope 与全入口证明

每次 Owner 命令必须由实际方法和业务数据独立声明操作/源对象，不直接复制外层已验证 Frame 当作证据。CREATE 的无源对象、COPY 的源修订与新草稿、COMPLETE 的当前修订分别表达。

新对象只在成功插入后登记成当前命令派生目标；表单/文件回调核对真实目标、主体和执行身份。嵌套命令必须拥有自己的验证；跨实体、跨操作、跨线程、过期或原始四参数请求不能借用证明。

全入口受控须盘点 HTTP、定时任务、消息、导入、内部调用等真正写入口，并证明不可绕过。仅在项目门面添加注解不属于 ALL_ENTRIES。没有全入口需求的操作不应被这一机制强行变成项目依赖。


## D08. 类型化事实与规则检查点

### D08.1 四类事实〔DES-08〕

| 命名空间 | 来源与责任 | 可用时点 |
|---|---|---|
| PROJECT | 执行中心已授权的项目/节点事实 | ADMISSION、PRE、COMPLETION、EXIT；POST 重新获取事务内可能变化的值 |
| BUSINESS | Owner 提供的受限对象事实/当前状态 | 按 Owner 声明的检查点和一致性能力 |
| COMMAND_INPUT | 通过精确输入 Schema 和授权范围验证的本次命令值 | PRE、POST；不能冒充提交后持久事实 |
| TRANSACTION_RESULT | Owner 命令在本事务形成的类型化结果摘要 | POST；提交前不能作为其他节点已提交证据 |

事实描述必须声明 `factCode`、Schema/类型版本、空值语义、允许检查点、读取权限、是否敏感、是否可在本地事务一致获取、以及依赖关系。不支持的事实在发布/编译时拒绝；临时不可用在运行时返回 UNKNOWN。

用户可以配置的只是 Owner 公开字段，不是对象任意属性路径或 Mapper 查询。模板不能据此读取密码、附件私有结构或未授权业务正文。命令输入中的金额/选项等是否业务合法仍由 Owner 校验；PRE/POST 只能追加项目条件。

### D08.2 求值一致性和诊断

规则求值引用冻结程序与事实 Schema。一次判定应记录必要的事实版本/结果引用、规则指纹、检查点和结论；不必把全部敏感正文写进项目审计。

能力 GET 是观察值：可显示“前置不满足”“事实暂不可用”“后置尚未产生”，但不能把 POST 未求值预填为通过。真正命令在锁/版本保护下重新获取事实。

多个 Owner 的当前事实只有在声明的相同一致性边界内才能被当作同一时点判断。首批强原子 POST 仅支持同一实际事务参与者；跨 Owner/远程事实无法保证时拒绝该原子规则配置，不用“先查一次”假装同事务一致。

## D09. 权威结果、事件与独立订阅

### D09.1 结果与状态分离〔DES-09〕

权威业务结果是可定位、可回读的业务事实，不等同于对象最新字段拼接。结果正文或摘要一经确定不可原地改义；新的事实内容使用新的结果身份或 Owner 明确的版本。

建议逻辑结构：

| 结构 | 核心内容 |
|---|---|
| BusinessResult | ResultRef、ObjectRef、可选 RevisionRef、结果类型/Schema、业务含义、形成时间、来源命令、内容摘要/哈希；支持 NEW_RESULT 时还需 formationPosition 与 cursorNamespace |
| ResultStatus | ResultRef、状态及状态版本、生效区间、替代结果、原因、纠正效力范围 |
| ResultChange | EventRef、ResultRef、变化类型、Owner 顺序/游标、前后状态版本、项目/范围相关的最小索引 |
| CommandReceipt | CommandRef、实际目标、结果引用集合、提交状态、可安全重放摘要 |

同一对象可以生成 R1、R2；同一结果也可以经历发布、撤销、替代等状态变化。`resultId` 必须由业务权威语义确定，不能每次事件重投都生成新 resultId。单纯重投消息或修改状态版本不能自动满足“新业务结果”要求。

首批变化类型覆盖结果形成、失效、撤销、替代；定时到期可通过确定的有效区间和到期重评处理，不必强行伪造一次业务确认。未知类型不映射为成功。

历史事实被事后撤销，和原事实被纠正为“从未成立”，含义不同。Owner 应区分前瞻失效与追溯纠正；项目按证据有效性政策解释。不能因为是历史事实政策，就忽略 Owner 明确的追溯纠正。

### D09.2 同事务生产与事件信封

业务数据变更、结果记录和对应 Outbox 在同一业务事务落地；提交后监听只负责唤醒投递，不是结果首次落盘的位置。投递失败不改变已提交业务状态；重复事件必须被幂等消费〔EXT-02、EXT-03〕。

事件信封包含：Producer/Event 身份、受信租户、业务 Owner、ResultRef、ObjectRef、可选 RevisionRef、变化版本/游标、发生时间、最小业务范围及追踪信息。项目 executionId 不是业务结果必填字段；有受控办理关联时可存在集成侧关系记录，不能反过来使普通独立业务必须携带项目轮次。

对象删除后需要重新判定证据时，应产生可验证的删除/失效事实或保留受限墓碑；不能查询不到就把旧成功当成仍有效，也不能查询不到就补造成功。没有可回读历史能力的旧数据应明确标注，不通过猜测重建历史结论。

### D09.3 独立订阅匹配

匹配入口只依赖 resultSubscription，不检查 operationContract 或业务页面是否存在。先按租户、Owner、结果类型、范围和订阅 generation 选出潜在目标，再由证据判定核验精确对象、版本及政策。

范围匹配和证据满足分开：匹配某个项目不意味着满足该项目全部节点。一个结果可以分别被多个节点采纳，是否跨节点共享由各自政策决定。不得在业务对象记录上只放一个“已被任务使用”标识封死复用。

无法精确确定目标时进入 `PENDING_SELECTION`，保留候选与安全原因；人工选择仍必须由系统重新校验实际 ResultRef，不允许直接人工写“完成”。`ALL_EXPECTED` 的集合为空时是否允许满足必须显式配置，首批缺省为不满足，避免空集误通过。

### D09.4 游标契约与无缺口衔接〔DES-10〕

Owner 的游标必须表示一个**已经完整提交、可稳定回读的前缀**。数据库自增 ID、消息到达时间和一次 `MAX(id)` 不能天然证明此前较小 ID 的事务不会稍后提交。P3 必须提供并发测试证明这一点。

推荐复用现有持久结果变更记录；缺失时可在当前存储内补齐最小结果日志或等价能力。本文不要求建设事件溯源平台。游标为 Owner 命名空间内的 opaque token，不在项目侧比较来自不同 Owner 的数值大小。

协议如下：

1. **建立订阅 generation。** 持久化执行身份、selectorHash、policyHash 和技术状态 `CATCHING_UP`；激活后事件允许暂存候选，但不能假装已完成全量核对。
2. **取得稳定高水位 H。** Owner 返回一个有稳定快照语义的 token，并声明历史读取保留范围。NEW_RESULT 使用独立已冻结的轮次基线 B，不得把本次 H 偷换成 B。
3. **分页读取历史。** 按政策查询至 H 的结果/状态或所需区间。使用 Owner 返回的 page cursor；候选处理与扫描 checkpoint 在同一项目事务持久化。不能只处理第一页，不能按 offset 跳过并发变化。
4. **增量接续。** 从 H 之后读取权威变化流；消息投递用于及时唤醒，但仍能从持久变化流验证和补齐缺口。H 之前的重复/交错消息按结果身份和状态版本去重。
5. **进入 LIVE。** 原子保存历史完成标识、已追平游标、订阅 generation 和当前证据版本。进入 LIVE 不等于节点完成，仍需正式完成/准出检查。
6. **恢复。** 中断从持久 checkpoint 继续；cursor 已过保留期时进入 `RECOVERY_REQUIRED`，执行有边界重建核对。禁止静默把游标设置为“现在”。

创建订阅前的结果也能被后建订阅采纳；全局源事件已处理不会阻止新 generation 的历史核对。计划变更/返工产生新的订阅 generation；旧 generation 可记录过期目标，但不能转投新轮次。

### D09.5 技术状态、传输 ACK 与退避

订阅技术状态为 `INITIALIZING / CATCHING_UP / LIVE / PAUSED / RECOVERY_REQUIRED / RETIRED`。这些只描述结果消费进度，不替代项目生命周期或业务状态机。

传输 ACK 表示消息已被可靠接收/记录或可判定无需本目标处理，不表示节点满足。事实暂不可用的候选可先持久化再 ACK 传输，由候选重评恢复；未持久化就 ACK 属于丢失风险。

临时错误采用有界退避与可观察积压；格式/版本不支持进入隔离和人工处理，不无限热重试。错误日志不泄露业务正文。无订阅是合法接收结果，不要求业务事务失败。

## D10. 证据政策与生命周期

### D10.1 首批政策维度〔DES-11〕

| 维度 | 首批值与语义 |
|---|---|
| acquisitionMode | `REUSE_EXISTING`：允许既有结果；`NEW_RESULT`：结果形成版本晚于本轮基线；`PINNED_RESULT`：只允许指定 ResultRef/RevisionRef |
| validityMode | `HISTORICAL_FACT`：在指定业务时间/版本曾成立；`CURRENT_VALID`：采纳及正式推进时仍有效 |
| selectionMode | `EXACT_ONE / ANY_MATCHING / ALL_EXPECTED`；需要具体胜出项时显式选择或 Owner 稳定顺序 |
| reuseScope | 明确当前节点、多个节点或受限范围的可复用规则；不使用全对象 once-only 标识 |
| invalidationBeforeCompletion | `REQUIRE_REPLACEMENT`：失效后重新等待，默认；有业务明确允许的历史政策才可继续使用 |
| invalidationAfterCompletion | 首批默认 `MARK_EXCEPTION`：记录风险/影响并走正式处理；可配置 `REQUEST_REWORK`，仅创建受控返工请求，不直接回退状态 |
| consistencyRequirement | 首批强当前有效证据用 `ATOMIC_WITH_TRANSITION`；不具备参与能力则拒绝启用，不假装获得全局原子性 |
| clockPolicy | 到期判断使用明确服务端时钟和时区；事件到达时间不是结果形成基线 |

`PINNED_RESULT` 的对象不存在、不匹配、失效或没有权限时必须等待/拒绝，不能替换为最新结果。选择“复用”也不表示忽略类型、范围、事实内容和有效性检查。

### D10.2 NEW_RESULT 基线的确定

首批基线时点固定为 `ROUND_OPEN`：执行中心正式开放新轮次之前，为其订阅取得并持久化各 Owner 的稳定结果形成水位 B。语义是“结果形成位于本轮基线之后”，不是“消息在页面打开后到达”。

若各 Owner 不能在本地同一事务取得水位，则该轮处于技术准备态，不能对其发起受控新工作；取得并保存所有基线之后才开放轮次。不同 Owner 的 B 是向量，不比较为一个全局序号。不承诺跨服务同一墙钟瞬间。

比较使用结果不可变的 formationPosition 与同一 cursorNamespace/epoch 下的 B；状态变更的 changeCursor 不能替代形成位置。不同命名空间必须由 Owner 提供明确转换或拒绝比较。

同一对象的新修订/新结果可以满足 NEW_RESULT。旧结果的重复事件、重新投递、单纯状态版本增长不算新结果。Owner 没有可信形成序列或等价基线断言能力时，不允许发布 NEW_RESULT 政策，不能改用消息时间凑数。

返工产生新轮次和新基线。旧证据可以保留审计引用，但不能直接复制成新轮次的 ACCEPTED。政策允许复用时仍需在新 generation 重新评估。

### D10.3 证据判定流程

```text
验证租户/订阅 generation/执行身份
 -> 从 Owner 回读精确结果及相关状态
 -> 校验结果 Schema、对象范围和修订关系
 -> 判定获取政策（复用/新结果/指定结果）
 -> 判定有效性及时间区间
 -> 执行订阅过滤/证据内容规则
 -> 处理基数/多匹配
 -> 持久化 EvidenceDecision
 -> 请求正式完成/准出重评
```

任何一步需要但无法取得的事实产生 `PENDING` 或 `UNKNOWN`，并保留原因和重试条件；不写成“没有结果”或“业务失败”。`REJECTED` 用于确定不适用的结果，后续政策/generation 变化可以形成新的判定，不覆写历史结论。

### D10.4 状态及去重

证据技术状态首批采用 `CANDIDATE / PENDING / ACCEPTED / REJECTED / INVALIDATED / SUPERSEDED`。正式完成记录引用当时的证据版本和规则指纹。以后证据失效不删除历史转换记录。

| 层次 | 幂等身份 | 不允许的混用 |
|---|---|---|
| 源接收 | producerNamespace + eventId；同 ID 异载荷隔离 | 用一次源去重阻止未来订阅 |
| 结果索引更新 | ResultRef + ResultStatusVersion/formationVersion | 用较晚到达的旧状态覆盖新状态 |
| 节点候选/证据 | subscriptionGeneration + policyHash + ResultRef + 必要结果版本 | 只按 objectId 或只按 eventId |
| 正式状态转换 | executionId + 原状态/版本 + transitionKind + 决策版本 | 重复消费直接重复 start/complete |
| 待办投影 | executionId + projectionVersion | 把消息重投当作新增待办 |

证据唯一键必须含租户隔离，并保留采用/拒绝政策版本。策略变更不能原地改写旧证据“当时为什么被接受”的解释。

### D10.5 失效矩阵

| 结果变化/节点阶段 | 尚未采纳 | 已采纳、节点未完成 | 节点已完成 |
|---|---|---|---|
| 当前有效结果撤销/到期 | 不能采纳；可等待替代 | 使当前有效证据失效，重评完成条件 | 记录证据变化和影响；按政策标异常或提出正式返工请求 |
| 新结果替代旧结果 | 按政策选择；PINNED 不替换 | CURRENT_VALID 重新核验；是否采纳新结果由选择政策决定 | 不自动重写过去采用的 ResultRef；必要时标异常/返工 |
| 历史事实的前瞻失效 | 仅当在要求时间区间曾成立时可采纳 | 满足 HISTORICAL_FACT 的历史断言可保留 | 历史采用记录保留，不自动回退 |
| Owner 追溯纠正为原事实不成立 | 不采纳错误事实 | 原证据失效，重新等待/阻断 | 标记已完成结果受影响，按正式异常/返工流程处理 |
| 旧事件迟到 | 比较 Owner 状态版本，不覆盖新状态 | 同左 | 同左；不回写旧完成结论 |
| 计划/轮次已更换 | 旧目标退休，不转投新轮次 | 旧证据归档，新轮次按新政策核对 | 保留历史转换与旧证据归属 |

### D10.6 正式推进的一致性

自动完成前，项目事务必须重新验证 executionRef、契约和证据当前版本，必要时调用 Owner 同事务状态断言或 CAS/锁定接口，随后调用现有正式完成/准出 Writer。证据判定与正式转换的提交边界由项目负责，不直接修改业务记录。

`CURRENT_VALID + ATOMIC_WITH_TRANSITION` 必须证明 Owner 状态断言与项目转换在同一有效事务/锁协议中。若未来采用远程“截至某游标有效”的证明，应新增明确的较弱一致性剖面并让模板显式选择；本轮不默许把读后可能变化的远程结果当作绝对当前有效。

结果在项目完成之后才被撤销是新的业务事实，不等于之前合法完成事务必须被撤销。其影响走上表的异常/返工政策；不能为了避免这种正常先后关系而重新耦合业务提交与项目永久锁定。

## D11. 项目执行、阶段推进与待办投影

正式状态由既有项目执行中心和公开 Writer 改变。订阅/证据消费者只提出重评或调用这些入口，不再维护另一套 DONE 标志。

阶段与任务共用上述契约，但保留不同实例类型与权限语义。阶段直接业务办理使用 stageExecution；不得伪造一个任务来满足接口签名。一个阶段可只组织任务、可直接办理、也可仅按多个业务事实完成。

依赖变化的重评从证据/状态依赖索引定位受影响节点；必要的项目级恢复重评可保留，但不能作为历史补采的唯一机制。业务操作成功不意味着相邻阶段必须立即推进。

待办的最小投影包含节点/轮次、责任主体、执行状态、当前办理入口、等待原因分类、证据摘要及 projectionVersion。等待类型至少区分：业务待办、节点未准入、等待结果、候选需选择、证据失效、准出阻塞、处理故障/恢复中。

待办可重建；重建只读取权威执行/证据状态，不调用业务写接口或新建业务实体。关闭通知、隐藏卡片、打开页面均不改变正式状态。对有权限的用户提供投影滞后和实际节点状态的区别，不把投影延迟误报为业务未提交。

依据：SRC-01 L23–28、L98–106、L348–362；本节技术字段和更新协议为新增设计。


## D12. 模板编辑、业务视图与待办交互

### D12.1 模板配置体验

保留“选择业务能力”向导，分别显示可选操作、结果、证据政策和视图。配置人员不必理解底层表结构，但在保存/发布前必须看到最终显式契约。

向导预设只负责生成草稿；发布后不读取最新预设改变行为。节点删除最后一个操作、删除整个订阅或切换控制范围时，应提示含义：没有操作绑定不是“禁止所有独立业务”，没有结果订阅也不是“节点自动完成”。新契约使用明确 NONE，旧字段缺失只在旧解释器下有意义。

证据复用、新结果、指定修订应为独立控件；不能让“已关联对象列表”代替证据政策。阶段直接办理、纯结果等待、只保存不自动完成、汇总阶段都应可配置，不创建虚拟实体凑齐界面。

### D12.2 能力查询响应〔DES-12〕

只读响应必须分轴返回：

```text
nodeSummary
executionObservation
ownerReadability / ownerOperationDecisions
projectEligibility
operationPreconditionObservations
postconditionStatus = NOT_EVALUATED 或显式 NONE
presentationStatus
result/evidenceSummary（当前用户授权范围内）
blockedReasons
observationVersion / refreshTime
```

逐操作结果包含精确 OperationRef、对象是否已选、Owner 是否授权、项目资格、前置结论、处理器运行可用性及最终是否能提交。列表未选对象时应标记 `OBJECT_SELECTION_REQUIRED`；不能把“某些行可能允许”作为所有行的 `allowed=true`。选定实际对象后重新查询、提交前再重新校验。

业务 VIEW、业务 WRITE、项目 EXECUTE、模板 UPDATE、文件 UPLOAD 是不同权限。不能从“报告允许发布”推导附件可写；不能从“任务可维护”推导可执行业务命令。沿用仓库真实权限体系，不无依据新增或重命名生产权限码。

### D12.3 呈现与编辑缓冲

视图状态首批为 `AVAILABLE / READ_ONLY / UNAVAILABLE`。状态变化限制当前页面交互，但不定义业务结果有效性，也不自动撤销业务的其他合法入口。

编辑目标身份由业务对象/修订、当前打开节点身份与视图结构决定；纯权限刷新、轮次观察版本增长、待办数量变化不得销毁未保存表单。实际对象改变、计划语义改变或返工产生新轮次时，旧命令客户端不得静默替换为新轮次。

界面必须允许“保留旧编辑内容、禁止新提交、确认旧请求结果”。旧轮次响应不确定的命令可以按原封套恢复；恢复结果后再允许明确重新进入新轮次。带未保存内容或未确认命令离开时使用既有退出保护，不由定时刷新绕过。

### D12.4 操作回执与状态刷新

业务回执只显示已提交的业务动作及结果引用，节点状态另查。POST 不满足是业务命令失败；COMPLETION/EXIT 不满足则显示业务成功、节点等待。

组件异常发生在提交后时，不得提示“未改变业务状态”。需要显示“界面异常无法判断该请求结果，请查询原请求”，并保留幂等恢复信息。

宿主以注册的 actionDescriptor 映射精确操作，不将创建草稿和更新草稿合并成一个宽泛 UPDATE 放行。已有业务 UI 可用语义动作键，但该键必须映射明确 OperationRef，而不是公共核心硬编码实体清单。

待办和业务页面可分别刷新；项目状态刷新不应重复载入业务正文，不应因结果广播使已打开的编辑器 remount。

## D13. 新旧兼容、权威写路径与切换

### D13.1 解释路径与写路径分离〔DES-13〕

解释版本解决“这个模板/节点按什么规则解释”；权威写路径解决“这个业务实例由哪个实现写入”。二者不能只靠同一个 `operationContract` 字段有无来猜测。

建议逻辑记录 `BusinessAuthorityBinding`：

| 字段 | 语义 |
|---|---|
| tenant + Owner + canonicalObjectRef | 唯一业务实例或聚合身份 |
| authorityMode | `LEGACY_OWNER / NEW_OWNER`；不是客户端可选模式 |
| writerEpoch | 每次写权切换的单调栅栏版本 |
| authoritativeHandlerRef | 已部署、精确版本的合法写边界 |
| provenance / identityMapping | 旧新 ID、业务修订及来源映射 |
| migrationState | 准备/核对/切换/只读兼容等技术状态 |
| cutoverReceipt | 切换时的对象版本、核对摘要、操作者、审计引用 |

若当前存储/模块天然只有一个权威写边界，可复用既有标识而不新建表。若旧入口无法识别 writerEpoch，又不允许安全扩展其共同写边界，则不得让新旧实现同时写同一实例；应采用明确隔离的新实例或保持未切换状态，不能仅靠前端隐藏旧按钮。

### D13.2 路由矩阵

| 实例/入口 | 处理 |
|---|---|
| LEGACY_OWNER 实例经旧入口 | 原业务与项目兼容解释；不默认套新语义 |
| LEGACY_OWNER 实例经新入口 | 仅在显式兼容适配或迁移协议允许时处理；不能偷偷接管 |
| NEW_OWNER 实例经新独立入口 | 按业务配置/授权办理；ALL_ENTRIES 操作仍按其受控声明 |
| NEW_OWNER 实例经项目办理入口 | 新执行契约和受控事务；任何失败不转旧入口 |
| NEW_OWNER 实例经旧写入口 | 服务端拒绝或明确引导新入口，不允许旧实现继续修改当前事实 |
| 历史读取 | 按授权通过版本化读取适配，可跨来源查询，但读取兼容不授予写权限 |

源对象与修订映射属于 Owner 事实；项目模板绑定能力和结果类型，不把 Mapper/表名冻结成能力身份。

### D13.3 单实例切换协议

1. 读取明确迁移范围和保护清单；检查是否有在途/不确定命令及待处理外部副作用。
2. 取得实例权威写栅栏及版本锁，暂缓新写；将旧版本、映射、结果身份和来源快照留存。
3. 在不改变旧真值的前提下构造新承接数据或配置，进行对账；不能把不可证明的历史状态转换成有效结果。
4. 核对新命令、结果读取、权限、历史回执和项目证据引用均可解析。
5. 原子切换 authorityMode/writerEpoch，并启用已验证的新路径；旧写入口同时失效。
6. 释放写栅栏后记录切换回执；新请求重新获取权威版本，旧预期请求拒绝或按原已提交回执恢复。

只做逻辑独立、不发生业务数据接管时，也应证明不存在两个平行“当前事实”写者。宽表暂时保留时须明确字段所有权和更新约束，不能各 Owner 完整 updateById 覆盖他方事实。

### D13.4 回滚边界

代码回滚、契约回退、实例数据回退是三个不同动作。已有新写入后，不能简单把 authorityMode 改回旧值；必须反向核对、转换并切换 writerEpoch，或者保持新写路径停止而只读运行。

旧哈希解释器和历史读取适配应可保留；新功能关闭不应导致旧数据无法读取。发生事故时优先暂停新契约激活/新受控工作，保留业务结果、Outbox 和恢复通道，不删除记录来“恢复干净状态”。

本轮不默认批量迁移所有旧实例。迁移计划只处理 C0 明确涉及的实例类别；迁移数量、窗口和执行人由实施时明确授权，文档不等于执行许可。

## D14. 存储、一致性、安全与可运维性

### D14.1 逻辑持久化要求，不强制新增同名表〔DES-14〕

| 逻辑记录 | 最小持久化要求 |
|---|---|
| TemplateFrozenArtifact | 解释版本、三类指纹、精确引用、不可变原文 |
| BusinessResult / ResultChange | Owner 权威结果及可恢复变化；独立于 Outbox 保留时间 |
| SubscriptionInstance | generation、ExecutionRef、政策/选择器指纹、状态、基线向量、扫描/增量 checkpoint |
| EvidenceDecision | ResultRef、适用范围、结论/理由、证据版本、事实断言及规则/政策指纹 |
| ProjectDecisionReceipt | 执行向量、正式转换、采用证据版本、结果及幂等身份 |
| CommandReceipt | 原摘要、提交状态、结果引用、可授权恢复所需信息 |
| BusinessAuthorityBinding | 单权威写入选择、writerEpoch、映射及切换回执 |
| TodoProjection | 可重建的执行义务和版本，不是真值替代 |

P0/P3/P4/P5 按现有表及 API 可用性决定复用、增量字段或新增最小表。新增结构须有唯一约束、租户索引、检查点事务边界、历史读取兼容和可回退迁移脚本。不能在缺少现状审计时直接按逻辑表格批量建表。

### D14.2 并发协议

必须建立覆盖现有 Owner 写入、项目开始/完成、计划生效、返工、证据判定和回执恢复的锁序清单。新路径不能在项目持锁调用 Owner 与 Owner 持锁回调项目之间形成逆序。

首选在公开应用边界统一“权威写栅栏/项目执行锁/业务对象锁”顺序，实际次序以 P0 对既有锁协议的核对为准；不在通用设计中武断改变所有业务的锁序。无法统一的路径应缩短事务或改成提交后处理，但不得因此把需原子 POST 的检查移出事务。

乐观版本检查必须作用于调用者看到的业务版本，不能在提交前自动读取最新版来覆盖原 expectedVersion。阶段开始产生的内部版本变化可以由执行中心受信更新，但不能改变原业务意图或允许换轮次。

### D14.3 安全与故障分类

所有主键查询、结果读取、游标、回执和证据操作必须带租户与授权范围。后台事实读取只暴露最小必要结果，不记录完整附件/正文。业务角色、项目执行角色、模板配置角色和后台系统读取主体分别授权。

错误最少分类：`FORBIDDEN`、`INVALID_CONTRACT`、`VERSION_CONFLICT`、`AMBIGUOUS_TARGET`、`FACT_UNAVAILABLE`、`PRE_NOT_MATCHED`、`POST_NOT_MATCHED`、`EVIDENCE_NOT_APPLICABLE`、`CURSOR_GAP`、`REPLAY_IN_PROGRESS`、`UNKNOWN_COMMIT_OUTCOME`。这是协议语义，不要求替换现有全局错误码编号；可使用已有 code+reason 映射。

不得把所有非 2xx/异常都归为已回滚，也不得把未知结果都永久无法重试。明确首次业务拒绝可以修正后发新意图；曾有未确认尝试的恢复请求被拒绝，应保留“不知道之前是否提交”的事实。

### D14.4 规模与观测

历史补采、匹配扇出、候选重评、投影重建均采用持久分批 checkpoint、幂等和有界并发，不在一个 HTTP/数据库事务全量遍历所有项目。具体分页上限、积压阈值、保留期和延迟预算必须从 P0 的实际数据量/现有运行目标确定，不用本文虚构吞吐数字。

至少观察：结果发布失败、源投递滞后、每订阅追平水位、cursor gap、重复/冲突消息、UNKNOWN 比例、证据失效、状态转换冲突、回执恢复次数、当前新旧写路径拒绝、待办投影延迟。

贯通 `correlationId / CommandRef / ResultRef / SubscriptionRef / EvidenceRef / ExecutionRef`，但不能让缺少可选业务追踪值导致伪造业务失败。由服务端生成根追踪标识，Owner、项目审计和事件使用同一关联；不使用输入 JSON 的同名字段作为受信追踪来源。

## D15. 设计决策清单与取舍

| 决策 | 固化内容 | 被排除的替代方案及原因 |
|---|---|---|
| DES-01 | 业务职责独立，ALL_ENTRIES 明确例外，按能力范围验收 | 要求一个订阅能力先迁整个领域，扩大范围且不必要 |
| DES-02 | 注解/Scope 仅是受信同步接入，真正应用边界负责覆盖 | 仅数注解或把 ThreadLocal 作为跨服务许可 |
| DES-03 | 对象、结果、事件、证据、执行、命令身份分开 | objectId 曾使用即永远不再可采纳 |
| DES-04 | 新语义显式版本，五类契约独立可选并共同编译 | 通过字段缺失同时猜历史/独立/错误/禁用 |
| DES-05 | 新完整指纹+旧算法固定解释，事实动态值不冻结 | 直接改公共哈希使历史失效，或漏掉准入/准出程序 |
| DES-06 | 精确能力注册驱动公共分派和动作描述 | 中央 if/switch 持续增加实体，或模板反射任意处理器 |
| DES-07 | 服务端判定三种新调用路径，历史兼容单独解释 | 请求漏传上下文就回到独立写入 |
| DES-08 | 类型化事实、明确检查点与事务能力，复用规则引擎 | 通用表达式任意查询业务内部对象 |
| DES-09 | Owner 权威结果与状态分开，单一生产机制 | 项目与业务各生成一个同义“成功结果” |
| DES-10 | 稳定游标+有边界历史扫描+持久增量恢复 | 仅等待下一次全项目重评，或按 MAX(id) 假设无缺口 |
| DES-11 | 三种获取政策、两种有效性与明确失效矩阵 | 所有历史证据都自动失效，或所有完成节点自动回退 |
| DES-12 | 分轴能力、精确动作、编辑缓冲与原请求恢复 | 刷新替换旧轮次客户端，或按页面 writable 放行所有动作 |
| DES-13 | 单权威写路径和版本栅栏，读兼容不授予写权 | 新旧两份当前事实长期双写，让执行中心猜真值 |
| DES-14 | 按现有能力复用最小存储，明确一致性与观测 | 为五类逻辑契约机械新增五套系统或先全面搬模块 |

这些决策是本设计新增的定稿建议。批准后应保持稳定；发现当前实现不支持时记录架构差异并显式修改基线，不由某个适配器私自改变协议。

## D16. 配置示例与关键伪代码

以下均为逻辑片段，不是现有 HTTP DTO、数据库种子或已经部署的能力。`example.*` 仅用于说明配置，不得注册为真实业务能力以通过验收。

### D16.1 纯订阅节点：没有操作和页面

```json
{
  "executionSemanticsVersion": "DECOUPLED_1",
  "nodeKey": "wait-survey-result",
  "nodeKind": "TASK",
  "operationBinding": {"mode": "NONE"},
  "presentationBinding": {"mode": "NONE"},
  "resultSubscription": {
    "mode": "CONFIGURED",
    "subscriptionKey": "survey-confirmed",
    "capabilityRef": {"key": "example.site-survey.results", "version": 1},
    "resultTypes": ["SURVEY_CONFIRMED"],
    "selectorRef": {"key": "same-business-project", "version": 1},
    "selectionMode": "EXACT_ONE",
    "evidencePolicyRef": "reuse-current-survey"
  },
  "evidencePolicy": {
    "key": "reuse-current-survey",
    "acquisitionMode": "REUSE_EXISTING",
    "validityMode": "CURRENT_VALID",
    "consistencyRequirement": "ATOMIC_WITH_TRANSITION",
    "invalidationAfterCompletion": "MARK_EXCEPTION"
  },
  "completionCondition": {"mode": "RULE", "ruleKey": "required-evidence-accepted"}
}
```

发布只检查本订阅依赖的结果/选择器/政策/规则能力；不要求 `example.site-survey` 命令处理器或页面。例中的当前有效强一致性必须由实际 Owner 证明，缺失时不可发布该政策。

### D16.2 同对象可产生本轮新结果

```json
{
  "evidencePolicy": {
    "key": "new-confirmation-for-this-round",
    "acquisitionMode": "NEW_RESULT",
    "baselinePoint": "ROUND_OPEN",
    "formationOrder": "OWNER_STABLE_COMMIT_CURSOR",
    "validityMode": "CURRENT_VALID",
    "selectionMode": "EXACT_ONE",
    "invalidationBeforeCompletion": "REQUIRE_REPLACEMENT",
    "invalidationAfterCompletion": "REQUEST_REWORK"
  }
}
```

本轮实际基线 B 在运行实例保存，不写入模板语义原文。相同 objectId 的新结果 R2 可满足，R1 的重新投递不能满足。REQUEST_REWORK 只提出正式返工请求，不由消费者直接回退项目状态。

### D16.3 可保存但不以保存结果自动完成

```json
{
  "nodeKey": "supplement-survey-data",
  "operationBinding": {
    "mode": "CONFIGURED",
    "operations": [{
      "capabilityRef": {"key": "example.site-survey.commands", "version": 1},
      "operationCode": "SAVE",
      "operationVersion": 1,
      "controlScope": "PROJECT_ENTRY_ONLY",
      "pre": {"mode": "RULE", "ruleKey": "node-specific-save-condition"},
      "post": {"mode": "NONE"}
    }]
  },
  "resultSubscription": {"mode": "NONE"},
  "completionCondition": {"mode": "RULE", "ruleKey": "explicitly-configured-completion"},
  "presentationBinding": {
    "mode": "CONFIGURED",
    "viewRef": {"key": "example.site-survey.editor", "version": 1}
  }
}
```

操作无订阅不等于节点无完成条件；完成条件必须独立、明确、受引擎支持。视图动作从能力注册解析，不建立中央 SAVE 别名猜测表。

### D16.4 证据消费者伪代码

```text
onResultCandidate(subscriptionRef, resultRef):
  load authoritative subscription and execution generation
  if retired or execution changed:
      record obsolete target; acknowledge transport
      return

  result = Owner.readExact(resultRef, restrictedSystemReadContext)
  status = Owner.readStatus(resultRef)
  decision = evaluate(policyHash, selectorHash, result, status, storedRoundBaseline)

  persist candidate/decision + checkpoint atomically
  if decision is accepted or invalidates prior evidence:
      request formal node reevaluation

formalComplete(executionRef, expectedDecisionVersion):
  acquire established project/Owner consistency protocol
  revalidate plan, execution generation, evidence versions and required current validity
  evaluate frozen completion and exit programs
  if not matched: persist waiting/unknown cause; do not alter business result
  if matched: invoke existing formal transition Writer
  persist transition receipt and projection notification in project transaction
```

其中 `readExact`、稳定基线和当前状态断言必须由真实 Owner 能力实现，不能用项目联表拼装假的业务结果。

## D17. 验收映射、待核实事项与变更控制

### D17.1 IR 问题到设计与实施

| 原发现 | 设计闭合点 | 主要实施阶段 |
|---|---|---|
| IR-01 执行指纹遗漏语义 | D04、D05 完整闭包和历史算法 | P1 |
| IR-02 普通业务反向依赖项目 | D02、D06.2、D07、D13 | P2、P6 |
| IR-03 订阅依赖操作绑定 | D04.4、D09 | P3、P4 |
| IR-04 证据等同对象关联 | D03、D10 | P5 |
| IR-05 PRE/POST 缺业务输入结果事实 | D08、D07.3 | P6 |
| IR-06 中央实体/版本/动作分支 | D06、D12 | P1 登记契约、P7 完整接入、P8 UI |
| 评审补充：全入口例外/路径判定 | D00.2、D07.1、D07.6 | P0、P2、P6 |
| 评审补充：旧新权威写路径 | D13 | P2、P9 |
| 评审补充：历史补采与失效闭环 | D09.4、D10.5 | P3、P4、P5、P9 |
| 范围与逐阶段提交/真实验证分开 | D01.2、D01.3、配套实施计划 | P0～P9 |

### D17.2 必须在当前仓库核实而非猜测的事项

| 待核实项 | 解决阶段与产物 | 不得采取的做法 |
|---|---|---|
| 当前 HEAD、既有阶段提交与未合并并行变更 | P0 baseline manifest | 使用历史对话中的 SHA 当成当前 HEAD |
| 旧 Schema/哈希算法与已发布原文样本 | P0/P1 金样及解释器映射 | 根据字段有无无界回退 |
| C0 内业务的实际入口、配置来源、Owner 与权限 | P0/P2 能力审计表 | 从页面名称推断已具备独立业务 |
| 原锁序、事务参与者与数据写边界 | P0/P2/P6 事务/锁图与测试 | 将注解名称当作原子保证 |
| 结果日志、稳定游标、历史读取及有效性能力 | P0/P3 能力矩阵 | 用已有 Outbox 自动宣称历史可补采 |
| 物理表/索引、迁移脚本、构建与测试命令 | 对应实现阶段 | 编造路径/命令或复制全领域模型 |
| 部署覆盖、观察阈值与业务验收环境 | P0/P9 验证台账 | 无依据填写性能数字或通过状态 |

这些事项不影响先固化逻辑设计，但在对应代码阶段交付前必须有确定映射。无法证实的能力标为不可用，不虚构实现。已确定的 IR 原理问题若在当前 HEAD 已修复，应记录证据并复用，不重复修复。

### D17.3 基线变更

实施时改变控制范围、证据身份、基线语义、哈希内容、失效处理或权威写入选择，必须更新本文的 DES 决策和相应验收项，再提交对应代码；不得只修改一个 Adapter。

调整物理类名或复用现有表，只要不改变上述契约，可记录映射而不改变设计版本。外部资料不是源码现状证据；任何部署/数据迁移均需单独授权和执行记录。

## D18. 参考资料与证据边界

**SRC-01：** 用户上传《调整方案》（工作名称），372 行，完整 SHA-256 见 D00。附件核心原意为业务独立、项目可选约束、独立订阅和可配置证据，不全面迁移物理模块。本文的字段、枚举、时序与默认政策均以 DES 标记为新增设计。

**SRC-02：** 本会话对附件的评审结论及用户“制定详细设计基线文档和实施计划”的指令。本交付不修改生产代码、不更新远端分支，也不声明本设计已被业务负责人审批。

以下工程资料核验日期为 2026-09-17。仅用于边界依据：
- EXT-01，Spring Framework，Proxying Mechanisms：`https://docs.spring.io/spring-framework/reference/core/aop/proxying.html`。用于确认代理和自调用覆盖风险；本文不要求切换代理技术。
- EXT-02，Spring Framework，Transaction-bound Events：`https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html`。用于区分事务前/后事件阶段；提交后通知不代替事务内结果落盘。
- EXT-03，AWS Prescriptive Guidance，Transactional outbox pattern：`https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html`。用于事务数据/事件一致性与重复投递边界；不引入 AWS 服务或事件溯源工程。

有关当前 NPDMS 源码、测试或运行状态的断言，必须由实施计划 P0/P9 的仓库读取及实际执行记录提供。本次设计文档没有重做这些取证。
