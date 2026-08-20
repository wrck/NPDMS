# Phase 1 门禁与审查证据

Phase 1 用于确认需求追溯、领域边界、聚合责任、系统架构、状态机、工作流和授权设计是否具备进入实现契约设计的条件。

## 当前文件

- [`gate-status.md`](gate-status.md)：当前阶段门禁汇总和放行结论。固定候选独立复审后为`APPROVED / READY_FOR_PHASE_2_V1.8`。
- [`self-review.md`](self-review.md)：V1.8当前自审与机器门禁结果。
- [`independent-review.md`](independent-review.md)：V1.8当前fresh-context独立复审记录；固定候选`4792f11`结论为GO。
- [`context-refinement-review.md`](context-refinement-review.md)：领域上下文重构后的复审记录。
- [`naming-review.md`](naming-review.md)：业务命名审查结论。
- [`naming-inventory.md`](naming-inventory.md)：命名迁移盘点证据。

## 输入与历史材料

- `input/` 保存收到的外部评审稿，例如 `sds-phase1-domain-model-review-after-field-renaming.md`。输入稿保持原貌，修订意见通过正式审查记录表达。
- `archive/` 保存已被当前结论替代的历史审查材料，包括V1.7独立评审结论和旧版领域模型完整审查稿。

## 使用要求

- 评审发现必须关联 Requirement ID、设计文件或门禁编号，避免只有结论没有证据。
- 通过项、待跟进项和阻塞项必须区分；阻塞项未关闭时，当前阶段不得标记为 `PASS`。
- 关闭门禁时，应在 `gate-status.md` 记录证据链接、确认人和日期，并同步更新正式 SDS 或 `docs/decisions/`。
- 本目录不承载正式业务规则；正式规则只能写入 PRD、SDS 或批准的决策记录。
