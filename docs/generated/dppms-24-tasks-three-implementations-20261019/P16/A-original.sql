-- P16 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [pm_project_header] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P16.json
SELECT CAST(p.projectId AS CHAR) AS _groupKey,
  CAST(JSON_MERGE_PRESERVE(
    JSON_ARRAY(JSON_OBJECT('object','pm_project_header','fields',JSON_OBJECT(
      'projectId',p.projectId,
      'projectType',p.projectType,
      'projectCode',p.projectCode,
      'projectName',p.projectName,
      'projectState',p.projectState,
      'isback',p.isback,
      'column001',p.column001,
      'column002',p.column002,
      'column003',p.column003,
      'column004',p.column004,
      'column005',p.column005,
      'column006',p.column006,
      'column007',p.column007,
      'column008',p.column008,
      'column009',p.column009,
      'column010',p.column010,
      'column011',p.column011,
      'column012',p.column012,
      'columno12_readonly',p.columno12_readonly,
      'column013',p.column013,
      'column014',p.column014,
      'customerProjectName',p.customerProjectName,
      'salesType',p.salesType,
      'majorProjectLevel',p.majorProjectLevel,
      'compId',p.compId,
      'createTime',p.createTime,
      'createBy',p.createBy,
      'updateTime',p.updateTime,
      'updateBy',p.updateBy,
      'effectiveFrom',p.effectiveFrom,
      'effectiveTo',p.effectiveTo,
      'disabled',CAST(p.disabled AS UNSIGNED),
      'projectStartTime',p.projectStartTime,
      'projectRefreshTime',p.projectRefreshTime,
      'projectCloseTime',p.projectCloseTime,
      'customInfo',p.customInfo,
      'customConfig',p.customConfig,
      'creatorFndSourceKey',MAX(CASE WHEN audit_role.kind=0 THEN actors.fndId END),
      'creatorTSourceKey',MAX(CASE WHEN audit_role.kind=0 THEN actors.tId END),
      'updaterFndSourceKey',MAX(CASE WHEN audit_role.kind=1 THEN actors.fndId END),
      'updaterTSourceKey',MAX(CASE WHEN audit_role.kind=1 THEN actors.tId END),
      'creatorFndMatchCount',COALESCE(MAX(CASE WHEN audit_role.kind=0 THEN actors.fndCount END),0),
      'creatorTMatchCount',COALESCE(MAX(CASE WHEN audit_role.kind=0 THEN actors.tCount END),0),
      'updaterFndMatchCount',COALESCE(MAX(CASE WHEN audit_role.kind=1 THEN actors.fndCount END),0),
      'updaterTMatchCount',COALESCE(MAX(CASE WHEN audit_role.kind=1 THEN actors.tCount END),0),
      'creatorFndSourceKeys',MAX(CASE WHEN audit_role.kind=0 THEN actors.fndIds END),
      'updaterFndSourceKeys',MAX(CASE WHEN audit_role.kind=1 THEN actors.fndIds END),
      'migrationCompanyCode',MAX(c.compCode)))),
    CAST(MAX(CAST(canonical.originalRows AS CHAR)) AS JSON)
  ) AS CHAR) AS _sourceRows
