-- F-CUT-008: preserve the trusted approval-command correlation chain on each notification intent.
-- Existing external rows cannot recover this value unambiguously, so fail before ALTER.

DROP PROCEDURE IF EXISTS `fcut008_require_no_external_notification_history`;

DELIMITER //
CREATE PROCEDURE `fcut008_require_no_external_notification_history`()
BEGIN
  IF EXISTS (
    SELECT 1 FROM `cut_approval_notification`
    WHERE `channel_code` IN ('SMS','EMAIL','DINGTALK')
    LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-CUT-008 correlation migration requires no external notification history';
  END IF;
END//
DELIMITER ;

CALL `fcut008_require_no_external_notification_history`();
DROP PROCEDURE IF EXISTS `fcut008_require_no_external_notification_history`;

ALTER TABLE `cut_approval_notification`
  ADD COLUMN `correlation_id` varchar(128) NULL AFTER `delivery_key`,
  ADD CONSTRAINT `chk_cut_approval_notification_correlation` CHECK (
    (`channel_code` = 'IN_PLATFORM' AND (`correlation_id` IS NULL OR COALESCE(
      CHAR_LENGTH(`correlation_id`) BETWEEN 1 AND 128
      AND CHAR_LENGTH(`correlation_id`) = CHAR_LENGTH(TRIM(`correlation_id`)), FALSE)))
    OR COALESCE((`channel_code` IN ('SMS','EMAIL','DINGTALK')
      AND `correlation_id` IS NOT NULL
      AND CHAR_LENGTH(`correlation_id`) BETWEEN 1 AND 128
      AND CHAR_LENGTH(`correlation_id`) = CHAR_LENGTH(TRIM(`correlation_id`))), FALSE)
  );
