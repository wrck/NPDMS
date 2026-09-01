# F-CUT-004 P4割接方案编制与版本提交

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY`
> Feature Ready Gate：`NOT_READY / REVIEW_REQUIRED`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-04@V1=FULL`
> Feature Spec：`specs/features/F-CUT-004-p4-cutover-plan-authoring.md`
> 机器合同：`specs/features/F-CUT-004-api-contract.json`、`specs/features/F-CUT-004-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`
> 旧实现审计：`specs/features/F-CUT-004-legacy-reuse-audit.md`

## 当前最小工作单元

- 完成API/Physical/Legacy机器合同Gate，不写Technical Plan、DDL或产品代码。
- 受控替身只用于未来`src/test`正向闭环；生产CUT-05/PLT Provider缺失继续阻断生产装配、浏览器和Implementation Done。

## Gate清单

- [x] 独立Feature边界裁决：CUT-04独立于CUT-05。
- [ ] CUT-04完整义务、CUT-04/CUT-05双向交接与P4/P5/P6状态Owner合同通过。
- [ ] `pms_cut_plan`字段/状态/完整性与不可迁行处置通过。
- [ ] API/Physical/Legacy Machine Contract Gate通过。
- [ ] Feature Ready最终裁决通过。

> 检查点：`9c13fcf3`复审C/E及D双事实已关闭，仅联系人PATCH版本Owner仍NO-GO；本轮统一由方案根`If-Match`先CAS递增，再同事务更新人员行与审计。当前仍为机器合同单点复审；不重复COM、不生成Technical Plan或实现，跨模块正向链仅允许未来`src/test`受控替身。
