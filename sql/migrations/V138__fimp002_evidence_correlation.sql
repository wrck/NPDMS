-- F-IMP-002 / EXE-01: persist the first publication correlationId for all retries.
-- Existing published rows cannot recover this value unambiguously, so fail closed.

DELIMITER //
CREATE PROCEDURE `fimp002_require_unpublished_delivery_evidence`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM `imp_delivery_evidence`
    WHERE `acc_sync_status` <> 'NOT_PUBLISHED'
    LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-IMP-002 correlation migration requires all evidence to be unpublished';
  END IF;
END//
DELIMITER ;

CALL `fimp002_require_unpublished_delivery_evidence`();
DROP PROCEDURE `fimp002_require_unpublished_delivery_evidence`;

ALTER TABLE `imp_delivery_evidence`
  ADD COLUMN `acc_correlation_id` varchar(128) NULL AFTER `acc_last_event_id`,
  ADD CONSTRAINT `chk_imp_delivery_evidence_correlation` CHECK (
    (`acc_sync_status` = 'NOT_PUBLISHED' AND `acc_correlation_id` IS NULL)
    OR
    (`acc_sync_status` <> 'NOT_PUBLISHED'
      AND `acc_correlation_id` IS NOT NULL
      AND CHAR_LENGTH(TRIM(`acc_correlation_id`)) BETWEEN 1 AND 128
      AND `acc_correlation_id` = TRIM(`acc_correlation_id`))
  );
