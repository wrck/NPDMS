# F-CUT-004 P4割接方案编制与版本提交

> Feature实施状态：`IN_PROGRESS`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`READY / GO@644816f2`
> Technical Plan Gate：`PASS / GO@9ef7545d`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-04@V1=FULL`
> Feature Spec：`specs/features/F-CUT-004-p4-cutover-plan-authoring.md`
> 机器合同：`specs/features/F-CUT-004-api-contract.json`、`specs/features/F-CUT-004-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`
> 旧实现审计：`specs/features/F-CUT-004-legacy-reuse-audit.md`
> 唯一Technical Plan：`docs/superpowers/plans/2026-09-01-f-cut-004-p4-cutover-plan-authoring.md`

## 当前最小工作单元

- 当前进入Task 1，只建立F-CUT-005审批消费Java合同、公共DTO/异常和`src/test`受控实现，不实现审批节点、待办或生产Provider。
- 后续按计划先完成每个Task最小正向实现，再补正向验证；生产CUT-05/PLT Provider缺失继续阻断生产装配、浏览器和Implementation Done。

## Gate清单

- [x] 独立Feature边界裁决：CUT-04独立于CUT-05。
- [x] CUT-04完整义务、CUT-04/CUT-05双向交接与P4/P5/P6状态Owner合同通过（`87b0b066`）。
- [x] `pms_cut_plan`字段/状态/完整性与不可迁行处置通过（`87b0b066`）。
- [x] API/Physical/Legacy Machine Contract Gate通过（`87b0b066`）。
- [x] Feature Ready最终裁决通过（状态基线`644816f2`）。
- [x] 唯一Technical Plan独立复审通过（`9ef7545d`）。

## Task 1：CUT-05审批消费Java合同

状态：`IN_PROGRESS`

- [ ] 实现`CutoverApprovalFactApi`、精确Command/Query/Fact/Result records与稳定公共异常。
- [ ] 在`src/test`提供确定性受控审批事实实现并补实现后的合同测试。
- [ ] 通过独立Contract/Code Review Gate；不注册生产审批Bean。

> 检查点：Technical Plan以`9ef7545d`独立复审`PASS/GO`，Feature实施进入`IN_PROGRESS`。当前只执行Task 1审批消费Java合同；不重复COM，生产审批业务及完整装配仍不在本Task。
