-- =============================================================================
-- F-PLT-001 / PLT-02：文件类别、敏感级别、用途策略、权限及Outbox投递Job。
-- 新权限不自动授予角色；用途策略仅提供锁定规格要求的首个SOL示例组合。
-- =============================================================================

INSERT INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 992201010001, 'PMS文件类别', 'pms_file_category', 0,
       'F-PLT-001统一文件业务类别',
       'seed', NOW(), 'seed', NOW(), b'0', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type` = 'pms_file_category' AND `deleted` = b'0'
);

INSERT INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT 992201010002, 'PMS文件敏感级别', 'pms_file_sensitivity_level', 0,
       'F-PLT-001统一文件访问敏感级别',
       'seed', NOW(), 'seed', NOW(), b'0', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type`
    WHERE `type` = 'pms_file_sensitivity_level' AND `deleted` = b'0'
);

UPDATE `system_dict_type`
SET `status` = 0, `updater` = 'seed', `update_time` = NOW(),
    `deleted` = b'0', `deleted_time` = NULL
WHERE `type` IN ('pms_file_category', 'pms_file_sensitivity_level');

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`,
 `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992201020001, 10, '客户延期依据', 'CUSTOMER_DELAY_EVIDENCE',
       'pms_file_category', 0, 'warning', '',
       'SOL工期变更客户延期依据文件', 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'pms_file_category'
      AND `value` = 'CUSTOMER_DELAY_EVIDENCE' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`,
 `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992201020002, 10, '内部', 'INTERNAL',
       'pms_file_sensitivity_level', 0, 'info', '',
       '仅按业务对象授权范围访问', 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'pms_file_sensitivity_level'
      AND `value` = 'INTERNAL' AND `deleted` = b'0'
);

UPDATE `system_dict_data`
SET `status` = 0, `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0'
WHERE (`dict_type` = 'pms_file_category' AND `value` = 'CUSTOMER_DELAY_EVIDENCE')
   OR (`dict_type` = 'pms_file_sensitivity_level' AND `value` = 'INTERNAL');

-- 用途策略以独立配置行表达，便于按enabled和priority做确定性选择。
-- exact与partial同时命中时exact优先；disabled不参与；无匹配由不存在适用行表达。
INSERT INTO `infra_config`
(`id`, `category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992201030001, 'pms-file-policy', 1, 'SOL客户延期依据-精确策略',
 'pms.file.policy.sol.construction-plan-change.customer-delay',
 '{"enabled":true,"priority":100,"ownerContext":"SOL","objectType":"CONSTRUCTION_PLAN_CHANGE","purposeCode":"CUSTOMER_DELAY_EVIDENCE","categoryCode":"CUSTOMER_DELAY_EVIDENCE","mediaTypes":["application/pdf","image/jpeg","image/png"],"maxSizeBytes":52428800,"sensitivityCode":"INTERNAL","cardinality":"SINGLE"}',
 b'0', 'F-PLT-001精确命中策略', 'seed', NOW(), 'seed', NOW(), b'0'),
(992201030002, 'pms-file-policy', 1, 'SOL客户依据-部分限定策略',
 'pms.file.policy.sol.customer-evidence.partial',
 '{"enabled":true,"priority":50,"ownerContext":"SOL","purposeCode":"CUSTOMER_DELAY_EVIDENCE","categoryCode":"CUSTOMER_DELAY_EVIDENCE","mediaTypes":["application/pdf","image/jpeg","image/png"],"maxSizeBytes":52428800,"sensitivityCode":"INTERNAL","cardinality":"SINGLE"}',
 b'0', 'F-PLT-001部分限定及优先级让位示例', 'seed', NOW(), 'seed', NOW(), b'0'),
(992201030003, 'pms-file-policy', 1, 'SOL客户延期依据-停用策略',
 'pms.file.policy.sol.customer-evidence.disabled',
 '{"enabled":false,"priority":200,"ownerContext":"SOL","objectType":"CONSTRUCTION_PLAN_CHANGE","purposeCode":"CUSTOMER_DELAY_EVIDENCE","categoryCode":"CUSTOMER_DELAY_EVIDENCE","mediaTypes":["application/pdf"],"maxSizeBytes":52428800,"sensitivityCode":"INTERNAL","cardinality":"SINGLE"}',
 b'0', 'F-PLT-001停用不参与示例', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `value` = VALUES(`value`),
  `visible` = b'0', `remark` = VALUES(`remark`), `updater` = 'seed',
  `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(198780, '业务文件查询', 'pms:file:query', 3, 10, 1243, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198781, '业务文件上传', 'pms:file:upload', 3, 20, 1243, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198782, '业务文件下载', 'pms:file:download', 3, 30, 1243, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198783, '业务文件预览', 'pms:file:preview', 3, 40, 1243, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198784, '业务文件管理', 'pms:file:manage', 3, 50, 1243, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(198785, '业务文件归档', 'pms:file:archive', 3, 60, 1243, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
  `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`), `status` = 0,
  `visible` = b'1', `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `infra_job`
(`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
 `retry_count`, `retry_interval`, `monitor_timeout`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992201040001, '统一文件事件投递', 1, 'fileOutboxDeliveryJob', '',
       '0/30 * * * * ?', 0, 0, 0,
       'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'fileOutboxDeliveryJob' AND `deleted` = b'0'
);

UPDATE `infra_job`
SET `name` = '统一文件事件投递', `status` = 1, `handler_param` = '',
    `cron_expression` = '0/30 * * * * ?', `retry_count` = 0,
    `retry_interval` = 0, `monitor_timeout` = 0,
    `updater` = 'seed', `update_time` = NOW(), `deleted` = b'0'
WHERE `handler_name` = 'fileOutboxDeliveryJob';

-- 本迁移不写system_role_menu；新权限由基础平台显式授权。
