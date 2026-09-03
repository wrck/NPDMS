-- EXE-01 到货签收证据业务回执重试 Job。
-- ACC 生产消费者与双向契约尚未形成，必须保持暂停。
INSERT INTO `infra_job`
(`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
 `retry_count`, `retry_interval`, `monitor_timeout`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992602010002, '到货签收证据业务重试', 2,
       'arrivalEvidenceRetryJob', '', '0 0/1 * * * ?',
       0, 0, 0, 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'arrivalEvidenceRetryJob'
      AND `deleted` = b'0'
);

UPDATE `infra_job`
SET `name` = '到货签收证据业务重试',
    `status` = 2,
    `handler_param` = '',
    `cron_expression` = '0 0/1 * * * ?',
    `retry_count` = 0,
    `retry_interval` = 0,
    `monitor_timeout` = 0,
    `updater` = 'seed',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `handler_name` = 'arrivalEvidenceRetryJob'
  AND `deleted` = b'0';
