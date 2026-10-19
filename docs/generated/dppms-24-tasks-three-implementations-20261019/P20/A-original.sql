-- P20 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [pm_project_product_line_real] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P20.json
SELECT l.*,
       u.projectCode AS upstream_projectCode, u.orderExecNumber AS upstream_orderExecNumber,
       u.productFirstName AS upstream_productFirstName, u.productName AS upstream_productName,
       u.productSubCode AS upstream_productSubCode, u.productSubModel AS upstream_productSubModel,
       u.productSubName AS upstream_productSubName, u.num AS upstream_num,
       u.memo AS upstream_memo, u.dataSource AS upstream_dataSource,
       COALESCE(u.matchCount,0) AS upstreamMatchCount
FROM pm_project_product_line_real l
LEFT JOIN (
  SELECT MIN(projectCode) AS projectCode, MIN(orderExecNumber) AS orderExecNumber,
    MIN(productFirstName) AS productFirstName, MIN(productName) AS productName,
    MIN(productSubCode) AS productSubCode, MIN(productSubModel) AS productSubModel,
    MIN(productSubName) AS productSubName, MIN(num) AS num, MIN(memo) AS memo,
    CASE WHEN COUNT(*)=1 THEN MAX(dataSource) ELSE NULL END AS dataSource, COUNT(*) AS matchCount
  FROM pm_project_real_product_line_from_sms
  GROUP BY CAST(projectCode AS BINARY), CAST(orderExecNumber AS BINARY),
    CAST(productFirstName AS BINARY), CAST(productName AS BINARY), CAST(productSubCode AS BINARY),
    CAST(productSubModel AS BINARY), CAST(productSubName AS BINARY), num, CAST(memo AS BINARY)
) u ON u.projectCode <=> l.projectCode
 AND CAST(u.projectCode AS BINARY) <=> CAST(l.projectCode AS BINARY)
 AND CAST(u.orderExecNumber AS BINARY) <=> CAST(l.orderExecNumber AS BINARY)
 AND CAST(u.productFirstName AS BINARY) <=> CAST(l.productFirstName AS BINARY)
 AND CAST(u.productName AS BINARY) <=> CAST(l.productName AS BINARY)
 AND CAST(u.productSubCode AS BINARY) <=> CAST(l.productSubCode AS BINARY)
 AND CAST(u.productSubModel AS BINARY) <=> CAST(l.productSubModel AS BINARY)
 AND CAST(u.productSubName AS BINARY) <=> CAST(l.productSubName AS BINARY)
 AND u.num <=> l.num AND CAST(u.memo AS BINARY) <=> CAST(l.memo AS BINARY);

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper.selectMaintenance; pms-module-integration/src/main/resources/mapper/sync/SyncRunMapper.xml:8; kind=select
SELECT id, status, config_snapshot FROM int_sync_run WHERE tenant_id=#{tenantId} AND deleted=FALSE
      AND (cache_pending=TRUE OR (status IN ('QUEUED','READING','VALIDATING','APPLYING') AND started_at < #{now}))
      ORDER BY id LIMIT 100;

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

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.selectRecordForUpdate; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:4; kind=select
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId}
          AND project_id=#{query.projectId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.selectBusinessRecordsForUpdate; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:8; kind=select
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND CAST(contract_no AS BINARY) <=> CAST(#{query.contractNo} AS BINARY)
          AND CAST(project_code AS BINARY) <=> CAST(#{query.projectCode} AS BINARY)
          AND CAST(execution_no AS BINARY) <=> CAST(#{query.executionNo} AS BINARY)
          AND CAST(product_category_name AS BINARY) <=> CAST(#{query.productCategoryName} AS BINARY)
          AND CAST(product_name AS BINARY) <=> CAST(#{query.productName} AS BINARY)
          AND CAST(product_sub_code AS BINARY) <=> CAST(#{query.productSubCode} AS BINARY)
          AND CAST(product_sub_model AS BINARY) <=> CAST(#{query.productSubModel} AS BINARY)
          AND CAST(product_sub_name AS BINARY) <=> CAST(#{query.productSubName} AS BINARY)
          AND quantity <=> #{query.quantity}
          AND CAST(memo AS BINARY) <=> CAST(#{query.memo} AS BINARY)
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.selectProjectPage; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:22; kind=select
SELECT * FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0'
        ORDER BY id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.countAllProjectRecords; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:26; kind=select
SELECT COUNT(*) FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.countProject; pms-module-project/src/main/resources/mapper/associated/ProjectRealProductMapper.xml:29; kind=select
SELECT COUNT(*) FROM proj_project_real_product WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSource; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:4; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSourceForUpdate; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:8; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
      AND source_project_key=#{query.sourceProjectKey} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectProjectSources; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:12; kind=select
SELECT * FROM proj_legacy_detail_source WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} ORDER BY id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSnapshot; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:15; kind=select
SELECT * FROM proj_legacy_detail_snapshot WHERE tenant_id=#{query.tenantId} AND id=#{query.id};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectBatch; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:18; kind=select
SELECT * FROM proj_legacy_detail_snapshot WHERE tenant_id=#{query.tenantId} AND source_id=#{query.sourceId} AND batch_key=#{query.batchKey};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.countRecords; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:42; kind=select
SELECT COUNT(*) FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectRecords; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:45; kind=select
SELECT * FROM proj_legacy_detail_record WHERE tenant_id=#{query.tenantId} AND snapshot_id=#{query.snapshotId} AND domain_code=#{query.domainCode}
     /* IF test=query.sourceKey != null */ AND source_key=#{query.sourceKey} /* END IF */ 
     /* IF test=query.parentDomain != null */ AND parent_domain=#{query.parentDomain} AND parent_source_key=#{query.parentSourceKey} /* END IF */ ORDER BY source_key,id
    LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper.selectSourceLicenseRecords; pms-module-project/src/main/resources/mapper/legacydetail/ProjectLegacyDetailMapper.xml:49; kind=select
SELECT r.* FROM proj_legacy_detail_record r
    JOIN proj_legacy_detail_snapshot s ON s.id=r.snapshot_id AND s.tenant_id=r.tenant_id
    WHERE r.tenant_id=#{query.tenantId} AND s.tenant_id=#{query.tenantId} AND s.source_id=#{query.sourceId}
      AND r.domain_code='licenses'
     /* IF test=query.afterId != null */ AND r.id>#{query.afterId} /* END IF */ 
    ORDER BY r.id LIMIT #{query.limit};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectIdentity; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:4; kind=select
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectIdentityForUpdate; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:7; kind=select
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND deleted=b'0' FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectExactCodeIds; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:10; kind=select
SELECT id FROM proj_project WHERE tenant_id=#{query.tenantId} AND project_code=#{query.projectCode}
    ORDER BY id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectRestorationIdentityForUpdate; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:147; kind=select
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectRestorationFingerprint; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:150; kind=select
SELECT SHA2(CAST(JSON_OBJECT(
      'id',p.`id`,
      'project_code',p.`project_code`,
      'code_root_id',p.`code_root_id`,
      'project_sequence',p.`project_sequence`,
      'code_rule_version',p.`code_rule_version`,
      'project_name',p.`project_name`,
      'parent_id',p.`parent_id`,
      'root_id',p.`root_id`,
      'tree_path',p.`tree_path`,
      'tree_depth',p.`tree_depth`,
      'tree_sort',p.`tree_sort`,
      'business_level_code',p.`business_level_code`,
      'business_level_name',p.`business_level_name`,
      'customer_id',p.`customer_id`,
      'customer_code',p.`customer_code`,
      'customer_name',p.`customer_name`,
      'manager_id',p.`manager_id`,
      'manager_employee_no',p.`manager_employee_no`,
      'manager_name',p.`manager_name`,
      'company_id',p.`company_id`,
      'company_code',p.`company_code`,
      'company_name',p.`company_name`,
      'department_id',p.`department_id`,
      'department_code',p.`department_code`,
      'department_name',p.`department_name`,
      'project_type',p.`project_type`,
      'signing_method',p.`signing_method`,
      'project_category',p.`project_category`,
      'implementation_mode',p.`implementation_mode`,
      'major_project_level',p.`major_project_level`,
      'market_code',p.`market_code`,
      'market_name',p.`market_name`,
      'system_code',p.`system_code`,
      'system_name',p.`system_name`,
      'expend_code',p.`expend_code`,
      'expend_name',p.`expend_name`,
      'industry_code',p.`industry_code`,
      'industry_name',p.`industry_name`,
      'customer_project_name',p.`customer_project_name`,
      'sales_type',p.`sales_type`,
      'business_type',p.`business_type`,
      'service_level_code',p.`service_level_code`,
      'not_track_reason',p.`not_track_reason`,
      'contract_no',p.`contract_no`,
      'implementation_location',p.`implementation_location`,
      'location_resolution_status',p.`location_resolution_status`,
      'creation_reason',p.`creation_reason`,
      'lifecycle_template_id',p.`lifecycle_template_id`,
      'lifecycle_template_revision_no',p.`lifecycle_template_revision_no`,
      'lifecycle_template_revision_id',p.`lifecycle_template_revision_id`,
      'template_load_method',p.`template_load_method`,
      'process_definition_key',p.`process_definition_key`,
      'process_definition_version',p.`process_definition_version`,
      'project_start_time',p.`project_start_time`,
      'project_refresh_time',p.`project_refresh_time`,
      'project_close_time',p.`project_close_time`,
      'source_type',p.`source_type`,
      'status',p.`status`,
      'progress',p.`progress`,
      'aggregation_weight',p.`aggregation_weight`,
      'weight_source',p.`weight_source`,
      'version',p.`version`,
      'creator',p.`creator`,
      'create_time',p.`create_time`,
      'updater',p.`updater`,
      'update_time',p.`update_time`,
      'deleted',CAST(p.`deleted` AS UNSIGNED),
      'deleted_time',p.`deleted_time`,
      'tenant_id',p.`tenant_id`,
      'lifecycle_status',p.`lifecycle_status`,
      'current_stage',p.`current_stage`,
      'assignment_status',p.`assignment_status`,
      'task_tree_version',p.`task_tree_version`,
      'task_progress_version',p.`task_progress_version`,
      'project_end_date',p.`project_end_date`,
      'closure_policy_snapshot',p.`closure_policy_snapshot`,
      'closure_type',p.`closure_type`,
      'closed_from_stage',p.`closed_from_stage`,
      'closed_at',p.`closed_at`,
      'active_plan_version_id',p.`active_plan_version_id`,
      'custom_info',p.`custom_info`,
      'order_create_time',p.`order_create_time`,
      'disabled',CAST(p.`disabled` AS UNSIGNED),
      'effective_from',p.`effective_from`,
      'effective_to',p.`effective_to`) AS CHAR),256)
    FROM proj_project p WHERE p.tenant_id=#{query.tenantId} AND p.id=#{query.projectId}
      AND p.deleted=b'1' AND p.source_type='MIGRATION' AND p.parent_id IS NULL
      AND p.root_id=p.id AND p.code_root_id=p.id AND p.assignment_status='UNASSIGNED'
      AND p.active_plan_version_id IS NULL AND p.lifecycle_template_id IS NULL AND p.manager_id IS NULL;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.selectRestorationReferenceCount; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:157; kind=select
SELECT (SELECT COUNT(*) FROM `proj_business_result_channel` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_legacy_detail_source` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_node_overdue_completion` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_preparation_confirmation` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_company_department_relation` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_deliverable` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_exit_record` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_gate` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_governance_action` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_lease_product` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_member_assignment` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_milestone` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_node_execution` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_party` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_plan_version` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_portfolio_member` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_product_configuration` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_progress_fact` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_progress_snapshot` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_real_product` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_risk` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_site` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_stage` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_stage_execution_contract` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_stage_snapshot` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_stage_transition` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_task` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_task_responsible` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_project_template_match_history` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_result_evidence_item` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_result_evidence_scan` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_result_subscription` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_result_subscription_candidate` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_task_business_link` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_task_dependency` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_task_tree_path` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM `proj_team_batch_change_item` WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId})
      +(SELECT COUNT(*) FROM proj_project WHERE tenant_id=#{query.tenantId} AND parent_id=#{query.projectId});

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectEndDateForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:17; kind=select
SELECT * FROM proj_project WHERE id = #{query.projectId} AND tenant_id = #{query.tenantId}
          AND deleted = b'0' FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectListByIdsForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:28; kind=select
SELECT *
        FROM proj_project
        WHERE id IN
         /* FOREACH collection=ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ 
          AND deleted = b'0'
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.selectVisiblePage; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:104; kind=select
SELECT p.*
        FROM proj_project p
        WHERE p.tenant_id = #{query.tenantId}
          AND p.deleted = b'0'
          AND p.id IN
           /* FOREACH collection=query.visibleProjectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
           /* IF test=query.projectNameKeyword != null and query.projectNameKeyword != '' */ AND p.project_name LIKE CONCAT('%', #{query.projectNameKeyword}, '%') /* END IF */ 
           /* IF test=query.projectCodePrefix != null and query.projectCodePrefix != '' */ AND p.project_code LIKE CONCAT(#{query.projectCodePrefix}, '%') /* END IF */ 
           /* CHOOSE  */ /* WHEN test=query.status != null and query.status != '' and query.status.matches('S[0-6]') */ AND p.current_stage = #{query.status}
                  AND p.lifecycle_status = 'ACTIVE' /* END WHEN */ 
               /* OTHERWISE  */ /* IF test=query.status != null and query.status != '' */ AND p.lifecycle_status = #{query.status} /* END IF */ /* END OTHERWISE */ /* END CHOOSE */ 
           /* IF test=query.signingMethod != null and query.signingMethod != '' */ AND p.signing_method = #{query.signingMethod} /* END IF */ 
           /* IF test=query.projectCategory != null and query.projectCategory != '' */ AND p.project_category = #{query.projectCategory} /* END IF */ 
           /* IF test=query.implementationMode != null and query.implementationMode != '' */ AND p.implementation_mode = #{query.implementationMode} /* END IF */ 
           /* IF test=query.majorProjectLevel != null and query.majorProjectLevel != '' */ AND p.major_project_level = #{query.majorProjectLevel} /* END IF */ 
           /* IF test=query.contractNoKeyword != null and query.contractNoKeyword != '' */ AND p.contract_no LIKE CONCAT('%', #{query.contractNoKeyword}, '%') /* END IF */ 
           /* IF test=query.departmentId != null */ AND p.department_id = #{query.departmentId} /* END IF */ 
           /* IF test=query.companyId != null */ AND p.company_id = #{query.companyId} /* END IF */ 
           /* IF test=query.createTimeStart != null */ AND p.create_time >= #{query.createTimeStart} /* END IF */ 
           /* IF test=query.createTimeEnd != null */ AND p.create_time < #{query.createTimeEnd} /* END IF */ 
           /* IF test=query.closeTimeStart != null */ AND p.project_close_time >= #{query.closeTimeStart} /* END IF */ 
           /* IF test=query.closeTimeEnd != null */ AND p.project_close_time < #{query.closeTimeEnd} /* END IF */ 
           /* IF test=query.refreshTimeStart != null */ AND p.project_refresh_time >= #{query.refreshTimeStart} /* END IF */ 
           /* IF test=query.refreshTimeEnd != null */ AND p.project_refresh_time < #{query.refreshTimeEnd} /* END IF */ 
           /* IF test=query.agentServiceProviderKeyword != null and query.agentServiceProviderKeyword != '' */ AND EXISTS (
                  SELECT 1 FROM proj_project_party pp
                  WHERE pp.project_id = p.id
                    AND pp.tenant_id = #{query.tenantId}
                    AND pp.deleted = b'0'
                    AND pp.status = 'ACTIVE'
                    AND (pp.effective_from IS NULL OR pp.effective_from <= #{query.effectiveAt})
                    AND (pp.effective_to IS NULL OR pp.effective_to > #{query.effectiveAt})
                    AND pp.party_role IN ('AGENT', 'SERVICE_PROVIDER')
                    AND (pp.party_name LIKE CONCAT('%', #{query.agentServiceProviderKeyword}, '%')
                      OR pp.party_code LIKE CONCAT('%', #{query.agentServiceProviderKeyword}, '%'))
              ) /* END IF */ 
        ORDER BY p.id DESC;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectCurrentResponsibilityForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:5; kind=select
SELECT *
        FROM proj_project_member_assignment
        WHERE project_id = #{query.projectId}
          AND member_role = #{query.memberRole}
           /* CHOOSE  */ /* WHEN test=query.assignmentType == "PRIMARY" */ AND (assignment_type = 'PRIMARY' OR assignment_type IS NULL) /* END WHEN */ 
               /* OTHERWISE  */ AND assignment_type = #{query.assignmentType} /* END OTHERWISE */ /* END CHOOSE */ 
          AND ((site_id = #{query.siteId}) OR (site_id IS NULL AND #{query.siteId} IS NULL))
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectActiveByUserForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:28; kind=select
SELECT *
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND user_id = #{query.userId}
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectResponsibilityNodeCount; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:55; kind=select
SELECT COUNT(*)
        FROM proj_project p
        WHERE p.tenant_id = #{query.tenantId}
        AND p.id IN
         /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
         /* IF test=query.filterProjectId != null */ AND p.id = #{query.filterProjectId} /* END IF */ 
        AND p.deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectResponsibilityNodePage; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:61; kind=select
SELECT p.id, p.project_code, p.project_name, p.parent_id, p.tree_path, p.tree_depth,
               p.tree_sort, p.assignment_status
        FROM proj_project p
        WHERE p.tenant_id = #{query.tenantId}
        AND p.id IN
         /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
         /* IF test=query.filterProjectId != null */ AND p.id = #{query.filterProjectId} /* END IF */ 
        AND p.deleted = b'0'
        ORDER BY p.tree_path, p.tree_sort, p.id
        LIMIT #{query.limit} OFFSET #{query.offset};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectCurrentServiceManagerAssignments; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:71; kind=select
SELECT id, project_id, user_id, employee_no, member_name, department_id, department_code,
               department_name, member_role, assignment_type, site_id, change_reason, effective_from
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id IN
           /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ 
          AND member_role IN ('SERVICE_MANAGER_L1', 'SERVICE_MANAGER_L2')
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY project_id, member_role, site_id, department_id,
                 CASE WHEN assignment_type = 'PRIMARY' OR assignment_type IS NULL THEN 0 ELSE 1 END,
                 id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectParticipantFacts; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:91; kind=select
SELECT id, project_id, user_id, member_role, assignment_type, version, tenant_id
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
           /* IF test=query.subjectUserId != null */ AND user_id = #{query.subjectUserId} /* END IF */ 
           /* CHOOSE  */ /* WHEN test=query.requiredRoleCodes != null and !query.requiredRoleCodes.isEmpty() */ AND member_role IN
                   /* FOREACH collection=query.requiredRoleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */ 
          AND (member_role = 'PROJECT_MANAGER' OR assignment_type = 'PRIMARY' OR assignment_type IS NULL)
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY user_id, member_role, id;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectParticipantFactsForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:119; kind=select
SELECT id, project_id, user_id, member_role, assignment_type, version, tenant_id
        FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND user_id = #{query.userId}
           /* CHOOSE  */ /* WHEN test=query.requiredRoleCodes != null and !query.requiredRoleCodes.isEmpty() */ AND member_role IN
                   /* FOREACH collection=query.requiredRoleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1 = 0 /* END OTHERWISE */ /* END CHOOSE */ 
          AND (member_role = 'PROJECT_MANAGER' OR assignment_type = 'PRIMARY' OR assignment_type IS NULL)
          AND authorization_confirmed = b'1' AND status = 'ACTIVE'
          AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= CURRENT_TIMESTAMP(3))
          AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP(3))
        ORDER BY user_id, member_role, id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectProjectManagersForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:163; kind=select
SELECT * FROM proj_project_member_assignment
        WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId}
          AND member_role='PROJECT_MANAGER' AND authorization_confirmed = b'1' AND status='ACTIVE' AND deleted=b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper.selectActiveMemberIdentityForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMemberAssignmentMapper.xml:172; kind=select
SELECT * FROM proj_project_member_assignment
        WHERE tenant_id = #{query.tenantId} AND project_id = #{query.projectId}
          AND user_id = #{query.userId}
          AND member_role IN
           /* FOREACH collection=query.roleCodes; item=roleCode; open=(; separator=,; close=) */ (#{roleCode}) /* repeat with separator=,; END FOREACH */ 
          AND authorization_confirmed = b'1' AND status = 'ACTIVE' AND deleted = b'0'
          AND (effective_from IS NULL OR effective_from <= #{query.effectiveAt})
          AND (effective_to IS NULL OR effective_to > #{query.effectiveAt})
        ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.selectLatestActive; pms-module-project/src/main/resources/mapper/projecttree/ProjectTreeVersionMapper.xml:12; kind=select
SELECT id, root_project_id, tree_version, status, change_batch_id, node_count,
        path_count, activated_at, failed_reason, version,
        creator, create_time, updater, update_time, deleted, tenant_id
        FROM proj_project_tree_version
        WHERE root_project_id = #{rootProjectId}
          AND status = 'ACTIVE'
          AND deleted = b'0'
        ORDER BY tree_version DESC, id DESC
        LIMIT 1;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.selectLatest; pms-module-project/src/main/resources/mapper/projecttree/ProjectTreeVersionMapper.xml:23; kind=select
SELECT id, root_project_id, tree_version, status, change_batch_id, node_count,
        path_count, activated_at, failed_reason, version,
        creator, create_time, updater, update_time, deleted, tenant_id
        FROM proj_project_tree_version
        WHERE root_project_id = #{rootProjectId}
          AND deleted = b'0'
        ORDER BY tree_version DESC, id DESC
        LIMIT 1;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.selectLatestActiveForUpdate; pms-module-project/src/main/resources/mapper/projecttree/ProjectTreeVersionMapper.xml:33; kind=select
SELECT id, root_project_id, tree_version, status, change_batch_id, node_count,
        path_count, activated_at, failed_reason, version,
        creator, create_time, updater, update_time, deleted, tenant_id
        FROM proj_project_tree_version
        WHERE root_project_id = #{rootProjectId}
          AND status = 'ACTIVE'
          AND deleted = b'0'
        ORDER BY tree_version DESC, id DESC
        LIMIT 1
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper.selectLatestForUpdate; pms-module-project/src/main/resources/mapper/projecttree/ProjectTreeVersionMapper.xml:45; kind=select
SELECT id, root_project_id, tree_version, status, change_batch_id, node_count,
        path_count, activated_at, failed_reason, version,
        creator, create_time, updater, update_time, deleted, tenant_id
        FROM proj_project_tree_version
        WHERE root_project_id = #{rootProjectId}
          AND deleted = b'0'
        ORDER BY tree_version DESC, id DESC
        LIMIT 1
        FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- BaseMapper.insert:proj_project_real_product; pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/associated/ProjectRealProductDO.java:1; kind=orm_template
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `proj_project_real_product` (`id`, `tenant_id`, `project_id`, `contract_no`, `project_code`, `execution_no`, `product_category_name`, `product_name`, `product_sub_code`, `product_sub_model`, `product_sub_name`, `quantity`, `memo`, `data_source`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (:orm_id, :orm_tenant_id, :orm_project_id, :orm_contract_no, :orm_project_code, :orm_execution_no, :orm_product_category_name, :orm_product_name, :orm_product_sub_code, :orm_product_sub_model, :orm_product_sub_name, :orm_quantity, :orm_memo, :orm_data_source, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
