-- V1.8 工勘、安装与设备当前位置统一事实

ALTER TABLE `ast_location_source_mapping`
    ADD COLUMN `site_location_id` bigint NULL AFTER `site_id`;

ALTER TABLE `pms_eng_site_survey`
    ADD COLUMN `address_id` bigint NULL AFTER `location`,
    ADD COLUMN `address_version` int unsigned NULL AFTER `address_id`,
    ADD COLUMN `site_id` bigint NULL AFTER `address_version`,
    ADD COLUMN `site_version` int unsigned NULL AFTER `site_id`,
    ADD COLUMN `site_location_id` bigint NULL AFTER `site_version`,
    ADD COLUMN `site_location_version` int unsigned NULL AFTER `site_location_id`,
    ADD COLUMN `location_resolution_status` varchar(16) NOT NULL DEFAULT 'UNRESOLVED' AFTER `site_location_version`,
    ADD COLUMN `address_snapshot` text NULL AFTER `location_resolution_status`,
    ADD COLUMN `location_snapshot` text NULL AFTER `address_snapshot`,
    ADD CONSTRAINT `chk_pms_eng_site_survey_location_resolution`
        CHECK (`location_resolution_status` IN ('UNRESOLVED', 'RESOLVED'));

ALTER TABLE `pms_eng_installation`
    ADD COLUMN `address_id` bigint NULL AFTER `install_location`,
    ADD COLUMN `address_version` int unsigned NULL AFTER `address_id`,
    ADD COLUMN `site_id` bigint NULL AFTER `address_version`,
    ADD COLUMN `site_version` int unsigned NULL AFTER `site_id`,
    ADD COLUMN `site_location_id` bigint NULL AFTER `site_version`,
    ADD COLUMN `site_location_version` int unsigned NULL AFTER `site_location_id`,
    ADD COLUMN `location_resolution_status` varchar(16) NOT NULL DEFAULT 'UNRESOLVED' AFTER `site_location_version`,
    ADD COLUMN `address_snapshot` text NULL AFTER `location_resolution_status`,
    ADD COLUMN `location_snapshot` text NULL AFTER `address_snapshot`,
    ADD COLUMN `effective_from` datetime(3) NULL AFTER `location_snapshot`,
    ADD COLUMN `effective_to` datetime(3) NULL AFTER `effective_from`,
    ADD CONSTRAINT `chk_pms_eng_installation_location_resolution`
        CHECK (`location_resolution_status` IN ('UNRESOLVED', 'RESOLVED'));

UPDATE `pms_eng_installation` target
JOIN (
    SELECT `id`,
           COALESCE(`install_time`, `create_time`) AS `effective_from_value`,
           LEAD(COALESCE(`install_time`, `create_time`)) OVER (
               PARTITION BY `tenant_id`, `equipment_id`
               ORDER BY COALESCE(`install_time`, `create_time`), `id`
           ) AS `effective_to_value`
    FROM `pms_eng_installation`
    WHERE `status` = 2 AND `deleted` = b'0'
) ordered ON ordered.`id` = target.`id`
SET target.`effective_from` = ordered.`effective_from_value`,
    target.`effective_to` = ordered.`effective_to_value`;

ALTER TABLE `pms_eng_installation`
    ADD COLUMN `current_equipment_id` bigint GENERATED ALWAYS AS (
        CASE WHEN `status` = 2 AND `effective_to` IS NULL AND `deleted` = b'0' THEN `equipment_id` ELSE NULL END
    ) STORED AFTER `effective_to`,
    ADD UNIQUE KEY `uk_pms_eng_installation_current_equipment` (`tenant_id`, `current_equipment_id`),
    ADD KEY `idx_pms_eng_installation_effective` (`tenant_id`, `equipment_id`, `effective_from`, `effective_to`);

ALTER TABLE `pms_equipment`
    ADD COLUMN `site_id` bigint NULL AFTER `location`,
    ADD COLUMN `site_location_id` bigint NULL AFTER `site_id`,
    ADD COLUMN `location_resolution_status` varchar(16) NOT NULL DEFAULT 'UNRESOLVED' AFTER `site_location_id`,
    ADD COLUMN `location_snapshot` text NULL AFTER `location_resolution_status`,
    ADD COLUMN `location_effective_from` datetime(3) NULL AFTER `location_snapshot`,
    ADD COLUMN `location_source_installation_id` bigint NULL AFTER `location_effective_from`,
    ADD UNIQUE KEY `uk_pms_equipment_location_source` (`tenant_id`, `location_source_installation_id`),
    ADD KEY `idx_pms_equipment_site_location` (`tenant_id`, `site_id`, `site_location_id`),
    ADD CONSTRAINT `chk_pms_equipment_location_resolution`
        CHECK (`location_resolution_status` IN ('UNRESOLVED', 'RESOLVED'));
