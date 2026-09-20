-- F-INT-012 Device Ops 生产网关：下发映射持久化（platform_task_id -> DAC collection_id）
CREATE TABLE `int_device_ops_dispatch` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `platform_task_id` VARCHAR(64) NOT NULL,
  `idempotency_key` VARCHAR(200) NOT NULL,
  `namespace` VARCHAR(100) NOT NULL,
  `collection_id` VARCHAR(128) NULL,
  `external_status` VARCHAR(64) NULL,
  `trace_id` VARCHAR(128) NULL,
  `creator` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` VARCHAR(64) DEFAULT '',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` BIT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_device_ops_dispatch_task` (`tenant_id`, `platform_task_id`),
  KEY `idx_int_device_ops_dispatch_collection` (`tenant_id`, `collection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- F-INT-012 对账轮询任务；网关未装配时注册器跳过同步，任务保持停用事实由注册入口控制
INSERT INTO infra_job
    (name, status, handler_name, handler_param, cron_expression,
     retry_count, retry_interval, monitor_timeout,
     creator, create_time, updater, update_time, deleted)
SELECT
    'DAC采集对账轮询', 1, 'deviceOpsReconciliationJob', '',
    '0/30 * * * * ?', 0, 0, 0,
    'fint012', CURRENT_TIMESTAMP, 'fint012', CURRENT_TIMESTAMP, b'0'
WHERE NOT EXISTS (
    SELECT 1
    FROM infra_job
    WHERE handler_name = 'deviceOpsReconciliationJob'
      AND deleted = b'0'
);
