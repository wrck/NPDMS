# F-PROJ-008 项目阶段准出门禁与正向推进 Feature Task

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`BASELINE / READY / GO`
> Technical Plan Gate：`PASS / GO`（`NPDMS-FPROJ008-TECHPLAN-20260901-01`；候选`8778b963`）
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：`无`
> 当前任务：`Task 2 readiness、Gate流程启动REST与原子相邻推进`
> Requirement ID：`PM-03@V1=PARTIAL`
> Feature Spec：`specs/features/F-PROJ-008-project-stage-gate-and-forward-advance.md`
> Feature物理契约：`specs/features/F-PROJ-008-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-09-01-f-proj-008-project-stage-gate-and-forward-advance.md`

## 串行任务

- [x] Task 1：六类Gate Owner、Flowable定义身份与模板发布校验（`COMPLETED`）
- [ ] Task 2：readiness、Gate流程启动REST与原子相邻推进（`IN_PROGRESS`）
- [ ] Task 3：项目工作区与一次真实Chromium正向闭环（`PENDING`）

## 当前检查点

基线`b36f709a`；Task 1六类Owner、Flowable定义选择/启动/重放及模板发布校验已完成；聚焦33/33、受影响reactor package PASS；无阻断；下一步实现Task 2 readiness、流程启动REST和原子相邻推进。

## 边界

- 不实现S4→S5、回退、异常关闭、重开、CUT或第三方审批；
- 不修改Yudao基础平台，不新增PMS流程版本字段、Flyway、权限键或第二阶段模型；
- 每个Task先实现正向功能，再做聚焦验证并提交；Implementation Done独立GO前不得回写完成。
