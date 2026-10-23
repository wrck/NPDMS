-- Read-only diagnostic evidence; not a migration or data-correction script.
-- Target: 172.17.90.178:13306/npdms; source: existing read-only dppms connection.
-- Worktree HEAD c2d20d57b3ea0202c6ae0631711081ae87a6d773; source SQL verified against current P15SourceMapper.xml.
-- Execute source and target sections on their corresponding connections; placeholders denote audited scoped batch input.

-- Evidence: p15-owner-backfill-readonly-audit.sql
-- Read-only audit SQL; all joins here use numeric IDs only. Code/contract/dept keys are matched exactly in Python to avoid collation coercion.
SELECT b.source_key AS binding_source_key,c.id,c.company_code,c.contract_no,c.master_source_system,c.master_source_record_key FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id JOIN com_contract c ON c.tenant_id=b.tenant_id AND c.id=b.target_id WHERE t.tenant_id=1 AND t.task_code='BASE_P15' AND t.deleted=FALSE AND b.deleted=FALSE AND b.object_key='CONTRACT' AND c.deleted=FALSE ORDER BY c.id;

SELECT b.source_key AS binding_source_key,s.id,s.source_record_key,s.contract_no,s.company_code,s.company_id,s.contract_id,s.department_code,s.department_id FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id JOIN com_shipment_contract_reference s ON s.tenant_id=b.tenant_id AND s.id=b.target_id WHERE t.tenant_id=1 AND t.task_code='BASE_P15' AND t.deleted=FALSE AND b.deleted=FALSE AND b.object_key='SHIPMENT_CONTRACT' AND s.deleted=FALSE ORDER BY s.id;

SELECT id,contract_no,company_code,master_source_system,master_source_record_key FROM com_contract WHERE tenant_id=1 AND deleted=FALSE ORDER BY id;

SELECT id,code,name,status FROM system_company WHERE tenant_id=1 AND deleted=FALSE ORDER BY id;

SELECT id,code,name,status,parent_id FROM system_dept WHERE tenant_id=1 AND deleted=FALSE ORDER BY id;

-- Evidence: p15-owner-backfill-collation-audit.sql
-- Read-only collation-semantics audit. Uses native GROUP BY and direct contract_no equality; no COLLATE/CONVERT/DDL.
SELECT s.id AS shipment_id,b.source_key AS binding_source_key,s.source_record_key,s.contract_no AS shipment_contract_no,HEX(s.contract_no) AS shipment_contract_hex,c.id AS matched_contract_id,c.contract_no AS matched_contract_no,HEX(c.contract_no) AS matched_contract_hex,g.match_count AS native_group_count FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id JOIN com_shipment_contract_reference s ON s.tenant_id=b.tenant_id AND s.id=b.target_id LEFT JOIN com_contract c ON c.tenant_id=s.tenant_id AND c.deleted=0 AND c.contract_no=s.contract_no LEFT JOIN (SELECT tenant_id,contract_no,COUNT(*) AS match_count FROM com_contract WHERE tenant_id=1 AND deleted=0 GROUP BY tenant_id,contract_no) g ON g.tenant_id=s.tenant_id AND g.contract_no=s.contract_no WHERE t.tenant_id=1 AND t.task_code='BASE_P15' AND t.deleted=FALSE AND b.deleted=FALSE AND b.object_key='SHIPMENT_CONTRACT' AND s.deleted=FALSE AND s.source_system='DPPMS' ORDER BY s.id,c.id;

-- Evidence: device-owner-facts.sql
SELECT contract_no,MAX(company_id) company_id,MAX(company_name) company_name,
 CASE WHEN COUNT(DISTINCT NULLIF(department_code,''))=1 THEN MAX(department_id) END department_id,
 CASE WHEN COUNT(DISTINCT NULLIF(department_code,''))=1 THEN MAX(NULLIF(department_code,'')) END department_code,
 CASE WHEN COUNT(DISTINCT NULLIF(department_code,''))=1 THEN MAX(NULLIF(department_name,'')) END department_name
 FROM (SELECT contract_no,company_id,company_code,company_name,NULL department_id,department_code,department_name FROM com_contract WHERE tenant_id=1 AND deleted=0
 UNION ALL SELECT contract_no,company_id,company_code,company_name,department_id,department_code,department_name FROM com_shipment_contract_reference WHERE tenant_id=1 AND deleted=0) ownership
 WHERE contract_no IS NOT NULL AND contract_no<>'' GROUP BY contract_no
 HAVING COUNT(DISTINCT company_id)=1 AND COUNT(DISTINCT NULLIF(company_code,''))=1 AND SUM(CASE WHEN company_code IS NULL OR company_code='' THEN 1 ELSE 0 END)=0;

