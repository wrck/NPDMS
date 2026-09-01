# F-PROJ-008 项目阶段准出门禁与正向推进 Feature Task

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`BASELINE / READY / GO`
> Technical Plan Gate：`PASS / GO`（`NPDMS-FPROJ008-TECHPLAN-20260901-01`；候选`8778b963`）
> Implementation Done Gate：`NOT_STARTED`
> 当前阻断：`无`
> 当前任务：`Task 1 六类Gate Owner、Flowable定义身份与模板发布校验`
> Requirement ID：`PM-03@V1=PARTIAL`
> Feature Spec：`specs/features/F-PROJ-008-project-stage-gate-and-forward-advance.md`
> Feature物理契约：`specs/features/F-PROJ-008-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-09-01-f-proj-008-project-stage-gate-and-forward-advance.md`

## 串行任务

- [ ] Task 1：六类Gate Owner、Flowable定义身份与模板发布校验（`IN_PROGRESS`）
- [ ] Task 2：readiness、Gate流程启动REST与原子相邻推进（`PENDING`）
- [ ] Task 3：项目工作区与一次真实Chromium正向闭环（`PENDING`）

## 当前检查点

master已以修订009选择收敛PROJ BPM身份语义，并迁入SDS、Feature Spec和Technical Plan；PRD语义/结构、13领域生成及BPM身份聚焦测试PASS；Task 1实现尚未进入master。下一步选择性迁入`0c7a9634`代码并重新验证，不继承分支Task完成结论。

## 边界

- 不实现S4→S5、回退、异常关闭、重开、CUT或第三方审批；
- 不修改Yudao基础平台，不新增PMS流程版本字段、Flyway、权限键或第二阶段模型；
- 每个Task先实现正向功能，再做聚焦验证并提交；Implementation Done独立GO前不得回写完成。
