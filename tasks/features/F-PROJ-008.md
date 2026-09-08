# F-PROJ-008 项目阶段准出门禁与正向推进 Feature Task

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：以当前Feature Spec为准（修订018为`REVALIDATION_REQUIRED`；原BASELINE/READY/GO保留为历史）
> Technical Plan Gate：`PASS / GO`（`NPDMS-FPROJ008-TECHPLAN-20260901-01`；候选`8778b963`）
> Implementation Done Gate：`NOT_READY`
> 当前阻断：`修订019改为项目级多经理、当前主责及同角色同权限；Q-FPROJ-010候选范围已关闭，成员及集合资格实现仍待完成；阶段图与独立验收的实际缺口按原范围保留，不由成员准备解除`
> 当前任务：`先由F-PROJ-001 Task 10完成项目级成员能力及消费者复核，再执行Task 3B；不再依赖固定T-ASSIGN-PM绑定，不预置单经理或改任务DONE绕过`
> Requirement ID：`PM-03@V1=PARTIAL`
> Feature Spec：`specs/features/F-PROJ-008-project-stage-gate-and-forward-advance.md`
> Feature物理契约：`specs/features/F-PROJ-008-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-09-01-f-proj-008-project-stage-gate-and-forward-advance.md`

## 串行任务

- [x] Task 1：六类Gate Owner、Flowable定义身份与模板发布校验（`COMPLETED / INTEGRATED`）
- [x] Task 2：readiness、Gate流程启动REST与原子相邻推进（`COMPLETED / INTEGRATED`）
- [x] Task 3A：项目工作区阶段门禁面板、API调用与组件测试（`IMPLEMENTED / SELECTIVELY_INTEGRATED_FROM_a3bd0043`）
- [ ] Task 3B：一次真实Chromium正向闭环（`IMPLEMENTATION_REVALIDATION_REQUIRED`；修订019成员与主责合同及实际阶段守卫可用后执行，不能靠预置经理或固定任务绕过）

## 当前准备与历史集成检查点

2026-09-08当前方向以[修订019](../../docs/baseline/prd-v1.8-amendment-019-project-member-assignment.md)和F-PROJ-001唯一计划当前增量为准：项目级联合/分次指派、多经理、一个当前主责、同角色同权限；无固定指派任务前置。Q-FPROJ-009原绑定设计已被替代，Q-FPROJ-010候选范围已确认同公司跨部门及上游通用公司/角色查询。Q-TPLACC-001仍只约束实际依赖独立验收的路径，不能扩大为成员能力的全局前置。

### 历史集成检查点（以下不作为修订019准入）

master已从源提交`0c7a9634`、`d69b3ff8`选择性迁入Task 1、Task 2，并完成master侧复核修订；Task 2计划要求的Readiness、Application、Controller与真实MySQL测试共10项全部PASS、无跳过，`pms-module-project,pms-module-integration`受影响模块package PASS。

本次按`PM-03@V1`需求标记复核源提交`a3bd0043`：其项目工作区UI、阶段准备度/流程启动/相邻推进API调用及组件测试不依赖首次项目经理指派裁决，且被修改的两个既有前端文件在源提交父版本与当前master之间Blob一致，因此Task 3A可无损选择性集成。未迁入该提交对Open Question的历史写入；`Q-FPROJ-009`继续只阻断新建项目S0→S1真实正向链、首次项目经理指派命令和Feature Implementation Done，不回退已实现UI。

## 边界

- 不实现S4→S5、回退、异常关闭、重开、CUT或第三方审批；
- 不修改Yudao基础平台，不新增PMS流程版本字段、Flyway、权限键或第二阶段模型；
- Task 3A集成不代表S0→S1可完成，也不代表Feature Implementation Done；
- 每个Task先实现正向功能，再做聚焦验证并提交；Implementation Done独立GO前不得回写完成。

## 2026-09-07 修订018影响记录

模板业务规则配置化与独立验收设计已获需求方确认，正文及当前Feature Spec已登记`CHG-PRD-2026-09-07-018`。上文Implementation状态、提交、测试及历史Done保持原范围，不自动覆盖新语义；当前Ready以Feature Spec的REVALIDATION_REQUIRED为准。Q-TPLACC-001限制新增实体创建/范围绑定及其消费者路径，须先完成Phase 2差量再更新唯一实施计划，不从本次文档确认派生Task/Feature完成。
