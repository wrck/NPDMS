# DU-20260908-CHANGE-SCOPED-REVIEW 取消阶段全量阻断

> DU状态：`IN_PROGRESS`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`用户已确认取消Phase 1/2阶段全量阻断，保留变更级实质审查；工程规则、审计入口与回归测试`
> Owner：`来源任务01a07a94-a002-7131-bf20-2ad931b6a0f7`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/79ed/NPDMS`
> 认领基线：`996d51503a275e82154801afdb7dc1fa671198a6`
> 认领提交：`SELF`
> 修改边界：`AGENTS.md;docs/engineering/00-engineering-chain.md;docs/README.md;docs/design/00-system-detailed-design.md;docs/engineering/gates/README.md;docs/engineering/gates/phase-1/README.md;docs/engineering/gates/phase-1/gate-status.md;docs/engineering/gates/phase-2/README.md;docs/engineering/gates/phase-2/gate-status.md;docs/decisions/open-questions.md;docs/superpowers/plans/2026-09-08-change-scoped-design-review.md;scripts/sds_gate_contract.py;scripts/validate_sds_phase1.py;scripts/validate_sds_phase2.py;scripts/validate_prd_revision_016_alignment.py;scripts/tests/test_sds_change_review.py;scripts/tests/test_sds_revision_016_gate_contract.py;scripts/tests/test_validate_sds_phase1.py;scripts/tests/test_validate_sds_phase2.py;scripts/tests/test_prd_revision_016_alignment.py;tasks/delivery-units/DU-20260908-CHANGE-SCOPED-REVIEW.md;tasks/delivery-units/README.md`
> 串行资源：`工程治理与Phase 1/2审计入口；不修改业务实现、数据库载体或CI运行门禁`
> 旧功能范围：`现有Phase 1/2检查由强制阶段准入改为可选全量审计；保留实质契约检查及全部历史证据`
> 验证：`默认不运行全量审计、不输出虚假批准；显式审计保留失败；租户/Owner/唯一性/历史/Schema证据负测与变更级流程回归`
> 集成记录：`先行登记认领，实施与测试结果待完成；不把取消门禁解释为既有缺口已修复`

## 已批准范围

需求方于2026-09-08明确确认“取消阶段全量阻断、保留变更级实质审查”。业务PRD不变，完整性、安全、历史保护和对应实际DDL/运行验证不取消。原验收DU已释放重叠文档；本任务只改变工程准入，不给未完成的验收实现签署Done。

## 执行

- [ ] 在已批准方向下记录一次性实施计划并同步权威工程规则。
- [ ] 调整Phase 1/2命令、跨阶段/全文身份耦合，保留显式审计与实质检查。
- [ ] 完成失败用例、聚焦回归及自审，按变更记录一次结果后本地提交。

日常变更的影响范围、修改依据、验证及审阅结果复用现有Task/DU/提交，不要求再建立独立状态清单或新Phase记录。
