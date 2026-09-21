# 模板执行解耦 v1.1：唯一实施进度记录

历史来源分支：wrck/NPDMS / codex/liteflow-remediation。当前专项在a4f5/NPDMS工作区继续，起点为2f1eb4f85（detached HEAD）。

## 2026-09-19 接续核对与执行安排

需求方确认：本专项不走工程实施链，仅回写当前计划与本进度；在当前工作区先完成全部功能，再集中测试验证。保留已有运行文件及未跟踪输出，不自动提交、合并或推送。

后续补充：正式代码按当前阶段逐阶段推进到P9并完成全部调整，其后再集中测试；已生成的测试源码保留，后续不以补写测试为阶段前置。截至此补充未执行测试、构建或运行验收。

以下为当前Git及源码核对结果，不构成本轮测试PASS：

- P1.04.3已由310e0fbf4提交并进入当前HEAD。文末远端写入受阻是历史事实，不再是当前阻塞。
- P2已有5bfc865ec、26666dc9c、96d0c3639、e6b17f3db、7e241f91d、5f4d1861d，覆盖工勘/验收报告独立入口、冻结业务配置及需求分析已有修订和首次创建。
- P3已有47773466e、0734f8c2b、8bb0689de，包含三类业务结果来源、原事务变化记录和有界存量读取。
- P4已有603ae673d、21e24fec7、1310c8dce，包含按计划/轮次安装、分页检查点和有界恢复。
- P5已有426c2dd15、0719a536d、5489c78a6、766f68694、6f4df8dfc、3162d77ab，包含证据政策、完整扫描、正式完成校验、纯订阅任务合同及评估后推进。
- 接续时确定的缺口：订阅发布和NEW_RESULT被无条件拒绝，模板编辑器缺独立订阅及新呈现配置，P6事实与P7公共分派未收齐。这些缺口已在本工作区完成源码调整，详见下文2026-09-19实施记录；不是本轮运行验证结论。

以上提交的测试源码与历史结果尚未完成适用性核对；本轮未运行测试、构建、数据库或浏览器验证。下文旧阶段记录按发生时点保留。

## 已提交基线

- 旧文档基准：75dfa7d98cade30f708759720400e6b8c01e5ec0。
- v1.1方案提交：f755c5721090d34bca6412aa39ded32ccd42093c。
- P0准备提交：a6313c6b4c84db03acbd0361ab57bb00a90e7ab5。
- P1.01实现提交：f6d28931608bb39c484c8b9a23da79b81e4a76e1。
- P1.02.1实现提交：65ec5445fc59ac13c2b81a875806936b790107f5。
- P1.02.2实现提交：3cba4ac7bfcb59ddc319827b39f9f674044fe198。
- P1-V1验证入口提交：4811a1a08c4c7e972a4c32092298cc08fc1c372d。
- P1-V2依赖修复提交：8053868b017252d88bd520602138cd35f2144df9。
- P1.02.3冻结读取提交：16d2c6b1aba4b3557fb1b372ad45b102a2a53e87。
- P1-V3事实目录权限回归提交：ba793870df04a3737dede25ca8f756d72732d4c5。
- P1.02.4新格式发布接线提交：bda6f2bc602d1d3ef1091745c21a769d72606b3e。
- P1.03独立配置提交：9f877b7295ab60ac4bf9cf08c72b385e8f230d3e。
- P1.03后继事件回归修复：55a4dacd7b18c0896e33a85783ed653064759073。
- P1.04.1页面路径与参数编码：17546cbf4268c82e8d503544300e9a1a8efa411a。
- P1-V4需求分析插入失败替身修复：df2fc1e393ec67ee627025ce075e73c84b438b8a。
- P1.04.2原业务输入与呈现保存边界：7cf1e89d9166aaf66e2ecc3412a2e5475bd5041b。
- P1-V5工程/操作回归入口：ac9e51e145c130c6a93721e345137198f342a8a9。
- P1-V6前端并行验证入口：2f815172c1872d3134feef7de91e6fe0d803f9c2。
- P1-V7真实组件测试修复：7955514192b4df3e671e9e060ead29bde062827e。
- 历史记录曾停在P1.04.3远端写入受阻；后续已由310e0fbf4完成提交，见本文件接续核对。
- 最新约束：版本优先、不新增多层Hash、权限码选择动作、复用业务输入、pageUrl仅展示。

## 阶段状态

| 阶段 | 状态 | 下一环节 |
|---|---|---|
| P0 | COMMITTED_PENDING_VERIFICATION（准备文档完成） | [范围与来源](template-execution-decoupling-p0.md)；对应真实验证在各改动阶段执行 |
| P1 | CODE_COMPLETE_PENDING_VERIFICATION | 订阅发布按实际来源能力校验；待集中验证 |
| P2 | CODE_COMPLETE_PENDING_VERIFICATION | 复用三组独立入口，修正工勘操作权限目录；待集中验证 |
| P3 | CODE_COMPLETE_PENDING_VERIFICATION | 复用三类权威来源、事务变化日志和形成边界；待集中验证 |
| P4 | CODE_COMPLETE_PENDING_VERIFICATION | 发布与安装统一要求事务屏障，复用分页及恢复；待集中验证 |
| P5 | CODE_COMPLETE_PENDING_VERIFICATION | 已完成证据失效记录影响并保留历史；待集中验证 |
| P6 | CODE_COMPLETE_PENDING_VERIFICATION | POST接入显式事务结果事实；R01～R03整改后自审确认，待集中验证 |
| P7 | CODE_COMPLETE_PENDING_VERIFICATION | 公共入口按原冻结操作版本分派；R04整改后自审确认，待集中验证 |
| P8 | CODE_COMPLETE_PENDING_VERIFICATION | 新执行配置编辑器及纯订阅任务展示已接线；R05整改后自审确认，待集中验证 |
| P9 | VERIFIED_SOURCE_AND_UNIT_PENDING_BUSINESS_CHAIN | R01～R05集中验证完成：源码/自测/真实DB-API/浏览器分层通过；订阅任务业务链与真实事务场景NOT_RUN（数据边界） |

## 2026-09-19 按阶段完成至P9的源码调整

本轮依据本专项计划及需求方确认实施，范围为C0三组14项操作及其模板、计划、执行、页面直接消费者。以下状态描述当前未提交工作区源码，不等于Feature Done、运行验收或全链路ACCEPTED。没有新增工程链记录、自动提交或更新其他文档。

