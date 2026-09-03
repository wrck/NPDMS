# 代码事实优先的 Feature 与追溯状态同步

- 仓库：`wrck/NPDMS`
- 审计基线：`master@4ec687f3fb2cb70a5bfff99f545284e64c86a218`
- 基线 Tree：`738da7e66ba286583d71f286c383e30e6113e59a`
- 同步分支：`codex/code-first-feature-traceability-sync-20260903`
- 审计日期：2026-09-03
- 原则：提交代码、测试、迁移及当前主干实现是代码事实源；Feature 索引和 Requirement 追溯只投影已经成立的事实，不以分支自报状态或接口空壳提升完成度。

## 1. 同步结论

逐项比较候选分支与当前主干后，本轮没有可安全回灌的分支独有生产实现：

- 生产代码新增或回灌：`0`
- 已进入主干或已被主干后继实现覆盖：`F-PROJ-008`、`F-ACC-001`、`F-ACC-002`、`F-COM-001`、`F-CUT-001`、`F-IMP-002`、`F-INT-012`、`F-INS-001`
- 明确拒绝回灌的旧实现：F-PROJ-001 第二套模板候选入口、F-INT-012 旧 Infra 文件 Owner/迁移、F-IMP-001 合同空壳
- Feature 完成状态提升：`0`
- 本次实际状态变更：修正 Feature 索引中的 4 条陈旧实施说明，并固化本追溯快照

“代码已进入主干”不等于“Implementation Done”。真实 MySQL、浏览器或端到端证据、独立复审以及剩余 Task 未闭合时，Feature 和 Requirement 切片继续保持进行中。

## 2. 逐项代码整合裁决

| Feature | 代码裁决 | 当前代码事实 | Feature 状态 | 剩余 Gate |
|---|---|---|---|---|
| F-PROJ-001 | `REJECT_BACK_MERGE / SUPERSEDED` | 当前主干已有完整替代实现 | `IMPLEMENTATION_COMPLETE` | 无 |
| F-PROJ-008 | `NO_OP` | Task 1、2、3A 已进入主干；不存在 Task 3B 生产提交 | `IN_PROGRESS` | Task 3B 规格关闭与实现 |
| F-ACC-001 | `NO_OP / EXACT_IN_MASTER` | 生产实现及迁移已接收 | `IN_PROGRESS` | 当前主干 MySQL、Chromium、独立 Done |
| F-ACC-002 | `NO_OP / EXACT_IN_MASTER` | 生产实现及迁移已接收 | `IN_PROGRESS` | 运行复验、独立 Done |
| F-COM-001 | `NO_OP / MASTER_SUPERSET` | 来源实现已接收，主干另有后继增强 | `IN_PROGRESS` | 当前主干运行复验、独立 Done |
| F-CUT-001 | `NO_OP / MASTER_SUPERSET` | 矩阵实现及 V132 已接收，主干另有后继修正 | `IN_PROGRESS` | V133 示例迁移、最终 DoD |
| F-IMP-001 | `REJECT / CONTRACT_ONLY` | AST 支撑已实现；旧分支仅有公共合同和 DTO，没有核心快照闭环 | `IN_PROGRESS` | 聚合、持久化、Provider、事件及 CUT 消费 |
| F-IMP-002 | `NO_OP` | Task 1～11 已进入主干；不存在 Task 12 生产装配提交 | `IN_PROGRESS` | 组件注册、Job、真实基础设施装配 |
| F-INT-012 | 核心 `NO_OP`；旧 Infra `REJECT / SUPERSEDED` | PLT 核心已接收；旧第二文件 Owner 与当前架构冲突 | `IN_PROGRESS` | INT 边缘、真实网关、E2E |
| F-INS-001 | `NO_OP / MASTER_SUPERSET` | Task 1～8 已进入主干，发布链已有后继修正 | `IN_PROGRESS` | Task 9 最终验证、独立 Done |

## 3. Feature 索引同步

`specs/features/README.md` 的以下说明按当前主干代码事实更新：

| Feature | 同步后的说明 |
|---|---|
| F-PROJ-008 | Task 1、2、3A 已进入 `master`；Task 3B 受规格缺口阻断；尚未进入 Implementation Done |
| F-COM-001 | 生产代码已进入 `master` 并有后继增强；待当前主干真实 MySQL、运行复验与独立 Done 裁决 |
| F-CUT-001 | 矩阵生产代码与 V132 已进入 `master` 并有后继修正；V133 示例迁移及当前主干最终运行 DoD 待完成 |
| F-INS-001 | Task 1～8 已进入 `master`；Task 9 最终验证、真实运行证据与独立 Done 裁决待完成 |

F-IMP-001 和 F-IMP-002 在索引中已经正确表达为进行中，因此不提升状态：

- F-IMP-001：`AST_SUPPORT_IMPLEMENTED_IN_MASTER / CORE_READINESS_SNAPSHOT_PENDING`
- F-IMP-002：`TASK_1_TO_11_IMPLEMENTED_IN_MASTER / TASK_12_PRODUCTION_ASSEMBLY_PENDING`

## 4. Requirement 追溯投影

| Requirement 切片 | 预期状态 | 依据 |
|---|---|---|
| `PM-03@V1` | `IMPLEMENTATION_PARTIAL` 或生成器按多 Feature 映射得到的当前值 | F-PROJ-001 已完成；F-PROJ-008 仍为部分实现 |
| `ACC-02@V1` | `IMPLEMENTATION_IN_PROGRESS` | F-ACC-002 未闭合运行证据与独立 Done |
| `ACC-03@V1` | `IMPLEMENTATION_IN_PROGRESS` | F-ACC-001 未闭合运行证据与独立 Done |
| `ACC-04@V1` | `IMPLEMENTATION_IN_PROGRESS` | 两个 ACC 局部来源均未完整 Done |
| `COM-01@V1` | `IMPLEMENTATION_IN_PROGRESS` | 当前主干最终运行复验及独立裁决未闭合 |
| `CUT-07@V1`、`CUT-09@V1`、`CUT-10@V1` | `IMPLEMENTATION_IN_PROGRESS` | V133 和最终 DoD 待完成 |
| `EXE-01@V1` | `IMPLEMENTATION_IN_PROGRESS` | F-IMP-002 Task 12 待完成 |
| `EXE-06@V1` | `IMPLEMENTATION_IN_PROGRESS` | F-IMP-001 核心快照未实现 |
| `INT-12@V1` | `IMPLEMENTATION_IN_PROGRESS` | INT 边缘、真实网关及 E2E 待完成 |
| `INS-03@V2`、`INS-09@V2` | `IMPLEMENTATION_IN_PROGRESS` | F-INS-001 Task 9 最终验证待完成 |

`docs/traceability/requirement-matrix.md` 和 `requirement-version-coverage.json` 由 `scripts/generate_requirement_traceability.py` 根据 Feature Spec 的机器可读覆盖声明与 `tasks/features/F-*.md` 权威状态生成。本轮没有修改覆盖声明或权威任务状态，因此不手工改写生成文件；其当前投影应保持不变。

## 5. 审计边界

本次提交只同步已经由代码事实证明的描述，不执行下列不成立的状态提升：

- 不把候选分支成为主干祖先解释为 Feature Done；
- 不把公共 API、DTO 或合同测试解释为生产 Provider 已实现；
- 不把历史浏览器或数据库证据自动继承到当前主干；
- 不把局部 Task 完成提升为完整 Requirement 切片完成；
- 不回灌会建立第二 Owner、第二套路由或冲突迁移链的旧实现。
