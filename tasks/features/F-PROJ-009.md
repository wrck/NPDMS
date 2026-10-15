# F-PROJ-009 项目交付模板配置中心

> Feature实施状态：`IN_PROGRESS`
> Implementation Done Gate：`NOT_READY`
> Requirement ID：`PM-03`
> Feature Spec：`specs/features/F-PROJ-009-project-delivery-template-configuration-center.md`
> 专项模式：`Template V2 Runtime Rewrite`
> 当前基线：`codex/feature-customer-contact`

## 2026-09-12 专项执行规则

本专项经需求方明确授权，不再受此前“生产代码必须沿既有工程实施链、不得重做运行模型”的限制。当前模板运行时尚未完全走通，允许以收益为依据替换尚未闭环的 Template Authoring / Publish / Runtime 层，但不得借机重写已经形成稳定业务语义的 Project / Stage / Task / Deliverable 运行域。

专项执行顺序固定如下：

1. 先 review 当前分支已提交代码、运行链和验证状态，禁止按旧计划重复实现。
2. 同步本 Task 的专项计划、任务逻辑和已完成内容。
3. 直接进行代码重写和收敛；每完成一个独立步骤立即单独提交，不把多个未验证步骤揉成一个大提交。
4. 代码重写完成后，再进行一次正式 Feature / SDS / API / DB 规格落库；本轮规格不先行约束专项重写。
5. 完成定向测试、编译/构建及可获得的 CI 验证；未实际执行的验证不得宣称通过。
6. Legacy 已发布模板不得被当前 Compiler 静默重新解释或自动升级；需要进入 V2 时只能走显式复制/升级边界。
7. 不从 `sortOrder`、阶段编码或历史缺失信息推导关系图；既有项目冻结历史不因模板 V2 重写被覆盖。

## 当前任务逻辑与目标边界

本专项不再把 `TemplateDefinitionContent + DefinitionRevision + FrozenDefinitions + ExecutionContract` 的多层转换视为必须兼容的永久架构。目标收敛为：

```text
ProjectTemplate（业务身份）
        │
        ▼
TemplateDesignerDocument（设计态唯一真值）
        │
        ▼
TemplateCompiler（唯一解释 / 校验 / 冻结边界）
        │
        ▼
TemplateExecutionSnapshot（发布态不可变真值）
        │
        ▼
ProjectRuntimeInitializer / TemplateInstantiator
        │
        ├─ ProjectStageInstance
        ├─ ProjectTaskInstance
        ├─ ProjectDeliverable
        └─ ProjectRuntimeGraph
```

核心约束：

- Designer 只表达模板业务设计，不要求用户维护 DefinitionRevision 技术引用。
- Compiler 是设计态进入运行态的唯一解释边界；同一发布版本的 Snapshot 必须稳定、可复现、不可被未来 Compiler 静默改变。
- V2 Runtime 只消费持久化 `TemplateExecutionSnapshot`，不得再次解析 DesignerDocument 或当前 DefinitionRevision 来改变发布语义。
- `TemplateDefinitionContent` 在 V2 新项目链仅允许作为进程内兼容投影 DTO，不再是模板运行真值。
- Project / Stage / Task / Deliverable 的实例表、状态机、Owner 业务服务和审计历史继续保留。
- Legacy 模板保持历史只读兼容；如果要用于新的 V2 发布/新建链，必须显式复制/升级并生成新的 V2 Snapshot。
- Rule Tree 与 Decision Table 是同一 Rule AST 的不同编辑视图，不建立第二套规则模型。

## 2026-09-12 最新已提交代码 Review

Review 基线 HEAD：`5affcaf95d80ef9a7bcae970cbe297de10b158ee`。

从首次专项计划同步提交 `bb6465c0a97f5702b71a38f30a15d0ed4f982489` 到当前 HEAD 共新增 7 个独立提交，涉及 Project Creation、Template V2 Service、RuntimeGraph freezer 及对应回归测试。逐项复核后，没有发现需要回滚的已提交实现；总体方向已经从“V2 与 Legacy 混合运行解释”收敛为“新写只走 V2 Snapshot，Legacy 只保历史读取”。

当前 HEAD 无 GitHub combined status，且无 PR-triggered workflow run；因此本轮仍没有新的 CI / 构建通过证据，不得表述为已绿。

### 本轮 Review 后确认的边界

