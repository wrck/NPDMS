-- P12 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [fb_items] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT i.id,i.item,i.itemname,i.describe_ FROM fb_items i WHERE (:productCodes='' OR FIND_IN_SET(i.item,:productCodes));

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductMapper.selectByProductCodeForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProductMapper.xml:4; kind=select
SELECT id,tenant_id,product_code,product_name,product_model,product_desc,status,source_system,source_key,sync_status,
      synced_at,version,creator,create_time,updater,update_time,deleted
    FROM ast_product_official_info WHERE tenant_id=#{query.tenantId} AND product_code=#{query.productCode} FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductMapper.insertProduct; pms-module-integration/src/main/resources/mapper/sync/DirectProductMapper.xml:9; kind=insert
INSERT INTO ast_product_official_info(id,tenant_id,product_code,product_name,product_desc,product_model,status,version,
      source_system,source_key,sync_status,synced_at,creator,updater,deleted)
    VALUES(#{row.id},#{row.tenantId},#{row.productCode},#{row.productName},#{row.productDesc},#{row.productModel},#{row.status},#{row.version},
      #{row.sourceSystem},#{row.sourceKey},#{row.syncStatus},#{row.syncedAt},#{row.creator},#{row.updater},FALSE);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
