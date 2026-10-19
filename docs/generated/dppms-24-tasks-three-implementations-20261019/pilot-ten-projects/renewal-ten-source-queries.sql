-- 另一批续保/多合同十项目的选定定义来源SQL
-- 历史定义摘录；2026-10-19未执行SQL、预览、导入或任何后处理。
-- 参数仅描述，不包含LOOKUP原值、前值明细、凭据或许可原文；参数SQL不能整体直接执行。

-- H001/pm_project_header task=2108608417343934465 version=6 adapter=DPPMS_PROJECT_MASTER
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-master-state-20261013.json
-- parameter-summary={"count": 3, "namePatterns": {"projectIds": 1, "projectTypes": 1, "excludedFndActorIds": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT p.*, c.compCode AS migrationCompanyCode, fc.actorId AS creatorFndSourceKey, tc.actorId AS creatorTSourceKey, fu.actorId AS updaterFndSourceKey, tu.actorId AS updaterTSourceKey, COALESCE(fc.matchCount,0) AS creatorFndMatchCount, COALESCE(tc.matchCount,0) AS creatorTMatchCount, COALESCE(fu.matchCount,0) AS updaterFndMatchCount, COALESCE(tu.matchCount,0) AS updaterTMatchCount,fc.actorIds AS creatorFndSourceKeys,fu.actorIds AS updaterFndSourceKeys
FROM pm_project p
JOIN (SELECT MAX(projectId) AS projectId FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical ON canonical.projectId=p.projectId
LEFT JOIN t_company c ON c.id=p.compId
LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey,COUNT(*) AS matchCount,CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId,GROUP_CONCAT(u.id ORDER BY u.id) AS actorIds FROM fnd_user_info u WHERE NOT FIND_IN_SET(u.id,:excludedFndActorIds) GROUP BY CAST(u.username AS BINARY)) fc ON fc.loginKey=CAST(p.createBy AS BINARY)
LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey,COUNT(*) AS matchCount,CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tc ON tc.loginKey=CAST(p.createBy AS BINARY)
LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey,COUNT(*) AS matchCount,CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId,GROUP_CONCAT(u.id ORDER BY u.id) AS actorIds FROM fnd_user_info u WHERE NOT FIND_IN_SET(u.id,:excludedFndActorIds) GROUP BY CAST(u.username AS BINARY)) fu ON fu.loginKey=CAST(p.updateBy AS BINARY)
LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey,COUNT(*) AS matchCount,CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tu ON tu.loginKey=CAST(p.updateBy AS BINARY)
WHERE FIND_IN_SET(p.projectType,:projectTypes) AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds));

-- H002/pm_project_identity task=2109622534918856706 version=2 adapter=DPPMS_PROJECT_IDENTITY
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-identity-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT original.projectId AS originalProjectId,original.projectType,original.projectCode,
  canonical.projectId AS canonicalProjectId
FROM pm_project original
JOIN (SELECT CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY)) AS projectIdentity,
  MAX(projectId) AS projectId FROM pm_project
  GROUP BY CAST(projectType AS BINARY),CAST(projectCode AS BINARY)) canonical
  ON canonical.projectIdentity <=> CONCAT(CAST(original.projectType AS BINARY),0x00,CAST(original.projectCode AS BINARY))
WHERE FIND_IN_SET(original.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds));

