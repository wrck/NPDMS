# DU-20260901-CUT-PROJ-OWNER-INTEGRATION CUT分支PROJ Owner支撑集成

> DU状态：`IN_PROGRESS`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`T-FCOM001-PROJ-01;T-FIMP002-PROJ-01;F-CUT-002项目上下文支撑合同;T-FCUT005-PROJ-01`
> Owner：`Codex本次master CUT-PROJ选择性集成会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/master-governance/NPDMS`
> 认领基线：`c8a2bb5c3a80f62148559391ea8173e7dc14dd84`
> 认领提交：`SELF`
> 修改边界：`pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/systemqualification/**;pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/deliveryscope/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/systemqualification/**;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/systemqualification/**;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/deliveryscope/**;specs/features/F-COM-001-project-qualification-contract.json;specs/features/F-CUT-002-project-context-fact-contract.json;specs/features/F-CUT-005-candidate-owner-contract.json;docs/decisions/0036-project-cutover-context-fact.md;docs/design/02d-cross-context-contracts.md;docs/design/05-state-machine.md;docs/design/07-authorization-design.md;docs/design/09-database-design.md;docs/design/10-api-design.md;tasks/delivery-units/**;tasks/features/README.md;docs/generated/branch-history-audit-2026-09-01-cut-proj-integration.md`
> 串行资源：`PROJ公共契约;SDS跨Context契约;master Feature任务矩阵`
> 旧功能范围：`NONE`
> 验证：`ProjectSystemQualification与ProjectDeliveryScopeQualification聚焦测试；pms-module-project编译；SDS与Delivery Unit校验；分支时间线重生成`
> 集成记录：`IN_PROGRESS；只选择性接收codex/f-cut-001-matrices中的PROJ Owner内容，不接收CUT/COM/IMP实现`

## 目标与边界

从`codex/f-cut-001-matrices@85b93828eb041db3b21611edf52b9180b673a5e0`按完整时间线选择性集成四个PROJ物理Owner支撑单元：项目系统资格锁、交付范围项目资格公共契约、项目割接上下文机器合同、割接服务经理候选机器合同。Requirement分别为`EXE-01`、`COM-01`、`CUT-01`、`CUT-05/PM-08`。

明确排除CUT、COM、IMP业务实现、SYSTEM候选Provider、共享Flyway和整支Feature状态。只有`ProjectSystemQualificationFactApi`在来源分支存在生产Provider；其他三项必须保留为合同或公共接口增量，不得产生Feature Done、生产装配或真实浏览器完成结论。

## 交接

- 最后提交：`NONE`
- 已完成：已按时间线识别四条PROJ Owner能力及最终文件边界。
- 剩余：选择性集成、聚焦验证、权威矩阵回写和集成提交。
- 测试：未开始。
- 已知失败：`ProjectDeliveryScopeQualificationFactApi`、`ProjectCutoverContextFactApi`、`ProjectCutoverServiceManagerFactApi`均无生产Provider；这是冻结的未完成事实，不以替身关闭。

## 集成回执

待本DU完成后由master协调者记录选中的来源提交、目标提交和验证结果；结论最多为`INTEGRATED_PARTIAL`，不改变四个消费Feature的Implementation Done状态。
