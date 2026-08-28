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
|当前候选 DDL|66 表、1,382 列、489 项约束/索引、66 项表选项|
|当前 DDL SHA-256|`6B203BF3B4CC860DFAEF1221977F2B48A620C0077638D857582FF7BB033E275B`|
|历史字段目录基线 SHA-256|`2B206992BA5580E776060F9D4ED177A7BD8C34DB614FD65EC9560DAF38F8BF33`|
|隔离执行|MySQL 8.4.10，执行通过；不等于模型批准或迁移放行|

## 2. 逐项决策现状

逐项登记共 2,079 项：

|决策|数量|依据与边界|
|---|---:|---|
|`ACCEPT_CURRENT`|994|历史目标字段与当前字段定义完全一致，按基线继承；逐项决策已登记|
|`AMEND_CURRENT`|1,085|ADR-0028历史清单覆盖889项；ADR-0030以精确六表item集合新增覆盖196项；逐项决策已登记|
|`DEFER`|0|需求方决策缺口已关闭；不代表独立整体一致性复审或迁移批准完成|

2,079项均已有逐项决策。`reviewOwner`不作为逐项签署字段；P3-E09不定义迁移批准哈希，未来历史迁移门禁按真实批次另行定义。正式独立复审已GO、模型基线已发布。

## 3. 当前真实阻断

|分组|数量|状态|关闭方式|
|---|---:|---|---|
|Q07 技术约束|282|`REQUIREMENT_OWNER_ACCEPTED`|ADR-0028历史清单与ADR-0030差量共同覆盖；当前整体一致性复审已GO|
|Q08 候选索引|130|`REQUIREMENT_OWNER_ACCEPTED_AS_CANDIDATE`|当前只作为候选；真实性能仍由Feature查询计划和P3-E06验收|
|V1.7 历史物理差量|10表、257项|`HISTORICAL_ACCEPTED`|ADR-0028显式itemId集合保留为历史证据锚点|
|V1.8 物理差量|6表、196项|`REQUIREMENT_OWNER_ACCEPTED`|ADR-0030使用精确表集合和itemId集合SHA接受；不授权历史迁移|
|Q09～Q14|108项|`REQUIREMENT_OWNER_ACCEPTED`|表选项、幂等/关系/身份版本键、跨字段CHECK和字段投影均按精确itemId接受|

ADR-0028确认只适用于`5EB9742F…4249`历史快照；当前`6B203BF3…275B`以未变化item继承历史证据、ADR-0030覆盖六表差量。DDL哈希、数量、分类或itemId集合再次变化时必须重新生成并复审。

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
4. 当前机器校验和正式独立复审均已通过；P3-E09为`MODEL_BASELINE_READY`，可作为SDS/Feature模型输入。历史迁移与数据切换仍按Release范围由`AI-MIG-000`阻断。
