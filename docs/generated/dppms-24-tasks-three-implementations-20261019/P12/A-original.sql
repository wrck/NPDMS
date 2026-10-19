-- P12 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [fb_items] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P12.json
SELECT i.id,i.item,i.itemname,i.describe_ FROM fb_items i WHERE (:productCodes='' OR FIND_IN_SET(i.item,:productCodes));

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper.selectByProductCodeForUpdate; pms-module-asset/src/main/resources/mapper/product/AssetProductOfficialMapper.xml:4; kind=select
SELECT * FROM ast_product_official_info
        WHERE tenant_id=#{query.tenantId} AND product_code=#{query.productCode} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper.selectMaintenance; pms-module-integration/src/main/resources/mapper/sync/SyncRunMapper.xml:8; kind=select
SELECT id, status, config_snapshot FROM int_sync_run WHERE tenant_id=#{tenantId} AND deleted=FALSE
      AND (cache_pending=TRUE OR (status IN ('QUEUED','READING','VALIDATING','APPLYING') AND started_at < #{now}))
      ORDER BY id LIMIT 100;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- BaseMapper.insert:ast_product_official_info; pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/product/AssetProductOfficialDO.java:1; kind=orm_template
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_product_official_info` (`id`, `product_code`, `product_model`, `product_name`, `product_desc`, `technical_spec`, `document_id`, `source_system`, `source_key`, `source_version`, `source_updated_at`, `synced_at`, `sync_status`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (:orm_id, :orm_product_code, :orm_product_model, :orm_product_name, :orm_product_desc, :orm_technical_spec, :orm_document_id, :orm_source_system, :orm_source_key, :orm_source_version, :orm_source_updated_at, :orm_synced_at, :orm_sync_status, :orm_status, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_tenant_id);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
