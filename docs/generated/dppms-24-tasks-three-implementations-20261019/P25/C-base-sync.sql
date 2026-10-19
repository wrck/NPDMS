-- P25 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectDuplicateIdentity] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P25SourceMapper.xml
SELECT 1 FROM fb_service GROUP BY BINARY id,BINARY COALESCE(barcode,'') HAVING COUNT(*)>1 LIMIT 1;

-- [selectWarranty] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P25SourceMapper.xml
SELECT `id` AS `source_id`, `con_xb` AS `warranty_contract_no`, `barcode` AS `device_sn`, `grade` AS `warranty_grade`, `begin_date` AS `warranty_start_date`, `end_date` AS `warranty_end_date`, `warranty` AS `warranty_years`, `remark` AS `remark`, `isyb` AS `extended`, `state` AS `is_latest` FROM fb_service LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P25WarrantySyncTask$TargetMapper.selectExisting; pms-module-integration/src/main/resources/mapper/basesync/P25TargetMapper.xml:4; kind=select
SELECT `id`, `tenant_id`, `device_sn`, `warranty_contract_no`, `warranty_grade`, `warranty_start_date`, `warranty_end_date`, `warranty_months`, `extended`, `is_latest`, `remark`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted` FROM ast_device_warranty_renewal_record
    WHERE tenant_id=#{query.tenantId}
     /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN
       /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P25WarrantySyncTask$TargetMapper.insertWarranty; pms-module-integration/src/main/resources/mapper/basesync/P25TargetMapper.xml:10; kind=insert
INSERT INTO ast_device_warranty_renewal_record (`id`, `tenant_id`, `device_sn`, `warranty_contract_no`, `warranty_grade`, `warranty_start_date`, `warranty_end_date`, `warranty_months`, `extended`, `is_latest`, `remark`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
     /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id}, #{row.target.tenantId}, #{row.target.deviceSn}, #{row.target.warrantyContractNo}, #{row.target.warrantyGrade}, #{row.target.warrantyStartDate}, #{row.target.warrantyEndDate}, #{row.target.warrantyMonths}, #{row.target.extended}, #{row.target.isLatest}, #{row.target.remark}, #{row.target.version}, #{row.target.creator}, #{row.target.createTime}, #{row.target.updater}, #{row.target.updateTime}, #{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P25WarrantySyncTask$TargetMapper.updateWarranty; pms-module-integration/src/main/resources/mapper/basesync/P25TargetMapper.xml:13; kind=update
UPDATE ast_device_warranty_renewal_record SET `device_sn`=#{row.target.deviceSn}, `warranty_contract_no`=#{row.target.warrantyContractNo}, `warranty_grade`=#{row.target.warrantyGrade}, `warranty_start_date`=#{row.target.warrantyStartDate}, `warranty_end_date`=#{row.target.warrantyEndDate}, `warranty_months`=#{row.target.warrantyMonths}, `extended`=#{row.target.extended}, `is_latest`=#{row.target.isLatest}, `remark`=#{row.target.remark}, `version`=#{row.target.version}, `updater`=#{row.target.updater}, `update_time`=#{row.target.updateTime} WHERE tenant_id=#{row.target.tenantId} AND id=#{row.target.id} AND version=#{row.expectedVersion} AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
