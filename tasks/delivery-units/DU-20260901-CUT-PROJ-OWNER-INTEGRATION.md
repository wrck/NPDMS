# DU-20260901-CUT-PROJ-OWNER-INTEGRATION CUT分支PROJ Owner支撑集成

> DU状态：`INTEGRATED_PARTIAL`
> DU类型：`GOVERNANCE`
> Feature协调：`NONE`
> Task范围：`T-FCOM001-PROJ-01;T-FIMP002-PROJ-01;F-CUT-002项目上下文支撑合同;T-FCUT005-PROJ-01`
> Owner：`Codex本次master CUT-PROJ选择性集成会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/master-governance/NPDMS`
> 认领基线：`c8a2bb5c3a80f62148559391ea8173e7dc14dd84`
> 认领提交：`SELF`
> 修改边界：`pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/systemqualification/**;pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/deliveryscope/**;pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/api/systemqualification/**;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/systemqualification/**;pms-module-project/src/test/java/cn/iocoder/yudao/module/pms/project/api/deliveryscope/**;specs/features/F-COM-001-project-qualification-contract.json;specs/features/F-CUT-002-project-context-fact-contract.json;specs/features/F-CUT-005-candidate-owner-contract.json;docs/decisions/0036-project-cutover-context-fact.md;docs/design/02d-cross-context-contracts.md;docs/design/05-state-machine.md;docs/design/07-authorization-design.md;docs/design/09-database-design.md;docs/design/10-api-design.md;scripts/generate_requirement_traceability.py;docs/traceability/requirement-matrix.md;docs/traceability/requirement-version-coverage.json;tasks/delivery-units/**;tasks/features/README.md;docs/generated/branch-history-audit-2026-09-01-cut-proj-integration.md`
> 串行资源：`PROJ公共契约;SDS跨Context契约;master Feature任务矩阵`
> 旧功能范围：`NONE`
> 验证：`ProjectSystemQualification与ProjectDeliveryScopeQualification聚焦测试；pms-module-project编译；SDS与Delivery Unit校验；分支时间线重生成`
> 集成记录：`codex/f-cut-001-matrices的四组PROJ Owner carve-out已选择性进入master@5f5148a9/f1cf7920/e2f51762；其余CUT/COM/IMP/SYSTEM内容未接收`

## 目标与边界

从`codex/f-cut-001-matrices@85b93828eb041db3b21611edf52b9180b673a5e0`按完整时间线选择性集成四个PROJ物理Owner支撑单元：项目系统资格锁、交付范围项目资格公共契约、项目割接上下文机器合同、割接服务经理候选机器合同。Requirement分别为`EXE-01`、`COM-01`、`CUT-01`、`CUT-05/PM-08`。

明确排除CUT、COM、IMP业务实现、SYSTEM候选Provider、共享Flyway和整支Feature状态。只有`ProjectSystemQualificationFactApi`在来源分支存在生产Provider；其他三项必须保留为合同或公共接口增量，不得产生Feature Done、生产装配或真实浏览器完成结论。

## 交接

- 最后提交：`5f5148a9122c15acaf54682bfa6646aa3aa0501f`、`f1cf7920bcfc8da2c37ead6b281b75cf374cd447`、`e2f51762b544e8ec9631a177c092493ca470d9eb`。
- 已完成：按完整分支时间线拆出并集成项目系统资格锁的公共API、生产Provider与测试；交付范围项目资格公共API、机器合同与契约测试；项目割接上下文ADR/机器合同；割接服务经理候选机器合同。SDS和Requirement追溯已从master权威输入重生成。
- 剩余：`ProjectDeliveryScopeQualificationFactApi`生产Provider、`ProjectCutoverContextFactApi`公共Java接口及生产Provider、`ProjectCutoverServiceManagerFactApi`公共Java接口及生产Provider；四个消费Feature仍须各自建立有效DU并按其权威Task完成。
- 测试：master聚焦Maven Reactor成功，3个测试类共16项（0失败、0错误、0跳过）；SDS Phase 1、追溯生成检查、JSON解析、Delivery Unit校验和分支时间线生成通过。
- 已知失败：master未配置Compose要求的`NPDMS_DB_USER`、`NPDMS_DB_PASSWORD`、`NPDMS_MYSQL_ROOT_PASSWORD`，因此未重复运行真实MySQL测试；来源提交`f4aa1ad2`中的MySQL结果仅保留为来源证据，不晋级为本次master运行证据。后三项缺失Provider是冻结的未完成事实，不以替身关闭。

## 集成回执

- `ProjectSystemQualificationFactApi`：来源`b4f16bdf`、`f4aa1ad2`，进入`master@5f5148a9`；公共API、生产Provider及聚焦测试已集成。
- `ProjectDeliveryScopeQualificationFactApi`：来源`9d029976`、`319a616e`、`86ea27de`，进入`master@f1cf7920`；只集成公共API、机器合同及契约测试，无生产Provider。
- `ProjectCutoverContextFactApi`：来源`e68ad4e0`、`f04650b6`、`17c826e1`、`5d334050`、`15c25e89`、`8eb36222`，进入`master@e2f51762`；只集成ADR和机器合同，无公共Java接口或生产Provider。
- `ProjectCutoverServiceManagerFactApi`：来源`5e3ce44c`、`2efad8ce`、`2e3fdba3`、`d990c205`、`912d0cdb`，进入`master@e2f51762`；只集成候选Owner机器合同，无公共Java接口或生产Provider。
- 结论：`INTEGRATED_PARTIAL`。该结论只释放本DU写边界，不改变F-COM-001、F-IMP-002、F-CUT-002、F-CUT-005的Implementation Done状态，也不解除来源分支其余内容的隔离。
