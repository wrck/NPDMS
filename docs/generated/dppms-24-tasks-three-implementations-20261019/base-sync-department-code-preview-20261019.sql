-- Current independent-suite XML after explicit 2026-10-19 decisions.
-- Review artifact only; MyBatis placeholders/dynamic nodes retained. No statement executed by report generation.
-- P22 department_id starts at -1; backfill is called once only outside preview, scoped to current BASE_P22 bindings.
-- Deferred P19/P35 extension fields are absent from the active source queries and writes.

-- P19 source | C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/base-sync-source/P19SourceMapper.xml
-- namespace: cn.iocoder.yudao.module.pms.integration.basesync.task.P19ProjectFactsSyncTask$SourceMapper
-- select selectProjectParty
<select id="selectProjectParty" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P19ProjectFactsSyncTask$Source">SELECT STRAIGHT_JOIN r.id AS sourceId,r.projectId AS originalProjectId,canonical.projectId AS canonicalProjectId,p.projectCode AS originalProjectCode,p.projectType,r.partyRole,r.partyCode,r.partyName,r.createTime,r.createBy AS source_creator,r.updateTime,r.updateBy AS source_updater,r.effectiveFrom,r.effectiveTo FROM pm_project_related_party r
JOIN pm_project p ON p.projectId=r.projectId
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
 ON canonical.typeKey=CAST(p.projectType AS BINARY) AND canonical.codeKey=CAST(p.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0 AND current_project.effectiveTo IS NULL AND current_project.projectType='10' ORDER BY r.id LIMIT #{limit}</select>

-- P19 target | C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P19TargetMapper.xml
-- namespace: cn.iocoder.yudao.module.pms.integration.basesync.task.P19ProjectFactsSyncTask$TargetMapper
-- select selectParties
<select id="selectParties" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P19ProjectFactsSyncTask$PartyRow">SELECT `id`,`tenant_id`,`project_id`,`party_role`,`party_code`,`party_name`,`contact_name`,`phone`,`source_system`,`source_table`,`source_record_key`,`effective_from`,`effective_to`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM proj_project_party WHERE tenant_id=#{query.tenantId} AND source_system='DPPMS' AND source_table='pm_project_related_party'
<choose><when test="query.keys != null and !query.keys.isEmpty()">AND source_record_key IN <foreach collection="query.keys" item="key" open="(" close=")" separator=",">#{key}</foreach></when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if></select>

-- insert insertProjectParty
<insert id="insertProjectParty">INSERT INTO proj_project_party (`id`,`tenant_id`,`project_id`,`party_role`,`party_code`,`party_name`,`contact_name`,`phone`,`source_system`,`source_table`,`source_record_key`,`effective_from`,`effective_to`,`status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES <foreach collection="rows" item="row" separator=",">(#{row.target.id},#{row.target.tenantId},#{row.target.projectId},#{row.target.partyRole},#{row.target.partyCode},#{row.target.partyName},#{row.target.contactName},#{row.target.phone},#{row.target.sourceSystem},#{row.target.sourceTable},#{row.target.sourceRecordKey},#{row.target.effectiveFrom},#{row.target.effectiveTo},#{row.target.status},#{row.target.version},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted})</foreach></insert>

-- update updateProjectParty
<update id="updateProjectParty">UPDATE proj_project_party SET `project_id`=#{row.target.projectId},`party_role`=#{row.target.partyRole},`party_code`=#{row.target.partyCode},`party_name`=#{row.target.partyName},`effective_from`=#{row.target.effectiveFrom},`effective_to`=#{row.target.effectiveTo},`version`=#{row.target.version},`creator`=#{row.target.creator},`create_time`=#{row.target.createTime},`updater`=#{row.target.updater},`update_time`=#{row.target.updateTime} WHERE tenant_id=#{context.tenantId} AND id=#{row.target.id} AND version=#{row.expectedVersion} AND deleted=FALSE</update>

-- P22 source | C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/base-sync-source/P22SourceMapper.xml
-- namespace: cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$SourceMapper
-- select selectProductScope
<select id="selectProductScope" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$Source">SELECT STRAIGHT_JOIN l.id AS sourceId,l.projectId AS originalProjectId,canonical.projectId AS canonicalProjectId,p.projectCode AS originalProjectCode,current_project.projectName AS originalProjectName,current_project.column001 AS originalDepartmentCode,p.projectType,l.contractNo,l.itemCode,l.itemName,l.projectQuantity,l.orderQuantity,l.deliverQuantity,l.openQuantity,l.orderNumber,l.lineNum FROM pm_project_product_line l
JOIN pm_project p ON p.projectId=l.projectId
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
 ON canonical.typeKey=CAST(p.projectType AS BINARY) AND canonical.codeKey=CAST(p.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0 AND current_project.effectiveTo IS NULL AND current_project.projectType='10' ORDER BY l.id LIMIT #{limit}</select>

-- P22 target | C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P22TargetMapper.xml
-- namespace: cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$TargetMapper
-- select selectAssociatedLines
<select id="selectAssociatedLines" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$OrderLine">
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

-- select selectSnapshots
<select id="selectSnapshots" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$Snapshot">SELECT p.id AS project_id,p.version AS project_version,p.project_code,p.project_name,p.company_code,p.company_name,p.customer_code,p.customer_name,p.manager_employee_no,p.manager_name,
COALESCE(v.scope_version,0) AS scope_version
FROM proj_project p
LEFT JOIN com_delivery_scope_project_version v ON v.tenant_id=p.tenant_id AND v.project_id=p.id AND v.deleted=FALSE
WHERE p.tenant_id=#{query.tenantId} AND p.deleted=FALSE
<choose><when test="query.ids != null and !query.ids.isEmpty()">AND p.id IN <foreach collection="query.ids" item="id" open="(" close=")" separator=",">#{id}</foreach></when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if></select>

-- select selectScopes
<select id="selectScopes" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$ScopeRow">SELECT * FROM com_delivery_scope WHERE tenant_id=#{query.tenantId}
<choose><when test="query.evidence != null and !query.evidence.isEmpty()">AND source_evidence IN <foreach collection="query.evidence" item="key" open="(" close=")" separator=",">#{key}</foreach></when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if></select>

-- select selectDetails
<select id="selectDetails" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$DetailRow">SELECT * FROM com_delivery_scope_detail WHERE tenant_id=#{query.tenantId}
<choose><when test="query.ids != null and !query.ids.isEmpty()">AND delivery_scope_id IN <foreach collection="query.ids" item="key" open="(" close=")" separator=",">#{key}</foreach></when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if></select>

-- insert insertProductScope
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

-- insert insertDetails
<insert id="insertDetails">
        INSERT INTO com_delivery_scope_detail
          (id,tenant_id,delivery_scope_id,detail_sequence,serial_no,product_code,product_name,
           device_type_code,device_type_name,delivery_batch_no,source_record_key,allocated_qty,detail_status,
           source_snapshot,remark,version,creator,create_time,updater,update_time,deleted)
        VALUES <foreach collection="rows" item="row" separator=",">(#{row.detail.id},#{row.detail.tenantId},#{row.detail.deliveryScopeId},1,NULL,#{row.detail.productCode},#{row.detail.productName},
           NULL,NULL,NULL,#{row.detail.sourceRecordKey},#{row.detail.allocatedQty},'ACTIVE',NULL,NULL,0,
           #{row.detail.creator},#{row.detail.createTime},#{row.detail.updater},#{row.detail.updateTime},0)</foreach></insert>

-- select selectDepartments
<select id="selectDepartments" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P22ProductScopeSyncTask$Department">
 SELECT id,code,name,version FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE AND code IS NOT NULL AND code&lt;&gt;''
</select>

-- sql currentRunScopes
<sql id="currentRunScopes">
 JOIN int_base_sync_binding b ON b.tenant_id=s.tenant_id AND b.target_id=s.id AND b.object_key='PRODUCT_SCOPE' AND b.deleted=FALSE
 JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id AND t.task_code='BASE_P22'
  AND t.connection_id=#{query.connectionId} AND t.deleted=FALSE AND t.active_run_id=b.last_run_id
</sql>

-- update backfillDepartmentIds
<update id="backfillDepartmentIds">
 UPDATE com_delivery_scope s <include refid="currentRunScopes" />
 JOIN system_dept d ON d.tenant_id=s.tenant_id AND CAST(d.code AS BINARY)=CAST(s.department_code AS BINARY)
  AND d.deleted=FALSE AND d.id&gt;0 AND d.version=s.department_version AND CAST(d.name AS BINARY)=CAST(s.department_name AS BINARY)
 SET s.department_id=d.id,s.update_time=s.update_time
 WHERE s.tenant_id=#{query.tenantId} AND s.department_id=-1 AND s.deleted=FALSE AND s.allocation_source='DPPMS'
</update>

-- select countPendingDepartments
<select id="countPendingDepartments" resultType="long">
 SELECT COUNT(*) FROM com_delivery_scope s <include refid="currentRunScopes" />
 WHERE s.tenant_id=#{query.tenantId} AND s.department_id=-1 AND s.deleted=FALSE AND s.allocation_source='DPPMS'
</select>

-- P35 source | C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/base-sync-source/P35SourceMapper.xml
-- namespace: cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$SourceMapper
-- select selectSoftwareBatch
<select id="selectSoftwareBatch" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$Source">SELECT s.id AS sourceId,s.projectId AS originalProjectId,s.`changeVersion`,s.`changeRemark`,s.`latest`,s.createTime,s.createBy AS source_creator,s.updateTime,s.updateBy AS source_updater FROM pm_project_soft_change_logs s <choose><when test="projectKeys != null and !projectKeys.isEmpty()">WHERE s.projectId IN <foreach collection="projectKeys" item="key" open="(" close=")" separator=",">#{key}</foreach></when><otherwise>WHERE 1=0</otherwise></choose> ORDER BY s.id LIMIT #{limit}</select>

-- select selectSoftwareObservation
<select id="selectSoftwareObservation" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$Source">SELECT s.id AS sourceId,s.projectId AS originalProjectId,s.`logId`,s.`contractNo`,s.`itemCode`,s.`barCode`,s.`conp`,s.`conpType`,s.`conpSeries`,s.`conpMark`,s.`cpld`,s.`boot`,s.`pcb`,s.`executeTime`,s.`datastate`,s.`customInfo`,s.createTime,s.createBy AS source_creator,s.updateTime,s.updateBy AS source_updater FROM pm_project_soft_version s <choose><when test="projectKeys != null and !projectKeys.isEmpty()">WHERE s.projectId IN <foreach collection="projectKeys" item="key" open="(" close=")" separator=",">#{key}</foreach></when><otherwise>WHERE 1=0</otherwise></choose> ORDER BY s.id LIMIT #{limit}</select>

-- P35 target | C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup/pms-module-integration/src/main/resources/mapper/basesync/P35TargetMapper.xml
-- namespace: cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$TargetMapper
-- select selectBatches
<select id="selectBatches" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$BatchRow">SELECT `id`,`tenant_id`,`project_id`,`change_version`,`change_remark`,`latest`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM ast_device_software_change_batch WHERE tenant_id=#{query.tenantId} <choose><when test="query.ids != null and !query.ids.isEmpty()">AND id IN <foreach collection="query.ids" item="id" open="(" close=")" separator=",">#{id}</foreach></when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if></select>

-- insert insertSoftwareBatch
<insert id="insertSoftwareBatch">INSERT INTO ast_device_software_change_batch (`id`,`tenant_id`,`project_id`,`change_version`,`change_remark`,`latest`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES <foreach collection="rows" item="row" separator=",">(#{row.batch.id},#{row.batch.tenantId},#{row.batch.projectId},#{row.batch.changeVersion},#{row.batch.changeRemark},#{row.batch.latest},#{row.batch.creator},#{row.batch.createTime},#{row.batch.updater},#{row.batch.updateTime},#{row.batch.deleted})</foreach></insert>

-- select selectObservations
<select id="selectObservations" resultType="cn.iocoder.yudao.module.pms.integration.basesync.task.P35SoftwareHistorySyncTask$ObservationRow">SELECT `id`,`tenant_id`,`device_id`,`device_sn`,`project_id`,`previous_record_id`,`conp_version`,`conp_type`,`conp_series`,`conp_mark`,`boot_version`,`cpld_version`,`pcb_version`,`customized`,`release_date`,`version_desc`,`recorded_at`,`data_source`,`parsed_fact_id`,`request_key`,`change_batch_id`,`contract_no`,`product_code`,`execute_time`,`data_state`,`custom_info`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM ast_device_software_version_record WHERE tenant_id=#{query.tenantId} <choose><when test="query.ids != null and !query.ids.isEmpty()">AND id IN <foreach collection="query.ids" item="id" open="(" close=")" separator=",">#{id}</foreach></when><otherwise>AND 1=0</otherwise></choose><if test="!query.preview"> FOR UPDATE</if></select>

-- insert insertSoftwareObservation
<insert id="insertSoftwareObservation">INSERT INTO ast_device_software_version_record (`id`,`tenant_id`,`device_id`,`device_sn`,`project_id`,`previous_record_id`,`conp_version`,`conp_type`,`conp_series`,`conp_mark`,`boot_version`,`cpld_version`,`pcb_version`,`customized`,`release_date`,`version_desc`,`recorded_at`,`data_source`,`parsed_fact_id`,`request_key`,`change_batch_id`,`contract_no`,`product_code`,`execute_time`,`data_state`,`custom_info`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES <foreach collection="rows" item="row" separator=",">(#{row.observation.id},#{row.observation.tenantId},#{row.observation.deviceId},#{row.observation.deviceSn},#{row.observation.projectId},#{row.observation.previousRecordId},#{row.observation.conpVersion},#{row.observation.conpType},#{row.observation.conpSeries},#{row.observation.conpMark},#{row.observation.bootVersion},#{row.observation.cpldVersion},#{row.observation.pcbVersion},#{row.observation.customized},#{row.observation.releaseDate},#{row.observation.versionDesc},#{row.observation.recordedAt},#{row.observation.dataSource},#{row.observation.parsedFactId},#{row.observation.requestKey},#{row.observation.changeBatchId},#{row.observation.contractNo},#{row.observation.productCode},#{row.observation.executeTime},#{row.observation.dataState},#{row.observation.customInfo},#{row.observation.creator},#{row.observation.createTime},#{row.observation.updater},#{row.observation.updateTime},#{row.observation.deleted})</foreach></insert>