1. **Legacy 发布版不再静默重编译**：历史发布 revision 缺少持久化 V2 Snapshot 时，`getExecutionSnapshot()` 直接拒绝，不再调用当前 Compiler 即时解释。
2. **Runtime node identity 已定界**：`nodeKey` 是规范身份；60-bit `runtimeNodeId` 仅用于未改造 Long 契约的兼容投影，当前分支已不存在按该 Long 跨模板全局反查模板 revision 的路径。
3. **Project Creation 已切到 Snapshot**：项目创建主链和 PRE-02 创建后初始化均从 `getExecutionSnapshot()` 获取运行输入；`TemplateDefinitionContent` 仅由 Snapshot 在进程内投影，不再读取发布 Definition rows 作为新项目运行真值。
4. **RuntimeGraph freezer 已单写 V2**：创建期 freezer 已删除 `FrozenDefinitions / legacy` 写入分支；兼容重载只允许从 `TemplateDefinitionContent.executionSnapshot` 解出 V2 Snapshot。历史项目继续由 Resolver 读取其已冻结的历史 Contract / Transition，不依赖 freezer 重建。
5. **新项目匹配已排除 Legacy revision**：V2 `matchPreview()` 只把最新发布版具有 `executionSchemaVersion + executionSnapshot + compilerVersion + snapshotHash` 的 ACTIVE 模板暴露为新项目候选，避免“预览命中、创建才失败”。
6. **仍存在 Snapshot 完整性缺口**：发布时已持久化 schema/compiler/hash，但当前读取只校验 envelope 字段存在，尚未校验 Snapshot JSON 内部 `executionSchemaVersion / compilerVersion` 与行字段一致，也未重算语义 hash 验证 `snapshotHash`。这是下一步首要修复。

## 已完成并保留的 V2 能力

- [x] `TemplateDesignerDocument`：Stage / Task / Milestone / Edge / Rule / Binding / Permission / Deliverable / Closure 的设计态模型。
- [x] `TemplateCompiler`：Designer 到不可变 Execution Snapshot 的编译、校验、稳定排序和语义哈希基础。
- [x] `TemplateExecutionSnapshot`：V2 运行快照模型及基础确定性约束。
- [x] V2 草稿 / 发布 Service 与 API 主链。
- [x] BusinessView / DynamicForm 等发布依赖的精确版本校验和快照固定基础。
- [x] V228 / V229 等 V2 相关前向迁移基础。
- [x] 模板统一设计器工作区：阶段与任务、流程画布、规则与决策、高级配置。
- [x] `StageGraphDesigner`：显式关系图编辑，不从阶段顺序推导边。
- [x] `RuleDecisionDesigner` + `ruleDecisionModel`：规则树 / 决策表共享同一 AST，复杂嵌套拒绝有损转换。
- [x] Legacy 发布版禁止当前 Compiler 静默重解释。
- [x] V2 项目创建主链与 PRE-02 初始化切换到持久化 ExecutionSnapshot。
- [x] RuntimeGraph freezer 新写路径只接受 V2 Snapshot。
- [x] 新项目模板匹配排除仅有 Legacy 发布版的模板。
- [x] Runtime node identity 作用域通过架构回归测试锁定，未做无收益的全局 ID 重写。
- [x] TASK_NATIVE / Owner completion 的分流与原生完成能力已进入当前分支。
- [x] S0 / pre-project 与 primary assignment 等当前分支后续改造已合入，不在本专项重复实现。
- [x] 2026-09-09 六类草稿纠偏、S0 零任务、操作型重复任务移除、真实 PAGE / DYNAMIC_FORM 绑定保存等历史成果继续保留；它们不等于 V2 Runtime 已闭环。

## 本轮专项提交记录

- `bb6465c0a97f5702b71a38f30a15d0ed4f982489` — `docs(pm-03): refresh template runtime rewrite task plan`
- `db0d5ab176c7cc7077e4ddd7a5de99f009105421` — `fix(pm-03): stop implicit legacy snapshot recompilation`
- `95eb9272e80a303b20493a49ea74c0424b92c3b6` — `test(pm-03): lock runtime node identity scope`
- `b57ad994c9f4ade36d6aa4b71e606c53c19ec98d` — `refactor(pm-03): initialize preparation from execution snapshot`
- `e9ea26175a99a6cd9b8e5ddab22b1d8647cd06d1` — `refactor(pm-03): instantiate projects from execution snapshot`
- `a3cff91e0652292331e56907ffdf656946b98ce5` — `test(pm-03): migrate project creation fixtures to snapshots`
- `06a5072a4559616481833e6e19b49be064fb36a0` — `refactor(pm-03): make runtime graph freezer snapshot-native`
- `5affcaf95d80ef9a7bcae970cbe297de10b158ee` — `fix(pm-03): exclude legacy revisions from new project matching`

## 本轮剩余专项步骤

按“每完成一步就提交一次”继续执行：

