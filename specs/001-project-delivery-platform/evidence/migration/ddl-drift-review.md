# DDL漂移审查报告

> 状态：`REVIEW_PENDING`（Q01～Q08需求方决策已确认，整体数据模型尚未取得Reviewer批准）
> 门禁：`P3-E09 / AI-MIG-000`
> 机器事实：[`ddl-drift-review.json`](ddl-drift-review.json)

## 1. 输入与哈希

|输入|当前事实|
|---|---|
|当前目标DDL|`project-order-physical-schema.mysql.sql`，SHA-256 `F788F56B7C1818383817D7B70279323D49D46D8DA279AEE54135E43A31C31DAA`|
|历史字段目录基线|DDL SHA-256 `2B206992BA5580E776060F9D4ED177A7BD8C34DB614FD65EC9560DAF38F8BF33`|
|命名归一化|仅按ADR-0019将历史表名和6个同义字段映射到新名称后比较，不改变历史类型、空值、默认值、生成属性或说明|
|当前字段目录|50表、1,065列，与当前DDL绑定同一SHA-256；范围为核心迁移子集|
|当前约束清单|385项DDL约束/索引、50项表选项，含48个同域外键、78个CHECK；隔离MySQL 8.4.10执行PASS|

## 2. 已确认裁决

- 52张表按`<领域编码>_<完整领域对象名称>`重命名，删除业务系统前缀`pms_`。
- 表名默认使用完整英文词，仅`config`、`sn`允许作为标准缩写。
- NAM-001～NAM-006同义字段按ADR-0019统一。
- ADR-0020确认同一CRM项目的多合同/多订单不派生项目编码，项目编码租户内唯一，编码命名空间与当前层级分离；新增`code_root_id`、`project_sequence`、`code_rule_version`及4项配套约束。
- ADR-0021确认CRM四维组合目录归CUS，目标表为`cus_market_relation`；客户与项目直接保存四组编码/名称，不保存`relation_id`，也不将分类映射到组织关系。
- ADR-0022确认当前DDL为核心迁移子集，移除4张V3技术公告治理表和跨领域物理外键；外部键映射新增`target_role/target_sequence`，当前唯一性、归一化、永久业务键及历史异常隔离规则已固化。
- `ddl-item-decision-register.json`已对1,883项完成机器化决策登记：994项未变化字段为`ACCEPT_CURRENT`，889项有需求或ADR依据的变化为`AMEND_CURRENT`，无`DEFER`项；其中Q07覆盖257项技术约束，Q08覆盖122项候选索引，重叠项保留全部证据引用。全部`reviewOwner`仍为空且`approvedCount=0`，本登记不替代独立Reviewer批准。
- Reviewer尚未签署，因此这些项目不计入最终批准数，也不生成`approvedDdlSha256`。

## 3. 仍未关闭的模型项

|对象|数量|状态|原因|
|---|---:|---|---|
|当前表|50|核心迁移子集边界及Q03交付范围明细已决定，Reviewer待签署|不能以需求方确认替代数据架构复核，也不能冒充平台全量模型|
|当前列|1,065|命名、项目编码、四维分类、外部键映射及Q03业务事实相关项已决定，其余保持`DEFER`|字段类型、默认值和未裁决业务规则仍需逐项批准|
|当前约束|385|Q07技术完整性约束和Q08候选索引已确认；业务约束沿用Q01～Q06/Q03及既有ADR|Q08仍须Feature查询计划和P3-E06压测；Reviewer尚未完成全量签署|
|当前表选项|50|`UNVERIFIED_BASELINE_MISSING`|历史证据未保存字符集、排序规则和存储选项|
|比较并集登记|1,626项|保留54个表事实、1,133个列事实及移除状态|用于审查历史到当前的新增、修改和移除，不等于当前DDL规模|

## 4. 证据边界

- `ddl-drift-review.json`证明当前DDL与历史字段事实的逐项差异；ADR-0020～ADR-0022覆盖的新增、修改和移除项已登记，但该报告不能证明其余约束和表选项与历史批准版本一致。
- `target-field-catalog.jsonl`、核心字段映射及完整物理字段矩阵已经更新目标引用；旧库`sourceTable/sourceColumn/sourceDefinition/sourceRefs/evidenceRefs`保持原值。
- 历史`migration-validation.json.passed=true`不具有当前生产迁移放行效力。
- 旧库继续只读，禁止旧库DDL/DML和跨库SQL。
- P3-E09保持`OPEN / BLOCKED_BY_REVIEW`，继续阻断SDS数据模型最终基线、历史迁移实施和数据切换；Q08性能验证另由Feature/P3-E06下游门禁控制。

## 5. 放行条件

1. 数据架构、业务Owner和迁移Owner完成全部模型项复核。
2. 每个非`DEFER`项目具有决策Owner、Reviewer和证据引用。
3. 形成非空`approvedDdlSha256`，并与DDL、字段目录、映射、校验及发布清单使用同一哈希。
4. 全部机器校验和独立复审通过后，才能关闭P3-E09。