### P1～P4：发布与运行能力衔接

- `TemplateExecutionConfigurationCompilation`移除“订阅尚未安装”的无条件拒绝；实际来源缺失、查询能力不足、存量/变化读取或事务提交屏障缺失仍阻止发布。显式空operations清除编译私有副本上的旧操作契约，字段缺省继续保留旧解释。
- `TemplateResultSubscriptionCapabilities`取消NEW_RESULT的临时总开关，按实际登记能力校验；`ProjectResultSubscriptionInstaller`同时要求事务提交屏障。基线继续来自原事务通道锁与提交序列，不使用最大ID、通知到达时间或伪造业务版本。
- P2复用工勘、需求分析及验收报告已有独立命令、实体输入、权限与项目归属检查。工勘Provider的权限元数据改为实际Controller/AccessProvider使用的`pms:sol-site-survey:*`，未改业务授权或历史快照。
- P3/P4复用三类Owner来源、原事务变化日志、有界存量扫描、检查点和恢复；需求分析重新激活旧修订不当作新形成结果，工勘不虚构修订ID。初始化、计划启用和返工仍使用原订阅安装入口。

### P5：完成历史与失效影响

`ProjectResultEvidenceProcessor`在已完成轮次的证据不再满足，或原完成扫描中的有效证据已失效时，记录`PROJECT_COMPLETED_EVIDENCE_INVALIDATED / REWORK_REVIEW_REQUIRED`。通过原扫描与本次完整扫描逐条比较，即使ANY_MATCHING被替代结果继续满足，也能记录原完成依据失效。新增查询仅在本模块Mapper XML中，显式限定租户和项目。

`ProjectResultSubscriptionContext`允许已完成历史轮次继续观察证据变化；其他旧轮次仍退休，不把消息转给新轮次。按需求方确认，完成记录、证据快照和生命周期不自动回写，由有权限用户使用已有正式人工返工入口。完成前仍复用全部订阅校验与原任务/阶段状态Writer。

### P6：原输入与按需事实

原SaveReq/Command、实体引用及PRE→Owner→POST受控事务继续复用。新增明确登记的`transactionResult.resultCode`、`transactionResult.objectId`、`transactionResult.revisionId`，仅允许POST使用；值直接取自本事务Owner返回结果。POST继续重读更新后的项目事实，失败仍由原事务回滚。

本轮按需支持PROJECT与上述TRANSACTION_RESULT标量，未登记的BUSINESS/COMMAND_INPUT字段及任意response载荷不可访问，不能据此宣称支持所有业务字段。未新增反射取值、动态脚本、通用业务版本或Hash。新增只读字段目录`GET /api/v1/pms/project-templates/rules/operation-result-fields`，授权沿用原规则目录；前端仅在操作后置编辑上下文加载。

### P7：冻结版本驱动公共分派

新增`ProjectOperationDispatcher`，公共命令Controller移除14项操作的硬编码分支，按请求原计划快照中的节点与精确操作版本选择已登记适配器。支持返工/计划变更后的原回执恢复，不静默选择最新版本。项目可见性、Owner回放授权、当前执行资格、Scope、并发版本、幂等请求内容及真实事务仍由原执行器重新校验。

`ProjectOperationResultOutbox`不再把操作版本强制限定为1；结果事件格式版本仍独立保持原定义。兼容包装方法保留，未改旧回执或公开命令请求结构。另一个对象形态的测试夹具已经生成并保留，但未执行，不作为真实业务迁移证据。

### P8：独立配置与任务展示

新增`ExecutionConfigurationEditor.vue`并接入模板内容编辑器。操作按真实权限/动作目录选择，多个版本不能默认择一；订阅单独选择结果来源、范围、获取/有效性/满足政策；页面单独选择已登记站内路径及项目/对象参数。后端新增只读`GET /api/v1/pms/project-templates/result-catalog`，沿用模板编辑/计划管理目录权限，仅返回来源能力，不读取业务对象。

保留旧操作契约编辑及冻结解释；纯订阅转换经明确确认后移除原业务绑定和页面，并使用独立节点完成规则，订阅证据仍必须满足。切换Owner/实体/组件时保护已有操作及呈现配置。原业务表单、页面宿主、未知命令恢复与离开保护继续复用。

`ProjectFlowPanel.vue`识别RESULT_SUBSCRIPTION并展示订阅推进说明和只读刷新入口，不再误报业务绑定缺失。`TaskStateActions.vue`同步隐藏这类任务的手工提交/完成按钮，服务端原授权和状态机仍是最终判断。待办继续读取正式节点状态，打开/刷新不产生业务完成事实。

### P9：源码自审结果与验证边界

已沿发布/冻结Reader、独立Owner写入、事务结果日志、订阅安装/恢复、证据扫描/正式推进、原回执恢复及编辑/运行页面逐段核对。跨阶段整改包括实际工勘权限映射、订阅发布能力、显式移除操作契约、已完成原证据失效、通用版本分派、POST结果事实和纯订阅任务误提示。没有改写已发布版本、旧Hasher、历史完成证据或数据库运行数据。

本轮仅完成源码阅读、修改与自审。测试、编译、类型检查、迁移验证、真实数据库/API/浏览器验收均为NOT_RUN，历史PASS不继承为本轮PASS。按用户要求，已生成测试文件保留，未继续补写测试。后续集中验证应覆盖：发布与冻结往返、三组实际入口权限、POST失败的真实事务/Outbox回滚、同对象新结果及有界恢复、完成后证据失效、旧版本回执重放、页面保存重开/未知结果恢复及纯订阅正式推进。

## 2026-09-19 R01～R05整改实施（依据计划I17）

上一轮P1～P9工作区改动已按需求方授权提交为`03c0e580a`（含I17整改方案落字）。本轮按I17.2顺序处理五组确定缺口：R01～R04经逐项核对已在`03c0e580a`源码中满足并记录复核证据，R05为本次新增实现。以下均为源码层结论，测试、编译、类型检查与运行验收仍为NOT_RUN，不构成任何PASS。

### R01：POST事务结果字段发布校验（复核确认，无新增改动）

