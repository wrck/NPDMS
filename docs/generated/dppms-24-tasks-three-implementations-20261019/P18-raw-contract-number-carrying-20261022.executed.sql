-- Executed after explicit approval on 172.17.90.178:13306/npdms. Historical receipt; do not rerun.
ALTER TABLE `npdms`.`com_project_contract_relation`
  ADD COLUMN `contract_no` VARCHAR(64) NULL COMMENT '原始合同号' AFTER `contract_id`;

ALTER TABLE `npdms`.`com_project_contract_relation`
  MODIFY COLUMN `contract_id` BIGINT NULL COMMENT '合同ID';
