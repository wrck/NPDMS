-- P22 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [pm_project_product_line] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P22.json
SELECT l.*
FROM pm_project_product_line l;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.selectProjectPage; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:4; kind=select
SELECT s.*,o.contract_no,l.unit_code,l.order_qty AS order_quantity
        FROM com_delivery_scope s
        JOIN com_sales_order_line l ON l.id=s.order_line_id AND l.tenant_id=s.tenant_id
        JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id
        WHERE s.tenant_id=#{query.tenantId} AND s.project_id=#{query.projectId} AND s.deleted=0
          AND s.allocation_source='DPPMS' AND s.allocation_confirmed=0
        ORDER BY s.id LIMIT #{query.offset},#{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.countProject; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:13; kind=select
SELECT COUNT(*) FROM com_delivery_scope s
        JOIN com_sales_order_line l ON l.id=s.order_line_id AND l.tenant_id=s.tenant_id
        JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id
        WHERE s.tenant_id=#{query.tenantId} AND s.project_id=#{query.projectId} AND s.deleted=0
          AND s.allocation_source='DPPMS' AND s.allocation_confirmed=0;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.selectAssociatedLines; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:42; kind=select
SELECT DISTINCT l.* FROM com_sales_order_line l
        JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id AND o.deleted=0
        JOIN com_contract c ON c.tenant_id=o.tenant_id AND c.deleted=0 AND c.contract_no=#{query.contractNo}
        JOIN com_project_contract_relation cr ON cr.contract_id=c.id AND cr.tenant_id=c.tenant_id AND cr.deleted=0
        WHERE l.tenant_id=#{query.tenantId} AND l.deleted=0 AND cr.project_id=#{query.projectId}
          AND cr.status='ACTIVE' AND (cr.effective_from IS NULL OR cr.effective_from <= CURRENT_TIMESTAMP)
          AND (cr.effective_to IS NULL OR cr.effective_to > CURRENT_TIMESTAMP)
          AND l.order_no=#{query.orderNo} AND l.line_no=#{query.lineNo} AND l.product_code=#{query.productCode}
          AND CAST(l.order_no AS BINARY)=CAST(#{query.orderNo} AS BINARY)
          AND CAST(l.line_no AS BINARY)=CAST(#{query.lineNo} AS BINARY)
          AND CAST(l.product_code AS BINARY)=CAST(#{query.productCode} AS BINARY)
          AND l.source_system IN ('SAP','D365','SMS') AND l.source_system=o.source_system
          AND l.company_code=o.company_code AND l.order_type=o.order_type
          AND l.status='ENABLED' AND o.status='ENABLED'
          AND (
            (CAST(o.contract_no AS BINARY)=CAST(c.contract_no AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(c.company_code AS BINARY))
            OR EXISTS (SELECT 1 FROM com_order_contract_relation r WHERE r.tenant_id=o.tenant_id AND r.order_id=o.id
              AND r.contract_id=c.id AND r.deleted=0 AND r.effective_from <= CURRENT_TIMESTAMP
              AND (r.effective_to IS NULL OR r.effective_to > CURRENT_TIMESTAMP))
          );

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.lockAssociatedLines; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:45; kind=select
SELECT DISTINCT l.* FROM com_sales_order_line l
        JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id AND o.deleted=0
        JOIN com_contract c ON c.tenant_id=o.tenant_id AND c.deleted=0 AND c.contract_no=#{query.contractNo}
        JOIN com_project_contract_relation cr ON cr.contract_id=c.id AND cr.tenant_id=c.tenant_id AND cr.deleted=0
        WHERE l.tenant_id=#{query.tenantId} AND l.deleted=0 AND cr.project_id=#{query.projectId}
          AND cr.status='ACTIVE' AND (cr.effective_from IS NULL OR cr.effective_from <= CURRENT_TIMESTAMP)
          AND (cr.effective_to IS NULL OR cr.effective_to > CURRENT_TIMESTAMP)
          AND l.order_no=#{query.orderNo} AND l.line_no=#{query.lineNo} AND l.product_code=#{query.productCode}
          AND CAST(l.order_no AS BINARY)=CAST(#{query.orderNo} AS BINARY)
          AND CAST(l.line_no AS BINARY)=CAST(#{query.lineNo} AS BINARY)
          AND CAST(l.product_code AS BINARY)=CAST(#{query.productCode} AS BINARY)
          AND l.source_system IN ('SAP','D365','SMS') AND l.source_system=o.source_system
          AND l.company_code=o.company_code AND l.order_type=o.order_type
          AND l.status='ENABLED' AND o.status='ENABLED'
          AND (
            (CAST(o.contract_no AS BINARY)=CAST(c.contract_no AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(c.company_code AS BINARY))
            OR EXISTS (SELECT 1 FROM com_order_contract_relation r WHERE r.tenant_id=o.tenant_id AND r.order_id=o.id
              AND r.contract_id=c.id AND r.deleted=0 AND r.effective_from <= CURRENT_TIMESTAMP
              AND (r.effective_to IS NULL OR r.effective_to > CURRENT_TIMESTAMP))
          ) FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.selectIdentity; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:51; kind=select
SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId} AND source_evidence=#{query.evidence};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.lockIdentity; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:54; kind=select
SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId} AND source_evidence=#{query.evidence} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.selectProductDetails; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:62; kind=select
SELECT * FROM com_delivery_scope_detail
        WHERE tenant_id=#{query.tenantId} AND delivery_scope_id=#{query.deliveryScopeId}
        ORDER BY detail_sequence,id;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.selectProductDetailsForUpdate; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:65; kind=select
SELECT * FROM com_delivery_scope_detail
        WHERE tenant_id=#{query.tenantId} AND delivery_scope_id=#{query.deliveryScopeId}
        ORDER BY detail_sequence,id FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectCurrentVersionsForAcceptanceLock; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:8; kind=select
SELECT id AS delivery_scope_id,
               allocation_version
        FROM com_delivery_scope
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND allocation_confirmed = b'1' AND effective_to IS NULL
          AND deleted = b'0'
        ORDER BY id ASC
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectActiveByProjectIdForUpdate; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:21; kind=select
SELECT *
        FROM com_delivery_scope
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND scope_status = 'ACTIVE'
          AND allocation_confirmed = b'1' AND effective_to IS NULL
          AND deleted = b'0'
        ORDER BY order_line_id, id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectCurrentByProjectId; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:34; kind=select
SELECT * FROM com_delivery_scope
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND allocation_confirmed = b'1' AND effective_to IS NULL
          AND deleted = b'0'
        ORDER BY order_line_id, id;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectCurrentByProjectIdForUpdate; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:44; kind=select
SELECT * FROM com_delivery_scope
        WHERE tenant_id = #{query.tenantId}
          AND project_id = #{query.projectId}
          AND allocation_confirmed = b'1' AND effective_to IS NULL
          AND deleted = b'0'
        ORDER BY order_line_id, id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectActiveByOrderLineIdsForUpdate; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:55; kind=select
SELECT *
        FROM com_delivery_scope
        WHERE tenant_id = #{query.tenantId}
           /* CHOOSE  */ /* WHEN test=query.orderLineIds != null and !query.orderLineIds.isEmpty() */ AND order_line_id IN
                   /* FOREACH collection=query.orderLineIds; item=orderLineId; open=(; separator=,; close=) */ (#{orderLineId}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
          AND scope_status = 'ACTIVE'
          AND allocation_confirmed = b'1' AND effective_to IS NULL
          AND deleted = b'0'
        ORDER BY order_line_id, id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectCurrentByOrderLineIdsForUpdate; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:74; kind=select
SELECT *
        FROM com_delivery_scope
        WHERE tenant_id = #{query.tenantId}
           /* CHOOSE  */ /* WHEN test=query.orderLineIds != null and !query.orderLineIds.isEmpty() */ AND order_line_id IN
                   /* FOREACH collection=query.orderLineIds; item=orderLineId; open=(; separator=,; close=) */ (#{orderLineId}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
               /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
          AND allocation_confirmed = b'1' AND effective_to IS NULL
          AND deleted = b'0'
        ORDER BY order_line_id, id
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectCurrentByIdForUpdate; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:92; kind=select
SELECT *
        FROM com_delivery_scope
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.deliveryScopeId}
          AND allocation_confirmed = b'1' AND effective_to IS NULL
          AND deleted = b'0'
        FOR UPDATE;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectCountByProjectScope; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:124; kind=select
SELECT COUNT(*)
        FROM com_delivery_scope
        WHERE tenant_id = #{query.tenantId}
         /* CHOOSE  */ /* WHEN test=query.projectIds != null and !query.projectIds.isEmpty() */ AND project_id IN
                 /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        AND deleted = b'0'
         /* IF test=query.projectId != null */ AND project_id = #{query.projectId} /* END IF */ 
         /* IF test=query.orderLineId != null */ AND order_line_id = #{query.orderLineId} /* END IF */ 
         /* IF test=!query.includeHistory */ AND effective_to IS NULL /* END IF */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectPageByProjectScope; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:130; kind=select
SELECT *
        FROM com_delivery_scope
        WHERE tenant_id = #{query.tenantId}
         /* CHOOSE  */ /* WHEN test=query.projectIds != null and !query.projectIds.isEmpty() */ AND project_id IN
                 /* FOREACH collection=query.projectIds; item=projectId; open=(; separator=,; close=) */ (#{projectId}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
        AND deleted = b'0'
         /* IF test=query.projectId != null */ AND project_id = #{query.projectId} /* END IF */ 
         /* IF test=query.orderLineId != null */ AND order_line_id = #{query.orderLineId} /* END IF */ 
         /* IF test=!query.includeHistory */ AND effective_to IS NULL /* END IF */
        ORDER BY project_id, order_line_id, effective_from DESC, allocation_version DESC, id DESC
        LIMIT #{query.offset}, #{query.limit};

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper.selectOrderFields; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeMapper.xml:139; kind=select
SELECT s.id AS scope_id, o.contract_no, l.order_qty AS order_quantity, l.unit_code
        FROM com_delivery_scope s
        LEFT JOIN com_sales_order_line l ON l.id=s.order_line_id AND l.tenant_id=s.tenant_id AND l.deleted=0
        LEFT JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id AND o.deleted=0
        WHERE s.tenant_id=#{query.tenantId} AND s.deleted=0
         /* CHOOSE  */ /* WHEN test=query.scopeIds != null and !query.scopeIds.isEmpty() */ AND s.id IN
                 /* FOREACH collection=query.scopeIds; item=scopeId; open=(; separator=,; close=) */ (#{scopeId}) /* repeat with separator=,; END FOREACH */ /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */;

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeProjectVersionMapper.selectCurrent; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeProjectVersionMapper.xml:13; kind=select
SELECT * FROM com_delivery_scope_project_version
        WHERE tenant_id = #{tenantId} AND project_id = #{projectId} AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeProjectVersionMapper.selectForUpdate; pms-module-commerce/src/main/resources/mapper/scope/DeliveryScopeProjectVersionMapper.xml:19; kind=select
SELECT * FROM com_delivery_scope_project_version
        WHERE tenant_id = #{tenantId} AND project_id = #{projectId} AND deleted = b'0'
        FOR UPDATE;

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

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.insertHistoricalProductDetail; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:68; kind=insert
INSERT INTO com_delivery_scope_detail
          (id,tenant_id,delivery_scope_id,detail_sequence,serial_no,product_code,product_name,
           device_type_code,device_type_name,delivery_batch_no,source_record_key,allocated_qty,detail_status,
           source_snapshot,remark,version,creator,create_time,updater,update_time,deleted)
        VALUES
          (#{row.id},#{row.tenantId},#{row.deliveryScopeId},1,NULL,#{row.productCode},#{row.productName},
           NULL,NULL,NULL,#{row.sourceRecordKey},#{row.allocatedQty},'ACTIVE',NULL,NULL,0,
           #{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},0);

-- cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper.insertHistoricalScope; pms-module-commerce/src/main/resources/mapper/migration/DppmsProductScopeMapper.xml:78; kind=insert
INSERT INTO com_delivery_scope
          (id,tenant_id,project_id,project_code,project_name,project_company_code,project_company_name,
           project_department_code,project_department_name,project_customer_code,project_customer_name,
           project_manager_employee_no,project_manager_name,order_line_id,order_source_system,order_company_code,
           order_company_name,order_type,order_no,line_no,product_code,product_desc,allocated_qty,
           allocation_confirmed,delivered_quantity,open_quantity,scope_status,allocation_version,allocation_source,
           change_reason,department_id,department_code,department_name,department_version,source_evidence,
           effective_from,effective_to,status,version,creator,create_time,updater,update_time,deleted)
        VALUES
          (#{row.id},#{row.tenantId},#{row.projectId},#{row.projectCode},#{row.projectName},#{row.projectCompanyCode},#{row.projectCompanyName},
           #{row.projectDepartmentCode},#{row.projectDepartmentName},#{row.projectCustomerCode},#{row.projectCustomerName},
           #{row.projectManagerEmployeeNo},#{row.projectManagerName},#{row.orderLineId},#{row.orderSourceSystem},#{row.orderCompanyCode},
           #{row.orderCompanyName},#{row.orderType},#{row.orderNo},#{row.lineNo},#{row.productCode},#{row.productDesc},#{row.allocatedQty},
           0,#{row.deliveredQuantity},#{row.openQuantity},'ACTIVE',#{row.allocationVersion},'DPPMS',
           NULL,#{row.departmentId},#{row.departmentCode},#{row.departmentName},#{row.departmentVersion},#{row.sourceEvidence},
           #{row.effectiveFrom},NULL,'ENABLED',0,#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},0);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