按I17.3六项实施点逐项核对`03c0e580a`中的`ProjectRulePublicationValidator`：`RuleUse`按实际使用位置（模板匹配、收口、节点准入/完成/退出、依赖条件、操作PRE/POST、新旧`execution.operations`/`operationContract`）确定字段范围；仅当规则全部使用位置为POST时开放`transactionResult.*`，共享引用继承全部检查点并通过`decisionUses`传递到被引用决策表；字段存在性、值类型与决策表输入按合并目录校验；未引用规则保持ORDINARY不自动获得POST能力。链条一致性：编辑器仅在POST上下文加载`operation-result-fields`（`RuleEditor.vue`），冻结`TemplateOperationCompilation.supports`按POST扩充目录，运行`ProjectOperationRuleEvaluator`仅经`ProjectOperationResultFields`取值。同字段用于PRE、匹配或节点位置仍被拒绝。

### R02：畸形响应保留待确认请求（复核确认，无新增改动）

按I17.4核对`operationClient.ts`：顺序为收到响应→`validateEnvelope`封套校验→`entry.validateResult`正文校验→登记结果→清理隐式请求→刷新展示；封套校验覆盖必要字段、值类型、Owner/实体身份（`transport.resultIdentity`），保留字符串ID精度。三组受信适配器均按动作提供正文校验（`siteSurveyResult`/`requirementAnalysisResult`/`acceptanceReportResult`），校验方式随意图捕获固定，恢复不切换解释。畸形或身份不符抛普通Error不清除待确认记录，`hasUncertain`阻止新意图；恢复请求被拒绝不清除记录、不解释为已回滚；`submitted`投影失败不影响已校验业务成功。创建/复制不要求返回对象等于输入，无修订业务不强制`revisionId`。

### R03：异步预检前完整捕获操作意图（复核确认，无新增改动）

按I17.5核对：`execute`在首次异步等待前同步捕获操作编码、对象身份（字符串化）、输入副本、预期版本来源（含函数引用）、幂等键与结果校验方式；指纹、能力预检与请求构造均使用捕获值，不再读取可变intent。首次取得版本后固定到提交封套；重试复用`entry.command`原封套与原键，不再次获取版本或能力。同键异意图冲突、执行中合并与未知请求阻止新意图的既有规则保留。

### R04：精确版本接入与业务动作登记（复核确认，无新增改动）

按I17.6核对：客户端已删除`operationVersion === 1`过滤，按冻结能力返回唯一绑定；零匹配、多匹配与不支持版本分别报`OPERATION_NOT_BOUND`/`OPERATION_BINDING_AMBIGUOUS`/原reason。C0三组14项动作完整迁入三份受信适配登记（`siteSurveyOperationActions`、`requirementAnalysisOperationActions`、`acceptanceReportOperationActions`），公共宿主只经`businessOperationAliases`按精确Owner/实体/组件匹配读取映射，无C0枚举或业务实体分支；未知映射不放行，文件写权限仍单独处理。

### R05：实际订阅状态与等待原因（本次新增实现）

- 新增共享只读查询`ProjectResultSubscriptionObservationQuery`：按租户、项目、当前轮次（`current_marker=1`）与订阅身份读取既有订阅、扫描与证据记录；只读无锁，不触发扫描、安装、返工或任何Writer。调用方（任务工作台、节点执行列表）先完成原有项目/任务访问授权。
- 新增Mapper只读查询（XML，场景化Query对象）：`ResultSubscriptionMapper.selectByExecutions`（限定当前轮次集合，空集合不查询）、`ResultEvidenceMapper.selectScanForSubscription`（当前订阅检查点的最新扫描）、`selectDistinctReasons`（有界去重原因码，LIMIT 20，不解释为完整集合）。
- 摘要内容：订阅局部标识、来源类型、扫描状态与版本核对、合格/检查数量、缺失对象（来自冻结范围与累积器，有界）及有界安全原因码。等待原因为封闭集合：`EVALUATION_PENDING`（缺记录，待评估）、`EVALUATION_OUTDATED`（旧扫描只显示待更新，不显示为当前已满足）、`EVALUATION_SCANNING`、`NO_QUALIFIED_RESULT`、`EXPECTED_OBJECTS_MISSING`、`RESULT_AMBIGUOUS`、`RESULT_SOURCE_UNAVAILABLE`、`EVIDENCE_SATISFIED`。结论解释复用`ResultEvidencePolicy.decide`，不建立第二套完成真值；摘要损坏时显示待评估，不构造替代结论。
- 消费接入：任务工作台`getWorkbench`对`RESULT_SUBSCRIPTION`绑定返回可选`resultSubscriptions`（查询失败按既有模式设置`RESULT_SUBSCRIPTION_FACT_UNAVAILABLE`，不破坏工作台）；节点执行列表`ExecutionView`新增可选订阅观察，任务与阶段共用同一查询。工作台响应不新增任务状态或完成真值字段。
- 前端：`ProjectFlowPanel.vue`固定说明替换为实际订阅评估表（等待原因、合格数量、缺失对象、已观察原因、历史轮复核提示），刷新仅读取；`ProjectStageStatusPanel.vue`推进轨为订阅阶段显示紧凑等待摘要。`TaskStateActions.vue`继续隐藏订阅任务手工提交/完成，服务端授权与状态机仍是最终判断。

### 本轮验证边界

本轮仅完成源码修改、逐项复核与自审。按I17.1约束未运行构建、测试、类型检查或运行验收，全部NOT_RUN；`03c0e580a`之前的历史PASS不继承为本轮PASS。R01～R04未新增生产代码改动，R05新增改动涉及上列文件。I17.8集中验证场景（发布与冻结、请求安全与恢复、通用接入、订阅展示、真实事务与运行页面）待下一阶段执行，其中订阅展示场景新增覆盖：扫描中、无结果、歧义、来源不可用、待评估与过期扫描的展示边界，以及集合读取有界与刷新无副作用。

## 2026-09-19 R01～R05集中验证实际结果（需求方指令）

按I17.8集中验证执行；源码、自测、真实数据库/API、浏览器证据分层记账。验证环境：本工作区独立`npdms-verify` compose栈（MySQL 8.4:13306/Redis:16379，全新卷）+ JDK25后端58080 + Vite前端18084；后端以`.env`数据源参数启动。原始`npdms`项目MySQL/Redis容器因旧.env丢失且卷凭据不可恢复而停止（数据卷`npdms_npdms-mysql-data`保留未动）；测试期间创建的`.env`、`.env.env.local`为本地验证文件，未提交。

### 源码层

