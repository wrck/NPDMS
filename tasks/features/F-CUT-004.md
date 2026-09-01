# F-CUT-004 P4割接方案编制与版本提交

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`TECHNICAL_PLAN`
> Feature Ready Gate：`READY / GO@644816f2`
> Technical Plan Gate：`REVIEW_REQUIRED`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-04@V1=FULL`
> Feature Spec：`specs/features/F-CUT-004-p4-cutover-plan-authoring.md`
> 机器合同：`specs/features/F-CUT-004-api-contract.json`、`specs/features/F-CUT-004-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`
> 旧实现审计：`specs/features/F-CUT-004-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-01-f-cut-004-p4-cutover-plan-authoring.md`

## 当前最小工作单元

- 唯一Technical Plan候选已形成，当前只申请独立Plan Gate，不写DDL或产品代码。
- 计划明确先以`src/test`受控替身完成CUT正向闭环；生产CUT-05/PLT Provider缺失继续阻断生产装配、浏览器和Implementation Done。

## Gate清单

- [x] 独立Feature边界裁决：CUT-04独立于CUT-05。
- [x] CUT-04完整义务、CUT-04/CUT-05双向交接与P4/P5/P6状态Owner合同通过（`87b0b066`）。
- [x] `pms_cut_plan`字段/状态/完整性与不可迁行处置通过（`87b0b066`）。
- [x] API/Physical/Legacy Machine Contract Gate通过（`87b0b066`）。
- [x] Feature Ready最终裁决通过（状态基线`644816f2`）。
- [ ] 唯一Technical Plan独立复审通过。

> 检查点：唯一Technical Plan候选已形成并进入`REVIEW_REQUIRED`；实施仍`NOT_STARTED`。下一Gate为该Plan独立复审；不重复COM、不写实现，跨模块正向链只允许`src/test`受控替身，生产依赖继续阻断装配、浏览器和Done。