- [x] **Step 0 — Review 当前已提交代码**：确认当前 V2 实际落地范围、验证状态和结构性风险。
- [x] **Step 1 — 同步专项 Task**：建立专项模式、目标架构、执行规则和初始 Review 结论。
- [x] **Step 2 — 修复 Legacy Runtime 边界**：历史发布版本缺 V2 Snapshot 时不再被当前 Compiler 静默重编译。
- [x] **Step 3 — 审计 Runtime Node Identity**：确认 `nodeKey` 为规范身份，Long 仅为兼容投影，并用测试锁定禁止恢复跨模板全局 lookup。
- [x] **Step 4A — PRE-02 初始化切到 Snapshot**：创建后准备域初始化不再重读 Legacy revision。
- [x] **Step 4B — Project Creation 主链切到 Snapshot**：新项目实例化只从持久化 ExecutionSnapshot 获取模板运行输入。
- [x] **Step 4C — 创建链测试 fixture 切到 Snapshot**：显式断言创建链不调用 `getRevisionContent()`。
- [x] **Step 5A — RuntimeGraph freezer 单写 V2**：删除创建期 Legacy freezer 分支，历史兼容仅留在 Resolver 已冻结事实读取。
- [x] **Step 5B — 新项目匹配排除 Legacy revision**：只暴露完整 V2 runtime envelope 的最新发布版。
- [ ] **Step 5C — Snapshot 完整性校验**：固定 schema-v2 语义哈希算法，读取时校验 row/snapshot 的 schema、compiler、hash 一致性；不得通过重新编译 Designer 验证。
- [ ] **Step 5D — 继续清理失效 Legacy Bridge**：仅删除已经无新写调用或会重新打开双解释语义的桥接；历史详情/显式升级入口保留。
- [ ] **Step 6 — 验证与修复**：运行可执行的后端定向测试、前端测试、类型/构建和 CI；每个实际失败修复单独提交，不用历史通过记录替代本轮验证。
- [ ] **Step 7 — 正式规格落库**：代码重写稳定后，一次性更新 Feature Spec / SDS / API / DB 规格，使 `DesignerDocument → Compiler → ExecutionSnapshot`、Legacy 边界和实际代码一致；规格提交与代码提交分离。
- [ ] **Step 8 — Task 收口**：回填最终 commit、验证证据、仍未完成边界和 GO/NO-GO；若仍有外部 Owner / 环境阻断，保持 `IN_PROGRESS / NOT_READY`。

## 本专项验收条件

只有同时满足以下条件，才允许把 Template V2 Runtime 视为本专项代码闭环：

- 新 V2 发布版本持久化不可变 `TemplateExecutionSnapshot`，运行时不依赖当前 Compiler 重解释该发布版本。
- Legacy 发布版本不会因 Compiler 升级发生静默语义变化；显式升级边界有测试。
- 新建项目 V2 路径以 Snapshot 为唯一模板运行输入，Stage / Task / Deliverable / Transition / Completion 合同来源可追溯。
- Runtime node identity 的作用域和唯一性有代码或测试证明，不存在未识别的跨模板碰撞语义。
- Rule / Binding / Permission / BusinessView / DynamicForm 等发布依赖在 Snapshot 中保持精确固定，不在运行时漂移。
- Designer / Compiler / Snapshot 的 schema / compiler version 和 semantic hash 能支撑发布后确定性验证，读取时能识别 Snapshot 损坏或漂移。
- 当前分支相关测试和构建由本轮实际执行并记录；CI 未绿或无 CI 证据时不得宣称合入 Gate 已通过。
- 代码收敛后完成一次正式规格落库，规格描述与最终实现一致。

## 历史成果摘要（保留，不作为本轮运行时闭环证据）

2026-09-09 已完成六类未发布草稿的业务任务纠偏：任务从 115 收敛为 65（14/13/13/12/11/2），S0 零任务，移除操作型/V2 重复任务、人工里程碑和默认逐任务必传文件；保留必要可选文件槽。模板页面已支持阶段导航、任务侧栏、真实已发布 PAGE / DYNAMIC_FORM 绑定及失败后保留编辑，历史已发布定义和项目实例未改写。

固定测试库数据补全只处理有明确模板来源的 ACTIVE 项目，不对无来源项目猜测模板、不从 sortOrder 补边；早期候选和 master 选择性集成记录继续作为历史证据，但不替代本专项对 V2 Runtime 的重新验证。

Feature 继续保持 `IN_PROGRESS`，Implementation Done Gate 继续保持 `NOT_READY`，直到上述代码收敛、验证和最终规格落库完成。

## 2026-09-21 模板种子替换与前向升级

依据需求方本轮确认，新增V336保存最终交付件验收模板原发布快照及4个业务视图，新增PRD 3.2前六类项目场景草稿；已有库仅按精确种子身份停用旧模板，保留发布版本和项目历史。新库使用独立初始化路径，移除旧模板及其依赖示例项目，原历史迁移不改写。

隔离MySQL空库279个迁移至V336及再次执行通过；升级脚本重复执行、身份冲突回滚、租户/creator范围和历史保护验证通过；实际模板编译器验证原验收文档及6个派生草稿通过。未迁移实际应用库、未发布派生场景、未重新作浏览器验收；该数据交付不晋级Feature状态。细节与BPM部署边界见[验证记录](../../docs/generated/2026-09-21-project-template-seed-migration.md)。

