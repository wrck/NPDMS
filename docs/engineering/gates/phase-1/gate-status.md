# SDS Phase 1 Review

> 审查状态：`REVALIDATION_REQUIRED`<br>
> 当前依据：PRD V1.8修订001—012、014—015正式基线（PRD Blob `37c709bf49ce3813042b650eea44050537cec4e0`）<br>
> 上次批准：PRD V1.8修订007，`APPROVED / READY_FOR_PHASE_2_V1.8`（历史证据）<br>
> 当前结论：`BLOCKED_BY_PRD_DELTA`<br>
> 机器门禁：`NOT_RUN_FOR_REVISION_015`<br>
> 需求方批准：`PENDING_DELTA_REVIEW`<br>
> 适用修订：`PRD_V1.8_REVISION_015`

## 1. 状态说明

修订008—015未改变100项Requirement和111个目标版本切片数量，但已改变领域、状态、工作流、范围和验收语义。修订007的Phase 1批准继续作为历史证据，不再代表当前PRD全部语义已完成Phase 1复核。受影响范围在差量复核完成前不得据旧结论进入新的Feature Ready、Implementation Done或Release覆盖声明。

差量只重开受影响范围；与下列变更无关的既有设计和Feature事实不自动撤销，但不得据此声称整个Phase 1已适配修订015。

## 2. 必须重验证的Phase 1契约

| 范围 | 当前状态 | 关闭条件 |
|---|---|---|
| S0～S6与模板阶段图 | REVALIDATION_REQUIRED | 区分业务阶段与工程Phase；只实例化模板关系图中的阶段，不建立`APPLICABLE/NOT_APPLICABLE`状态；推进规则有唯一Owner |
| 通用交付配置 | REVALIDATION_REQUIRED | 明确Stage/Task/Transition/Deliverable/WorkBinding/BusinessView/CompletionRule/PermissionPolicy聚合边界和注册表Owner |
| 项目状态与闭环 | REVALIDATION_REQUIRED | 明确`ACTIVE / NORMAL_CLOSED / NO_TRACKING_CLOSED / EXCEPTION_CLOSED`、`closure_type`、`closed_from_stage`和CLO-02/PM-10唯一Writer |
| S5/S6边界 | REVALIDATION_REQUIRED | 模板含S5才要求终验；S6不是统一闭环前置；不得补造未执行阶段或验收事实 |
| PM-06范围追加 | REVALIDATION_REQUIRED | 建模为同一活动项目内合同、订单行和实施范围追加；明确ProjectScopeVersion、跨阶段影响和终态项目边界 |
| RPT-02和流程全集 | REVALIDATION_REQUIRED | 全状态、真实阶段、超期、三类终态、两套闭环率及8个关键子流程进入领域、状态机、工作流和权限设计 |
| CUT/BPM/COM差量 | REVALIDATION_REQUIRED | 97项检查口径、BPM定义身份、范围地点/ERP Owner及全范围割接聚合与PRD一致 |

## 3. 关闭动作

1. 更新受影响的领域模型、上下文图、聚合边界、Owner矩阵、跨Context契约、模块、状态机、工作流和授权设计；
2. 为每个受影响Requirement形成“旧语义—新语义—设计缺口—处理Feature”映射；
3. 确认没有把RPT-02或第13.1章缩成仅闭环摘要或单一路径；
4. 运行适用Phase 1校验并完成语义复审；
5. 将证据写入本文件后，才可恢复`READY_FOR_PHASE_2_V1.8`。

## 4. 当前放行边界

当前不批准受影响范围进入新的Phase 2定稿、Feature Ready、DDL、实现或发布。与差量无关的独立工作可以继续，但必须显式标注不依赖上述契约。本状态不撤销历史Git、测试或Feature Done事实，也不把历史事实自动提升为修订015覆盖。