FROM pm_project p
JOIN (
  SELECT MAX(projectId) AS projectId,
    JSON_ARRAYAGG(CAST(CASE WHEN disabled=0 AND effectiveTo IS NULL THEN
      JSON_OBJECT('object','pm_project_identity','fields',JSON_OBJECT(
        'originalProjectId',projectId,'projectType',projectType,'projectCode',projectCode))
      ELSE 'null' END AS JSON)) AS originalRows
  FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)
) canonical ON canonical.projectId=p.projectId
LEFT JOIN t_company c ON c.id=p.compId
JOIN (SELECT 0 AS kind UNION ALL SELECT 1) audit_role
LEFT JOIN (
  SELECT loginKey,
    SUM(origin='FND') AS fndCount,SUM(origin='T') AS tCount,
    CASE WHEN SUM(origin='FND')=1 THEN MAX(CASE WHEN origin='FND' THEN actorId END) END AS fndId,
    CASE WHEN SUM(origin='T')=1 THEN MAX(CASE WHEN origin='T' THEN actorId END) END AS tId,
    GROUP_CONCAT(CASE WHEN origin='FND' THEN actorId END ORDER BY actorId) AS fndIds
  FROM (
    SELECT CAST(username AS BINARY) AS loginKey,'FND' AS origin,id AS actorId
    FROM fnd_user_info WHERE NOT FIND_IN_SET(id,:excludedFndActorIds)
    UNION ALL
    SELECT CAST(user_name AS BINARY),'T',user_id FROM t_user
  ) source_actors GROUP BY loginKey
) actors ON actors.loginKey=CAST(CASE WHEN audit_role.kind=0 THEN p.createBy ELSE p.updateBy END AS BINARY)
WHERE p.disabled=0 AND p.effectiveTo IS NULL
  AND FIND_IN_SET(p.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
GROUP BY p.projectId;

-- [pm_project_identity] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P16.json
-- queryRef=pm_project_header; 复用该来源结果/分组，不重复执行一份SELECT。;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectInstallationSelectionPage; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:4; kind=select
SELECT * FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId} AND deleted=FALSE
      AND object_key='INSTALLATION_HISTORY' AND source_object='pm_project_shipment'
     /* CHOOSE  */ /* WHEN test=query.deviceSns != null and !query.deviceSns.isEmpty() */ AND JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$.originalFields.barcode')) IN
       /* FOREACH collection=query.deviceSns; item=sn; open=(; separator=,; close=) */ (#{sn}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
     /* IF test=query.afterId != null */ AND id > #{query.afterId} /* END IF */ 
    ORDER BY id LIMIT 500;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectRetiredSourceKeysForUpdate; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:23; kind=select
SELECT * FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId}
      AND object_key=#{query.object} AND source_object=#{query.sourceObject} AND deleted=TRUE
     /* CHOOSE  */ /* WHEN test=query.sourceKeys != null and !query.sourceKeys.isEmpty() */ AND source_key IN  /* FOREACH collection=query.sourceKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectIdentitySources; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:31; kind=select
SELECT b.* FROM int_sync_binding b
    JOIN int_sync_task t ON t.id=b.task_id AND t.tenant_id=b.tenant_id AND t.deleted=FALSE
    WHERE b.tenant_id=#{query.tenantId} AND b.deleted=FALSE
      AND t.source_system=#{query.sourceSystem} AND t.connection_id=#{query.connectionId}
      AND (t.adapter=#{query.adapter}
         /* IF test=query.originalOnly and query.adapter == 'DPPMS_USERS' */ OR (t.adapter='DPPMS_ACCOUNT_FOUNDATION' AND b.object_key IN ('t_user','t_user_info','fnd_user_info') AND b.source_key NOT LIKE '%:%') /* END IF */ 
         /* IF test=!query.originalOnly */ /* CHOOSE  */ /* WHEN test=query.adapter == 'DPPMS_DEVICE_BOOTSTRAP' */ OR (t.adapter='DPPMS_DEVICE_BOOTSTRAP_DIRECT' AND b.object_key='DEVICE_BASE' AND b.source_object='ast_device_shipment') /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_PROJECT_MASTER' */ OR (t.adapter='DPPMS_PROJECT_MASTER_DIRECT' AND b.object_key IN ('pm_project_header','pm_project_identity')) /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_ORGANIZATION' */ OR (t.adapter='DPPMS_ORGANIZATION_DIRECT' AND b.object_key IN ('COMPANY','DEPARTMENT','fnd_department','ehr_department','COMPANY_REFERENCE')) /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_USERS' */ OR (t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT') AND b.object_key IN ('t_user','t_user_info','fnd_user_info')
                AND b.source_key NOT LIKE '%:%') /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_ROLES' */ OR (t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT') AND b.object_key IN ('T_ROLE','FND_ROLE')) /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_USER_ROLES' */ OR (t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT') AND b.object_key IN ('T_USER_ROLE','FND_USER_ROLE')) /* END WHEN */ 
           /* WHEN test=query.adapter == 'DPPMS_USER_SCOPES' */ OR (t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT') AND b.object_key IN ('t_user_info','fnd_user_info','fnd_user_power')
                AND b.source_key LIKE '%:%') /* END WHEN */ /* END CHOOSE */ /* END IF */ 
      ) AND b.source_object=#{query.sourceObject}
     /* CHOOSE  */ /* WHEN test=query.sourceKeys != null and !query.sourceKeys.isEmpty() */ AND b.source_key IN
         /* FOREACH collection=query.sourceKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
       /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY b.id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectEhrEmployeeUsers; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:76; kind=select
SELECT b.* FROM int_sync_binding b
    JOIN int_sync_task t ON t.id=b.task_id AND t.tenant_id=b.tenant_id AND t.deleted=FALSE
    WHERE b.tenant_id=#{query.tenantId} AND b.deleted=FALSE AND t.source_system='DPPMS'
      AND t.connection_id=#{query.connectionId} AND t.adapter IN ('DPPMS_ACCOUNT_FOUNDATION','DPPMS_ACCOUNT_FOUNDATION_DIRECT')
       /* IF test=query.originalOnly */ AND t.adapter='DPPMS_ACCOUNT_FOUNDATION' /* END IF */ 
      AND b.object_key IN ('t_user','fnd_user_info') AND b.object_key=b.source_object
      AND b.source_key NOT LIKE '%:%'
     /* CHOOSE  */ /* WHEN test=query.sourceEmployeeIds != null and !query.sourceEmployeeIds.isEmpty() */ AND JSON_UNQUOTE(JSON_EXTRACT(b.fields_json,'$._verifiedEhrEmployeeId')) IN
       /* FOREACH collection=query.sourceEmployeeIds; item=employee; open=(; separator=,; close=) */ (#{employee}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY b.id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectFirstTaskBindingId; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:90; kind=select
SELECT id FROM int_sync_binding
    WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND deleted=FALSE
    LIMIT 1;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectObsoleteOrderLinesForUpdate; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:112; kind=select
SELECT * FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId}
      AND object_key='LINE' AND source_object='pm_order_line_from_erp' AND deleted=FALSE
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$.companyCode')) AS BINARY)=CAST('01' AS BINARY)
     /* CHOOSE  */ /* WHEN test=query.sourceKeys != null and !query.sourceKeys.isEmpty() */ AND source_key IN  /* FOREACH collection=query.sourceKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper.selectMaintenance; pms-module-integration/src/main/resources/mapper/sync/SyncRunMapper.xml:8; kind=select
SELECT id, status, config_snapshot FROM int_sync_run WHERE tenant_id=#{tenantId} AND deleted=FALSE
      AND (cache_pending=TRUE OR (status IN ('QUEUED','READING','VALIDATING','APPLYING') AND started_at < #{now}))
      ORDER BY id LIMIT 100;

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

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.selectSourceForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectPartyMapper.xml:5; kind=select
SELECT * FROM proj_project_party
        WHERE tenant_id = #{query.tenantId} AND source_system = #{query.sourceSystem}
          AND source_table = #{query.sourceTable} AND source_record_key = #{query.sourceRecordKey}
          AND party_role = #{query.partyRole}
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.selectMigrationSourceForUpdate; pms-module-project/src/main/resources/mapper/projectmanual/ProjectPartyMapper.xml:12; kind=select
SELECT * FROM proj_project_party WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
          AND source_table=#{query.sourceTable} AND source_record_key=#{query.sourceKey} FOR UPDATE;

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

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.insertHistoricalRoot; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:37; kind=insert
INSERT INTO proj_project (
      id,tenant_id,project_code,project_name,code_root_id,project_sequence,code_rule_version,
      root_id,tree_path,tree_depth,tree_sort,department_id,department_code,department_name,
      company_id,company_code,company_name,project_type,project_category,signing_method,implementation_mode,
      customer_project_name,sales_type,major_project_level,market_name,system_name,expend_name,industry_name,
      not_track_reason,project_start_time,project_refresh_time,project_close_time,order_create_time,custom_info,
      disabled,effective_from,effective_to,creator,create_time,updater,update_time,
      source_type,status,lifecycle_status,current_stage,closed_at,assignment_status,version,deleted
    ) VALUES (
      #{query.facts.id},#{query.facts.tenantId},#{query.facts.projectCode},#{query.facts.projectName},#{query.facts.id},0,'V1',
      #{query.facts.id},'',0,0,#{query.departmentId},#{query.departmentCode},#{query.departmentName},
      #{query.facts.companyId},#{query.facts.companyCode},#{query.facts.companyName},#{query.facts.projectType},
      #{query.facts.projectCategory},#{query.facts.signingMethod},#{query.facts.implementationMode},
      #{query.facts.customerProjectName},#{query.facts.salesType},#{query.facts.majorProjectLevel},#{query.facts.marketName},
      #{query.facts.systemName},#{query.facts.expendName},#{query.facts.industryName},#{query.facts.notTrackReason},
      #{query.facts.projectStartTime},#{query.facts.projectRefreshTime},#{query.facts.projectCloseTime},
      #{query.facts.orderCreateTime},#{query.facts.customInfo},#{query.facts.disabled},#{query.facts.effectiveFrom},#{query.facts.effectiveTo},
      #{query.facts.creator},#{query.facts.createTime},#{query.facts.updater},#{query.facts.updateTime},
      'MIGRATION',#{query.facts.lifecycleStatus},#{query.facts.lifecycleStatus},#{query.facts.currentStage},
      #{query.facts.closedAt},'UNASSIGNED',#{query.facts.version},b'0'
    );

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.insertMigrationParty; pms-module-project/src/main/resources/mapper/projectmanual/ProjectPartyMapper.xml:16; kind=insert
INSERT INTO proj_project_party (id, tenant_id, project_id, party_role, party_code, party_name, contact_name, phone, source_system, source_table, source_record_key, effective_from, effective_to, status, version, creator, create_time, updater, update_time, deleted)
        VALUES (#{command.row.id}, #{command.row.tenantId}, #{command.row.projectId}, #{command.row.partyRole}, #{command.row.partyCode}, #{command.row.partyName}, #{command.row.contactName}, #{command.row.phone}, #{command.row.sourceSystem}, #{command.row.sourceTable}, #{command.row.sourceRecordKey}, #{command.row.effectiveFrom}, #{command.row.effectiveTo}, #{command.row.status}, #{command.row.version}, #{command.creator}, #{command.row.createTime}, #{command.updater}, #{command.row.updateTime}, #{command.row.deleted});

-- BaseMapper.insert:int_sync_binding; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/dataobject/sync/SyncBindingDO.java:1; kind=orm_template
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `int_sync_binding` (`id`, `tenant_id`, `task_id`, `object_key`, `source_object`, `source_key`, `target_id`, `fields_json`, `last_run_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `target_shared`)
VALUES (:orm_id, :orm_tenant_id, :orm_task_id, :orm_object_key, :orm_source_object, :orm_source_key, :orm_target_id, :orm_fields_json, :orm_last_run_id, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_target_shared);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.restoreVerifiedBinding; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:14; kind=update
UPDATE int_sync_binding SET deleted=FALSE
    WHERE id=#{original.id} AND tenant_id=#{original.tenantId} AND task_id=#{original.taskId}
      AND object_key=#{original.objectKey} AND source_object=#{original.sourceObject}
      AND source_key=#{original.sourceKey} AND target_id=#{original.targetId} AND deleted=TRUE
      AND last_run_id <=> #{original.lastRunId}
      AND target_shared <=> #{original.targetShared}
      AND fields_json=CAST(#{original.fieldsJson} AS JSON);

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.updateLastRun; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:95; kind=update
UPDATE int_sync_binding SET last_run_id=#{runId},updater='data_sync',update_time=#{updatedAt}
    WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND deleted=FALSE
     /* CHOOSE  */ /* WHEN test=ids != null and !ids.isEmpty() */ AND id IN  /* FOREACH collection=ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
       /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.deleteTaskBindings; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:105; kind=delete
DELETE FROM int_sync_binding WHERE tenant_id=#{tenantId} AND task_id=#{taskId};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.deleteAdapterBindings; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:108; kind=delete
DELETE FROM int_sync_binding WHERE tenant_id=#{tenantId} AND task_id IN
      (SELECT id FROM int_sync_task WHERE tenant_id=#{tenantId} AND adapter=#{adapter});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.deleteObsoleteOrderLines; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:121; kind=delete
DELETE FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId}
      AND object_key='LINE' AND source_object='pm_order_line_from_erp' AND deleted=FALSE
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$.companyCode')) AS BINARY)=CAST('01' AS BINARY)
     /* CHOOSE  */ /* WHEN test=query.bindingIds != null and !query.bindingIds.isEmpty() */ AND id IN  /* FOREACH collection=query.bindingIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.initializeFormalIfMatch; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:14; kind=update
UPDATE proj_project SET
      project_code=#{query.facts.projectCode}, project_name=#{query.facts.projectName},
      company_id=#{query.facts.companyId}, company_code=#{query.facts.companyCode}, company_name=#{query.facts.companyName},
      project_type=#{query.facts.projectType}, project_category=#{query.facts.projectCategory},
      signing_method=#{query.facts.signingMethod}, implementation_mode=#{query.facts.implementationMode},
      customer_project_name=#{query.facts.customerProjectName}, sales_type=#{query.facts.salesType},
      major_project_level=#{query.facts.majorProjectLevel},
      market_name=#{query.facts.marketName}, system_name=#{query.facts.systemName},
      expend_name=#{query.facts.expendName}, industry_name=#{query.facts.industryName},
      not_track_reason=#{query.facts.notTrackReason},
      project_start_time=#{query.facts.projectStartTime}, project_refresh_time=#{query.facts.projectRefreshTime},
      project_close_time=#{query.facts.projectCloseTime}, order_create_time=#{query.facts.orderCreateTime},
      custom_info=#{query.facts.customInfo}, disabled=#{query.facts.disabled},
      effective_from=#{query.facts.effectiveFrom}, effective_to=#{query.facts.effectiveTo},
      creator=#{query.facts.creator}, create_time=#{query.facts.createTime},
      updater=#{query.facts.updater}, update_time=#{query.facts.updateTime},
      lifecycle_status=#{query.facts.lifecycleStatus}, current_stage=#{query.facts.currentStage},
      closed_at=#{query.facts.closedAt}, version=version+1
    WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND version=#{query.expectedVersion}
      AND lifecycle_status=#{query.expectedLifecycleStatus} AND current_stage=#{query.expectedCurrentStage}
      AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMasterMigrationMapper.restoreHistoricalIfMatch; pms-module-project/src/main/resources/mapper/legacydetail/ProjectMasterMigrationMapper.xml:197; kind=update
UPDATE proj_project p SET deleted=b'0',update_time=update_time
    WHERE p.tenant_id=#{query.tenantId} AND p.id=#{query.projectId} AND p.deleted=b'1'
      AND p.version=#{query.expectedVersion} AND p.source_type='MIGRATION'
      AND SHA2(CAST(JSON_OBJECT(
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
      'effective_to',p.`effective_to`) AS CHAR),256)=#{query.expectedFingerprint};

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.correctCustomerIfMatch; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:5; kind=update
UPDATE proj_project SET customer_id=#{query.customerId}, customer_code=#{query.customerCode}, customer_name=#{query.customerName},
          version=version+1, updater=#{query.updater}, update_time=CURRENT_TIMESTAMP(3)
        WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND version=#{query.expectedVersion}
          AND lifecycle_status='ACTIVE' AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.associateCustomerIfMissing; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:11; kind=update
UPDATE proj_project SET customer_id=#{query.customerId}, customer_code=#{query.customerCode}, customer_name=#{query.customerName},
          version=version+1, updater=#{query.updater}, update_time=CURRENT_TIMESTAMP
        WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND version=#{query.expectedVersion}
          AND lifecycle_status='ACTIVE' AND deleted=b'0' AND customer_id IS NULL;

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateEndDateIfMatch; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:21; kind=update
UPDATE proj_project SET project_end_date = #{query.endDate}, version = version + 1,
            updater = #{query.updater}, update_time = CURRENT_TIMESTAMP
        WHERE id = #{query.projectId} AND tenant_id = #{query.tenantId} AND version = #{query.expectedVersion}
          AND lifecycle_status = 'ACTIVE' AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.incrementVersionIfMatch; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:41; kind=update
UPDATE proj_project
        SET version = version + 1
        WHERE id = #{projectId}
          AND version = #{expectedVersion}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateAssignmentStatusIfVersion; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:49; kind=update
UPDATE proj_project
        SET assignment_status = #{query.assignmentStatus}
        WHERE id = #{query.projectId}
          AND version = #{query.version}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateBusinessAttributesIfMatch; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:57; kind=update
UPDATE proj_project
        SET signing_method = #{query.signingMethod},
            project_category = #{query.projectCategory},
            implementation_mode = #{query.implementationMode},
            major_project_level = #{query.majorProjectLevel},
            version = version + 1
        WHERE id = #{query.projectId}
          AND tenant_id = #{query.tenantId}
          AND version = #{query.expectedVersion}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateGovernanceStateIfMatch; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:70; kind=update
UPDATE proj_project
        SET current_stage = #{query.currentStage},
            lifecycle_status = #{query.lifecycleStatus},
            assignment_status = #{query.assignmentStatus},
            version = version + 1,
            updater = #{query.updater},
            update_time = CURRENT_TIMESTAMP
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.projectId}
          AND version = #{query.expectedVersion}
          AND lifecycle_status = #{query.expectedLifecycleStatus}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.advanceStageIfMatch; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:85; kind=update
UPDATE proj_project
        SET current_stage = #{query.targetStage}, version = version + 1,
            updater = #{query.updater}, update_time = CURRENT_TIMESTAMP
        WHERE tenant_id = #{query.tenantId} AND id = #{query.projectId}
          AND version = #{query.expectedVersion} AND current_stage = #{query.expectedCurrentStage}
          AND lifecycle_status = 'ACTIVE' AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.updateManagerMembersIfMatch; pms-module-project/src/main/resources/mapper/projectmanual/ProjectMasterMapper.xml:94; kind=update
UPDATE proj_project
        SET manager_id=#{query.managerId}, manager_name=#{query.managerName},
            manager_employee_no=#{query.managerEmployeeNo}, assignment_status=#{query.assignmentStatus},
            version=version+1, updater=#{query.updater}, update_time=CURRENT_TIMESTAMP
        WHERE id=#{query.projectId} AND tenant_id=#{query.tenantId}
          AND version=#{query.expectedVersion} AND lifecycle_status='ACTIVE' AND deleted=b'0';

-- cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper.updateMigrationParty; pms-module-project/src/main/resources/mapper/projectmanual/ProjectPartyMapper.xml:20; kind=update
UPDATE proj_project_party SET project_id=#{command.row.projectId}, party_role=#{command.row.partyRole}, party_code=#{command.row.partyCode}, party_name=#{command.row.partyName}, contact_name=#{command.row.contactName}, phone=#{command.row.phone}, source_system=#{command.row.sourceSystem}, source_table=#{command.row.sourceTable}, source_record_key=#{command.row.sourceRecordKey}, effective_from=#{command.row.effectiveFrom}, effective_to=#{command.row.effectiveTo}, status=#{command.row.status}, version=#{command.row.version}, creator=#{command.creator}, create_time=#{command.row.createTime}, updater=#{command.updater}, update_time=#{command.row.updateTime}, deleted=#{command.row.deleted}
        WHERE tenant_id=#{command.row.tenantId} AND id=#{command.row.id} AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
