# DDL 漂移审查报告

> 状态：`REVIEW_PENDING / BLOCKED_BY_REVIEW`
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
|`ACCEPT_CURRENT`|994|历史目标字段与当前字段定义完全一致，按基线继承；Reviewer 尚未签署|
|`AMEND_CURRENT`|197|仅限 ADR 明确列出的命名、项目编码、市场行业字段、核心迁移项、Q03 精确清单及明确移除的 V3 表|
|`DEFER`|692|当前哈希下尚未重新确认的物理细节|

全部逐项 `reviewOwner` 为空，`approvedCount=0`，`approvedDdlSha256` 为空。

## 3. 当前真实阻断

|分组|数量|状态|关闭方式|
|---|---:|---|---|
|Q07 技术约束|257|`RECONFIRMATION_REQUIRED`|需求方确认当前哈希下继续采用现有技术约束；随后 Reviewer 逐项签署|
|Q08 候选索引|122|`RECONFIRMATION_REQUIRED`|需求方确认仅作为候选基线；真实性能仍由 Feature 查询计划和 P3-E06 验收|
|V1.7 物理候选|10 表|`PROPOSED_FOR_REVIEW`|形成与当前哈希绑定的显式 itemId 清单，不得按整表自动接受|
|表选项|60|`DEFER`|按精确值确认字符集、排序规则、存储引擎和注释；不得按 SQL 类型批量接受|
|其余字段/业务约束|按逐项登记|`DEFER`|由明确业务规则或当前哈希决策清单逐项覆盖|

ADR-0023 明确规定：DDL 哈希、数量或分类变化时，原 Q07/Q08 决策自动失效并重新评审。因此旧哈希下的确认不能直接套用于当前候选 DDL。

ADR-0025、ADR-0027 当前状态为 `PROPOSED_FOR_REVIEW`，只能证明候选来源，不能证明十张表的所有物理项已经由需求 Owner 批准。

## 4. 证据与安全边界

- 旧库继续只读，禁止旧库 DDL/DML 和跨库 SQL。
- 已明确排除、后置或 V3 的表不得进入当前 DDL、对象映射或迁移目标。
- `pm_project_maintenance` 仅保留顶层 `EXCLUDED / NO_MIGRATION` 审计，不建立对象、目标表或字段映射。
- Q08 只代表候选索引，不代表查询计划或性能验收通过。
- MySQL 可执行性不替代业务模型确认、Reviewer 签署或生产迁移批准。

## 5. 放行条件

1. 692 项 `DEFER` 全部由当前哈希绑定的显式决策清单覆盖，不允许按 SQL 类型或整表推定。
2. 数据架构、业务 Owner、迁移 Owner 完成逐项复核；签署证据与候选事实分离保存。
3. 形成非空 `approvedDdlSha256`，并确保 DDL、字段目录、迁移映射、校验与发布清单使用同一哈希。
4. 机器校验和独立复审均通过后，才可关闭 P3-E09。
