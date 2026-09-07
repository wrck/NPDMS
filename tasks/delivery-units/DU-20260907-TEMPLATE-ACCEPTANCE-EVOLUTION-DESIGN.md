# DU-20260907-TEMPLATE-ACCEPTANCE-EVOLUTION-DESIGN 模板业务规则与终验解耦设计

> DU状态：`CLAIMED`
> DU类型：`GOVERNANCE`
> Feature协调：`F-PROJ-001=TASK_COORDINATED;F-PROJ-007=TASK_COORDINATED;F-PROJ-008=TASK_COORDINATED;F-ACC-001=TASK_COORDINATED;F-COM-001=TASK_COORDINATED`
> Task范围：`用户已确认的模板演进设计及正式规格差量；只改文档与其派生投影，不实施应用功能`
> Owner：`审计模板能力演进设计任务01a07a94-a002-7131-bf20-2ad931b6a0f7`
> 分支：`master`
> Worktree：`M:/AICoding/CodexData/worktrees/79ed/NPDMS`
> 认领基线：`4fa4e3861326ce1154cfd6d14323feb23a3baccd`
> 认领提交：`SELF`
> 修改边界：`docs/baseline/prd-v1.8.md;docs/baseline/prd-v1.8-amendment-018-template-business-rules-and-acceptance.md;需求/PRD-项目实施交付管理平台.md;docs/engineering/00-engineering-chain.md;docs/design/02-domain-model.md;docs/design/02b-aggregate-boundary-decisions.md;docs/design/02c-data-ownership-matrix.md;docs/design/02d-cross-context-contracts.md;docs/design/04-module-design.md;docs/design/05-state-machine.md;docs/design/06-workflow-design.md;docs/design/07-authorization-design.md;docs/design/08-data-model.md;docs/design/09-database-design.md;docs/design/10-api-design.md;docs/design/11-event-design.md;docs/design/16-exception-and-idempotency.md;docs/design/20-test-design.md;docs/decisions/0045-template-business-rules-and-acceptance.md;docs/decisions/open-questions.md;docs/superpowers/specs/2026-09-07-template-acceptance-evolution-design.md;docs/engineering/gates/phase-1/gate-status.md;docs/engineering/gates/phase-2/gate-status.md;docs/engineering/gates/phase-3/gate-status.md;specs/features/F-PROJ-001-manual-project-creation-and-template-initialization.md;specs/features/F-PROJ-007-project-task-tree-and-native-workbench.md;specs/features/F-PROJ-008-project-stage-gate-and-forward-advance.md;specs/features/F-ACC-001-acceptance-report-version-and-deliverable-sync.md;specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md;specs/features/README.md;specs/001-project-delivery-platform/domains/**;tasks/features/F-PROJ-001.md;tasks/features/F-PROJ-007.md;tasks/features/F-PROJ-008.md;tasks/features/F-ACC-001.md;tasks/features/F-COM-001.md;tasks/features/README.md;docs/traceability/requirement-matrix.md;docs/traceability/requirement-version-coverage.json;tasks/delivery-units/DU-20260907-TEMPLATE-ACCEPTANCE-EVOLUTION-DESIGN.md;tasks/delivery-units/README.md`
> 串行资源：`master PRD下一修订号;相关SDS与Feature规格;由权威文件派生的领域需求和追溯投影`
> 旧功能范围：`仅现有模板和验收载体的复用、历史只读与迁移解释；不修改废弃实现、历史证据或已执行迁移`
> 验证：`PRD与来源副本一致；当前正文无S5强制终验残留；Owner/触发/门禁/视图边界自审；需求ID与原Feature实施状态保留；文档链接、差异及认领范围检查`
> 集成记录：`NONE`

## 已批准目标

- 先恢复模板基础配置闭环，再补可复用定义、阶段关系图及冻结，最后接入阶段/任务业务视图与完成判定；本DU只落设计。
- 业务校验的适用性、前置、组合、时点和阈值由版本化配置决定；授权、租户、不可变历史、事务和结构完整性不成为可关闭开关。
- 终验复用ACC独立业务实体，不因S5或特定任务编码自动产生义务；办理使用WorkBinding/业务视图，结果消费使用CompletionRule/Gate。
- 触发动作与只读条件评估分离；一起复核PROJ上下文、COM范围版本和ACC验收范围绑定，不以本次设计确认猜测尚未冻结的物理/API契约。
- 不新增Requirement、业务角色、审批节点或状态轴；不执行DDL、业务代码、数据迁移、运行验收或推送，不晋级Feature Done。

## 写入交接

原PR #1/#7 Owner已于2026-09-07明确释放其全部写边界；两旧DU以原任务回执和master集成事实收口。PLANNED记录已在master提交为`1474ee91`；本次提交激活CLAIMED，之后才写入规格。用户明确要求使用当前主分支，故不另建分支或工作树。

## 当前进度

- 已完成：当前master只读能力审计；用户确认演进顺序、终验解耦及分层复核结论；原Owner释放。
- 剩余：正式PRD/SDS/Feature文档差量、设计文档及相应检查。
- Feature实施状态：保持原权威Task事实；设计确认不等于实现完成。
