# master Feature任务追溯矩阵

本矩阵只投影`master`当前Feature Task状态。Feature Ready以Feature Spec为权威，Implementation状态与Done以对应`tasks/features/F-*.md`为权威；认领、写边界、Worktree交接和集成回执见[`tasks/delivery-units/`](../delivery-units/README.md)。Git分支、提交、测试和浏览器结果只作候选证据。

首次冻结审计输入为`master@e4b7c863b202320eed9c012c16a4a56e0e3ffe49`、截点`2026-09-01T16:59:30+08:00`，见[原始时间线](../../docs/generated/branch-history-audit-2026-09-01.md)。上一轮增量审计输入为`master@133f7b8132e0f9f496ba4dbe79a4cce43a04019e`、截点`2026-09-01T17:59:19+08:00`，见[合入后完整时间线](../../docs/generated/branch-history-audit-2026-09-01-post-governance.md)。本轮F-INS增量审计输入为`master@c33c7eb9d69eda365dd19ea1d5b8a25816b77850`、截点`2026-09-01T20:07:30+08:00`，见[F-INS Task 4后时间线](../../docs/generated/branch-history-audit-2026-09-01-fins-task4.md)；INS已从`6719ab94 + 7项未提交Task 4`前进为`e13feca7 + 4项未提交Task 5`。后续分支前进必须继续增量复审，不覆盖任一冻结快照。

本轮PROJ权威审计输入为`master@158118d0a085d0ee1dc08c7c74fe10936537752c`、截点`2026-09-01T22:05:15+08:00`，见[PROJ选择性集成后完整时间线](../../docs/generated/branch-history-audit-2026-09-01-proj-integration.md)。本轮同时裁决`codex/v1-8-feature-revalidation-50eb`、`codex/f-proj-001-atomic-alignment`与`codex/f-proj-008-stage-advance`，不得再以“仅审查008”的局部结论覆盖该快照。

本轮CUT内PROJ Owner选择性集成审计输入为`master@2dd62f1d0f81492b77a5ea0f27a41aaff13e7886`、截点`2026-09-01T22:41:49+08:00`，见[CUT-PROJ集成后全部分支时间线](../../docs/generated/branch-history-audit-2026-09-01-cut-proj-integration.md)。报告覆盖22个本地分支、全部Worktree、470条master外提交与2个stash；`codex/f-cut-001-matrices@85b93828`仍按整支隔离，只承认下表四组PROJ物理Owner carve-out已进入master。

本轮PRE/SOL权威审计输入为`master@a57fa1bf2817a5071f38c6a94b9a6ffdeee2d4ef`、截点`2026-09-01T23:26:08.0719898+08:00`，见[PRE/SOL集成后全部分支时间线](../../docs/generated/branch-history-audit-2026-09-01-pre-sol-integration.md)。报告覆盖22个本地分支、16个Worktree、470条master外分支提交与2个stash；裁决确认不存在master缺失的PRE/SOL物理Owner增量。快照后，`codex/f-sol-003-legacy-deprecation@3e27f047`已通过真实双亲merge进入`master@c1bbae90`；树等价历史、ACC专用适配和临时副本仍不得制造重复合并。

本轮已集成任务代码收口输入为`master@dc55b92af86c95b518195accb365487485f7c3ba`、截点`2026-09-02T00:09:35.6762162+08:00`，见[代码收口后全部分支时间线](../../docs/generated/branch-history-audit-2026-09-02-integrated-code-convergence.md)。报告覆盖22个本地分支、16个Worktree、468条master外提交与2个stash；七个已集成master回执均为主干祖先，116个去重后的实现、测试、迁移和校验路径当前全部存在。`codex/f-cut-001-master-integration@07b6eb06`已通过标准双亲merge进入`master@dc55b92a`；PROJ/CUT多Feature分支仍只承认选择性master代码回执，不因源提交未成为祖先而整支合并。

