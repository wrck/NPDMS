UPDATE `system_menu`
SET `name` = '客户历史（只读）',
    `permission` = 'pms:customer:query',
    `path` = 'customer-history',
    `component` = 'pms/project/customer/index',
    `component_name` = 'PmsCustomerHistory',
    `sort` = 2,
    `status` = 0,
    `visible` = b'1',
    `updater` = 'seed',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = 18001;

UPDATE `system_menu`
SET `status` = 1,
    `visible` = b'0',
    `updater` = 'seed',
    `update_time` = NOW()
WHERE `id` IN (18002, 18003, 18004);

UPDATE `system_role_menu`
SET `deleted` = b'1',
    `updater` = 'seed',
    `update_time` = NOW()
WHERE `menu_id` IN (18002, 18003, 18004)
  AND `deleted` = b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198760, '客户工作台', 'pms:customer:query', 2, 1, 19260, 'customers', 'ep:office-building',
 'pms/customer/index', 'PmsCustomerWorkbench', 0, b'1', b'1', b'1',
 'seed', NOW(), 'seed', NOW(), b'0'),
(198761, '客户查询', 'pms:customer:query', 3, 10, 198760, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198762, '客户创建', 'pms:customer:create', 3, 20, 198760, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198763, '客户更新', 'pms:customer:update', 3, 30, 198760, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198764, '客户停用', 'pms:customer:disable', 3, 40, 198760, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198765, '客户删除', 'pms:customer:delete', 3, 50, 198760, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198766, '客户恢复', 'pms:customer:restore', 3, 60, 198760, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198767, '客户敏感字段查看', 'pms:customer:sensitive-read', 3, 70, 198760, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198768, '客户导出', 'pms:customer:export', 3, 80, 198760, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `type` = VALUES(`type`),
 `sort` = VALUES(`sort`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
 `icon` = VALUES(`icon`), `component` = VALUES(`component`),
 `component_name` = VALUES(`component_name`), `status` = 0, `visible` = b'1',
 `keep_alive` = VALUES(`keep_alive`), `always_show` = VALUES(`always_show`),
 `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';
