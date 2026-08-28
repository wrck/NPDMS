# ADR-0031：客户服务等级与割接配置版本载体

## 状态

`ACCEPTED`

## 日期

2026-08-21

## 需求依据

- PRD V1.8：`CUS-02`、`CUT-07`。
- CUS-02要求同一客户同一时点只有一个有效服务等级，变更生成新有效区间并冻结策略版本，历史业务快照不得回写。
- CUT-07要求统一维护采集项、动态绑定关系和发布版本；割接类型、组网模式、设备类型属于基础平台可配置字典维度。

## 问题

原Phase 2映射把CUS-02落到联系人/关系快照，把CUT-07落到割接方案。两者均不能承载对应业务事实：联系信息快照不拥有服务等级时态版本，割接方案也不拥有后台采集项和绑定规则主数据。

## 决策

### 1. 客户服务等级

- 新增逻辑聚合`CustomerServiceLevelRevision`，未来物理表为`cus_customer_service_level_revision`。
- 聚合保存客户、等级字典代码、策略快照、生效区间、变更依据、证据引用和确认人；受控切换必须原子关闭旧区间并追加新版本。
- 同一租户、同一客户最多一个未结束的当前区间。项目、割接和服务动作只冻结命中的等级及策略版本，不回写历史业务事实。
- 等级代码使用基础平台字典，不新增客户等级字典业务表。

### 2. 割接后台配置

- 新增逻辑聚合`CutoverConfigurationRevision`，未来由三张表承载：`cut_cutover_configuration_revision`、`cut_cutover_checklist_item_definition_revision`、`cut_cutover_checklist_binding_rule_revision`。
- 配置根保存草稿、发布、停用和版本事实；采集项定义版本保存稳定项键、类型、界面Schema、工作方式和可选外部数据源引用；绑定规则版本保存配置版本、采集项版本、维度条件快照和优先级。
- 割接类型、组网模式、设备类型继续使用基础平台可配置字典；配置版本及消费实例冻结所用字典代码和显示名称，不为三个维度另建业务主表。
- `CutoverPlan`只承载CUT-04/05方案revision，不得承载CUT-07后台配置主数据。

### 3. 前向迁移边界

- 两个聚合均为`NONE_NEW / FEATURE_FORWARD_MIGRATION`，没有可证明的历史来源，不生成历史迁移字段绑定。
- 本ADR只批准SDS逻辑对象、未来表名和关键约束，不创建或修改当前Flyway、核心DDL、P3-E09逐项寄存器或生产数据库。
- 对应Feature实施前必须提交前向迁移、API契约、权限与并发测试；只有发布实际包含历史迁移或数据切换时，`AI-MIG-000`才成为Release前置门禁。

## 明确排除

- 不把服务等级写入`CustomerContact`或`CustomerRelationshipSnapshot`。
- 不把CUT-07配置写入`CutoverPlan`、`cut_plan_revision`或`cut_step`。
- 不新建设备类型、割接类型、组网模式独立业务表。
- 不借本次设计变更扩大其他Requirement、API、状态机、权限或迁移范围。
