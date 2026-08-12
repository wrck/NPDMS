# SDS 工作目录

本目录用于沉淀《项目实施交付管理平台 系统详细设计说明书（SDS）》。

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

## 设计约束

- 每个章节必须列出对应 PRD Requirement ID。
- 技术设计可以补充实现细节，但不能新增业务语义。
- 不确定的业务问题进入 `docs/decisions/open-questions.md`。
- 每一阶段先 Review，再继续下一阶段。
- SDS 通过后才进入 Feature Spec 和编码。
- `specs/001-project-delivery-platform/domains/` 为历史参考资料；本目录的 SDS 必须直接以 PRD V1.6 和基线快照为准，并记录与旧规格的差异。
