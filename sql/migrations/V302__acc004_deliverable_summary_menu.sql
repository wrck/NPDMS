-- P0-5 按项目交付件汇总视图菜单（V302，前向、幂等）
-- 背景：6.4 查看交付件（ACC-04）缺按项目 6 类汇总视图（核对记录
--       docs/reference/2026-09-19-项目交付全流程操作清单核对.md P0-5）。
-- 复用交付件既有查询权限 pms:imp-deliverable:query（V256 已由 pms:eng-deliverable: 改名），
-- 挂在项目交付目录 18000 下，与交付件归集菜单 19018 同级。

INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(993109180001, '交付件汇总（6.4）', 'pms:imp-deliverable:query', 2, 61, 18000, 'imp-deliverable-summary', 'ep:files',
 'pms/engineering/deliverable/summary/index', 'PmsImpDeliverableSummary', 0, b'1', b'1', b'1',
 'p005_v302', NOW(), 'p005_v302', NOW(), b'0');

-- 已可见交付件归集菜单（19018）的角色同步可见汇总页，幂等；不新增业务操作权限
INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT source.role_id, 993109180001, 'p005_v302', NOW(), 'p005_v302', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
WHERE source.`menu_id` = 19018 AND source.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.`tenant_id` = source.`tenant_id`
                    AND existing.`role_id` = source.`role_id`
                    AND existing.`menu_id` = 993109180001 AND existing.`deleted` = b'0');
