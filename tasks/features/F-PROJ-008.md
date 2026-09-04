# F-PROJ-008 项目阶段准出门禁与正向推进 Feature Task

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`BASELINE / READY / GO`
> Technical Plan Gate：`PASS / GO`（`NPDMS-FPROJ008-TECHPLAN-20260901-01`；候选`8778b963`）
> Implementation Done Gate：`NOT_READY`
> 当前阻断：`Q-FPROJ-009（新建项目首次PROJECT_MANAGER指派与T-ASSIGN-PM完成形成循环依赖）`
> 当前任务：`Task 3A项目工作区已选择性集成；Task 3B真实Chromium正向闭环BLOCKED_BY_SPEC`
> Requirement ID：`PM-03@V1=PARTIAL`
> Feature Spec：`specs/features/F-PROJ-008-project-stage-gate-and-forward-advance.md`
> Feature物理契约：`specs/features/F-PROJ-008-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-09-01-f-proj-008-project-stage-gate-and-forward-advance.md`

## 串行任务

- [x] Task 1：六类Gate Owner、Flowable定义身份与模板发布校验（`COMPLETED / INTEGRATED`）
- [x] Task 2：readiness、Gate流程启动REST与原子相邻推进（`COMPLETED / INTEGRATED`）
- [x] Task 3A：项目工作区阶段门禁面板、API调用与组件测试（`IMPLEMENTED / SELECTIVELY_INTEGRATED_FROM_a3bd0043`）
- [ ] Task 3B：一次真实Chromium正向闭环（`BLOCKED_BY_SPEC / Q-FPROJ-009`）

## 当前检查点

master已从源提交`0c7a9634`、`d69b3ff8`选择性迁入Task 1、Task 2，并完成master侧复核修订；Task 2计划要求的Readiness、Application、Controller与真实MySQL测试共10项全部PASS、无跳过，`pms-module-project,pms-module-integration`受影响模块package PASS。

本次按`PM-03@V1`需求标记复核源提交`a3bd0043`：其项目工作区UI、阶段准备度/流程启动/相邻推进API调用及组件测试不依赖首次项目经理指派裁决，且被修改的两个既有前端文件在源提交父版本与当前master之间Blob一致，因此Task 3A可无损选择性集成。未迁入该提交对Open Question的历史写入；`Q-FPROJ-009`继续只阻断新建项目S0→S1真实正向链、首次项目经理指派命令和Feature Implementation Done，不回退已实现UI。

## 边界

- 不实现S4→S5、回退、异常关闭、重开、CUT或第三方审批；
- 不修改Yudao基础平台，不新增PMS流程版本字段、Flyway、权限键或第二阶段模型；
- Task 3A集成不代表S0→S1可完成，也不代表Feature Implementation Done；
- 每个Task先实现正向功能，再做聚焦验证并提交；Implementation Done独立GO前不得回写完成。

## 代码事实时间序重放检查点（2026-09-04）

> 依据三个来源分支的实际提交代码记录；代码接收不自动构成 Implementation Done。

- 来源分支：`codex/f-acc-001-sds`, `codex/f-cut-001-matrices`
- 提交-路径事实：`42`
- 重放方式：572 条来源提交按全局提交时间、来源稳定顺序和分支拓扑逐条生成回执。
- 接收边界：全部模块进入扫描；不符合项仅保留到具体文件或 hunk。
- 详细追溯：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。
