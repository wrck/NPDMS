# F-CUT-010 割接备件系统协同

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY_REVIEW`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-08@V2=FULL`
> Feature Spec：`specs/features/F-CUT-010-cutover-spare-system-coordination.md`
> 机器合同：`specs/features/F-CUT-010-api-contract.json`、`specs/features/F-CUT-010-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-010-legacy-reuse-audit.md`

## 当前最小工作单元

- 形成CUT-08完整纵向Feature：需求识别、外部申请发起/跳转、引用与状态回填、人工证据、P5展示共同承接，不拆成Provider或单页碎片。
- INT-06只预留生产端口；CUT实施阶段以`src/test`受控替身完成正常正向闭环，不实现第三方、COM或Yudao。
- 当前只完成规格、机器合同、复用审计、追溯和Feature Ready送审；GO前不生成Technical Plan或实现。

## Gate清单

- [ ] API/Physical/Legacy/SDS Machine Contract Gate。
- [ ] Feature Ready独立复审。
- [ ] 唯一Technical Plan独立复审。
- [ ] CUT领域、Schema、应用/REST、UI、受控MySQL正向闭环各Task Gate。
- [ ] 生产INT-06 Provider、唯一装配、真实浏览器与Implementation Done。

## Phase-switch checkpoint

基线ee632ba5；当前Gate为F-CUT-010 Feature Ready A-D整改复审。已补首次引用绑定、P5 FULL安全摘要、INT-06精确DTO及Phase 2/迁移生成源。阻断：整改尚未取得独立GO。下一步：提交锁定候选并送最小复审，GO前不生成Technical Plan或实现。
