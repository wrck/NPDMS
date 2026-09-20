-- F-ACC-002 满意度管理菜单挂载（V301，前向、幂等）
-- 背景：V171 建立的 930930 目录仅挂 5 个按钮权限（930931~930935），无 type=2 页面菜单，
--       满意度工作台 pms/acceptance/satisfaction/index 无固定二级导航入口（核对记录 docs/reference/2026-09-19-项目交付全流程操作清单核对.md P0-2）。
-- 组件名使用 PmsSatisfactionManage，避免与 remaining.ts 隐藏路由 PmsSatisfactionWorkbench 路由名冲突。

INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(930936, '满意度调查', 'pms:acceptance:satisfaction:query', 2, 1, 930930, 'workbench', 'ep:chat-line-square',
 'pms/acceptance/satisfaction/index', 'PmsSatisfactionManage', 0, b'1', b'1', b'1',
 'facc002_v301', NOW(), 'facc002_v301', NOW(), b'0');

-- 与 V171 相同的验收角色授权闭包，幂等
INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 992004800002, 930936, 'facc002_v301', NOW(), 'facc002_v301', NOW(), b'0', 0
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.`tenant_id` = 0 AND existing.`role_id` = 992004800002
                    AND existing.`menu_id` = 930936 AND existing.`deleted` = b'0');
