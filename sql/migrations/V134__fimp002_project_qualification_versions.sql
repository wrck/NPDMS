-- F-IMP-002 / EXE-01: persist the frozen PROJ qualification versions.
-- V133 has no production rows; fail closed instead of inventing values for existing roots.

DELIMITER //
CREATE PROCEDURE `fimp002_require_empty_arrival_acceptance`()
BEGIN
  IF EXISTS (SELECT 1 FROM `imp_arrival_acceptance` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-IMP-002 qualification version migration requires an empty arrival root';
  END IF;
END//
DELIMITER ;

CALL `fimp002_require_empty_arrival_acceptance`();
DROP PROCEDURE `fimp002_require_empty_arrival_acceptance`;

ALTER TABLE `imp_arrival_acceptance`
  ADD COLUMN `project_version` int NOT NULL AFTER `status`,
  ADD COLUMN `project_participant_fact_version` bigint NOT NULL AFTER `project_version`,
  ADD COLUMN `project_scope_version` bigint NOT NULL AFTER `project_participant_fact_version`;
