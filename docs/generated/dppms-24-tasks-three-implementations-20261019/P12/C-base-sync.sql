-- P12 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectSupplementProduct] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P12SourceMapper.xml
SELECT item AS product_code, itemname AS product_model, describe_ AS product_name, describe_ AS product_desc
    FROM fb_items ORDER BY item, id LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P12SupplementProductSyncTask$TargetMapper.selectSupplementTargets; pms-module-integration/src/main/resources/mapper/basesync/P12TargetMapper.xml:6; kind=select
SELECT product_code, product_model, product_name, product_desc, id, tenant_id, source_system, source_key, source_version, status, version, synced_at, creator, updater, create_time, update_time, deleted FROM ast_product_official_info
    WHERE tenant_id=#{query.tenantId}
     /* CHOOSE  */ /* WHEN test=query.productCodes != null and !query.productCodes.isEmpty() */ AND product_code IN  /* FOREACH collection=query.productCodes; item=code; open=(; close=); separator=, */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
     /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P12SupplementProductSyncTask$TargetMapper.insertSupplementProduct; pms-module-integration/src/main/resources/mapper/basesync/P12TargetMapper.xml:14; kind=insert
INSERT INTO ast_product_official_info
      (product_code, product_model, product_name, product_desc, id, tenant_id, source_system, source_key, source_version, status, version, synced_at, creator, updater, create_time, update_time, deleted, sync_status)
    VALUES
     /* FOREACH collection=rows; item=row; separator=, */ (#{row.productCode}, #{row.productModel}, #{row.productName}, #{row.productDesc}, #{row.id}, #{row.tenantId}, #{row.sourceSystem}, #{row.sourceKey}, #{row.sourceVersion}, #{row.status}, #{row.version}, #{row.syncedAt}, #{row.creator}, #{row.updater}, #{row.createTime}, #{row.updateTime}, #{row.deleted}, 'SUCCESS') /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
