-- Chronologically replayed from 3a27eeffb1f081a0a70842b6326b66d90b9c95cf (codex/f-acc-001-sds), original sql/migrations/V131__facc001_acceptance_report_jobs.sql.
-- Renumbered after current master; Feature status is not promoted by this receipt.

-- F-ACC-001 / ACC-03、ACC-04：正式配置报告事件投递与归档补偿任务。

DROP PROCEDURE IF EXISTS `facc001_apply_v131_acceptance_report_jobs`;

DELIMITER $$
CREATE PROCEDURE `facc001_apply_v131_acceptance_report_jobs`()
BEGIN
  DECLARE involved_row_count INT DEFAULT 0;
  DECLARE exact_row_count INT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;

  SELECT COUNT(*) INTO involved_row_count
  FROM `infra_job`
  WHERE `id` IN (992004900001,992004900002)
     OR `handler_name` IN ('acceptanceReportOutboxDeliveryJob',
                           'acceptanceReportArchiveCompensationJob');

  SELECT COUNT(*) INTO exact_row_count
  FROM `infra_job`
  WHERE (`id` = 992004900001
      AND `handler_name` = 'acceptanceReportOutboxDeliveryJob'
      OR `id` = 992004900002
      AND `handler_name` = 'acceptanceReportArchiveCompensationJob')
    AND `status` = 1
    AND `handler_param` = ''
    AND `cron_expression` = '0/30 * * * * ?'
    AND `retry_count` = 0
    AND `retry_interval` = 0
    AND `monitor_timeout` = 0
    AND `deleted` = b'0';

  IF involved_row_count = 0 THEN
    INSERT INTO `infra_job`
    (`id`,`name`,`status`,`handler_name`,`handler_param`,`cron_expression`,
     `retry_count`,`retry_interval`,`monitor_timeout`,
     `creator`,`create_time`,`updater`,`update_time`,`deleted`)
    VALUES
    (992004900001,'验收报告事件投递',1,'acceptanceReportOutboxDeliveryJob','',
     '0/30 * * * * ?',0,0,0,'facc001_v131',NOW(3),'facc001_v131',NOW(3),b'0'),
    (992004900002,'验收报告归档补偿',1,'acceptanceReportArchiveCompensationJob','',
     '0/30 * * * * ?',0,0,0,'facc001_v131',NOW(3),'facc001_v131',NOW(3),b'0');
  ELSEIF NOT (involved_row_count = 2 AND exact_row_count = 2) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V131 acceptance report job state is partial or conflicting';
  END IF;

  IF (SELECT COUNT(*)
      FROM `infra_job`
      WHERE (`id` = 992004900001
          AND `handler_name` = 'acceptanceReportOutboxDeliveryJob'
          OR `id` = 992004900002
          AND `handler_name` = 'acceptanceReportArchiveCompensationJob')
        AND `status` = 1
        AND `handler_param` = ''
        AND `cron_expression` = '0/30 * * * * ?'
        AND `retry_count` = 0
        AND `retry_interval` = 0
        AND `monitor_timeout` = 0
        AND `deleted` = b'0') <> 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'F-ACC-001 V131 acceptance report job verification failed';
  END IF;

  COMMIT;
END$$
DELIMITER ;

CALL `facc001_apply_v131_acceptance_report_jobs`();
DROP PROCEDURE IF EXISTS `facc001_apply_v131_acceptance_report_jobs`;
