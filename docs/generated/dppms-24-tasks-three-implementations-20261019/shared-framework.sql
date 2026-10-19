-- 三种实现共用/各自技术元数据与目录查询；报告摘录，不执行。
-- int_sync_* 与 int_base_sync_* 是两套技术运行身份，不是正式业务数据的替代表。
-- MyBatis分支/集合标签保留为注释，完整节点另见shared-mapper-nodes.json；禁止整体复制执行。

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.createTask; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:4
INSERT INTO int_base_sync_task(id,tenant_id,name,connection_id,task_code,source_system,batch_size,max_rows,creator,updater)
    VALUES(#{row.id},#{row.tenantId},#{row.name},#{row.config.connectionId},#{row.config.taskCode},#{row.sourceSystem},
           #{row.config.batchSize},#{row.config.maxRows},#{row.actor},#{row.actor});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.selectTask; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:9
SELECT id,connection_id,task_code,batch_size,max_rows,active_run_id FROM int_base_sync_task
    WHERE tenant_id=#{query.tenantId} AND id=#{query.taskId} AND deleted=FALSE
     /* IF test=query.lock */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.createRun; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:14
INSERT INTO int_base_sync_run(id,tenant_id,task_id,request_key,status,preview,task_code,connection_id,batch_size,max_rows,started_at,creator,updater)
    VALUES(#{row.id},#{row.tenantId},#{row.taskId},#{row.requestKey},'READING',#{row.preview},#{row.config.taskCode},
           #{row.config.connectionId},#{row.config.batchSize},#{row.config.maxRows},CURRENT_TIMESTAMP(3),#{row.actor},#{row.actor});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.selectRun; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:19
SELECT id,preview,status,started_at FROM int_base_sync_run
    WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId} AND id=#{query.runId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.claimTask; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:23
UPDATE int_base_sync_task SET active_run_id=#{row.id}
    WHERE tenant_id=#{row.tenantId} AND id=#{row.taskId} AND active_run_id IS NULL AND deleted=FALSE
      AND task_code=#{row.config.taskCode} AND connection_id=#{row.config.connectionId}
      AND batch_size=#{row.config.batchSize} AND max_rows=#{row.config.maxRows};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.finishRun; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:29
UPDATE int_base_sync_run SET status=#{command.status},read_count=#{command.counts.read},
      inserted_count=#{command.counts.inserted},updated_count=#{command.counts.updated},
      unchanged_count=#{command.counts.unchanged},deleted_count=#{command.counts.deleted},
      error_message=#{command.error},finished_at=CURRENT_TIMESTAMP(3)
    WHERE tenant_id=#{command.tenantId} AND task_id=#{command.taskId} AND id=#{command.runId} AND status='READING' AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.releaseTask; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:36
UPDATE int_base_sync_task SET active_run_id=NULL WHERE tenant_id=#{command.tenantId} AND id=#{command.taskId}
      AND active_run_id=#{command.runId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.selectBindings; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:40
SELECT source_key,target_id,fingerprint FROM int_base_sync_binding
    WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId} AND object_key=#{query.object} AND deleted=FALSE
     /* CHOOSE  */ /* WHEN test=query.sourceKeys != null and !query.sourceKeys.isEmpty() */ AND source_key IN
       /* FOREACH collection=query.sourceKeys; item=key; open=(; close=); separator=, */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
     /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.insertBinding; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:48
