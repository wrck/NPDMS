SELECT r.*
FROM sms_ofst_contract_head_sap r
JOIN (/* @include valid-contracts */) scope ON scope.contractNo=r.contract_num
WHERE r.contract_num IS NOT NULL
  AND (:contractNos='' OR FIND_IN_SET(r.contract_num,:contractNos))