- 后端`mvn -pl pms-module-project -am test-compile`：主代码编译通过；两个既有测试类（`ProjectStageSubmissionServiceTest`、`ProjectTaskQueryServiceTest`）构造器因R05新增依赖参数编译失败，已按最小适配补mock参数后通过（自测层）。
- 前端`vue-tsc --noEmit`：本次改动触碰的文件零错误；仅存的14个错误全部位于未跟踪的WMS遗留文件（`src/views/wms/order/shipment/*`，被.gitignore忽略的并行会话残留），与本次改动无关。

### 自测层（既有测试复用，全部通过）

- 后端专项影响面（rule/operation/projecttemplate/taskworkbench全包+计划提交服务）：925个测试，除2个既有失败外全部通过。
- Node客户端恢复测试（operationClient/selfReview/presentation/site-survey input，node --test）：44/44通过，覆盖R02/R03恢复语义。
- 前端受影响spec（vitest pms-file配置）：BusinessView、执行配置编辑器、执行历史、工勘/需求分析/验收API等受影响范围全绿。
- 对照实验：以git stash在HEAD（无R05）重跑全部失败用例，失败集合完全一致（11失败+7错误）——R05引入零回归。18个既有失败归属：生命周期校验类（99a603be7，早于专项基线）、projectmember/mock装配类与交付域迁移类（对应测试与主代码均非本专项触碰）、`ResultSubscriptionTaskContractTest`（断言滞后于03c0e580a已批准移除无条件拒绝，本次对齐为`RESULT_SOURCE_UNAVAILABLE`，保护意图不变）。
- 验证中发现并修复的真实缺陷：①迁移文件版本重复（V251/V252/V253/V254各两份，并行合并产物，全新库必失败）——工程域两个对齐文件重编号为V301~V304；②`V289__equipment_migration_ddl_completion.sql`与V285重复的非幂等RENAME（全新库必失败，V285已完整覆盖）——移除重复段并注明；③`SpringJdbcStreamingReader`经`@Import`按无参构造器实例化导致应用启动失败（62d4d285e构造器改造的漏网）——改为组件扫描+`@Autowired`显式构造器；④`ExecutionConfigurationEditor.vue`以getter数组watch绑定，父组件重渲染即重置已加载目录——改为多源逐值watch（由批次自带的未执行spec暴露）。

### 真实数据库/API层

- 迁移：全新库应用238个迁移至V293成功（含专项V251~254）。V294起因`proj_project_portfolio`等遗留表在任何迁移中都未建而失败——迁移集整体依赖一份无法从迁移复现的历史状态，属专项之外的数据迁移治理缺口，已记录未修复。
- API（真实登录令牌调用）：R01元数据`operation-result-fields`返回3个transactionResult字段；`rules/fields`、`result-catalog`返回登记来源与真实能力位（工勘exactLookup=false与适配器一致）；`node-executions`（项目1001，无生效计划）返回空集无异常；不存在任务的工作台访问被授权边界拒绝（1014024045）。R05观察字段接线在真实服务上无副作用执行。

### 浏览器层（真实页面）

- 登录→工作台→项目详情（PROJ-2026-001）→阶段状态面板→阶段工作台抽屉全程可用：本次改动的`ProjectStageStatusPanel`（阶段表+本轮办理列）与`ProjectFlowPanel`（阶段视图，含STAGE_CONTRACT_NOT_FROZEN正确降级提示）真实渲染无控制台错误、无失败请求；截图留存。
- 纯订阅任务的订阅评估表展示、发布/冻结往返、畸形响应恢复、旧版本回执重放、真实事务回滚：需种子无法提供的生效计划+订阅任务业务链，NOT_RUN（数据边界，非功能缺失；对应后端行为已由自测层单测覆盖）。

### 环境遗留状态

- `npdms-verify`栈与验证用后端（58080）、前端（18084）仍在运行，供复验；`npdms`项目原mysql/redis容器处于停止状态，数据卷未动。
- 本轮全部生产代码与测试适配改动未提交；`V289`、V301~V304重编号为集成修复，建议随本批提交。

以下章节均为此前提交时点的历史记录，其中“下一项”“未启用”“未完成”和环境限制仅对当时有效，不覆盖上面的当前阶段状态。

## P0 实际完成及验证

C0为当前目录三组业务14项操作和直接消费者边界；确认实际权限共用，未审计深层行为分配到对应阶段。不是全部模块独立化审计。

当前源文件字节核对后重跑13项已有Node客户端恢复测试，全部通过；对应TypeScript单文件严格检查通过。没有执行框架、JDK25/Maven、数据库、API或浏览器验证；不修改原Feature状态。

仅新增P0范围记录并更新本进度，未修改生产代码、旧历史、数据库或运行配置。实际下一项P1.01不能被本记录视为完成。

## P1.01 权限目录与操作消歧

基准：a6313c6b4c84db03acbd0361ab57bb00a90e7ab5。对应PM-03/F-PROJ-009，接口契约补充在SDS04a第13节；原Feature Ready/Done未修改。

实现：ProjectBusinessOperationProvider增加默认可选permissionCodes元数据；三组真实Provider登记原Controller中的14项功能权限映射。旧操作描述符、方法、版本和回调不变；无权限映射的旧Provider保留精确查找和目录读取，不猜测权限。

ProjectOperationPermissionIndex将权限与命令选择分开：Owner/实体/权限均精确匹配，必要时使用已有operationCode；多个动作或版本均无默认选中项。注册表复用此索引，保留真实方法存在性校验、精确find和唯一运行适配判断。不新增Hash或业务版本字段。

原目录GET增加可空permissionCode；新增同权限边界的只读GET /operation-catalog/resolve返回status/selected/candidates。前端API类型和调用已接入；不存在从配置解析结果直接授权执行业务的路径。RESOLVED与runtimeAvailable分别返回，不能因为其中一个版本暂不可运行就自动选另一个版本。

### 实际验证

- JDK21实际编译原Descriptor、兼容Provider、纯权限索引和同一套场景源码，javac -Werror -Xlint:all通过。
- 25个纯Java具名场景通过，0失败；覆盖唯一/共用权限、操作与版本歧义、跨Owner/实体、未知/重复映射、旧Provider、精确版本保留、不可变集合、读取次数和查询不执行命令。
- 修改的TypeScript API和新Vitest文件只做语法转译，0语法错误；不等于全前端类型/组件测试。
- 三个Provider原operations正文保留；SDS原有1～12节字节保留，仅追加本接口设计。对应源文件已核对原Git Blob，差异空白检查通过。
- 新增JUnit桥接、实际Registry/Controller/JSON/配置权限声明测试、三个真实Provider与Controller权限对应测试、前端API调用Vitest源码。这些框架测试未运行，不计入25个已通过场景。

