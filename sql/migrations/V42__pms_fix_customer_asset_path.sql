-- =============================================================================
-- V42: 修复「客户与资产」顶级菜单 path 与「项目交付」冲突
-- 背景：V40 中新建顶级菜单「客户与资产」path 设为 /pms/customer-asset，
--      以 /pms 开头导致 Vue Router 将其视为「项目交付」(/pms) 的子路由，
--      造成链接重复、菜单无法展开。
--      所有顶级菜单 path 必须独立（如 /system、/infra、/pms），不能嵌套。
-- 修复：将 path 从 /pms/customer-asset 改为 /customer-asset。
-- =============================================================================
UPDATE `system_menu` SET `path` = '/customer-asset', `update_time` = NOW()
WHERE `id` = 19260 AND `deleted` = b'0';
