-- F-CUT-004旧割接方案核对Job仅登记为暂停；生产激活需另行Gate。
INSERT INTO `infra_job`
(`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
 `retry_count`, `retry_interval`, `monitor_timeout`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992602010004, '割接方案旧数据核对', 2,
       'legacyCutoverPlanReconciliationJob', '', '0 0/5 * * * ?',
       0, 0, 0, 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'legacyCutoverPlanReconciliationJob'
      AND `deleted` = b'0'
);

UPDATE `infra_job`
SET `name`='割接方案旧数据核对', `status`=2, `handler_param`='',
    `cron_expression`='0 0/5 * * * ?', `retry_count`=0, `retry_interval`=0,
    `monitor_timeout`=0, `updater`='seed', `update_time`=NOW(), `deleted`=b'0'
WHERE `handler_name`='legacyCutoverPlanReconciliationJob'
  AND `deleted`=b'0';
