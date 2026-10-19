# P23 三种实现的正式写入字段

[返回本任务](README.md)

原表逐字段去向见field-mapping.md；实际来源SQL见三份.sql。以下是正式INSERT列→运行参数的实际对应，Java转换位于源别名与这些参数之间。MyBatis typeHandler参数完整保留；带_TEMPLATE的条目只是动态列/ORM结构示意，不是实际执行日志。

## 原通用引擎 / Owner适配器

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_contract_collection_plan` (`id`, `tenant_id`, `deleted`, `contract_no`, `batch_code`, `collection_item_name`, `reference_event_name`, `event_planned_at`, `after_days`, `event_actual_finished_at`, `marketing_feedback`, `creator`, `create_time`, `updater`, `update_time`, `effective_from`, `effective_to`, `data_source`)
VALUES (:id, :tenant_id, :deleted, :contract_no, :batch_code, :collection_item_name, :reference_event_name, :event_planned_at, :after_days, :event_actual_finished_at, :marketing_feedback, :creator, :create_time, :updater, :update_time, :effective_from, :effective_to, :data_source);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 独立直写任务

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|

该方式还有动态编译/ORM INSERT，列集合及参数模板如下。其映射/LOOKUP/默认值以同方式JSON及实际代码为准：

```sql
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_contract_collection_plan` (`id`, `tenant_id`, `deleted`, `contract_no`, `batch_code`, `collection_item_name`, `reference_event_name`, `event_planned_at`, `after_days`, `event_actual_finished_at`, `marketing_feedback`, `creator`, `create_time`, `updater`, `update_time`, `effective_from`, `effective_to`, `data_source`)
VALUES (:id, :tenant_id, :deleted, :contract_no, :batch_code, :collection_item_name, :reference_event_name, :event_planned_at, :after_days, :event_actual_finished_at, :marketing_feedback, :creator, :create_time, :updater, :update_time, :effective_from, :effective_to, :data_source);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。
```

## 新虚基类套件

| 正式表.字段 | 实际INSERT值/参数 | SQL声明 |
|---|---|---|
| com_contract_collection_plan.id | #{row.target.id} | insertCollectionPlan |
| com_contract_collection_plan.tenant_id | #{row.target.tenantId} | insertCollectionPlan |
| com_contract_collection_plan.contract_no | #{row.target.contractNo} | insertCollectionPlan |
| com_contract_collection_plan.batch_code | #{row.target.batchCode} | insertCollectionPlan |
| com_contract_collection_plan.collection_item_name | #{row.target.collectionItemName} | insertCollectionPlan |
| com_contract_collection_plan.reference_event_name | #{row.target.referenceEventName} | insertCollectionPlan |
| com_contract_collection_plan.event_planned_at | #{row.target.eventPlannedAt} | insertCollectionPlan |
| com_contract_collection_plan.after_days | #{row.target.afterDays} | insertCollectionPlan |
| com_contract_collection_plan.event_actual_finished_at | #{row.target.eventActualFinishedAt} | insertCollectionPlan |
| com_contract_collection_plan.marketing_feedback | #{row.target.marketingFeedback} | insertCollectionPlan |
| com_contract_collection_plan.creator | #{row.target.creator} | insertCollectionPlan |
| com_contract_collection_plan.create_time | #{row.target.createTime} | insertCollectionPlan |
| com_contract_collection_plan.updater | #{row.target.updater} | insertCollectionPlan |
| com_contract_collection_plan.update_time | #{row.target.updateTime} | insertCollectionPlan |
| com_contract_collection_plan.effective_from | #{row.target.effectiveFrom} | insertCollectionPlan |
| com_contract_collection_plan.effective_to | #{row.target.effectiveTo} | insertCollectionPlan |
| com_contract_collection_plan.data_source | #{row.target.dataSource} | insertCollectionPlan |
| com_contract_collection_plan.deleted | #{row.target.deleted} | insertCollectionPlan |
