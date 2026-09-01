# master Feature任务追溯矩阵

本矩阵只投影`master`当前Feature Task状态。Feature Ready以Feature Spec为权威，Implementation状态与Done以对应`tasks/features/F-*.md`为权威；认领、写边界、Worktree交接和集成回执见[`tasks/delivery-units/`](../delivery-units/README.md)。Git分支、提交、测试和浏览器结果只作候选证据。

首次冻结审计输入为`master@e4b7c863b202320eed9c012c16a4a56e0e3ffe49`、截点`2026-09-01T16:59:30+08:00`，见[原始时间线](../../docs/generated/branch-history-audit-2026-09-01.md)。本轮增量审计输入为`master@133f7b8132e0f9f496ba4dbe79a4cce43a04019e`、截点`2026-09-01T17:59:19+08:00`，见[合入后完整时间线](../../docs/generated/branch-history-audit-2026-09-01-post-governance.md)；CUT仍停在`85b93828`，INS新增未认领提交`6719ab94`。后续分支前进必须继续增量复审，不覆盖任一冻结快照。

## master权威Feature状态

| Feature | Feature Task | master实施状态 | 当前有效DU | Requirement投影 |
|---|---|---|---|---|
| F-PROJ-001 | [Task](F-PROJ-001.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-PROJ-002 | [Task](F-PROJ-002.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-PROJ-003 | [Task](F-PROJ-003.md) | IMPLEMENTATION_DONE | 无 | 由生成矩阵派生 |
| F-PROJ-004 | [Task](F-PROJ-004.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-PROJ-005 | [Task](F-PROJ-005.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-PROJ-006 | [Task](F-PROJ-006.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-PROJ-007 | [Task](F-PROJ-007.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-SOL-001 | [Task](F-SOL-001.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-SOL-002 | [Task](F-SOL-002.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-SOL-003 | [Task](F-SOL-003.md) | IMPLEMENTATION_COMPLETE | [废弃标记DU](../delivery-units/DU-20260901-FSOL003-DEPRECATION.md)已集成 | PRE-04保持PARTIAL覆盖 |
| F-PLT-001 | [Task](F-PLT-001.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-PLT-002 | [Task](F-PLT-002.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-CUS-001 | [Task](F-CUS-001.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-AST-001 | [Task](F-AST-001.md) | REVALIDATION_REQUIRED | 无；待建复核DU | EQP-01不派生完成 |
| F-CUT-001 | [Task](F-CUT-001.md) | IN_PROGRESS | [DU-20260901-FCUT001-INTEGRATION](../delivery-units/DU-20260901-FCUT001-INTEGRATION.md)已部分集成并释放边界 | V133示例迁移与master最终运行DoD未完成；不得声明Done |

当前没有有效写入认领；`DU-20260901-FCUT001-INTEGRATION`已完成可构建增量的选择性集成并释放边界，`DU-20260901-FSOL003-DEPRECATION`已完整集成。其他历史活动分支均未被倒签为有效认领。

## 分支Feature候选裁决

| Feature/工作包 | 提交或分支证据 | 更正后的master裁决 | 下一动作 |
|---|---|---|---|
| COM-A、ACC-001、ACC-002 | `codex/f-acc-001-sds@58576666`；完成证据`563daac1/ad5b401f/8ed75093` | 顺序完成候选，但与COM-B竞争且PRD Change ID冲突 | 关闭`Q-GOV-20260901-001/002`后逐项选择提交，禁止整支合入 |
| COM-B | CUT/PROJ共享线，自`c21745a9`开始 | `CONFLICTED_IMPLEMENTATION / IN_PROGRESS` | 与COM-A按PRD、Spec、公共契约逐项裁决 |
| F-AST-002 | `a52b22b4..68bc56ec` | 独立完成候选；不是F-INS-001脏改动的一部分 | PRD冲突关闭后更新master并复验 |
| F-INS-001 | `feat-inspection-feature-xkjuCC@6719ab94`及7项未提交Task 4实现；新增提交复用冲突的修订011 | `QUARANTINED / IN_PROGRESS`；分支Ready/Task 4B解阻不进入master | 保留工作树；先关闭`Q-GOV-20260901-001`，再从master建立有效DU和锁定输入 |
| F-CUT-001 | `08457e39..72ccb83f` -> `07b6eb06` -> `master@c61e5b1e` | `INTEGRATED_PARTIAL`；Feature权威状态仍为`IN_PROGRESS / MASTER_REVALIDATION` | 新建DU完成V133示例迁移与合入后独立MySQL/真实浏览器最终DoD |
| F-CUT-002/003 | `codex/f-cut-001-matrices@85b93828`继承文本与实现 | `QUARANTINED / IN_PROGRESS` | 从多Feature分支拆分DU，不按分支头推断Owner |
| F-CUT-004 | 同上 | `IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`，生产依赖未满足 | 不得声明Done或激活完整生产入口 |
| F-CUT-005 | `2e3fdba3` Ready；`912d0cdb` Plan GO；Task 1/2候选至`85b93828` | `QUARANTINED / IN_PROGRESS / Task 3` | Task 1/2按提交边界拆出集成DU；Task 3继续写入前必须先形成有效认领，不得借CUT整支历史认领 |
| F-IMP-001 | CUT共享线API/DTO/测试提交 | Ready NO-GO、Task NOT_STARTED下发生实施 | 隔离候选，Ready前代码不得作为正常认领或Done |
| F-IMP-002 | CUT共享线 | `QUARANTINED / IN_PROGRESS` | 拆分DU并确认依赖、边界和交接 |
| F-PROJ-008 | `codex/f-proj-008-stage-advance@48175aa0` | `QUARANTINED / IN_PROGRESS`；Task 3阻塞 | 分离F-SOL-003脏改动，PRD汇总冲突关闭后再认领 |
| F-INT-012 | `84258059..cdfbd71a` | `NO_TASK / UNCLAIMED_IMPLEMENTATION` | 补Feature Task、Ready和Owner边界前禁止合入 |

## 全部分支分类

- 已完成本轮选择性集成：`codex/f-cut-001-master-integration`为`PATCH_EQUIVALENT / INTEGRATED_PARTIAL`，`codex/f-sol-003-legacy-deprecation`为`PATCH_EQUIVALENT / INTEGRATED_COMPLETE`。
- 历史来源候选：`codex/integrate-f-cut-001`；其适配范围已进入master，不能再作为当前状态源或新实施基础。
- 活动但隔离：`codex/f-cut-001-matrices`、`codex/f-proj-008-stage-advance`、`feat-inspection-feature-xkjuCC`、`codex/f-acc-001-sds`、`prereq-parallel-check-kKiAdn`。
- 完成候选祖先：`codex/f-com-001-feature-ready`、F-AST-002提交段。
- 已被master包含或补丁/树等价：`engineering-chain-phase-TmrsP0`、`feat-inspection-feature-Q7yA35`、`feat-parallel-features-akPsDH`、`codex/merge-engineering-chain-phase-tmrsp0`、`codex/v1-8-feature-revalidation-50eb`及已包含的chore/import/spec历史分支。
- 已替代实施基础：`codex/f-proj-001-atomic-alignment`。其旧任务链不完整，不得再承接F-PROJ-001新实施。
- 报告或审计历史：`prd-audit-v1-8-LAR2Ap`，不构成Feature认领。

精确HEAD、ahead/behind、DAG关系、Worktree脏项、stash和所有master外提交以固定时间线报告为准，不能用本段摘要覆盖。

## 严重漂移与阻断

- `Q-GOV-20260901-001`：并行PRD分别重复使用修订`010`与`011`表达不同业务语义；PROJ与INS两份revision-011互相遗漏对方及ACC/AST输入，均不得成为master锁定基线。
- `Q-GOV-20260901-002`：F-COM-001存在两套大范围、低重叠实现；不得按提交时间或分支Done整体选择。
- CUT活动分支同时承载COM、IMP和CUT多个未收口Feature，且在前序未交接时继续启动后续Feature，是当前最严重的任务飘移。
- Worktree脏改动和stash均未被提升为提交证据；原内容保持不变。

## 投影规则

- `tasks/delivery-units/DU-*.md`决定认领，不从分支内Task、提交标题或继承关系推断。
- 分支候选进入master前不改变Feature Done或Requirement覆盖。
- `INTEGRATED_PARTIAL`只表示master已接收可构建增量；Feature仍保持`IN_PROGRESS`。
- 旧功能替代与禁用以`tasks/implementation-baseline-inventory.json#legacyCutovers`为唯一结构化记录；废弃路径不得承接新Feature。
