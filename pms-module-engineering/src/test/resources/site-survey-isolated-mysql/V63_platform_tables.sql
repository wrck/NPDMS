CREATE TABLE IF NOT EXISTS `plt_idempotency_record` (

    `id` BIGINT NOT NULL AUTO_INCREMENT,

    `scope_code` VARCHAR(128) NOT NULL,

    `actor_id` BIGINT NOT NULL,

    `idempotency_key` VARCHAR(128) NOT NULL,

    `request_digest` CHAR(64) NOT NULL,

    `status` VARCHAR(32) NOT NULL,

    `resource_type` VARCHAR(64) NULL,

    `resource_key` VARCHAR(128) NULL,

    `response_payload` JSON NULL,

    `version` INT UNSIGNED NOT NULL DEFAULT 0,

    `creator` VARCHAR(64) NULL DEFAULT '',

    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    `updater` VARCHAR(64) NULL DEFAULT '',

    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    `deleted` BIT(1) NOT NULL DEFAULT b'0',

    `tenant_id` BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_plt_idempotency_scope` (`tenant_id`, `scope_code`, `actor_id`, `idempotency_key`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台命令幂等记录';

CREATE TABLE IF NOT EXISTS `plt_operation_audit` (

    `id` BIGINT NOT NULL AUTO_INCREMENT,

    `operation_code` VARCHAR(128) NOT NULL,

    `aggregate_type` VARCHAR(64) NOT NULL,

    `aggregate_key` VARCHAR(128) NULL,

    `actor_id` BIGINT NOT NULL,

    `correlation_id` VARCHAR(128) NOT NULL,

    `idempotency_key_digest` CHAR(64) NULL,

    `result_code` VARCHAR(32) NOT NULL,

    `detail_snapshot` JSON NOT NULL,

    `occurred_at` DATETIME(3) NOT NULL,

    `creator` VARCHAR(64) NULL DEFAULT '',

    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    `tenant_id` BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),

    KEY `idx_plt_operation_audit_aggregate`

        (`tenant_id`, `aggregate_type`, `aggregate_key`, `occurred_at`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台操作审计';

CREATE TABLE IF NOT EXISTS `plt_outbox_event` (

    `id` BIGINT NOT NULL AUTO_INCREMENT,

    `event_id` VARCHAR(64) NOT NULL,

    `event_type` VARCHAR(128) NOT NULL,

    `aggregate_type` VARCHAR(64) NOT NULL,

    `aggregate_key` VARCHAR(128) NOT NULL,

    `payload` JSON NOT NULL,

    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',

    `occurred_at` DATETIME(3) NOT NULL,

    `next_retry_time` DATETIME(3) NULL,

    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0,

    `creator` VARCHAR(64) NULL DEFAULT '',

    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    `updater` VARCHAR(64) NULL DEFAULT '',

    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    `tenant_id` BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_plt_outbox_event_id` (`event_id`),

    KEY `idx_plt_outbox_dispatch` (`status`, `next_retry_time`, `id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台事务Outbox事件';
