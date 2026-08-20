# SDS Phase 1 Review

> 审查状态：`REVALIDATION_REQUIRED`<br>
> 依据：PRD V1.8正式基线、正式工程链 V1.8<br>
> 结论：`NOT_READY_FOR_PHASE_2_V1.8`<br>
> 说明：V1.7的`APPROVED / READY_FOR_PHASE_2`保留为历史结论，不自动继承到V1.8。

## 1. V1.8差量影响

| 检查项 | 当前状态 | 需要关闭的差量 |
|---|---|---|
| 正式范围 | OPEN | 追溯范围由103项调整为100项（V1 53、V2 47） |
| 领域与聚合 | OPEN | ACC-05后置V3；COM-02、IMP-02退出当前基线；确认旧聚合、对象和跨域契约无残留 |
| 项目状态模型 | OPEN | 按`current_stage`、`lifecycle_status`、指派状态和派生展示状态重验证状态机与模块职责 |
| 闭环与异常关闭 | OPEN | CLO-02唯一进入`NORMAL_CLOSED`，PM-10进入`EXCEPTION_CLOSED` |
| 外部事实边界 | OPEN | ERP商务事实权威、CRM上下文和非阻断依赖分层须进入Context与Owner复审 |
| 权限与工作流 | OPEN | 检查V1.8差量是否改变命令权限、冻结流程版本和数据范围，不从旧SDS推断新规则 |
| Stage—ProjectTask工作台 | OPEN | 按ADR-0029复核WorkBinding统一必填、TASK_NATIVE默认业务实体、其他绑定Owner、分类型完成规则、两级导航与任意深度任务树边界 |
| CUT-03同阶段工作台 | OPEN | 确认P1～P6不变，P3匹配/填写/采集回填不产生独立阶段、聚合或工单 |

## 2. 当前可用资产

- PRD V1.8、13领域需求和需求追溯矩阵可作为本轮重验证输入。
- `independent-review.md`、`context-refinement-review.md`、`naming-review.md`记录V1.7及更早版本的历史评审证据；未完成V1.8定点复审前不得作为当前GO结论。
- P3-E09和生产证据门禁不因PRD发布自动关闭或失效；数据模型是否受V1.8影响需在后续阶段单独判断。`AI-MIG-000`按具体Release范围条件适用，仅包含历史迁移或数据切换时才作为前置门禁。

## 3. 放行条件

1. V1.8的100项正式需求全部映射到唯一Owner和聚合；
2. ACC-05、COM-02、IMP-02在当前SDS中无活动对象、API、表、事件或流程残留；
3. 项目状态分层、正常闭环、异常关闭和外部事实边界完成差量审查；
4. 追溯、状态机、工作流和权限设计校验通过；
5. fresh-context独立复审给出GO。
6. ADR-0029的ProjectTask工作台和CUT-03同阶段边界在领域、状态、流程与权限设计中无冲突。

满足以上条件前，Phase 1保持`REVALIDATION_REQUIRED / NOT_READY_FOR_PHASE_2_V1.8`。
