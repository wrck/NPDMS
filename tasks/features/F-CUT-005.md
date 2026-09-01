# F-CUT-005 P5分级审批

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`TECHNICAL_PLAN`
> Feature Ready Gate：`READY / GO @ 2e3fdba3`
> Technical Plan Gate：`NOT_STARTED`
> Implementation Done Gate：`NOT_STARTED`
> Requirement：`CUT-05@V1=FULL`
> Feature Spec：`specs/features/F-CUT-005-p5-graded-approval.md`
> 机器合同：`specs/features/F-CUT-005-api-contract.json`、`specs/features/F-CUT-005-physical-contract.json`、`specs/features/F-CUT-005-approval-owner-contract.json`、`specs/features/F-CUT-005-candidate-owner-contract.json`
> 旧实现审计：`specs/features/F-CUT-005-legacy-reuse-audit.md`
> 唯一Technical Plan：`N/A（Feature Ready通过后生成）`

## 当前最小工作单元

- Feature Ready已在锁定提交`2e3fdba3`独立复审GO；当前生成唯一Technical Plan并送审，不进入实现。
- PROJ/SYSTEM生产候选Provider缺失不阻断受控替身规格与后续内核实现，但阻断生产完整装配、真实浏览器和Implementation Done。

## Gate清单

- [x] API/Physical/Candidate/Legacy Machine Contract Gate。
- [x] Feature Ready最终裁决：`GO @ 2e3fdba3`。
- [ ] 生成并通过唯一Technical Plan。

## 最近检查点

- `5e3ce44c`方向成立但Feature Ready未通过；不得进入Technical Plan或实现。
- `2efad8ce`已关闭动作判别、候选交集和通知边界；快照可空性与管理员改派入口仍需定点复审。
- `2e3fdba3`关闭剩余两项并获Feature Ready正式GO；最近Gate为唯一Technical Plan独立复审。
- 整改只改Feature/API/Physical/Candidate/Authorization等正式规格，不新增DDL、运行代码或测试实现。

## 物理Owner支撑Task

- `T-FCUT005-PROJ-01`：PROJ拥有`ProjectCutoverServiceManagerFactApi`公开事实、锁定实现和合入顺序；当前仅预留合同，Provider未实施。
- `T-FCUT005-SYSTEM-01`：SYSTEM拥有`CutoverApprovalRoleCandidateFactApi`角色成员事实、锁定实现和合入顺序；当前仅预留合同，未经明确授权不得修改Yudao基础模块。
- 两项Provider缺失不阻断Feature Ready后的CUT内核及`src/test`正向闭环，持续阻断完整生产装配、真实浏览器和Implementation Done。

## 依赖边界

- F-CUT-002/003/004为业务来源；当前允许在F-CUT-005单元/集成中使用已锁定合同的受控事实。
- PROJ当前服务经理与SYSTEM二线/研发候选均作为本Feature的物理Owner支撑Task预留正式端口，不建立纯Provider Feature；不得跨模块读表、修改Yudao或注册生产Fake/fallback。
- V2提前时间与外部通知、CUT-06闭环和`Q-FCUT004-001`均排除。
