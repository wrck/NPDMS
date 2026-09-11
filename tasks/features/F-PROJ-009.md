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
- Project / Stage / Task / Deliverable 的实例表、状态机、Owner 业务服务和审计历史继续保留。
- Legacy 模板保持只读兼容；如果要用于新的 V2 发布/新建链，必须显式复制/升级并生成新的 V2 Snapshot。
- Rule Tree 与 Decision Table 是同一 Rule AST 的不同编辑视图，不建立第二套规则模型。

## 2026-09-12 已提交代码 Review 基线

Review 基线 HEAD：`ff87761f6ecf6b82d774f60c46108e339da8d9c8`。

相对此前界面重构提交 `d445f02c7105ac232bb959a5b0cc1de73b811c5b`，当前分支已继续前进 81 个提交，Template V2 已不是“待设计”，而是主体代码已经进入分支。因此本专项从“继续搭 V2”调整为“review 已落地 V2 → 修正结构性问题 → 收敛单一运行链”。

当前 CI 状态：HEAD 的 CircleCI `buildgroup` 仍为 `pending`；本 Task 不把当前状态表述为已绿。

### Review 已确认的结构性问题

1. **专项 Task 与代码现实漂移**：旧 Task 仍记录“修六类草稿 / 本次不改 SDS / 生产代码受既有实施链约束”，已经不符合当前 V2 重写事实和本次明确授权，本提交先纠正。
2. **Legacy 发布语义漂移风险**：`ProjectTemplateV2ServiceImpl#getExecutionSnapshot` 在历史发布版本没有持久化 V2 Snapshot 时，会调用当前 `TemplateCompiler` 即时把 Legacy 内容编译为 V2 Snapshot。Compiler 未来变化会改变历史版本运行语义，与“旧发布版不静默升级”原则冲突，列为首要代码修复。
3. **Runtime node identity 需审计**：当前 `TemplateExecutionSnapshot.stableRuntimeNodeId(nodeKey)` 仅以 `nodeKey` 计算 60-bit Long；需确认该值是否被当作跨模板全局定义 ID 使用。如果存在全局语义，将改为命名空间化身份；如果只在 Snapshot / Project 范围使用，则保留并补充约束测试，避免无收益改动。
4. **V2 Runtime 仍需收敛验证**：需要继续核对项目新建、RuntimeGraphFreezer、TaskExecutionContractFactory 等路径，确保 V2 新链不再回查 `TemplateDefinitionContent / DefinitionSnapshot / DefinitionRevision` 重新解释已经发布的 Snapshot。

## 已完成并保留的 V2 能力

以下能力已在当前分支落地，本专项不重复造轮子，而是作为后续收敛基础：

- [x] `TemplateDesignerDocument`：Stage / Task / Milestone / Edge / Rule / Binding / Permission / Deliverable / Closure 的设计态模型。
- [x] `TemplateCompiler`：Designer 到不可变 Execution Snapshot 的编译、校验、稳定排序和语义哈希基础。
- [x] `TemplateExecutionSnapshot`：V2 运行快照模型及基础确定性约束。
- [x] V2 草稿 / 发布 Service 与 API 主链。
- [x] BusinessView / DynamicForm 等发布依赖的精确版本校验和快照固定基础。
- [x] V228 / V229 等 V2 相关前向迁移基础。
- [x] 模板统一设计器工作区：阶段与任务、流程画布、规则与决策、高级配置。
- [x] `StageGraphDesigner`：显式关系图编辑，不从阶段顺序推导边。
- [x] `RuleDecisionDesigner` + `ruleDecisionModel`：规则树 / 决策表共享同一 AST，复杂嵌套拒绝有损转换。
- [x] RuntimeGraph freezer / resolver 的 V2 演进基础。
- [x] 新建项目链已大幅转向 V2 Snapshot / RuntimeGraph。
- [x] TASK_NATIVE / Owner completion 的分流与原生完成能力已进入当前分支。
- [x] S0 / pre-project 与 primary assignment 等当前分支后续改造已合入，不在本专项重复实现。
- [x] 2026-09-09 六类草稿纠偏、S0 零任务、操作型重复任务移除、真实 PAGE / DYNAMIC_FORM 绑定保存等历史成果继续保留；它们不等于 V2 Runtime 已闭环。

## 本轮剩余专项步骤

按“每完成一步就提交一次”执行：

- [x] **Step 0 — Review 当前已提交代码**：确认当前 V2 实际落地范围、CI 状态和结构性风险。
- [x] **Step 1 — 同步专项 Task**：更新本文件的专项计划、任务逻辑、Review 结论和已完成内容；本步单独提交。
- [ ] **Step 2 — 修复 Legacy Runtime 边界**：移除历史发布版本被当前 Compiler 静默重编译的路径；V2 runtime 要求持久化 V2 Snapshot，Legacy 进入 V2 必须显式复制/升级；补回归测试；单独提交。
- [ ] **Step 3 — 审计并收敛 Runtime Node Identity**：核查 `TaskDef.id / templateTaskDefinitionId / stage identity` 的实际持久化和查询语义；只有存在跨模板全局冲突风险时才修改 ID 算法，否则补约束测试和命名说明；单独提交。
- [ ] **Step 4 — 收敛 Project Creation / Runtime Freeze**：V2 新建项目路径只消费 `TemplateExecutionSnapshot`；去掉 V2 路径中残留的 DefinitionSnapshot / TemplateDefinitionContent 二次解释；根据独立逻辑拆成多个小提交。
- [ ] **Step 5 — 清理失去作用的 Legacy Bridge**：仅删除已经无调用或与 V2 重复的转换层，保留明确历史读取边界；每个独立清理单独提交。
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
- Designer / Compiler / Snapshot 的 schema / compiler version 和 semantic hash 能支撑发布后确定性验证。
- 当前分支相关测试和构建由本轮实际执行并记录；CI 未绿时不得宣称合入 Gate 已通过。
- 代码收敛后完成一次正式规格落库，规格描述与最终实现一致。

## 历史成果摘要（保留，不作为本轮运行时闭环证据）

2026-09-09 已完成六类未发布草稿的业务任务纠偏：任务从 115 收敛为 65（14/13/13/12/11/2），S0 零任务，移除操作型/V2 重复任务、人工里程碑和默认逐任务必传文件；保留必要可选文件槽。模板页面已支持阶段导航、任务侧栏、真实已发布 PAGE / DYNAMIC_FORM 绑定及失败后保留编辑，历史已发布定义和项目实例未改写。

固定测试库数据补全只处理有明确模板来源的 ACTIVE 项目，不对无来源项目猜测模板、不从 sortOrder 补边；早期候选和 master 选择性集成记录继续作为历史证据，但不替代本专项对 V2 Runtime 的重新验证。

Feature 继续保持 `IN_PROGRESS`，Implementation Done Gate 继续保持 `NOT_READY`，直到上述代码收敛、验证和最终规格落库完成。
