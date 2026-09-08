# Phase 2 修订017当前验证入口

> 当前用途（2026-09-08需求方批准）：设计分类与可选全量审计，不再作为阶段准入。以下状态/结果是原流程快照，不随日常变更同步，也不据此要求全阶段重签。

日常工作按受影响契约/表/消费者审查及验证。`python -X utf8 -B scripts/validate_sds_phase2.py --audit`显式运行全量内容及来源审计，保留真实失败但不批准后续阶段；默认输出NOT_RUN，原`--technical`保留为显式审计选择。Schema/Owner/租户/历史保护等实质检查继续保留。

## 取消阶段阻断前的记录

> 来源目录：`106对象/124来源绑定/1排除源`
> 当前结论：`REVALIDATION_REQUIRED`
> 文档用途：`当前Gate投影及自审；不代替独立复审`

100项正式需求、111个目标版本切片；106对象/124来源绑定/1排除源。当前结论以gate-status.md为准；修复与真实命令结果见`../phase-1/revision-017-review-remediation.md`。以下原记录仅属历史，不产生修订017批准。

## 历史记录（原内容保留）

# Phase 2 门禁与证据

Phase 2 用于审查数据、数据库、API、事件、集成、文件、缓存、并发、异常、幂等契约是否足以进入 Phase 3。

> 当前门禁：`REVALIDATION_REQUIRED / BLOCKED_BY_REVIEW`<br>
> 当前PRD：修订017，保留master修订001～014及PR #1来源历史；100项Requirement、111个版本切片不变<br>
> 历史批准：修订007及未受影响后续批准范围保留，不自动重开无关Feature<br>
> 当前结论：按用户确认完成项目经理职责、通用字段和冲突整改；只重验证受影响设计与参考Schema，不新增审批门禁

> 2026-08-25聚焦增量：F-PROJ-004新增`ProjectTemplateMatchHistory`后，当前迁移契约为88对象/99来源绑定/1排除源；该对象为`NONE_NEW / FEATURE_FORWARD_MIGRATION(PM-07)`，不进入既有核心DDL，Feature Ready独立复审仍须单独GO。
> 2026-08-28聚焦候选：F-PLT-002把共享动态表单前向归属PLT，并保留Preparation专用实例后，该候选形成时的生成契约为90对象/101来源绑定/1排除源；三个PLT对象均不迁移、不双写旧`pms_eng_form_*`。该历史统计只证明候选生成链一致；修订007当前总体统计见上方迁移边界，Feature Ready仍待独立裁决。
> 2026-08-28 F-CUS-001回写：新增`MarketRelation`、`CustomerLocationReference`、`CustomerScopeSlice`并将`Customer`映射到F-CUS前向表后，当前迁移契约为93对象/104来源绑定/1排除源；V106～V108属于Feature前向迁移，不进入当前核心DDL精确表集。

## 当前文件

- [`gate-status.md`](gate-status.md)：Phase 2 当前门禁状态。
- [`implementation-fact-inventory.md`](implementation-fact-inventory.md)：实现仓库事实、漂移分类和前向纠正约束。
- [`self-review.md`](self-review.md)：修订007的Phase 2差量自审历史结果；修订015当前结果以`gate-status.md`为准。
- [`independent-review.md`](independent-review.md)：修订007前的历史独立复审记录，仅用于追溯，不构成当前独立裁决角色。

## 归档规则

- 实现事实盘点、评审输入和生成校验证据放入本目录或 `input/`。
- 正式 08～16 SDS 分册只放入 `docs/design/`，不在本目录创建平行副本。
- 独立评审结果必须回指 Requirement ID、正式 SDS 或可重现命令。
