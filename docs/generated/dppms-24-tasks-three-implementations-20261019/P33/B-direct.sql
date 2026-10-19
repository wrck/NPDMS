-- P33 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [INSTALLATION_HISTORY] TABLE_READER_PROJECTION; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT `id`, `projectId`, `barcode`, `itemCode`, `itemModel`, `itemName`, `receiveName`, `emsNum`, `emsCompany`, `packdate`, `contractNo`, `installAddress`, `chProjectId`, `chContractNo`, `transferProjectId`, `transferContractNo`, `transferFlag`, `createTime`, `createBy`, `updateTime`, `updateBy` FROM `pm_project_shipment`;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectInstallationMapper.selectBoundRecordForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectInstallationMapper.xml:4; kind=select
SELECT * FROM imp_eng_installation
        WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND id=#{query.installationId}
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectInstallationMapper.selectCurrentRecordForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectInstallationMapper.xml:25; kind=select
SELECT * FROM imp_eng_installation WHERE tenant_id=#{query.tenantId} AND equipment_id=#{query.equipmentId} AND status=2 AND effective_to IS NULL AND deleted=0 FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectInstallationMapper.selectRecordCodesIncludeDeleted; pms-module-integration/src/main/resources/mapper/sync/DirectInstallationMapper.xml:28; kind=select
SELECT code FROM imp_eng_installation WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND code IS NOT NULL;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectEvidenceMapper.selectSource; pms-module-integration/src/main/resources/mapper/sync/DirectProjectEvidenceMapper.xml:4; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectEvidenceMapper.selectSourceForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProjectEvidenceMapper.xml:8; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectEvidenceMapper.selectRecords; pms-module-integration/src/main/resources/mapper/sync/DirectProjectEvidenceMapper.xml:17; kind=select
SELECT * FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */ ORDER BY source_key,id
    LIMIT #{query.offset},#{query.limit};

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectInstallationMapper.insertHistorical; pms-module-integration/src/main/resources/mapper/sync/DirectInstallationMapper.xml:10; kind=insert
INSERT INTO imp_eng_installation
          (id,tenant_id,project_id,code,equipment_id,install_location,
           address_id,address_version,site_id,site_version,site_location_id,site_location_version,
           location_resolution_status,address_snapshot,location_snapshot,
           effective_from,effective_to,install_time,installer_user_id,environment_check,
           spec_check,photo_url,result,status,remark,version,creator,create_time,updater,update_time,deleted)
        VALUES
          (#{record.id},#{record.tenantId},#{record.projectId},#{record.code},#{record.equipmentId},#{record.installLocation},
           #{record.addressId},#{record.addressVersion},#{record.siteId},#{record.siteVersion},#{record.siteLocationId},#{record.siteLocationVersion},
           #{record.locationResolutionStatus},#{record.addressSnapshot},#{record.locationSnapshot},
           #{record.effectiveFrom},#{record.effectiveTo},#{record.installTime},#{record.installerUserId},#{record.environmentCheck},
           #{record.specCheck},#{record.photoUrl},#{record.result},#{record.status},#{record.remark},#{record.version},
           #{record.creator},#{record.createTime},#{record.updater},#{record.updateTime},#{record.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
