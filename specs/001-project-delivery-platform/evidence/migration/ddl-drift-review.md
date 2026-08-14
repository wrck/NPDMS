# DDL 漂移审查报告

> 状态：`MODEL_BASELINE_READY`
>
> 门禁：`P3-E09 / AI-MIG-000`
>
> 机器事实：[`ddl-drift-review.json`](ddl-drift-review.json)
>
> 逐项登记：[`ddl-item-decision-register.json`](ddl-item-decision-register.json)

## 1. 当前输入

|输入|当前事实|
|---|---|
|当前候选 DDL|60 表、1,240 列、447 项约束/索引、60 项表选项|
|当前 DDL SHA-256|`5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249`|
|历史字段目录基线 SHA-256|`2B206992BA5580E776060F9D4ED177A7BD8C34DB614FD65EC9560DAF38F8BF33`|
|隔离执行|MySQL 8.4.10，执行通过；不等于模型批准或迁移放行|

## 2. 逐项决策现状

逐项登记共 1,883 项：

|决策|数量|依据与边界|
|---|---:|---|
|`ACCEPT_CURRENT`|994|历史目标字段与当前字段定义完全一致，按基线继承；逐项决策已登记|
|`AMEND_CURRENT`|889|197项既有ADR决策，加上ADR-0028九组完整清单覆盖的692项当前哈希决策；逐项决策已登记|
|`DEFER`|0|需求方决策缺口已关闭；不代表独立整体一致性复审或迁移批准完成|

1,883项均已有逐项决策。`reviewOwner`不作为逐项签署字段，`approvedCount=0`；P3-E09不定义迁移批准哈希，未来历史迁移门禁按真实批次另行定义。

## 3. 当前真实阻断

|分组|数量|状态|关闭方式|
|---|---:|---|---|
|Q07 技术约束|257|`REQUIREMENT_OWNER_ACCEPTED`|ADR-0028已绑定当前哈希接受；整体一致性独立复审为`GO`|
|Q08 候选索引|122|`REQUIREMENT_OWNER_ACCEPTED_AS_CANDIDATE`|ADR-0028已接受为候选基线；真实性能仍由Feature查询计划和P3-E06验收|
|V1.7 物理候选|10表、257项|`REQUIREMENT_OWNER_ACCEPTED`|ADR-0028使用显式itemId完整集合接受；整体一致性独立复审为`GO`|
|Q09～Q14|108项|`REQUIREMENT_OWNER_ACCEPTED`|表选项、幂等/关系/身份版本键、跨字段CHECK和字段投影均按精确itemId接受|

ADR-0023明确规定的哈希变化重确认已由ADR-0028完成；本次确认只适用于`5EB9742F…4249`，DDL哈希、数量、分类或itemId集合变化时必须重新确认。

ADR-0025、ADR-0027仍保存候选形成与纠偏过程；十张表的当前物理项由ADR-0028和确认包形成Requirement Owner接受证据。该证据不替代对候选制品哈希和整体一致性的独立复审。

## 4. 证据与安全边界

- 旧库继续只读，禁止旧库 DDL/DML 和跨库 SQL。
- 已明确排除、后置或 V3 的表不得进入当前 DDL、对象映射或迁移目标。
- `pm_project_maintenance` 仅保留顶层 `EXCLUDED / NO_MIGRATION` 审计，不建立对象、目标表或字段映射。
- Q08 只代表候选索引，不代表查询计划或性能验收通过。
- MySQL 可执行性不替代业务模型确认、独立整体一致性复审或生产迁移批准。

## 5. 放行条件

1. 【已完成】692项原`DEFER`已由当前哈希绑定的九组显式决策清单覆盖，生成器禁止按SQL类型或整表推定未来新增项。
2. 【已完成】fresh reviewer已在`independent-review.md`完成当前制品、哈希、MySQL事实、`DEFER=0`和责任人分离的整体一致性复审，并给出`GO`；不逐项签署。
3. 【边界】P3-E09不定义迁移批准哈希，不是P3-E09候选或模型基线条件；未来历史迁移门禁与真实批次另行定义。
4. 当前机器校验和独立复审`GO`均已通过，P3-E09为`MODEL_BASELINE_READY`，可作为SDS/Feature模型输入；历史迁移与数据切换仍由各自下游门禁阻断。
