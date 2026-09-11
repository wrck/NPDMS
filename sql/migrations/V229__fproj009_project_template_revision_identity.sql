-- F-PROJ-009 / PM-03
-- Freeze the exact lifecycle template revision identity on the project master.
-- Historical backfill is limited to an unambiguous (tenant, template, revisionNo) identity;
-- no latest/status/sort inference is allowed.

ALTER TABLE `proj_project`
    ADD COLUMN `lifecycle_template_revision_id` BIGINT NULL
        COMMENT '创建时冻结的生命周期模板修订ID；仅来源追溯/冻结身份，不作为运行时设计态反查入口'
        AFTER `lifecycle_template_revision_no`,
    ADD KEY `idx_project_lifecycle_template_revision` (`tenant_id`, `lifecycle_template_revision_id`);

UPDATE `proj_project` p
JOIN (
    SELECT `tenant_id`, `template_id`, `revision_no`, MIN(`id`) AS `revision_id`
    FROM `proj_project_template_revision`
    GROUP BY `tenant_id`, `template_id`, `revision_no`
    HAVING COUNT(*) = 1
) r
  ON r.`tenant_id` = p.`tenant_id`
 AND r.`template_id` = p.`lifecycle_template_id`
 AND r.`revision_no` = p.`lifecycle_template_revision_no`
SET p.`lifecycle_template_revision_id` = r.`revision_id`,
    p.`updater` = 'V229',
    p.`update_time` = NOW()
WHERE p.`lifecycle_template_revision_id` IS NULL
  AND p.`lifecycle_template_id` IS NOT NULL
  AND p.`lifecycle_template_revision_no` IS NOT NULL;
