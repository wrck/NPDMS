-- 项目任务 Outbox 投递 Job 注册（TaskAssigned / TaskCompleted）。
-- 当前主代码尚无 TaskAssignedMessage/TaskCompletedMessage 的 @EventListener 消费者；
-- 依据 V197 的零监听器守卫先例，注册为暂停（status=2）：补齐 infra_job 登记缺口并保持可管理，
-- 首个消费者落地时再置为开启，避免空投递把事件误标记为已投递。
INSERT INTO `infra_job`
(`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
 `retry_count`, `retry_interval`, `monitor_timeout`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992602010007, '项目任务事件投递', 2,
       'projectTaskOutboxDeliveryJob', '', '0/30 * * * * ?',
       0, 0, 0, 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'projectTaskOutboxDeliveryJob'
      AND `deleted` = b'0'
);

UPDATE `infra_job`
SET `name` = '项目任务事件投递',
    `status` = 2,
    `handler_param` = '',
    `cron_expression` = '0/30 * * * * ?',
    `retry_count` = 0,
    `retry_interval` = 0,
    `monitor_timeout` = 0,
    `updater` = 'seed',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `handler_name` = 'projectTaskOutboxDeliveryJob'
  AND `deleted` = b'0';
