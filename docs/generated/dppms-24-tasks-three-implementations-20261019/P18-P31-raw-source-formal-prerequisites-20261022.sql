-- 只读来源/正式定义核查，不执行DDL、迁移或真实同步。

-- 来源dppms：P18.selectContracts
SELECT id AS contractSourceId,contractNo,projectGroupCode,createTime AS contractCreateTime,NULLIF(createBy,CAST('' AS BINARY)) AS contractCreateBy,updateTime AS contractUpdateTime,NULLIF(updateBy,CAST('' AS BINARY)) AS contractUpdateBy FROM pm_project_contract ORDER BY id LIMIT 1000001;

-- 来源dppms：P18.selectGroups
SELECT id AS groupId,projectGroupCode AS groupProjectGroupCode,projectGroupName AS groupProjectGroupName,projectType,createTime AS groupCreateTime,createBy AS groupCreateBy,updateTime AS groupUpdateTime,updateBy AS groupUpdateBy FROM pm_project_group WHERE projectType='10' ORDER BY id LIMIT 1000001;

-- 来源dppms：P18.selectMembers
SELECT id AS memberId,projectGroupCode AS memberProjectGroupCode,projectCode AS memberProjectCode,mergeBranchMark,smsProjectCode,createTime AS memberCreateTime,COALESCE(createBy,'') AS memberCreateBy,updateTime AS memberUpdateTime,COALESCE(updateBy,'') AS memberUpdateBy FROM pm_project_group_relationship ORDER BY id LIMIT 1000001;

-- 来源dppms：P18.selectProjects
SELECT p.projectId AS originalProjectId,p.projectCode AS originalProjectCode,company.compCode AS originalCompanyCode FROM pm_project p JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) chosen ON chosen.projectId=p.projectId LEFT JOIN t_company company ON company.id=p.compId WHERE p.disabled=0 AND p.effectiveTo IS NULL AND p.projectType='10' ORDER BY p.projectId LIMIT 1000001;

-- REVIEW-ONLY SELECT collection; not executed by this preparation script.

-- p31.activeRuns; parameter values: []
SELECT t.id AS task_id,t.task_code,t.active_run_id,r.status,r.started_at FROM int_base_sync_task t LEFT JOIN int_base_sync_run r ON r.id=t.active_run_id AND r.tenant_id=t.tenant_id WHERE t.tenant_id=1 AND t.deleted=0 AND t.active_run_id IS NOT NULL ORDER BY t.id;

-- p31.independentTasks; parameter values: []
SELECT id,task_code,connection_id FROM int_base_sync_task WHERE tenant_id=1 AND deleted=0 AND task_code IN ('BASE_P16','BASE_P31') ORDER BY id;

-- p31.receipts; parameter values: [2112881208178343938, 2112881209570852866]
SELECT id,task_id,task_code,CAST(preview AS UNSIGNED) preview,status,read_count,inserted_count,updated_count,unchanged_count,deleted_count,error_message,started_at,finished_at FROM int_base_sync_run WHERE tenant_id=1 AND task_id IN (%s,%s) ORDER BY id;

-- p31.bindings; parameter values: [2112881208178343938, 2112881209570852866]
SELECT task_id,object_key,COUNT(*) n,COUNT(DISTINCT source_key) sourceKeys,COUNT(DISTINCT target_id) targetIds FROM int_base_sync_binding WHERE tenant_id=1 AND deleted=0 AND task_id IN (%s,%s) GROUP BY task_id,object_key;

-- p31.p16SourceBindings; parameter values: [2112881208178343938]
SELECT object_key,source_key,target_id FROM int_base_sync_binding WHERE tenant_id=1 AND deleted=0 AND task_id=%s;

-- p31.targetMemberCount; parameter values: []
SELECT COUNT(*) total,SUM(tenant_id=1 AND deleted=0) tenant1Undeleted FROM proj_project_member_assignment;

