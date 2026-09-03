-- F-IMP-002 / EXE-01: a difference revision has a fact version only when it is itself a fact source.
-- Existing rows cannot prove whether the old mandatory value was authoritative, so fail closed.

DELIMITER //
CREATE PROCEDURE `fimp002_require_empty_arrival_difference`()
BEGIN
  IF EXISTS (SELECT 1 FROM `imp_arrival_difference` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-IMP-002 difference fact version migration requires an empty difference table';
  END IF;
END//
DELIMITER ;

CALL `fimp002_require_empty_arrival_difference`();
DROP PROCEDURE `fimp002_require_empty_arrival_difference`;

ALTER TABLE `imp_arrival_difference`
  DROP CHECK `chk_imp_arrival_difference_fact_version`,
  MODIFY COLUMN `project_fact_version` bigint NULL,
  ADD CONSTRAINT `chk_imp_arrival_difference_fact_version`
    CHECK (`project_fact_version` IS NULL OR `project_fact_version` >= 0);