### 尚未完成/启用

P1.01提交时P1.02～P1.04尚未实现（后续增量见下节）：没有启用新模板发布格式、没有删除旧Hash验证、没有改变运行快照或业务实体输入；权限码配置在模板编辑器内的完整保存/发布以及pageUrl展示仍待后续。P2～P9未开始本轮实现。

完整JDK25/Maven、Spring/JUnit、Vue/Vitest、数据库/API/浏览器均待验证。仅提交代码/测试源码及必要契约/本进度；没有部署、迁移、重启或新旧实例切换。

## P1.02.1 已发布快照读取与复制防降级

基准：f6d28931608bb39c484c8b9a23da79b81e4a76e1。按实施计划允许的独立子步骤推进P1.02；本子步骤不等于完整版本冻结完成，P1仍为IMPLEMENTING。

### 审计与实现

审计了TemplateExecutionSnapshot、TemplateCompiler、ProjectTemplateV2ServiceImpl、旧Hasher，以及ProjectRuntimeGraphFreezer、ProjectPlanInitializationService和ProjectPlanDraftService直接消费路径。本次仅修改模板V2读取/复制、运行图新写入口及新增格式Reader，不宣称其他计划/运行读取点已全部收敛。

- 新增TemplateExecutionSnapshotReader：先检查原始JSON对象必须明确携带受支持的整数schema，再绑定模型；拒绝缺失、null、字符串、小数、未知版本和溢出数字，不能利用模型默认值或类型转换恢复成v2。字符串输入检查版本后仍绑定原文，不经JSON树重序列化改变小数精度或旧Hash；Reader只负责格式边界，发布元数据与旧Hash仍由发布服务校验。
- 模板运行读取和revision兼容投影继续通过同一verifiedExecutionSnapshot；原有匹配候选也复用该路径。旧schema2/compiler/hash校验保留，不调用当前Compiler或活体Definition补救坏发布记录。
- 发布版本复制先检查PUBLISHED及V2快照，再读取冻结Designer；缺快照、Hash损坏、版本/编译器不一致、缺Designer或Designer格式/元数据异常均在创建副本前拒绝。真正Legacy发布版仍沿原显式导入路径；草稿复制入口不变。
- ProjectRuntimeGraphFreezer由“schema>=2”改为精确版本判定；兼容投影先检查原始schema，未知格式在合同/关系写入前拒绝。既有冻结合同构造、历史Resolver及业务规则不改。
- 新增三组JUnit测试源码：原始格式与原文小数精度/回读/旧Hash稳定性、公开复制负向/冻结Designer只读、运行图类型化及兼容入口写前拒绝。未修改或删除旧测试。

本次是恢复F-PROJ-009既有“未知格式拒绝、半V2禁止降级”的约束，不改变API/数据库设计，不另行修改SDS或历史Feature状态。

### 本次实际验证

- 修改前两个生产类和本进度文件的本地副本均已逐字节核对远端Git Blob。
- 使用JDK21的JavacTask.parse解析全部6个本次Java文件，0语法错误；这不是类型检查、JDK25构建或JUnit执行。
- 差异自审核对：原发布方法、旧Hash算法、业务操作、历史Resolver及数据库文件不在改动范围；修改差异通过空白检查。
- 当前环境只有JDK21、无Maven，且依赖下载未成功；三组JUnit及原兼容回归未执行，不记录测试通过。Spring装配、JDK25/Maven、数据库/API/浏览器仍待真实验证。

### P1.02剩余范围 / 下一环节

完整新版本格式、全部规则/决策闭包与执行配置冻结、发布不可覆盖、Compiler/Publisher/Reader/复制/计划全部消费者接线、数据库可空契约检查及新旧格式回归仍未完成。审计发现ProjectPlanDraftService.prepare仍直接反序列化effective快照，计划初始化也需核验传入快照与来源版本的一致性；这些是下一增量的确定待整改项，不记成仅待验证。

新格式发布保持未启用，旧Hasher保持不变；不通过本子步骤对历史快照补字段、重算Hash或自动迁移。P1.03/P1.04及P2～P9继续按原顺序推进，没有部署、迁移、重启或实例切换。


## P1.02.2 计划快照来源与精确合同集合

基准：65ec5445fc59ac13c2b81a875806936b790107f5。对应PM-03/F-PROJ-009及PM-01直接计划消费者；恢复既有精确版本/不可变快照约束，不变更API或数据库设计。

实现：ProjectPlanDraftService.prepare在编译草稿和读取运行实体之前核验有效计划ID、租户、项目、状态及原始快照格式；预览和正式启用复用同一路径。异常有效快照不能经对象默认值、当前Compiler或Designer恢复为合法版本。

ProjectPlanInitializationService回读项目指定的已发布模板版本，比较完整类型化快照（包括匹配、收口、准入、准出、规则等），不同则在计划/轮次/定时器写入前拒绝；持久化和定时器使用该已校验发布快照。合同集合不再只比数量：新增无框架TemplateExecutionContractSet验证节点精确覆盖、节点/实例/合同ID唯一性和有效ID。阶段合同核验租户/项目，任务合同核验其实际持有的租户字段；任务归属仍由原创建链保证，本次不虚构DO中不存在的projectId字段或新增跨表查询。

文件：两个计划Service、新增TemplateExecutionContractSet及其场景/桥接测试、两个计划边界测试，原初始化测试仅补充有效发布/范围夹具并保留全部旧断言。本进度是唯一状态记录。

实际验证：修改前两个Service、原初始化测试及进度已核对Git Blob；JDK21以-Werror -Xlint:all实际编译新合同集合生产类及场景，26项通过；8个Java文件语法解析0错误；差异空白检查通过。框架测试源码覆盖完整快照差异、来源替换、合同错配、异常格式、预览/启用写前拒绝及正常阶段/任务轮次；JUnit/Spring/JDK25/Maven/数据库/API/浏览器未运行。当前容器Git和依赖站点DNS不可用，无Maven，不复用历史PASS。

