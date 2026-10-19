-- P16 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [pm_project_header] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
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

-- [pm_project_identity] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
-- queryRef=pm_project_header; 复用该来源结果/分组，不重复执行一份SELECT。;

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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper.selectIdentity; pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:4; kind=select
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper.selectIdentityForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:7; kind=select
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND deleted=FALSE FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper.selectExactCodeIds; pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:10; kind=select
SELECT id FROM proj_project WHERE tenant_id=#{query.tenantId} AND project_code=#{query.projectCode}
    ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper.selectRestorationIdentityForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:147; kind=select
SELECT * FROM proj_project WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper.selectRestorationFingerprint; pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:150; kind=select
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
      AND p.deleted=TRUE AND p.source_type='MIGRATION' AND p.parent_id IS NULL
      AND p.root_id=p.id AND p.code_root_id=p.id AND p.assignment_status='UNASSIGNED'
      AND p.active_plan_version_id IS NULL AND p.lifecycle_template_id IS NULL AND p.manager_id IS NULL;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper.selectRestorationReferenceCount; pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:157; kind=select
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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectPartyMapper.selectMigrationSourceForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProjectPartyMapper.xml:4; kind=select
SELECT * FROM proj_project_party WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS'
          AND source_table=#{query.sourceTable} AND source_record_key=#{query.sourceKey} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRootMapper.lockRoot; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRootMapper.xml:4; kind=select
SELECT id,tenant_id,parent_id,root_id FROM proj_project
    WHERE tenant_id=#{query.tenantId} AND id=#{query.projectId} AND deleted=FALSE FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRootMapper.selectActiveVersion; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRootMapper.xml:8; kind=select
SELECT tree_version FROM proj_project_tree_version
    WHERE tenant_id=#{query.tenantId} AND root_project_id=#{query.projectId} AND status='ACTIVE' AND deleted=FALSE
    ORDER BY tree_version DESC,id DESC LIMIT 1 FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRootMapper.selectRootNodes; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRootMapper.xml:13; kind=select
SELECT id,tenant_id,parent_id,root_id FROM proj_project
    WHERE tenant_id=#{query.tenantId} AND root_id=#{query.projectId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectScopeLookupMapper.selectById; pms-module-integration/src/main/resources/mapper/sync/DirectScopeLookupMapper.xml:4; kind=select
SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId} AND id=#{query.id} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectScopeLookupMapper.current; pms-module-integration/src/main/resources/mapper/sync/DirectScopeLookupMapper.xml:8; kind=select
SELECT * FROM com_delivery_scope_project_version WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectScopeLookupMapper.lockExisting; pms-module-integration/src/main/resources/mapper/sync/DirectScopeLookupMapper.xml:9; kind=select
SELECT * FROM com_delivery_scope_project_version WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=FALSE FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper.insertHistoricalRoot; pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:37; kind=insert
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
      #{query.facts.closedAt},'UNASSIGNED',#{query.facts.version},FALSE
    );

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectPartyMapper.insertMigrationParty; pms-module-integration/src/main/resources/mapper/sync/DirectProjectPartyMapper.xml:8; kind=insert
INSERT INTO proj_project_party (id, tenant_id, project_id, party_role, party_code, party_name, contact_name, phone, source_system, source_table, source_record_key, effective_from, effective_to, status, version, creator, create_time, updater, update_time, deleted)
        VALUES (#{command.row.id}, #{command.row.tenantId}, #{command.row.projectId}, #{command.row.partyRole}, #{command.row.partyCode}, #{command.row.partyName}, #{command.row.contactName}, #{command.row.phone}, #{command.row.sourceSystem}, #{command.row.sourceTable}, #{command.row.sourceRecordKey}, #{command.row.effectiveFrom}, #{command.row.effectiveTo}, #{command.row.status}, #{command.row.version}, #{command.creator}, #{command.row.createTime}, #{command.updater}, #{command.row.updateTime}, #{command.row.deleted});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRootMapper.insertBuilding; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRootMapper.xml:17; kind=insert
INSERT INTO proj_project_tree_version(root_project_id,tree_version,status,change_batch_id,node_count,path_count,version,
      creator,updater,create_time,update_time,deleted,tenant_id)
    VALUES(#{row.rootProjectId},1,'BUILDING',#{row.changeBatchId},0,0,0,
      #{row.actor},#{row.actor},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE,#{row.tenantId});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRootMapper.insertSelfPath; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRootMapper.xml:23; kind=insert
INSERT INTO proj_project_tree_path(id,tenant_id,tree_version,root_project_id,ancestor_project_id,descendant_project_id,
      distance,version,creator,updater,create_time,update_time,deleted)
    VALUES(#{row.id},#{row.tenantId},1,#{row.projectId},#{row.projectId},#{row.projectId},0,0,
      #{row.actor},#{row.actor},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,FALSE);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper.initializeFormalIfMatch; pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:14; kind=update
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
      AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectMapper.restoreHistoricalIfMatch; pms-module-integration/src/main/resources/mapper/sync/DirectProjectMapper.xml:197; kind=update
UPDATE proj_project p SET deleted=FALSE,update_time=update_time
    WHERE p.tenant_id=#{query.tenantId} AND p.id=#{query.projectId} AND p.deleted=TRUE
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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectPartyMapper.updateMigrationParty; pms-module-integration/src/main/resources/mapper/sync/DirectProjectPartyMapper.xml:12; kind=update
UPDATE proj_project_party SET project_id=#{command.row.projectId}, party_role=#{command.row.partyRole}, party_code=#{command.row.partyCode}, party_name=#{command.row.partyName}, contact_name=#{command.row.contactName}, phone=#{command.row.phone}, source_system=#{command.row.sourceSystem}, source_table=#{command.row.sourceTable}, source_record_key=#{command.row.sourceRecordKey}, effective_from=#{command.row.effectiveFrom}, effective_to=#{command.row.effectiveTo}, status=#{command.row.status}, version=#{command.row.version}, creator=#{command.creator}, create_time=#{command.row.createTime}, updater=#{command.updater}, update_time=#{command.row.updateTime}, deleted=#{command.row.deleted}
        WHERE tenant_id=#{command.row.tenantId} AND id=#{command.row.id} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRootMapper.activate; pms-module-integration/src/main/resources/mapper/sync/DirectProjectRootMapper.xml:29; kind=update
UPDATE proj_project_tree_version SET status='ACTIVE',node_count=1,path_count=1,activated_at=CURRENT_TIMESTAMP,
      failed_reason=NULL,updater=#{row.actor},update_time=CURRENT_TIMESTAMP
    WHERE id=#{row.id} AND tenant_id=#{row.tenantId} AND root_project_id=#{row.rootProjectId}
      AND tree_version=1 AND status='BUILDING' AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
