-- F-CUT-005 P5分级审批封闭字典、三个功能权限、站内信模板与暂停投递Job。
-- 复用F-CUT-002割接任务工作台，不写角色授权，不修改旧菜单/权限，不同步Quartz。

INSERT INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES
(992602070001, 'PMS-割接审批状态', 'pms_cutover_approval_status', 0, 'F-CUT-005审批实例状态', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602070002, 'PMS-割接审批节点状态', 'pms_cutover_approval_node_status', 0, 'F-CUT-005串行节点状态', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602070003, 'PMS-割接审批节点', 'pms_cutover_approval_node_code', 0, 'F-CUT-005审批节点编码', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602070004, 'PMS-割接审批评审项', 'pms_cutover_approval_review_item', 0, 'F-CUT-005五项评审编码', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602070005, 'PMS-割接审批评审结论', 'pms_cutover_approval_review_decision', 0, 'F-CUT-005评审结论', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602070006, 'PMS-割接服务经理复核结论', 'pms_cutover_approval_assessment_review', 0, 'F-CUT-005服务经理P2复核结论', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602070007, 'PMS-割接审批暂停原因', 'pms_cutover_approval_hold_reason', 0, 'F-CUT-005审批暂停原因', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602070008, 'PMS-割接审批通知状态', 'pms_cutover_approval_notification_status', 0, 'F-CUT-005站内信投递状态', 'seed', NOW(), 'seed', NOW(), b'0', NULL)
ON DUPLICATE KEY UPDATE
 `name`=VALUES(`name`), `status`=0, `remark`=VALUES(`remark`),
 `updater`='seed', `update_time`=NOW(), `deleted`=b'0', `deleted_time`=NULL;

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992602071001, 10, '审批中', 'PENDING', 'pms_cutover_approval_status', 0, 'warning', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071002, 20, '来源失效已暂停', 'PAUSED_SOURCE_INVALIDATED', 'pms_cutover_approval_status', 0, 'danger', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071003, 30, '审批通过', 'APPROVED', 'pms_cutover_approval_status', 0, 'success', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071004, 40, '审批驳回', 'REJECTED', 'pms_cutover_approval_status', 0, 'danger', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602071011, 10, '等待中', 'WAITING', 'pms_cutover_approval_node_status', 0, 'info', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071012, 20, '审批中', 'PENDING', 'pms_cutover_approval_node_status', 0, 'warning', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071013, 30, '已通过', 'APPROVED', 'pms_cutover_approval_node_status', 0, 'success', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071014, 40, '已驳回', 'REJECTED', 'pms_cutover_approval_node_status', 0, 'danger', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071015, 50, '已取消', 'CANCELLED', 'pms_cutover_approval_node_status', 0, 'info', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602071021, 10, '发起人确认', 'INITIATOR', 'pms_cutover_approval_node_code', 0, 'primary', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071022, 20, '服务经理审批', 'SERVICE_MANAGER', 'pms_cutover_approval_node_code', 0, 'warning', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071023, 30, '二线审批', 'SECOND_LINE', 'pms_cutover_approval_node_code', 0, 'warning', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071024, 40, '研发审批', 'RND', 'pms_cutover_approval_node_code', 0, 'danger', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602071031, 10, '准备工作', 'PREPARATION', 'pms_cutover_approval_review_item', 0, 'info', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071032, 20, '业务测试', 'BUSINESS_TEST', 'pms_cutover_approval_review_item', 0, 'info', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071033, 30, '实施步骤', 'EXECUTION', 'pms_cutover_approval_review_item', 0, 'info', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071034, 40, '回退方案', 'ROLLBACK', 'pms_cutover_approval_review_item', 0, 'info', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071035, 50, '其他事项', 'OTHER', 'pms_cutover_approval_review_item', 0, 'info', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602071041, 10, '合理', 'YES', 'pms_cutover_approval_review_decision', 0, 'success', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071042, 20, '不合理', 'NO', 'pms_cutover_approval_review_decision', 0, 'danger', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071051, 10, '确认合理', 'CONFIRMED', 'pms_cutover_approval_assessment_review', 0, 'success', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071052, 20, '不合理', 'NOT_REASONABLE', 'pms_cutover_approval_assessment_review', 0, 'danger', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602071061, 10, '路由候选不唯一', 'ROUTE_CANDIDATE_NOT_UNIQUE', 'pms_cutover_approval_hold_reason', 0, 'warning', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071062, 20, '审批人不可用', 'APPROVER_UNAVAILABLE', 'pms_cutover_approval_hold_reason', 0, 'danger', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602071071, 10, '待投递', 'PENDING', 'pms_cutover_approval_notification_status', 0, 'info', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071072, 20, '已送达', 'SENT', 'pms_cutover_approval_notification_status', 0, 'success', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602071073, 30, '待重试', 'PENDING_RETRY', 'pms_cutover_approval_notification_status', 0, 'warning', '', 'F-CUT-005', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `sort`=VALUES(`sort`), `label`=VALUES(`label`), `status`=0,
 `color_type`=VALUES(`color_type`), `remark`=VALUES(`remark`),
 `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992602072001, '查询P5审批', 'pms:cutover-task:query-approval', 3, 120, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602072002, '处理P5审批', 'pms:cutover-task:approve', 3, 130, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602072003, '改派P5审批', 'pms:cutover-task:reassign-approval', 3, 140, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
 `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`),
 `icon`=VALUES(`icon`), `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
 `status`=0, `visible`=b'1', `keep_alive`=VALUES(`keep_alive`),
 `always_show`=VALUES(`always_show`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '割接P5审批待办', 'CUT_APPROVAL_PENDING', '项目交付平台',
       '割接任务 {taskCode}（{taskName}）的第 {nodeNo} 个审批节点 {nodeCode} 待您处理。{link}',
       2, '["taskId","taskCode","taskName","approvalInstanceId","nodeNo","nodeCode","link"]',
       0, 'F-CUT-005 P5审批节点待办站内信', 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_notify_template`
    WHERE `code`='CUT_APPROVAL_PENDING' AND `deleted`=b'0'
);

UPDATE `system_notify_template`
SET `name`='割接P5审批待办', `nickname`='项目交付平台',
    `content`='割接任务 {taskCode}（{taskName}）的第 {nodeNo} 个审批节点 {nodeCode} 待您处理。{link}',
    `type`=2, `params`='["taskId","taskCode","taskName","approvalInstanceId","nodeNo","nodeCode","link"]',
    `status`=0, `remark`='F-CUT-005 P5审批节点待办站内信',
    `updater`='seed', `update_time`=NOW(), `deleted`=b'0'
WHERE `code`='CUT_APPROVAL_PENDING' AND `deleted`=b'0';

INSERT INTO `infra_job`
(`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
 `retry_count`, `retry_interval`, `monitor_timeout`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 992602073001, '割接P5审批站内信投递', 2,
       'cutoverApprovalNotificationJob', '', '0/30 * * * * ?',
       0, 0, 0, 'seed', NOW(), 'seed', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job`
    WHERE `handler_name`='cutoverApprovalNotificationJob' AND `deleted`=b'0'
);

UPDATE `infra_job`
SET `name`='割接P5审批站内信投递', `status`=2, `handler_param`='',
    `cron_expression`='0/30 * * * * ?', `retry_count`=0, `retry_interval`=0,
    `monitor_timeout`=0, `updater`='seed', `update_time`=NOW(), `deleted`=b'0'
WHERE `handler_name`='cutoverApprovalNotificationJob' AND `deleted`=b'0';