旧Hasher、Compiler发布格式、业务命令、历史Resolver、数据库和部署配置保持不变。下一项仍为P1.02新版本完整冻结/不可变发布及其全部Reader接线，未启用新格式，不将P1父阶段或P2～P9提前标为完成。

## P1-V1 分支定向构建与可复现验证入口

基准：3cba4ac7bfcb59ddc319827b39f9f674044fe198。实施计划I12的本阶段验证支撑，不替代P1.02生产出口或P9全阶段验收。

已审计现有integration-code-regression.yml：仅PR/master及手动触发，前述分支提交不能据此推导CI执行。新增独立template-execution-decoupling.yml，在目标分支相关代码推送时用JDK25/Maven编译依赖并执行Template、ProjectPlan、RuntimeGraph定向测试；缺少关键测试报告、零测试、失败、错误或跳过均明确失败。复用仓库现有Actions版本，不修改既有工作流、生产服务、部署或数据库。

只授予contents:read，checkout不保留凭据；日志与JUnit报告留在本仓库Actions artifact。手动运行或提交明确含[复现包]时，额外归档当前HEAD的Git跟踪源码用于隔离复现，不包含.git、Runner配置、Maven settings或未跟踪文件，保留3天。

本地实际执行：YAML结构检查、所有run块bash -n和内嵌Python语法检查通过。工作流提交时尚无该HEAD的远端运行结果，不能记录CI或JUnit通过；本地仍无JDK25/Maven，Git直连DNS失败。下一生产项仍为P1.02新格式冻结/发布接线，P1与P2～P9状态不变。


## P1-V2 操作公开契约的显式序列化依赖

基准：4811a1a08c4c7e972a4c32092298cc08fc1c372d。首轮真实JDK25 CI（run 35255389400 / job 105317637207）FAIL：project-api的ProjectOperationCommand和ProjectOperationResult引用tools.jackson.databind.JsonNode但模块没有声明Jackson依赖，导致4项编译错误；后续Project测试尚未执行。不是依赖下载故障，不把这轮写成测试通过。

修复：project-api显式声明仓库已管理版本的tools.jackson.core:jackson-databind，保持原DTO、权限和运行语义不变，采用与platform-api相同的依赖方式。为后续独立复现，定向工作流仅在提交显式含[离线复现]时归档JDK、Maven bin/boot/lib及repository；不归档Maven settings/toolchains、Git配置、Runner配置或环境转储，产物仅保留1天。

本地POM/XML、工作流YAML、Shell及Python语法检查通过；此修复尚待新HEAD真实构建。已通过源码归档获得4811a1a0完整工作树，本地Git tree与远端e46835e19b4fabbee87d81c85b99a81ab338c092一致；后续直接对准确源码实施，不凭片段猜测。P1.02生产出口及P2～P9仍未完成，不覆盖前述FAIL。


## P1.02.3 完整冻结格式与直接消费者统一读取

基准：8053868b017252d88bd520602138cd35f2144df9。对应PM-03及PM-01/PM-11直接消费者。先补SDS04a第14节与F-PROJ-009格式兼容说明，原Feature状态不晋级。

新增TemplateVersionSnapshot格式3结构校验，复用完整ExecutionSnapshot模型，不新增Hash。校验全部根集合、节点/关系/规则身份与引用、条件叶子与冻结表达式对应、规则/程序精确集合、决策表完整内联、操作PRE/POST程序与模板版本内规则一致。Reader严格区分格式2/3，原文绑定保留小数精度；拒绝重复JSON字段、尾随文档、未知新格式字段、缺失闭包及类型降级，不在读取时重新编译、求值或回查最新配置。

18个直接消费文件的20处原始快照绑定统一接入Reader，覆盖计划/执行历史、准入、完成、收口、门禁、返工、定时器、业务上下文和结果分派；Freezer和定时器的类型化入口也校验格式3，不能绕过原始读取。原Owner授权、业务命令、锁序和正式状态Writer不变。本轮不改TemplateCompiler、ProjectTemplateV2ServiceImpl发布方法、旧Hasher、历史Resolver或数据库；格式3发布仍未启用。

新增TemplateVersionSnapshotTest、TemplateSnapshotConsumersTest共53个参数展开用例；保留旧读取断言，并将明确未知的类型化版本改为4。扩大直接消费者回归时发现ProjectRuleTimerTest缺少现有生命周期依赖，补齐真实任务准入服务的测试装配，验证未知/不满足时不启动、满足时只调用现有startAdmittedTask且保持同一事件ID；没有修改生产准入逻辑或删除旧负向断言。

### 实际验证与剩余限制

从本仓库CI的离线复现包取得JDK25.0.4.1/Maven3.9.16及真实依赖，当前容器现可执行离线Maven，不再沿用“只能语法解析”的旧限制。最新定向命令在完整28模块依赖反应堆执行test，真实编译生产和测试源码，8组153项测试通过，0失败、0错误、0跳过：TemplateExecutionSnapshotReaderTest(31)、ProjectTemplatePublishedSnapshotBoundaryTest(19)、TemplateVersionSnapshotTest(45)、TemplateSnapshotConsumersTest(8)、ProjectRuleTimerTest(11)、ProjectRuntimeGraphSnapshotFormatTest(12)、ProjectExecutionHistoryServiceTest(8)、ProjectStageGateProcessContextResolverTest(19)。日志为本轮容器npdms-p1023-focused-r3.log及对应Surefire XML；提交树逐文件与该源码Git Blob核对，git diff --check通过。

定向通过不代表全套通过：8053868b的真实CI run35256618711为FAIL，437项中1项旧Controller权限契约断言失败、4项MySQL用例因未提供环境跳过；未覆盖这些缺口。当前定向测试使用持久化替身，不计为MySQL事务/并发、浏览器或业务验收。之前本地测试夹具的集合修改异常、定时器装配和事件ID断言失败已修复后重跑；只以上述最新153项计PASS，不覆盖既有FAIL历史。

下一项P1.02.4：新格式Compiler/发布/匹配/复制/计划写入，以及数据库可空约束和已发布不可覆盖保护。未全部接通前不产生格式3发布数据；P1仍为IMPLEMENTING，P1.03/P1.04及P2～P9尚未完成。没有部署、迁移、服务重启或旧实例切换。


## P1-V3 / P1.02.4 回归纠正与完整版本发布接线

