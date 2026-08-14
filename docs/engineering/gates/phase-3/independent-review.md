# P3-E09 数据模型基线独立复审记录

> status: `IN_REVIEW`<br>
> conclusion: `PENDING_FRESH_REVIEW`<br>
> ddlSha256: `5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249`<br>
> itemsSha256: `36503D53BDBF9264E01D3FC59A157CCB5F8168D51159A0FCE29B688936F87D5D`<br>
> itemCount: `1883`<br>
> deferCount: `0`<br>
> testResult: `PASS`<br>

## 当前候选状态

上一轮`GO`只覆盖当时的候选制品。本轮已按V1.8轻量治理原则移除复审记录中重复的Git元数据和迁移批准哈希；在独立复审对当前模型事实给出明确`GO`前，P3-E09为`MODEL_BASELINE_REVIEW_PENDING`，不得作为SDS/Feature模型输入。`AI-MIG-000`、历史数据迁移和数据切换持续阻断。

## 已复审候选事实

|项目|当前候选事实|
|---|---|
|Git 基线|Git原生保存commit ID、作者、时间和差异；复审记录不重复维护候选提交、日期或范围字段|
|当前 DDL SHA-256|`5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249`|
|逐项寄存器|`ddl-item-decision-register.json`，SHA-256 `36503D53…87D5D`，共 1,883 项；994 项 `ACCEPT_CURRENT`、889 项 `AMEND_CURRENT`、0 项 `DEFER`|
|逐项决策证据|ADR-0019～ADR-0023、ADR-0025、ADR-0027、ADR-0028；逐项裁决已完成|
|隔离执行事实|MySQL 8.4.10 执行证据绑定同一当前 DDL 哈希，状态 `PASS`|
|Q08|122 项候选索引；仍须由 Feature 查询计划及 P3-E06 性能验收验证|
|迁移批准哈希|P3-E09不定义该字段；未来历史迁移门禁按真实批次另行定义|

## 独立复审范围

本复审只核对当前模型制品的整体一致性：正式制品哈希、当前 DDL、MySQL 8.4 执行事实、`DEFER=0`以及决策与复审责任人不同。提交、作者、时间和文件差异由Git原生记录；本文件不重复构造候选提交、日期或范围授权。它不要求四角色外部附件、OA/电子签名、逐项 Reviewer 签署、独立批准 JSON 或迁移批准状态机。

固定字段只包括复审结论和模型事实，且均只出现一次；仅在`APPROVED / GO`时才要求`testResult`和隔离MySQL状态均为`PASS`。后续任何DDL或逐项寄存器变化均会使模型哈希失配并要求重新复审；`AI-MIG-000`、历史迁移实施和数据切换继续`OPEN` / 阻断。
