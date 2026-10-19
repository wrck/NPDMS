-- P35 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [SOFTWARE_BATCH] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT l.id AS _groupKey,l.projectId,
JSON_MERGE_PRESERVE(
 JSON_ARRAY(JSON_OBJECT('object','SOFTWARE_BATCH','fields',JSON_OBJECT('id',l.`id`,'projectId',l.`projectId`,'changeVersion',l.`changeVersion`,'changeRemark',l.`changeRemark`,'latest',l.`latest`,'createBy',l.`createBy`,'createTime',l.`createTime`,'updateBy',l.`updateBy`,'updateTime',l.`updateTime`))),
 COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('object','SOFTWARE_VERSION','fields',JSON_OBJECT('id',v.`id`,'projectId',v.`projectId`,'logId',v.`logId`,'contractNo',v.`contractNo`,'itemCode',v.`itemCode`,'barCode',v.`barCode`,'conp',v.`conp`,'conpType',v.`conpType`,'conpSeries',v.`conpSeries`,'conpMark',v.`conpMark`,'conpBak',v.`conpBak`,'conpChange',v.`conpChange`,'cpld',v.`cpld`,'cpldBak',v.`cpldBak`,'cpldChange',v.`cpldChange`,'boot',v.`boot`,'bootBak',v.`bootBak`,'bootChange',v.`bootChange`,'pcb',v.`pcb`,'pcbBak',v.`pcbBak`,'pcbChange',v.`pcbChange`,'executeTime',v.`executeTime`,'datastate',v.`datastate`,'createTime',v.`createTime`,'createBy',v.`createBy`,'updateTime',v.`updateTime`,'updateBy',v.`updateBy`,'customInfo',v.`customInfo`)))
 FROM pm_project_soft_version v WHERE v.logId=l.id),JSON_ARRAY())) AS _sourceRows
FROM pm_project_soft_change_logs l;

-- [SOFTWARE_VERSION] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
-- queryRef=SOFTWARE_BATCH; 复用该来源结果/分组，不重复执行一份SELECT。;

-- ===== 2. 目标/辅助查询与前置 =====

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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectSoftwareBatchMapper.selectRecordForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectSoftwareBatchMapper.xml:10; kind=select
SELECT * FROM ast_device_software_change_batch WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND id=#{query.recordId} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectSoftwareHistoryMapper.selectForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectSoftwareHistoryMapper.xml:16; kind=select
SELECT *
        FROM ast_device_software_version_record
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.recordId}
          AND deleted = b'0'
        FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectSoftwareBatchMapper.insertHistorical; pms-module-integration/src/main/resources/mapper/sync/DirectSoftwareBatchMapper.xml:4; kind=insert
INSERT INTO ast_device_software_change_batch
        (id,tenant_id,project_id,change_version,change_remark,latest,creator,create_time,updater,update_time,deleted)
        VALUES (#{row.values.id},#{row.values.tenantId},#{row.values.projectId},#{row.values.changeVersion},#{row.values.changeRemark},#{row.values.latest},
                #{row.values.creator},#{row.values.createTime},#{row.values.updater},#{row.values.updateTime},#{row.values.deleted});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectSoftwareHistoryMapper.insertHistorical; pms-module-integration/src/main/resources/mapper/sync/DirectSoftwareHistoryMapper.xml:4; kind=insert
INSERT INTO ast_device_software_version_record
        (id,tenant_id,device_id,device_sn,project_id,previous_record_id,conp_version,conp_type,conp_series,conp_mark,
         boot_version,cpld_version,pcb_version,customized,release_date,version_desc,recorded_at,data_source,
         parsed_fact_id,request_key,change_batch_id,contract_no,product_code,execute_time,data_state,custom_info,
         creator,create_time,updater,update_time,deleted)
        VALUES (#{row.values.id},#{row.values.tenantId},#{row.values.deviceId},#{row.values.deviceSn},#{row.values.projectId},#{row.values.previousRecordId},
         #{row.values.conpVersion},#{row.values.conpType},#{row.values.conpSeries},#{row.values.conpMark},#{row.values.bootVersion},#{row.values.cpldVersion},
         #{row.values.pcbVersion},#{row.values.customized},#{row.values.releaseDate},#{row.values.versionDesc},#{row.values.recordedAt},#{row.values.dataSource},
         #{row.values.parsedFactId},#{row.values.requestKey},#{row.values.changeBatchId},#{row.values.contractNo},#{row.values.productCode},#{row.values.executeTime},
         #{row.values.dataState},#{row.values.customInfo},#{row.values.creator},#{row.values.createTime},#{row.values.updater},#{row.values.updateTime},#{row.values.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
