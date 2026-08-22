ALTER TABLE `system_dept`
    ADD COLUMN `code` varchar(64) NULL COMMENT '统一部门编码' AFTER `id`,
    ADD COLUMN `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER `status`,
    ADD UNIQUE KEY `uk_system_dept_code` (`tenant_id`, `code`, `deleted`);

CREATE TABLE `system_company` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `code` varchar(64) NOT NULL,
    `name` varchar(128) NOT NULL,
    `status` tinyint NOT NULL,
    `version` int unsigned NOT NULL DEFAULT 0,
    `creator` varchar(64) NULL DEFAULT '',
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` varchar(64) NULL DEFAULT '',
    `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_company_code` (`tenant_id`, `code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司主数据';

CREATE TABLE `system_user_company_department_scope` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `company_id` bigint NOT NULL,
    `company_code` varchar(64) NOT NULL,
    `company_name` varchar(128) NOT NULL,
    `department_id` bigint NULL,
    `department_code` varchar(64) NULL,
    `department_name` varchar(128) NULL,
    `scope_role` varchar(32) NOT NULL,
    `is_primary` bit(1) NOT NULL DEFAULT b'0',
    `effective_from` datetime(3) NOT NULL,
    `effective_to` datetime(3) NULL,
    `status` tinyint NOT NULL,
    `version` int unsigned NOT NULL DEFAULT 0,
    `creator` varchar(64) NULL DEFAULT '',
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` varchar(64) NULL DEFAULT '',
    `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_user_org_scope_current` (`tenant_id`, `user_id`, `status`, `effective_to`),
    CONSTRAINT `chk_user_scope_department_pair` CHECK (
        (`department_id` IS NULL AND `department_code` IS NULL)
        OR (`department_id` IS NOT NULL AND `department_code` IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户公司部门有效范围';
