INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198770, '设备工作台', 'pms:device:query', 2, 4, 19260, 'devices', 'ep:cpu',
 'pms/asset/device/index', 'PmsAssetDeviceWorkbench', 0, b'1', b'1', b'1',
 'seed', NOW(), 'seed', NOW(), b'0'),
(198771, '设备查询', 'pms:device:query', 3, 10, 198770, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198772, '设备归属管理', 'pms:device:assign', 3, 20, 198770, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198773, '设备冲突处置', 'pms:device:conflict-handle', 3, 30, 198770, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198774, '配置Log下载', 'pms:device-configuration-log:download', 3, 40, 198770, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `type` = VALUES(`type`),
 `sort` = VALUES(`sort`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
 `icon` = VALUES(`icon`), `component` = VALUES(`component`),
 `component_name` = VALUES(`component_name`), `status` = 0, `visible` = b'1',
 `keep_alive` = VALUES(`keep_alive`), `always_show` = VALUES(`always_show`),
 `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

UPDATE `system_role_menu` role_menu
JOIN `system_role` role
  ON role.`id` = role_menu.`role_id`
 AND role.`tenant_id` = role_menu.`tenant_id`
 AND role.`deleted` = b'0'
SET role_menu.`deleted` = b'1',
    role_menu.`updater` = 'seed',
    role_menu.`update_time` = NOW()
WHERE role_menu.`menu_id` IN (19002, 19003, 19004, 19005)
  AND role_menu.`deleted` = b'0'
  AND role.`code` <> 'super_admin';
