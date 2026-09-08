-- F-PLT-003 | Owner: PLT | Requirements: PM-03, PM-11
-- Source: docs/design/09-database-design.md (business view sources),
-- docs/traceability/sds-revision-016-physical-contract.json.
-- Candidate version only; master integration determines the final Flyway number.
-- No Provider/view seed: only a real, validated Owner API may publish a view.
-- Published content and historical references are not rewritten by this migration.

CREATE TABLE `plt_business_view_revision` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `entity_type` VARCHAR(64) NOT NULL,
    `view_key` VARCHAR(128) NOT NULL,
    `revision_no` BIGINT UNSIGNED NOT NULL,
    `owner_context` VARCHAR(32) NOT NULL,
    `view_source` VARCHAR(16) NOT NULL,
    `dynamic_form_revision_id` BIGINT NULL,
    `component_key` VARCHAR(128) NOT NULL,
    `component_version` VARCHAR(64) NOT NULL,
    `context_schema` JSON NOT NULL,
    `supported_actions` JSON NOT NULL,
    `query_provider_key` VARCHAR(128) NOT NULL,
    `command_provider_key` VARCHAR(128) NOT NULL,
    `permission_provider_key` VARCHAR(128) NOT NULL,
    `published_at` DATETIME(6) NULL,
    `disabled_at` DATETIME(6) NULL,
    `version` INT NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NOT NULL DEFAULT '',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updater` VARCHAR(64) NOT NULL DEFAULT '',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted` BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bvr_identity` (`tenant_id`, `entity_type`, `view_key`, `revision_no`),
    CONSTRAINT `ck_bvr_version` CHECK (revision_no > 0),
    CONSTRAINT `ck_bvr_actions` CHECK (JSON_TYPE(supported_actions)='ARRAY'),
    CONSTRAINT `ck_bvr_schema` CHECK (JSON_TYPE(context_schema)='OBJECT'),
    CONSTRAINT `ck_bvr_source` CHECK ((view_source='PAGE' AND dynamic_form_revision_id IS NULL) OR (view_source='DYNAMIC_FORM' AND dynamic_form_revision_id IS NOT NULL AND dynamic_form_revision_id > 0)),
    CONSTRAINT `ck_bvr_disabled` CHECK (disabled_at IS NULL OR (published_at IS NOT NULL AND disabled_at >= published_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- IDs 199800..199803: absent in master/p903 SQL and coordinator's read-only
-- fixed test DB check (including deleted rows); parent 19271 exists.
-- Follow the dynamic-form sibling menu convention. No role grants:
-- super_admin already obtains all menus/permissions through the platform policy.
-- Plain insertion intentionally fails on an unexpected collision, never overwrites it.
INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(199800, '业务视图注册', 'pms:business-view:query', 2, 50, 19271,
 'business-view', 'ep:window', 'pms/platform/business-view/index',
 'PmsBusinessView', 0, b'1', b'1', b'1', 'fplt003-seed', NOW(), 'fplt003-seed', NOW(), b'0'),
(199801, '业务视图管理', 'pms:business-view:manage', 3, 10, 199800,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'fplt003-seed', NOW(), 'fplt003-seed', NOW(), b'0'),
(199802, '业务视图发布', 'pms:business-view:publish', 3, 20, 199800,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'fplt003-seed', NOW(), 'fplt003-seed', NOW(), b'0'),
(199803, '业务视图停用', 'pms:business-view:disable', 3, 30, 199800,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'fplt003-seed', NOW(), 'fplt003-seed', NOW(), b'0');
