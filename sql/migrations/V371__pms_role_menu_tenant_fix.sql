-- =============================================================================
-- V371: 修复 V16/V18/V21 给超级管理员授权 PMS 菜单时漏写 tenant_id 的种子缺陷。
-- system_role_menu 的租户语义必须与角色所属租户一致（role_id=1 属于租户 1，
-- 见 V120/V122/V163 等后续迁移的显式 tenant_id=1 写法）。
-- 后果：显式权限校验（ExplicitPermissionApi 按运行租户查库，无超管旁路）在租户 1
-- 查不到授权，项目闭环提交的 requireReviewer('pms:acc-project-closure:audit')
-- 恒为拒绝，正常闭环审批无法发起。
-- 修复范围限定为三批迁移授权的 PMS 菜单区间 18000~19138；上游 yudao 平台数据
-- （menu_id < 18000）保持不动。幂等：重复执行无变化。
-- =============================================================================
UPDATE `system_role_menu`
SET `tenant_id` = 1, `updater` = 'v371_role_menu_tenant_fix', `update_time` = NOW()
WHERE `role_id` = 1 AND `tenant_id` = 0 AND `deleted` = b'0'
  AND `menu_id` BETWEEN 18000 AND 19138;
