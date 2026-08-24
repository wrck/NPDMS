CREATE TABLE `proj_project_split_request` (
    `id` bigint NOT NULL, `parent_project_id` bigint NOT NULL,
    `status` varchar(16) NOT NULL, `draft_version` int NOT NULL DEFAULT 0,
    `parent_version` int DEFAULT NULL, `scope_version` bigint DEFAULT NULL,
    `tree_version` bigint DEFAULT NULL, `template_revision_id` bigint DEFAULT NULL,
    `preview_hash` varchar(64) DEFAULT NULL, `validation_status` varchar(32) DEFAULT NULL,
    `validation_summary` json DEFAULT NULL, `validated_at` datetime DEFAULT NULL,
    `applied_change_batch_id` varchar(64) DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_proj_split_request_parent` (`tenant_id`,`parent_project_id`,`status`,`update_time`,`id`),
    CONSTRAINT `ck_proj_split_request_status` CHECK (`status` IN ('DRAFT','APPLIED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目拆分申请';

CREATE TABLE `proj_project_split_item` (
    `id` bigint NOT NULL, `split_request_id` bigint NOT NULL, `client_item_key` varchar(64) NOT NULL,
    `project_name` varchar(255) NOT NULL, `business_level_code` varchar(64) DEFAULT NULL,
    `tree_sort` int NOT NULL DEFAULT 0, `office_department_code` varchar(64) DEFAULT NULL,
    `item_status` varchar(32) NOT NULL, `validation_result` json DEFAULT NULL, `created_project_id` bigint DEFAULT NULL,
    `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_split_item_client` (`tenant_id`,`split_request_id`,`client_item_key`),
    KEY `idx_proj_split_item_request` (`tenant_id`,`split_request_id`,`tree_sort`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目拆分方案项';

CREATE TABLE `proj_project_split_scope` (
    `id` bigint NOT NULL, `split_item_id` bigint NOT NULL, `order_line_id` bigint NOT NULL,
    `allocated_qty` decimal(18,6) NOT NULL, `office_department_code` varchar(64) DEFAULT NULL,
    `serial_no` varchar(128) DEFAULT NULL, `source_scope_version` bigint NOT NULL,
    `source_snapshot` json DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_split_scope_dimension` (`tenant_id`,`split_item_id`,`order_line_id`,`office_department_code`,`serial_no`),
    KEY `idx_proj_split_scope_item` (`tenant_id`,`split_item_id`,`id`),
    CONSTRAINT `ck_proj_split_scope_quantity` CHECK (`allocated_qty` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目拆分范围发生时快照';

CREATE TABLE `proj_project_tree_version` (
    `id` bigint NOT NULL, `root_project_id` bigint NOT NULL, `tree_version` bigint NOT NULL,
    `status` varchar(16) NOT NULL, `change_batch_id` varchar(64) NOT NULL,
    `node_count` int NOT NULL DEFAULT 0, `path_count` int NOT NULL DEFAULT 0,
    `activated_at` datetime DEFAULT NULL, `failed_reason` varchar(500) DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_tree_version` (`tenant_id`,`root_project_id`,`tree_version`),
    KEY `idx_proj_tree_version_active` (`tenant_id`,`root_project_id`,`status`,`tree_version`),
    CONSTRAINT `ck_proj_tree_version_status` CHECK (`status` IN ('BUILDING','ACTIVE','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目树完整版本';

CREATE TABLE `proj_project_tree_path` (
    `id` bigint NOT NULL, `tree_version` bigint NOT NULL, `root_project_id` bigint NOT NULL,
    `ancestor_project_id` bigint NOT NULL, `descendant_project_id` bigint NOT NULL, `distance` int NOT NULL,
    `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_tree_path` (`tenant_id`,`root_project_id`,`tree_version`,`ancestor_project_id`,`descendant_project_id`),
    KEY `idx_proj_tree_path_ancestor` (`tenant_id`,`root_project_id`,`tree_version`,`ancestor_project_id`,`distance`,`descendant_project_id`),
    KEY `idx_proj_tree_path_descendant` (`tenant_id`,`root_project_id`,`tree_version`,`descendant_project_id`,`ancestor_project_id`),
    CONSTRAINT `ck_proj_tree_path_distance` CHECK (`distance` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目树祖先路径投影';

CREATE TABLE `proj_project_tree_change` (
    `id` bigint NOT NULL, `change_batch_id` varchar(64) NOT NULL, `operation_type` varchar(32) NOT NULL,
    `project_id` bigint NOT NULL, `parent_id_before` bigint DEFAULT NULL, `parent_id_after` bigint DEFAULT NULL,
    `base_tree_version` bigint NOT NULL, `new_tree_version` bigint NOT NULL,
    `actor_id` bigint NOT NULL, `reason` varchar(500) DEFAULT NULL, `occurred_at` datetime NOT NULL,
    `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_tree_change_batch` (`tenant_id`,`change_batch_id`,`project_id`),
    KEY `idx_proj_tree_change_project` (`tenant_id`,`project_id`,`occurred_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目树追加变更';

CREATE TABLE `proj_project_progress_fact` (
    `id` bigint NOT NULL, `project_id` bigint NOT NULL, `fact_source_type` varchar(32) NOT NULL,
    `fact_source_id` varchar(128) NOT NULL, `fact_version` bigint NOT NULL, `progress` decimal(7,4) NOT NULL,
    `source_watermark` varchar(128) NOT NULL, `occurred_at` datetime NOT NULL,
    `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_progress_fact` (`tenant_id`,`project_id`,`fact_source_type`,`fact_source_id`,`fact_version`),
    KEY `idx_proj_progress_fact_latest` (`tenant_id`,`project_id`,`occurred_at`,`id`),
    CONSTRAINT `ck_proj_progress_fact_value` CHECK (`progress` >= 0 AND `progress` <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目进度追加事实';

CREATE TABLE `proj_project_progress_policy_revision` (
    `id` bigint NOT NULL, `parent_project_id` bigint NOT NULL, `revision_no` int NOT NULL,
    `status` varchar(16) NOT NULL, `policy_type` varchar(32) NOT NULL,
    `process_definition_key` varchar(128) DEFAULT NULL, `process_instance_id` varchar(64) DEFAULT NULL,
    `effective_from` datetime DEFAULT NULL, `effective_to` datetime DEFAULT NULL,
    `approved_by` bigint DEFAULT NULL, `approved_at` datetime DEFAULT NULL, `supersedes_revision_id` bigint DEFAULT NULL,
    `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_progress_policy_revision` (`tenant_id`,`parent_project_id`,`revision_no`),
    UNIQUE KEY `uk_proj_progress_policy_process` (`tenant_id`,`process_instance_id`),
    KEY `idx_proj_progress_policy_active` (`tenant_id`,`parent_project_id`,`status`,`effective_from`,`effective_to`),
    CONSTRAINT `ck_proj_progress_policy_status` CHECK (`status` IN ('DRAFT','APPROVING','ACTIVE','REJECTED','SUPERSEDED')),
    CONSTRAINT `ck_proj_progress_policy_range` CHECK (`effective_to` IS NULL OR `effective_from` IS NULL OR `effective_to` >= `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目进度策略版本';

CREATE TABLE `proj_project_progress_policy_item` (
    `id` bigint NOT NULL, `policy_revision_id` bigint NOT NULL, `child_project_id` bigint NOT NULL,
    `weight` decimal(7,4) NOT NULL, `include_status_snapshot` json NOT NULL,
    `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_progress_policy_item` (`tenant_id`,`policy_revision_id`,`child_project_id`),
    CONSTRAINT `ck_proj_progress_policy_weight` CHECK (`weight` >= 0 AND `weight` <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目进度策略权重项';

CREATE TABLE `proj_project_progress_snapshot` (
    `id` bigint NOT NULL, `project_id` bigint NOT NULL, `policy_revision_id` bigint NOT NULL,
    `tree_version` bigint NOT NULL, `source_watermark` varchar(128) NOT NULL,
    `snapshot_status` varchar(16) NOT NULL, `progress` decimal(7,4) DEFAULT NULL,
    `missing_item_count` int NOT NULL DEFAULT 0, `calculated_at` datetime NOT NULL,
    `version` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_progress_snapshot` (`tenant_id`,`project_id`,`policy_revision_id`,`tree_version`,`source_watermark`),
    KEY `idx_proj_progress_snapshot_latest` (`tenant_id`,`project_id`,`calculated_at`,`id`),
    CONSTRAINT `ck_proj_progress_snapshot_status` CHECK (`snapshot_status` IN ('READY','PENDING')),
    CONSTRAINT `ck_proj_progress_snapshot_value` CHECK (`progress` IS NULL OR (`progress` >= 0 AND `progress` <= 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目进度不可变快照';

CREATE TABLE `proj_project_progress_snapshot_detail` (
    `id` bigint NOT NULL, `snapshot_id` bigint NOT NULL, `child_project_id` bigint NOT NULL,
    `fact_version` bigint DEFAULT NULL, `child_progress` decimal(7,4) DEFAULT NULL,
    `normalized_weight` decimal(7,4) NOT NULL, `contribution` decimal(7,4) DEFAULT NULL,
    `missing_reason` varchar(128) DEFAULT NULL, `version` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_proj_progress_snapshot_detail` (`tenant_id`,`snapshot_id`,`child_project_id`),
    KEY `idx_proj_progress_snapshot_detail` (`tenant_id`,`snapshot_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目进度快照解释明细';
