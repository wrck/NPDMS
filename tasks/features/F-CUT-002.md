# F-CUT-002 割接任务接入与人工分级

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY_BLOCKED`
> Feature Ready Gate：`NO-GO`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_STARTED`
> `pms_cut_task -> cut_task` Migration Contract Gate：`PASS`（`36d1b37f`）
> API/Physical Machine Contract Gate：`REVIEW_REQUIRED`
> Requirement：`CUT-01@V1=PARTIAL；CUT-02@V1=PARTIAL`
> Feature Spec：`specs/features/F-CUT-002-cutover-intake-and-manual-assessment.md`
> 硬依赖：`F-IMP-001`、`F-PROJ-003`、`T-FIMP001-AST-01`

## 当前最小工作单元

- `02198211`独立复审已确认前六项关闭；当前只按方案B统一DRAFT可保存结构化IMP `NOT_READY`、SUBMITTED/INVALIDATED只允许`READY`的单点机器语义，再申请同一`API/Physical Machine Contract Gate`。
- 该Gate通过后分别推动`ImplementationReadinessApi`与`CustomerServiceLevelFactApi`公共机器合同，不在CUT重复实现Owner。
- 直接消费合同及F-CUT-002 Feature Ready通过后，使用受控正向模拟推进CUT自身P1→P2→P3/P4；生产依赖合入前不声明真实浏览器闭环或Implementation Done。

## 已完成

- 接受独立裁决：EXE-06拆为独立IMP Feature；CUT-01与CUT-02覆盖均为`PARTIAL`。
- 已固定一线自建P1→P2问卷人工判级→A/B/C进入P3、D进入P4的最小业务闭环。
- 已明确来源幂等、活动设备范围唯一性、IMP快照重验、权限、API、数据和UI边界。
- 已完成本Feature的后端、前端、配置、运行数据/迁移、状态机、权限和测试复用审计；结论为`CURRENT_FORWARD / COPY_THEN_ENHANCE / PRESERVE_LEGACY`。
- 已纠正CUT物理Owner为`cut_task/cut_assessment`，并将`CutoverAssessment`与旧`pms_cut_risk`解耦为`NEW_ONLY`。
- `pms_cut_task -> cut_task`字段、旧状态只读化、完整性资格和不可迁行处置的机器合同已在`36d1b37f`通过独立迁移Contract Gate；旧类型/组网仅存legacy raw，新路径事实保持空，Owner暂时失败与确定性不匹配分流，PLT批次事务和11组生成投影已锁定。
- 独立裁决已确认F-IMP-003～005未Ready不应永久阻断CUT；跨模块接口冻结后，Feature Ready可允许CUT在非生产装配中使用受控模拟完成自身正向闭环。
- ITR/项目事件Producer、P3以后、V2/V3、自动指派和通用工单动作均排除。

## 阻断

- F-CUT-002完整API/物理机器合同在`02198211`独立复审仍为NO-GO，但前六项均已关闭；最近Gate仅剩DRAFT IMP就绪上下文可空态的单点整改复审。
- AST的`DeviceScopeFactApi`公开合同、Owner Provider及IMP消费适配已分别通过独立Gate；该项不再是F-CUT-002规格阻断，但生产装配与真实依赖闭环仍按各Owner任务状态判定。
- `ImplementationReadinessApi`与CUS `CustomerServiceLevelFactApi`尚无通过Gate的独立公共机器合同；生产Provider继续只阻断生产装配、真实浏览器和Implementation Done，不授权CUT重复建设。

## 验收分层

- CUT单元/集成测试可使用受控`ImplementationReadinessApi`替身验证消费边界。
- 真实MySQL和浏览器正向验收必须使用IMP生产Provider和AST权威设备事实；替身、手工SQL、附件或测试种子不得替代。
