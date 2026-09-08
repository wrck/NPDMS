# DU-20260908-CHANGE-SCOPED-REVIEW 取消阶段全量阻断

> DU状态：`HANDOFF_READY`
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
> 集成记录：`工程规则和审计入口迁移已实现；169项聚焦回归通过，附加Phase 3仅保留旧失败；本地提交后做本变更实质复核，不申请整阶段批准`

## 已批准范围

需求方于2026-09-08明确确认“取消阶段全量阻断、保留变更级实质审查”。业务PRD不变，完整性、安全、历史保护和对应实际DDL/运行验证不取消。原验收DU已释放重叠文档；本任务只改变工程准入，不给未完成的验收实现签署Done。

## 执行

- [x] 在已批准方向下记录一次性实施计划并同步权威工程规则。
- [x] 调整Phase 1/2命令、跨阶段/全文身份耦合，保留显式审计与实质检查。
- [x] 完成失败用例、聚焦回归及自审，按变更记录一次结果后本地提交。

日常变更的影响范围、修改依据、验证及审阅结果复用现有Task/DU/提交，不要求再建立独立状态清单或新Phase记录。

## 本次验证与限制

- 新入口8项用例先RED后GREEN；五组聚焦工具回归共169项通过。保留并验证租户非空、Owner、唯一性、跨Owner外键、历史/快照和Schema实际内容漂移等拒绝路径；原阶段审批/措辞断言按已批准政策替换。
- 默认Phase 1/2命令均exit 0并明确NOT_RUN，不调用全量验证、不输出PASS。显式Phase 1审计exit 0/AUDIT-PASS；Phase 2审计exit 1，仍报告9项已有旧契约文案诊断及1项旧PRD身份诊断，未伪报全量通过。
- 9项旧文案诊断用认领前`c4165671`中的原检查代码复现。对应旧契约负测使用声明明确的正向fixture再逐项破坏，不再假定当前修订018文案必须符合旧阶段契约；真实仓库诊断仍由显式审计报告。
- 附加Phase 3回归21/22通过，唯一失败是当前仓库基线审计存在6项既有诊断；修改前代码同输入有7项（另含已解除的全文PRD身份耦合），新引入为0。没有修改Phase 3分册、物理合同或CI来消除旧结果。
- PRD三个静态断言改为识别已批准的修订018等价表述，并增加失败结论/第二当前报告拒绝的正向基线及负测，未修改PRD或放宽业务规则。
- 未运行应用、Maven、浏览器、DDL、MySQL或生产操作；CI文件、验收R2合同、业务实现、物理资产和原历史结果保持。原PR1 DU行尾差异不暂存。
