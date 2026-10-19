-- P11 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [PRODUCT] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P11.json
SELECT itemCode,productCode,productSubCode,itemModel,itemDesc,remark,listPrice,status,BU,productLine,orgId,createTime,updateTime,statecode,statuscode,productStage,endOfSaleDate,endOfSupportDate,endOfLifeDate,lastRenewalDate FROM (SELECT itemCode,productCode,productSubCode,itemModel,itemDesc,remark,listPrice,status,BU,productLine,orgId,createTime,updateTime,statecode,statuscode,productStage,endOfSaleDate,endOfSupportDate,endOfLifeDate,lastRenewalDate,ROW_NUMBER() OVER (PARTITION BY itemCode ORDER BY updateTime DESC,createTime DESC,itemModel DESC) AS row_rank FROM DPtech_v_product_info_single) ranked WHERE row_rank=1;

-- ===== 2. 目标/辅助查询与前置 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- A:GenericSyncJdbcStore.insertBatch:PRODUCT:ast_product_official_info; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_product_official_info` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `product_code`, `product_model`, `product_name`, `product_desc`, `technical_spec`, `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`, `status`, `product_category`, `product_type`, `list_price`, `crm_status`, `business_unit`, `product_line`, `org_id`, `source_created_at`, `crm_state_code`, `crm_status_code`, `product_stage`, `end_of_sale_date`, `end_of_support_date`, `end_of_life_date`, `last_renewal_date`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :product_code, :product_model, :product_name, :product_desc, :technical_spec, :source_system, :source_key, :source_version, :source_updated_at, :synced_at, :sync_status, :status, :product_category, :product_type, :list_price, :crm_status, :business_unit, :product_line, :org_id, :source_created_at, :crm_state_code, :crm_status_code, :product_stage, :end_of_sale_date, :end_of_support_date, :end_of_life_date, :last_renewal_date);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- A:GenericSyncJdbcStore.updateBatch:PRODUCT:ast_product_official_info; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `ast_product_official_info` SET `product_model` = CASE `id` WHEN :id THEN :product_model ELSE `product_model` END, `product_name` = CASE `id` WHEN :id THEN :product_name ELSE `product_name` END, `product_desc` = CASE `id` WHEN :id THEN :product_desc ELSE `product_desc` END, `technical_spec` = CASE `id` WHEN :id THEN :technical_spec ELSE `technical_spec` END, `source_version` = CASE `id` WHEN :id THEN :source_version ELSE `source_version` END, `source_updated_at` = CASE `id` WHEN :id THEN :source_updated_at ELSE `source_updated_at` END, `synced_at` = CASE `id` WHEN :id THEN :synced_at ELSE `synced_at` END, `sync_status` = CASE `id` WHEN :id THEN :sync_status ELSE `sync_status` END, `status` = CASE `id` WHEN :id THEN :status ELSE `status` END, `product_category` = CASE `id` WHEN :id THEN :product_category ELSE `product_category` END, `product_type` = CASE `id` WHEN :id THEN :product_type ELSE `product_type` END, `list_price` = CASE `id` WHEN :id THEN :list_price ELSE `list_price` END, `crm_status` = CASE `id` WHEN :id THEN :crm_status ELSE `crm_status` END, `business_unit` = CASE `id` WHEN :id THEN :business_unit ELSE `business_unit` END, `product_line` = CASE `id` WHEN :id THEN :product_line ELSE `product_line` END, `org_id` = CASE `id` WHEN :id THEN :org_id ELSE `org_id` END, `source_created_at` = CASE `id` WHEN :id THEN :source_created_at ELSE `source_created_at` END, `crm_state_code` = CASE `id` WHEN :id THEN :crm_state_code ELSE `crm_state_code` END, `crm_status_code` = CASE `id` WHEN :id THEN :crm_status_code ELSE `crm_status_code` END, `product_stage` = CASE `id` WHEN :id THEN :product_stage ELSE `product_stage` END, `end_of_sale_date` = CASE `id` WHEN :id THEN :end_of_sale_date ELSE `end_of_sale_date` END, `end_of_support_date` = CASE `id` WHEN :id THEN :end_of_support_date ELSE `end_of_support_date` END, `end_of_life_date` = CASE `id` WHEN :id THEN :end_of_life_date ELSE `end_of_life_date` END, `last_renewal_date` = CASE `id` WHEN :id THEN :last_renewal_date ELSE `last_renewal_date` END, version = CASE id WHEN :id THEN :before_version_plus_bumps ELSE version END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
