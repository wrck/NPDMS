# DU-20260908-TEMPLATE-ACCEPTANCE-CONTRACT-CANDIDATE 独立验收接口与物理契约候选

> DU状态：`CLAIMED`
> DU类型：`GOVERNANCE`
> Feature协调：`F-PROJ-001=TASK_COORDINATED;F-PROJ-007=TASK_COORDINATED;F-PROJ-008=TASK_COORDINATED;F-ACC-001=TASK_COORDINATED;F-COM-001=TASK_COORDINATED`
> Task范围：`书面设计确认后的Q-TPLACC-001具体契约候选及计划准入判断；不实施应用代码`
> Owner：`审计模板能力演进设计任务01a07a94-a002-7131-bf20-2ad931b6a0f7`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/79ed/NPDMS`
> 认领基线：`3abdeb1ea50acca70eb7c6e1b256dab8a2c1c76f`
> 认领提交：`SELF`
> 修改边界：`docs/superpowers/specs/2026-09-08-template-acceptance-phase2-contract-design.md;docs/superpowers/specs/2026-09-08-template-acceptance-phase2-contract.json;docs/decisions/open-questions.md;docs/engineering/gates/phase-2/gate-status.md;tasks/delivery-units/DU-20260908-TEMPLATE-ACCEPTANCE-CONTRACT-CANDIDATE.md;tasks/delivery-units/README.md`
> 串行资源：`Q-TPLACC-001候选与Phase 2审阅输入；不占用业务代码或Flyway`
> 旧功能范围：`仅读取既有验收/范围/工作绑定及迁移作为复用证据；不修改旧实现或历史数据`
> 验证：`接口类型/字段引用一致；候选JSON可解析；空范围/重复触发/版本/锁序/旧路径边界自审；认领及差异范围检查`
> 集成记录：`NONE`

## 输出与边界

- 上一文档DU已由本任务交接，书面逻辑设计于2026-09-08获需求方确认。
- 产出ACC独立创建/完成/事实、COM阶段无关范围快照、范围变化回调、来源与规则冻结、字段/唯一键/约束及权限/事务的可审阅候选。
- 只选择真实存量可复用能力；新接口明确标为候选，不假设现有方法已有所需字段。scopeVersion不替代allocationVersion，文件scopeVersion不替代业务范围版本。
- 当前SDS Gate仍REVALIDATION_REQUIRED；不以书面确认或候选自审直接关闭Q、Phase、Feature Ready/Done，不生成绕过前置的可执行代码计划。
- Q仅限制独立验收及其接入，不阻断不依赖它的模板基础配置；具体业务实现、DDL、生产迁移和推送均不在本DU范围。

## 交接

- 最后提交：`NONE`
- 已完成：确认现有接口、范围读取/绑定、BPM事件及新旧身份的差量来源。
- 剩余：候选文档、结构化契约、自审和审阅输入登记。
- 运行验证：未运行应用或数据库迁移。