本轮2026-08-21起非排除分支审计输入为`master@dff24f780f531a712c92c7da1b687f980b475824`、截点`2026-09-02T09:59:14.3053640+08:00`，见[2026-08-21起分支Feature收口时间线](../../docs/generated/branch-history-audit-2026-09-02-post-20260821-feature-convergence.md)。时间范围包含2026-08-21当日，明确排除`codex/f-cut-001-matrices`与`feat-inspection-feature-xkjuCC`的Feature/代码合并裁决，但完整报告仍冻结二者HEAD以防通过继承关系绕过排除。报告共记录22个分支、16个Worktree、567条master外提交和2个stash；19个非排除、非master分支中10个已是master祖先，9个仍显示分支外历史。去除master及两个排除分支已包含的提交后共有179条去重候选提交，最终`MERGE_APPROVED=0`，不制造空merge或重复代码。

### 2026-08-21起非排除分支逐项裁决

| 分支 | 截点HEAD | Feature/Task事实 | 时间范围内差量 | master裁决 |
|---|---|---|---|---|
| `chore/merge-spec-revision-005` | `2dc8063b` | PRD修订005整理 | `IN_MASTER` | 已在master，不重复合并 |
| `chore/single-repository-governance` | `98ef4a41` | 单仓治理计划证据 | `IN_MASTER` | 已在master，不重复合并 |
| `codex/f-acc-001-sds` | `58576666` | 分支自报F-COM-001、F-ACC-001、F-ACC-002完成；master无三者当前Feature Task | 142条提交、560个路径，其中478个代码/测试路径；F-COM-001的62条提交为其祖先 | `BLOCKED_BY_SPEC`：`Q-GOV-20260901-001/002`仍开放；ACC依赖的COM-A地点Owner与当前PRD COM-01的AST结构化地点权威冲突，禁止整支或按Done倒签 |
| `codex/f-com-001-feature-ready` | `21423d9c` | COM-A实现候选；分支Task自报Done，master仅有另一条PROJ资格支撑合同 | 62条提交、215个路径，其中162个代码/测试路径 | `BLOCKED_BY_SPEC`：与COM-B竞争且当前PRD地点Owner不一致；关闭`Q-GOV-20260901-002`前不接收代码、Spec或Task状态 |
| `codex/f-cut-001-master-integration` | `07b6eb06` | F-CUT-001矩阵增量来源 | `IN_MASTER@dc55b92a` | 已形成标准双亲merge；Feature保持`IN_PROGRESS` |
| `codex/f-proj-001-atomic-alignment` | `8bbaf69a` | 2026-08-21旧F-PROJ-001 Task只完成中段，Task 0/6～10及全部AC未闭合 | 6条提交、170个路径，其中90个代码/测试路径 | `SUPERSEDED / DO_NOT_MERGE`：master当前F-PROJ-001已按后续V1.8 Task 0～9、AC 1～10完成，旧V60/Yudao基础改造不得覆盖当前实现 |
| `codex/f-proj-008-stage-advance` | `48175aa0` | F-PROJ-008 Task 1/2已接收，Task 3 UI未接收 | 原始203条master外提交；剔除排除分支继承后24条PROJ提交、106个路径，其中65个代码/测试路径 | `INTEGRATED_PARTIAL`：Task 1/2由`db876b43`、`158118d0`适配承载；Task 3继续受`Q-FPROJ-009`阻断，禁止迁入`a3bd0043`或整支合并 |
| `codex/f-sol-003-legacy-deprecation` | `3e27f047` | F-SOL-003固定章节废弃整改 | `IN_MASTER@c1bbae90` | 已形成标准双亲merge；旧固定章节继续只读废弃 |
| `codex/integrate-f-cut-001` | `72ccb83f` | F-CUT-001原始候选来源 | 9条提交全部也在已排除CUT共享线；适配代码已由`07b6eb06`进入master | `ALREADY_RECEIVED`：不得通过该历史分支绕过排除边界或重复合并 |
| `codex/merge-engineering-chain-phase-tmrsp0` | `29111833` | 历史工程链合并线 | 1条merge提交；tree `9e76104c`与master历史`00db759c`完全相同 | `TREE_EQUIVALENT`：无master缺失树差量，不合并 |
| `codex/v1-8-feature-revalidation-50eb` | `68db25b3` | V1.8独立裁决规则 | 1条文档提交 | `PATCH_EQUIVALENT`：已由master`29e9a415`等价接收并继续修订，不合并 |
| `engineering-chain-phase-TmrsP0` | `abbc3fa0` | 旧工程链实施历史 | `IN_MASTER` | 已在master，不重复合并 |
| `feat-inspection-feature-Q7yA35` | `08457e39` | F-CUT-001早期规格/计划基线，不是本轮排除的INS活动分支 | `IN_MASTER` | 已在master，不重复合并 |
| `feat-parallel-features-akPsDH` | `4060039c` | 历史并行Feature实现 | `IN_MASTER` | 已在master，不重复合并 |
| `feat/feature-01` | `28d44fe5` | 工程执行资料归档 | `IN_MASTER` | 已在master，不重复合并 |
| `feat/specification-baseline-sync` | `91ba833a` | 规格基线同步 | `IN_MASTER` | 已在master，不重复合并 |
| `import/spec-prd-v1.8-revision-005` | `aaed378a` | PRD V1.8修订005导入 | `IN_MASTER` | 已在master，不重复合并 |
| `prd-audit-v1-8-LAR2Ap` | `48156a8a` | 旧PRD审核报告与整理稿 | 1条提交、3个报告文件，无代码 | `REPORT_ONLY / SUPERSEDED`：不能覆盖当前修订001～007合并基线，且旧`docs/reports`不符合当前生成报告目录规则 |
| `prereq-parallel-check-kKiAdn` | `cdfbd71a` | F-INT-012 Device Ops/凭证/回调候选；无master Feature Task、无有效认领 | 4条提交、112个路径，其中91个代码/测试路径；前两批实现早于Feature Spec提交 | `QUARANTINED / NO_TASK`：不得倒签认领；候选还直接改动Yudao Infra文件基础能力，须先由master建立Feature Task、Owner边界和新DU后重新实施/迁移裁决 |

