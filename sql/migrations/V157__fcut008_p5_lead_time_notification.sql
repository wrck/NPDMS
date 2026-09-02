ALTER TABLE `cut_approval_instance`
  ADD COLUMN `lead_time_enabled` bit(1) NULL AFTER `route_snapshot`,
  ADD COLUMN `lead_time_snapshot` json NULL AFTER `lead_time_enabled`;

UPDATE `cut_approval_instance`
SET `lead_time_enabled` = b'0'
WHERE `lead_time_enabled` IS NULL;

ALTER TABLE `cut_approval_instance`
  MODIFY COLUMN `lead_time_enabled` bit(1) NOT NULL,
  ADD CONSTRAINT `chk_cut_approval_lead_time` CHECK (
    (`lead_time_enabled` = b'0' AND `lead_time_snapshot` IS NULL)
    OR COALESCE((`lead_time_enabled` = b'1' AND `grade_code` IN ('A','B')
      AND `lead_time_snapshot` IS NOT NULL), FALSE));

ALTER TABLE `cut_approval_notification`
  ADD COLUMN `channel_code` varchar(24) NULL AFTER `template_code`,
  ADD COLUMN `provider_reference_id` varchar(128) NULL AFTER `message_id`,
  ADD COLUMN `last_attempt_at` datetime(3) NULL AFTER `last_error_code`;

UPDATE `cut_approval_notification`
SET `channel_code` = 'IN_PLATFORM'
WHERE `channel_code` IS NULL;

ALTER TABLE `cut_approval_notification`
  MODIFY COLUMN `channel_code` varchar(24) NOT NULL,
  DROP CHECK `chk_cut_approval_notification_status`,
  ADD CONSTRAINT `chk_cut_approval_notification_channel` CHECK (
    `channel_code` IN ('IN_PLATFORM','SMS','EMAIL','DINGTALK')),
  ADD CONSTRAINT `chk_cut_approval_notification_status` CHECK (
    (`channel_code` = 'IN_PLATFORM' AND `provider_reference_id` IS NULL AND `last_attempt_at` IS NULL
      AND ((`status_code` = 'PENDING' AND `message_id` IS NULL AND `next_retry_at` IS NULL
          AND `last_error_code` IS NULL AND `sent_at` IS NULL)
        OR COALESCE((`status_code` = 'PENDING_RETRY' AND `message_id` IS NULL
          AND `next_retry_at` IS NOT NULL
          AND CHAR_LENGTH(TRIM(`last_error_code`)) BETWEEN 1 AND 64 AND `sent_at` IS NULL), FALSE)
        OR COALESCE((`status_code` = 'SENT' AND `message_id` > 0 AND `next_retry_at` IS NULL
          AND `last_error_code` IS NULL AND `sent_at` IS NOT NULL), FALSE)))
    OR (`channel_code` IN ('SMS','EMAIL','DINGTALK') AND `message_id` IS NULL AND `sent_at` IS NULL
      AND ((`status_code` = 'PENDING' AND `retry_count` = 0 AND `provider_reference_id` IS NULL
          AND `next_retry_at` IS NULL AND `last_error_code` IS NULL AND `last_attempt_at` IS NULL)
        OR COALESCE((`status_code` = 'PENDING_RETRY' AND `retry_count` > 0
          AND `next_retry_at` IS NOT NULL AND `last_attempt_at` IS NOT NULL
          AND CHAR_LENGTH(TRIM(`last_error_code`)) BETWEEN 1 AND 64), FALSE)
        OR COALESCE((`status_code` = 'ACCEPTED'
          AND CHAR_LENGTH(TRIM(`provider_reference_id`)) BETWEEN 1 AND 128
          AND `next_retry_at` IS NULL AND `last_error_code` IS NULL AND `last_attempt_at` IS NOT NULL), FALSE)
        OR (`status_code` = 'DELIVERY_UNKNOWN' AND `next_retry_at` IS NULL
          AND `last_error_code` IS NULL AND `last_attempt_at` IS NOT NULL))));