-- p31.columns; parameter values: []
SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,DATETIME_PRECISION,COLUMN_DEFAULT,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='npdms' AND TABLE_NAME='proj_project_member_assignment' ORDER BY ORDINAL_POSITION;

-- p31.sourceIdentity; parameter values: []
SELECT DATABASE() schemaName,@@port port;

-- p31.sourceScope; parameter values: []

    SELECT CAST(p.projectId AS CHAR) AS source_key,chosen.projectId AS project_id,p.projectType AS project_type,
           p.projectCode AS original_project_code,current_project.projectCode AS project_code,NULL AS fingerprint
    FROM pm_project p
    JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId
          FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) chosen
      ON chosen.typeKey=CAST(p.projectType AS BINARY) AND chosen.codeKey=CAST(p.projectCode AS BINARY)
    JOIN pm_project current_project ON current_project.projectId=chosen.projectId
    WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0
      AND current_project.effectiveTo IS NULL AND current_project.projectType='10'
    ORDER BY p.projectId LIMIT 1000001
  ;

-- p31.sourceMemberRows; parameter values: []
SELECT id,projectId,projectType,memberRole,effectiveFrom,effectiveTo,createTime,updateTime FROM pm_project_member WHERE projectType='10' AND memberRole IN ('10','20','30','60') ORDER BY id;

-- p31.sourceColumns; parameter values: []
SELECT COLUMN_NAME,COLUMN_TYPE,DATETIME_PRECISION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dppms' AND TABLE_NAME='pm_project_member' AND COLUMN_NAME IN ('effectiveFrom','effectiveTo','createTime','updateTime') ORDER BY ORDINAL_POSITION;

-- protection.activeRuns; parameter values: []
SELECT t.id AS task_id,t.task_code,t.active_run_id,r.status,r.started_at FROM int_base_sync_task t LEFT JOIN int_base_sync_run r ON r.id=t.active_run_id AND r.tenant_id=t.tenant_id WHERE t.tenant_id=1 AND t.deleted=0 AND t.active_run_id IS NOT NULL ORDER BY t.id;

-- protection.int_sync_task; parameter values: []
SELECT COUNT(*) n FROM int_sync_task;

-- protection.int_sync_run; parameter values: []
SELECT COUNT(*) n FROM int_sync_run;

-- protection.int_sync_binding; parameter values: []
SELECT COUNT(*) n FROM int_sync_binding;

-- protection.migrationHistory; parameter values: []
SELECT installed_rank,version,description,type,script,checksum,installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank;

-- protection.newTasks; parameter values: []
SELECT id,tenant_id,name,task_code,source_system,connection_id,batch_size,max_rows,active_run_id,creator,create_time,updater,update_time FROM int_base_sync_task WHERE tenant_id=1 AND deleted=0 ORDER BY task_code;

-- protection.P22P33; parameter values: []
SELECT COUNT(*) n FROM int_base_sync_run WHERE tenant_id=1 AND task_code IN ('BASE_P22','BASE_P33') AND preview=0;

-- protection.roles; parameter values: []
SELECT r.id,r.code,r.name,r.status FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id WHERE ur.user_id=1 AND ur.tenant_id=1 AND ur.deleted=0 AND r.deleted=0 AND r.status=0 ORDER BY r.id;

-- protection.permissions; parameter values: []
SELECT DISTINCT m.permission,m.status FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id JOIN system_menu m ON m.id=rm.menu_id WHERE ur.user_id=1 AND ur.tenant_id=1 AND ur.deleted=0 AND r.deleted=0 AND r.status=0 AND rm.deleted=0 AND m.deleted=0 AND m.permission IN ('pms:integration:execute','pms:commerce:contract:relate');

-- protection.projects; parameter values: []
SELECT p.id,p.root_id FROM proj_project p JOIN int_base_sync_binding b ON b.tenant_id=p.tenant_id AND b.target_id=p.id AND b.object_key='PROJECT_MASTER' AND b.deleted=0 JOIN int_base_sync_task t ON t.id=b.task_id AND t.tenant_id=b.tenant_id AND t.task_code='BASE_P16' AND t.deleted=0 WHERE p.tenant_id=1 AND p.deleted=0;

