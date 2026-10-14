-- P26 / SHIPMENT_CONTRACT | 原任务 2107037031219654657 v14
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
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
JOIN (SELECT DISTINCT pc.contractNo FROM pm_project_group_relationship m
 JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode
 JOIN pm_project p ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType
 JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode
 WHERE (:projectTypes='' OR FIND_IN_SET(p.projectType,:projectTypes))
 AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))
 AND p.projectId=(SELECT MAX(newer.projectId) FROM pm_project newer
 WHERE newer.projectCode=p.projectCode AND newer.projectType=p.projectType
 AND CAST(newer.projectCode AS BINARY)=CAST(p.projectCode AS BINARY))) project_scope ON project_scope.contractNo=after_sales_scope.contract_code AND FIND_IN_SET(after_sales_scope.contract_code,project_scope.contractNo);