后续经需求方明确授权，`npdms-domain-test / npdms_domain_test`已成功执行V330—V334及V336，迁移后校验及重复执行通过；287条既有项目、发布修订、计划版本与执行合同逐行比对未变。导入1个ACTIVE验收模板、6个DRAFT场景及4个业务视图，名单内15个旧示例最终均停用（本轮14个、既有1个）。详见同一验证记录的实际库执行章节；派生场景仍未发布或获得浏览器验收。

## 2026-09-22 流程图模板结果接入

用户明确要求人员指派、计划审批、方案审核、割接完成接入模板，并取消规则可选字段硬编码。本增量在 `codex/domain-migration` 串行实施，复用指定 `19191/59191`、`npdms-domain-test` 环境；相关旧模板业务视图 DU 已记录释放本轮写边界。不自动提交、合并或推进 Feature 状态。

- [x] 项目实体元数据生成字段目录，保留旧字段含义与创建时匹配边界。
- [x] 四类 Owner 完成事实、版本/权限复验及原业务结果变化唤醒。
- [x] 已有业务入口与配置供给接线，更新本会话新建草稿。
- [x] 定向失败测试、实际环境 API 与真实浏览器核验，记录重大方案等真实限制。

复用审计：人员指派沿用项目主档和成员有效区间；施工计划使用 `StagePlanBatchServiceImpl` 的审批生效批次，不混用工期基线；普通方案使用 `SolutionServiceImpl` 的批准基线，重大方案当前明确拒绝；割接使用 `cut_task` 与 `cut_cutover_closure` 的 P6 归档事实。原规则目录为手写 `ProjectRuleFields`；改为项目实体元数据，原 HTTP 规则字段契约兼容，内部调用统一注入元数据服务。新增查询采用场景 Query/XML，原领域命令仅增加完成结果重评估接线，不以模板层代替领域审批。

实际交付：模板 993009900014 / V9 DRAFT，56 个自动发现字段，4 个真实完成依据与业务视图已配置；原有 25 个模板版本/状态不变。后端 105 项定向测试、规则组件 7 项、浏览器保存重开及动态配置选择通过。阶段 S1 工前依据和 S3 工具检查/重大复审仍阻止发布。全量前端类型检查及 BusinessView 无 DOM 测试环境的未通过项如实记录；不晋级 Feature Done。详见 [接入与验证记录](../../docs/generated/2026-09-22-visio-delivery-template/integration/README.md)。


### 2026-09-22 剩余流程续办

需求方确认忽略自动配置检查，重大复审由工程管理部办理。复用现有方案字段、表单和普通审核，新入口复制原页面后补 BPM 分级审批，原路由保留；SOL 形成版本批准，PROJ 消费完成事实。工作项：补分级审批及失败验证，部署指定隔离环境，回填模板 S3；工前判断来源等待答复后配置，不臆造外部来源结果。

### 2026-09-22 续办交付回执

- [x] 项目经理工前确认表、历史保护及三个独立支线条件配置；字段由动态表单与实体元数据供给。
- [x] 服务经理审核 → 工程管理部复审，实际 BPM 模型、候选资格、冻结版本、真实终态与复制修订闭环。
- [x] 按用户提供的三个 CRM 重大项目级别配置复审/普通规则；提交时自动判定并留存源事实快照，旧普通入口不能跳过复审，未知依据阻止提交。
- [x] 指定隔离环境构建部署、V340～V343 迁移与重复执行、62 项本增量后端测试、前端类型检查、8+37 项组件测试及相关真实浏览器闭环。

模板 `993009900014` 最终 **V15 / DRAFT**，39 条规则，编译预检无问题，其他 25 个模板版本/状态保持原值。实际分级审批、驳回复制与工前表单使用独立验收项目；未发布源图模板或宣称完整 S0～S5 项目运行验收。本次临时人员已停用，历史保留。Feature / Implementation Done 状态不因本次配置增量晋级，未提交或推送。见 [完整回执](../../docs/generated/2026-09-22-visio-delivery-template/remaining/README.md)。

### 2026-09-22 工前来源更正回执

- [x] 按需求方更正取消独立填报和额外 S1 门禁，原四任务及工勘原生确认继续生效；旧记录只读、旧提交停止。
- [x] 按需求方明确的工勘第 8、第 9 及物料环境项配置三个独立支线，不新增输入字段，不硬编码字段名。
- [x] V344 前向及重复迁移；13 项后端、16 项前端测试、全量前端类型检查、宿主机打包及真实浏览器 8 组合/保存/重开/确认/历史保护验证通过。

当前模板 **V16 / DRAFT**，Compiler 预检通过；本段替代上段独立工前表的当前口径，上段 V15 回执保持历史含义。未发布源图模板、未晋级 Feature / Implementation Done。完整范围及证据见 [本轮唯一回执](../../docs/generated/2026-09-22-visio-delivery-template/survey-source/README.md)。

### 2026-09-22 历史工前确认代码剔除

