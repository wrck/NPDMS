-- P34 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [fb_soft_version] TABLE_READER_PROJECTION; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P34.json
SELECT `serial_number`, `conp`, `cpld`, `boot`, `pcb` FROM `fb_soft_version`;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMigrationIdentityMapper.selectIdentityForUpdate; pms-module-asset/src/main/resources/mapper/device/DeviceMigrationIdentityMapper.xml:4; kind=select
SELECT * FROM ast_device WHERE tenant_id=#{query.tenantId} AND sn=#{query.deviceSn} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionRecordMapper.selectByIdForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceFactoryVersionRecordMapper.xml:4; kind=select
SELECT * FROM ast_device_factory_version_record WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionRecordMapper.selectGlobalRecordForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceFactoryVersionRecordMapper.xml:8; kind=select
SELECT * FROM ast_device_factory_version_record
        WHERE tenant_id=#{query.tenantId} AND project_id IS NULL AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareChangeBatchMapper.selectLegacyForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceSoftwareChangeBatchMapper.xml:4; kind=select
SELECT * FROM ast_device_software_change_batch WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND id=#{query.recordId} AND deleted=0 FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareChangeBatchMapper.selectRecordForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceSoftwareChangeBatchMapper.xml:17; kind=select
SELECT * FROM ast_device_software_change_batch WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND id=#{query.recordId} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareVersionRecordMapper.selectForUpdate; pms-module-asset/src/main/resources/mapper/version/DeviceSoftwareVersionRecordMapper.xml:16; kind=select
SELECT *
        FROM ast_device_software_version_record
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.recordId}
          AND deleted = b'0'
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareVersionRecordMapper.selectCurrentPredecessor; pms-module-asset/src/main/resources/mapper/version/DeviceSoftwareVersionRecordMapper.xml:25; kind=select
SELECT * FROM ast_device_software_version_record
    WHERE tenant_id=#{query.tenantId} AND device_id=#{query.deviceId} AND deleted=0
      AND data_source=#{query.dataSource} AND recorded_at <=> #{query.recordedAt}
     /* IF test=query.requestKey != null */ AND request_key=#{query.requestKey} /* END IF */ 
    ORDER BY create_time DESC, id DESC LIMIT 1;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.ExternalKeyMappingMapper.selectSourcePage; pms-module-platform/src/main/resources/mapper/migration/ExternalKeyMappingMapper.xml:16; kind=select
