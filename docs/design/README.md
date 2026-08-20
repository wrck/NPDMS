# SDS 工作目录

> 目录治理总则：见 [`docs/README.md`](../README.md)。本文件只补充 SDS 专属规则；任何评审稿、门禁证据、计划稿和临时分析必须按总则归档，禁止混入本目录。

本目录用于沉淀《项目实施交付管理平台 系统详细设计说明书（SDS）》。

> 当前基线提示：PRD V1.8已于2026-08-19发布。当前分册已按阶段同步V1.8范围，但仍须通过对应`gate-status.md`的差量复审；状态为`IN_REVIEW`、`REVALIDATION_REQUIRED`或`DEFERRED_TO_PHASE_3`的分册不得作为新Feature或实现的放行依据。V1.7审查材料只作为历史证据。

## 目标文件

Codex 按顺序维护：

1. `01-requirement-traceability.md`
2. `02-domain-model.md`
3. `03-system-architecture.md`
4. `04-module-design.md`
5. `05-state-machine.md`
6. `06-workflow-design.md`
7. `07-authorization-design.md`
8. `08-data-model.md`
9. `09-database-design.md`
10. `10-api-design.md`
11. `11-event-design.md`
12. `12-integration-design.md`
13. `13-file-design.md`
14. `14-security-design.md`
15. `15-cache-and-concurrency.md`
16. `16-exception-and-idempotency.md`
17. `17-audit-and-observability.md`
18. `18-deployment-design.md`
19. `19-performance-design.md`
20. `20-test-design.md`
21. `00-system-detailed-design.md`

`00-system-detailed-design.md` 在各分册稳定后生成，作为总册与索引，不应成为复制粘贴所有分册的超大单文件。

## 文档边界

本目录只保留正式 SDS 和其稳定的补充设计分册。Phase 1 的门禁状态、独立评审、命名盘点、评审输入及历史审查稿统一归档到 [`docs/engineering/gates/phase-1/`](../engineering/gates/phase-1/)，不再与设计正文混放。

当前已启用的 Phase 1 补充分册：

- `02a-context-map.md`
- `02b-aggregate-boundary-decisions.md`
- `02c-data-ownership-matrix.md`
- `02d-cross-context-contracts.md`
- `02e-version-scope-matrix.md`

当前已启用的 Phase 2 补充分册：

- `08a-domain-entity-migration-alignment.md`：覆盖全部显式领域数据对象的旧数据元/旧库/当前实现来源、迁移策略、排除项与迁移Gate。

## SDS 文件准入规则

- 一个主题只允许一个当前正式分册；不同方案、评审意见和中间版本不在本目录复制。
- 新增分册必须先登记到本 README 和正式工程链，并明确 PRD 版本、Requirement ID、文档状态和 Owner。
- `DRAFT`/`IN_REVIEW` 只表示正式分册的编辑状态，不得通过另起 `*-draft.md` 或 `*-review.md` 文件规避状态管理。
- 评审发现通过修订正式分册解决；评审过程和证据放在 `docs/engineering/gates/`，决策放在 `docs/decisions/`。
- 被替代的 SDS 不能删除或覆盖，必须标记 `SUPERSEDED` 并移至版本化归档位置，同时保留替代文档链接。

## 设计约束

- 每个章节必须列出对应 PRD Requirement ID。
- 技术设计可以补充实现细节，但不能新增业务语义。
- 不确定的业务问题进入 `docs/decisions/open-questions.md`。
- 每一阶段先 Review，再继续下一阶段。
- SDS 通过后才进入 Feature Spec 和编码。
- `specs/001-project-delivery-platform/domains/` 为PRD V1.8直接派生的领域需求输入；本目录的 SDS 必须直接以 PRD V1.8 和当前基线快照为准，并记录与V1.7设计的差异。
