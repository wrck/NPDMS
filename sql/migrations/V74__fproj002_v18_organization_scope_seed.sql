-- F-PROJ-002 V1.8：补齐真实浏览器拆分验收所需的基础平台组织范围。
-- 公司与部门只引用V65已存在的权威示例，不在项目域臆造主数据。

UPDATE `proj_project` p
JOIN `system_company` c ON c.`code`='DPTECH-DEMO' AND c.`deleted`=b'0'
JOIN `system_dept` d ON d.`code`='OFFICE-HZ-DEMO' AND d.`deleted`=b'0'
SET p.`company_id`=c.`id`, p.`company_code`=c.`code`, p.`company_name`=c.`name`,
    p.`department_id`=d.`id`, p.`department_code`=d.`code`, p.`department_name`=d.`name`,
    p.`updater`='seed', p.`update_time`=NOW()
WHERE p.`tenant_id`=0 AND p.`id`=992002000000 AND p.`deleted`=b'0';

INSERT INTO `proj_project_company_department_relation`
  (`id`, `project_id`, `company_id`, `company_code`, `company_name`, `department_id`,
   `department_code`, `department_name`, `relation_role`, `is_primary`, `effective_from`,
   `effective_to`, `status`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 992002600003, 992002000000, c.`id`, c.`code`, c.`name`, d.`id`, d.`code`, d.`name`,
       'ORDER_OFFICE', 1, '2026-08-01 00:00:00', NULL, 'ACTIVE', 0, 'seed', 'seed', b'0', 0
FROM `system_company` c
JOIN `system_dept` d ON d.`code`='OFFICE-HZ-DEMO' AND d.`deleted`=b'0'
WHERE c.`code`='DPTECH-DEMO' AND c.`deleted`=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `proj_project_company_department_relation` r
    WHERE r.`tenant_id`=0 AND r.`project_id`=992002000000
      AND r.`relation_role`='ORDER_OFFICE' AND r.`effective_to` IS NULL
      AND r.`is_primary`=1 AND r.`deleted`=b'0'
  );

INSERT INTO `system_user_company_department_scope`
  (`id`, `tenant_id`, `user_id`, `company_id`, `company_code`, `company_name`, `department_id`,
   `department_code`, `department_name`, `scope_role`, `is_primary`, `effective_from`,
   `effective_to`, `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 992002600004, 1, 1, c.`id`, c.`code`, c.`name`, d.`id`, d.`code`, d.`name`,
       'PROJECT_MANAGER', b'1', '2026-08-01 00:00:00', NULL, 0, 0, 'seed', 'seed', b'0'
FROM `system_company` c
JOIN `system_dept` d ON d.`code`='OFFICE-HZ-DEMO' AND d.`deleted`=b'0'
WHERE c.`code`='DPTECH-DEMO' AND c.`deleted`=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_user_company_department_scope` s
    WHERE s.`user_id`=1 AND s.`company_id`=c.`id` AND s.`department_id`=d.`id`
      AND s.`status`=0 AND s.`effective_to` IS NULL AND s.`deleted`=b'0'
  );