按需求方明确要求，移除历史记录页面及引用、专用 Controller / Service / DO / Mapper / XML、表单策略和规则前缀兼容读取，共删除 9 个源文件，并删除该功能专属测试。应用源码引用归零；已执行迁移和数据库归档不变。工勘原字段、绑定、确认与独立支线配置保留。

验证：清理项目模块构建产物后重新打包，服务包不含已删类和 Mapper；12 项相关后端测试、前端全量类型检查通过。真实浏览器确认历史入口消失且不再发起旧请求，既有工勘详情与支线正常、已确认内容保持只读；旧 GET / POST 均返回路由不存在（业务码 404）。后端 59191 健康 UP，前端 19191，指定 npdms-domain-test 环境。模板仍 V16 / DRAFT，预检通过。

证据：[移除检查回执](../../docs/generated/2026-09-22-visio-delivery-template/confirmation-code-removal.json)、[页面截图](../../docs/generated/2026-09-22-visio-delivery-template/confirmation-code-removal.png)。本段替代上段“应用只读保留”的当前口径，不改写其历史验证结果；未提交或推送。

### 2026-10-06 方案审核级别分支修复

用户报告“方案流程须在服务经理审核后按审核级别分支”。核对确认：`SolutionReviewBpmService` 已要求分支，但内置 BPMN 和本机已发布 V1 仍为服务经理直连工程管理部。按既有冻结审核级别补排他网关：`0` 经服务经理通过后结束，`1` 进入工程管理部复审；不设置未知级别默认放行路径。原职责、候选人冻结与权限重验监听器保留。

- 定向验证：新增 6 项真实 Flowable/H2 用例先复现原报错，修复后连同 6 项既有候选/租户/权限测试全部通过。覆盖普通结束、重大复审后结束、缺失复审授权、缺失人工决定与未知级别失败回滚。命令：`mvn -pl yudao-module-bpm -am test -Dtest=SolutionReviewBpmnFlowableTest,SolutionReviewBpmGuardTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false`。
- 用户确认本机 `19191/59191` 环境并完成登录后，经现有模型 update/deploy 接口发布 **V2**；方案定义接口返回正常，旧 V1 定义保留。见[发布回执](../../docs/generated/2026-10-06-solution-review-branch/deployment.json)。没有重启应用或改写已部署定义内容。
- 真实浏览器：在既有验证项目 `PJT2026000031 / 992203060038` 中新建本轮方案（正文注明 2026-10-06 分支回归验收），完成提交、服务经理审批、终态和历史回读；实例 `de9383c4-c15b-11f1-8ba1-00ff0b78abd7` 为审批通过，仅有服务经理审核记录。方案业务结果同步为已通过，页面重开后正文只读且无提交按钮；项目仍处工前准备。见[审批历史截图](../../docs/generated/2026-10-06-solution-review-branch/ordinary-approved-history.png)。重大分支本轮完成引擎验证，未完成当前环境的真实浏览器复审闭环。
- 另发现既有 BPMN 设计器预览序列化会丢失 `pmsReviewResponsibility` 扩展；本次退出未保存页面，通过模型接口保留完整职责字段发布。设计器问题未在本次修改，不能将本回执视为设计器保存验收。

本次只修复流程资源、补回归测试并更新指定本机定义；不晋级 Feature / Implementation Done，未提交或推送。

### 2026-10-06 项目详情内审批记录抽屉

按用户明确要求，项目详情内查看审批记录改为 Drawer。当前菜单实际入口 `/pms-inheritance/project-detail` 和 `/pms/project-management/project-master-detail` 均提供页内审批抽屉；任务、阶段、门禁流程、施工计划、工期变更、方案审核和正常闭环共用该打开能力。复用原 BPM 流程详情组件及其审批、流程图、流转记录、评论和授权接口，不复制审批逻辑。未提供项目详情上下文的独立业务页面仍使用原详情路由。

方案审核的项目内面板改为抽屉，保留提交、刷新结果和复制新版本操作；补上非草稿终态方案的审批记录入口。关闭完整审批抽屉会卸载详情，重开或切换实例按实例 ID 重新加载；项目办理区和未提交表单保留，提交中仍禁止打开重叠办理。

- 定向验证：7 个相关测试文件共 80 项通过（按受影响检查分别执行）。新增抽屉实例切换、关闭重开、独立入口兼容和任务未保存表单保留验证；原有审批授权、版本、并发提交、重试及离开保护用例保留。
- 真实浏览器：复用本机 `19191` 和既有项目 `992203060038` 的已通过方案，验证方案审批面板抽屉、完整审批记录抽屉、流转记录、流程图、关闭重开和项目工作区保留。地址保持项目详情，实例仍为 `de9383c4-c15b-11f1-8ba1-00ff0b78abd7`，历史服务经理通过记录正常回读。见[抽屉截图](../../docs/generated/2026-10-06-project-approval-drawer/approval-records-drawer.png)。其余审批入口完成代码接线及适用组件验证，本轮未逐项重新执行各自业务审批。
- 全量前端类型检查未通过：BusinessEntity、工勘、设备采集、迁移页面等既有错误，以及 `ProjectSchedulePanel.vue:142` 既有可空任务数组和 `schedulePresentation.ts` 字段问题仍在；本轮新增或变更语句未报类型错误。本轮不扩大修复范围。

