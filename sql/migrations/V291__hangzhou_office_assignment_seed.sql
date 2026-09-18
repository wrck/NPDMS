-- V291: 现有项目杭州办事处分配与人员组织范围初始化（用户 2026-09-18 确认口径）
-- 范围：tenant 1 全部启用项目补下单办事处；tenant 1 全部启用用户初始化杭州办事处组织范围授权。
--   tenant 0 的 41 个验收夹具项目（fproj005/fcom001 等自带关系或故意缺失）不动。
-- 组织口径：V68 种子 tenant 1 正牌记录——公司 930800 迪普科技示例公司（DPTECH-DEMO）、
--   部门 930801 杭州服务办事处（OFFICE-HZ-DEMO）；930850/930851 为 tenant 0 验收夹具，禁止跨租户引用。
-- 1/3 主档：proj_project 公司/部门六列补值（V286 迁移明确不造值，此处按批准口径统一补）。
-- 2/3 关系：proj_project_company_department_relation 补 ORDER_OFFICE 主关系（is_primary=1，ACTIVE）。
-- 3/3 授权：system_user_company_department_scope 增加 scope_role=SERVICE_MANAGER 授权行
--   （下单办事处服务经理语境，PRD v1.8 多省份服务经理指派；与现有 PROJECT_MANAGER/SERVICE_MANAGER 值域一致）。
-- 幂等：主档条件更新（仅空值补齐）+ NOT EXISTS 守卫；重放零副作用。
-- id 派生：关系 993109300000+project_id（p.id 唯一）；授权 993109400000+(user_id mod 1000000000)
--   （tenant 1 现有 user id 尾段互异，重放确定）。creator 统一 v291-office-assignment。

-- 1/3 主档公司/办事处补值（仅空值，不覆盖已人工录入）
UPDATE `proj_project` p
JOIN `system_company` c ON c.`id` = 930800 AND c.`tenant_id` = 1 AND c.`deleted` = b'0'
JOIN `system_dept` d ON d.`id` = 930801 AND d.`tenant_id` = 1 AND d.`deleted` = b'0'
SET p.`company_id` = c.`id`, p.`company_code` = c.`code`, p.`company_name` = c.`name`,
    p.`department_id` = d.`id`, p.`department_code` = d.`code`, p.`department_name` = d.`name`,
    p.`updater` = 'v291-office-assignment', p.`update_time` = NOW(3)
WHERE p.`tenant_id` = 1 AND p.`deleted` = b'0'
  AND (p.`company_id` IS NULL OR p.`department_id` IS NULL);

-- 2/3 下单办事处关系补齐（生成列 primary_project_id 不写，由数据库维护）
INSERT INTO `proj_project_company_department_relation`
(`id`, `tenant_id`, `project_id`, `company_id`, `company_code`, `company_name`,
 `department_id`, `department_code`, `department_name`, `relation_role`, `is_primary`,
 `effective_from`, `effective_to`, `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 993109300000 + p.`id`, p.`tenant_id`, p.`id`,
       c.`id`, c.`code`, c.`name`, d.`id`, d.`code`, d.`name`,
       'ORDER_OFFICE', 1, NOW(3), NULL, 'ACTIVE', 0,
       'v291-office-assignment', 'v291-office-assignment', b'0'
FROM `proj_project` p
JOIN `system_company` c ON c.`id` = 930800 AND c.`tenant_id` = 1 AND c.`deleted` = b'0'
JOIN `system_dept` d ON d.`id` = 930801 AND d.`tenant_id` = 1 AND d.`deleted` = b'0'
WHERE p.`tenant_id` = 1 AND p.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_company_department_relation` r
                  WHERE r.`project_id` = p.`id` AND r.`relation_role` = 'ORDER_OFFICE'
                    AND r.`deleted` = b'0');

-- 3/3 人员组织范围授权（含已有其他 scope_role 行的用户；同用户同办事处同角色幂等跳过）
INSERT INTO `system_user_company_department_scope`
(`id`, `tenant_id`, `user_id`, `company_id`, `company_code`, `company_name`,
 `department_id`, `department_code`, `department_name`, `scope_role`, `is_primary`,
 `effective_from`, `effective_to`, `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 993109400000 + (u.`id` MOD 1000000000), u.`tenant_id`, u.`id`,
       c.`id`, c.`code`, c.`name`, d.`id`, d.`code`, d.`name`,
       'SERVICE_MANAGER', b'0', NOW(3), NULL, 0, 0,
       'v291-office-assignment', 'v291-office-assignment', b'0'
FROM `system_users` u
JOIN `system_company` c ON c.`id` = 930800 AND c.`tenant_id` = 1 AND c.`deleted` = b'0'
JOIN `system_dept` d ON d.`id` = 930801 AND d.`tenant_id` = 1 AND d.`deleted` = b'0'
WHERE u.`tenant_id` = 1 AND u.`deleted` = b'0' AND u.`status` = 0
  AND NOT EXISTS (SELECT 1 FROM `system_user_company_department_scope` s
                  WHERE s.`user_id` = u.`id` AND s.`company_id` = 930800
                    AND s.`department_id` = 930801 AND s.`scope_role` = 'SERVICE_MANAGER'
                    AND s.`deleted` = b'0');
