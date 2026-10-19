-- P15 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [CONTRACT] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P15-phase-stream/task-after.json
SELECT source_keys._groupKey,
 CASE source_keys.objectRank
 WHEN 3 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_SN','fields',JSON_OBJECT('id',b.`id`,'pack_id',b.`pack_id`,'item',b.`item`,'barcode',b.`barcode`,'com_barcode',b.`com_barcode`,'rma_no',b.`rma_no`,'isRMA',b.`isRMA`,'item2',b.`item2`,'barcode2',b.`barcode2`,'orderNumber',b.`orderNumber`,'lineNum',b.`lineNum`,'profitCenter',b.`profitCenter`,'soleAgentSuffix',b.`soleAgentSuffix`,'warrantyStartDate',b.`warrantyStartDate`,'warrantyMonth',b.`warrantyMonth`,'rmaBarcode',b.`rmaBarcode`,'updateTime',b.`updateTime`,'syncTime',b.`syncTime`,'uuid',b.`uuid`,'shipment_time',p.packdate,'contract_no',f.contract_code,'event_type',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END,'rma_marked',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END))) AS CHAR)
 WHEN 4 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_RELATION','fields',JSON_OBJECT('id',relation.`id`,'sn1',relation.`sn1`,'item1',relation.`item1`,'sn2',relation.`sn2`,'item2',relation.`item2`,'contract',relation.`contract`,'createtime',relation.`createtime`,'updatetime',relation.`updatetime`))) AS CHAR)
 ELSE source_keys._sourceRows END AS _sourceRows
