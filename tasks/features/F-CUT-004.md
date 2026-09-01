# F-CUT-004 P4割接方案编制与版本提交

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`TECHNICAL_PLAN`
> Feature Ready Gate：`READY / GO@644816f2`
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
- [x] CUT-04完整义务、CUT-04/CUT-05双向交接与P4/P5/P6状态Owner合同通过（`87b0b066`）。
- [x] `pms_cut_plan`字段/状态/完整性与不可迁行处置通过（`87b0b066`）。
- [x] API/Physical/Legacy Machine Contract Gate通过（`87b0b066`）。
- [x] Feature Ready最终裁决通过（状态基线`644816f2`）。

> 检查点：Feature Ready以状态基线`644816f2`独立裁决`READY/GO`；实施仍`NOT_STARTED`。当前阶段进入`TECHNICAL_PLAN`，下一Gate为唯一Technical Plan独立复审；不重复COM、不写实现，跨模块正向链仅允许未来`src/test`受控替身。
