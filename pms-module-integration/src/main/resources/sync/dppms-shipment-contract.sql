SELECT f.*, e.company_code,
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
