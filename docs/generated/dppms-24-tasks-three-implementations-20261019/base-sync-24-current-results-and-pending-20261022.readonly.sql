-- Evidence only. Source: 127.0.0.1:3306/dppms; target:172.17.90.178:13306/npdms.
-- Each labelled SELECT belongs to its own connection; %s are bound parameters, not string substitutions.
-- Original failing P22 query and repaired candidate are separately labelled as historical/current evidence.
-- No DDL, DML, migration or sync execution statements.

-- P22 source counts
-- bound parameters: []
SELECT COUNT(*) total,SUM(COALESCE(l.projectQuantity,l.orderQuantity)=0) zero_quantity,SUM(COALESCE(l.projectQuantity,l.orderQuantity)>0) positive,SUM(COALESCE(l.projectQuantity,l.orderQuantity)>0 AND (p.column001 IS NULL OR TRIM(p.column001)='')) missing_department FROM pm_project_product_line l
JOIN pm_project p ON p.projectId=l.projectId
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
 ON canonical.typeKey=CAST(p.projectType AS BINARY) AND canonical.codeKey=CAST(p.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0 AND current_project.effectiveTo IS NULL AND current_project.projectType='10' ;

-- P22 positive exact business-key groups
-- bound parameters: []
SELECT canonical.projectId canonicalProjectId,current_project.compId originalCompanyId,l.orderNumber,l.lineNum,l.itemCode,p.column001 departmentCode,MIN(l.id) sampleSourceId,COUNT(*) n FROM pm_project_product_line l
JOIN pm_project p ON p.projectId=l.projectId
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
 ON canonical.typeKey=CAST(p.projectType AS BINARY) AND canonical.codeKey=CAST(p.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0 AND current_project.effectiveTo IS NULL AND current_project.projectType='10'  AND COALESCE(l.projectQuantity,l.orderQuantity)>0 GROUP BY canonical.projectId,current_project.compId,l.orderNumber,l.lineNum,l.itemCode,p.column001;

-- P22 original company directory
-- bound parameters: []
SELECT id,compCode FROM t_company;

-- P33 five missing devices and two deferred transfers
-- bound parameters: [68590, 72828, 68689, 139767, 208442, 1161000, 1161002]
SELECT id,projectId,barcode,contractNo,itemCode,transferFlag,chProjectId,chContractNo,transferProjectId,transferContractNo,createTime,CHAR_LENGTH(installAddress) addressLength FROM pm_project_shipment WHERE id IN (%s,%s,%s,%s,%s,%s,%s) ORDER BY id;

-- P22 exact owned BASE_P14 lines
-- bound parameters: [2112881203220676609, 1]
SELECT l.id,l.company_code,l.order_no,l.line_no,l.product_code
 FROM com_sales_order_line l
 JOIN com_sales_order o ON o.tenant_id=l.tenant_id AND o.id=l.order_id AND o.deleted=FALSE AND o.status='ENABLED'
  AND CAST(o.source_system AS BINARY)=CAST(l.source_system AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(l.company_code AS BINARY) AND CAST(o.order_type AS BINARY)=CAST(l.order_type AS BINARY) AND CAST(o.order_no AS BINARY)=CAST(l.order_no AS BINARY)
 JOIN int_base_sync_binding ob ON ob.tenant_id=l.tenant_id AND ob.target_id=l.id AND ob.object_key='SALES_LINE' AND ob.deleted=FALSE
  AND CAST(ob.source_key AS BINARY)=CAST(SHA2(CAST(JSON_ARRAY(l.source_system,l.company_code,l.line_type,l.order_no,l.line_no) AS CHAR),256) AS BINARY)
 JOIN int_base_sync_task ot ON ot.tenant_id=ob.tenant_id AND ot.id=ob.task_id AND ot.task_code='BASE_P14' AND ot.connection_id=%s AND ot.deleted=FALSE
 JOIN int_base_sync_run rr ON rr.tenant_id=ot.tenant_id AND rr.task_id=ot.id AND rr.connection_id=ot.connection_id AND rr.id=ob.last_run_id AND rr.task_code='BASE_P14' AND rr.status='COMPLETED' AND rr.preview=FALSE AND rr.deleted=FALSE
 WHERE l.tenant_id=%s AND l.deleted=FALSE AND l.status='ENABLED' AND l.source_system IN ('SAP','D365','SMS')
;

-- P22 tenant department catalogue
-- bound parameters: []
SELECT id,code,name,version FROM system_dept WHERE tenant_id=1 AND deleted=0 AND code IS NOT NULL AND code<>'';

-- P22 target pending-reference column definitions
-- bound parameters: []
SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='com_delivery_scope' AND COLUMN_NAME IN ('order_line_id','order_source_system','order_company_code','order_type','department_id','department_code','department_name','department_version') ORDER BY ORDINAL_POSITION;

-- P22 target foreign keys
-- bound parameters: []
SELECT CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='com_delivery_scope' AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY CONSTRAINT_NAME,ORDINAL_POSITION;

-- P22 P33 non-preview runs
-- bound parameters: []
SELECT task_code,COUNT(*) n FROM int_base_sync_run WHERE task_code IN ('BASE_P22','BASE_P33') AND preview=0 GROUP BY task_code;

-- P14 latest completed receipts
-- bound parameters: []
SELECT id,status,read_count,inserted_count,unchanged_count FROM int_base_sync_run WHERE tenant_id=1 AND task_code='BASE_P14' AND preview=0 ORDER BY id DESC LIMIT 2;

-- P33 exact formal device matching
-- bound parameters: ["31020151111E", "53010130D165000015", "31120151228B", "02050432D177000008", "01100107D184000007", "020522970248000018", "020522970248000016"]
SELECT d.id,d.sn,requests.requested_sn,d.project_id,d.contract_no FROM ast_device d JOIN (VALUES ROW(%s),ROW(%s),ROW(%s),ROW(%s),ROW(%s),ROW(%s),ROW(%s)) requests(requested_sn) ON d.sn=CONVERT(requests.requested_sn USING utf8mb4) COLLATE utf8mb4_unicode_ci WHERE d.tenant_id=1 AND d.deleted=0;

-- Current formal selection-route aggregate
-- bound parameters: []
SELECT (SELECT COUNT(*) FROM ast_device WHERE tenant_id=1 AND deleted=0 AND project_id IS NOT NULL) devicesWithProject,(SELECT COUNT(*) FROM proj_project WHERE tenant_id=1 AND deleted=0 AND contract_no IS NOT NULL AND TRIM(contract_no)<>'') projectsWithRegisteredContract,(SELECT COUNT(*) FROM com_project_contract_relation WHERE tenant_id=1 AND deleted=0 AND status='ACTIVE' AND contract_id IS NOT NULL AND (effective_from IS NULL OR effective_from<=CURRENT_TIMESTAMP) AND (effective_to IS NULL OR effective_to>CURRENT_TIMESTAMP)) currentResolvedRelations,(SELECT COUNT(*) FROM com_delivery_scope WHERE tenant_id=1 AND deleted=0 AND allocation_confirmed=1 AND scope_status='ACTIVE' AND effective_to IS NULL) confirmedDeliveryScopes;

-- Source key serialization narrow sample
SELECT id,source,compCode,lineType,orderNumber,lineNum,CAST(JSON_ARRAY(source,CASE compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE compCode END,lineType,orderNumber,lineNum) AS CHAR) serialized,SHA2(CAST(JSON_ARRAY(source,CASE compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE compCode END,lineType,orderNumber,lineNum) AS CHAR),256) computedKey FROM pm_order_line_from_erp WHERE source='D365' AND compCode='01' AND orderNumber='SO045509' AND lineNum IN ('1','10') ORDER BY id LIMIT 5;

-- P22 candidate type-preserving read-only exact matching, args connection/tenant
SELECT l.id,l.company_code,l.order_no,l.line_no,l.product_code
 FROM com_sales_order_line l
 JOIN com_sales_order o ON o.tenant_id=l.tenant_id AND o.id=l.order_id AND o.deleted=FALSE AND o.status='ENABLED'
  AND CAST(o.source_system AS BINARY)=CAST(l.source_system AS BINARY) AND CAST(o.company_code AS BINARY)=CAST(l.company_code AS BINARY) AND CAST(o.order_type AS BINARY)=CAST(l.order_type AS BINARY) AND CAST(o.order_no AS BINARY)=CAST(l.order_no AS BINARY)
 JOIN int_base_sync_binding ob ON ob.tenant_id=l.tenant_id AND ob.target_id=l.id AND ob.object_key='SALES_LINE' AND ob.deleted=FALSE
  AND CAST(ob.source_key AS BINARY)=CAST(SHA2(CAST(JSON_ARRAY(l.source_system,l.company_code,CAST(l.line_type AS UNSIGNED),l.order_no,l.line_no) AS CHAR),256) AS BINARY)
 JOIN int_base_sync_task ot ON ot.tenant_id=ob.tenant_id AND ot.id=ob.task_id AND ot.task_code='BASE_P14' AND ot.connection_id=%s AND ot.deleted=FALSE
 JOIN int_base_sync_run rr ON rr.tenant_id=ot.tenant_id AND rr.task_id=ot.id AND rr.connection_id=ot.connection_id AND rr.id=ob.last_run_id AND rr.task_code='BASE_P14' AND rr.status='COMPLETED' AND rr.preview=FALSE AND rr.deleted=FALSE
 WHERE l.tenant_id=%s AND l.deleted=FALSE AND l.status='ENABLED' AND l.source_system IN ('SAP','D365','SMS')
;

-- P33 full current selection contracts
SELECT rel.project_id,c.contract_no FROM com_project_contract_relation rel JOIN com_contract c ON c.tenant_id=rel.tenant_id AND c.id=rel.contract_id AND c.deleted=0 WHERE rel.tenant_id=1 AND rel.deleted=0 AND rel.status='ACTIVE' AND (rel.effective_from IS NULL OR rel.effective_from<=CURRENT_TIMESTAMP) AND (rel.effective_to IS NULL OR rel.effective_to>CURRENT_TIMESTAMP);
-- P33 current source stream (application scope/Java blank filter retained)
SELECT id,projectId,barcode,installAddress,transferFlag,chProjectId,chContractNo,transferProjectId,transferContractNo FROM pm_project_shipment WHERE installAddress IS NOT NULL AND TRIM(installAddress)<>'' ORDER BY id;

-- source12
SELECT id,projectId,barcode,contractNo,createTime FROM pm_project_shipment WHERE id IN (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) ORDER BY id;

-- device12 candidates
SELECT req.source_id,d.id device_id,d.sn,d.contract_no device_contract_no,d.project_id device_project_id,p.company_code project_company_code,c.id candidate_contract_id,c.company_code candidate_company_code,c.contract_no candidate_contract_no,c.deleted candidate_deleted FROM (VALUES ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s),ROW(%s,%s,%s,%s)) req(source_id,device_id,project_id,source_contract_no) JOIN ast_device d ON d.id=req.device_id AND d.tenant_id=1 AND d.deleted=0 JOIN proj_project p ON p.id=req.project_id AND p.tenant_id=1 LEFT JOIN com_contract c ON c.tenant_id=1 AND (CAST(c.contract_no AS BINARY)=CAST(req.source_contract_no AS BINARY) OR CONVERT(c.contract_no USING utf8mb4) COLLATE utf8mb4_unicode_ci=CONVERT(d.contract_no USING utf8mb4) COLLATE utf8mb4_unicode_ci) ORDER BY req.source_id,c.id;

-- relations for sampled projects
SELECT r.id,r.project_id,r.contract_id,r.contract_no raw_relation_contract_no,r.status,r.deleted,r.effective_from,r.effective_to,c.contract_no formal_contract_no,c.company_code formal_company_code,c.deleted contract_deleted,(r.deleted=0 AND r.status='ACTIVE' AND c.id IS NOT NULL AND c.deleted=0 AND (r.effective_from IS NULL OR r.effective_from<=CURRENT_TIMESTAMP) AND (r.effective_to IS NULL OR r.effective_to>CURRENT_TIMESTAMP)) qualifies_current_contract_context FROM com_project_contract_relation r LEFT JOIN com_contract c ON c.id=r.contract_id AND c.tenant_id=r.tenant_id WHERE r.tenant_id=1 AND r.project_id IN (%s,%s,%s,%s) ORDER BY r.project_id,r.id;

-- P22 corrected302 original raw-row scan; exact target candidate applied in memory
SELECT l.id sourceId,l.projectId projectId,canonical.projectId canonicalProjectId,current_project.compId originalCompanyId,company.compCode originalCompanyCode,CASE company.compCode WHEN '01' THEN '001' WHEN '02' THEN '002' WHEN '03' THEN '003' WHEN '04' THEN '004' ELSE NULL END companyCode,l.orderNumber,l.lineNum,l.itemCode,l.projectQuantity,l.orderQuantity,l.deliverQuantity,l.openQuantity,p.column001 originalDepartmentCode FROM pm_project_product_line l
JOIN pm_project p ON p.projectId=l.projectId
JOIN (SELECT CAST(projectType AS BINARY) AS typeKey,CAST(projectCode AS BINARY) AS codeKey,MAX(projectId) AS projectId FROM pm_project GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
 ON canonical.typeKey=CAST(p.projectType AS BINARY) AND canonical.codeKey=CAST(p.projectCode AS BINARY)
JOIN pm_project current_project ON current_project.projectId=canonical.projectId
LEFT JOIN t_company company ON company.id=current_project.compId
WHERE p.disabled=0 AND p.effectiveTo IS NULL AND current_project.disabled=0 AND current_project.effectiveTo IS NULL AND current_project.projectType='10'  AND COALESCE(l.projectQuantity,l.orderQuantity)>0 ORDER BY l.id;
