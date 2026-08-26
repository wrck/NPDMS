-- =============================================================================
-- F-SOL-001 / PRE-01：工期变更原因、客户依据配置和稳定功能权限。
-- 新权限不自动授予角色；旧PRE-01写入口仅退役菜单与有效角色关联。
-- =============================================================================

INSERT INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 992101010001, '项目工期变更原因', 'pms_duration_change_reason_type', 0,
       'F-SOL-001项目工期版本化变更原因',
       'seed', NOW(), 'seed', NOW(), b'0', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type` = 'pms_duration_change_reason_type' AND `deleted` = b'0'
);

UPDATE `system_dict_type`
SET `name` = '项目工期变更原因', `status` = 0,
    `remark` = 'F-SOL-001项目工期版本化变更原因',
    `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0', `deleted_time` = NULL
WHERE `type` = 'pms_duration_change_reason_type';

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`,
 `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992101020001, 1, '客户原因延期', 'CUSTOMER_DELAY',
       'pms_duration_change_reason_type', 0, 'warning', '',
       '客户原因导致项目工期调整，提交时要求冻结客户依据',
       'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'pms_duration_change_reason_type'
      AND `value` = 'CUSTOMER_DELAY' AND `deleted` = b'0'
);

UPDATE `system_dict_data`
SET `sort` = 1, `label` = '客户原因延期', `status` = 0,
    `color_type` = 'warning',
    `remark` = '客户原因导致项目工期调整，提交时要求冻结客户依据',
    `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0'
WHERE `dict_type` = 'pms_duration_change_reason_type'
  AND `value` = 'CUSTOMER_DELAY';

INSERT INTO `infra_config`
(`id`, `category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992101030001, 'pms', 1, '项目工期变更客户依据必填原因',
       'pms.sol.duration-change.customer-evidence-required-reason-codes',
       'CUSTOMER_DELAY', b'0', '逗号分隔的工期变更原因码；命中时必须冻结客户依据文件事实',
       'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_config`
    WHERE `config_key` = 'pms.sol.duration-change.customer-evidence-required-reason-codes'
      AND `deleted` = b'0'
);

UPDATE `infra_config`
SET `category` = 'pms', `type` = 1,
    `name` = '项目工期变更客户依据必填原因', `value` = 'CUSTOMER_DELAY',
    `visible` = b'0',
    `remark` = '逗号分隔的工期变更原因码；命中时必须冻结客户依据文件事实',
    `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0'
WHERE `config_key` = 'pms.sol.duration-change.customer-evidence-required-reason-codes';

-- V1.7工期倒排和计划变更仍保留查询证据，所有PRE-01写权限停止暴露。
UPDATE `system_menu`
SET `status` = 1, `visible` = b'0', `updater` = 'seed', `update_time` = NOW()
WHERE `id` IN (19146, 19147, 19148, 19149, 19150,
               19152, 19153, 19154, 19155, 19156);

UPDATE `system_role_menu`
SET `deleted` = b'1', `updater` = 'seed', `update_time` = NOW()
WHERE `menu_id` IN (19146, 19147, 19148, 19149, 19150,
                    19152, 19153, 19154, 19155, 19156)
  AND `deleted` = b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198770, '项目工期查询', 'pms:construction-plan:query', 3, 130, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198771, '项目工期维护', 'pms:construction-plan:duration-manage', 3, 140, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198772, '项目工期审批', 'pms:construction-plan:duration-approve', 3, 150, 18071, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`), `status` = 0,
  `visible` = b'1', `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

-- 新权限由基础平台授权；本迁移不写system_role_menu授权记录。
