-- V292: 测试项目服务经理/项目经理组织范围授权补齐（用户 2026-09-18 指令）
-- 范围：tenant 1 全部启用用户 × （公司 930800 迪普科技示例公司 + 部门 930801 杭州服务办事处）
--   × { SERVICE_MANAGER, PROJECT_MANAGER }。
-- 依据：tenant 1 全部 41 个测试项目的下单办事处均为 (930800, 930801)（V291 补齐），
--   授权该组合即覆盖全部测试项目；项目经理候选资格由 SYSTEM OrganizationScopeApi.pageCompanyRoleUsers
--   按 companyId + scope_role=PROJECT_MANAGER 取有效授权（Q-FPROJ-010 同公司跨部门口径），
--   服务经理候选/指派同源（PM-08/PRD v1.8 下单办事处服务经理）。
--   tenant 0 为验收夹具租户，跨租户引用组织无效，不授权。
-- 幂等：同用户同公司同办事处同角色 NOT EXISTS 守卫；重放零副作用。
-- id 派生：993109500000/993109600000 + (user_id mod 1000000000)，与 V291 的 9931094 基段错开；
--   tenant 1 现有 user id 尾段互异（V291 已核），重放确定。creator 统一 v292-scope-seed。

-- 1/2 项目经理授权（本库预期补 18 行：除既有 seed 行的 user 1 外全部启用用户）
INSERT INTO `system_user_company_department_scope`
(`id`, `tenant_id`, `user_id`, `company_id`, `company_code`, `company_name`,
 `department_id`, `department_code`, `department_name`, `scope_role`, `is_primary`,
 `effective_from`, `effective_to`, `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 993109500000 + (u.`id` MOD 1000000000), u.`tenant_id`, u.`id`,
       c.`id`, c.`code`, c.`name`, d.`id`, d.`code`, d.`name`,
       'PROJECT_MANAGER', b'0', NOW(3), NULL, 0, 0,
       'v292-scope-seed', 'v292-scope-seed', b'0'
FROM `system_users` u
JOIN `system_company` c ON c.`id` = 930800 AND c.`tenant_id` = 1 AND c.`deleted` = b'0'
JOIN `system_dept` d ON d.`id` = 930801 AND d.`tenant_id` = 1 AND d.`deleted` = b'0'
WHERE u.`tenant_id` = 1 AND u.`deleted` = b'0' AND u.`status` = 0
  AND NOT EXISTS (SELECT 1 FROM `system_user_company_department_scope` s
                  WHERE s.`user_id` = u.`id` AND s.`company_id` = 930800
                    AND s.`department_id` = 930801 AND s.`scope_role` = 'PROJECT_MANAGER'
                    AND s.`deleted` = b'0');

-- 2/2 服务经理授权重申（V291 已覆盖本库全部 19 人，预期 0 行新增；其他环境重放时一步到位）
INSERT INTO `system_user_company_department_scope`
(`id`, `tenant_id`, `user_id`, `company_id`, `company_code`, `company_name`,
 `department_id`, `department_code`, `department_name`, `scope_role`, `is_primary`,
 `effective_from`, `effective_to`, `status`, `version`, `creator`, `updater`, `deleted`)
SELECT 993109600000 + (u.`id` MOD 1000000000), u.`tenant_id`, u.`id`,
       c.`id`, c.`code`, c.`name`, d.`id`, d.`code`, d.`name`,
       'SERVICE_MANAGER', b'0', NOW(3), NULL, 0, 0,
       'v292-scope-seed', 'v292-scope-seed', b'0'
FROM `system_users` u
JOIN `system_company` c ON c.`id` = 930800 AND c.`tenant_id` = 1 AND c.`deleted` = b'0'
JOIN `system_dept` d ON d.`id` = 930801 AND d.`tenant_id` = 1 AND d.`deleted` = b'0'
WHERE u.`tenant_id` = 1 AND u.`deleted` = b'0' AND u.`status` = 0
  AND NOT EXISTS (SELECT 1 FROM `system_user_company_department_scope` s
                  WHERE s.`user_id` = u.`id` AND s.`company_id` = 930800
                    AND s.`department_id` = 930801 AND s.`scope_role` = 'SERVICE_MANAGER'
                    AND s.`deleted` = b'0');
