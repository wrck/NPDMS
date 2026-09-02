# DU-20260902-FCOM001-COMB-INTEGRATION COM-B权威增量集成

> DU状态：`CLAIMED`
> DU类型：`FEATURE`
> Feature协调：`F-COM-001=FEATURE_EXCLUSIVE`
> Task范围：`COM-B已通过Gate的Task 1～4；排除Task 5候选18237796及后续未完成范围`
> Owner：`Codex本次master COM-B权威增量集成会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/master-governance/NPDMS`
> 认领基线：`4ba927d29e629ab828a0a9e4f12bf3041e0b627d`
> 认领提交：`SELF`
> 修改边界：`docs/decisions/open-questions.md;docs/design/02d-cross-context-contracts.md;docs/design/07-authorization-design.md;docs/design/09-database-design.md;docs/design/10-api-design.md;docs/design/12-integration-design.md;docs/design/16-exception-and-idempotency.md;docs/superpowers/plans/2026-09-02-f-com-001-com-b-authoritative-integration.md;specs/features/F-COM-001-*;tasks/features/F-COM-001.md;tasks/features/README.md;tasks/delivery-units/DU-20260902-FCOM001-COMB-INTEGRATION.md;tasks/delivery-units/README.md;pms-framework/pms-common/**;pms-module-commerce-api/**;pms-module-commerce/**;sql/migrations/**`
> 串行资源：`F-COM-001 Feature状态;COM公共契约;COM Flyway;迁移证据公共契约/Flyway;共享SDS;master追溯投影`
> 旧功能范围：`COM-A@codex/f-com-001-feature-ready及其在codex/f-acc-001-sds中的继承段，裁决为SUPERSEDED / DO_NOT_MERGE；分支历史不删除`
> 验证：`COM相关Maven构建与聚焦测试;Flyway静态校验;Requirement追溯;Delivery Unit校验;五轴Code Review`
> 集成记录：`PENDING；候选源为c21745a9..3e26a537，18237796保持NO-GO，f1cf7920已接收的PROJ资格契约不重复合并`

## 目标与边界

用户选择COM-B作为F-COM-001唯一后续权威实现。Git事实显示COM-B并未继承COM-A：`21423d9c`不是`c21745a9`、`codex/f-cut-001-matrices`或`codex/f-proj-008-stage-advance`的祖先，两线共同基线为`259b2612`。本DU据此记录“COM-B替代COM-A”，不把COM-A的Implementation Done候选、ACC消费证据或项目办事处快照语义转记到COM-B。

本轮只接收COM-B源线已通过Gate的Task 1～4及其必要平台依赖，允许`master`形成可构建的`INTEGRATED_PARTIAL`。原分支Task 5提交`18237796`已被独立复审判定`NO-GO / REVIEW_REQUIRED`，Task 6～8尚未形成完整实现，均不进入本轮。CUT/PROJ共享分支只作为提交来源证据，不按分支合并。

## 交接

- 最后提交：`PENDING`
- 已完成：`PENDING`
- 剩余：`Task 5项目交付范围命令整改；Task 6当前范围查询；Task 7工作台；Task 8整体验收与Implementation Done`
- 测试：`PENDING`
- 已知失败：`PENDING`
