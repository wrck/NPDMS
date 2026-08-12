INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(18000, '项目交付', '', 1, 20, 0, '/pms', 'ep:management', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18001, '客户管理', 'pms:customer:query', 2, 10, 18000, 'customer', 'ep:office-building',
 'pms/project/customer/index', 'PmsCustomer', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18002, '客户新增', 'pms:customer:create', 3, 1, 18001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18003, '客户修改', 'pms:customer:update', 3, 2, 18001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18004, '客户删除', 'pms:customer:delete', 3, 3, 18001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18005, '联系人查询', 'pms:customer-contact:query', 3, 4, 18001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18006, '联系人新增', 'pms:customer-contact:create', 3, 5, 18001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18007, '联系人修改', 'pms:customer-contact:update', 3, 6, 18001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18008, '联系人删除', 'pms:customer-contact:delete', 3, 7, 18001, '', '', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`), `permission` = VALUES(`permission`), `component` = VALUES(`component`),
 `component_name` = VALUES(`component_name`), `update_time` = NOW(), `deleted` = b'0';
