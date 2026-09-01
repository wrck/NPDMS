# DU-20260901-FINS001-MIGRATION F-INS-001 Task 4候选迁移

> DU状态：`INTEGRATION_CANDIDATE`
> DU类型：`TASK`
> Feature协调：`F-INS-001=TASK_COORDINATED`
> Task范围：`Task 4纯领域规则、受限正则校验与Secret扫描候选；Task 5及以后明确排除`
> Owner：`UNCONFIRMED`
> 分支：`feat-inspection-feature-xkjuCC`
> Worktree：`C:/Users/user/.trae-cn/worktrees/NPDMS/feat-inspection-feature-xkjuCC`
> 认领基线：`69d61514b969c3d275688804110e1f3e8c688d60`
> 认领提交：`NONE`
> 修改边界：`docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md;pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/**;pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/**;scripts/tests/test_fins001_legacy_preservation.py;scripts/tests/test_fins001_owner_and_query_boundary.py;scripts/tests/test_fins001_plan_and_scope.py;tasks/features/F-INS-001.md`
> 串行资源：`Feature Task;Technical Plan`
> 旧功能范围：`NONE`
> 验证：`20:04增量审计时分支HEAD e13feca79ba768234477315e2ccfe7ca54d4068c且工作树清洁；分支报告Task 4定向、模块、Reactor和package验证通过，仍待master独立复验`
> 集成记录：`NONE；e13feca7为Task 4独立候选，不产生Feature Done；Task 5须由master新建有效DU后实施`

## 审计结论

F-AST-002已是该分支的完成祖先；`6719ab94`形成F-INS正式链候选，`e13feca7`将Task 4实现和验证记录聚焦提交，工作树已清洁。相对`master@69d61514`的共同基线为`08457e39`：两侧实现文件交集为0；三方合并的3处文本冲突仅位于`docs/traceability/requirement-matrix.md`、`docs/traceability/requirement-version-coverage.json`和`specs/features/README.md`，内容为F-AST状态投影差异；共同出现的`V132__fcut001_matrix_contract.sql`blob完全一致。故F-INS不存在跨Feature实现冲突，`Q-GOV-20260901-001`只保留为全局PRD编号串行收口问题。

本DU只把`e13feca7`记录为可独立复核的Task 4候选，不倒签历史认领、不采信分支投影为master状态，也不允许整支合并。集成时从最新master选择性适配本DU边界，Feature Task与Technical Plan按master事实收口并重新执行适用验证；Task 5及以后必须另建有效DU。
