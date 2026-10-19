SELECT f.*, f.contract_id AS sync_evidence_key,
 CASE WHEN f.contract_id IS NULL OR TRIM(f.contract_id)=''
      THEN 'SOURCE_KEY_MISSING: fb_contract.contract_id' ELSE NULL END AS sync_source_issue
FROM fb_contract f
WHERE (:contractNos='' OR FIND_IN_SET(f.contract_code,:contractNos))
