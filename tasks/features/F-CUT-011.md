# F-CUT-011 割接备件系统协同

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY_REVIEW`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_READY`
> Requirement：`CUT-08@V2=FULL`
> Feature Spec：`specs/features/F-CUT-011-cutover-spare-system-coordination.md`
> 机器合同：`specs/features/F-CUT-011-api-contract.json`、`specs/features/F-CUT-011-physical-contract.json`
> 旧实现审计：`specs/features/F-CUT-011-legacy-reuse-audit.md`

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

基线cd2d6e04；当前Gate为F-CUT-011 Feature Ready规格候选。F-CUT-010保留给既有V1内容；CUT-08@V2由本Feature承接，COM及F-CUT-007～009不重做。阻断：独立评审未通过。下一步：校验规格/追溯后提交候选并送Feature Ready审批。