非排除Worktree在截点仍有用户脏改动：F-COM/ACC 2项、旧F-PROJ-001 1项、F-PROJ-008 24项、F-INT-012 2项、PRD审计2项、50eb临时目录1项。它们不属于提交证据，本轮未修改、清理、暂存或迁移。

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
| F-PROJ-008 | [Task](F-PROJ-008.md) | IN_PROGRESS；Task 1-2已集成，Implementation Done仍NOT_STARTED | [DU-20260901-FPROJ008-MIGRATION](../delivery-units/DU-20260901-FPROJ008-MIGRATION.md)已部分集成并释放边界 | PM-03@V1保持PARTIAL；Task 3受Q-FPROJ-009阻断 |
| F-SOL-001 | [Task](F-SOL-001.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-SOL-002 | [Task](F-SOL-002.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-SOL-003 | [Task](F-SOL-003.md) | IMPLEMENTATION_COMPLETE | [废弃标记DU](../delivery-units/DU-20260901-FSOL003-DEPRECATION.md)与[代码分支合入DU](../delivery-units/DU-20260901-PRE-SOL-CODE-BRANCH-MERGE.md)已集成 | PRE-04保持PARTIAL覆盖 |
| F-PLT-001 | [Task](F-PLT-001.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-PLT-002 | [Task](F-PLT-002.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-CUS-001 | [Task](F-CUS-001.md) | IMPLEMENTATION_COMPLETE | 无 | 由生成矩阵派生 |
| F-AST-001 | [Task](F-AST-001.md) | REVALIDATION_REQUIRED | 无；待建复核DU | EQP-01不派生完成 |
| F-CUT-001 | [Task](F-CUT-001.md) | IN_PROGRESS | [DU-20260901-FCUT001-INTEGRATION](../delivery-units/DU-20260901-FCUT001-INTEGRATION.md)已部分集成并释放边界 | V133示例迁移与master最终运行DoD未完成；不得声明Done |

### master已集成Task代码收口回执

| 已集成单元 | 来源实现 | master代码回执 | 当前代码事实 | 分支合并裁决 |
|---|---|---|---|---|
| F-CUT-001矩阵增量 | `07b6eb06` | `c61e5b1e` | 23个Java/SQL/Python/TypeScript/Vue实现与测试路径当前存在；来源与回执稳定patch-id相同 | 隔离代码分支已通过`master@dc55b92a`真实双亲merge进入主干；Feature仍为`IN_PROGRESS` |
| F-PROJ-008 Task 1 | `0c7a9634` | `db876b43` | 30个Java/XML/POM实现与测试路径当前存在；master适配补强PROCESS正向谓词与未知Provider失败关闭 | 源分支继承其他Feature历史，禁止整支merge；以master适配回执为代码集成事实 |
| F-PROJ-008 Task 2 | `d69b3ff8` | `158118d0` | 30个Java/XML实现与测试路径当前存在；master适配补强REST前缀、幂等、相邻阶段和失败关闭 | 源分支Task 3及继承历史未批准，禁止整支merge；Task 1/2代码已真实位于master |
| F-SOL-003固定章节废弃整改 | `3e27f047` | `2bdbb04c` | 20个Java/Python/TypeScript/Vue废弃标记和防回流路径当前存在 | 已通过`master@c1bbae90`真实双亲merge；旧固定章节继续`DEPRECATED_READ_ONLY` |
| `T-FIMP002-PROJ-01`系统资格支撑 | `b4f16bdf`、`f4aa1ad2` | `5f5148a9` | 7个公共API、生产Provider及单元/契约/MySQL测试路径当前存在 | 仅该PROJ支撑代码已集成，不接收IMP消费实现 |
| `T-FCOM001-PROJ-01`交付范围资格支撑 | `9d029976`、`319a616e`、`86ea27de` | `f1cf7920` | 7个公共API、DTO、异常与契约测试路径当前存在；生产Provider仍未实现 | 仅该PROJ公共契约代码已集成，不接收COM实现 |
| F-CUT-002/F-CUT-005 PROJ候选合同 | `e68ad4e0..8eb36222`、`5e3ce44c..912d0cdb` | `e2f51762` | `CONTRACT_ONLY`：ADR、机器合同和追溯生成器变更已集成；没有可声称已完成的Java接口或Provider | 不把合同回执伪装成生产代码；缺失实现须另建物理Owner DU |

### master已接收的CUT分支PROJ物理Owner增量

| PROJ能力 | 来源提交时间线 | master回执 | 当前权威成熟度 | 消费Feature影响 |
|---|---|---|---|---|
| `ProjectSystemQualificationFactApi` | `b4f16bdf`定义契约 → `f4aa1ad2`实现Provider与测试 | `5f5148a9` | 公共API、生产Provider、单元/契约/MySQL测试源已集成；master聚焦测试通过，真实MySQL未在本工作树重跑 | 仅支撑F-IMP-002；不产生Feature Done |
| `ProjectDeliveryScopeQualificationFactApi` | `9d029976`初始契约 → `319a616e`机器合同 → `86ea27de`最终判定顺序 | `f1cf7920` | 公共API、机器合同和契约测试已集成；生产Provider未实现 | 仅支撑F-COM-001；不解决COM-A/COM-B冲突，不产生Feature Done |
| `ProjectCutoverContextFactApi` | `e68ad4e0`提出合同 → `f04650b6/17c826e1/5d334050/15c25e89`收敛 → `8eb36222`冻结 | `e2f51762` | ADR和机器合同已集成；公共Java接口、生产Provider未实现 | 仅支撑F-CUT-002；不产生Feature Ready或Done |
| `ProjectCutoverServiceManagerFactApi` | `5e3ce44c`提出候选合同 → `2efad8ce/2e3fdba3/d990c205`收敛 → `912d0cdb`计划Gate | `e2f51762` | 共址候选Owner机器合同已原样集成，其中SYSTEM组合结构只用于保持判定完整性；PROJ公共Java接口与生产Provider、SYSTEM Provider/实现均未集成 | 仅支撑F-CUT-005；不产生Feature Ready或Done |

### PRE/SOL全时间线集成裁决

| Feature/Requirement | master权威事实 | 分支与Worktree候选裁决 | 后续实施边界 |
|---|---|---|---|
| F-SOL-001 / PRE-01 | `IMPLEMENTATION_COMPLETE`；旧工期写入口为`DEPRECATED_READ_ONLY` | 未发现master缺失增量 | 只可从master新建DU，不得恢复旧写入口 |
| F-SOL-002 / PRE-02 | `IMPLEMENTATION_COMPLETE`；旧现场勘察写入口为`DEPRECATED_READ_ONLY` | `29111833`的6个相关路径与master树等价 | 不重复合并，不在旧现场勘察入口继续实施 |
| F-SOL-003 / PRE-04 | `IMPLEMENTATION_COMPLETE`且Requirement覆盖仍为`PARTIAL`；固定章节载体为`DEPRECATED_READ_ONLY` | `3e27f047`先由`master@2bdbb04c`补丁等价接收，现已通过真实双亲merge进入`master@c1bbae90`；7a76工作树18项相同副本无需迁移，4项共享投影为`STALE_COPY` | 新能力只进入动态表单链；不得恢复固定章节实施入口 |
| PRE-03 / PRE-05 | 无正式Feature/Task实施链，`NOT_STARTED` | 全部分支、Worktree和stash均无可接收Owner增量 | 先建立Feature Spec、Task和DU，再实施 |
| SOL-01 | F-PLT-002仅形成`PARTIAL`覆盖 | 未发现可把SOL-01提升为完成的权威候选 | 保持`PARTIAL`，不得从相邻平台能力推导Done |
| 临时/跨Feature命中 | 不属于PRE/SOL实现事实 | 50eb的914项命中均在`.codex-tmp/qa/`；`486727a3`为ACC分支9参数测试适配，均不适用master | 临时副本不得提交；ACC适配不得移植到8参数master合同 |

当前没有有效写入认领；`DU-20260902-POST-20260821-BRANCH-FEATURE-CONVERGENCE`已完成2026-08-21起19个非排除分支的逐项裁决，以零新增代码合并释放治理边界。`DU-20260902-MASTER-INTEGRATED-CODE-CONVERGENCE`已完成全部已集成代码回执核验和F-CUT-001隔离代码分支真实合入并释放边界。`DU-20260901-PRE-SOL-CODE-BRANCH-MERGE`已把F-SOL-003废弃代码分支真实合入master并释放边界，`DU-20260901-PRE-SOL-AUTHORITATIVE-INTEGRATION`已完成全时间线裁决，`DU-20260901-CUT-PROJ-OWNER-INTEGRATION`、`DU-20260901-FCUT001-INTEGRATION`与`DU-20260901-FPROJ008-MIGRATION`已完成可构建增量的选择性集成并释放边界。其他历史活动分支均未被倒签为有效认领。

## 分支Feature候选裁决

| Feature/工作包 | 提交或分支证据 | 更正后的master裁决 | 下一动作 |
|---|---|---|---|
| COM-A、ACC-001、ACC-002 | `codex/f-acc-001-sds@58576666`；完成证据`563daac1/ad5b401f/8ed75093` | 顺序完成候选，但与COM-B竞争且PRD Change ID冲突 | 关闭`Q-GOV-20260901-001/002`后逐项选择提交，禁止整支合入 |
| COM-B | CUT/PROJ共享线，自`c21745a9`开始；PROJ资格契约最终提交`86ea27de` | COM实现仍为`CONFLICTED_IMPLEMENTATION / IN_PROGRESS`；仅`ProjectDeliveryScopeQualificationFactApi`公共契约已进入`master@f1cf7920` | 与COM-A按PRD、Spec逐项裁决；PROJ生产Provider仍须独立DU实现 |
| F-AST-002 | `a52b22b4..68bc56ec` | 独立完成候选；不是F-INS-001脏改动的一部分 | PRD冲突关闭后更新master并复验 |
| F-INS-001 | `feat-inspection-feature-xkjuCC@e13feca7`及4项未提交Task 5变更；`e13feca7`聚焦提交Task 4实现与验证记录 | Task 4=`INTEGRATION_CANDIDATE`；Task 5=`UNCLAIMED_DIRTY`；无跨Feature领域实现冲突 | 独立复核并选择性集成Task 4；保留Task 5脏改动，先在最新master建立有效DU，再处理`Q-FINS001-004`和Flyway最终编号 |
| F-CUT-001 | `08457e39..72ccb83f` -> `07b6eb06` -> `master@c61e5b1e` -> merge `dc55b92a` | `INTEGRATED_PARTIAL / SOURCE_BRANCH_IN_MASTER`；Feature权威状态仍为`IN_PROGRESS / MASTER_REVALIDATION` | 新建DU完成V133示例迁移与合入后独立MySQL/真实浏览器最终DoD |
| F-CUT-002 | `codex/f-cut-001-matrices@85b93828`；PROJ上下文合同时间线`e68ad4e0..8eb36222` | `QUARANTINED / IN_PROGRESS`；仅PROJ ADR和机器合同已进入`master@e2f51762`，无Java接口/Provider | CUT实现仍须从多Feature分支拆分DU；PROJ Provider另建物理Owner DU |
| F-CUT-003 | `codex/f-cut-001-matrices@85b93828`继承文本与实现 | `QUARANTINED / IN_PROGRESS` | 从多Feature分支拆分DU，不按分支头推断Owner |
| F-CUT-004 | 同上 | `IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES`，生产依赖未满足 | 不得声明Done或激活完整生产入口 |
| F-CUT-005 | `2e3fdba3` Ready；`912d0cdb` Plan GO；Task 1/2候选至`85b93828`；PROJ服务经理合同`5e3ce44c..912d0cdb` | `QUARANTINED / IN_PROGRESS / Task 3`；仅PROJ候选Owner机器合同已进入`master@e2f51762`，无Java接口/Provider | CUT Task 1/2仍按提交边界拆出集成DU；Task 3继续写入前必须先形成有效认领；PROJ Provider另建物理Owner DU |
| F-IMP-001 | CUT共享线API/DTO/测试提交 | Ready NO-GO、Task NOT_STARTED下发生实施 | 隔离候选，Ready前代码不得作为正常认领或Done |
| F-IMP-002 | CUT共享线；PROJ系统资格合同/Provider提交`b4f16bdf/f4aa1ad2` | `QUARANTINED / IN_PROGRESS`；仅PROJ支撑API、生产Provider和测试已进入`master@5f5148a9` | IMP消费实现仍须拆分DU并确认依赖、边界和交接；不得因Provider存在声明Feature Done |
| PRE/SOL物理Owner候选 | `3e27f047`、`29111833`、`486727a3`及7a76/50eb工作树 | `3e27f047=IN_MASTER@c1bbae90`；其余为`ALREADY_IN_MASTER / CROSS_FEATURE_NOT_APPLICABLE / STALE_COPY`；不存在`MISSING_VALID_DELTA` | 已接收代码分支不得重复实施；后续PRE/SOL只从master新建Feature/Task/DU |
| F-PROJ-001旧原子对齐线 | `codex/f-proj-001-atomic-alignment@8bbaf69a`；`298a2340`～`8bbaf69a`六条提交发生于2026-08-21 14:42～15:43 | `SUPERSEDED / DO_NOT_MERGE`；旧Task仅推进到中段，master后续V1.8重做已完成Task 0～9与AC 1～10 | 禁止继续在旧分支实施；后续PROJ工作只从master当前权威Task建立新DU |
| V1.8独立裁决线 | `codex/v1-8-feature-revalidation-50eb@68db25b3`；仅一条2026-08-29 04:54提交 | `ALREADY_IN_MASTER / SUPERSEDED`；补丁等价于master`29e9a415`，且master已由`0b0f9f9a`继续修订 | 无需合并、无需新认领；保留只读历史 |
| F-PROJ-008 | `codex/f-proj-008-stage-advance@48175aa0`；Task 1=`0c7a9634`、Task 2=`d69b3ff8`、Task 3=`a3bd0043` | `INTEGRATED_PARTIAL / IN_PROGRESS`；Task 1-2已选择性进入master，Task 3未集成 | 等待`Q-FPROJ-009`业务裁决；关闭后从最新master新建DU，不回旧分支继续写入 |
| F-INT-012 | `84258059..cdfbd71a` | `NO_TASK / UNCLAIMED_IMPLEMENTATION` | 补Feature Task、Ready和Owner边界前禁止合入 |

## 全部分支分类

- 已完成本轮选择性集成：`codex/f-cut-001-master-integration`已通过`master@dc55b92a`真实双亲merge转为`IN_MASTER / INTEGRATED_PARTIAL`；`codex/f-sol-003-legacy-deprecation`已通过`master@c1bbae90`真实双亲merge转为`IN_MASTER / INTEGRATED_COMPLETE`；`codex/f-proj-008-stage-advance`只迁入Task 1-2，裁决为`INTEGRATED_PARTIAL`而非整支等价。
- 历史来源候选：`codex/integrate-f-cut-001`；其适配范围已进入master，不能再作为当前状态源或新实施基础。
- 活动但隔离：`codex/f-cut-001-matrices`、`codex/f-acc-001-sds`、`prereq-parallel-check-kKiAdn`。`codex/f-cut-001-matrices`仅四组PROJ物理Owner carve-out已选择性集成，剩余文件、提交与Feature状态仍隔离；`codex/f-proj-008-stage-advance`已释放写边界，其未集成Task 3只能作为冻结候选，不再是后续实施基础。
- 独立实现候选：`feat-inspection-feature-xkjuCC@e13feca7`；仅Task 4候选进入复核。该工作树另有4项未提交Task 5变更，必须保留并迁移到master新DU，不得沿历史未认领边界继续写入。
- 完成候选祖先：`codex/f-com-001-feature-ready`、F-AST-002提交段。
- 已被master包含或补丁/树等价：`engineering-chain-phase-TmrsP0`、`feat-inspection-feature-Q7yA35`、`feat-parallel-features-akPsDH`、`codex/merge-engineering-chain-phase-tmrsp0`、`codex/v1-8-feature-revalidation-50eb`及已包含的chore/import/spec历史分支。
- 已替代实施基础：`codex/f-proj-001-atomic-alignment`。其六条旧提交及旧任务链全部只读保留，不得再承接F-PROJ-001或其他PROJ新实施。
- 报告或审计历史：`prd-audit-v1-8-LAR2Ap`，不构成Feature认领。

精确HEAD、ahead/behind、DAG关系、Worktree脏项、stash和所有master外提交以固定时间线报告为准，不能用本段摘要覆盖。

## 严重漂移与阻断

- `Q-GOV-20260901-001`：并行PRD分别重复使用修订`010`与`011`表达不同业务语义；它只阻断分支PRD直接晋级、全局编号定稿和整支合并，不构成F-INS与其他Feature的实现冲突，也不阻断`e13feca7`的独立复核。
- `Q-GOV-20260901-002`：F-COM-001存在两套大范围、低重叠实现；不得按提交时间或分支Done整体选择。
- CUT活动分支同时承载COM、IMP和CUT多个未收口Feature，且在前序未交接时继续启动后续Feature，是当前最严重的任务飘移；本轮只收回四组PROJ物理Owner事实，不代表其余跨Feature漂移已经解除。
- F-INS Task 5已在无有效DU时产生4项脏改动，且临时`V148`与CUT分支提交`37723669`编号相同；这是共享Flyway的串行收口问题，不是两个Feature的领域实现冲突。
- PROJ三线的漂移已收口：50eb线是master补丁等价历史；F-PROJ-001原子对齐线已被当前完成实现替代；F-PROJ-008只承认Task 1-2集成事实，分支Task 3和Done表述不得覆盖master权威Task。
- Worktree脏改动和stash均未被提升为提交证据；原内容保持不变。

## 投影规则

- `tasks/delivery-units/DU-*.md`决定认领，不从分支内Task、提交标题或继承关系推断。
- 分支候选进入master前不改变Feature Done或Requirement覆盖。
- `INTEGRATED_PARTIAL`只表示master已接收可构建增量；Feature仍保持`IN_PROGRESS`。
- 旧功能替代与禁用以`tasks/implementation-baseline-inventory.json#legacyCutovers`为唯一结构化记录；废弃路径不得承接新Feature。
