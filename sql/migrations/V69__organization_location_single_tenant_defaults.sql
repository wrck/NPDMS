-- V69: V1 单租户运行时不启用租户拦截，新增组织与地点表需承接当前租户 1。
-- 启用租户拦截后，MyBatis Plus 会显式写入 tenant_id，此默认值不参与多租户写入。

ALTER TABLE `system_company`
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 1;

ALTER TABLE `system_user_company_department_scope`
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 1;

ALTER TABLE `ast_address`
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 1;

ALTER TABLE `ast_site`
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 1;

ALTER TABLE `ast_site_location`
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 1;

ALTER TABLE `ast_location_source_mapping`
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 1;

ALTER TABLE `ast_area_department_mapping`
    MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 1;
