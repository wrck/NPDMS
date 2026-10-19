-- P37 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectLicenseHistory] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P37SourceMapper.xml
SELECT id AS source_id,sn,specModel AS spec_model,`contract`,contractType AS contract_type,item,status,licenseCode AS license_code FROM pm_project_license_info_from_license ORDER BY id LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P37LicenseHistorySyncTask$TargetMapper.selectExisting; pms-module-integration/src/main/resources/mapper/basesync/P37TargetMapper.xml:4; kind=select
SELECT `id`,`tenant_id`,`device_id`,`contract_id`,`device_sn`,`contract_no`,`contract_type`,`product_code`,`product_model`,`license_status`,`creator`,`updater`,`create_time`,`update_time`,`deleted`,`license_code_ciphertext` FROM ast_device_license_record WHERE tenant_id=#{query.tenantId}  /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN  /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P37LicenseHistorySyncTask$TargetMapper.insertLicenseHistory; pms-module-integration/src/main/resources/mapper/basesync/P37TargetMapper.xml:5; kind=insert
INSERT INTO ast_device_license_record (`id`,`tenant_id`,`device_id`,`contract_id`,`device_sn`,`contract_no`,`contract_type`,`product_code`,`product_model`,`license_status`,`creator`,`updater`,`create_time`,`update_time`,`deleted`,`license_code_ciphertext`) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.deviceId},#{row.target.contractId},#{row.target.deviceSn},#{row.target.contractNo},#{row.target.contractType},#{row.target.productCode},#{row.target.productModel},#{row.target.licenseStatus},#{row.target.creator},#{row.target.updater},#{row.target.createTime},#{row.target.updateTime},#{row.target.deleted},#{row.target.licenseCodeCiphertext}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