-- protection.treeVersions; parameter values: []
SELECT id,root_project_id,tree_version FROM proj_project_tree_version WHERE tenant_id=1 AND status='ACTIVE' AND deleted=0;

-- protection.selfPaths; parameter values: []
SELECT root_project_id,tree_version,ancestor_project_id,descendant_project_id,distance FROM proj_project_tree_path WHERE tenant_id=1 AND deleted=0 AND ancestor_project_id=descendant_project_id AND distance=0;

-- authority.activeScopes; parameter values: ["2026-10-22 10:57:11.858964", "2026-10-22 10:57:11.858964"]
SELECT s.user_id,s.company_code,s.scope_role,u.status user_status FROM system_user_company_department_scope s LEFT JOIN system_users u ON u.id=s.user_id AND u.tenant_id=s.tenant_id AND u.deleted=0 WHERE s.tenant_id=1 AND s.deleted=0 AND s.status=0 AND s.effective_from<=%s AND (s.effective_to IS NULL OR s.effective_to>%s) AND s.id IS NOT NULL AND s.version IS NOT NULL AND s.company_code IN ('001','002','003');

-- authority.activeRoles; parameter values: []
SELECT ur.user_id,r.code FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.status=0 AND r.deleted=0 WHERE ur.tenant_id=1 AND ur.deleted=0;

-- authority.explicitPermissions; parameter values: []
SELECT DISTINCT ur.user_id,m.permission FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.status=0 AND r.deleted=0 JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=0 JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 AND m.status=0 WHERE ur.tenant_id=1 AND ur.deleted=0 AND m.permission IN ('pms:integration:execute','pms:commerce:contract:relate');

-- 正式范围只读查询：query.taskId沿用同一selectCompleted返回的task_id；其他上下文参数保持。

-- 正式目标npdms：BaseSyncPublishedProjects.selectCompleted
SELECT r.id AS run_id,r.read_count,r.task_id FROM int_base_sync_run r JOIN int_base_sync_task t ON t.tenant_id=r.tenant_id AND t.id=r.task_id
WHERE r.tenant_id=#{query.tenantId} AND r.connection_id=#{query.connectionId} AND t.connection_id=#{query.connectionId}
AND t.task_code='BASE_P16' AND r.task_code='BASE_P16' AND t.deleted=FALSE AND r.deleted=FALSE AND r.preview=FALSE AND r.status='COMPLETED'
ORDER BY r.id DESC LIMIT 1;

-- 正式目标npdms：BaseSyncPublishedProjects.countMasters
SELECT COUNT(*) FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id
WHERE b.tenant_id=#{query.tenantId} AND t.connection_id=#{query.connectionId} AND t.task_code='BASE_P16' AND t.deleted=FALSE
AND b.task_id=#{query.taskId} AND b.deleted=FALSE AND b.last_run_id=#{query.publicationId} AND b.object_key='PROJECT_MASTER';

-- 正式目标npdms：BaseSyncPublishedProjects.selectPage
SELECT b.source_key,p.id AS project_id,p.project_type,p.project_code AS original_project_code,p.project_code,b.fingerprint
FROM int_base_sync_task t STRAIGHT_JOIN int_base_sync_binding b FORCE INDEX (uk_int_base_sync_binding_source) ON t.tenant_id=b.tenant_id AND t.id=b.task_id
STRAIGHT_JOIN proj_project p ON p.tenant_id=b.tenant_id AND p.id=b.target_id
WHERE b.tenant_id=#{query.tenantId} AND t.connection_id=#{query.connectionId} AND t.task_code='BASE_P16' AND t.deleted=FALSE
AND b.task_id=#{query.taskId} AND b.deleted=FALSE AND p.deleted=FALSE AND b.last_run_id=#{query.publicationId} AND b.object_key='PROJECT_ALIAS'
AND b.source_key > #{query.afterKey} ORDER BY b.source_key LIMIT 500;
