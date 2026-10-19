-- Current new-suite SQL excerpts after the 2026-10-19 original-identifier decision.
-- MyBatis XML preserved; not an executable SQL bundle. No SQL or post-processing executed by this report.
-- P16 proj_project.id/root_id/code_root_id <- selected original pm_project.projectId in Java.
-- creator/updater <- raw source usernames; no platform account ID lookup.

-- SOURCE C:\Users\user\Documents\Codex\2026-10-06\task-3\project-detail-followup\pms-module-integration\src\main\resources\base-sync-source\BaseSyncPublishedProjectsSourceMapper.xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncPublishedProjects$SourceMapper">
  <select id="selectPreviewScope" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncPublishedProjects$Member">
    SELECT CAST(p.projectId AS CHAR) AS source_key,chosen.projectId AS project_id,p.projectType AS project_type,
           p.projectCode AS original_project_code,current_project.projectCode AS project_code,NULL AS fingerprint
    FROM pm_project p
    JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId
          FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) chosen
      ON chosen.typeKey=CAST(p.projectType AS BINARY) AND chosen.codeKey=CAST(p.projectCode AS BINARY)
    JOIN pm_project current_project ON current_project.projectId=chosen.projectId
    WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0
      AND current_project.effectiveTo IS NULL AND current_project.projectType='10'
    ORDER BY p.projectId LIMIT #{limit}
  </select>
</mapper>