此增量仅调整呈现与记录入口，不推进 Feature 状态，未提交或推送。

### 2026-10-06 项目硬件安装按序列号办理与地址完成校验

按用户本轮明确要求，项目详情页签与 PAGE 绑定硬件实施任务使用新的 `ProjectInstallationWorkbench.vue`：列表读取当前直接归属项目的全部设备序列号，支持单台或跨页勾选批量创建记录，统一填写安装地址、安装人员、照片和备注。原独立安装页面和原记录创建、状态流转、位置生效及不可变历史逻辑保留。批量逐条调用既有服务端创建接口，中途失败显示剩余设备，已收到成功回执的设备不再重复提交。

页面完成提示与服务端任务完成校验共用 `InstallationAddressCompletionApi`：通过 AST 公开接口锁定全项目设备范围，通过 IMP 自有 Mapper 锁定安装记录，检查每台设备的最新未失效记录安装地址或当前设备位置快照；缺失地址、空项目和读取失败不满足条件。`/api/v1/pms/implementation/projects/{projectId}/installation-address-completion` 保留查询权限与完整项目范围授权。硬件安装 PAGE 任务完成时增加此 Owner 校验，原有提交、计划规则、Gate、状态机和审计继续生效；不直接写任务或阶段状态，也不改已冻结模板与 F-IMP-003 后续签收/位置生效设计。

- 定向后端验证：18 项地址完成与项目计划完成评估测试、2 项查询范围保护测试通过，覆盖空项目、部分缺失、完整覆盖、其他设备记录不能代替缺失设备、租户不一致、空授权/路径占位项目拒绝和地址齐备仍受原计划规则约束。
- 定向前端验证：新工作台 3 项测试、原安装工作台 2 项、项目任务页面绑定 16 项通过。覆盖第 101 台设备、筛选不改变全量判定、单台/批量创建、失败后只继续剩余设备、加载失败和已完成记录只读；分页仅改变显示，保留跨页勾选。
- 本机 `19191/59191` 真实浏览器：项目 `992203060038` 的序列号 `02011768M247000998` 新增验收安装记录，回读地址覆盖为 `1/1`；项目 `992203060151` 原有 18 台序列号，勾选 `02051131N226000002` 和 `99020564D229000001`，沿用该项目实施地点创建两条带验收备注的待开始记录，回读为 `2/18`，按单个 SN 筛选仍为 `2/18`。新建记录保留，未覆盖原历史、未确认现场安装或推进项目生命周期。见[批量及部分覆盖截图](../../docs/generated/2026-10-06-hardware-installation/batch-partial-addresses.png)。原项目的未准入硬件实施任务仍为只读。服务端新接口已在现有本机数据库验证实际 XML/记录映射；后端运行包基于原验证包，仅替换本次相关类和 XML，保留其他功能。
- 全量前端类型检查仍被 BusinessEntity、工勘、迁移、项目计划等既有错误阻断；本轮变更文件无残留类型错误。未在真实项目中执行任务完成动作，因为其实施部署阶段尚未准入；任务完成阻断与放行由后端定向测试验证。

本条仅记录用户指定的项目工作区增量，不晋级整个 Feature，未提交或推送。

## 2026-10-09 施工计划审批成果自动关联

- 按用户确认，施工计划审批通过后自动关联匹配交付件要求。补齐PLN/STAGE_PLAN/CONSTRUCTION_PLAN_APPROVED原生源及原changed日志接线，复用已有Outbox、ACC归集及统一提交Writer。
- 匹配有效冻结计划显式来源，或本次授权的PLN/STAGE_PLAN任务完成规则所引用、同阶段同任务且允许BUSINESS_RESULT的要求。没有修改冻结计划、绕过授权/审批或覆盖历史；旧批次及过期工期不能成为当前证据。
- 后端72项与真实浏览器通过：当前批准#29一次补关联，新#30实际审批后自动关联，只有新提交为当前且两版历史保留；详见[审批成果自动关联验收](../../docs/generated/2026-10-09-stage-plan-auto-result-acceptance.md)。本增量不改变Feature总体状态。

## 2026-10-09 到货签收文件自动关联修复

项目签收工作台原 UploadFile 只保存 infra 附件 URL，未接入既定 IMP.ARRIVAL_SIGN_DOCUMENT 自动归集。改为真实待签收记录下的统一文件两段式上传、原 update/sign 命令及统一引用回显；失败保留同一记录和上传会话，原签收状态与历史保护保持。详情接口增补授权文件引用视图，无新增数据库表或迁移。

