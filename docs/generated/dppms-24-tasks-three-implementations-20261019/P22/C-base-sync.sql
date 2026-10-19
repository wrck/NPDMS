-- 当前P22优先见 ../base-sync-p22-order-line-preview-20261019.sql 与 .md；以下保留早期冻结SQL，不代表当前订单前置。
-- 当前实现优先见 ../base-sync-department-code-preview-20261019.sql 与 .md；本文件以下为早期冻结SQL。
-- 最新原ID/用户名规则及P18/P22/源范围SQL变更见 ../base-sync-original-identifiers-preview-20261019.md 与 .sql；以下保留初次汇总SQL。
-- 初次汇总未执行本脚本；2026-10-19后续真实API预览及失败/修复结论见../base-sync-13-failures-followup-20261019.md。
-- P22 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectProductScope] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P22SourceMapper.xml
SELECT STRAIGHT_JOIN l.id AS sourceId,l.projectId AS originalProjectId,canonical.projectId AS canonicalProjectId,p.projectCode AS originalProjectCode,p.projectType,l.contractNo,l.itemCode,l.itemName,l.projectQuantity,l.orderQuantity,l.deliverQuantity,l.openQuantity,l.orderNumber,l.lineNum FROM pm_project_product_line l
JOIN pm_project p ON p.projectId=l.projectId
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
 ON canonical.typeKey=CAST(p.projectType AS BINARY) AND canonical.codeKey=CAST(p.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0 AND current_project.effectiveTo IS NULL AND current_project.projectType='10' ORDER BY l.id LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$TargetMapper.selectAssociatedLines; pms-module-integration/src/main/resources/mapper/basesync/P22TargetMapper.xml:3; kind=select
SELECT DISTINCT l.id,l.source_system,l.company_code,l.company_name,l.order_type,l.order_no,l.line_no,l.product_code,l.order_qty,cr.project_id,c.contract_no FROM com_sales_order_line l
        JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id AND o.deleted=0
        JOIN com_contract c ON c.tenant_id=o.tenant_id AND c.deleted=0 
        JOIN com_project_contract_relation cr ON cr.contract_id=c.id AND cr.tenant_id=c.tenant_id AND cr.deleted=0
        WHERE l.tenant_id=#{query.tenantId} AND l.deleted=0 
          AND cr.status='ACTIVE' AND (cr.effective_from IS NULL OR cr.effective_from <= CURRENT_TIMESTAMP)
          AND (cr.effective_to IS NULL OR cr.effective_to > CURRENT_TIMESTAMP)
          AND l.source_system IN ('SAP','D365','SMS') AND l.source_system=o.source_system
          AND l.company_code=o.company_code AND l.order_type=o.order_type
          AND l.status='ENABLED' AND o.status='ENABLED'
          AND (
            (CAST(o.contract_no AS BINARY)=CAST(c.contract_no AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(c.company_code AS BINARY))
            OR EXISTS (SELECT 1 FROM com_order_contract_relation r WHERE r.tenant_id=o.tenant_id AND r.order_id=o.id
              AND r.contract_id=c.id AND r.deleted=0 AND r.effective_from <= CURRENT_TIMESTAMP
              AND (r.effective_to IS NULL OR r.effective_to > CURRENT_TIMESTAMP))
          )
    
 /* CHOOSE  */ /* WHEN test=query.references != null and !query.references.isEmpty() */ AND ( /* FOREACH collection=query.references; item=r; separator= OR  */ (cr.project_id=#{r.projectId} AND CAST(c.contract_no AS BINARY)=CAST(#{r.contractNo} AS BINARY) AND CAST(l.order_no AS BINARY)=CAST(#{r.orderNo} AS BINARY) AND CAST(l.line_no AS BINARY)=CAST(#{r.lineNo} AS BINARY) AND CAST(l.product_code AS BINARY)=CAST(#{r.productCode} AS BINARY)) /* repeat with separator= OR ; END FOREACH */ ) /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$TargetMapper.selectSnapshots; pms-module-integration/src/main/resources/mapper/basesync/P22TargetMapper.xml:23; kind=select
SELECT p.id AS project_id,p.version AS project_version,p.project_code,p.project_name,p.company_code,p.company_name,p.customer_code,p.customer_name,p.manager_employee_no,p.manager_name,
d.id AS department_id,d.code AS department_code,d.name AS department_name,d.version AS department_version,COALESCE(v.scope_version,0) AS scope_version
FROM proj_project p LEFT JOIN system_dept d ON d.tenant_id=p.tenant_id AND d.id=p.department_id AND d.code=p.department_code AND d.deleted=FALSE
LEFT JOIN com_delivery_scope_project_version v ON v.tenant_id=p.tenant_id AND v.project_id=p.id AND v.deleted=FALSE
WHERE p.tenant_id=#{query.tenantId} AND p.deleted=FALSE
 /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND p.id IN  /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$TargetMapper.selectScopes; pms-module-integration/src/main/resources/mapper/basesync/P22TargetMapper.xml:29; kind=select
SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId}
 /* CHOOSE  */ /* WHEN test=query.evidence != null and !query.evidence.isEmpty() */ AND source_evidence IN  /* FOREACH collection=query.evidence; item=key; open=(; close=); separator=, */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$TargetMapper.selectDetails; pms-module-integration/src/main/resources/mapper/basesync/P22TargetMapper.xml:31; kind=select
SELECT * FROM com_delivery_scope_detail WHERE tenant_id=#{query.tenantId}
 /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND delivery_scope_id IN  /* FOREACH collection=query.ids; item=key; open=(; close=); separator=, */ (#{key}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$TargetMapper.insertProductScope; pms-module-integration/src/main/resources/mapper/basesync/P22TargetMapper.xml:33; kind=insert
INSERT INTO com_delivery_scope
          (id,tenant_id,project_id,project_code,project_name,project_company_code,project_company_name,
           project_department_code,project_department_name,project_customer_code,project_customer_name,
           project_manager_employee_no,project_manager_name,order_line_id,order_source_system,order_company_code,
           order_company_name,order_type,order_no,line_no,product_code,product_desc,allocated_qty,
           allocation_confirmed,delivered_quantity,open_quantity,scope_status,allocation_version,allocation_source,
           change_reason,department_id,department_code,department_name,department_version,source_evidence,
           effective_from,effective_to,status,version,creator,create_time,updater,update_time,deleted)
        VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.projectId},#{row.target.projectCode},#{row.target.projectName},#{row.target.projectCompanyCode},#{row.target.projectCompanyName},
           #{row.target.projectDepartmentCode},#{row.target.projectDepartmentName},#{row.target.projectCustomerCode},#{row.target.projectCustomerName},
           #{row.target.projectManagerEmployeeNo},#{row.target.projectManagerName},#{row.target.orderLineId},#{row.target.orderSourceSystem},#{row.target.orderCompanyCode},
           #{row.target.orderCompanyName},#{row.target.orderType},#{row.target.orderNo},#{row.target.lineNo},#{row.target.productCode},#{row.target.productDesc},#{row.target.allocatedQty},
           0,#{row.target.deliveredQuantity},#{row.target.openQuantity},'ACTIVE',#{row.target.allocationVersion},'DPPMS',
           NULL,#{row.target.departmentId},#{row.target.departmentCode},#{row.target.departmentName},#{row.target.departmentVersion},#{row.target.sourceEvidence},
           #{row.target.effectiveFrom},NULL,'ENABLED',0,#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},0) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$TargetMapper.insertDetails; pms-module-integration/src/main/resources/mapper/basesync/P22TargetMapper.xml:49; kind=insert
INSERT INTO com_delivery_scope_detail
          (id,tenant_id,delivery_scope_id,detail_sequence,serial_no,product_code,product_name,
           device_type_code,device_type_name,delivery_batch_no,source_record_key,allocated_qty,detail_status,
           source_snapshot,remark,version,creator,create_time,updater,update_time,deleted)
        VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.detail.id},#{row.detail.tenantId},#{row.detail.deliveryScopeId},1,NULL,#{row.detail.productCode},#{row.detail.productName},
           NULL,NULL,NULL,#{row.detail.sourceRecordKey},#{row.detail.allocatedQty},'ACTIVE',NULL,NULL,0,
           #{row.detail.creator},#{row.detail.createTime},#{row.detail.updater},#{row.detail.updateTime},0) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
