# P3-E09 数据模型基线独立复审记录

> status: `IN_REVIEW`<br>
> conclusion: `PENDING_FRESH_REVIEW`<br>
> candidateCommit: `37218eec2fcf82224a90e0b59f9e187bed71849d`<br>
> ddlSha256: `5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249`<br>
> itemsSha256: `36503D53BDBF9264E01D3FC59A157CCB5F8168D51159A0FCE29B688936F87D5D`<br>
> itemCount: `1883`<br>
> deferCount: `0`<br>
> testResult: `PASS`<br>
> reviewDate: `2026-08-14`<br>
> reviewRange: `a37c70aa0251419cd69f8a6969cbabb23d7ed834..37218eec2fcf82224a90e0b59f9e187bed71849d`

## 当前候选状态

上一轮`GO`只覆盖当时的候选制品。本轮已加固复审固定字段、范围和候选寄存器内容校验，形成新的候选；在fresh independent review对新候选给出明确`GO`前，P3-E09为`MODEL_BASELINE_REVIEW_PENDING`，不得作为SDS/Feature模型输入。`AI-MIG-000`、历史数据迁移和数据切换持续阻断。

## 已复审候选事实

|项目|当前候选事实|
|---|---|
|候选 Git 基线|本轮核心修复提交后由metadata提交绑定新的完整40位`candidateCommit`及`reviewRange`；范围固定为基线提交至核心候选，不自引用metadata提交|
|当前 DDL SHA-256|`5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249`|
|逐项寄存器|`ddl-item-decision-register.json`，SHA-256 `36503D53…87D5D`，共 1,883 项；994 项 `ACCEPT_CURRENT`、889 项 `AMEND_CURRENT`、0 项 `DEFER`|
|逐项决策证据|ADR-0019～ADR-0023、ADR-0025、ADR-0027、ADR-0028；逐项裁决已完成|
|隔离执行事实|MySQL 8.4.10 执行证据绑定同一当前 DDL 哈希，状态 `PASS`|
|Q08|122 项候选索引；仍须由 Feature 查询计划及 P3-E06 性能验收验证|
|迁移批准哈希|`approvedDdlSha256: null`；它不是模型候选条件，仅由未来历史迁移门禁管理|

## 独立复审范围

本复审只核对候选制品的整体一致性：正式制品哈希、当前 DDL、MySQL 8.4 执行事实、`DEFER=0`、决策与复审责任人不同，以及本记录所绑定候选的完整性。它不要求四角色外部附件、OA/电子签名、逐项 Reviewer 签署、独立批准 JSON 或迁移批准状态机。

固定字段均只出现一次；仅在`APPROVED / GO`时才要求`testResult`和隔离MySQL状态均为`PASS`。后续任何DDL、逐项寄存器、复审校验或候选提交变化均须重新复审；`AI-MIG-000`、历史迁移实施和数据切换继续`OPEN` / 阻断。
