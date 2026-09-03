CREATE TABLE `srv_inspection_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `detection_id` varchar(64) NOT NULL,
  `rule_name` varchar(128) NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_srv_inspection_rule_tenant_detection` (`tenant_id`, `detection_id`),
  UNIQUE KEY `uk_srv_inspection_rule_tenant_name` (`tenant_id`, `rule_name`),
  UNIQUE KEY `uk_srv_inspection_rule_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_srv_inspection_rule_tenant_id_name` (`tenant_id`, `id`, `rule_name`),
  CONSTRAINT `chk_srv_inspection_rule_detection_id` CHECK (`detection_id` <> ''),
  CONSTRAINT `chk_srv_inspection_rule_name` CHECK (`rule_name` <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='INS-03 INS-09 inspection rule identity';

CREATE TABLE `srv_inspection_rule_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_id` bigint NOT NULL,
  `revision_no` int unsigned NOT NULL,
  `status_code` varchar(16) NOT NULL,
  `rule_name_snapshot` varchar(128) NOT NULL,
  `inspection_item` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `category_code` varchar(32) DEFAULT NULL,
  `category_name_snapshot` varchar(128) DEFAULT NULL,
  `severity_code` varchar(16) DEFAULT NULL,
  `severity_name_snapshot` varchar(64) DEFAULT NULL,
  `sort_order` int DEFAULT NULL,
  `expected_result_regex` varchar(1024) DEFAULT NULL,
  `threshold_data_type` varchar(32) DEFAULT NULL,
  `threshold_operator` varchar(4) DEFAULT NULL,
  `threshold_value` decimal(20,6) DEFAULT NULL,
  `threshold_unit` varchar(32) DEFAULT NULL,
  `published_by` bigint DEFAULT NULL,
  `published_at` datetime(3) DEFAULT NULL,
  `disabled_by` bigint DEFAULT NULL,
  `disabled_at` datetime(3) DEFAULT NULL,
  `current_published_marker` tinyint GENERATED ALWAYS AS (
    CASE WHEN `status_code` = 'PUBLISHED' THEN 1 ELSE NULL END
  ) STORED,
  `version` int unsigned NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_srv_inspection_revision_rule_no` (`tenant_id`, `rule_id`, `revision_no`),
  UNIQUE KEY `uk_srv_inspection_revision_current` (`tenant_id`, `rule_id`, `current_published_marker`),
  UNIQUE KEY `uk_srv_inspection_revision_tenant_id` (`tenant_id`, `id`),
  KEY `idx_srv_inspection_revision_filter` (`tenant_id`, `status_code`, `category_code`, `severity_code`, `sort_order`, `id`),
  CONSTRAINT `fk_srv_inspection_revision_rule`
    FOREIGN KEY (`tenant_id`, `rule_id`, `rule_name_snapshot`)
    REFERENCES `srv_inspection_rule` (`tenant_id`, `id`, `rule_name`),
  CONSTRAINT `chk_srv_inspection_revision_no` CHECK (`revision_no` > 0),
  CONSTRAINT `chk_srv_inspection_revision_status` CHECK (`status_code` IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
  CONSTRAINT `chk_srv_inspection_revision_category` CHECK (`category_code` IS NULL OR `category_code` IN ('BASIC', 'OPERATING_STATUS', 'LOG', 'BUSINESS_STATUS', 'REDUNDANCY', 'ROUTING', 'SECURITY', 'FORWARDING_CHANNEL', 'LOAD_BALANCING', 'TRAFFIC_CLEANING')),
  CONSTRAINT `chk_srv_inspection_revision_severity` CHECK (`severity_code` IS NULL OR `severity_code` IN ('GENERAL', 'SEVERE', 'FATAL')),
  CONSTRAINT `chk_srv_inspection_revision_threshold_type` CHECK (`threshold_data_type` IS NULL OR `threshold_data_type` = 'NUMBER'),
  CONSTRAINT `chk_srv_inspection_revision_threshold_operator` CHECK (`threshold_operator` IS NULL OR `threshold_operator` IN ('>', '<', '≥', '≤', '=', '≠')),
  CONSTRAINT `chk_srv_inspection_revision_state_facts` CHECK (
    (`status_code` = 'DRAFT' AND `published_by` IS NULL AND `published_at` IS NULL AND `disabled_by` IS NULL AND `disabled_at` IS NULL)
    OR (`status_code` = 'PUBLISHED' AND `published_by` IS NOT NULL AND `published_at` IS NOT NULL AND `disabled_by` IS NULL AND `disabled_at` IS NULL)
    OR (`status_code` = 'DISABLED' AND `published_by` IS NOT NULL AND `published_at` IS NOT NULL AND `disabled_by` IS NOT NULL AND `disabled_at` IS NOT NULL)
  ),
  CONSTRAINT `chk_srv_inspection_revision_publish_complete` CHECK (
    `status_code` = 'DRAFT'
    OR (`rule_name_snapshot` IS NOT NULL AND `rule_name_snapshot` <> ''
      AND `inspection_item` IS NOT NULL AND `inspection_item` <> ''
      AND `description` IS NOT NULL AND `description` <> ''
      AND `category_code` IS NOT NULL AND `category_name_snapshot` IS NOT NULL AND `category_name_snapshot` <> ''
      AND `severity_code` IS NOT NULL AND `severity_name_snapshot` IS NOT NULL AND `severity_name_snapshot` <> ''
      AND `sort_order` IS NOT NULL
      AND `expected_result_regex` IS NOT NULL AND `expected_result_regex` <> ''
      AND `threshold_data_type` = 'NUMBER'
      AND `threshold_operator` IS NOT NULL
      AND `threshold_value` IS NOT NULL
      AND `threshold_unit` IS NOT NULL AND `threshold_unit` <> '')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `srv_inspection_rule_command_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `revision_id` bigint NOT NULL,
  `stable_command_key` varchar(96) DEFAULT NULL,
  `command_content` text DEFAULT NULL,
  `execution_order` int unsigned DEFAULT NULL,
  `timeout_seconds` tinyint unsigned DEFAULT NULL,
  `continue_on_timeout` bit(1) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_srv_inspection_command_key` (`tenant_id`, `revision_id`, `stable_command_key`),
  UNIQUE KEY `uk_srv_inspection_command_order` (`tenant_id`, `revision_id`, `execution_order`),
  KEY `idx_srv_inspection_command_revision` (`tenant_id`, `revision_id`, `execution_order`, `id`),
  CONSTRAINT `fk_srv_inspection_command_revision`
    FOREIGN KEY (`tenant_id`, `revision_id`) REFERENCES `srv_inspection_rule_revision` (`tenant_id`, `id`),
  CONSTRAINT `chk_srv_inspection_command_order` CHECK (`execution_order` IS NULL OR `execution_order` > 0),
  CONSTRAINT `chk_srv_inspection_command_timeout` CHECK (`timeout_seconds` IS NULL OR `timeout_seconds` BETWEEN 1 AND 30),
  CONSTRAINT `chk_srv_inspection_command_key` CHECK (`stable_command_key` IS NULL OR `stable_command_key` <> ''),
  CONSTRAINT `chk_srv_inspection_command_content` CHECK (`command_content` IS NULL OR `command_content` <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `srv_inspection_rule_product_type_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `revision_id` bigint NOT NULL,
  `product_type_code` varchar(64) DEFAULT NULL,
  `product_type_name_snapshot` varchar(128) DEFAULT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_srv_inspection_product_type` (`tenant_id`, `revision_id`, `product_type_code`),
  KEY `idx_srv_inspection_product_type_code` (`tenant_id`, `product_type_code`, `revision_id`, `id`),
  CONSTRAINT `fk_srv_inspection_product_type_revision`
    FOREIGN KEY (`tenant_id`, `revision_id`) REFERENCES `srv_inspection_rule_revision` (`tenant_id`, `id`),
  CONSTRAINT `chk_srv_inspection_product_type_code` CHECK (`product_type_code` IS NULL OR `product_type_code` <> ''),
  CONSTRAINT `chk_srv_inspection_product_type_name` CHECK (`product_type_name_snapshot` IS NULL OR `product_type_name_snapshot` <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `srv_inspection_rule_security_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_reference` varchar(128) NOT NULL,
  `revision_id` bigint NOT NULL,
  `content_digest` char(64) NOT NULL,
  `reviewed_by` bigint NOT NULL,
  `permission_code` varchar(128) NOT NULL,
  `authorization_type` varchar(32) NOT NULL,
  `authorization_source_id` varchar(128) DEFAULT NULL,
  `conclusion_code` varchar(16) NOT NULL,
  `reviewed_at` datetime(3) NOT NULL,
  `version` int unsigned NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_srv_inspection_review_reference` (`tenant_id`, `review_reference`),
  KEY `idx_srv_inspection_review_revision_digest` (`tenant_id`, `revision_id`, `content_digest`, `reviewed_at`, `id`),
  CONSTRAINT `fk_srv_inspection_review_revision`
    FOREIGN KEY (`tenant_id`, `revision_id`) REFERENCES `srv_inspection_rule_revision` (`tenant_id`, `id`),
  CONSTRAINT `chk_srv_inspection_review_digest` CHECK (CHAR_LENGTH(`content_digest`) = 64 AND `content_digest` REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT `chk_srv_inspection_review_permission` CHECK (`permission_code` = 'pms:inspection-rule:security-review'),
  CONSTRAINT `chk_srv_inspection_review_authorization_type` CHECK (`authorization_type` = 'RBAC_PERMISSION'),
  CONSTRAINT `chk_srv_inspection_review_conclusion` CHECK (`conclusion_code` IN ('PASSED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
