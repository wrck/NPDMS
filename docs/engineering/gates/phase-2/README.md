# Phase 2 门禁与证据

Phase 2 用于审查数据、数据库、API、事件、集成、文件、缓存、并发、异常、幂等契约是否足以进入 Phase 3。

> 当前门禁：`APPROVED / READY_FOR_PHASE_3_V1.8`<br>
> 当前范围：V1 53项、V2 47项、V1/V2正式需求100项；已编号V3 31项、跨需求演进方向2项；`OUT_OF_SCOPE` 9项<br>
> 迁移边界：87对象/98来源绑定/1排除源；`CustomerServiceLevelRevision`和`CutoverConfigurationRevision`均为`NONE_NEW / FEATURE_FORWARD_MIGRATION`，不进入当前核心DDL；P3-E09=`MODEL_BASELINE_READY`仅批准当前冻结模型。仅当发布包含历史迁移或数据切换时，才由`AI-MIG-000`作为Release前置门禁并绑定批准窗口，普通功能发布不适用

> 2026-08-25聚焦增量：F-PROJ-004新增`ProjectTemplateMatchHistory`后，当前迁移契约为88对象/99来源绑定/1排除源；该对象为`NONE_NEW / FEATURE_FORWARD_MIGRATION(PM-07)`，不进入既有核心DDL，Feature Ready独立复审仍须单独GO。
> 2026-08-28聚焦候选：F-PLT-002把共享动态表单前向归属PLT，并保留Preparation专用实例后，当前生成契约为90对象/101来源绑定/1排除源；三个PLT对象均不迁移、不双写旧`pms_eng_form_*`。该统计只证明候选生成链一致，Feature Ready仍待独立裁决。
> 2026-08-28 F-CUS-001回写：新增`MarketRelation`、`CustomerLocationReference`、`CustomerScopeSlice`并将`Customer`映射到F-CUS前向表后，当前迁移契约为93对象/104来源绑定/1排除源；V106～V108属于Feature前向迁移，不进入当前核心DDL精确表集。

## 当前文件

- [`gate-status.md`](gate-status.md)：Phase 2 当前门禁状态。
- [`implementation-fact-inventory.md`](implementation-fact-inventory.md)：实现仓库事实、漂移分类和前向纠正约束。
- [`self-review.md`](self-review.md)：本轮V1.8 Phase 2 差量自审与回归证据。
- [`independent-review.md`](independent-review.md)：本轮V1.8 fresh-context独立复审与最终GO结论；旧V1.7结论不作为放行依据。

## 归档规则

- 实现事实盘点、评审输入和生成校验证据放入本目录或 `input/`。
- 正式 08～16 SDS 分册只放入 `docs/design/`，不在本目录创建平行副本。
- 独立评审结果必须回指 Requirement ID、正式 SDS 或可重现命令。
