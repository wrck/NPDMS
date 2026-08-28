-- F-PROJ-003 / PRD V1.8：项目授权动作、范围字典和项目详情权限。
-- 固定值域来自当前Feature，不创建第二套项目导航。

INSERT INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`,
  `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
(992003010001, 'PMS-项目授权动作', 'pms_project_authorization_action', 0,
 'PM-04项目授权动作固定值域', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992003010002, 'PMS-项目授权范围', 'pms_project_authorization_scope', 0,
 'PM-04项目授权范围固定值域', 'seed', NOW(), 'seed', NOW(), b'0', NULL)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `status`=0, `remark`=VALUES(`remark`),
  `updater`='seed', `update_time`=NOW(), `deleted`=b'0', `deleted_time`=NULL;

INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`,
  `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(992003020001, 1, '查看项目', 'PROJECT_VIEW', 'pms_project_authorization_action', 0,
 'primary', '', '允许查看授权范围内的项目', 'seed', NOW(), 'seed', NOW(), b'0'),
(992003020002, 2, '管理项目', 'PROJECT_MANAGE', 'pms_project_authorization_action', 0,
 'warning', '', '允许管理并查看授权范围内的项目', 'seed', NOW(), 'seed', NOW(), b'0'),
(992003020003, 1, '当前项目', 'CURRENT_PROJECT', 'pms_project_authorization_scope', 0,
 'primary', '', '仅授权锚点项目', 'seed', NOW(), 'seed', NOW(), b'0'),
(992003020004, 2, '项目及全部后代', 'PROJECT_AND_DESCENDANTS', 'pms_project_authorization_scope', 0,
 'success', '', '授权锚点项目及当前树版本中的全部后代', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `sort`=VALUES(`sort`), `label`=VALUES(`label`),
  `value`=VALUES(`value`), `dict_type`=VALUES(`dict_type`), `status`=0,
  `color_type`=VALUES(`color_type`), `remark`=VALUES(`remark`), `updater`='seed',
  `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
  `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
  `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(198730, '项目授权查询', 'pms:project:authorization:query', 3, 50, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198731, '项目授权管理', 'pms:project:authorization:manage', 3, 60, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198732, '项目授权撤销', 'pms:project:authorization:revoke', 3, 70, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`),
  `parent_id`=VALUES(`parent_id`), `sort`=VALUES(`sort`), `status`=0, `visible`=b'1',
  `updater`='seed', `update_time`=NOW(), `deleted`=b'0';
