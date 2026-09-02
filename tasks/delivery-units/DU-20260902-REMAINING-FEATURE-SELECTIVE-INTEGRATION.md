# DU-20260902-REMAINING-FEATURE-SELECTIVE-INTEGRATION 剩余Feature选择性集成

> DU状态：`CLAIMED`
> DU类型：`MULTI_FEATURE_SLICE`
> Feature协调：`F-INS-001=TASK_COORDINATED;F-CUT-002=FEATURE_EXCLUSIVE;F-CUT-003=FEATURE_EXCLUSIVE;F-CUT-004=FEATURE_EXCLUSIVE;F-CUT-005=FEATURE_EXCLUSIVE;F-CUT-006=FEATURE_EXCLUSIVE;F-CUT-007=FEATURE_EXCLUSIVE;F-CUT-008=FEATURE_EXCLUSIVE;F-CUT-009=FEATURE_EXCLUSIVE;F-CUT-010=FEATURE_EXCLUSIVE;F-PROJ-008=TASK_COORDINATED`
> Task范围：`复核feat-inspection-feature-xkjuCC、codex/f-cut-001-matrices与codex/f-proj-008-stage-advance的已提交时间线；选择性集成可构建且不越过当前规格Gate的Feature增量；不接收来源工作树未提交改动、重复祖先、生成投影、未裁决业务语义或生产替身`
> Owner：`Codex本次master剩余Feature选择性集成会话`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/master-governance/NPDMS`
> 认领基线：`75b1f43dabd14461b2d34b56f891f81bad88952a`
> 认领提交：`SELF`
> 修改边界：`docs/baseline/**;docs/decisions/**;docs/design/**;docs/engineering/**;docs/superpowers/plans/*f-ins-001*;docs/superpowers/plans/*f-cut-00*;docs/traceability/**;specs/001-project-delivery-platform/domains/SRV-*;specs/001-project-delivery-platform/domains/CUT-*;specs/features/F-INS-001*;specs/features/F-CUT-00*;tasks/features/F-INS-001.md;tasks/features/F-CUT-00*.md;tasks/features/README.md;tasks/delivery-units/DU-20260902-REMAINING-FEATURE-SELECTIVE-INTEGRATION.md;tasks/delivery-units/README.md;pms-module-service/**;pms-module-cutover-api/**;pms-module-cutover/**;pom.xml;sql/migrations/V17*.sql;sql/migrations/V18*.sql;sql/migrations/V19*.sql;scripts/**;yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/**;yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/**`
> 串行资源：`PRD修订号;F-INS-001与F-CUT-002～010正式规格;Flyway V173起连续编号;CUT公开API模块;Requirement追溯投影`
> 旧功能范围：`F-INS-001和F-CUT-002～010规格均要求PRESERVE_EXISTING；旧巡检规则、旧cut-task/cut-risk/cut-plan页面、接口和数据不作为新实现基础，也不在未形成完整替代闭环前标记废弃或删除`
> 验证：`分支提交时间线与patch边界;Feature/Task/Gate一致性;数据库查询规范;迁移静态合同;INS与CUT聚焦测试及受影响Reactor构建;CUT前端定向测试/类型检查/构建;git diff --check;五轴代码审查`
> 集成记录：`NONE；F-PROJ-008 Task 3继续由Q-FPROJ-009阻断，不迁入代码；其余范围待本DU验证后按Feature切片登记`

## 初始裁决

- `feat-inspection-feature-xkjuCC@7fe168af`只接收F-INS-001的正式规格、Task 4～7以及Task 8停用和内部发布CAS基础；F-AST-002已由`master@524a70e7`接收，不重复导入。`Q-FINS001-005/006`继续阻断安全审核生产入口、完整发布放行与Implementation Done。
- `codex/f-cut-001-matrices@faed8387`仍禁止整支合并；只按F-CUT-002～010最终Feature边界接收CUT自有模块、公开合同、测试、UI和前向迁移。生产Owner/Fake不得由来源测试替身替代，F-CUT-010仅接收已完成Task 1～3。
- `codex/f-proj-008-stage-advance@48175aa0`的Task 1/2已由`master@db876b43/158118d0`接收；Task 3的UI候选虽有局部构建证据，但新建项目S0→S1正向验收仍依赖`Q-FPROJ-009`，本DU不迁入`a3bd0043`。
- 来源分支Flyway V146～V161及V148～V150均与master冲突，只保留历史映射，进入master时从V173起重新串行；来源生成矩阵和Feature完成自报不得覆盖master权威投影。

## 完成口径

- 允许`master`包含已通过聚焦验证、可构建但Feature尚未Done的增量；Task文件必须保留真实`IN_PROGRESS / IMPLEMENTED_WITH_CONTROLLED_SUBSTITUTES / BLOCKED_* / NOT_READY`状态。
- 本DU不产生任何Feature Ready或Implementation Done裁决；最终状态只记录已接收代码边界、未接收原因、主干迁移编号和验证结果。
- 全部切片提交并更新权威矩阵后，将本DU置为`INTEGRATED_PARTIAL`并释放写边界。
