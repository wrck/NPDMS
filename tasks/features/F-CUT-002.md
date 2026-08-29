# F-CUT-002 割接任务接入与人工分级

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY_BLOCKED`
> Feature Ready Gate：`NO-GO`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-01@V1=PARTIAL；CUT-02@V1=PARTIAL`
> Feature Spec：`specs/features/F-CUT-002-cutover-intake-and-manual-assessment.md`
> 硬依赖：`F-IMP-001`、`F-AST-002`、`F-PROJ-003`

## 当前最小工作单元

- 先锁定F-IMP-001、EXE-01～04、PROJ项目scope action和AST稳定设备范围的Owner Feature Spec/公开契约/合入顺序；本Feature不吸收IMP/PROJ/AST Owner实现。
- 将`pms_cut_task -> cut_task`字段/状态/完整性映射固化为正式可评审契约；`cut_assessment`保持`NEW_ONLY`。
- 上述设计输入关闭并通过Feature Ready后才生成现行Technical Plan。只有相关Feature通过Ready后才可用受控替身实施CUT侧；生产依赖合入前不声明真实浏览器闭环或Implementation Done。

## 已完成

- 接受独立裁决：EXE-06拆为独立IMP Feature；CUT-01与CUT-02覆盖均为`PARTIAL`。
- 已固定一线自建P1→P2问卷人工判级→A/B/C进入P3、D进入P4的最小业务闭环。
- 已明确来源幂等、活动设备范围唯一性、IMP快照重验、权限、API、数据和UI边界。
- 已完成本Feature的后端、前端、配置、运行数据/迁移、状态机、权限和测试复用审计；结论为`CURRENT_FORWARD / COPY_THEN_ENHANCE / PRESERVE_LEGACY`。
- 已纠正CUT物理Owner为`cut_task/cut_assessment`，并将`CutoverAssessment`与旧`pms_cut_risk`解耦为`NEW_ONLY`。
- ITR/项目事件Producer、P3以后、V2/V3、自动指派和通用工单动作均排除。

## 阻断

- F-IMP-001无生产Provider；EXE-01～04无权威完成事实。
- AST现有SN校验接口不足以冻结稳定设备ID和归属版本，且EQP-01当前无任务记录可派生Implementation Done。
- PROJ项目scope action、AST设备范围契约及`pms_cut_task -> cut_task`完整映射尚未通过正式评审。

## 验收分层

- CUT单元/集成测试可使用受控`ImplementationReadinessApi`替身验证消费边界。
- 真实MySQL和浏览器正向验收必须使用IMP生产Provider和AST权威设备事实；替身、手工SQL、附件或测试种子不得替代。
