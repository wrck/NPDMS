INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198775, '产品类型受控导入', 'pms:asset-product-type:controlled-import', 3, 50, 198770, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`),
 `permission` = VALUES(`permission`),
 `type` = VALUES(`type`),
 `sort` = VALUES(`sort`),
 `parent_id` = VALUES(`parent_id`),
 `status` = 0,
 `visible` = b'1',
 `updater` = 'seed',
 `update_time` = NOW(),
 `deleted` = b'0';
