# P23 新虚基类实际读写字段衔接

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectCollectionPlan | `contractNo` | contract_no |
| selectCollectionPlan | `batchCode` | batch_code |
| selectCollectionPlan | `basicDataName` | collection_item_name |
| selectCollectionPlan | `referenceEventName` | reference_event_name |
| selectCollectionPlan | `eventPlanHappenDate` | event_planned_at |
| selectCollectionPlan | `afterDaysNum` | after_days |
| selectCollectionPlan | `eventActualFinishDate` | event_actual_finished_at |
| selectCollectionPlan | `marketingFeedback` | marketing_feedback |
| selectCollectionPlan | `createBy` | creator |
| selectCollectionPlan | `createTime` | create_time |
| selectCollectionPlan | `updateBy` | updater |
| selectCollectionPlan | `updateTime` | update_time |
| selectCollectionPlan | `effectiveFrom` | effective_from |
| selectCollectionPlan | `effectiveTo` | effective_to |
| selectCollectionPlan | `dataSource` | data_source |

| 正式表.列 | 实际写入参数 | INSERT语句 |
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
