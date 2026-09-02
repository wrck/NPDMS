-- F-CUT-008外部提醒投递Job仅登记为暂停；生产激活必须等待INT-10/INT-05 Provider与真实传播Gate。
INSERT INTO `infra_job`
(`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
 `retry_count`, `retry_interval`, `monitor_timeout`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992602073002, '割接P5审批外部提醒投递', 2,
       'cutoverExternalApprovalNotificationJob', '', '0/30 * * * * ?',
       0, 0, 0, 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name`='cutoverExternalApprovalNotificationJob' AND `deleted`=b'0'
);

UPDATE `infra_job`
SET `name`='割接P5审批外部提醒投递', `status`=2, `handler_param`='',
    `cron_expression`='0/30 * * * * ?', `retry_count`=0, `retry_interval`=0,
    `monitor_timeout`=0, `updater`='seed', `update_time`=NOW(), `deleted`=b'0'
WHERE `handler_name`='cutoverExternalApprovalNotificationJob' AND `deleted`=b'0';
