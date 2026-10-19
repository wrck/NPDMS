-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P21 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectLease] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P21SourceMapper.xml
SELECT `projectCode` AS `project_code`, `orderExecNumber` AS `execution_no`, `productFirstName` AS `product_category`, `productName` AS `product_type`, `productSubCode` AS `product_code`, `productSubModel` AS `product_model`, `productSubName` AS `product_name`, `num` AS `quantity`, `memo` AS `memo`, `leaseDuration` AS `lease_duration`, `dataSource` AS `data_source` FROM `pm_project_product_lease_line_from_crm` LIMIT #{limit};

-- [selectConfiguration] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P21SourceMapper.xml
SELECT `projectCode` AS `project_code`, `orderExecNumber` AS `execution_no`, `itemGroup` AS `item_group`, `itemCode` AS `product_code`, `parentCode` AS `parent_code`, `quantity` AS `quantity`, `bomPaths` AS `bom_path`, `itemModel` AS `product_model`, `itemDesc` AS `product_description`, `level` AS `level`, `dataSource` AS `data_source` FROM `pm_project_product_config_level_info_from_crm` LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P21ProjectCacheSyncTask$TargetMapper.selectLeaseIds; pms-module-integration/src/main/resources/mapper/basesync/P21TargetMapper.xml:4; kind=select
SELECT id FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId}  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P21ProjectCacheSyncTask$TargetMapper.selectConfigurationIds; pms-module-integration/src/main/resources/mapper/basesync/P21TargetMapper.xml:9; kind=select
SELECT id FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId}  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P21ProjectCacheSyncTask$TargetMapper.nullableColumns; pms-module-integration/src/main/resources/mapper/basesync/P21TargetMapper.xml:14; kind=select
SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND (table_name='proj_project_lease_product' OR table_name='proj_project_product_configuration') AND column_name='project_id' AND is_nullable='YES';

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P21ProjectCacheSyncTask$TargetMapper.insertLease; pms-module-integration/src/main/resources/mapper/basesync/P21TargetMapper.xml:6; kind=insert
INSERT INTO proj_project_lease_product (`id`, `tenant_id`, `project_code`, `execution_no`, `product_category`, `product_type`, `product_code`, `product_model`, `product_name`, `quantity`, `memo`, `lease_duration`, `data_source`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
     /* FOREACH collection=rows; item=row; separator=, */ (#{row.id}, #{row.tenantId}, #{row.projectCode}, #{row.executionNo}, #{row.productCategory}, #{row.productType}, #{row.productCode}, #{row.productModel}, #{row.productName}, #{row.quantity}, #{row.memo}, #{row.leaseDuration}, #{row.dataSource}, #{row.creator}, #{row.createTime}, #{row.updater}, #{row.updateTime}, #{row.deleted}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P21ProjectCacheSyncTask$TargetMapper.insertConfiguration; pms-module-integration/src/main/resources/mapper/basesync/P21TargetMapper.xml:11; kind=insert
INSERT INTO proj_project_product_configuration (`id`, `tenant_id`, `project_code`, `execution_no`, `item_group`, `product_code`, `parent_code`, `quantity`, `bom_path`, `product_model`, `product_description`, `level`, `data_source`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
     /* FOREACH collection=rows; item=row; separator=, */ (#{row.id}, #{row.tenantId}, #{row.projectCode}, #{row.executionNo}, #{row.itemGroup}, #{row.productCode}, #{row.parentCode}, #{row.quantity}, #{row.bomPath}, #{row.productModel}, #{row.productDescription}, #{row.level}, #{row.dataSource}, #{row.creator}, #{row.createTime}, #{row.updater}, #{row.updateTime}, #{row.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P21ProjectCacheSyncTask$TargetMapper.deleteLease; pms-module-integration/src/main/resources/mapper/basesync/P21TargetMapper.xml:5; kind=delete
DELETE FROM proj_project_lease_product WHERE tenant_id=#{query.tenantId};

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P21ProjectCacheSyncTask$TargetMapper.deleteConfiguration; pms-module-integration/src/main/resources/mapper/basesync/P21TargetMapper.xml:10; kind=delete
DELETE FROM proj_project_product_configuration WHERE tenant_id=#{query.tenantId};

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
