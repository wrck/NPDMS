-- P23 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [PLAN] TABLE_READER_PROJECTION; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P23.json
SELECT `id`, `contractNo`, `batchCode`, `basicDataName`, `referenceEventName`, `eventPlanHappenDate`, `afterDaysNum`, `eventActualFinishDate`, `marketingFeedback`, `createBy`, `createTime`, `updateBy`, `updateTime`, `effectiveFrom`, `effectiveTo`, `dataSource` FROM `pm_pb_plan_from_sms`;

-- ===== 2. 目标/辅助查询与前置 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- A:GenericSyncJdbcStore.insertBatch:PLAN:com_contract_collection_plan; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_contract_collection_plan` (`id`, `tenant_id`, `deleted`, `contract_no`, `batch_code`, `collection_item_name`, `reference_event_name`, `event_planned_at`, `after_days`, `event_actual_finished_at`, `marketing_feedback`, `creator`, `create_time`, `updater`, `update_time`, `effective_from`, `effective_to`, `data_source`)
VALUES (:id, :tenant_id, :deleted, :contract_no, :batch_code, :collection_item_name, :reference_event_name, :event_planned_at, :after_days, :event_actual_finished_at, :marketing_feedback, :creator, :create_time, :updater, :update_time, :effective_from, :effective_to, :data_source);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
