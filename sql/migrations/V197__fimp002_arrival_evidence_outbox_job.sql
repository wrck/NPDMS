-- EXE-01 到货签收证据 Outbox 投递 Job。
-- ACC 生产消费者尚未形成，必须保持暂停，避免零监听器时误标记投递成功。
INSERT INTO `infra_job`
(`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
 `retry_count`, `retry_interval`, `monitor_timeout`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992602010001, '到货签收证据事件投递', 2,
       'arrivalEvidenceOutboxDeliveryJob', '', '0/30 * * * * ?',
       0, 0, 0, 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'arrivalEvidenceOutboxDeliveryJob'
      AND `deleted` = b'0'
);

UPDATE `infra_job`
SET `name` = '到货签收证据事件投递',
    `status` = 2,
    `handler_param` = '',
    `cron_expression` = '0/30 * * * * ?',
    `retry_count` = 0,
    `retry_interval` = 0,
    `monitor_timeout` = 0,
    `updater` = 'seed',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `handler_name` = 'arrivalEvidenceOutboxDeliveryJob'
  AND `deleted` = b'0';
