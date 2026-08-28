-- F-PROJ-006 / PM-10：异常治理原因字典类型与稳定权限种子。
-- 原因值由基础平台按业务配置；规格未定义枚举值，本迁移不臆造原因码。

INSERT INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 992006010001, '项目异常治理原因', 'pms_project_governance_reason', 0,
       'F-PROJ-006回退、异常关闭与重开共用的可配置原因',
       'seed', NOW(), 'seed', NOW(), b'0', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type` = 'pms_project_governance_reason' AND `deleted` = b'0'
);

UPDATE `system_dict_type`
SET `name` = '项目异常治理原因', `status` = 0,
    `remark` = 'F-PROJ-006回退、异常关闭与重开共用的可配置原因',
    `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0', `deleted_time` = NULL
WHERE `type` = 'pms_project_governance_reason';

-- 旧V1.7治理动作仅保留历史查询；停止暴露写菜单并撤销既有角色关联。
UPDATE `system_menu`
SET `status` = 1, `visible` = b'0', `updater` = 'seed', `update_time` = NOW()
WHERE `id` IN (19158, 19159, 19160, 19161, 19162);

UPDATE `system_role_menu`
SET `deleted` = b'1', `updater` = 'seed', `update_time` = NOW()
WHERE `menu_id` IN (19158, 19159, 19160, 19161, 19162)
  AND `deleted` = b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198750, '项目治理查询', 'pms:project:governance:query', 3, 90, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198751, '项目回退', 'pms:project:rollback', 3, 100, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198752, '项目异常关闭', 'pms:project:close', 3, 110, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198753, '项目受控重开', 'pms:project:reopen', 3, 120, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`),
  `parent_id`=VALUES(`parent_id`), `sort`=VALUES(`sort`), `status`=0,
  `visible`=b'1', `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

-- 不在迁移中把写权限授予任何角色；功能权限由基础平台授权，ProjectTreeScope由服务端再次校验。
