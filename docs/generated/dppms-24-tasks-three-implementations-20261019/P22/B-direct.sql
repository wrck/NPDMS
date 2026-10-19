-- P22 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [pm_project_product_line] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT l.*,p.projectCode AS originalProjectCode,p.projectType
FROM pm_project_product_line l
JOIN (SELECT original.*,canonical.projectId AS canonicalProjectId
FROM pm_project original
JOIN (
  SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,
    MAX(projectId) AS projectId
  FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)
) canonical ON canonical.typeKey <=> CAST(original.projectType AS BINARY)
  AND canonical.codeKey <=> CAST(original.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE current_project.disabled=0 AND current_project.effectiveTo IS NULL
  AND original.disabled=0 AND original.effectiveTo IS NULL
  AND FIND_IN_SET(current_project.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) p ON p.projectId=l.projectId
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes));

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductScopeMapper.countProject; pms-module-integration/src/main/resources/mapper/sync/DirectProductScopeMapper.xml:4; kind=select
SELECT COUNT(*) FROM com_delivery_scope s
        JOIN com_sales_order_line l ON l.id=s.order_line_id AND l.tenant_id=s.tenant_id
        JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id
        WHERE s.tenant_id=#{query.tenantId} AND s.project_id=#{query.projectId} AND s.deleted=0
          AND s.allocation_source='DPPMS' AND s.allocation_confirmed=0;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductScopeMapper.selectAssociatedLines; pms-module-integration/src/main/resources/mapper/sync/DirectProductScopeMapper.xml:33; kind=select
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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductScopeMapper.lockAssociatedLines; pms-module-integration/src/main/resources/mapper/sync/DirectProductScopeMapper.xml:36; kind=select
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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductScopeMapper.selectIdentity; pms-module-integration/src/main/resources/mapper/sync/DirectProductScopeMapper.xml:42; kind=select
SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId} AND source_evidence=#{query.evidence};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductScopeMapper.lockIdentity; pms-module-integration/src/main/resources/mapper/sync/DirectProductScopeMapper.xml:45; kind=select
SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId} AND source_evidence=#{query.evidence} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductScopeMapper.selectProductDetails; pms-module-integration/src/main/resources/mapper/sync/DirectProductScopeMapper.xml:53; kind=select
SELECT * FROM com_delivery_scope_detail
        WHERE tenant_id=#{query.tenantId} AND delivery_scope_id=#{query.deliveryScopeId}
        ORDER BY detail_sequence,id;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductScopeMapper.selectProductDetailsForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectProductScopeMapper.xml:56; kind=select
SELECT * FROM com_delivery_scope_detail
        WHERE tenant_id=#{query.tenantId} AND delivery_scope_id=#{query.deliveryScopeId}
        ORDER BY detail_sequence,id FOR UPDATE;

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

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectScopeLookupMapper.selectById; pms-module-integration/src/main/resources/mapper/sync/DirectScopeLookupMapper.xml:4; kind=select
SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId} AND id=#{query.id} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectScopeLookupMapper.current; pms-module-integration/src/main/resources/mapper/sync/DirectScopeLookupMapper.xml:8; kind=select
SELECT * FROM com_delivery_scope_project_version WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=FALSE;

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectScopeLookupMapper.lockExisting; pms-module-integration/src/main/resources/mapper/sync/DirectScopeLookupMapper.xml:9; kind=select
SELECT * FROM com_delivery_scope_project_version WHERE tenant_id=#{query.tenantId} AND project_id=#{query.projectId} AND deleted=FALSE FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductScopeMapper.insertHistoricalProductDetail; pms-module-integration/src/main/resources/mapper/sync/DirectProductScopeMapper.xml:59; kind=insert
INSERT INTO com_delivery_scope_detail
          (id,tenant_id,delivery_scope_id,detail_sequence,serial_no,product_code,product_name,
           device_type_code,device_type_name,delivery_batch_no,source_record_key,allocated_qty,detail_status,
           source_snapshot,remark,version,creator,create_time,updater,update_time,deleted)
        VALUES
          (#{row.id},#{row.tenantId},#{row.deliveryScopeId},1,NULL,#{row.productCode},#{row.productName},
           NULL,NULL,NULL,#{row.sourceRecordKey},#{row.allocatedQty},'ACTIVE',NULL,NULL,0,
           #{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},0);

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProductScopeMapper.insertHistoricalScope; pms-module-integration/src/main/resources/mapper/sync/DirectProductScopeMapper.xml:69; kind=insert
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
