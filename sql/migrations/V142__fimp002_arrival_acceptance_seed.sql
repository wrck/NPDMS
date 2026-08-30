-- F-IMP-002 到货签收正式字典、菜单与暂停的旧数据核对 Job。
-- 不写角色授权，不修改旧到货菜单，不播种到货业务事实。

INSERT INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES
(992602020001, 'PMS-到货签收批次状态', 'pms_arrival_acceptance_status', 0,
 'F-IMP-002正式批次状态', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602020002, 'PMS-到货签收差异类型', 'pms_arrival_difference_type', 0,
 'F-IMP-002正式差异类型', 'seed', NOW(), 'seed', NOW(), b'0', NULL)
ON DUPLICATE KEY UPDATE
 `name`=VALUES(`name`), `status`=0, `remark`=VALUES(`remark`),
 `updater`='seed', `update_time`=NOW(), `deleted`=b'0', `deleted_time`=NULL;

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992602030001, 10, '草稿', 'DRAFT', 'pms_arrival_acceptance_status', 0, 'info', '', 'F-IMP-002',
 'seed', NOW(), 'seed', NOW(), b'0'),
(992602030002, 20, '部分签收', 'PARTIALLY_ACCEPTED', 'pms_arrival_acceptance_status', 0, 'warning', '', 'F-IMP-002',
 'seed', NOW(), 'seed', NOW(), b'0'),
(992602030003, 30, '差异待处理', 'DIFFERENCE_PENDING', 'pms_arrival_acceptance_status', 0, 'danger', '', 'F-IMP-002',
 'seed', NOW(), 'seed', NOW(), b'0'),
(992602030004, 40, '已满足', 'ACCEPTED', 'pms_arrival_acceptance_status', 0, 'success', '', 'F-IMP-002',
 'seed', NOW(), 'seed', NOW(), b'0'),
(992602030005, 50, '已确认', 'CONFIRMED', 'pms_arrival_acceptance_status', 0, 'primary', '', 'F-IMP-002',
 'seed', NOW(), 'seed', NOW(), b'0'),
(992602030011, 10, '数量不符', 'QUANTITY_MISMATCH', 'pms_arrival_difference_type', 0, 'warning', '', 'F-IMP-002',
 'seed', NOW(), 'seed', NOW(), b'0'),
(992602030012, 20, '型号或序列号不符', 'MODEL_OR_SN_MISMATCH', 'pms_arrival_difference_type', 0, 'danger', '', 'F-IMP-002',
 'seed', NOW(), 'seed', NOW(), b'0'),
(992602030013, 30, '外观或质量异常', 'APPEARANCE_OR_QUALITY', 'pms_arrival_difference_type', 0, 'danger', '', 'F-IMP-002',
 'seed', NOW(), 'seed', NOW(), b'0'),
(992602030014, 40, '证据不完整', 'EVIDENCE_INCOMPLETE', 'pms_arrival_difference_type', 0, 'warning', '', 'F-IMP-002',
 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `sort`=VALUES(`sort`), `label`=VALUES(`label`), `status`=0,
 `color_type`=VALUES(`color_type`), `remark`=VALUES(`remark`),
 `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992602040001, '到货签收工作台', 'pms:arrival-acceptance:query', 2, 8, 19269,
 'arrival-acceptance', 'ep:van', 'pms/engineering/arrival-acceptance/index', 'PmsArrivalAcceptance',
 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602040002, '到货签收查询', 'pms:arrival-acceptance:query', 3, 10, 992602040001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602040003, '创建到货草稿', 'pms:arrival-acceptance:create', 3, 20, 992602040001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602040004, '编辑本人草稿', 'pms:arrival-acceptance:edit-own-draft', 3, 30, 992602040001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602040005, '确认到货签收', 'pms:arrival-acceptance:confirm', 3, 40, 992602040001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602040006, '处理到货差异', 'pms:arrival-acceptance:resolve-difference', 3, 50, 992602040001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
 `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`),
 `icon`=VALUES(`icon`), `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
 `status`=0, `visible`=b'1', `keep_alive`=VALUES(`keep_alive`),
 `always_show`=VALUES(`always_show`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `infra_job`
(`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
 `retry_count`, `retry_interval`, `monitor_timeout`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992602010003, '到货签收旧数据核对', 2,
       'arrivalLegacyReconciliationJob', '', '0 0/5 * * * ?',
       0, 0, 0, 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name` = 'arrivalLegacyReconciliationJob'
      AND `deleted` = b'0'
);

UPDATE `infra_job`
SET `name`='到货签收旧数据核对', `status`=2, `handler_param`='',
    `cron_expression`='0 0/5 * * * ?', `retry_count`=0, `retry_interval`=0,
    `monitor_timeout`=0, `updater`='seed', `update_time`=NOW(), `deleted`=b'0'
WHERE `handler_name`='arrivalLegacyReconciliationJob'
  AND `deleted`=b'0';
