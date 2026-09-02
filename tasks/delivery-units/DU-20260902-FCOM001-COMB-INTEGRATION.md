# DU-20260902-FCOM001-COMB-INTEGRATION COM-B权威增量集成

> DU状态：`RELEASED`
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
> 旧功能范围：`COM-B单线权威方案；已由Requirement合并方案替代，分支历史不删除`
> 验证：`COM相关Maven构建与聚焦测试;Flyway静态校验;Requirement追溯;Delivery Unit校验;五轴Code Review`
> 集成记录：`RELEASED / ZERO_CODE_MERGED；COM-B Task 1 cherry-pick已abort，后续只由Requirement合并DU选择性吸收非重复能力`

## 目标与边界

本DU曾按当时指令计划选择COM-B。需求方随后确认COM-A与COM-B实现的是不同需求，要求按Requirement整体合并。Git事实显示COM-B并未继承COM-A：`21423d9c`不是`c21745a9`、`codex/f-cut-001-matrices`或`codex/f-proj-008-stage-advance`的祖先，两线共同基线为`259b2612`。因此本DU停止且释放边界，不把两线任何完成证据相互转记。

COM-B Task 1的未完成cherry-pick已执行`git cherry-pick --abort`并恢复到`master@a11f95e2`，没有COM-B业务代码进入master。此前进入master的COM-B Spec/Task/Plan只保留为历史需求来源；后续不得以本DU继续写入，统一合并由新DU承接。

## 交接

- 最后提交：`ZERO_CODE_MERGED`
- 已完成：`中止COM-B代码接收并释放F-COM-001写边界`
- 剩余：`由DU-20260902-FCOM001-REQUIREMENT-CONVERGENCE按Requirement选择性接收COM-A与COM-B`
- 测试：`不适用；未形成代码差量`
- 已知失败：`无；cherry-pick冲突发生于全局追溯投影，已通过abort完整回退`