基准：ba793870df04a3737dede25ca8f756d72732d4c5。重新读取远端确认原HEAD只有16d2c6b1；此前对话所称d7440272、8978cbec未成功创建/推送，不属于已交付进度，也不采用其声称测试结果。ba793870仅修复事实目录的实际双消费权限测试，生产授权不变；准确源码通过本仓库Actions归档回读，完整Git树与远端8501fc739eaae256f3a011f9ea1c02061bbfeaee一致。

### 实现与保护

- TemplateCompiler保留原compile及格式2/旧Hasher路径，新增compileVersioned共用原编译逻辑后形成格式3完整快照，并经同一严格Reader回读；新路径不调用旧Hasher、snapshotHash为null，不新增摘要。
- ProjectTemplateV2ServiceImpl的校验/发布接入新编译入口；新发布行明确保存租户、递增revision、完整Designer和Snapshot、实际格式/编译器元数据。发布仍处于原模板锁和事务内，插入、状态更新、版本递增返回非1即失败；草稿写之前核验租户/模板/正数行ID/revision0/DRAFT，禁止把已发布行当草稿更新。
- TemplateVersionPublication统一格式3发布身份、冻结Designer及执行快照回读；运行读取、revision投影、最新匹配和发布版复制均接入。拒绝错租户/模板/版本、混入Hash、损坏/缺失Designer及快照、重复/未知字段。复制只创建独立新草稿，不反编译Snapshot，不修改原发布内容。最新版坏数据不回退早期版本。
- ProjectPlanDraftService预览/启用使用compileVersioned；初始化对回读的完整发布快照执行格式3验证。正式计划Writer、原锁序和Owner规则不变；六处原计划测试Mock同步到实际新调用入口，旧断言保留。
- 核查既有V52/V206/V228及Mapper：已有模板版本唯一键、snapshot_hash可空和原发布/草稿写分离；未新增数据库列/表、Hash、触发器或迁移。生产代码可写格式3不代表部署或数据库验收已通过。

### 实际验证

同一任务的独立源码副本并行运行基线验证，主工作树实施发布接线；未使用多代理、未并行写共同文件或分支引用。

JDK25.0.4.1/Maven3.9.16离线28模块反应堆真实编译生产及测试源码，准确ba793870基线487项非MySQL测试通过。加入本增量后最终520项通过，0失败、0错误、0跳过；其中新增编译8项、发布/复制/匹配25项。命令：`/mnt/data/mvn-local.sh -pl pms-module-project -am -DskipITs=true '-Dtest=*Template*Test,*ProjectPlan*Test,*ProjectRuntimeGraph*Test,!*MySql*' -Dsurefire.failIfNoSpecifiedTests=false test`；本轮日志`/mnt/data/logs/p1024-final.log`与Surefire报告一致。两次范围重叠，不累加为1007项。

新公开复制用例首次失败是测试直接写入未经原保存层规范化的Designer，与真实草稿来源不一致；改为使用既有TemplateRuleCollection.forEditing生成夹具后重跑全部上述测试，保留原发布内容字节不变断言。git diff --check通过；对旧Hasher、历史Resolver、旧基础Service、生产Controller、业务DTO、数据库和部署文件无修改。

这是L2级真实编译/序列化/Service单测（外围持久化与Owner依赖Mock），不是MySQL事务/并发或L4验收。未执行4项已有MySQL计划测试，未取得真实发布事务、浏览器或全业务验收结果；原CI FAIL/跳过记录仍保留。没有部署、迁移、重启或旧实例切换。

### 下一项

P1.03独立操作/订阅/证据/页面配置与草稿无损保存，随后P1.04原业务输入/URL安全边界。新订阅/政策实际能力未接入时必须禁止相应发布，不以格式3发布已接线声称P1或P2～P9完成。


## P1.03 独立配置、原权限编译与草稿无损保存

基准：bda6f2bc602d1d3ef1091745c21a769d72606b3e。先按当前HEAD审计Designer/RuleCollection/Compiler/格式Reader、保存及复制Service、操作目录/编译器和前端规则消费者；通用配置契约先追加SDS04a第15节，复用PM-03/F-PROJ-009，不修改历史Feature状态。

Stage/Task增加可选execution，严格区分操作、结果订阅和页面呈现；无字段的旧JSON/Hash不变。操作复用原权限和动作、原PRE/POST规则，无技术版本组合、类名或写URL输入；编译唯一解析动作后派生原operationContract，复用实际处理器/规则编译，不产生第二套业务命令。新旧可编辑操作来源冲突拒绝；未解析、共用权限未消歧、跨Owner、运行适配缺失均不发布。

订阅内联Owner/实体/结果类型、明确对象范围与三种获取/两种有效性/三种数量政策；ID保持字符串，未知字段、显式null、错误政策组合、重复对象和无界ALL_EXPECTED拒绝。纯订阅无需主绑定、命令或页面即可保存草稿。结果来源/证据/恢复尚未接线，订阅发布被明确阻断，不将模型存在记为P3～P5完成。页面URL/独立query可保存；P1.04安全路由未接通前不发布页面配置。

原Designer保存/重开/复制通过现有归一化保留execution；RULE引用进入原共享规则校验；旧content写接口拒绝覆盖含新配置的草稿，避免有损投影抹掉配置。格式2拒绝夹带新execution；格式3冻结真实动作及规则，并按动作身份而非展示顺序核对派生契约。前端同步类型和规则引用提示，没有增加新业务状态或新模板根。

实际验证：同一Feature的独立源码副本并行执行准确bda基线520项回归；主工作树实现并自审后JDK25.0.4.1/Maven3.9.16离线28模块真实编译，566项测试全部通过，0失败/错误/跳过（包含新增46项，不与基线相加）。日志为/mnt/data/logs/p103-final.log及Surefire XML。新旧前端Node契约测试33项全部通过；execution.ts单文件严格tsc检查通过；git diff --check通过。自审补齐逆序多动作、显式null、无绑定纯订阅保存、原权限简写保存后发布及旧草稿不变测试。查询解析不调用Owner命令。

以上为真实单元/序列化/Service验证，持久化与外围依赖使用Mock；未运行MySQL、完整Vue类型/组件、API鉴权代理或浏览器，不声称整体CI/业务验收通过。旧Hasher、历史Resolver、业务Controller/DTO、数据库和部署配置保持不变。下一项P1.04；P2～P9未因此晋级。没有部署、迁移、重启或实例切换。