SELECT id,batch_id,source_record_id,result_type,target_context,target_object_type,
               target_table,target_id,target_role,target_sequence,tenant_id,create_time,
               update_time,creator,updater,deleted
        FROM plt_external_key_mapping FORCE INDEX (idx_plt_external_mapping_batch)
        WHERE tenant_id = #{query.tenantId} AND batch_id = #{query.batchId} AND deleted = 0
         /* CHOOSE  */ /* WHEN test=query.sourceRecordIds != null and query.sourceRecordIds.size() > 0 */ AND source_record_id IN
                 /* FOREACH collection=query.sourceRecordIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationBatchMapper.selectByTenantAndIdForUpdate; pms-module-platform/src/main/resources/mapper/migration/MigrationBatchMapper.xml:6; kind=select
SELECT * FROM plt_migration_batch
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.batchId}
          AND deleted = b'0'
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationBatchMapper.selectNextStagedForUpdate; pms-module-platform/src/main/resources/mapper/migration/MigrationBatchMapper.xml:15; kind=select
SELECT * FROM plt_migration_batch
        WHERE tenant_id = #{query.tenantId}
          AND owner_context_code = #{query.ownerContextCode}
          AND purpose_code = #{query.purposeCode}
          AND batch_status = 'STAGED_READY'
          AND deleted = b'0'
          AND source_system IN
           /* FOREACH collection=query.sourceSystems; item=value; open=(; separator=,; close=) */ (#{value}) /* repeat with separator=,; END FOREACH */ 
          AND source_table IN
           /* FOREACH collection=query.sourceTables; item=value; open=(; separator=,; close=) */ (#{value}) /* repeat with separator=,; END FOREACH */ 
        ORDER BY create_time ASC, id ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.selectByBatchAndIdForUpdate; pms-module-platform/src/main/resources/mapper/migration/MigrationSourceRecordMapper.xml:16; kind=select
SELECT * FROM plt_migration_source_record
        WHERE tenant_id = #{query.tenantId}
          AND batch_id = #{query.batchId}
          AND id = #{query.sourceRecordId}
          AND deleted = b'0'
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.selectCursorPage; pms-module-platform/src/main/resources/mapper/migration/MigrationSourceRecordMapper.xml:26; kind=select
SELECT s.*,
               CASE
                   WHEN EXISTS (SELECT 1 FROM plt_external_key_mapping m
                                WHERE m.tenant_id = s.tenant_id AND m.source_record_id = s.id
                                  AND m.result_type = 'MAPPED' AND m.deleted = b'0') THEN 'MAPPED'
                   WHEN EXISTS (SELECT 1 FROM plt_migration_issue i
                                WHERE i.tenant_id = s.tenant_id AND i.source_record_id = s.id
                                  AND i.deleted = b'0') THEN 'ISSUE'
                   WHEN EXISTS (SELECT 1 FROM plt_external_key_mapping m
                                WHERE m.tenant_id = s.tenant_id AND m.source_record_id = s.id
                                  AND m.result_type = 'RETAINED' AND m.deleted = b'0') THEN 'RETAINED'
                   ELSE NULL
               END AS result_type
        FROM plt_migration_source_record s
        WHERE s.tenant_id = #{query.tenantId}
          AND s.batch_id = #{query.batchId}
          AND s.deleted = b'0'
           /* IF test=query.afterSourceRecordId != null */ AND s.id > #{query.afterSourceRecordId} /* END IF */ 
        ORDER BY s.id ASC
        LIMIT #{query.limit};

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.selectClassificationSummary; pms-module-platform/src/main/resources/mapper/migration/MigrationSourceRecordMapper.xml:52; kind=select
SELECT COUNT(*) AS source_count,
               COALESCE(SUM(has_mapped), 0) AS mapped_count,
               COALESCE(SUM(has_issue), 0) AS issue_count,
               COALESCE(SUM(has_retained), 0) AS retained_count,
               COALESCE(SUM(has_mapped + has_issue + has_retained = 0), 0) AS unclassified_count,
               COALESCE(SUM(has_mapped + has_issue + has_retained > 1), 0) AS conflicting_count
        FROM (
            SELECT s.id,
                   EXISTS (SELECT 1 FROM plt_external_key_mapping m FORCE INDEX (uk_plt_external_mapping_result)
                           WHERE m.tenant_id = s.tenant_id AND m.source_record_id = s.id
                             AND m.result_type = 'MAPPED' AND m.deleted = b'0') AS has_mapped,
                   EXISTS (SELECT 1 FROM plt_migration_issue i FORCE INDEX (uk_plt_migration_issue_key)
                           WHERE i.tenant_id = s.tenant_id AND i.source_record_id = s.id
                             AND i.deleted = b'0') AS has_issue,
                   EXISTS (SELECT 1 FROM plt_external_key_mapping m FORCE INDEX (uk_plt_external_mapping_result)
                           WHERE m.tenant_id = s.tenant_id AND m.source_record_id = s.id
                             AND m.result_type = 'RETAINED' AND m.deleted = b'0') AS has_retained
            FROM plt_migration_source_record s FORCE INDEX (idx_plt_migration_source_cursor)
            WHERE s.tenant_id = #{query.tenantId}
              AND s.batch_id = #{query.batchId}
              AND s.deleted = b'0'
        ) classified;

-- cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.countBatchSources; pms-module-platform/src/main/resources/mapper/migration/MigrationSourceRecordMapper.xml:79; kind=select
SELECT COUNT(*) FROM plt_migration_source_record
    WHERE tenant_id = #{query.tenantId} AND batch_id = #{query.batchId} AND deleted = 0;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- BaseMapper.insert:ast_device_factory_version_record; pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/version/DeviceFactoryVersionRecordDO.java:1; kind=orm_template
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `ast_device_factory_version_record` (`id`, `tenant_id`, `project_id`, `device_sn`, `conp_version`, `boot_version`, `cpld_version`, `pcb_version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_project_id, :orm_device_sn, :orm_conp_version, :orm_boot_version, :orm_cpld_version, :orm_pcb_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
