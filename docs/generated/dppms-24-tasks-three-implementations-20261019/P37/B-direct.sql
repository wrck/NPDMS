-- P37 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [LICENSE_HISTORY] OWNER_OR_CONTROLLED_INPUT; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
-- readMode=SECURE_LICENSE; object=LICENSE_HISTORY; 本地/受控Owner输入，实际Mapper查询及Java调用见本方式的查询和处理逻辑。;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectLicenseHistoryMapper.selectRecordForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectLicenseHistoryMapper.xml:4; kind=select
SELECT * FROM ast_device_license_record WHERE tenant_id=#{query.tenantId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectLicenseHistoryMapper.selectDevice; pms-module-integration/src/main/resources/mapper/sync/DirectLicenseHistoryMapper.xml:5; kind=select
SELECT id,tenant_id,sn,deleted FROM ast_device WHERE tenant_id=#{query.tenantId} AND sn=#{query.sn} AND deleted=0;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectLicenseHistoryMapper.selectContractIds; pms-module-integration/src/main/resources/mapper/sync/DirectLicenseHistoryMapper.xml:6; kind=select
SELECT id FROM com_contract WHERE tenant_id=#{query.tenantId} AND contract_no=#{query.contractNo} AND deleted=0 LIMIT 2;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectLicenseHistoryMapper.insert; pms-module-integration/src/main/resources/mapper/sync/DirectLicenseHistoryMapper.xml:9; kind=insert
INSERT INTO ast_device_license_record(id,tenant_id,device_id,device_sn,contract_id,contract_no,contract_type,product_code,product_model,license_status,license_code_ciphertext,creator,create_time,updater,update_time,deleted)
VALUES(#{row.id},#{row.tenantId},#{row.deviceId},#{row.deviceSn},#{row.contractId},#{row.contractNo},#{row.contractType},#{row.productCode},#{row.productModel},#{row.licenseStatus},#{row.licenseCodeCiphertext},#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},#{row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