## P1.04.1 / P1-V4 页面基础边界与原回归替身修复

17546cbf基于55a4：新增TemplatePresentationUrl，站内路径与独立参数分别校验/编码，拒绝协议/外域/穿越/路径编码/片段/API根和身份凭据参数，字符串ID无损；没有路由授权或Owner写入。JDK25以-Werror -Xlint:all实际编译生产类及同一场景源码，63个无框架行为场景通过；JUnit桥接后续纳入回归，其一项测试内部复用63个场景，不与JUnit计数重复相加。

已从17546cbf的真实Actions源码归档恢复完整工作树，Git tree=d5ab08c6b72494ec3487ca066608b884c201c29f；独立副本复跑567项模板/计划/运行图非MySQL回归全部通过。55a4真实CI产物另有570项总记录，其中4项MySQL因缺夹具跳过，不能标整体CI通过。

df2fc1e在未修改生产的17546cbf副本上复现RequirementAnalysisOwnerOperationContextTest：重设insertRevision的when调用先触发旧Answer并收到null。仅用doReturn修正替身设置，保留插入失败不得注册派生对象/调用表单文件等全部原断言；原5项测试全部通过。此修复不改变业务或权限规则。

## P1.04.2 原业务输入、字段约束与呈现保存/编译边界

基准：df2fc1e393ec67ee627025ce075e73c84b438b8a。先审计C0三个实际CommandAdapter、原Controller/SaveReq/Commands及前端提交路径，通用边界追加SDS04a第16节；不修改原业务DTO、Controller、服务状态机或旧Hash。

ProjectOperationInput仅使用代码指定的原业务类型和已有Mapper配置，拒绝非对象、execution/tenantId、未知字段、小数转整数及null转原始数值默认值；不改全局Mapper、不加载用户类名、不回显原请求或Java类型。动态业务Map保留原字段，由Owner继续校验。

三个真实适配器均已接入：工勘复用SiteSurveyEntitySaveReqVO/Bean Validation，重复对象/项目/版本输入不再静默覆盖，四个对象动作拒绝多余输入；需求分析SAVE复用原Patch，CREATE拒绝被忽略输入，COMPLETE/COPY原因只接受文本/null；验收报告复用原Draft/Publish/Revoke ReqVO及Bean Validation，再调用原Commands，保留大小/正数/并发约束和原权限。更新报告的路径传输字段仅从私有副本剥离；原意图与幂等键不变。

工勘受控客户端单独去掉GET返回的只读地点/表单目录/审计等投影，原写字段、业务/扩展值及未知新增字段均保留给服务端校验；独立普通HTTP请求分支不变。新presentation在草稿保存归一化及编译前接入URL语法校验；非法配置不能到草稿Writer或生成快照。合法配置保存重开无损，但尚未安装的页面/订阅继续返回明确阻断，不冒充路由已登记或页面已可用。

实际验证：JDK25.0.4.1/Maven3.9.16离线29模块真实编译。新输入定向74项通过；扩大到模板、计划、运行图、操作和工程事件回归后，最终项目683项+工程46项=729项全部通过，0失败/错误/跳过，日志/mnt/data/logs/p104-all.log。74项是729项的子集，不相加。前端Node实际执行受控输入投影2项通过；完整Vue/Vitest/浏览器尚未运行。此前扩大回归的既有替身FAIL由P1-V4修复后重跑，记录保留。

源码自审与git diff --check通过；没有新增数据库结构、Hash或DTO技术字段，没有改旧Reader/历史Resolver/业务状态机。剩余：受信页面登记及真实呈现消费尚需接线（未放开页面发布），P2～P9仍按各自出口实施；当前验证使用外围Mock，不是MySQL事务、API代理或浏览器验收。没有部署、迁移、重启或实例切换。


## P1-V7 / P1.04.3实际验证与远端写入阻断

基准：2f815172c1872d3134feef7de91e6fe0d803f9c2；P1-V7提交7955514192b4df3e671e9e060ead29bde062827e已通过非强制快进回读确认。此增量只修改三个前端测试文件：复用仓库已有Vue测试渲染器，移除未声明的vue-test-utils测试依赖；旧Owner页面用例明确模拟LEGACY_BINDING并隔离浏览器HTTP初始化，原权限、身份、恢复和未保存输入断言保留。四组65项Vue测试通过；原失败记录不覆盖。独立后端基准727项非MySQL回归通过，不与后续扩展回归相加。

P1.04.3已完成本地实现、自审和定向回归，但远端提交未完成：受信PAGE路径由现有Owner Provider登记，发布/冻结Reader/能力查询/实际BusinessViewHost及静态组件选择均已接线；URL不是写API，参数只回显同一已选项目/对象，不成为命令、权限或执行身份。异常/停用页面限制呈现，保留原表单和恢复客户端；页面参数变化须处理未保存内容并显式重新进入。实际路径是宿主内嵌组件别名，不是新增全局浏览器路由。纯订阅来源及证据/恢复尚未接通，相关发布限制未放开。

本地代码验证：JDK25.0.4.1/Maven3.9.16离线29模块真实编译成功，项目714项+工程46项=760项非MySQL测试通过，0失败/错误/跳过；原65项加新增21项，共86项Vue/Vitest运行测试通过；44项Node测试通过；presentationRoute.ts单文件严格tsc及git diff --check通过。新测试是各总数的子集，不重复累加。MySQL事务、完整Vue类型检查、API鉴权代理及真实浏览器业务验收未执行，不能标整体CI或P1已验收。

提交阶段，连接器对operationHost.ts的完整文件写入两次返回“因OpenAI无法确定请求的安全状态，已拦截此工具调用”，没有返回对应新tree/commit。已上传的其他中间Git对象不是分支提交；未将任何半接线tree更新到分支。此时最后已核验的生产/测试基准仍是7955514192b4df3e671e9e060ead29bde062827e。本进度更新是独立记录，不是P1.04.3实施提交。

已通过测试的完整P1.04.3代码、必要SDS差异和测试源码保留在本地补丁；适用代码树0d1533e932db7ea6cd34216c8e2df2b29ac35ba0是本地已验证暂存树，不是远端提交SHA。后续须在实际HEAD重新核对并完成原23文件增量的完整提交，不能直接使用本地SHA推进远端引用。P1继续IMPLEMENTING，P2～P9未因本记录晋级；没有部署、迁移、重启或实例切换。