-- H003/CONTRACT task=2107037031219654657 version=9 adapter=TABLE_MAPPING
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-contractmasters-state-20261013.json
-- parameter-summary={"count": 5, "namePatterns": {"projectIds": 1, "contractNos": 1, "projectTypes": 1, "approvedContractNos": 1, "customerCodeOverrides": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "contractNos": "31020220620A23,31120220620A37", "projectTypes": "10"}}
SELECT erp.*,'DPPMS_ERP' AS master_origin,'pm_order_data_from_erp' AS origin_table FROM (SELECT approved.* FROM (SELECT normalized.*,CASE WHEN company_code='01' THEN '001' ELSE company_code END AS normalized_company_code FROM (SELECT e.*,CASE WHEN u.company_code IS NOT NULL THEN r.`currency_name` ELSE NULL END AS `currency_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`currency_name` ELSE NULL END AS `currency_code`,CASE WHEN u.company_code IS NOT NULL THEN r.`contract_money_amount` ELSE NULL END AS `contract_amount`,CASE WHEN u.company_code IS NOT NULL THEN r.`contract_create_date` ELSE NULL END AS `contract_create_time`,CASE WHEN u.company_code IS NOT NULL THEN COALESCE(s.`project_name`,r.`project_name`) ELSE NULL END AS `project_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`projectCode` ELSE NULL END AS `project_code`,CASE WHEN u.company_code IS NOT NULL THEN COALESCE(s.`marketCode`,r.`marketCode`) ELSE NULL END AS `market_code`,CASE WHEN u.company_code IS NOT NULL THEN COALESCE(s.`marketName`,r.`maketing_department_name`) ELSE NULL END AS `market_name`,CASE WHEN u.company_code IS NOT NULL THEN COALESCE(s.`office_code`,r.`officeCode`) ELSE NULL END AS `department_code`,CASE WHEN u.company_code IS NOT NULL THEN r.`office_name` ELSE NULL END AS `department_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`industryId` ELSE NULL END AS `industry_code`,CASE WHEN u.company_code IS NOT NULL THEN r.`industry_name` ELSE NULL END AS `industry_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`usernamec` ELSE NULL END AS `marketing_representative_code`,CASE WHEN u.company_code IS NOT NULL THEN r.`marketing_representative_name` ELSE NULL END AS `marketing_representative_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`usernamec2` ELSE NULL END AS `secondary_representative_code`,CASE WHEN u.company_code IS NOT NULL THEN s.`systemName` ELSE NULL END AS `system_name` FROM (SELECT MIN(id) AS id,compCode AS company_code,contractNo AS contract_no,COALESCE(JSON_UNQUOTE(JSON_EXTRACT(:customerCodeOverrides,CONCAT('$.',JSON_QUOTE(contractNo)))),MIN(customerCode)) AS customer_code,MIN(customerName) AS customer_name, CASE WHEN (COUNT(DISTINCT NULLIF(customerCode,''))>1 AND JSON_UNQUOTE(JSON_EXTRACT(:customerCodeOverrides,CONCAT('$.',JSON_QUOTE(contractNo)))) IS NULL) OR COUNT(DISTINCT NULLIF(customerName,''))>1 THEN 'CONTRACT_CUSTOMER_CONFLICT' ELSE NULL END AS migration_issue FROM pm_order_data_from_erp WHERE contractNo IS NOT NULL AND contractNo<>'' AND compCode IS NOT NULL AND compCode<>'' GROUP BY compCode,contractNo) e LEFT JOIN (SELECT contractNo,MIN(compCode) AS company_code FROM pm_order_data_from_erp WHERE compCode IS NOT NULL AND compCode<>'' GROUP BY contractNo HAVING COUNT(DISTINCT compCode)=1) u ON u.contractNo=e.contract_no LEFT JOIN (SELECT contract_num,SUBSTRING(MAX(CASE WHEN `currency_name` IS NOT NULL AND CAST(`currency_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`currency_name` AS CHAR)) END),40) AS `currency_name`,SUBSTRING(MAX(CASE WHEN `contract_money_amount` IS NOT NULL AND CAST(`contract_money_amount` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`contract_money_amount` AS CHAR)) END),40) AS `contract_money_amount`,SUBSTRING(MAX(CASE WHEN `contract_create_date` IS NOT NULL AND CAST(`contract_create_date` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`contract_create_date` AS CHAR)) END),40) AS `contract_create_date`,SUBSTRING(MAX(CASE WHEN `project_name` IS NOT NULL AND CAST(`project_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`project_name` AS CHAR)) END),40) AS `project_name`,SUBSTRING(MAX(CASE WHEN `projectCode` IS NOT NULL AND CAST(`projectCode` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`projectCode` AS CHAR)) END),40) AS `projectCode`,SUBSTRING(MAX(CASE WHEN `marketCode` IS NOT NULL AND CAST(`marketCode` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`marketCode` AS CHAR)) END),40) AS `marketCode`,SUBSTRING(MAX(CASE WHEN `maketing_department_name` IS NOT NULL AND CAST(`maketing_department_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`maketing_department_name` AS CHAR)) END),40) AS `maketing_department_name`,SUBSTRING(MAX(CASE WHEN `officeCode` IS NOT NULL AND CAST(`officeCode` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`officeCode` AS CHAR)) END),40) AS `officeCode`,SUBSTRING(MAX(CASE WHEN `office_name` IS NOT NULL AND CAST(`office_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`office_name` AS CHAR)) END),40) AS `office_name`,SUBSTRING(MAX(CASE WHEN `industryId` IS NOT NULL AND CAST(`industryId` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`industryId` AS CHAR)) END),40) AS `industryId`,SUBSTRING(MAX(CASE WHEN `industry_name` IS NOT NULL AND CAST(`industry_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`industry_name` AS CHAR)) END),40) AS `industry_name`,SUBSTRING(MAX(CASE WHEN `usernamec` IS NOT NULL AND CAST(`usernamec` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`usernamec` AS CHAR)) END),40) AS `usernamec`,SUBSTRING(MAX(CASE WHEN `marketing_representative_name` IS NOT NULL AND CAST(`marketing_representative_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`marketing_representative_name` AS CHAR)) END),40) AS `marketing_representative_name`,SUBSTRING(MAX(CASE WHEN `usernamec2` IS NOT NULL AND CAST(`usernamec2` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`usernamec2` AS CHAR)) END),40) AS `usernamec2` FROM sms_ofst_contract_head_sap GROUP BY contract_num) r ON r.contract_num=e.contract_no LEFT JOIN (SELECT contract_code,SUBSTRING(MAX(CASE WHEN `project_name` IS NOT NULL AND CAST(`project_name` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`project_name` AS CHAR)) END),65) AS `project_name`,SUBSTRING(MAX(CASE WHEN `marketCode` IS NOT NULL AND CAST(`marketCode` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`marketCode` AS CHAR)) END),65) AS `marketCode`,SUBSTRING(MAX(CASE WHEN `marketName` IS NOT NULL AND CAST(`marketName` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`marketName` AS CHAR)) END),65) AS `marketName`,SUBSTRING(MAX(CASE WHEN `office_code` IS NOT NULL AND CAST(`office_code` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`office_code` AS CHAR)) END),65) AS `office_code`,SUBSTRING(MAX(CASE WHEN `systemName` IS NOT NULL AND CAST(`systemName` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`systemName` AS CHAR)) END),65) AS `systemName` FROM fb_contract GROUP BY contract_code) s ON s.contract_code=e.contract_no) normalized) approved WHERE (:contractNos='' OR FIND_IN_SET(approved.contract_no,:contractNos))) erp UNION ALL SELECT CONCAT('PROJECT_CONTRACT|',q.company_code,'|',q.contractNo) AS `id`,q.company_code AS `company_code`,q.contractNo AS `contract_no`,NULL AS `customer_code`,NULL AS `customer_name`,NULL AS `migration_issue`,NULL AS `currency_name`,NULL AS `currency_code`,NULL AS `contract_amount`,NULL AS `contract_create_time`,NULL AS `project_name`,NULL AS `project_code`,NULL AS `market_code`,NULL AS `market_name`,NULL AS `department_code`,NULL AS `department_name`,NULL AS `industry_code`,NULL AS `industry_name`,NULL AS `marketing_representative_code`,NULL AS `marketing_representative_name`,NULL AS `secondary_representative_code`,NULL AS `system_name`,NULL AS `normalized_company_code`,'DPPMS' AS master_origin,'pm_project_contract' AS origin_table FROM (SELECT DISTINCT f.code AS company_code,c.contractNo
FROM pm_project_contract c
JOIN pm_project_group g ON g.projectGroupCode=c.projectGroupCode AND CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
JOIN pm_project_group_relationship m ON m.projectGroupCode=c.projectGroupCode AND CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
JOIN (SELECT CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY)) AS identity_key,MAX(projectId) AS projectId FROM pm_project GROUP BY CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY))) canonical
 ON canonical.identity_key=CONCAT(CAST(g.projectType AS BINARY),0x00,CAST(REPLACE(m.projectCode,'-his','') AS BINARY))
JOIN pm_project p ON p.projectId=canonical.projectId
JOIN fnd_company f ON f.id=p.compId
WHERE FIND_IN_SET(g.projectType,:projectTypes)
 AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
 AND FIND_IN_SET(c.contractNo,:approvedContractNos)) q;

-- H003/SHIPMENT_CONTRACT task=2107037031219654657 version=9 adapter=TABLE_MAPPING
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-contractmasters-state-20261013.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT inactive.* FROM (SELECT normalized.*,CASE WHEN company_code='01' THEN '001' ELSE company_code END AS normalized_company_code FROM (SELECT f.*, e.company_code,
       CASE WHEN f.contract_id IS NULL OR TRIM(f.contract_id) = ''
            THEN CONCAT('invalid-source-key:', SHA2(CAST(JSON_OBJECT(
                 'contract_id', f.contract_id, 'contract_code', f.contract_code,
                 'office_code', f.office_code, 'contract_type', f.contract_type,
                 'customer_name', f.customer_name, 'project_name', f.project_name,
                 'warranty', f.warranty, 'marketCode', f.marketCode,
                 'marketName', f.marketName, 'systemId', f.systemId,
                 'systemName', f.systemName, 'remark', f.remark) AS CHAR), 256))
            ELSE f.contract_id END AS sync_evidence_key,
       CASE WHEN f.contract_id IS NULL OR TRIM(f.contract_id) = ''
            THEN 'SOURCE_KEY_MISSING: fb_contract.contract_id'
            ELSE NULL END AS sync_source_issue
FROM fb_contract f
LEFT JOIN (
    SELECT contractNo, MIN(compCode) AS company_code
    FROM pm_order_data_from_erp
    WHERE compCode IS NOT NULL AND compCode <> ''
    GROUP BY contractNo
    HAVING COUNT(DISTINCT compCode) = 1
) e ON e.contractNo = f.contract_code
) normalized) inactive WHERE 1=0;

-- H003/PACKAGE task=2107037031219654657 version=9 adapter=TABLE_MAPPING
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-contractmasters-state-20261013.json
-- parameter-summary={"count": 1, "namePatterns": {"contractNos": 1}, "scopeParameters": {"contractNos": ""}}
SELECT inactive.* FROM (SELECT p.*,COALESCE(d.basicDataName,p.emsCompany) AS carrier_name FROM fb_shipment p LEFT JOIN fnd_basic_data d ON d.dataTypeCode='01' AND d.basicDataId=p.emsCompany WHERE (:contractNos='' OR EXISTS (SELECT 1 FROM fb_contract c WHERE c.contract_id=p.con_id AND FIND_IN_SET(c.contract_code,:contractNos)))) inactive WHERE 1=0;

-- H003/DEVICE_SN task=2107037031219654657 version=9 adapter=TABLE_MAPPING
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-contractmasters-state-20261013.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT inactive.* FROM (SELECT b.*,p.packdate AS shipment_time,f.contract_code AS contract_no, CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END AS event_type, CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END AS rma_marked FROM fb_shipment_barcode b LEFT JOIN fb_shipment p ON p.packlist_id=b.pack_id LEFT JOIN fb_contract f ON f.contract_id=p.con_id) inactive WHERE 1=0;

-- H003/DEVICE_RELATION task=2107037031219654657 version=9 adapter=TABLE_MAPPING
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-contractmasters-state-20261013.json
-- parameter-summary={"count": 0, "namePatterns": {}, "scopeParameters": {}}
SELECT inactive.* FROM (SELECT * FROM fb_shipment_barcode_relation WHERE sn1 <> sn2) inactive WHERE 1=0;

-- H004/ORDER task=2101587859478949890 version=28 adapter=DPPMS_ERP_ORDER
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-orders-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"reviewedSourceIds": 1, "unreviewedSourceIds": 1}, "scopeParameters": {}}
SELECT inactive.* FROM (-- Existing order identities only. The task supplies reviewed fingerprints and the exact approved source scope.
-- Every selected original keeps its full sixteen source columns. Conflicting originals stay in separate evidence.
SELECT r.*, g.migrationVersion,
       CASE WHEN g.payloadVariants>1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT' ELSE NULL END AS migrationIssue,
       SHA2(CAST(JSON_ARRAY(r.id,r.orderNumber,r.contractNo,r.orderExecNumber,r.orderCreateTime,
           r.customerRequireTime,r.customerCode,r.customerName,r.projectName,r.orderComment,r.orderType,
           r.compCode,r.salesType,r.source,r.customInfo,r.syncTime) AS CHAR),256) AS originalSourceFingerprint,
       JSON_OBJECT('id',r.id,'orderNumber',r.orderNumber,'contractNo',r.contractNo,'orderExecNumber',r.orderExecNumber,
           'orderCreateTime',r.orderCreateTime,'customerRequireTime',r.customerRequireTime,'customerCode',r.customerCode,
           'customerName',r.customerName,'projectName',r.projectName,'orderComment',r.orderComment,'orderType',r.orderType,
           'compCode',r.compCode,'salesType',r.salesType,'source',r.source,'customInfo',r.customInfo,'syncTime',r.syncTime) AS originalSourceFields
FROM pm_order_data_from_erp r
JOIN (SELECT source,compCode,orderType,orderNumber,MAX(syncTime) AS migrationVersion,
             COUNT(DISTINCT JSON_ARRAY(orderCreateTime,customerRequireTime,customerCode,customerName,projectName,
                 orderComment,salesType,customInfo,contractNo,orderExecNumber)) AS payloadVariants
      FROM pm_order_data_from_erp GROUP BY source,compCode,orderType,orderNumber) g
  ON r.source <=> g.source AND r.compCode <=> g.compCode AND r.orderType <=> g.orderType AND r.orderNumber <=> g.orderNumber
WHERE (:reviewedSourceIds='' OR FIND_IN_SET(r.id,:reviewedSourceIds))
  AND (:unreviewedSourceIds='' OR NOT FIND_IN_SET(r.id,:unreviewedSourceIds))
) inactive WHERE 1=0;

-- H004/LINE task=2101587859478949890 version=28 adapter=DPPMS_ERP_ORDER
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-orders-state-20261013.json
-- parameter-summary={"count": 1, "namePatterns": {"sourceIds": 1}, "scopeParameters": {}}
SELECT r.*,g.migrationVersion,CASE WHEN g.payloadVariants > 1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT' ELSE NULL END AS migrationIssue ,EXISTS (SELECT 1 FROM pm_order_data_from_erp h WHERE h.source=r.source AND h.compCode=r.compCode AND h.orderType=r.lineType AND h.orderNumber=r.orderNumber GROUP BY h.source,h.compCode,h.orderType,h.orderNumber HAVING MAX(h.syncTime) IS NOT NULL AND COUNT(DISTINCT JSON_ARRAY(h.orderCreateTime,h.customerRequireTime,h.customerCode,h.customerName,h.projectName,h.orderComment,h.salesType,h.customInfo,h.contractNo,h.orderExecNumber))=1) AS migrationParentExists FROM pm_order_line_from_erp r JOIN (SELECT source,compCode,lineType,orderNumber,lineNum,MAX(syncTime) AS migrationVersion,COUNT(DISTINCT JSON_ARRAY(itemCode,itemDesc,orderQuantity,openQuantity,bundleCode,warrantyMonth,profitCenter,realOrderExecNumber,customInfo)) AS payloadVariants FROM pm_order_line_from_erp GROUP BY source,compCode,lineType,orderNumber,lineNum) g ON r.source <=> g.source AND r.compCode <=> g.compCode AND r.lineType <=> g.lineType AND r.orderNumber <=> g.orderNumber AND r.lineNum <=> g.lineNum WHERE (:sourceIds='' OR FIND_IN_SET(r.id,:sourceIds));

-- H005/pm_project_contract task=2109363204544983041 version=6 adapter=DPPMS_PROJECT_CONTRACT_RELATION
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-contract-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT c.id AS id,
  c.contractNo AS contractNo,
  c.projectGroupCode AS projectGroupCode,
  c.createTime AS createTime,
  c.createBy AS createBy,
  c.updateTime AS updateTime,
  c.updateBy AS updateBy,
  m.id AS member_id,
  m.projectGroupCode AS member_projectGroupCode,
  m.projectCode AS member_projectCode,
  m.mergeBranchMark AS member_mergeBranchMark,
  m.smsProjectCode AS member_smsProjectCode,
  m.createTime AS member_createTime,
  m.createBy AS member_createBy,
  m.updateTime AS member_updateTime,
  m.updateBy AS member_updateBy,
  g.id AS group_id,
  g.projectGroupCode AS group_projectGroupCode,
  g.projectGroupName AS group_projectGroupName,
  g.projectType AS group_projectType,
  g.createTime AS group_createTime,
  g.createBy AS group_createBy,
  g.updateTime AS group_updateTime,
  g.updateBy AS group_updateBy,
  p.projectId AS originalProjectId,
  p.projectCode AS originalProjectCode,
  IF(p.projectId IS NULL,0,1) AS projectCandidateCount,
  CONCAT(c.id,':',COALESCE(m.id,0)) AS relationOriginalKey,
  a.formalGroupSize,
  IF(SUBSTRING(a.createdPick,1,1)='0','ACTIVE','HISTORICAL') AS formalStatus,
  COALESCE(kc.createTime,km.createTime) AS formalCreatedAt,
  COALESCE(NULLIF(kc.createBy,''),NULLIF(km.createBy,'')) AS formalCreator,
  COALESCE(uc.updateTime,um.updateTime) AS formalUpdatedAt,
  COALESCE(NULLIF(uc.updateBy,''),NULLIF(um.updateBy,'')) AS formalUpdater,
  IF(a.formalGroupSize=1,0,(SELECT COUNT(*) FROM pm_project_contract d
  LEFT JOIN pm_project_group dg ON dg.projectGroupCode=d.projectGroupCode AND CAST(dg.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship dm ON dm.projectGroupCode=d.projectGroupCode AND CAST(dm.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  WHERE d.contractNo <=> c.contractNo AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(dg.projectType AS BINARY) <=> CAST(g.projectType AS BINARY)
    AND CAST(REPLACE(dm.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
    AND IF(RIGHT(CAST(dm.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0)=SUBSTRING(a.createdPick,1,1)
    AND COALESCE(d.createTime,dm.createTime) <=> COALESCE(kc.createTime,km.createTime)
    AND NOT (CAST(COALESCE(NULLIF(d.createBy,''),NULLIF(dm.createBy,'')) AS BINARY) <=> CAST(COALESCE(NULLIF(kc.createBy,''),NULLIF(km.createBy,'')) AS BINARY)))) AS formalCreateConflicts,
  IF(a.formalGroupSize=1,0,(SELECT COUNT(*) FROM pm_project_contract d
  LEFT JOIN pm_project_group dg ON dg.projectGroupCode=d.projectGroupCode AND CAST(dg.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship dm ON dm.projectGroupCode=d.projectGroupCode AND CAST(dm.projectGroupCode AS BINARY)=CAST(d.projectGroupCode AS BINARY)
  WHERE d.contractNo <=> c.contractNo AND CAST(d.contractNo AS BINARY) <=> CAST(c.contractNo AS BINARY)
    AND CAST(dg.projectType AS BINARY) <=> CAST(g.projectType AS BINARY)
    AND CAST(REPLACE(dm.projectCode,'-his','') AS BINARY) <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
    AND IF(RIGHT(CAST(dm.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0)=SUBSTRING(a.createdPick,1,1)
    AND COALESCE(d.updateTime,dm.updateTime) <=> COALESCE(uc.updateTime,um.updateTime)
    AND NOT (CAST(COALESCE(NULLIF(d.updateBy,''),NULLIF(dm.updateBy,'')) AS BINARY) <=> CAST(COALESCE(NULLIF(uc.updateBy,''),NULLIF(um.updateBy,'')) AS BINARY)))) AS formalUpdateConflicts,
  kc.id AS canonical_id,
  kc.contractNo AS canonical_contractNo,
  kc.projectGroupCode AS canonical_projectGroupCode,
  kc.createTime AS canonical_createTime,
  kc.createBy AS canonical_createBy,
  kc.updateTime AS canonical_updateTime,
  kc.updateBy AS canonical_updateBy,
  km.id AS canonical_member_id,
  km.projectGroupCode AS canonical_member_projectGroupCode,
  km.projectCode AS canonical_member_projectCode,
  km.mergeBranchMark AS canonical_member_mergeBranchMark,
  km.smsProjectCode AS canonical_member_smsProjectCode,
  km.createTime AS canonical_member_createTime,
  km.createBy AS canonical_member_createBy,
  km.updateTime AS canonical_member_updateTime,
  km.updateBy AS canonical_member_updateBy,
  kg.id AS canonical_group_id,
  kg.projectGroupCode AS canonical_group_projectGroupCode,
  kg.projectGroupName AS canonical_group_projectGroupName,
  kg.projectType AS canonical_group_projectType,
  kg.createTime AS canonical_group_createTime,
  kg.createBy AS canonical_group_createBy,
  kg.updateTime AS canonical_group_updateTime,
  kg.updateBy AS canonical_group_updateBy,
  p.projectId AS canonical_originalProjectId,
  p.projectCode AS canonical_originalProjectCode,
  CONCAT(LPAD(kc.id,20,'0'),':',LPAD(COALESCE(km.id,0),20,'0'),':',
    IF(c.id=kc.id AND COALESCE(m.id,0)=COALESCE(km.id,0),0,1),':',
    LPAD(c.id,20,'0'),':',LPAD(COALESCE(m.id,0),20,'0')) AS relationSourceKey
FROM pm_project_contract c
LEFT JOIN pm_project_group g ON g.projectGroupCode=c.projectGroupCode AND CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship m ON m.projectGroupCode=c.projectGroupCode AND CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
LEFT JOIN (SELECT CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY)) AS projectIdentity,
  MAX(projectId) AS projectId FROM pm_project
  GROUP BY CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY))) canonical
  ON canonical.projectIdentity=CONCAT(CAST(g.projectType AS BINARY),0x00,CAST(REPLACE(m.projectCode,'-his','') AS BINARY))
LEFT JOIN pm_project p ON p.projectId=canonical.projectId
JOIN (
  -- Aggregate the full formal identity before applying task scope or streaming chunks.
  -- Picks encode status priority (1), timestamp (20), contract ID (20), member ID (20).
  SELECT CAST(g.projectType AS BINARY) AS projectType,CAST(REPLACE(m.projectCode,'-his','') AS BINARY) AS projectCode,
    CAST(c.contractNo AS BINARY) AS contractNo,COUNT(*) AS formalGroupSize,
    MIN(CONCAT(IF(RIGHT(CAST(m.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0),
      COALESCE(DATE_FORMAT(COALESCE(c.createTime,m.createTime),'%Y%m%d%H%i%s%f'),'00000000000000000000'),
      LPAD(c.id,20,'0'),LPAD(COALESCE(m.id,0),20,'0'))) AS createdPick,
    MAX(CONCAT(1-IF(RIGHT(CAST(m.projectCode AS BINARY),4)=CAST('-his' AS BINARY),1,0),
      COALESCE(DATE_FORMAT(COALESCE(c.updateTime,m.updateTime),'%Y%m%d%H%i%s%f'),'00000000000000000000'),
      LPAD(c.id,20,'0'),LPAD(COALESCE(m.id,0),20,'0'))) AS updatedPick
  FROM pm_project_contract c
  LEFT JOIN pm_project_group g ON g.projectGroupCode=c.projectGroupCode AND CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
  LEFT JOIN pm_project_group_relationship m ON m.projectGroupCode=c.projectGroupCode AND CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
  GROUP BY CAST(g.projectType AS BINARY),CAST(REPLACE(m.projectCode,'-his','') AS BINARY),CAST(c.contractNo AS BINARY)
) a ON a.projectType <=> CAST(g.projectType AS BINARY)
  AND a.projectCode <=> CAST(REPLACE(m.projectCode,'-his','') AS BINARY) AND a.contractNo <=> CAST(c.contractNo AS BINARY)
LEFT JOIN pm_project_contract kc ON kc.id=CAST(SUBSTRING(a.createdPick,22,20) AS UNSIGNED)
LEFT JOIN pm_project_group_relationship km ON km.id=CAST(SUBSTRING(a.createdPick,42,20) AS UNSIGNED)
LEFT JOIN pm_project_group kg ON kg.projectGroupCode=kc.projectGroupCode AND CAST(kg.projectGroupCode AS BINARY)=CAST(kc.projectGroupCode AS BINARY)
LEFT JOIN pm_project_contract uc ON uc.id=CAST(SUBSTRING(a.updatedPick,22,20) AS UNSIGNED)
LEFT JOIN pm_project_group_relationship um ON um.id=CAST(SUBSTRING(a.updatedPick,42,20) AS UNSIGNED)
WHERE (g.id IS NULL OR FIND_IN_SET(g.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds));

-- H006/pm_project_product_line task=2109829769958453249 version=2 adapter=DPPMS_PROJECT_PRODUCT_SCOPE
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-scope-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT l.*,p.projectCode AS originalProjectCode,p.projectType
FROM pm_project_product_line l
LEFT JOIN pm_project p ON p.projectId=l.projectId
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(l.projectId,:projectIds));

-- H007/pm_project_related_party task=2109968219214897153 version=2 adapter=DPPMS_PROJECT_FACTS
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-facts-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT r.*, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_related_party r
JOIN pm_project p ON p.projectId=r.projectId
WHERE FIND_IN_SET(p.projectType,:projectTypes)
  AND (:projectIds='' OR FIND_IN_SET(r.projectId,:projectIds));

-- H007/pm_project_state task=2109968219214897153 version=2 adapter=DPPMS_PROJECT_FACTS
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-facts-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT s.*, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_state s
JOIN pm_project p ON p.projectId=s.projectId
WHERE FIND_IN_SET(p.projectType,:projectTypes)
  AND p.projectId=(SELECT MAX(c.projectId) FROM pm_project c
                  WHERE c.projectType=p.projectType AND c.projectCode=p.projectCode)
  AND (:projectIds='' OR FIND_IN_SET(s.projectId,:projectIds));

-- H008/sms_ofst_contract_head_sap task=2109967339937804289 version=1 adapter=DPPMS_CONTRACT_RECEIVABLE
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-receivable-state-20261013.json
-- parameter-summary={"count": 1, "namePatterns": {"contractNos": 1}, "scopeParameters": {"contractNos": "16030018120220N,16200218122515N-L,16201819112615N,16500021122915N-L,31020151103C,31020160912G,31020170818E,31020171201Z,31020181224A12,31020211228A12,31020220620A23,31120151225A,31120190129A01,31120190909A96,31120190910A76,31120200327A05,31120201116A80,31120220324A89,31120220620A37,61020190129A04,61020200327A14"}}
SELECT r.*
FROM sms_ofst_contract_head_sap r
WHERE r.contract_num IS NOT NULL
  AND (:contractNos='' OR FIND_IN_SET(r.contract_num,:contractNos));

-- H009/fb_service task=2110010787726274561 version=2 adapter=DPPMS_WARRANTY_RENEWAL
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-renewal-state-20261013.json
-- parameter-summary={"count": 3, "namePatterns": {"deviceSns": 1, "projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT r.id,r.con_xb,r.barcode,r.grade,r.begin_date,r.end_date,r.warranty,r.remark,r.isyb,r.state,
       CONCAT('fb_service:',r.id,':',COALESCE(r.barcode,'')) AS renewalSourceKey
FROM fb_service r
WHERE :projectTypes='' AND :projectIds=''
  AND (:deviceSns='' OR FIND_IN_SET(r.barcode,:deviceSns))
UNION ALL
SELECT r.id,r.con_xb,r.barcode,r.grade,r.begin_date,r.end_date,r.warranty,r.remark,r.isyb,r.state,
       CONCAT('fb_service:',r.id,':',COALESCE(r.barcode,'')) AS renewalSourceKey
FROM (
    SELECT DISTINCT sb.barcode
    FROM pm_project_group_relationship m
    JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
    JOIN pm_project p ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
    JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
    JOIN fb_contract c ON c.contract_code=pc.contractNo
    JOIN fb_shipment s ON s.con_id=c.contract_id
    JOIN fb_shipment_barcode sb ON sb.pack_id=s.packlist_id
    WHERE (:projectTypes<>'' OR :projectIds<>'')
      AND (:projectTypes='' OR FIND_IN_SET(p.projectType,:projectTypes))
      AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
      AND p.projectId=(SELECT MAX(newer.projectId) FROM pm_project newer
          WHERE newer.projectCode=p.projectCode AND newer.projectType=p.projectType
            AND CAST(newer.projectCode AS BINARY)=CAST(p.projectCode AS BINARY))
      AND c.contract_type IN (0,11) AND sb.barcode IS NOT NULL AND sb.barcode<>''
      AND (sb.rma_no IS NULL OR sb.rma_no='' OR sb.rma_no='null')
) selected STRAIGHT_JOIN fb_service r ON r.barcode=selected.barcode
WHERE :deviceSns='' OR FIND_IN_SET(r.barcode,:deviceSns);

-- H010/pm_project_product_line_real task=2109865269478027265 version=1 adapter=DPPMS_PROJECT_REAL_PRODUCT
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-real-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT l.*, p.projectCode AS originalProjectCode, p.projectType,
       u.projectCode AS upstream_projectCode, u.orderExecNumber AS upstream_orderExecNumber,
       u.productFirstName AS upstream_productFirstName, u.productName AS upstream_productName,
       u.productSubCode AS upstream_productSubCode, u.productSubModel AS upstream_productSubModel,
       u.productSubName AS upstream_productSubName, u.num AS upstream_num,
       u.memo AS upstream_memo, u.dataSource AS upstream_dataSource,
       (SELECT COUNT(*) FROM pm_project_real_product_line_from_sms m
        WHERE CAST(m.projectCode AS BINARY) <=> CAST(l.projectCode AS BINARY)
          AND CAST(m.orderExecNumber AS BINARY) <=> CAST(l.orderExecNumber AS BINARY)
          AND CAST(m.productFirstName AS BINARY) <=> CAST(l.productFirstName AS BINARY)
          AND CAST(m.productName AS BINARY) <=> CAST(l.productName AS BINARY)
          AND CAST(m.productSubCode AS BINARY) <=> CAST(l.productSubCode AS BINARY)
          AND CAST(m.productSubModel AS BINARY) <=> CAST(l.productSubModel AS BINARY)
          AND CAST(m.productSubName AS BINARY) <=> CAST(l.productSubName AS BINARY)
          AND m.num <=> l.num AND CAST(m.memo AS BINARY) <=> CAST(l.memo AS BINARY)) AS upstreamMatchCount
FROM pm_project_product_line_real l
LEFT JOIN pm_project p ON p.projectId=l.projectId
LEFT JOIN pm_project_real_product_line_from_sms u
  ON CAST(u.projectCode AS BINARY) <=> CAST(l.projectCode AS BINARY)
 AND CAST(u.orderExecNumber AS BINARY) <=> CAST(l.orderExecNumber AS BINARY)
 AND CAST(u.productFirstName AS BINARY) <=> CAST(l.productFirstName AS BINARY)
 AND CAST(u.productName AS BINARY) <=> CAST(l.productName AS BINARY)
 AND CAST(u.productSubCode AS BINARY) <=> CAST(l.productSubCode AS BINARY)
 AND CAST(u.productSubModel AS BINARY) <=> CAST(l.productSubModel AS BINARY)
 AND CAST(u.productSubName AS BINARY) <=> CAST(l.productSubName AS BINARY)
 AND u.num <=> l.num AND CAST(u.memo AS BINARY) <=> CAST(l.memo AS BINARY)
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(l.projectId,:projectIds));

-- H011/pm_project_product_lease_line_from_crm task=2109865270430134273 version=1 adapter=DPPMS_PROJECT_ASSOCIATED_PRODUCTS
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-associated-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT l.*, p.projectId AS sourceProjectId, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_product_lease_line_from_crm l
LEFT JOIN (
    SELECT p.projectId, p.projectCode, p.projectType,
           CAST(SUBSTRING_INDEX(p.projectCode,'-',1) AS BINARY) AS queryProjectCode
    FROM pm_project p
    INNER JOIN (
        SELECT projectType, projectCode, MAX(projectId) AS projectId
        FROM pm_project GROUP BY projectType, projectCode
    ) selected ON selected.projectId=p.projectId
    GROUP BY p.projectId, p.projectCode, p.projectType
) p ON p.queryProjectCode=CAST(l.projectCode AS BINARY)
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds));

-- H011/pm_project_product_config_level_info_from_crm task=2109865270430134273 version=1 adapter=DPPMS_PROJECT_ASSOCIATED_PRODUCTS
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-associated-state-20261013.json
-- parameter-summary={"count": 2, "namePatterns": {"projectIds": 1, "projectTypes": 1}, "scopeParameters": {"projectIds": "31339,44338,26040,46781,9957,25501,32945,20072,13383,29284", "projectTypes": "10"}}
SELECT l.*, p.projectId AS sourceProjectId, p.projectCode AS originalProjectCode, p.projectType
FROM pm_project_product_config_level_info_from_crm l
LEFT JOIN (
    SELECT p.projectId, p.projectCode, p.projectType,
           CAST(SUBSTRING_INDEX(p.projectCode,'-',1) AS BINARY) AS queryProjectCode
    FROM pm_project p
    INNER JOIN (
        SELECT projectType, projectCode, MAX(projectId) AS projectId
        FROM pm_project GROUP BY projectType, projectCode
    ) selected ON selected.projectId=p.projectId
    GROUP BY p.projectId, p.projectCode, p.projectType
) p ON p.queryProjectCode=CAST(l.projectCode AS BINARY)
WHERE (p.projectId IS NULL OR FIND_IN_SET(p.projectType,:projectTypes))
  AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds));

-- H012/pm_pb_plan_from_sms task=2108574253093683202 version=7 adapter=DPPMS_CONTRACT_COLLECTION_PLAN
-- source=C:/Users/user/Documents/Codex/2026-10-08/c-users-user-documents-codex-2026/work/native-followup-private/renewal-ten-collection-state-20261013.json
-- parameter-summary={"count": 4, "namePatterns": {"planIds": 1, "approvedServiceActor<n>": 2, "approvedUnlinkedPlanIds": 1}, "scopeParameters": {"planIds": "0"}}
SELECT p.*,fc.actorId AS creatorFndSourceKey,tc.actorId AS creatorTSourceKey,
  fu.actorId AS updaterFndSourceKey,tu.actorId AS updaterTSourceKey,
  COALESCE(fc.matchCount,0) AS creatorFndMatchCount,COALESCE(tc.matchCount,0) AS creatorTMatchCount,
  COALESCE(fu.matchCount,0) AS updaterFndMatchCount,COALESCE(tu.matchCount,0) AS updaterTMatchCount,
  fc.actorIds AS creatorFndSourceKeys,fu.actorIds AS updaterFndSourceKeys
FROM pm_pb_plan_from_sms p
LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey,COUNT(*) AS matchCount,
  CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId,GROUP_CONCAT(u.id ORDER BY u.id) AS actorIds
  FROM fnd_user_info u GROUP BY CAST(u.username AS BINARY)) fc ON fc.loginKey=CAST(p.createBy AS BINARY)
LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey,COUNT(*) AS matchCount,
  CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId
  FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tc ON tc.loginKey=CAST(p.createBy AS BINARY)
LEFT JOIN (SELECT CAST(u.username AS BINARY) AS loginKey,COUNT(*) AS matchCount,
  CASE WHEN COUNT(*)=1 THEN MAX(u.id) ELSE NULL END AS actorId,GROUP_CONCAT(u.id ORDER BY u.id) AS actorIds
  FROM fnd_user_info u GROUP BY CAST(u.username AS BINARY)) fu ON fu.loginKey=CAST(p.updateBy AS BINARY)
LEFT JOIN (SELECT CAST(u.user_name AS BINARY) AS loginKey,COUNT(*) AS matchCount,
  CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END AS actorId
  FROM t_user u GROUP BY CAST(u.user_name AS BINARY)) tu ON tu.loginKey=CAST(p.updateBy AS BINARY)
WHERE (:planIds='' OR FIND_IN_SET(p.id,:planIds))
 AND (p.createBy IS NULL OR p.createBy='' OR COALESCE(fc.matchCount,0)>0 OR COALESCE(tc.matchCount,0)>0 OR FIND_IN_SET(p.id,:approvedUnlinkedPlanIds) OR CAST(p.createBy AS BINARY) IN (CAST(:approvedServiceActor0 AS BINARY),CAST(:approvedServiceActor1 AS BINARY)))
 AND (p.updateBy IS NULL OR p.updateBy='' OR COALESCE(fu.matchCount,0)>0 OR COALESCE(tu.matchCount,0)>0 OR FIND_IN_SET(p.id,:approvedUnlinkedPlanIds) OR CAST(p.updateBy AS BINARY) IN (CAST(:approvedServiceActor0 AS BINARY),CAST(:approvedServiceActor1 AS BINARY)));
