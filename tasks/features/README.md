# Feature Task追溯矩阵

本矩阵是`master`当前Feature任务记录、Git分支/Worktree和分支候选的审计投影，不是独立状态源。Feature Ready以`specs/features/F-*.md`为权威；实施状态、工作模式、Feature/Task工作单元认领与交接只以`master`中的当前`tasks/features/F-*.md`为权威。分支中的同名任务状态、测试和提交只作候选证据，不能直接产生Implementation Done或Requirement完成。

审计快照：2026-09-01 15:29:34 +08:00；master：`3a93fc789e767d435a5fb1764792c81ea43d22cd`。快照之后继续前进的分支必须在下一次master协调提交中重新核对，不得覆盖本快照的历史判断。

## master权威任务矩阵

| Feature | master当前任务记录 | master实施判定 | 有效认领 | Requirement投影 |
|---|---|---|---|---|
| F-PROJ-001 | [Task](F-PROJ-001.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-PROJ-002 | [Task](F-PROJ-002.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-PROJ-003 | [Task](F-PROJ-003.md) | IMPLEMENTATION_DONE | 无活动认领 | 由生成矩阵派生 |
| F-PROJ-004 | [Task](F-PROJ-004.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-PROJ-005 | [Task](F-PROJ-005.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-PROJ-006 | [Task](F-PROJ-006.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-PROJ-007 | [Task](F-PROJ-007.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-SOL-001 | [Task](F-SOL-001.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-SOL-002 | [Task](F-SOL-002.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-SOL-003 | [Task](F-SOL-003.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | PRE-04为PARTIAL覆盖，不等于Requirement完整完成 |
| F-PLT-001 | [Task](F-PLT-001.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-PLT-002 | [Task](F-PLT-002.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-CUS-001 | [Task](F-CUS-001.md) | IMPLEMENTATION_COMPLETE | 无活动认领 | 由生成矩阵派生 |
| F-AST-001 | 缺失 | NO_TASK；历史实现证据不派生Done | 无活动认领 | EQP-01保持NOT_STARTED |
| F-CUT-001 | [Task](F-CUT-001.md) | TECHNICAL_PLAN_READY；分支完成候选待集成 | 无有效活动认领 | CUT-07/09/10保持NOT_STARTED |

结论：快照时master没有任何按当前工程链完整登记的活动Feature/Task排他认领。所有正在写入的Feature Worktree都早于`3a93fc78`中的认领规则，必须先由协调者在master补齐工作模式、工作单元、执行者、分支/Worktree、认领基线、修改边界、依赖、共享资源和测试，再更新分支到该master提交；不能用本矩阵倒签认领。

## 分支Feature候选与认领判定

| Feature | 分支证据 | 分支自报/观察状态 | master判定 | 下一动作 |
|---|---|---|---|---|
| F-COM-001 | `codex/f-com-001-feature-ready@21423d9c17e1`、`codex/f-acc-001-sds@58576666af68` | 分支自报Done，完成证据`563daac1` | 完成候选；master无Spec/Task，不产生Done | 按依赖核对后串行集成正式规格、任务、代码与验证证据 |
| F-ACC-001 | `codex/f-acc-001-sds@58576666af68` | 分支自报Done，完成证据`ad5b401f` | 完成候选；master无Spec/Task，不产生Done | 串行集成并在master最终状态复验 |
| F-ACC-002 | `codex/f-acc-001-sds@58576666af68` | 分支自报Done，完成证据`8ed75093` | 完成候选；master无Spec/Task，不产生Done | 串行集成并在master最终状态复验 |
| F-AST-002 | `feat-inspection-feature-xkjuCC@ec80c924096f` | 分支自报Done，完成证据`68bc56ec` | 完成候选；master无Spec/Task，不产生Done | 从同分支的F-INS-001未提交改动中拆清后串行集成 |
| F-CUT-001 | `codex/integrate-f-cut-001@72ccb83f8052` | 分支自报Done，Worktree干净 | 完成候选；master仍TECHNICAL_PLAN_READY | 更新到最新master后串行集成并执行完整DoD |
| F-CUT-002 | `codex/f-cut-001-matrices@f4ecbfdd7236` | 分支任务写IN_PROGRESS | 未登记认领；当前分支近期工作不属于本Feature | 先明确继续、交接或释放，不得沿分支状态推进master |
| F-CUT-003 | `codex/f-cut-001-matrices@f4ecbfdd7236` | 分支任务写IN_PROGRESS | 未登记认领；当前分支近期工作不属于本Feature | 先明确继续、交接或释放，不得沿分支状态推进master |
| F-CUT-004 | `codex/f-cut-001-matrices@f4ecbfdd7236` | Task 1～11通过；受控替身闭环；Done未开始 | 当前实际活动Feature，但未登记认领；生产Owner依赖与`Q-FCUT004-001`阻断Done | 暂停扩大写入，先在master登记工作单元后更新分支；受控替身不能产生Done |
| F-IMP-001 | `codex/f-cut-001-matrices@f4ecbfdd7236` | 分支任务写NOT_STARTED/NO-GO | 无认领、未开始 | 不实施，等待Feature Ready |
| F-IMP-002 | `codex/f-cut-001-matrices@f4ecbfdd7236` | 分支任务写IN_PROGRESS/NOT_READY | 未登记认领，且无近期独占活动证据 | 明确交接或释放后再认领 |
| F-IMP-003 | `codex/f-cut-001-matrices@f4ecbfdd7236` | 分支任务写NOT_STARTED/NOT_READY | 无认领、未开始 | 不实施，等待Feature Ready |
| F-IMP-004 | `codex/f-cut-001-matrices@f4ecbfdd7236` | 分支任务写NOT_STARTED/NOT_READY | 无认领、未开始 | 不实施，等待Feature Ready |
| F-IMP-005 | `codex/f-cut-001-matrices@f4ecbfdd7236` | 分支任务写NOT_STARTED/NOT_READY | 无认领、未开始 | 不实施，等待Feature Ready |
| F-PROJ-008 | `codex/f-proj-008-stage-advance@48175aa0e818` | 分支任务写IN_PROGRESS；Task 3受`Q-FPROJ009`阻断 | 未登记认领；同Worktree混有未提交F-SOL-003实现，修改边界冲突 | 先停止混写并拆清F-SOL-003，再在master登记唯一工作单元 |
| F-INS-001 | `feat-inspection-feature-xkjuCC@ec80c924096f`及未提交inspectionrule实现 | 分支任务写IMPLEMENTATION_IN_PROGRESS | 当前实际活动Feature，但未登记认领；与同分支F-AST-002候选未拆清 | 先提交/交接并拆清候选，再在master登记工作单元后继续 |
| F-INT-012 | `prereq-parallel-check-kKiAdn@cdfbd71a1722` | 有Feature Spec和实现提交，但无Feature任务记录 | NO_TASK；无认领、无Done | 先完成权威任务记录和Ready/Done链路审查，不得从提交推断完成 |

`specs/features/F-CUT-005-approval-owner-contract.json`只是F-CUT-004引用的跨Feature合同候选；快照中不存在F-CUT-005 Feature Spec或Task，因此不把它列为已认领Feature。

## 全部分支审计

| 分支 | 快照HEAD | Worktree/活动性 | Feature认领与完成结论 |
|---|---|---|---|
| `master` | `3a93fc789e76` | 本次干净审计Worktree | 唯一协调、状态与集成权威 |
| `codex/f-cut-001-matrices` | `f4ecbfdd7236` | 活动；仅`.run` PID删除 | 实际推进F-CUT-004；F-CUT-001为继承完成候选，F-CUT-002/003与F-IMP-002为未登记的旧IN_PROGRESS文本，不构成并行认领 |
| `codex/f-proj-008-stage-advance` | `48175aa0e818` | 活动且脏；混有F-SOL-003实现 | F-PROJ-008未登记认领且边界冲突；不得继续混写或声明Done |
| `feat-inspection-feature-xkjuCC` | `ec80c924096f` | 活动且脏；F-INS-001实现 | F-AST-002为完成候选；F-INS-001是未登记活动Feature，二者必须拆清 |
| `codex/f-acc-001-sds` | `58576666af68` | 有Worktree且脏；治理文件和浏览器输出 | F-COM-001、F-ACC-001、F-ACC-002为完成候选；无当前实现认领 |
| `codex/integrate-f-cut-001` | `72ccb83f8052` | 干净Worktree | F-CUT-001完成候选待master集成；不是长期集成分支 |
| `codex/f-com-001-feature-ready` | `21423d9c17e1` | 无Worktree | F-COM-001完成候选；已被ACC分支包含，不构成新认领 |
| `prereq-parallel-check-kKiAdn` | `cdfbd71a1722` | 脏；子仓与未跟踪平台目录 | F-INT-012有规格/实现但无Task；不构成认领或完成 |
| `codex/f-proj-001-atomic-alignment` | `8bbaf69ae125` | 脏且远落后master | F-PROJ-001已在master Done；该分支不得作为旧Feature继续实施基础 |
| `codex/merge-engineering-chain-phase-tmrsp0` | `2911183338b6` | 无Worktree且远落后 | 历史合并分支；其Feature状态已被master后续事实替代，无认领 |
| `codex/v1-8-feature-revalidation-50eb` | `68db25b3c6bd` | 仅未跟踪临时目录 | 规格重验证历史分支，无当前Feature认领 |
| `prd-audit-v1-8-LAR2Ap` | `48156a8a5d6b` | 脏；报告修改 | PRD审计历史分支，无当前Feature认领；其旧F-SOL-003提交不覆盖master Done事实 |
| `engineering-chain-phase-TmrsP0` | `abbc3fa0b5b2` | 干净、远落后 | 历史工程链分支，无Feature认领 |
| `feat-inspection-feature-Q7yA35` | `08457e39d3f2` | 干净、旧基线 | 检查Feature准备基线，已被活动检查分支继承；无认领 |
| `feat-parallel-features-akPsDH` | `4060039ce486` | 干净、远落后 | 历史并行开发分支，无当前认领 |
| `chore/merge-spec-revision-005` | `2dc8063ba987` | 无Worktree、已被master包含 | 历史分支，无认领 |
| `chore/single-repository-governance` | `98ef4a41a005` | 无Worktree、已被master包含 | 历史治理分支，无认领 |
| `feat/feature-01` | `28d44fe50e45` | 无Worktree、远落后 | 历史归档分支，无认领 |
| `feat/specification-baseline-sync` | `91ba833a88b7` | 无Worktree、远落后 | 历史规格同步分支，无认领 |
| `import/spec-prd-v1.8-revision-005` | `aaed378a9e35` | 无Worktree、远落后 | 历史PRD导入分支，无认领 |

## 收口规则

- 分支完成候选进入master前保持候选状态；不得更新Requirement覆盖，不得替换现行旧功能。
- 旧分支包含后来已Done Feature的提交时，视为历史继承，不是重新认领；新工作不得继续建立在该旧Feature分支上。
- 活动Worktree存在未提交跨Feature修改时，认领判定为冲突；先拆清提交与修改边界，再登记或交接。
- `docs/traceability/requirement-matrix.md`只从master Feature Spec覆盖声明和master当前任务状态生成。本次没有把任何分支候选提升为Done，因此Requirement覆盖值不因本次审计改变。
