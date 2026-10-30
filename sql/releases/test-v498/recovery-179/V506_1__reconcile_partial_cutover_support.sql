-- 179-specific Flyway V506.1 recovery of the partially executed V506.
-- Preconditions: the original V506 failed receipt and exact history row are archived;
-- original V506 remains frozen for environments where it was applied successfully.
-- This file is not suitable for an empty database or a database without the partial V506 DDL.
-- It records the observed partial schema and removes the two retired triggers.
SET @v506_1_columns = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND is_nullable = 'YES' AND column_default IS NULL
    AND ((table_name = 'cut_cutover_support_arrangement' AND
            ((column_name = 'person_source_code' AND column_type = 'varchar(32)') OR
             (column_name IN ('person_source_id', 'person_source_version') AND column_type = 'bigint')))
      OR (table_name = 'cut_plan_support_contact_change' AND
            ((column_name IN ('before_person_source_code', 'after_person_source_code') AND column_type = 'varchar(32)') OR
             (column_name IN ('before_person_source_id', 'before_person_source_version',
                              'after_person_source_id', 'after_person_source_version') AND column_type = 'bigint'))))
);
SET @v506_1_checks = (
  SELECT COUNT(*) FROM information_schema.table_constraints tc
  JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema
    AND cc.constraint_name=tc.constraint_name
  WHERE tc.table_schema=DATABASE() AND tc.table_name='cut_cutover_support_arrangement'
    AND tc.constraint_name='chk_cut_support_person_source' AND tc.enforced='YES'
    AND SHA2(cc.check_clause,256)='27ca892b05a36890e2ab6fa38219b740a148c990d4301032861359a6601eb1f9'
);
SET @v506_1_guard = IF(@v506_1_columns=9 AND @v506_1_checks=1,
  'SELECT 1 AS v506_partial_schema_verified', 'V506_1_PARTIAL_SCHEMA_CONTRACT_FAILED');
PREPARE v506_1_guard_statement FROM @v506_1_guard;
EXECUTE v506_1_guard_statement;
DEALLOCATE PREPARE v506_1_guard_statement;
DROP TRIGGER IF EXISTS trg_cut_plan_support_submit;
DROP TRIGGER IF EXISTS trg_cut_support_person_history;
