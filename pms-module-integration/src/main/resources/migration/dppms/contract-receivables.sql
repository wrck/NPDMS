SELECT r.*
FROM sms_ofst_contract_head_sap r
WHERE r.contract_num IS NOT NULL
  AND (:contractNos='' OR FIND_IN_SET(r.contract_num,:contractNos))