-- Evidence: p30-owner-unresolved-classification.sql
-- Read-only SQL evidence; audit_support.target_db session transaction_read_only=1.
-- Scope is active BASE_P30 DEVICE_BASE bindings and active target device rows with project_id IS NULL.
SELECT COUNT(*) bindings,COUNT(DISTINCT b.target_id) targets FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id WHERE t.tenant_id=1 AND t.task_code='BASE_P30' AND t.deleted=FALSE AND b.deleted=FALSE AND b.object_key='DEVICE_BASE';
SELECT id,target_id FROM int_base_sync_binding WHERE tenant_id=1 AND task_id=2112881209449218050 AND deleted=FALSE AND object_key='DEVICE_BASE' AND id><last_binding_id> ORDER BY id LIMIT 30000;
SELECT id,sn,contract_no FROM ast_device WHERE tenant_id=1 AND deleted=FALSE AND project_id IS NULL AND id IN (<page target ids>);
-- Owner aggregate unchanged: device-owner-facts.sql. Formal: SELECT contract_no FROM com_contract WHERE tenant_id=1 AND deleted=0 AND contract_no IS NOT NULL AND contract_no<>'';

-- Evidence: p30-null-contract-source-lineage.sql
-- Read-only lineage evidence. Connections created only through audit_support.target_db() and source_db(), both SET SESSION transaction_read_only=1.
-- Target BASE_P30/DEVICE_BASE active scope and candidates with no contract number:
SELECT COUNT(*) bindings, COUNT(DISTINCT b.target_id) targets
FROM int_base_sync_binding b JOIN int_base_sync_task t ON t.tenant_id=b.tenant_id AND t.id=b.task_id
WHERE t.tenant_id=1 AND t.task_code='BASE_P30' AND t.deleted=FALSE AND b.deleted=FALSE AND b.object_key='DEVICE_BASE';
SELECT d.id,d.sn,d.contract_no,d.shipment_record_id,s.source_key,s.legacy_package_key,s.contract_no AS shipment_contract_no,s.deleted AS shipment_deleted
FROM ast_device d LEFT JOIN ast_device_shipment s ON s.tenant_id=d.tenant_id AND s.id=d.shipment_record_id
WHERE d.tenant_id=1 AND d.deleted=FALSE AND d.project_id IS NULL AND (d.contract_no IS NULL OR TRIM(d.contract_no)='');
-- Source lookup repeated in bounded batches of exact decimal source_key IDs (1000 IDs per query):
SELECT b.id,b.pack_id,p.packlist_id,p.con_id,f.contract_id,f.contract_code
FROM fb_shipment_barcode b
LEFT JOIN fb_shipment p ON p.packlist_id=b.pack_id
LEFT JOIN fb_contract f ON f.contract_id=p.con_id
WHERE b.id IN (<exact source_key IDs>);
-- Source key joins above use the source mapper's physical columns. Exact spelling checks were performed in Python after retrieval.
-- No DML, DDL, or business SQL changes.

-- Evidence: p30-missing-contract-source-input-classification.sql
-- Read-only evidence. target_db/source_db helpers use SET SESSION transaction_read_only=1.
-- Historical query audit reference 08a8ecf38692a31c13dcb36b618f3b15ba8511e1; actual worktree HEAD c2d20d57b3ea0202c6ae0631711081ae87a6d773:
-- pms-module-integration/src/main/resources/base-sync-source/P15SourceMapper.xml, selectContractCustomers lines 17-25.
-- Its eligible contract generator is pm_order_data_from_erp; it filters source SAP/D365/SMS and nonempty compCode/contractNo (line 24), then ORDER BY id LIMIT #{limit} (line 25).
-- Raw exact target contract groups:
SELECT MIN(contract_no) raw_contract_no, COUNT(*) device_count, MIN(id) sample_id
FROM ast_device
WHERE tenant_id=1 AND deleted=FALSE AND project_id IS NULL
  AND contract_no IS NOT NULL AND TRIM(contract_no)<>''
GROUP BY BINARY contract_no;
-- Formal exact contract-key input set:
SELECT contract_no FROM com_contract
WHERE tenant_id=1 AND deleted=FALSE AND contract_no IS NOT NULL AND contract_no<>'';
-- Equivalent read projection of P15 selectContractCustomers filter; source row count is below its 5,000,001 cap:
SELECT contractNo
FROM pm_order_data_from_erp
WHERE source IN ('SAP','D365','SMS')
  AND compCode IS NOT NULL AND compCode<>''
  AND contractNo IS NOT NULL AND contractNo<>''
ORDER BY id LIMIT 5000001;
-- Raw key sets compared byte-for-byte in Python; no company mapping or transformed contract keys.
-- selectContractShipmentFields reads fb_contract as enrichment, and selectContractReceivableFields reads sms_ofst_contract_head_sap as enrichment; neither is a new contract generator.
-- DML/DDL not executed.
