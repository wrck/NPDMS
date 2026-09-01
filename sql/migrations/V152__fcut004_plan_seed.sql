-- F-CUT-004 P4割接方案封闭字典与四项功能权限。
-- 复用F-CUT-002割接任务工作台，不写角色授权，不修改既有方案权限或旧菜单。

INSERT INTO `system_dict_type`
(`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
VALUES
(992602060001, 'PMS-割接方案状态', 'pms_cutover_plan_revision_status', 0, 'F-CUT-004正式方案revision状态', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602060002, 'PMS-割接方案编辑方式', 'pms_cutover_plan_edit_mode', 0, 'F-CUT-004正式编辑方式', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602060003, 'PMS-割接方案步骤章节', 'pms_cutover_plan_section', 0, 'F-CUT-004正式步骤章节', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602060004, 'PMS-割接保障角色', 'pms_cutover_support_role', 0, 'F-CUT-004正式保障角色', 'seed', NOW(), 'seed', NOW(), b'0', NULL),
(992602060005, 'PMS-割接方案修订原因', 'pms_cutover_plan_revision_reason', 0, 'F-CUT-004正式修订原因', 'seed', NOW(), 'seed', NOW(), b'0', NULL)
ON DUPLICATE KEY UPDATE
 `name`=VALUES(`name`), `status`=0, `remark`=VALUES(`remark`),
 `updater`='seed', `update_time`=NOW(), `deleted`=b'0', `deleted_time`=NULL;

INSERT INTO `system_dict_data`
(`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992602061001, 10, '草稿', 'DRAFT', 'pms_cutover_plan_revision_status', 0, 'info', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061002, 20, '已提交', 'SUBMITTED', 'pms_cutover_plan_revision_status', 0, 'warning', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061003, 30, '来源失效', 'INVALIDATED', 'pms_cutover_plan_revision_status', 0, 'danger', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602061011, 10, '在线标准方案', 'ONLINE_TEMPLATE_STANDARD', 'pms_cutover_plan_edit_mode', 0, 'primary', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061012, 20, 'D级简易方案', 'ONLINE_TEMPLATE_SIMPLE_D', 'pms_cutover_plan_edit_mode', 0, 'warning', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061013, 30, '完整文件方案', 'FULL_FILE_UPLOAD', 'pms_cutover_plan_edit_mode', 0, 'success', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061014, 40, '历史只读方案', 'LEGACY_READ_ONLY', 'pms_cutover_plan_edit_mode', 0, 'info', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602061021, 10, '操作前准备', 'PRE_OPERATION', 'pms_cutover_plan_section', 0, 'info', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061022, 20, '阶段操作', 'OPERATION', 'pms_cutover_plan_section', 0, 'primary', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061023, 30, '收尾与信息采集', 'CLOSING_COLLECTION', 'pms_cutover_plan_section', 0, 'info', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061024, 40, '割接后业务测试', 'POST_BUSINESS_TEST', 'pms_cutover_plan_section', 0, 'success', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061025, 50, '回退步骤', 'ROLLBACK', 'pms_cutover_plan_section', 0, 'danger', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061026, 60, '割接后保障', 'POST_CUTOVER_SUPPORT', 'pms_cutover_plan_section', 0, 'warning', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602061031, 10, '客户方', 'CUSTOMER', 'pms_cutover_support_role', 0, 'primary', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061032, 20, '数通一线', 'DP_FIRST_LINE', 'pms_cutover_support_role', 0, 'success', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061033, 30, '数通二线', 'DP_SECOND_LINE', 'pms_cutover_support_role', 0, 'warning', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061034, 40, '数通研发', 'DP_RND', 'pms_cutover_support_role', 0, 'danger', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),

(992602061041, 10, '初始方案', 'INITIAL', 'pms_cutover_plan_revision_reason', 0, 'info', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061042, 20, '审批驳回修订', 'APPROVAL_REJECTED', 'pms_cutover_plan_revision_reason', 0, 'warning', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061043, 30, '职责变化修订', 'DUTY_CHANGED', 'pms_cutover_plan_revision_reason', 0, 'warning', '', 'F-CUT-004预留枚举；运行分支仍BLOCKED_BY_SPEC', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602061044, 40, '来源替换修订', 'SOURCE_REPLACED', 'pms_cutover_plan_revision_reason', 0, 'danger', '', 'F-CUT-004', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `sort`=VALUES(`sort`), `label`=VALUES(`label`), `status`=0,
 `color_type`=VALUES(`color_type`), `remark`=VALUES(`remark`),
 `updater`='seed', `update_time`=NOW(), `deleted`=b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(992602062001, '查询割接方案', 'pms:cutover-task:query-plan', 3, 80, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602062002, '保存割接方案', 'pms:cutover-task:save-plan', 3, 90, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602062003, '下载割接初稿', 'pms:cutover-task:download-plan', 3, 100, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0'),
(992602062004, '提交割接方案', 'pms:cutover-task:submit-plan', 3, 110, 992602050001,
 '', '', NULL, NULL, 0, b'1', b'1', b'1', 'seed', NOW(), 'seed', NOW(), b'0')
ON DUPLICATE KEY UPDATE
 `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`),
 `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`),
 `icon`=VALUES(`icon`), `component`=VALUES(`component`), `component_name`=VALUES(`component_name`),
 `status`=0, `visible`=b'1', `keep_alive`=VALUES(`keep_alive`),
 `always_show`=VALUES(`always_show`), `updater`='seed', `update_time`=NOW(), `deleted`=b'0';
