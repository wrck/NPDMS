-- P23 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectCollectionPlan] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P23SourceMapper.xml
SELECT `contractNo` AS `contract_no`, `batchCode` AS `batch_code`, `basicDataName` AS `collection_item_name`, `referenceEventName` AS `reference_event_name`, `eventPlanHappenDate` AS `event_planned_at`, `afterDaysNum` AS `after_days`, `eventActualFinishDate` AS `event_actual_finished_at`, `marketingFeedback` AS `marketing_feedback`, `createBy` AS `creator`, `createTime` AS `create_time`, `updateBy` AS `updater`, `updateTime` AS `update_time`, `effectiveFrom` AS `effective_from`, `effectiveTo` AS `effective_to`, `dataSource` AS `data_source` FROM `pm_pb_plan_from_sms` LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P23CollectionPlanSyncTask$TargetMapper.selectCollectionPlanIds; pms-module-integration/src/main/resources/mapper/basesync/P23TargetMapper.xml:4; kind=select
SELECT id FROM com_contract_collection_plan WHERE tenant_id=#{query.tenantId}  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P23CollectionPlanSyncTask$TargetMapper.nullableColumns; pms-module-integration/src/main/resources/mapper/basesync/P23TargetMapper.xml:9; kind=select
SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='com_contract_collection_plan' AND column_name IN ('create_time','update_time') AND is_nullable='YES';

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P23CollectionPlanSyncTask$TargetMapper.insertCollectionPlan; pms-module-integration/src/main/resources/mapper/basesync/P23TargetMapper.xml:6; kind=insert
INSERT INTO com_contract_collection_plan (`id`, `tenant_id`, `contract_no`, `batch_code`, `collection_item_name`, `reference_event_name`, `event_planned_at`, `after_days`, `event_actual_finished_at`, `marketing_feedback`, `creator`, `create_time`, `updater`, `update_time`, `effective_from`, `effective_to`, `data_source`, `deleted`) VALUES
     /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id}, #{row.target.tenantId}, #{row.target.contractNo}, #{row.target.batchCode}, #{row.target.collectionItemName}, #{row.target.referenceEventName}, #{row.target.eventPlannedAt}, #{row.target.afterDays}, #{row.target.eventActualFinishedAt}, #{row.target.marketingFeedback}, #{row.target.creator}, #{row.target.createTime}, #{row.target.updater}, #{row.target.updateTime}, #{row.target.effectiveFrom}, #{row.target.effectiveTo}, #{row.target.dataSource}, #{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P23CollectionPlanSyncTask$TargetMapper.deleteCollectionPlan; pms-module-integration/src/main/resources/mapper/basesync/P23TargetMapper.xml:5; kind=delete
DELETE FROM com_contract_collection_plan WHERE tenant_id=#{query.tenantId};

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
