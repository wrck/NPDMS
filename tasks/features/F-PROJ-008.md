# F-PROJ-008 项目阶段准出门禁与正向推进 Feature Task

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`BASELINE / READY / GO`
> Technical Plan Gate：`PASS / GO`（`NPDMS-FPROJ008-TECHPLAN-20260901-01`；候选`8778b963`）
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：`Q-FPROJ-009（Task 3 项目工作区展示边界未获权威规格批准）`
> 当前任务：`Task 3 BLOCKED_BY_SPEC；不得迁入源分支UI提交`
> Requirement ID：`PM-03@V1=PARTIAL`
> Feature Spec：`specs/features/F-PROJ-008-project-stage-gate-and-forward-advance.md`
> Feature物理契约：`specs/features/F-PROJ-008-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-09-01-f-proj-008-project-stage-gate-and-forward-advance.md`

## 串行任务

- [x] Task 1：六类Gate Owner、Flowable定义身份与模板发布校验（`COMPLETED / INTEGRATED`）
- [x] Task 2：readiness、Gate流程启动REST与原子相邻推进（`COMPLETED / INTEGRATED`）
- [ ] Task 3：项目工作区与一次真实Chromium正向闭环（`BLOCKED_BY_SPEC / NOT_INTEGRATED`）

## 当前检查点

master已从源提交`0c7a9634`、`d69b3ff8`选择性迁入Task 1、Task 2，并完成master侧复核修订；Task 2计划要求的Readiness、Application、Controller与真实MySQL测试共10项全部PASS、无跳过，`pms-module-project,pms-module-integration`受影响模块package PASS。Task 1、2完成不等于Feature Implementation Done；源提交`a3bd0043`的Task 3 UI因`Q-FPROJ-009`仍为`BLOCKED_BY_SPEC`，不得迁入master。

## 边界

- 不实现S4→S5、回退、异常关闭、重开、CUT或第三方审批；
- 不修改Yudao基础平台，不新增PMS流程版本字段、Flyway、权限键或第二阶段模型；
- 每个Task先实现正向功能，再做聚焦验证并提交；Implementation Done独立GO前不得回写完成。