INSERT INTO int_base_sync_binding(id,tenant_id,task_id,object_key,source_key,target_id,fingerprint,last_run_id,creator,updater)
    VALUES(#{row.id},#{row.tenantId},#{row.taskId},#{row.object},#{row.sourceKey},#{row.targetId},
           #{row.fingerprint},#{row.runId},#{row.actor},#{row.actor});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.updateBinding; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncMetadataMapper.xml:53
UPDATE int_base_sync_binding SET fingerprint=#{row.fingerprint},last_run_id=#{row.runId},updater=#{row.actor}
    WHERE tenant_id=#{row.tenantId} AND task_id=#{row.taskId} AND object_key=#{row.object} AND source_key=#{row.sourceKey}
      AND target_id=#{row.targetId} AND fingerprint=#{row.previousFingerprint} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncReferences$ReferenceMapper.selectAuditUserCatalogue; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncReferencesMapper.xml:4
SELECT id,username FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncReferences$ReferenceMapper.selectAuditUsers; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncReferencesMapper.xml:7
SELECT id,username FROM system_users WHERE tenant_id=#{query.tenantId} AND deleted=FALSE
     /* CHOOSE  */ /* WHEN test=query.usernames != null and !query.usernames.isEmpty() */ AND username IN  /* FOREACH collection=query.usernames; item=name; open=(; separator=,; close=) */ (#{name}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY id;

-- cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences$ReferenceMapper.selectFormal; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncProjectReferencesMapper.xml:4
SELECT s.source_project_key AS source_key,p.id,p.tenant_id,p.version,p.project_code,p.project_type,p.company_id,p.company_code,p.company_name,p.lifecycle_status,p.deleted FROM proj_legacy_detail_source s  JOIN proj_project p ON p.tenant_id=s.tenant_id AND p.id=s.project_id
WHERE s.tenant_id=#{query.tenantId} AND s.source_system='DPPMS'
 /* CHOOSE  */ /* WHEN test=query.canonicalKeys != null and !query.canonicalKeys.isEmpty() */ AND s.source_project_key IN  /* FOREACH collection=query.canonicalKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences$ReferenceMapper.selectIndependent; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncProjectReferencesMapper.xml:7
SELECT s.source_key AS source_key,p.id,p.tenant_id,p.version,p.project_code,p.project_type,p.company_id,p.company_code,p.company_name,p.lifecycle_status,p.deleted FROM int_base_sync_binding s JOIN int_base_sync_task t ON t.tenant_id=s.tenant_id AND t.id=s.task_id JOIN proj_project p ON p.tenant_id=s.tenant_id AND p.id=s.target_id
WHERE s.tenant_id=#{query.tenantId} AND s.object_key='PROJECT_MASTER' AND s.deleted=FALSE AND t.deleted=FALSE AND t.task_code='BASE_P16' AND t.connection_id=#{query.connectionId}
 /* CHOOSE  */ /* WHEN test=query.canonicalKeys != null and !query.canonicalKeys.isEmpty() */ AND s.source_key IN  /* FOREACH collection=query.canonicalKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences$ReferenceMapper.selectCurrentProjects; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncProjectReferencesMapper.xml:10
SELECT CAST(p.id AS CHAR) AS source_key,p.id,p.tenant_id,p.version,p.project_code,p.project_type,p.company_id,p.company_code,p.company_name,p.lifecycle_status,p.deleted FROM proj_project p WHERE p.tenant_id=#{query.tenantId}  /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND p.id IN  /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  ORDER BY p.id  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncPublishedProjects$ReferenceMapper.selectCompleted; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncPublishedProjectsMapper.xml:4
SELECT r.id AS run_id,r.read_count FROM int_base_sync_run r JOIN int_base_sync_task t ON t.tenant_id=r.tenant_id AND t.id=r.task_id
WHERE r.tenant_id=#{query.tenantId} AND r.connection_id=#{query.connectionId} AND t.connection_id=#{query.connectionId}
AND t.task_code='BASE_P16' AND r.task_code='BASE_P16' AND t.deleted=FALSE AND r.deleted=FALSE AND r.preview=FALSE AND r.status='COMPLETED'
ORDER BY r.id DESC LIMIT 1;

-- cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncPublishedProjects$ReferenceMapper.countMasters; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncPublishedProjectsMapper.xml:9
SELECT COUNT(*) FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id
WHERE b.tenant_id=#{query.tenantId} AND t.connection_id=#{query.connectionId} AND t.task_code='BASE_P16' AND t.deleted=FALSE
AND b.deleted=FALSE AND b.last_run_id=#{query.publicationId} AND b.object_key='PROJECT_MASTER';

-- cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncPublishedProjects$ReferenceMapper.selectPage; pms-module-integration/src/main/resources/mapper/basesync/BaseSyncPublishedProjectsMapper.xml:13
SELECT b.source_key,p.id AS project_id,p.project_type,p.project_code AS original_project_code,p.project_code,b.fingerprint
FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id
JOIN proj_project p ON p.tenant_id=b.tenant_id AND p.id=b.target_id
WHERE b.tenant_id=#{query.tenantId} AND t.connection_id=#{query.connectionId} AND t.task_code='BASE_P16' AND t.deleted=FALSE
AND b.deleted=FALSE AND p.deleted=FALSE AND b.last_run_id=#{query.publicationId} AND b.object_key='PROJECT_ALIAS'
AND b.source_key > #{query.afterKey} ORDER BY b.source_key LIMIT 500;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncDisplayCacheMapper.clearCurrent; pms-module-integration/src/main/resources/mapper/sync/SyncDisplayCacheMapper.xml:4
DELETE FROM
     /* CHOOSE  */ /* WHEN test=query.cache.name() == 'PROJECT_LEASE' */ proj_project_lease_product /* END WHEN */ 
       /* WHEN test=query.cache.name() == 'PROJECT_CONFIGURATION' */ proj_project_product_configuration /* END WHEN */ 
       /* OTHERWISE  */ com_contract_collection_plan /* END OTHERWISE */ /* END CHOOSE */ 
    WHERE tenant_id=#{query.tenantId};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncDisplayCacheMapper.retireBindings; pms-module-integration/src/main/resources/mapper/sync/SyncDisplayCacheMapper.xml:13
UPDATE int_sync_binding SET deleted=1 WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId} AND deleted=0;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectInstallationSelectionPage; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:4
SELECT * FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId} AND deleted=FALSE
      AND object_key='INSTALLATION_HISTORY' AND source_object='pm_project_shipment'
     /* CHOOSE  */ /* WHEN test=query.deviceSns != null and !query.deviceSns.isEmpty() */ AND JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$.originalFields.barcode')) IN
       /* FOREACH collection=query.deviceSns; item=sn; open=(; separator=,; close=) */ (#{sn}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
     /* IF test=query.afterId != null */ AND id > #{query.afterId} /* END IF */ 
    ORDER BY id LIMIT 500;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.restoreVerifiedBinding; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:14
UPDATE int_sync_binding SET deleted=FALSE
    WHERE id=#{original.id} AND tenant_id=#{original.tenantId} AND task_id=#{original.taskId}
      AND object_key=#{original.objectKey} AND source_object=#{original.sourceObject}
      AND source_key=#{original.sourceKey} AND target_id=#{original.targetId} AND deleted=TRUE
      AND last_run_id <=> #{original.lastRunId}
      AND target_shared <=> #{original.targetShared}
      AND fields_json=CAST(#{original.fieldsJson} AS JSON);

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectRetiredSourceKeysForUpdate; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:23
SELECT * FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId}
      AND object_key=#{query.object} AND source_object=#{query.sourceObject} AND deleted=TRUE
     /* CHOOSE  */ /* WHEN test=query.sourceKeys != null and !query.sourceKeys.isEmpty() */ AND source_key IN  /* FOREACH collection=query.sourceKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectIdentitySources; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:31
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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectEhrEmployeeUsers; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:76
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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectFirstTaskBindingId; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:90
SELECT id FROM int_sync_binding
    WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND deleted=FALSE
    LIMIT 1;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.updateLastRun; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:95
UPDATE int_sync_binding SET last_run_id=#{runId},updater='data_sync',update_time=#{updatedAt}
    WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND deleted=FALSE
     /* CHOOSE  */ /* WHEN test=ids != null and !ids.isEmpty() */ AND id IN  /* FOREACH collection=ids; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
       /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.deleteTaskBindings; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:105
DELETE FROM int_sync_binding WHERE tenant_id=#{tenantId} AND task_id=#{taskId};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.deleteAdapterBindings; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:108
DELETE FROM int_sync_binding WHERE tenant_id=#{tenantId} AND task_id IN
      (SELECT id FROM int_sync_task WHERE tenant_id=#{tenantId} AND adapter=#{adapter});

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.selectObsoleteOrderLinesForUpdate; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:112
SELECT * FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId}
      AND object_key='LINE' AND source_object='pm_order_line_from_erp' AND deleted=FALSE
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$.companyCode')) AS BINARY)=CAST('01' AS BINARY)
     /* CHOOSE  */ /* WHEN test=query.sourceKeys != null and !query.sourceKeys.isEmpty() */ AND source_key IN  /* FOREACH collection=query.sourceKeys; item=key; open=(; separator=,; close=) */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
    ORDER BY id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper.deleteObsoleteOrderLines; pms-module-integration/src/main/resources/mapper/sync/SyncBindingMapper.xml:121
DELETE FROM int_sync_binding WHERE tenant_id=#{query.tenantId} AND task_id=#{query.taskId}
      AND object_key='LINE' AND source_object='pm_order_line_from_erp' AND deleted=FALSE
      AND CAST(JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$.companyCode')) AS BINARY)=CAST('01' AS BINARY)
     /* CHOOSE  */ /* WHEN test=query.bindingIds != null and !query.bindingIds.isEmpty() */ AND id IN  /* FOREACH collection=query.bindingIds; item=id; open=(; separator=,; close=) */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper.markCacheRefreshed; pms-module-integration/src/main/resources/mapper/sync/SyncRunMapper.xml:4
UPDATE int_sync_run SET cache_pending=FALSE,update_time=CURRENT_TIMESTAMP(3)
    WHERE tenant_id=#{tenantId} AND id=#{id} AND status='SUCCESS' AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper.selectMaintenance; pms-module-integration/src/main/resources/mapper/sync/SyncRunMapper.xml:8
SELECT id, status, config_snapshot FROM int_sync_run WHERE tenant_id=#{tenantId} AND deleted=FALSE
      AND (cache_pending=TRUE OR (status IN ('QUEUED','READING','VALIDATING','APPLYING') AND started_at < #{now}))
      ORDER BY id LIMIT 100;
