# DU-20260908-TEMPLATE-ACCEPTANCE-CONTRACT-CANDIDATE 独立验收接口与物理契约候选

> DU状态：`RELEASED`
> DU类型：`GOVERNANCE`
> Feature协调：`F-PROJ-001=TASK_COORDINATED;F-PROJ-007=TASK_COORDINATED;F-PROJ-008=TASK_COORDINATED;F-ACC-001=TASK_COORDINATED;F-COM-001=TASK_COORDINATED`
> Task范围：`R2候选技术GO回执与修订018受影响Phase 1边界复核；不实施应用代码或Phase 2正式物理合同`
> Owner：`审计模板能力演进设计任务01a07a94-a002-7131-bf20-2ad931b6a0f7`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/79ed/NPDMS`
> 认领基线：`3abdeb1ea50acca70eb7c6e1b256dab8a2c1c76f`
> 认领提交：`SELF`
> 修改边界：`docs/superpowers/specs/2026-09-08-template-acceptance-phase2-contract-design.md;docs/superpowers/specs/2026-09-08-template-acceptance-phase2-contract.json;docs/decisions/open-questions.md;docs/engineering/gates/phase-2/gate-status.md;tasks/delivery-units/DU-20260908-TEMPLATE-ACCEPTANCE-CONTRACT-CANDIDATE.md;tasks/delivery-units/README.md;docs/design/02-domain-model.md;docs/design/02a-context-map.md;docs/design/02b-aggregate-boundary-decisions.md;docs/design/02c-data-ownership-matrix.md;docs/design/02d-cross-context-contracts.md;docs/design/04-module-design.md;docs/design/05-state-machine.md;docs/design/06-workflow-design.md;docs/design/07-authorization-design.md;docs/engineering/gates/phase-1/gate-status.md;docs/engineering/gates/phase-1/README.md;docs/engineering/gates/phase-1/revision-018-template-acceptance-review.md;docs/engineering/gates/phase-2/input/template-acceptance-r2-independent-review.md`
> 串行资源：`修订018受影响Phase 1正式分册/复核输入及R2回执；不占用业务代码、Phase 2物理合同或Flyway`
> 旧功能范围：`仅读取既有验收/范围/工作绑定及迁移作为复用证据；不修改旧实现或历史数据`
> 验证：`Phase 1边界自审、R2合同内容未漂移及认领范围检查；全量技术脚本仍有Phase 2载体PRD身份陈旧一项FAIL，不据此放行`
> 集成记录：`996d5150受影响Phase 1逻辑边界独立GO；原全量审计FAIL保留历史。需求方转入变更级审查治理，本DU释放范围，不晋级验收实现`

## 输出与边界

- 上一文档DU已由本任务交接，书面逻辑设计于2026-09-08获需求方确认。
- 产出ACC独立创建/完成/事实、COM阶段无关范围快照、范围变化回调、来源与规则冻结、字段/唯一键/约束及权限/事务的可审阅候选。
- 只选择真实存量可复用能力；新接口明确标为候选，不假设现有方法已有所需字段。scopeVersion不替代allocationVersion，文件scopeVersion不替代业务范围版本。
- 当前SDS Gate仍REVALIDATION_REQUIRED；不以书面确认或候选自审直接关闭Q、Phase、Feature Ready/Done，不生成绕过前置的可执行代码计划。
- Q仅限制独立验收及其接入，不阻断不依赖它的模板基础配置；具体业务实现、DDL、生产迁移和推送均不在本DU范围。

## 2026-09-08 独立复核整改R1

- 独立任务`01a07ce8-42fa-7dd2-8991-094d0c15cc6c`对`c18e5959e70ea2eef17a815b56067e4612842d6f`裁决`NO-GO`：报告共享锁序、配置化报告生命周期、范围替换/拆分保护、多交付件查询、BPM可靠捕获共五项。
- 需求方已确认仅修订上述契约，再交同一任务复审；沿用本DU六文件边界及原认领，不修改实现或正式物理合同。
- 本轮只补齐原候选的具体技术义务，不改变项目级主体、业务规则可配置及S5不强制终验的已确认边界。

## 2026-09-08 两项剩余契约修订R2

- 独立任务对`a606e3c4`保留NO-GO，仅余多目标共同归档身份和captureKey/eventId长度两项P2；R1-01/02/03契约层已解决，其他主要设计保留。
- 需求方授权继续修订，并要求先完成一轮自审再提交复审；继续沿用本DU六文件认领边界，不改代码、DDL或正式物理合同。
- 已先完成候选修订和一轮归档命令重放、BPM捕获/重放、物理长度及文档一致性自审；本地提交后交原独立任务。

## 交接

本轮阶段切换（2026-09-08）：需求方要求“下一步”，按工程链推进受影响Phase 1；原R2技术GO只作候选证据。先在master提交本次排他范围扩展，再写九份Phase 1分册及复核输入。候选正文/API/存储设计不再调整，只登记复核结果；不改PRD、正式物理合同、Feature Ready/Done或实现。

- 最后提交：`SELF`（本文件所在候选送审提交，以Git读取）
- 已完成：R2独立技术GO接收记录；九份修订018受影响Phase 1正式分册的项目级身份/Owner/可选COM/状态与权限对齐，自审及独立复核输入。R2仅修改review回执，不改已审合同。
- 需求方补充：验收主要是项目层面；本候选以tenant/project/acceptanceType为主身份，不按节点/订单行/设备创建验收根，COM依赖按规则适用。
- 剩余：受影响Phase 1边界复核、Phase 2具体合同/Schema审阅与适用验证；通过后再按Feature形成实施计划，不提前关闭Q。
- 运行验证：未运行应用或数据库迁移。

## 自审与检查

- 候选R2 JSON解析、50个记录类型、10个Owner接口/应用服务、16个方法及10条REST映射的引用检查通过；新增捕获请求/结果与既有PLT归档命令精确对应。
- 项目主身份、可选COM版本、未知Fact与明确不满足的区分、来源互斥、旧数据边界和只读无创建规则已核对。
- Q-TPLACC-001和SDS状态保持未关闭；结构检查不是Java编译、MySQL/DDL或业务验收通过。
- 原PR1 DU仅行尾改动继续保留；业务代码、迁移、现行物理合同及其他工作树未修改。
- R1独立结果及R2自审定位见候选第9/10节。自审逐项追踪PLT重放字段、捕获请求/结果/Outbox及提交/回滚/ACK丢失；命令样例两目标八字段一致，批次长38、最大AAP长108、eventId长36，物理长度匹配。
- R2相对R1未更改已解决的锁序、报告生命周期、范围保护、REST和原物理设计；旧归档批次保持。实际双目标归档、事务/DDL及业务测试NOT_RUN；自审完成后才提交原独立任务，不自签GO。

## Phase 1本轮检查点

2026-09-08接收独立任务`01a07ce8-42fa-7dd2-8991-094d0c15cc6c`裁决：`996d5150`受影响逻辑边界GO，全量Gate当时仍未放行。需求方随后明确批准“取消阶段全量阻断、保留变更级实质审查”，由`DU-20260908-CHANGE-SCOPED-REVIEW`承接治理修改；本DU停止写入并释放全部边界，原审阅和检查事实不改写。

- 基线：R2@3d82db0b技术GO；范围扩展先提交`278ee1e9`。当前Gate：REVALIDATION_REQUIRED/独立PENDING。
- 九份Phase 1分册完成差量自审；技术脚本修改前2项FAIL，README对齐后仅剩Phase 2 carrier contract PRD identity陈旧1项FAIL。未改该物理合同或脚本，不把检查失败隐藏成通过。
- 下一步：同一独立任务复核本次受影响逻辑边界及全量Gate限制；其后再按序处理正式Phase 2/API/物理/Schema。代码、DDL、PRD、Feature状态及原PR1行尾差异保留。
