CREATE TABLE `pms_project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL COMMENT '全局唯一且不可复用的项目编码',
  `name` varchar(128) NOT NULL,
  `customer_id` bigint NOT NULL,
  `contract_code` varchar(64) DEFAULT NULL,
  `office_id` bigint DEFAULT NULL,
  `sales_user_id` bigint DEFAULT NULL,
  `industry` varchar(64) DEFAULT NULL,
  `implementation_mode` varchar(64) DEFAULT NULL,
  `project_type` varchar(64) DEFAULT NULL,
  `shipment_status` varchar(64) DEFAULT NULL,
  `source_system` varchar(64) NOT NULL,
  `source_business_key` varchar(128) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_project_code` (`code`),
  UNIQUE KEY `uk_pms_project_source_key` (`source_system`, `source_business_key`),
  KEY `idx_pms_project_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS项目权威主数据';

CREATE TABLE `pms_project_sync_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(64) NOT NULL,
  `source_system` varchar(64) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `payload_hash` varchar(64) NOT NULL,
  `total_count` int NOT NULL,
  `success_count` int NOT NULL DEFAULT 0,
  `failure_count` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0处理中 1成功 2部分成功 3失败',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_project_sync_batch_no` (`batch_no`),
  UNIQUE KEY `uk_pms_project_sync_idempotency` (`source_system`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS项目同步批次';

CREATE TABLE `pms_project_sync_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `project_code` varchar(64) NOT NULL,
  `project_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL COMMENT '0失败 1成功',
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pms_project_sync_detail` (`batch_id`, `project_code`),
  KEY `idx_pms_project_sync_detail_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PMS项目同步明细';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(18009, '项目主数据同步', 'pms:project:sync', 3, 10, 18000, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18010, '同步批次查询', 'pms:project:sync-query', 3, 11, 18000, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
 `update_time` = NOW(), `deleted` = b'0';
