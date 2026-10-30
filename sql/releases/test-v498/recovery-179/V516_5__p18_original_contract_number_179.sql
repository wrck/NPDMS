-- 179-only P18 original contract number and nullable unresolved contract reference.
-- Source: previously reviewed and verified two-statement P18 DDL on 2026-10-22.
-- Existing FK and unique constraints are retained; no source rows or business rows are modified.
SET @p18_179_history_ok = (
 SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND
 ((version='506.1' AND checksum=-703476136 AND script='V506_1__reconcile_partial_cutover_support.sql') OR
  (version='516' AND checksum=-2064355135 AND script='V516__stage_plan_management_menu_roles.sql'))
);
SET @p18_179_guard_sql = IF(@p18_179_history_ok=2
 AND (SELECT COUNT(*) FROM flyway_schema_history WHERE version='516.4' AND success=1)=1
 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='com_project_contract_relation' AND column_name='contract_no')=0
 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='com_project_contract_relation' AND column_name='contract_id' AND column_type='bigint' AND is_nullable='NO')=1
 AND (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='com_project_contract_relation' AND column_name='contract_id' AND referenced_table_name='com_contract')=1,
 'SELECT 1 AS p18_179_preconditions_verified','P18_179_PRECONDITIONS_NOT_MET');
PREPARE p18_179_guard FROM @p18_179_guard_sql;
EXECUTE p18_179_guard;
DEALLOCATE PREPARE p18_179_guard;

-- Previously verified statement 1; SHA-256 001cdab0fe606a1cadfe1f27b0e9cf4cf1a025b367ba1b97a1755546280515a2
ALTER TABLE `com_project_contract_relation`
  ADD COLUMN `contract_no` VARCHAR(64) NULL COMMENT '原始合同号' AFTER `contract_id`;

-- Previously verified statement 2; SHA-256 36719e8549413a8f38e77c27dc075057ff43bb0b3962502af8dd8c34d110c671
ALTER TABLE `com_project_contract_relation`
  MODIFY COLUMN `contract_id` BIGINT NULL COMMENT '合同ID';

