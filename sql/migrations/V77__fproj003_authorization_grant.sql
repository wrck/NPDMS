CREATE TABLE `plt_authorization_grant` (
    `id` bigint NOT NULL,
    `subject_type_code` varchar(32) NOT NULL,
    `subject_id` bigint NOT NULL,
    `resource_context_code` varchar(32) NOT NULL,
    `resource_type_code` varchar(32) NOT NULL,
    `resource_id` bigint NOT NULL,
    `action_code` varchar(64) NOT NULL,
    `scope_code` varchar(64) NOT NULL,
    `effective_from` datetime NOT NULL,
    `effective_to` datetime DEFAULT NULL,
    `status_code` varchar(32) NOT NULL,
    `source_context_code` varchar(32) NOT NULL,
    `source_object_type` varchar(64) DEFAULT NULL,
    `source_object_id` varchar(128) DEFAULT NULL,
    `granted_by` bigint NOT NULL,
    `granted_at` datetime NOT NULL,
    `revoked_by` bigint DEFAULT NULL,
    `revoked_at` datetime DEFAULT NULL,
    `revoke_reason` varchar(500) DEFAULT NULL,
    `version` int NOT NULL DEFAULT 0,
    `current_marker` tinyint DEFAULT 1,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plt_authorization_grant_current`
        (`tenant_id`,`subject_type_code`,`subject_id`,`resource_context_code`,
         `resource_type_code`,`resource_id`,`action_code`,`scope_code`,`current_marker`),
    KEY `idx_plt_authorization_grant_subject`
        (`tenant_id`,`subject_type_code`,`subject_id`,`status_code`,`effective_from`,`effective_to`),
    KEY `idx_plt_authorization_grant_resource`
        (`tenant_id`,`resource_context_code`,`resource_type_code`,`resource_id`,
         `action_code`,`status_code`,`effective_from`,`effective_to`),
    CONSTRAINT `ck_plt_authorization_grant_interval`
        CHECK (`effective_to` IS NULL OR `effective_to` > `effective_from`),
    CONSTRAINT `ck_plt_authorization_grant_current_marker`
        CHECK (`current_marker` IS NULL OR `current_marker` = 1),
    CONSTRAINT `ck_plt_authorization_grant_status`
        CHECK (`status_code` IN ('ACTIVE','REVOKED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台通用授权事实';
