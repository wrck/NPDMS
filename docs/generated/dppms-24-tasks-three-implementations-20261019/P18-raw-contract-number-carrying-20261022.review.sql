-- REVIEW ONLY. Not executed. Target only 172.17.90.178:13306/npdms.
-- User has not authorized these two structural exceptions.
-- The current relation table has 17 columns, no contract_no, and contract_id NOT NULL.
-- Raw source contract numbers: maximum 18 characters; formal com_contract.contract_no capacity 64.

ALTER TABLE `npdms`.`com_project_contract_relation`
  ADD COLUMN `contract_no` VARCHAR(64) NULL COMMENT '原始合同号' AFTER `contract_id`;

ALTER TABLE `npdms`.`com_project_contract_relation`
  MODIFY COLUMN `contract_id` BIGINT NULL COMMENT '合同ID';

-- Existing tenant foreign key and unique keys remain in place.
-- No Flyway, data migration, source modification, new contract, placeholder ID or mirror table.
-- These statements alone do not implement the synchronization or authorize its execution.

-- Postprocessing SQL shape for review only; named parameters are not executable SQL.
-- Original fields, including contract_no and historical update_time, remain unchanged.
-- this_run_relation_ids contains only new rows / verified independent bindings of this Run.
-- Exact matching retains tenant + source project company + original contract number.
-- No cross-company, trim, case folding or guessed contract selection.
-- UPDATE npdms.com_project_contract_relation r
-- JOIN npdms.proj_project p ON p.tenant_id=r.tenant_id AND p.id=r.project_id
-- JOIN npdms.com_contract c ON c.tenant_id=r.tenant_id
--   AND c.company_code=p.company_code AND c.contract_no=r.contract_no
--   AND BINARY c.company_code=BINARY p.company_code
--   AND BINARY c.contract_no=BINARY r.contract_no AND c.deleted=0
-- SET r.contract_id=c.id,r.update_time=r.update_time
-- WHERE r.tenant_id=:tenant_id AND r.id IN (:this_run_relation_ids)
--   AND r.contract_id IS NULL AND r.source_system='DPPMS';