FROM (
SELECT 0 AS objectRank,CAST(NULL AS UNSIGNED) AS sourceId,CONCAT('0:',SHA2(CAST(JSON_ARRAY(r.`company_code`,r.`contract_no`) AS CHAR),256)) AS _groupKey,
 CAST(JSON_ARRAYAGG(JSON_OBJECT('object','CONTRACT','fields',JSON_OBJECT('id',r.`id`,'company_code',r.`company_code`,'contract_no',r.`contract_no`,'customer_code',r.`customer_code`,'customer_name',r.`customer_name`,'migration_issue',r.`migration_issue`,'currency_name',r.`currency_name`,'currency_code',r.`currency_code`,'contract_amount',r.`contract_amount`,'contract_create_time',r.`contract_create_time`,'project_name',r.`project_name`,'project_code',r.`project_code`,'market_code',r.`market_code`,'market_name',r.`market_name`,'department_code',r.`department_code`,'department_name',r.`department_name`,'industry_code',r.`industry_code`,'industry_name',r.`industry_name`,'marketing_representative_code',r.`marketing_representative_code`,'marketing_representative_name',r.`marketing_representative_name`,'secondary_representative_code',r.`secondary_representative_code`,'system_name',r.`system_name`,'normalized_company_code',r.`normalized_company_code`,'master_origin',r.`master_origin`,'origin_table',r.`origin_table`))) AS CHAR) AS _sourceRows
FROM (
SELECT master.* FROM (SELECT erp.*,'DPPMS_ERP' AS master_origin,'pm_order_data_from_erp' AS origin_table FROM (SELECT approved.* FROM (SELECT normalized.*,CASE WHEN company_code='01' THEN '001' ELSE company_code END AS normalized_company_code FROM (SELECT e.*,CASE WHEN u.company_code IS NOT NULL THEN r.`currency_name` ELSE NULL END AS `currency_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`currency_name` ELSE NULL END AS `currency_code`,CASE WHEN u.company_code IS NOT NULL THEN r.`contract_money_amount` ELSE NULL END AS `contract_amount`,CASE WHEN u.company_code IS NOT NULL THEN r.`contract_create_date` ELSE NULL END AS `contract_create_time`,CASE WHEN u.company_code IS NOT NULL THEN COALESCE(s.`project_name`,r.`project_name`) ELSE NULL END AS `project_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`projectCode` ELSE NULL END AS `project_code`,CASE WHEN u.company_code IS NOT NULL THEN COALESCE(s.`marketCode`,r.`marketCode`) ELSE NULL END AS `market_code`,CASE WHEN u.company_code IS NOT NULL THEN COALESCE(s.`marketName`,r.`maketing_department_name`) ELSE NULL END AS `market_name`,CASE WHEN u.company_code IS NOT NULL THEN COALESCE(s.`office_code`,r.`officeCode`) ELSE NULL END AS `department_code`,CASE WHEN u.company_code IS NOT NULL THEN r.`office_name` ELSE NULL END AS `department_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`industryId` ELSE NULL END AS `industry_code`,CASE WHEN u.company_code IS NOT NULL THEN r.`industry_name` ELSE NULL END AS `industry_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`usernamec` ELSE NULL END AS `marketing_representative_code`,CASE WHEN u.company_code IS NOT NULL THEN r.`marketing_representative_name` ELSE NULL END AS `marketing_representative_name`,CASE WHEN u.company_code IS NOT NULL THEN r.`usernamec2` ELSE NULL END AS `secondary_representative_code`,CASE WHEN u.company_code IS NOT NULL THEN s.`systemName` ELSE NULL END AS `system_name` FROM (SELECT MIN(id) AS id,compCode AS company_code,contractNo AS contract_no,COALESCE(JSON_UNQUOTE(JSON_EXTRACT(:customerCodeOverrides,CONCAT('$.',JSON_QUOTE(contractNo)))),MIN(customerCode)) AS customer_code,MIN(customerName) AS customer_name, CASE WHEN (COUNT(DISTINCT NULLIF(customerCode,''))>1 AND JSON_UNQUOTE(JSON_EXTRACT(:customerCodeOverrides,CONCAT('$.',JSON_QUOTE(contractNo)))) IS NULL) OR COUNT(DISTINCT NULLIF(customerName,''))>1 THEN 'CONTRACT_CUSTOMER_CONFLICT' ELSE NULL END AS migration_issue FROM pm_order_data_from_erp WHERE contractNo IS NOT NULL AND contractNo<>'' AND compCode IS NOT NULL AND compCode<>'' GROUP BY compCode,contractNo) e LEFT JOIN (SELECT contractNo,MIN(compCode) AS company_code FROM pm_order_data_from_erp WHERE compCode IS NOT NULL AND compCode<>'' GROUP BY contractNo HAVING COUNT(DISTINCT compCode)=1) u ON u.contractNo=e.contract_no LEFT JOIN (SELECT contract_num,SUBSTRING(MAX(CASE WHEN `currency_name` IS NOT NULL AND CAST(`currency_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`currency_name` AS CHAR)) END),40) AS `currency_name`,SUBSTRING(MAX(CASE WHEN `contract_money_amount` IS NOT NULL AND CAST(`contract_money_amount` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`contract_money_amount` AS CHAR)) END),40) AS `contract_money_amount`,SUBSTRING(MAX(CASE WHEN `contract_create_date` IS NOT NULL AND CAST(`contract_create_date` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`contract_create_date` AS CHAR)) END),40) AS `contract_create_date`,SUBSTRING(MAX(CASE WHEN `project_name` IS NOT NULL AND CAST(`project_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`project_name` AS CHAR)) END),40) AS `project_name`,SUBSTRING(MAX(CASE WHEN `projectCode` IS NOT NULL AND CAST(`projectCode` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`projectCode` AS CHAR)) END),40) AS `projectCode`,SUBSTRING(MAX(CASE WHEN `marketCode` IS NOT NULL AND CAST(`marketCode` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`marketCode` AS CHAR)) END),40) AS `marketCode`,SUBSTRING(MAX(CASE WHEN `maketing_department_name` IS NOT NULL AND CAST(`maketing_department_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`maketing_department_name` AS CHAR)) END),40) AS `maketing_department_name`,SUBSTRING(MAX(CASE WHEN `officeCode` IS NOT NULL AND CAST(`officeCode` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`officeCode` AS CHAR)) END),40) AS `officeCode`,SUBSTRING(MAX(CASE WHEN `office_name` IS NOT NULL AND CAST(`office_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`office_name` AS CHAR)) END),40) AS `office_name`,SUBSTRING(MAX(CASE WHEN `industryId` IS NOT NULL AND CAST(`industryId` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`industryId` AS CHAR)) END),40) AS `industryId`,SUBSTRING(MAX(CASE WHEN `industry_name` IS NOT NULL AND CAST(`industry_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`industry_name` AS CHAR)) END),40) AS `industry_name`,SUBSTRING(MAX(CASE WHEN `usernamec` IS NOT NULL AND CAST(`usernamec` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`usernamec` AS CHAR)) END),40) AS `usernamec`,SUBSTRING(MAX(CASE WHEN `marketing_representative_name` IS NOT NULL AND CAST(`marketing_representative_name` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`marketing_representative_name` AS CHAR)) END),40) AS `marketing_representative_name`,SUBSTRING(MAX(CASE WHEN `usernamec2` IS NOT NULL AND CAST(`usernamec2` AS CHAR)<>'' THEN CONCAT(CONCAT(COALESCE(LEFT(CAST(update_time AS CHAR),19),'1000-01-01 00:00:00'),LPAD(CAST(id AS CHAR),20,'0')),CAST(`usernamec2` AS CHAR)) END),40) AS `usernamec2` FROM sms_ofst_contract_head_sap GROUP BY contract_num) r ON r.contract_num=e.contract_no LEFT JOIN (SELECT contract_code,SUBSTRING(MAX(CASE WHEN `project_name` IS NOT NULL AND CAST(`project_name` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`project_name` AS CHAR)) END),65) AS `project_name`,SUBSTRING(MAX(CASE WHEN `marketCode` IS NOT NULL AND CAST(`marketCode` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`marketCode` AS CHAR)) END),65) AS `marketCode`,SUBSTRING(MAX(CASE WHEN `marketName` IS NOT NULL AND CAST(`marketName` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`marketName` AS CHAR)) END),65) AS `marketName`,SUBSTRING(MAX(CASE WHEN `office_code` IS NOT NULL AND CAST(`office_code` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`office_code` AS CHAR)) END),65) AS `office_code`,SUBSTRING(MAX(CASE WHEN `systemName` IS NOT NULL AND CAST(`systemName` AS CHAR)<>'' THEN CONCAT(RPAD(CAST(contract_id AS CHAR),64,' '),CAST(`systemName` AS CHAR)) END),65) AS `systemName` FROM fb_contract GROUP BY contract_code) s ON s.contract_code=e.contract_no) normalized) approved WHERE (:contractNos='' OR FIND_IN_SET(approved.contract_no,:contractNos))) erp UNION ALL SELECT CONCAT('PROJECT_CONTRACT|',q.company_code,'|',q.contractNo) AS `id`,q.company_code AS `company_code`,q.contractNo AS `contract_no`,NULL AS `customer_code`,NULL AS `customer_name`,NULL AS `migration_issue`,NULL AS `currency_name`,NULL AS `currency_code`,NULL AS `contract_amount`,NULL AS `contract_create_time`,NULL AS `project_name`,NULL AS `project_code`,NULL AS `market_code`,NULL AS `market_name`,NULL AS `department_code`,NULL AS `department_name`,NULL AS `industry_code`,NULL AS `industry_name`,NULL AS `marketing_representative_code`,NULL AS `marketing_representative_name`,NULL AS `secondary_representative_code`,NULL AS `system_name`,NULL AS `normalized_company_code`,'DPPMS' AS master_origin,'pm_project_contract' AS origin_table FROM (SELECT DISTINCT f.code AS company_code,c.contractNo
FROM pm_project_contract c
JOIN pm_project_group g ON g.projectGroupCode=c.projectGroupCode AND CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
JOIN pm_project_group_relationship m ON m.projectGroupCode=c.projectGroupCode AND CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
JOIN (SELECT projectCode,projectType,CONCAT(CAST(projectType AS BINARY),0x00,CAST(projectCode AS BINARY)) AS identity_key,MAX(projectId) AS projectId FROM pm_project GROUP BY projectCode,projectType,CAST(projectCode AS BINARY),CAST(projectType AS BINARY)) canonical
 ON canonical.projectType=g.projectType AND canonical.projectCode=REPLACE(m.projectCode,'-his','') AND canonical.identity_key=CONCAT(CAST(g.projectType AS BINARY),0x00,CAST(REPLACE(m.projectCode,'-his','') AS BINARY))
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
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) p ON p.projectId=canonical.projectId
JOIN fnd_company f ON f.id=p.compId
WHERE FIND_IN_SET(g.projectType,:projectTypes)
 AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
 AND FIND_IN_SET(c.contractNo,:approvedContractNos)) q) master
) r
JOIN (SELECT DISTINCT pc.contractNo
FROM pm_project_group_relationship m
JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
  AND CAST(g.projectGroupCode AS BINARY)=CAST(m.projectGroupCode AS BINARY)
JOIN (SELECT DISTINCT scoped.projectId,scoped.projectCode,scoped.projectType
FROM (SELECT original.*,canonical.projectId AS canonicalProjectId
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
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) scoped
WHERE scoped.projectId=scoped.canonicalProjectId) p
  ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
  AND CAST(p.projectCode AS BINARY)=CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
  AND CAST(p.projectType AS BINARY)=CAST(g.projectType AS BINARY)
JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
  AND CAST(pc.projectGroupCode AS BINARY)=CAST(g.projectGroupCode AS BINARY)) scope ON scope.contractNo=r.`contract_no`
GROUP BY CAST(JSON_ARRAY(r.`company_code`,r.`contract_no`) AS CHAR),CAST(CAST(JSON_ARRAY(r.`company_code`,r.`contract_no`) AS CHAR) AS BINARY)
) source_keys
LEFT JOIN fb_shipment_barcode b ON source_keys.objectRank=3 AND b.id=source_keys.sourceId
LEFT JOIN fb_shipment p ON p.packlist_id=b.pack_id
LEFT JOIN fb_contract f ON f.contract_id=p.con_id
LEFT JOIN fb_shipment_barcode_relation relation ON source_keys.objectRank=4 AND relation.id=source_keys.sourceId

UNION ALL
SELECT source_keys._groupKey,
 CASE source_keys.objectRank
 WHEN 3 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_SN','fields',JSON_OBJECT('id',b.`id`,'pack_id',b.`pack_id`,'item',b.`item`,'barcode',b.`barcode`,'com_barcode',b.`com_barcode`,'rma_no',b.`rma_no`,'isRMA',b.`isRMA`,'item2',b.`item2`,'barcode2',b.`barcode2`,'orderNumber',b.`orderNumber`,'lineNum',b.`lineNum`,'profitCenter',b.`profitCenter`,'soleAgentSuffix',b.`soleAgentSuffix`,'warrantyStartDate',b.`warrantyStartDate`,'warrantyMonth',b.`warrantyMonth`,'rmaBarcode',b.`rmaBarcode`,'updateTime',b.`updateTime`,'syncTime',b.`syncTime`,'uuid',b.`uuid`,'shipment_time',p.packdate,'contract_no',f.contract_code,'event_type',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END,'rma_marked',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END))) AS CHAR)
 WHEN 4 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_RELATION','fields',JSON_OBJECT('id',relation.`id`,'sn1',relation.`sn1`,'item1',relation.`item1`,'sn2',relation.`sn2`,'item2',relation.`item2`,'contract',relation.`contract`,'createtime',relation.`createtime`,'updatetime',relation.`updatetime`))) AS CHAR)
 ELSE source_keys._sourceRows END AS _sourceRows
FROM (
SELECT 1 AS objectRank,CAST(NULL AS UNSIGNED) AS sourceId,CONCAT('1:',SHA2(CAST(JSON_ARRAY('DPPMS',r.`sync_evidence_key`) AS CHAR),256)) AS _groupKey,
 CAST(JSON_ARRAYAGG(JSON_OBJECT('object','SHIPMENT_CONTRACT','fields',JSON_OBJECT('contract_id',r.`contract_id`,'contract_code',r.`contract_code`,'office_code',r.`office_code`,'contract_type',r.`contract_type`,'customer_name',r.`customer_name`,'project_name',r.`project_name`,'warranty',r.`warranty`,'marketCode',r.`marketCode`,'marketName',r.`marketName`,'systemId',r.`systemId`,'systemName',r.`systemName`,'remark',r.`remark`,'company_code',r.`company_code`,'sync_evidence_key',r.`sync_evidence_key`,'sync_source_issue',r.`sync_source_issue`,'normalized_company_code',r.`normalized_company_code`))) AS CHAR) AS _sourceRows
FROM (
SELECT after_sales_scope.* FROM (SELECT normalized.*,CASE WHEN company_code='01' THEN '001' ELSE company_code END AS normalized_company_code FROM (SELECT f.*, e.company_code,
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
) normalized) after_sales_scope
) r
JOIN (SELECT DISTINCT pc.contractNo
FROM pm_project_group_relationship m
JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
  AND CAST(g.projectGroupCode AS BINARY)=CAST(m.projectGroupCode AS BINARY)
JOIN (SELECT DISTINCT scoped.projectId,scoped.projectCode,scoped.projectType
FROM (SELECT original.*,canonical.projectId AS canonicalProjectId
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
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) scoped
WHERE scoped.projectId=scoped.canonicalProjectId) p
  ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
  AND CAST(p.projectCode AS BINARY)=CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
  AND CAST(p.projectType AS BINARY)=CAST(g.projectType AS BINARY)
JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
  AND CAST(pc.projectGroupCode AS BINARY)=CAST(g.projectGroupCode AS BINARY)) scope ON scope.contractNo=r.`contract_code`
GROUP BY CAST(JSON_ARRAY('DPPMS',r.`sync_evidence_key`) AS CHAR),CAST(CAST(JSON_ARRAY('DPPMS',r.`sync_evidence_key`) AS CHAR) AS BINARY)
) source_keys
LEFT JOIN fb_shipment_barcode b ON source_keys.objectRank=3 AND b.id=source_keys.sourceId
LEFT JOIN fb_shipment p ON p.packlist_id=b.pack_id
LEFT JOIN fb_contract f ON f.contract_id=p.con_id
LEFT JOIN fb_shipment_barcode_relation relation ON source_keys.objectRank=4 AND relation.id=source_keys.sourceId

UNION ALL
SELECT source_keys._groupKey,
 CASE source_keys.objectRank
 WHEN 3 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_SN','fields',JSON_OBJECT('id',b.`id`,'pack_id',b.`pack_id`,'item',b.`item`,'barcode',b.`barcode`,'com_barcode',b.`com_barcode`,'rma_no',b.`rma_no`,'isRMA',b.`isRMA`,'item2',b.`item2`,'barcode2',b.`barcode2`,'orderNumber',b.`orderNumber`,'lineNum',b.`lineNum`,'profitCenter',b.`profitCenter`,'soleAgentSuffix',b.`soleAgentSuffix`,'warrantyStartDate',b.`warrantyStartDate`,'warrantyMonth',b.`warrantyMonth`,'rmaBarcode',b.`rmaBarcode`,'updateTime',b.`updateTime`,'syncTime',b.`syncTime`,'uuid',b.`uuid`,'shipment_time',p.packdate,'contract_no',f.contract_code,'event_type',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END,'rma_marked',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END))) AS CHAR)
 WHEN 4 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_RELATION','fields',JSON_OBJECT('id',relation.`id`,'sn1',relation.`sn1`,'item1',relation.`item1`,'sn2',relation.`sn2`,'item2',relation.`item2`,'contract',relation.`contract`,'createtime',relation.`createtime`,'updatetime',relation.`updatetime`))) AS CHAR)
 ELSE source_keys._sourceRows END AS _sourceRows
FROM (
SELECT 2 AS objectRank,CAST(NULL AS UNSIGNED) AS sourceId,CONCAT('2:',SHA2(CAST(JSON_ARRAY('DPPMS',r.`packlist_id`) AS CHAR),256)) AS _groupKey,
 CAST(JSON_ARRAYAGG(JSON_OBJECT('object','PACKAGE','fields',JSON_OBJECT('packlist_id',r.`packlist_id`,'con_id',r.`con_id`,'packdate',r.`packdate`,'warrantyStartTime',r.`warrantyStartTime`,'warrantyEndTime',r.`warrantyEndTime`,'receiveName',r.`receiveName`,'emsNum',r.`emsNum`,'emsCompany',r.`emsCompany`,'carrier_name',r.`carrier_name`,'_scopeContractNo',r.`_scopeContractNo`))) AS CHAR) AS _sourceRows
FROM (
SELECT p.*,COALESCE(d.basicDataName,p.emsCompany) AS carrier_name,f.contract_code AS _scopeContractNo FROM fb_shipment p LEFT JOIN fnd_basic_data d ON d.dataTypeCode='01' AND d.basicDataId=p.emsCompany JOIN fb_contract f ON f.contract_id=p.con_id
) r
JOIN (SELECT DISTINCT pc.contractNo
FROM pm_project_group_relationship m
JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
  AND CAST(g.projectGroupCode AS BINARY)=CAST(m.projectGroupCode AS BINARY)
JOIN (SELECT DISTINCT scoped.projectId,scoped.projectCode,scoped.projectType
FROM (SELECT original.*,canonical.projectId AS canonicalProjectId
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
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) scoped
WHERE scoped.projectId=scoped.canonicalProjectId) p
  ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
  AND CAST(p.projectCode AS BINARY)=CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
  AND CAST(p.projectType AS BINARY)=CAST(g.projectType AS BINARY)
JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
  AND CAST(pc.projectGroupCode AS BINARY)=CAST(g.projectGroupCode AS BINARY)) scope ON scope.contractNo=r.`_scopeContractNo`
GROUP BY CAST(JSON_ARRAY('DPPMS',r.`packlist_id`) AS CHAR),CAST(CAST(JSON_ARRAY('DPPMS',r.`packlist_id`) AS CHAR) AS BINARY)
) source_keys
LEFT JOIN fb_shipment_barcode b ON source_keys.objectRank=3 AND b.id=source_keys.sourceId
LEFT JOIN fb_shipment p ON p.packlist_id=b.pack_id
LEFT JOIN fb_contract f ON f.contract_id=p.con_id
LEFT JOIN fb_shipment_barcode_relation relation ON source_keys.objectRank=4 AND relation.id=source_keys.sourceId

UNION ALL
SELECT source_keys._groupKey,
 CASE source_keys.objectRank
 WHEN 3 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_SN','fields',JSON_OBJECT('id',b.`id`,'pack_id',b.`pack_id`,'item',b.`item`,'barcode',b.`barcode`,'com_barcode',b.`com_barcode`,'rma_no',b.`rma_no`,'isRMA',b.`isRMA`,'item2',b.`item2`,'barcode2',b.`barcode2`,'orderNumber',b.`orderNumber`,'lineNum',b.`lineNum`,'profitCenter',b.`profitCenter`,'soleAgentSuffix',b.`soleAgentSuffix`,'warrantyStartDate',b.`warrantyStartDate`,'warrantyMonth',b.`warrantyMonth`,'rmaBarcode',b.`rmaBarcode`,'updateTime',b.`updateTime`,'syncTime',b.`syncTime`,'uuid',b.`uuid`,'shipment_time',p.packdate,'contract_no',f.contract_code,'event_type',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END,'rma_marked',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END))) AS CHAR)
 WHEN 4 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_RELATION','fields',JSON_OBJECT('id',relation.`id`,'sn1',relation.`sn1`,'item1',relation.`item1`,'sn2',relation.`sn2`,'item2',relation.`item2`,'contract',relation.`contract`,'createtime',relation.`createtime`,'updatetime',relation.`updatetime`))) AS CHAR)
 ELSE source_keys._sourceRows END AS _sourceRows
FROM (
SELECT 3 AS objectRank,r.`id` AS sourceId,CONCAT('3:',LPAD(CAST(r.`id` AS CHAR),20,'0')) AS _groupKey,
 CAST(NULL AS CHAR) AS _sourceRows
FROM (
SELECT STRAIGHT_JOIN b.*,p.packdate AS shipment_time,f.contract_code AS contract_no,
 CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END AS event_type,
 CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END AS rma_marked
FROM (SELECT DISTINCT pc.contractNo
FROM pm_project_group_relationship m
JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
  AND CAST(g.projectGroupCode AS BINARY)=CAST(m.projectGroupCode AS BINARY)
JOIN (SELECT DISTINCT scoped.projectId,scoped.projectCode,scoped.projectType
FROM (SELECT original.*,canonical.projectId AS canonicalProjectId
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
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) scoped
WHERE scoped.projectId=scoped.canonicalProjectId) p
  ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
  AND CAST(p.projectCode AS BINARY)=CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
  AND CAST(p.projectType AS BINARY)=CAST(g.projectType AS BINARY)
JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
  AND CAST(pc.projectGroupCode AS BINARY)=CAST(g.projectGroupCode AS BINARY)) scope
STRAIGHT_JOIN fb_contract f ON f.contract_code=scope.contractNo
STRAIGHT_JOIN fb_shipment p ON p.con_id=f.contract_id
STRAIGHT_JOIN fb_shipment_barcode b ON b.pack_id=p.packlist_id
) r
) source_keys
LEFT JOIN fb_shipment_barcode b ON source_keys.objectRank=3 AND b.id=source_keys.sourceId
LEFT JOIN fb_shipment p ON p.packlist_id=b.pack_id
LEFT JOIN fb_contract f ON f.contract_id=p.con_id
LEFT JOIN fb_shipment_barcode_relation relation ON source_keys.objectRank=4 AND relation.id=source_keys.sourceId

UNION ALL
SELECT source_keys._groupKey,
 CASE source_keys.objectRank
 WHEN 3 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_SN','fields',JSON_OBJECT('id',b.`id`,'pack_id',b.`pack_id`,'item',b.`item`,'barcode',b.`barcode`,'com_barcode',b.`com_barcode`,'rma_no',b.`rma_no`,'isRMA',b.`isRMA`,'item2',b.`item2`,'barcode2',b.`barcode2`,'orderNumber',b.`orderNumber`,'lineNum',b.`lineNum`,'profitCenter',b.`profitCenter`,'soleAgentSuffix',b.`soleAgentSuffix`,'warrantyStartDate',b.`warrantyStartDate`,'warrantyMonth',b.`warrantyMonth`,'rmaBarcode',b.`rmaBarcode`,'updateTime',b.`updateTime`,'syncTime',b.`syncTime`,'uuid',b.`uuid`,'shipment_time',p.packdate,'contract_no',f.contract_code,'event_type',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 'UNCLASSIFIED' ELSE 'SHIPMENT' END,'rma_marked',CASE WHEN COALESCE(b.rma_no,'')<>'' THEN 1 ELSE 0 END))) AS CHAR)
 WHEN 4 THEN CAST(JSON_ARRAY(JSON_OBJECT('object','DEVICE_RELATION','fields',JSON_OBJECT('id',relation.`id`,'sn1',relation.`sn1`,'item1',relation.`item1`,'sn2',relation.`sn2`,'item2',relation.`item2`,'contract',relation.`contract`,'createtime',relation.`createtime`,'updatetime',relation.`updatetime`))) AS CHAR)
 ELSE source_keys._sourceRows END AS _sourceRows
FROM (
SELECT 4 AS objectRank,r.`id` AS sourceId,CONCAT('4:',LPAD(CAST(r.`id` AS CHAR),20,'0')) AS _groupKey,
 CAST(NULL AS CHAR) AS _sourceRows
FROM (
SELECT after_sales_scope.* FROM (SELECT * FROM fb_shipment_barcode_relation WHERE sn1 <> sn2) after_sales_scope
) r
JOIN (SELECT DISTINCT pc.contractNo
FROM pm_project_group_relationship m
JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
  AND CAST(g.projectGroupCode AS BINARY)=CAST(m.projectGroupCode AS BINARY)
JOIN (SELECT DISTINCT scoped.projectId,scoped.projectCode,scoped.projectType
FROM (SELECT original.*,canonical.projectId AS canonicalProjectId
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
  AND (:projectIds='' OR FIND_IN_SET(canonical.projectId,:projectIds))) scoped
WHERE scoped.projectId=scoped.canonicalProjectId) p
  ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
  AND CAST(p.projectCode AS BINARY)=CAST(REPLACE(m.projectCode,'-his','') AS BINARY)
  AND CAST(p.projectType AS BINARY)=CAST(g.projectType AS BINARY)
JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
  AND CAST(pc.projectGroupCode AS BINARY)=CAST(g.projectGroupCode AS BINARY)) scope ON scope.contractNo=r.`contract`
) source_keys
LEFT JOIN fb_shipment_barcode b ON source_keys.objectRank=3 AND b.id=source_keys.sourceId
LEFT JOIN fb_shipment p ON p.packlist_id=b.pack_id
LEFT JOIN fb_contract f ON f.contract_id=p.con_id
LEFT JOIN fb_shipment_barcode_relation relation ON source_keys.objectRank=4 AND relation.id=source_keys.sourceId
;

-- [SHIPMENT_CONTRACT] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P15-phase-stream/task-after.json
-- queryRef=CONTRACT; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [PACKAGE] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P15-phase-stream/task-after.json
-- queryRef=CONTRACT; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [DEVICE_SN] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P15-phase-stream/task-after.json
-- queryRef=CONTRACT; 复用该来源结果/分组，不重复执行一份SELECT。;

-- [DEVICE_RELATION] QUERY_REF; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/P15-phase-stream/task-after.json
-- queryRef=CONTRACT; 复用该来源结果/分组，不重复执行一份SELECT。;

-- ===== 2. 目标/辅助查询与前置 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- B:GenericSyncJdbcStore.insertBatch:CONTRACT:com_contract; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_contract` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `authority_status`, `source_lifecycle_status`, `status`, `company_code`, `contract_no`, `customer_code`, `customer_name`, `master_source_record_key`, `master_source_system`, `currency_name`, `currency_code`, `contract_amount`, `contract_create_time`, `project_name`, `project_code`, `market_code`, `market_name`, `department_code`, `department_name`, `industry_code`, `industry_name`, `marketing_representative_code`, `marketing_representative_name`, `secondary_representative_code`, `system_name`, `company_id`, `company_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :authority_status, :source_lifecycle_status, :status, :company_code, :contract_no, :customer_code, :customer_name, :master_source_record_key, :master_source_system, :currency_name, :currency_code, :contract_amount, :contract_create_time, :project_name, :project_code, :market_code, :market_name, :department_code, :department_name, :industry_code, :industry_name, :marketing_representative_code, :marketing_representative_name, :secondary_representative_code, :system_name, :company_id, :company_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- B:GenericSyncJdbcStore.insertBatch:SHIPMENT_CONTRACT:com_shipment_contract_reference; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_shipment_contract_reference` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `mapping_status`, `contract_no`, `company_code`, `department_code`, `contract_type`, `customer_name`, `project_name`, `warranty_years`, `market_code`, `market_name`, `system_source_key`, `system_name`, `remark`, `source_record_key`, `source_system`, `contract_id`, `company_id`, `company_name`, `department_id`, `department_name`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :mapping_status, :contract_no, :company_code, :department_code, :contract_type, :customer_name, :project_name, :warranty_years, :market_code, :market_name, :system_source_key, :system_name, :remark, :source_record_key, :source_system, :contract_id, :company_id, :company_name, :department_id, :department_name);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- B:GenericSyncJdbcStore.insertBatch:PACKAGE:com_shipment_package; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `com_shipment_package` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `mapping_status`, `package_no`, `shipment_time`, `warranty_start_time`, `warranty_end_time`, `receiver_name`, `express_no`, `carrier_name`, `source_system`, `source_record_key`, `carrier_code`, `shipment_contract_ref_id`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :mapping_status, :package_no, :shipment_time, :warranty_start_time, :warranty_end_time, :receiver_name, :express_no, :carrier_name, :source_system, :source_record_key, :carrier_code, :shipment_contract_ref_id);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- B:GenericSyncJdbcStore.insertBatch:DEVICE_SN:ast_device_shipment; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_device_shipment` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `sync_status`, `device_sn`, `shipment_time`, `package_no`, `legacy_package_key`, `contract_no`, `event_type`, `warranty_start_date`, `warranty_months`, `rma_no`, `rma_related_sn`, `rma_marked`, `source_system`, `source_key`, `source_updated_at`, `source_payload`, `order_no`, `line_no`, `product_code`, `secondary_sn`, `secondary_item`, `internal_serial_no`, `is_rma`, `profit_center`, `sole_agent_suffix`, `sync_time`, `uuid`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :sync_status, :device_sn, :shipment_time, :package_no, :legacy_package_key, :contract_no, :event_type, :warranty_start_date, :warranty_months, :rma_no, :rma_related_sn, :rma_marked, :source_system, :source_key, :source_updated_at, :source_payload, :order_no, :line_no, :product_code, :secondary_sn, :secondary_item, :internal_serial_no, :is_rma, :profit_center, :sole_agent_suffix, :sync_time, :uuid);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- B:GenericSyncJdbcStore.insertBatch:DEVICE_RELATION:ast_device_relationship; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:125; kind=generic_insert_template
-- GENERIC_JDBC_DYNAMIC_TEMPLATE：由实际targets与目录列集合还原；运行按本批行的列签名生成多VALUES。
INSERT INTO `ast_device_relationship` (`id`, `tenant_id`, `creator`, `updater`, `create_time`, `update_time`, `deleted`, `version`, `source_device_sn`, `target_device_sn`, `relationship_type`, `contract_no`, `effective_from`, `source_system`, `source_key`, `source_version`, `source_item`, `target_item`)
VALUES (:id, :tenant_id, :creator, :updater, :create_time, :update_time, :deleted, :version, :source_device_sn, :target_device_sn, :relationship_type, :contract_no, :effective_from, :source_system, :source_key, :source_version, :source_item, :target_item);
-- 同批其余行沿同一列签名重复VALUES；具体NULL策略/LOOKUP/默认值/源版本由配置及GenericSyncEngine核验。;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- B:GenericSyncJdbcStore.updateBatch:CONTRACT:com_contract; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_contract` SET `currency_name` = CASE `id` WHEN :id THEN :currency_name ELSE `currency_name` END, `contract_amount` = CASE `id` WHEN :id THEN :contract_amount ELSE `contract_amount` END, `currency_code` = CASE `id` WHEN :id THEN :currency_code ELSE `currency_code` END, `contract_create_time` = CASE `id` WHEN :id THEN :contract_create_time ELSE `contract_create_time` END, `project_name` = CASE `id` WHEN :id THEN :project_name ELSE `project_name` END, `project_code` = CASE `id` WHEN :id THEN :project_code ELSE `project_code` END, `market_code` = CASE `id` WHEN :id THEN :market_code ELSE `market_code` END, `market_name` = CASE `id` WHEN :id THEN :market_name ELSE `market_name` END, `department_code` = CASE `id` WHEN :id THEN :department_code ELSE `department_code` END, `department_name` = CASE `id` WHEN :id THEN :department_name ELSE `department_name` END, `industry_code` = CASE `id` WHEN :id THEN :industry_code ELSE `industry_code` END, `industry_name` = CASE `id` WHEN :id THEN :industry_name ELSE `industry_name` END, `marketing_representative_code` = CASE `id` WHEN :id THEN :marketing_representative_code ELSE `marketing_representative_code` END, `marketing_representative_name` = CASE `id` WHEN :id THEN :marketing_representative_name ELSE `marketing_representative_name` END, `secondary_representative_code` = CASE `id` WHEN :id THEN :secondary_representative_code ELSE `secondary_representative_code` END, `system_name` = CASE `id` WHEN :id THEN :system_name ELSE `system_name` END, version = CASE id WHEN :id THEN :before_version_plus_bumps ELSE version END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- B:GenericSyncJdbcStore.updateBatch:SHIPMENT_CONTRACT:com_shipment_contract_reference; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_shipment_contract_reference` SET `contract_no` = CASE `id` WHEN :id THEN :contract_no ELSE `contract_no` END, `company_code` = CASE `id` WHEN :id THEN :company_code ELSE `company_code` END, `department_code` = CASE `id` WHEN :id THEN :department_code ELSE `department_code` END, `contract_type` = CASE `id` WHEN :id THEN :contract_type ELSE `contract_type` END, `customer_name` = CASE `id` WHEN :id THEN :customer_name ELSE `customer_name` END, `project_name` = CASE `id` WHEN :id THEN :project_name ELSE `project_name` END, `warranty_years` = CASE `id` WHEN :id THEN :warranty_years ELSE `warranty_years` END, `market_code` = CASE `id` WHEN :id THEN :market_code ELSE `market_code` END, `market_name` = CASE `id` WHEN :id THEN :market_name ELSE `market_name` END, `system_source_key` = CASE `id` WHEN :id THEN :system_source_key ELSE `system_source_key` END, `system_name` = CASE `id` WHEN :id THEN :system_name ELSE `system_name` END, `remark` = CASE `id` WHEN :id THEN :remark ELSE `remark` END, `contract_id` = CASE `id` WHEN :id THEN :contract_id ELSE `contract_id` END, `company_id` = CASE `id` WHEN :id THEN :company_id ELSE `company_id` END, `company_name` = CASE `id` WHEN :id THEN :company_name ELSE `company_name` END, `department_id` = CASE `id` WHEN :id THEN :department_id ELSE `department_id` END, `department_name` = CASE `id` WHEN :id THEN :department_name ELSE `department_name` END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- B:GenericSyncJdbcStore.updateBatch:PACKAGE:com_shipment_package; pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/dal/mysql/sync/GenericSyncJdbcStore.java:132; kind=generic_update_template
-- 实际updateBatch按列签名分组，按id构造CASE；本模板展示一项参数组，非UPDATE JOIN。
UPDATE `com_shipment_package` SET `shipment_time` = CASE `id` WHEN :id THEN :shipment_time ELSE `shipment_time` END, `warranty_start_time` = CASE `id` WHEN :id THEN :warranty_start_time ELSE `warranty_start_time` END, `warranty_end_time` = CASE `id` WHEN :id THEN :warranty_end_time ELSE `warranty_end_time` END, `receiver_name` = CASE `id` WHEN :id THEN :receiver_name ELSE `receiver_name` END, `express_no` = CASE `id` WHEN :id THEN :express_no ELSE `express_no` END, `carrier_name` = CASE `id` WHEN :id THEN :carrier_name ELSE `carrier_name` END, `shipment_contract_ref_id` = CASE `id` WHEN :id THEN :shipment_contract_ref_id ELSE `shipment_contract_ref_id` END, `carrier_code` = CASE `id` WHEN :id THEN :carrier_code ELSE `carrier_code` END
WHERE `tenant_id`=:tenant_id AND `deleted`=0 AND id IN (:current_batch_ids);
-- 实际目标先按业务键FOR UPDATE，更新行数必须等于本批行数；没有在此伪造额外version WHERE条件。;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
