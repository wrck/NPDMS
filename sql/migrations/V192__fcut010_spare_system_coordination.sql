-- F-CUT-010 CUT-08@V2 备件系统协同。仅建立CUT侧引用、状态版本和人工证据；不承载外部备件生命周期。

CREATE TABLE `cut_spare_application_reference` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `cutover_task_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `platform_request_id` varchar(128) NOT NULL,
  `integration_status` varchar(32) NOT NULL,
  `external_system_code` varchar(64) NOT NULL,
  `external_request_id` varchar(128) DEFAULT NULL,
  `external_application_no` varchar(128) DEFAULT NULL,
  `launch_url` varchar(2048) DEFAULT NULL,
  `need_snapshot` json NOT NULL,
  `request_context_snapshot` json NOT NULL,
  `current_status_revision_id` bigint DEFAULT NULL,
  `retry_count` int NOT NULL,
  `last_failure_code` varchar(64) DEFAULT NULL,
  `last_failure_detail` varchar(1000) DEFAULT NULL,
  `last_attempt_at` datetime(3) DEFAULT NULL,
  `version` int NOT NULL,
  `creator` varchar(64) NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `updater` varchar(64) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_spare_platform_request` (`tenant_id`,`platform_request_id`),
  UNIQUE KEY `uk_cut_spare_external_request` (`tenant_id`,`external_system_code`,`external_request_id`),
  UNIQUE KEY `uk_cut_spare_external_application` (`tenant_id`,`external_system_code`,`external_application_no`),
  KEY `idx_cut_spare_task` (`tenant_id`,`cutover_task_id`,`id`),
  KEY `idx_cut_spare_retry` (`tenant_id`,`integration_status`,`last_attempt_at`,`id`),
  CONSTRAINT `fk_cut_spare_application_task` FOREIGN KEY (`cutover_task_id`) REFERENCES `cut_task` (`id`),
  CONSTRAINT `chk_cut_spare_application_status` CHECK (`integration_status` IN ('REQUEST_PENDING','EXTERNAL_REFERENCED','RETRY_PENDING')),
  CONSTRAINT `chk_cut_spare_application_version` CHECK (`retry_count` >= 0 AND `version` >= 0),
  CONSTRAINT `chk_cut_spare_application_json` CHECK (JSON_TYPE(`need_snapshot`)='OBJECT' AND JSON_TYPE(`request_context_snapshot`)='OBJECT'),
  CONSTRAINT `chk_cut_spare_platform_request_text` CHECK (CHAR_LENGTH(`platform_request_id`) BETWEEN 1 AND 128 AND CHAR_LENGTH(`platform_request_id`)=CHAR_LENGTH(TRIM(`platform_request_id`))),
  CONSTRAINT `chk_cut_spare_external_system_text` CHECK (CHAR_LENGTH(`external_system_code`) BETWEEN 1 AND 64 AND CHAR_LENGTH(`external_system_code`)=CHAR_LENGTH(TRIM(`external_system_code`))),
  CONSTRAINT `chk_cut_spare_external_request_text` CHECK (`external_request_id` IS NULL OR (CHAR_LENGTH(`external_request_id`) BETWEEN 1 AND 128 AND CHAR_LENGTH(`external_request_id`)=CHAR_LENGTH(TRIM(`external_request_id`)))),
  CONSTRAINT `chk_cut_spare_external_application_text` CHECK (`external_application_no` IS NULL OR (CHAR_LENGTH(`external_application_no`) BETWEEN 1 AND 128 AND CHAR_LENGTH(`external_application_no`)=CHAR_LENGTH(TRIM(`external_application_no`)))),
  CONSTRAINT `chk_cut_spare_launch_url_text` CHECK (`launch_url` IS NULL OR (CHAR_LENGTH(`launch_url`) BETWEEN 1 AND 2048 AND CHAR_LENGTH(`launch_url`)=CHAR_LENGTH(TRIM(`launch_url`)))),
  CONSTRAINT `chk_cut_spare_external_result` CHECK (`external_request_id` IS NULL OR (`external_application_no` IS NOT NULL OR `launch_url` IS NOT NULL)),
  CONSTRAINT `chk_cut_spare_external_binding` CHECK (`external_application_no` IS NULL OR `external_request_id` IS NOT NULL),
  CONSTRAINT `chk_cut_spare_referenced_status` CHECK (`integration_status` <> 'EXTERNAL_REFERENCED' OR (`external_request_id` IS NOT NULL AND `external_application_no` IS NOT NULL)),
  CONSTRAINT `chk_cut_spare_pending_binding` CHECK (`integration_status` <> 'REQUEST_PENDING' OR `external_application_no` IS NULL),
  CONSTRAINT `chk_cut_spare_application_deleted` CHECK (`deleted`=b'0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-010备件申请外部引用';

CREATE TABLE `cut_spare_status_revision` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `application_reference_id` bigint NOT NULL,
  `status_version` bigint NOT NULL,
  `external_status_raw` varchar(128) NOT NULL,
  `status_snapshot` json NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `observed_at` datetime(3) NOT NULL,
  `external_occurred_at` datetime(3) DEFAULT NULL,
  `event_id` varchar(128) NOT NULL,
  `correlation_id` varchar(128) NOT NULL,
  `current_marker` tinyint DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_spare_status_version` (`tenant_id`,`application_reference_id`,`status_version`),
  UNIQUE KEY `uk_cut_spare_status_event` (`tenant_id`,`event_id`),
  UNIQUE KEY `uk_cut_spare_status_current` (`tenant_id`,`application_reference_id`,`current_marker`),
  KEY `idx_cut_spare_status_order` (`tenant_id`,`application_reference_id`,`status_version`,`id`),
  CONSTRAINT `fk_cut_spare_status_application` FOREIGN KEY (`application_reference_id`) REFERENCES `cut_spare_application_reference` (`id`),
  CONSTRAINT `chk_cut_spare_status_values` CHECK (`status_version` > 0 AND `created_by` >= 0),
  CONSTRAINT `chk_cut_spare_status_source` CHECK (`source_type` IN ('INITIATE_RESPONSE','CALLBACK','REFRESH')),
  CONSTRAINT `chk_cut_spare_status_marker` CHECK (`current_marker` IS NULL OR `current_marker`=1),
  CONSTRAINT `chk_cut_spare_status_raw` CHECK (CHAR_LENGTH(`external_status_raw`) BETWEEN 1 AND 128 AND CHAR_LENGTH(`external_status_raw`)=CHAR_LENGTH(TRIM(`external_status_raw`))),
  CONSTRAINT `chk_cut_spare_status_event` CHECK (CHAR_LENGTH(`event_id`) BETWEEN 1 AND 128 AND CHAR_LENGTH(`event_id`)=CHAR_LENGTH(TRIM(`event_id`))),
  CONSTRAINT `chk_cut_spare_status_correlation` CHECK (CHAR_LENGTH(`correlation_id`) BETWEEN 1 AND 128 AND CHAR_LENGTH(`correlation_id`)=CHAR_LENGTH(TRIM(`correlation_id`))),
  CONSTRAINT `chk_cut_spare_status_snapshot` CHECK (JSON_TYPE(`status_snapshot`)='OBJECT' AND OCTET_LENGTH(CAST(`status_snapshot` AS CHAR CHARACTER SET utf8mb4)) <= 16384)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-010外部备件状态不可变版本';

CREATE TABLE `cut_spare_manual_evidence` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `cutover_task_id` bigint NOT NULL,
  `application_reference_id` bigint DEFAULT NULL,
  `file_artifact_id` bigint NOT NULL,
  `file_reference_key` varchar(128) NOT NULL,
  `file_version_no` int NOT NULL,
  `file_fact_version` json NOT NULL,
  `file_scope_version` bigint NOT NULL,
  `description` varchar(1000) NOT NULL,
  `uploaded_by` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cut_spare_manual_file` (`tenant_id`,`cutover_task_id`,`file_artifact_id`,`file_version_no`),
  KEY `idx_cut_spare_manual_task` (`tenant_id`,`cutover_task_id`,`id`),
  KEY `idx_cut_spare_manual_application` (`tenant_id`,`application_reference_id`,`id`),
  CONSTRAINT `fk_cut_spare_manual_task` FOREIGN KEY (`cutover_task_id`) REFERENCES `cut_task` (`id`),
  CONSTRAINT `fk_cut_spare_manual_application` FOREIGN KEY (`application_reference_id`) REFERENCES `cut_spare_application_reference` (`id`),
  CONSTRAINT `chk_cut_spare_manual_values` CHECK (`file_artifact_id` > 0 AND `file_version_no` > 0 AND `file_scope_version` >= 0 AND `uploaded_by` > 0),
  CONSTRAINT `chk_cut_spare_manual_reference` CHECK (CHAR_LENGTH(`file_reference_key`) BETWEEN 1 AND 128 AND CHAR_LENGTH(`file_reference_key`)=CHAR_LENGTH(TRIM(`file_reference_key`))),
  CONSTRAINT `chk_cut_spare_manual_description` CHECK (CHAR_LENGTH(`description`) BETWEEN 1 AND 1000 AND CHAR_LENGTH(`description`)=CHAR_LENGTH(TRIM(`description`))),
  CONSTRAINT `chk_cut_spare_manual_file_fact` CHECK (
    JSON_TYPE(`file_fact_version`)='OBJECT'
    AND JSON_LENGTH(`file_fact_version`)=3
    AND JSON_CONTAINS_PATH(`file_fact_version`,'all','$.artifactVersion','$.referenceVersion','$.availabilityVersion')
    AND JSON_TYPE(JSON_EXTRACT(`file_fact_version`,'$.artifactVersion'))='INTEGER'
    AND JSON_TYPE(JSON_EXTRACT(`file_fact_version`,'$.referenceVersion'))='INTEGER'
    AND JSON_TYPE(JSON_EXTRACT(`file_fact_version`,'$.availabilityVersion'))='INTEGER'
    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`file_fact_version`,'$.artifactVersion')) AS SIGNED) >= 0
    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`file_fact_version`,'$.referenceVersion')) AS SIGNED) >= 0
    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`file_fact_version`,'$.availabilityVersion')) AS SIGNED) >= 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='F-CUT-010人工备件证据不可变引用';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992602090001, '管理备件协同', 'pms:cutover-task:manage-spare', 3, 150, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
 `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`),
 `icon`=VALUES(`icon`), `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
 `status`=0, `visible`=b'1', `keep_alive`=VALUES(`keep_alive`),
 `always_show`=VALUES(`always_show`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';
