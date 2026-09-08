# F-PROJ-008 项目阶段准出门禁与正向推进 Feature Task

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：以当前Feature Spec为准（修订018为`REVALIDATION_REQUIRED`；原BASELINE/READY/GO保留为历史）
> Technical Plan Gate：`PASS / GO`（`NPDMS-FPROJ008-TECHPLAN-20260901-01`；候选`8778b963`）
> Implementation Done Gate：`NOT_READY`
> 当前阻断：`Q-FPROJ-009业务设计已解决；首次PROJECT_MANAGER指派、PM-01绑定与双主责状态事务仍待当前Spec/计划及实现复验；独立验收新增接入另受Q-TPLACC-001实质合同约束`
> 当前任务：`先准备相应Spec/唯一计划差量与明确业务代码Task，再完成Task 3B真实Chromium正向闭环；不重复请求首次指派业务裁决`
> Requirement ID：`PM-03@V1=PARTIAL`
> Feature Spec：`specs/features/F-PROJ-008-project-stage-gate-and-forward-advance.md`
> Feature物理契约：`specs/features/F-PROJ-008-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-09-01-f-proj-008-project-stage-gate-and-forward-advance.md`

## 串行任务

- [x] Task 1：六类Gate Owner、Flowable定义身份与模板发布校验（`COMPLETED / INTEGRATED`）
- [x] Task 2：readiness、Gate流程启动REST与原子相邻推进（`COMPLETED / INTEGRATED`）
- [x] Task 3A：项目工作区阶段门禁面板、API调用与组件测试（`IMPLEMENTED / SELECTIVELY_INTEGRATED_FROM_a3bd0043`）
- [ ] Task 3B：一次真实Chromium正向闭环（`IMPLEMENTATION_REVALIDATION_REQUIRED / Q-FPROJ-009已解决设计`；先补齐实际业务命令与绑定链，不能靠预置项目经理绕过）

## 当前准备与历史集成检查点

2026-09-08当前准备范围见[主线分析](../../docs/superpowers/plans/2026-09-08-project-delivery-mainline-preparation.md)。下文集成回执及当时对Q-FPROJ-009的阻断描述保留历史；当前Question已为RESOLVED_DESIGN/IMPLEMENTATION_REVALIDATION_REQUIRED，不能据此重复业务裁决或放宽TASK_NATIVE通用权限。首次指派准备包不依赖独立验收合同的部分可先推进，不把Q-TPLACC-001扩大为全S0准备阻断。

master已从源提交`0c7a9634`、`d69b3ff8`选择性迁入Task 1、Task 2，并完成master侧复核修订；Task 2计划要求的Readiness、Application、Controller与真实MySQL测试共10项全部PASS、无跳过，`pms-module-project,pms-module-integration`受影响模块package PASS。

本次按`PM-03@V1`需求标记复核源提交`a3bd0043`：其项目工作区UI、阶段准备度/流程启动/相邻推进API调用及组件测试不依赖首次项目经理指派裁决，且被修改的两个既有前端文件在源提交父版本与当前master之间Blob一致，因此Task 3A可无损选择性集成。未迁入该提交对Open Question的历史写入；`Q-FPROJ-009`继续只阻断新建项目S0→S1真实正向链、首次项目经理指派命令和Feature Implementation Done，不回退已实现UI。

## 边界

- 不实现S4→S5、回退、异常关闭、重开、CUT或第三方审批；
- 不修改Yudao基础平台，不新增PMS流程版本字段、Flyway、权限键或第二阶段模型；
- Task 3A集成不代表S0→S1可完成，也不代表Feature Implementation Done；
- 每个Task先实现正向功能，再做聚焦验证并提交；Implementation Done独立GO前不得回写完成。

## 2026-09-07 修订018影响记录

模板业务规则配置化与独立验收设计已获需求方确认，正文及当前Feature Spec已登记`CHG-PRD-2026-09-07-018`。上文Implementation状态、提交、测试及历史Done保持原范围，不自动覆盖新语义；当前Ready以Feature Spec的REVALIDATION_REQUIRED为准。Q-TPLACC-001限制新增实体创建/范围绑定及其消费者路径，须先完成Phase 2差量再更新唯一实施计划，不从本次文档确认派生Task/Feature完成。
