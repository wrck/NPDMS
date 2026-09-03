-- F-IMP-002 / EXE-01: persist the frozen PLT file fact versions for revalidation.
-- V133 has no production evidence revisions; fail closed instead of inventing file facts.

DELIMITER //
CREATE PROCEDURE `fimp002_require_empty_delivery_evidence_revision`()
BEGIN
  IF EXISTS (SELECT 1 FROM `imp_delivery_evidence_revision` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-IMP-002 file fact migration requires an empty evidence revision table';
  END IF;
END//
DELIMITER ;

CALL `fimp002_require_empty_delivery_evidence_revision`();
DROP PROCEDURE `fimp002_require_empty_delivery_evidence_revision`;

ALTER TABLE `imp_delivery_evidence_revision`
  ADD COLUMN `file_artifact_id` bigint NOT NULL AFTER `revision_no`,
  ADD COLUMN `file_scope_version` bigint NOT NULL AFTER `file_version_no`,
  ADD COLUMN `file_fact_version` json NOT NULL AFTER `file_scope_version`,
  ADD CONSTRAINT `chk_imp_delivery_evidence_file_artifact`
    CHECK (`file_artifact_id` > 0),
  ADD CONSTRAINT `chk_imp_delivery_evidence_file_scope`
    CHECK (`file_scope_version` >= 0),
  ADD CONSTRAINT `chk_imp_delivery_evidence_file_fact_version` CHECK (
    JSON_TYPE(`file_fact_version`) = 'OBJECT'
    AND JSON_LENGTH(`file_fact_version`) = 3
    AND JSON_TYPE(JSON_EXTRACT(`file_fact_version`, '$.artifactVersion')) IN ('INTEGER', 'UNSIGNED INTEGER')
    AND JSON_TYPE(JSON_EXTRACT(`file_fact_version`, '$.referenceVersion')) IN ('INTEGER', 'UNSIGNED INTEGER')
    AND JSON_TYPE(JSON_EXTRACT(`file_fact_version`, '$.availabilityVersion')) IN ('INTEGER', 'UNSIGNED INTEGER')
    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`file_fact_version`, '$.artifactVersion')) AS SIGNED) >= 0
    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`file_fact_version`, '$.referenceVersion')) AS SIGNED) >= 0
    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`file_fact_version`, '$.availabilityVersion')) AS SIGNED) >= 0
  );
