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
