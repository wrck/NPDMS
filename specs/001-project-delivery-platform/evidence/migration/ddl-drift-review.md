# DDL漂移审查报告

> 状态：`DEFER`（命名项已确认，整体数据模型尚未批准）
> 门禁：`P3-E09 / AI-MIG-000`
> 机器事实：[`ddl-drift-review.json`](ddl-drift-review.json)

## 1. 输入与哈希

|输入|当前事实|
|---|---|
|当前目标DDL|`project-order-physical-schema.mysql.sql`，SHA-256 `9CAE49A641022EB20B42CBC2D1059C7732125528EC4613667E2514E4D14D1411`|
|历史字段目录基线|DDL SHA-256 `2B206992BA5580E776060F9D4ED177A7BD8C34DB614FD65EC9560DAF38F8BF33`|
|命名归一化|仅按ADR-0019将历史表名和6个同义字段映射到新名称后比较，不改变历史类型、空值、默认值、生成属性或说明|
|当前字段目录|52表、1,076列，与当前DDL绑定同一SHA-256|
|当前约束清单|422项约束、52项表选项，绑定当前DDL SHA-256|

## 2. 已确认裁决

- 52张表按`<领域编码>_<完整领域对象名称>`重命名，删除业务系统前缀`pms_`。
- 表名默认使用完整英文词，仅`config`、`sn`允许作为标准缩写。
- NAM-001～NAM-006同义字段按ADR-0019统一。
- 上述58项在`ddl-item-decision-register.json`登记为`AMEND_CURRENT`，`decisionOwner=REQUIREMENT_OWNER`并引用ADR-0019。
- Reviewer尚未签署，因此这些项目不计入最终批准数，也不生成`approvedDdlSha256`。

## 3. 仍未关闭的模型项

|对象|数量|状态|原因|
|---|---:|---|---|
|表|52|命名已决定，Reviewer待签署|不能以需求方命名确认替代数据架构复核|
|列|1,076|6项命名已决定，其余保持`DEFER`|字段类型、默认值和业务规则仍需逐项批准|
|约束|422|`UNVERIFIED_BASELINE_MISSING`|历史字段目录未保存主键、外键、唯一键、索引和CHECK完整定义|
|表选项|52|`UNVERIFIED_BASELINE_MISSING`|历史证据未保存字符集、排序规则和存储选项|

## 4. 证据边界

- `ddl-drift-review.json`证明当前DDL与历史字段事实在ADR命名归一化后没有列级差异，但不能证明约束和表选项与历史批准版本一致。
- `target-field-catalog.jsonl`、核心字段映射及完整物理字段矩阵已经更新目标引用；旧库`sourceTable/sourceColumn/sourceDefinition/sourceRefs/evidenceRefs`保持原值。
- 历史`migration-validation.json.passed=true`不具有当前生产迁移放行效力。
- 旧库继续只读，禁止旧库DDL/DML和跨库SQL。
- P3-E09保持`OPEN / BLOCKED_BY_MODEL_DECISION`，继续阻断SDS数据模型最终基线、历史迁移实施和数据切换。

## 5. 放行条件

1. 数据架构、业务Owner和迁移Owner完成全部模型项复核。
2. 每个非`DEFER`项目具有决策Owner、Reviewer和证据引用。
3. 形成非空`approvedDdlSha256`，并与DDL、字段目录、映射、校验及发布清单使用同一哈希。
4. 全部机器校验和独立复审通过后，才能关闭P3-E09。