npdms_domain_test、19191/59191 已部署并真实浏览器验收：原 30022/30023 同一签收文件通过统一项目材料入口补关联，原记录不变；新 30024 保存及签收后自动归集 D_ARRIVAL，重新打开可查看原生文件，项目交付件已满足且旧材料和提交历史保留。38 项后端、11 项前端测试通过。详见 [到货签收自动关联验收](../../docs/generated/2026-10-09-arrival-file-association-acceptance.md)。本增量不晋级整个 Feature，未提交或推送。

## 2026-10-09 施工计划调整理由移至提交审批

首次提交不要求调整理由；已有生效计划时，仅在提交审批弹窗必填并随本次提交保存。项目详情与独立入口同步调整，服务端判定并强制校验，原日期、输入快照、授权及审批状态机保持。25项后端、10项前端测试及真实浏览器两种提交路径通过，19191/59191已部署。详见[调整理由提交验收](../../docs/generated/2026-10-09-stage-plan-submit-reason-acceptance.md)。本增量不晋级整个Feature。

## 2026-10-09 安装记录自动归集与任务完成

按用户确认，项目完整安装记录接入IMP/INSTALLATION/INSTALLATION_COMPLETED基础成果来源；安装完成事务追加原事件和成果日志，匹配允许业务成果且由安装页任务完成规则引用的冻结交付件。部分完成不宣告项目形成，全设备当前完成记录有效才关联，原状态机与历史保护保持。

45项相关后端测试通过，19191/59191已部署，真实浏览器在992203060198回填原地址保存后，D_INSTALL自动归集为ACCEPTED，原待验收硬件实施任务自动DONE，原地址及历史回读确认保留。详见[安装成果自动完成验收](../../docs/generated/2026-10-09-installation-result-auto-completion-acceptance.md)。本增量不晋级整个Feature，未提交或推送。


## 2026-10-14 五类草稿的五项业务接入（已部署、草稿预检通过）

用户授权补齐到货签收、安装、割接审批、培训确认和S6审批后完成的接入，更新五类草稿并重新预检，暂不发布。当前工作树保留既有未提交增量，相关master活动DU未认领本次业务事实适配和正常闭环任务范围；不接管其他动态表单、需求分析正文或巡检任务。

计划：核对Owner已有命令/事实与界面；新增窄适配并保持旧业务与冻结历史；覆盖未确认、部分安装、错误项目/租户、旧版本、非真实审批、闭环循环及事务失败；通过固定工具链和工作树锁验证；准备可审查产物后按本工作树规则请求59191部署批准，再用实际运行能力更新五类未发布草稿、重新预检及真实浏览器回读。当前仅进入实现，不记Feature Done。

