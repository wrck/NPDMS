# P3-E09 数据模型基线独立复审记录

> status: `IN_REVIEW`<br>
> conclusion: `PENDING_FRESH_REVIEW`<br>
> candidateCommit: `PENDING_CORE_FIX_COMMIT`<br>
> ddlSha256: `5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249`<br>
> itemsSha256: `36503D53BDBF9264E01D3FC59A157CCB5F8168D51159A0FCE29B688936F87D5D`<br>
> itemCount: `1883`<br>
> deferCount: `0`<br>
> testResult: `PASS`<br>
> 本记录不是 `GO`，不得据此设置 `MODEL_BASELINE_READY`、关闭 `P3-E09` 或放行 `DATA_MODEL_BASELINE`。fresh reviewer 如给出最终结论，必须同时替换全部 `IN_REVIEW`、`PENDING` 和“不是 `GO`”表述及上述固定字段，使其与当时的模型事实逐项精确匹配。

## 候选绑定事实

|项目|当前候选事实|
|---|---|
|候选 Git 基线|核心fix提交完成后，以`candidateCommit`固定绑定；fresh reviewer 必须在该精确候选上重新复核|
|当前 DDL SHA-256|`5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249`|
|逐项寄存器|`ddl-item-decision-register.json`，SHA-256 `36503D53…87D5D`，共 1,883 项；994 项 `ACCEPT_CURRENT`、889 项 `AMEND_CURRENT`、0 项 `DEFER`|
|逐项决策证据|ADR-0019～ADR-0023、ADR-0025、ADR-0027、ADR-0028；逐项裁决已完成|
|隔离执行事实|MySQL 8.4.10 执行证据绑定同一当前 DDL 哈希，状态 `PASS`|
|Q08|122 项候选索引；仍须由 Feature 查询计划及 P3-E06 性能验收验证|
|迁移批准哈希|`approvedDdlSha256: null`；它不是模型候选条件，仅由未来历史迁移门禁管理|

## 独立复审范围

本复审只核对候选制品的整体一致性：正式制品哈希、当前 DDL、MySQL 8.4 执行事实、`DEFER=0`、决策与复审责任人不同，以及本记录所绑定候选的完整性。它不要求四角色外部附件、OA/电子签名、逐项 Reviewer 签署、独立批准 JSON 或迁移批准状态机。

fresh reviewer 在精确候选 Git 上完成复核后，才可在本文件写入明确的独立复审结论 `GO`，并由后续发布轮次回写 `MODEL_BASELINE_READY`。在此之前，P3-E09 是 `MODEL_BASELINE_REVIEW_PENDING`；`AI-MIG-000`、历史迁移实施和数据切换继续 `OPEN` / 阻断。
