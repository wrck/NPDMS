# Phase 2 门禁与证据

Phase 2 用于审查数据、数据库、API、事件、集成、文件、缓存、并发、异常、幂等契约是否足以进入 Phase 3。

> 当前门禁：`REVALIDATION_REQUIRED / BLOCKED_BY_PRD_DELTA`<br>
> 当前PRD：修订001—012、014—015；正式Requirement 100项、111个目标版本切片，数量不变但部分实现契约已变化<br>
> 上次批准：修订007 `APPROVED / READY_FOR_PHASE_3_V1.8`，仅作历史证据<br>
> 当前结论：须重验证通用阶段编排、WorkBinding、三类闭环终态、条件性验收、PM-06范围版本、BPM身份和RPT-02读模型；涉及物理模型时重做P3-E09差量

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