2026-10-14阶段结果：五项接入代码与失败保护已实现，经用户批准已部署59191；197项后端定向检查及前端相关测试通过，最终前后端构建和22项既有部署保护通过。五类草稿已更新，正式API逐字段读回一致且Compiler全部通过，模板均未发布。前端全量类型检查仍有41项未落在本次新增代码行的错误，扩展路由测试另有4项既有共用夹具问题，如实记录。已在部署地址10.210.0.11:19191用真实浏览器逐一重开五类草稿并预检通过，核对五项新规则及督导阶段路径；随后API确认草稿内容及源模板未变。未做五类项目全流程验收，未标Feature Done。见[本轮接入记录](../../docs/generated/2026-10-14-project-template-config/README.md#7-2026-10-14-五项接入补齐已部署草稿预检通过)。

2026-10-14项目 `PJT2026000061` 的窄屏任务入口及联系人保存后同步等局部前端修复已真实浏览器复核；需求分析冻结精确提示的后端部署待割接测试编译依赖修复后继续打包，本轮未重启59191，不晋级Feature状态。详见 [冻结提示与任务同步修复记录](F-SOL-003.md#2026-10-14-pjt2026000061-冻结提示与任务同步修复后端待部署)。

## 2026-10-15 五类草稿修订与按需任务（已部署，草稿未发布）

依据需求方确认的[修订清单](../../需求/项目模板配置清单-20261014.md)，补齐最终用户成员基础维护、成员完成事实、任务必选性冻结及割接前置适用范围，再更新五份既有草稿；保持未发布，不改既有冻结历史，不移除原实体办理与完成规则。最终用户为外部人员，不要求也不创建系统账号，不取得项目管理或任务办理权限。

旧实现审计结论：复用统一成员的授权、版本、幂等、有效区间与重判事件；扩展独立外部人员资料及完成谓词。复用Designer/Compiler/计划冻结及原状态机，在新版本显式保存任务必选性，缺省仍必选；按需未开始和已开始未完成均不阻断其他任务、阶段和项目闭环。复用IMP结构化就绪快照，以PROJ公开的冻结计划要求决定EXE-01～04适用性；不以文件上传伪造原实体完成事实。旧实体页面、服务和冻结规则继续有效。

实施顺序：先基础成员维护，再完成适配；补齐任务契约、运行检查与IMP前置适用性；覆盖授权、空事实、失效区间、版本冲突、必选仍阻断、按需已开始不阻断及旧快照兼容等失败场景；持工作树构建锁完成定向检查；准备冻结产物后申请59191重启，再更新草稿、正式预检和真实浏览器验收。保留其他会话改动，不提交或推送，不晋级Feature状态。

结果：基础成员、完成谓词、按需运行语义和IMP冻结适用性已实现并经用户批准部署；五草稿保存／读回／Compiler通过，任务数16／16／13／13／12，均保持DRAFT。新项目真实浏览器验证最终用户无账号维护、完成判定、历史保留，以及S1/S2按需任务已开始未完成仍进入S3。定向后端、前端及隔离库保护检查通过，保留IMP一项既有接口目录断言失败、全量TypeScript的40项既有错误；完整割接与项目终态浏览器链尚未执行。详见[本轮实施与验收记录](../../docs/generated/2026-10-15-project-template-revision/README.md)。不晋级整个Feature，未提交或推送。

## 2026-10-15 督导全流程验收阻断修复（待部署）

延续当前任务和用户授权，新增 CUTOVER_APPROVAL 项目创建办理入口，保留旧只读注册与审批完成事实；闭环入口/复核读取当前正式计划冻结规则并记录计划身份，禁止规则缺失回退与历史回写。另修复闭环树检查对字符串长 ID 的拒绝。后端57项、前端19项定向检查通过，前端构建通过，真实浏览器已确认树维度检查恢复；后端冻结构建进行中，尚未重启/变更验收项目计划/发布模板，完整终态验收仍待继续。详见本轮生成记录第8节。
后端冻结构建已通过（2分32秒），22项部署保护通过；清单 f6a01265-5dda-40dc-b62b-2926814eb7a1，待本次59191重启批准。

2026-10-16续验：项目64当前轮次割接已完成两节点审批与P6归档，1/1设备服务事实回执有效，项目正式进入并完成S5，六项按需任务仍未完成。正常闭环check暴露参数快照编译与旧阶段合同规则读取两个问题，已最小修复、25项定向测试通过；冻结构建进行中，待本次59191重启批准后继续真实闭环审批与终态验收。五份模板保持未发布，不晋级Feature；详见[本轮记录第16节](../../docs/generated/2026-10-15-project-template-revision/README.md#16-第二轮割接归档s5推进与闭环规则修复2026-10-16)。
本轮闭环规则修复冻结构建及22项部署保护已通过，清单67a3900f-457a-4274-8037-485008bfdc97，固定部署入口ValidateOnly通过；尚未部署，等待本次59191重启确认。25项定向测试通过，完整项目闭环仍待真实浏览器续验。
2026-10-16实际审批续验：67a3900f已批准部署；项目64闭环check通过并发起真实BPM，服务经理通过。材料审核最终收口因遗留SQL要求旧建项closure_policy_snapshot非空被误拒绝（项目/申请版本均11）。已修正为有效计划冻结策略来源，并用实际XML在连接级临时表通过11组MySQL正反场景及重复闭环拒绝；固定构建进行中，原审批IN_REVIEW与六项按需未完成保留，尚未取得终态。详见本轮生成记录第17节。
SQL修复两次完整编译均通过，但并行巡检源码变化导致固定入口拒绝冻结；尚无新增部署包、未再重启。已请求约4分钟稳定源码窗口，材料审核待办及ACTIVE项目原样保留，终态验收仍未完成。
继续构建已成功，冻结清单53ebaf1a-7273-4f27-a156-5dbcfecde17f，22项部署保护及ValidateOnly通过；等待此次59191重启明确批准，原材料审核待办继续保留。

2026-10-16最终续验：用户明确确认后，固定入口已部署53ebaf1a至59191；48182未启停。真实浏览器完成原材料审核并重开核对两节点APPROVE历史；项目PJT2026000064已于02:39:44正常闭环（NORMAL_CLOSED、S5、version12），正式退出记录及来源快照保留。闭环后六项按需任务仍进行中／0%，五份模板仍为未发布草稿。本次隔离项目S3至正常闭环路径验收完成，不晋级整个Feature。最终证据见[本轮生成记录第18节](../../docs/generated/2026-10-15-project-template-revision/README.md#18-项目64完整闭环验收完成2026-10-16)。

闭环后详情页补验：修复阶段工作区仍查询推进准备度导致GRAPH_NODE_STALE重复提示；仅ACTIVE项目查询，生命周期变化清空旧结果并丢弃在途响应。24项测试和项目64真实浏览器阶段切换验证通过，后端状态守卫与历史保持，详见本轮生成记录第19节。

用户补充要求现场培训与之前模板保持内嵌：IMP_TRAINING_CONFIRMATION复用原TrainingWorkbench直接呈现授权项目培训列表并保留只读状态，项目64真实浏览器及5项定向测试通过；未新增培训记录或完成按需任务，见本轮生成记录第20节。
