-- F-PROJ-005：单租户（tenant_id=0）浏览器闭环所需的最小组织候选数据。
-- 复用V68示例编码与名称，使用独立高段ID；候选账号密码复制未知口令哈希，不提供登录能力。

INSERT INTO `system_company`
  (`id`, `tenant_id`, `code`, `name`, `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 930850, 0, `code`, `name`, 0, 0, 'v85-fproj005', 'v85-fproj005', b'0'
FROM `system_company`
WHERE `id`=930800 AND `deleted`=b'0'
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `status`=0,
  `updater`='v85-fproj005', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_dept`
  (`id`, `code`, `name`, `parent_id`, `sort`, `status`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 930851, `code`, `name`, 0, `sort`, 0, 0, 'v85-fproj005', 'v85-fproj005', b'0', 0
FROM `system_dept`
WHERE `id`=930801 AND `deleted`=b'0'
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `status`=0,
  `updater`='v85-fproj005', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_users`
  (`id`, `username`, `password`, `nickname`, `dept_id`, `status`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992005000001, 'fproj005-sm-demo', `password`, 'F-PROJ-005示例服务经理', 930851, 0,
       'v85-fproj005', 'v85-fproj005', b'0', 0
FROM `system_users`
WHERE `id`=107 AND `deleted`=b'0'
ON DUPLICATE KEY UPDATE `nickname`=VALUES(`nickname`), `dept_id`=930851, `status`=0,
  `updater`='v85-fproj005', `update_time`=NOW(), `deleted`=b'0', `tenant_id`=0;

INSERT INTO `system_user_company_department_scope`
  (`id`, `tenant_id`, `user_id`, `company_id`, `company_code`, `company_name`, `department_id`,
   `department_code`, `department_name`, `scope_role`, `is_primary`, `effective_from`,
   `effective_to`, `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 992005000001, 0, 992005000001, c.`id`, c.`code`, c.`name`, d.`id`, d.`code`, d.`name`,
       'SERVICE_MANAGER', b'1', '2026-01-01 00:00:00.000', NULL, 0, 0,
       'v85-fproj005', 'v85-fproj005', b'0'
FROM `system_company` c
JOIN `system_dept` d ON d.`id`=930851 AND d.`tenant_id`=0 AND d.`deleted`=b'0'
WHERE c.`id`=930850 AND c.`tenant_id`=0 AND c.`deleted`=b'0'
ON DUPLICATE KEY UPDATE `company_id`=VALUES(`company_id`), `department_id`=VALUES(`department_id`),
  `department_code`=VALUES(`department_code`), `effective_to`=NULL, `status`=0,
  `updater`='v85-fproj005', `update_time`=NOW(), `deleted`=b'0';

UPDATE `proj_project`
SET `company_id`=930850, `department_id`=930851,
    `updater`='v85-fproj005', `update_time`=NOW()
WHERE `tenant_id`=0 AND `company_code`='DPTECH-DEMO' AND `department_code`='OFFICE-HZ-DEMO'
  AND `deleted`=b'0';

UPDATE `proj_project_company_department_relation`
SET `company_id`=930850, `department_id`=930851,
    `updater`='v85-fproj005', `update_time`=NOW()
WHERE `tenant_id`=0 AND `company_code`='DPTECH-DEMO' AND `department_code`='OFFICE-HZ-DEMO'
  AND `deleted`=b'0';
