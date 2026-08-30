# F-CUT-002 割接任务接入与人工分级

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`TECHNICAL_PLAN`
> Feature Ready Gate：`READY / GO`（锁定基线`cad8088a`）
> Technical Plan Gate：`PASS / GO`（锁定提交`14440e45`）
> Implementation Done Gate：`NOT_STARTED`
> `pms_cut_task -> cut_task` Migration Contract Gate：`PASS`（`36d1b37f`）
> API/Physical Machine Contract Gate：`PASS / b7f49166`
> Requirement：`CUT-01@V1=PARTIAL；CUT-02@V1=PARTIAL`
> Feature Spec：`specs/features/F-CUT-002-cutover-intake-and-manual-assessment.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-08-31-f-cut-002-cutover-intake-and-manual-assessment.md`
> 硬依赖：`F-IMP-001`、`F-PROJ-003`、`T-FIMP001-AST-01`

## 当前最小工作单元

- `API/Physical Machine Contract Gate`已在`b7f49166`通过；`ImplementationReadinessApi Public Machine Contract Gate`已在`38fc0d9d`独立复审`PASS / GO`，只冻结IMP Owner公开消费接口，不实现Provider。
- `CustomerServiceLevelFactApi Public Machine Contract Gate`已在`64e3dbbd`独立复审`PASS / GO`：只冻结CUS API/DTO/公共失败和机器合同，不实现Provider，不在CUT重复实现Owner。
- `F-CUT-002 Feature Ready`已在锁定基线`cad8088a`独立复审`PASS / GO`。
- `NPDMS-FCUT002-TECHPLAN-20260831-01`已在锁定提交`14440e45`独立最小整改复审`PASS / GO`。当前最近工程单元为Task 1“后端、数据与正向业务链”；Task 1完整正向实现后再执行正向验证，不新增中间独立Gate。

## 已完成

- 接受独立裁决：EXE-06拆为独立IMP Feature；CUT-01与CUT-02覆盖均为`PARTIAL`。
- 已固定一线自建P1→P2问卷人工判级→A/B/C进入P3、D进入P4的最小业务闭环。
- 已明确来源幂等、活动设备范围唯一性、IMP快照重验、权限、API、数据和UI边界。
- 已完成本Feature的后端、前端、配置、运行数据/迁移、状态机、权限和测试复用审计；结论为`CURRENT_FORWARD / COPY_THEN_ENHANCE / PRESERVE_LEGACY`。
- 已纠正CUT物理Owner为`cut_task/cut_assessment`，并将`CutoverAssessment`与旧`pms_cut_risk`解耦为`NEW_ONLY`。
- `pms_cut_task -> cut_task`字段、旧状态只读化、完整性资格和不可迁行处置的机器合同已在`36d1b37f`通过独立迁移Contract Gate；旧类型/组网仅存legacy raw，新路径事实保持空，Owner暂时失败与确定性不匹配分流，PLT批次事务和11组生成投影已锁定。
- 独立裁决已确认F-IMP-003～005未Ready不应永久阻断CUT；跨模块接口冻结后，Feature Ready可允许CUT在非生产装配中使用受控模拟完成自身正向闭环。
- ITR/项目事件Producer、P3以后、V2/V3、自动指派和通用工单动作均排除。

## 依赖边界

- F-CUT-002完整API/物理机器合同已通过，不再阻断Feature Ready。
- AST的`DeviceScopeFactApi`公开合同、Owner Provider及IMP消费适配已分别通过独立Gate；该项不再是F-CUT-002规格阻断，但生产装配与真实依赖闭环仍按各Owner任务状态判定。
- `ImplementationReadinessApi`与CUS `CustomerServiceLevelFactApi`公共机器合同均已通过。生产Provider继续只阻断生产装配、真实浏览器和Implementation Done，不授权CUT重复建设。

## 验收分层

- CUT单元/集成测试可使用受控`ImplementationReadinessApi`替身验证消费边界。
- 真实MySQL和浏览器正向验收必须使用IMP生产Provider和AST权威设备事实；替身、手工SQL、附件或测试种子不得替代。