-- SOURCE C:\Users\user\Documents\Codex\2026-10-06\task-3\project-detail-followup\pms-module-integration\src\main\resources\base-sync-source\P18SourceMapper.xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask$SourceMapper"><select id="selectContracts" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask$Source"><![CDATA[SELECT id AS contractSourceId,contractNo,projectGroupCode,createTime AS contractCreateTime,createBy AS contractCreateBy,updateTime AS contractUpdateTime,updateBy AS contractUpdateBy FROM pm_project_contract ORDER BY id LIMIT #{limit}]]></select>
<select id="selectGroups" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask$Source"><![CDATA[SELECT id AS groupId,projectGroupCode AS groupProjectGroupCode,projectGroupName AS groupProjectGroupName,projectType,createTime AS groupCreateTime,createBy AS groupCreateBy,updateTime AS groupUpdateTime,updateBy AS groupUpdateBy FROM pm_project_group WHERE projectType='10' ORDER BY id LIMIT #{limit}]]></select>
<select id="selectMembers" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask$Source"><![CDATA[SELECT id AS memberId,projectGroupCode AS memberProjectGroupCode,projectCode AS memberProjectCode,mergeBranchMark,smsProjectCode,createTime AS memberCreateTime,createBy AS memberCreateBy,updateTime AS memberUpdateTime,updateBy AS memberUpdateBy FROM pm_project_group_relationship ORDER BY id LIMIT #{limit}]]></select>
<select id="selectProjects" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P18ProjectContractSyncTask$Source"><![CDATA[SELECT p.projectId AS originalProjectId,p.projectCode AS originalProjectCode,company.compCode AS originalCompanyCode FROM pm_project p JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) chosen ON chosen.projectId=p.projectId LEFT JOIN t_company company ON company.id=p.compId WHERE p.disabled=0 AND p.effectiveTo IS NULL AND p.projectType='10' ORDER BY p.projectId LIMIT #{limit}]]></select>
</mapper>


-- SOURCE C:\Users\user\Documents\Codex\2026-10-06\task-3\project-detail-followup\pms-module-integration\src\main\resources\mapper\basesync\P22TargetMapper.xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$TargetMapper"><select id="selectAssociatedLines" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$OrderLine">
        SELECT DISTINCT l.id,l.source_system,l.company_code,l.company_name,l.order_type,l.order_no,l.line_no,l.product_code,l.order_qty,cr.project_id,c.contract_no FROM com_sales_order_line l
        JOIN com_sales_order o ON o.id=l.order_id AND o.tenant_id=l.tenant_id AND o.deleted=0
        JOIN com_contract c ON c.tenant_id=o.tenant_id AND c.deleted=0 
        JOIN com_project_contract_relation cr ON cr.contract_id=c.id AND cr.tenant_id=c.tenant_id AND cr.deleted=0
        WHERE l.tenant_id=#{query.tenantId} AND l.deleted=0 
          AND cr.status='ACTIVE' AND (cr.effective_from IS NULL OR cr.effective_from &lt;= CURRENT_TIMESTAMP)
          AND (cr.effective_to IS NULL OR cr.effective_to &gt; CURRENT_TIMESTAMP)
          AND l.source_system IN ('SAP','D365','SMS') AND l.source_system=o.source_system
          AND l.company_code=o.company_code AND l.order_type=o.order_type
          AND l.status='ENABLED' AND o.status='ENABLED'
          AND (
            (CAST(o.contract_no AS BINARY)=CAST(c.contract_no AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(c.company_code AS BINARY))
            OR EXISTS (SELECT 1 FROM com_order_contract_relation r WHERE r.tenant_id=o.tenant_id AND r.order_id=o.id
              AND r.contract_id=c.id AND r.deleted=0 AND r.effective_from &lt;= CURRENT_TIMESTAMP
              AND (r.effective_to IS NULL OR r.effective_to &gt; CURRENT_TIMESTAMP))
          )
    
<choose><when test="query.references != null and !query.references.isEmpty()">AND (<foreach collection="query.references" item="r" separator=" OR ">(cr.project_id=#{r.projectId} AND CAST(c.contract_no AS BINARY)=CAST(#{r.contractNo} AS BINARY) AND CAST(l.order_no AS BINARY)=CAST(#{r.orderNo} AS BINARY) AND CAST(l.line_no AS BINARY)=CAST(#{r.lineNo} AS BINARY) AND CAST(l.product_code AS BINARY)=CAST(#{r.productCode} AS BINARY))</foreach>)</when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if>
</select>
<select id="selectSnapshots" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$Snapshot">SELECT p.id AS project_id,p.version AS project_version,p.project_code,p.project_name,p.company_code,p.company_name,p.customer_code,p.customer_name,p.manager_employee_no,p.manager_name,
d.id AS department_id,d.code AS department_code,d.name AS department_name,d.version AS department_version,COALESCE(v.scope_version,0) AS scope_version
FROM proj_project p LEFT JOIN system_dept d ON d.tenant_id=p.tenant_id AND d.id=p.department_id AND CAST(d.code AS BINARY)=CAST(p.department_code AS BINARY) AND d.deleted=FALSE
LEFT JOIN com_delivery_scope_project_version v ON v.tenant_id=p.tenant_id AND v.project_id=p.id AND v.deleted=FALSE
WHERE p.tenant_id=#{query.tenantId} AND p.deleted=FALSE
<choose><when test="query.ids != null and !query.ids.isEmpty()">AND p.id IN <foreach collection="query.ids" item="id" open="(" close=")" separator=",">#{id}</foreach></when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if></select>
<select id="selectScopes" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$ScopeRow">SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId}
<choose><when test="query.evidence != null and !query.evidence.isEmpty()">AND source_evidence IN <foreach collection="query.evidence" item="key" open="(" close=")" separator=",">#{key}</foreach></when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if></select>
<select id="selectDetails" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$DetailRow">SELECT * FROM com_delivery_scope_detail WHERE tenant_id=#{query.tenantId}
<choose><when test="query.ids != null and !query.ids.isEmpty()">AND delivery_scope_id IN <foreach collection="query.ids" item="key" open="(" close=")" separator=",">#{key}</foreach></when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if></select>
<insert id="insertProductScope">
        INSERT INTO com_delivery_scope
          (id,tenant_id,project_id,project_code,project_name,project_company_code,project_company_name,
           project_department_code,project_department_name,project_customer_code,project_customer_name,
           project_manager_employee_no,project_manager_name,order_line_id,order_source_system,order_company_code,
           order_company_name,order_type,order_no,line_no,product_code,product_desc,allocated_qty,
           allocation_confirmed,delivered_quantity,open_quantity,scope_status,allocation_version,allocation_source,
           change_reason,department_id,department_code,department_name,department_version,source_evidence,
           effective_from,effective_to,status,version,creator,create_time,updater,update_time,deleted)
        VALUES <foreach collection="rows" item="row" separator=",">(#{row.target.id},#{row.target.tenantId},#{row.target.projectId},#{row.target.projectCode},#{row.target.projectName},#{row.target.projectCompanyCode},#{row.target.projectCompanyName},
           #{row.target.projectDepartmentCode},#{row.target.projectDepartmentName},#{row.target.projectCustomerCode},#{row.target.projectCustomerName},
           #{row.target.projectManagerEmployeeNo},#{row.target.projectManagerName},#{row.target.orderLineId},#{row.target.orderSourceSystem},#{row.target.orderCompanyCode},
           #{row.target.orderCompanyName},#{row.target.orderType},#{row.target.orderNo},#{row.target.lineNo},#{row.target.productCode},#{row.target.productDesc},#{row.target.allocatedQty},
           0,#{row.target.deliveredQuantity},#{row.target.openQuantity},'ACTIVE',#{row.target.allocationVersion},'DPPMS',
           NULL,#{row.target.departmentId},#{row.target.departmentCode},#{row.target.departmentName},#{row.target.departmentVersion},#{row.target.sourceEvidence},
           #{row.target.effectiveFrom},NULL,'ENABLED',0,#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},0)</foreach></insert>
<insert id="insertDetails">
        INSERT INTO com_delivery_scope_detail
          (id,tenant_id,delivery_scope_id,detail_sequence,serial_no,product_code,product_name,
           device_type_code,device_type_name,delivery_batch_no,source_record_key,allocated_qty,detail_status,
           source_snapshot,remark,version,creator,create_time,updater,update_time,deleted)
        VALUES <foreach collection="rows" item="row" separator=",">(#{row.detail.id},#{row.detail.tenantId},#{row.detail.deliveryScopeId},1,NULL,#{row.detail.productCode},#{row.detail.productName},
           NULL,NULL,NULL,#{row.detail.sourceRecordKey},#{row.detail.allocatedQty},'ACTIVE',NULL,NULL,0,
           #{row.detail.creator},#{row.detail.createTime},#{row.detail.updater},#{row.detail.updateTime},0)</foreach></insert>
</mapper>


