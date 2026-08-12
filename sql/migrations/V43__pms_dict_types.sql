-- =============================================================================
-- V43: PMS 字典类型与字典数据初始化
-- 背景：PMS 模块前端大量硬编码状态/类型选项，需字典化统一管理。
--      本迁移创建 PMS 业务域全部字典类型及字典数据。
--      前端通过 DICT_TYPE.PMS_* 引用，使用 getIntDictOptions/getStrDictOptions 获取选项。
-- =============================================================================

-- ============================================================
-- 1. 字典类型（system_dict_type）
-- ============================================================
INSERT IGNORE INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
-- 项目域
(2001, 'PMS-项目状态', 'pms_project_status', 0, '项目交付状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2002, 'PMS-项目分类', 'pms_project_category', 0, '项目分类(主/子)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2003, 'PMS-任务WBS状态', 'pms_task_status', 0, '任务WBS状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2004, 'PMS-项目风险等级', 'pms_risk_level', 0, '项目风险等级', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2005, 'PMS-计划变更状态', 'pms_plan_change_status', 0, '计划变更审批状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
-- 工程域
(2010, 'PMS-交付件类型', 'pms_deliverable_type', 0, '工程交付件类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2011, 'PMS-交付件状态', 'pms_deliverable_status', 0, '工程交付件状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2012, 'PMS-公告类型', 'pms_announcement_type', 0, '技术公告类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2013, 'PMS-授权类型', 'pms_authorization_type', 0, '授权类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2014, 'PMS-交底类型', 'pms_briefing_type', 0, '工程交底类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2015, 'PMS-表单模板产品类型', 'pms_product_type', 0, '表单模板产品类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2016, 'PMS-表单模板状态', 'pms_form_template_status', 0, '表单模板状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2017, 'PMS-表单实例状态', 'pms_form_instance_status', 0, '表单实例状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2018, 'PMS-问题严重等级', 'pms_issue_severity', 0, '实施问题严重等级', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2019, 'PMS-问题状态', 'pms_issue_status', 0, '实施问题状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2020, 'PMS-工程通用状态', 'pms_eng_status', 0, '工程实施通用状态(资源/到货/安装/配置/联调)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
-- 割接域
(2030, 'PMS-割接任务状态', 'pms_cutover_task_status', 0, '割接任务状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2031, 'PMS-割接方案状态', 'pms_cutover_plan_status', 0, '割接方案状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2032, 'PMS-割接类型', 'pms_cutover_type', 0, '割接类型(替换/接入/升级/演练/配置)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2033, 'PMS-组网模式', 'pms_network_mode', 0, '组网模式(VSM/双活/集群/单机)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2034, 'PMS-来源类型', 'pms_source_type', 0, '来源类型(项目/工单/手动)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2035, 'PMS-割接风险类型', 'pms_cutover_risk_type', 0, '割接风险类型(风险/调研)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2036, 'PMS-割接风险状态', 'pms_cutover_risk_status', 0, '割接风险状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2037, 'PMS-割接执行状态', 'pms_cutover_exec_status', 0, '割接执行状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2038, 'PMS-割接观察状态', 'pms_cutover_observation_status', 0, '割接观察状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2039, 'PMS-遗留状态', 'pms_leftover_status', 0, '割接遗留问题状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
-- 验收域
(2040, 'PMS-验收类型', 'pms_acceptance_type', 0, '验收类型(初验/终验)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2041, 'PMS-文档类型', 'pms_document_type', 0, '归档文档类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2042, 'PMS-验收状态', 'pms_acceptance_status', 0, '验收通用状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
-- 巡检域
(2050, 'PMS-巡检模式', 'pms_inspection_mode', 0, '巡检模式(在线/离线)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2051, 'PMS-巡检任务状态', 'pms_srv_task_status', 0, '巡检任务状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2052, 'PMS-巡检规则类型', 'pms_srv_rule_type', 0, '巡检规则类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2053, 'PMS-巡检规则状态', 'pms_srv_rule_status', 0, '巡检规则状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2054, 'PMS-巡检报告类型', 'pms_srv_report_type', 0, '巡检报告类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2055, 'PMS-巡检报告状态', 'pms_srv_report_status', 0, '巡检报告状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2056, 'PMS-巡检问题严重等级', 'pms_srv_issue_severity', 0, '巡检问题严重等级', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2057, 'PMS-巡检问题状态', 'pms_srv_issue_status', 0, '巡检问题状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2058, 'PMS-维保状态', 'pms_srv_maintenance_status', 0, '维保状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2059, 'PMS-服务等级', 'pms_service_level', 0, '服务等级(金/银/铜/标准)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
-- 资产域
(2060, 'PMS-设备状态', 'pms_equipment_status', 0, '设备状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
-- 批量变更域
(2061, 'PMS-批量变更状态', 'pms_batch_change_status', 0, '批量变更状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

-- ============================================================
-- 2. 字典数据（system_dict_data）
-- ============================================================
INSERT IGNORE INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- PMS-项目状态 (pms_project_status)
(21001, 0, '立项待指派', '0', 'pms_project_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21002, 1, '进行中', '1', 'pms_project_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21003, 2, '已完成', '2', 'pms_project_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21004, 3, '已关闭', '3', 'pms_project_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-项目分类 (pms_project_category)
(21010, 1, '主项目', 'MAIN', 'pms_project_category', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21011, 2, '子项目', 'SUB', 'pms_project_category', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-任务WBS状态 (pms_task_status)
(21020, 0, '待开始', '0', 'pms_task_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21021, 1, '进行中', '1', 'pms_task_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21022, 2, '已完成', '2', 'pms_task_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21023, 3, '已暂停', '3', 'pms_task_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21024, 4, '已取消', '4', 'pms_task_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21025, 5, '已延期', '5', 'pms_task_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21026, 6, '已阻塞', '6', 'pms_task_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-项目风险等级 (pms_risk_level)
(21030, 1, 'A级-高', 'A', 'pms_risk_level', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21031, 2, 'B级-中', 'B', 'pms_risk_level', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21032, 3, 'C级-低', 'C', 'pms_risk_level', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21033, 4, 'D级-极低', 'D', 'pms_risk_level', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-计划变更状态 (pms_plan_change_status)
(21040, 0, '待审批', '0', 'pms_plan_change_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21041, 1, '已通过', '1', 'pms_plan_change_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21042, 2, '已驳回', '2', 'pms_plan_change_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21043, 3, '已撤回', '3', 'pms_plan_change_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-交付件类型 (pms_deliverable_type)
(21050, 1, '必选', 'REQUIRED', 'pms_deliverable_type', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21051, 2, '可选', 'OPTIONAL', 'pms_deliverable_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21052, 3, '条件', 'CONDITIONAL', 'pms_deliverable_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-交付件状态 (pms_deliverable_status)
(21060, 0, '待提交', '0', 'pms_deliverable_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21061, 1, '已提交', '1', 'pms_deliverable_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21062, 2, '已审核', '2', 'pms_deliverable_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21063, 3, '已驳回', '3', 'pms_deliverable_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-公告类型 (pms_announcement_type)
(21070, 1, '安全公告', 'SECURITY', 'pms_announcement_type', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21071, 2, '技术公告', 'TECHNICAL', 'pms_announcement_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21072, 3, '补丁公告', 'PATCH', 'pms_announcement_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-授权类型 (pms_authorization_type)
(21080, 1, '临时授权', 'TEMP', 'pms_authorization_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21081, 2, '正式授权', 'OFFICIAL', 'pms_authorization_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-交底类型 (pms_briefing_type)
(21090, 1, '技术交底', 'TECHNICAL', 'pms_briefing_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21091, 2, '安全交底', 'SAFETY', 'pms_briefing_type', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-表单模板产品类型 (pms_product_type)
(21100, 1, '防火墙', 'FIREWALL', 'pms_product_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21101, 2, '入侵检测', 'IPS', 'pms_product_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21102, 3, 'WAF', 'WAF', 'pms_product_type', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21103, 4, '交换机', 'SWITCH', 'pms_product_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21104, 5, '路由器', 'ROUTER', 'pms_product_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-表单模板状态 (pms_form_template_status)
(21110, 0, '草稿', '0', 'pms_form_template_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21111, 1, '已发布', '1', 'pms_form_template_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21112, 2, '已停用', '2', 'pms_form_template_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-表单实例状态 (pms_form_instance_status)
(21120, 0, '待填', '0', 'pms_form_instance_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21121, 1, '已填', '1', 'pms_form_instance_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21122, 2, '已提交', '2', 'pms_form_instance_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21123, 3, '已审核', '3', 'pms_form_instance_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21124, 4, '已驳回', '4', 'pms_form_instance_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-问题严重等级 (pms_issue_severity)
(21130, 1, '严重', '1', 'pms_issue_severity', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21131, 2, '重要', '2', 'pms_issue_severity', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21132, 3, '一般', '3', 'pms_issue_severity', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-问题状态 (pms_issue_status)
(21140, 0, '待处理', '0', 'pms_issue_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21141, 1, '处理中', '1', 'pms_issue_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21142, 2, '已解决', '2', 'pms_issue_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21143, 3, '已关闭', '3', 'pms_issue_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21144, 4, '已挂起', '4', 'pms_issue_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-工程通用状态 (pms_eng_status)
(21150, 0, '待开始', '0', 'pms_eng_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21151, 1, '进行中', '1', 'pms_eng_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21152, 2, '已完成', '2', 'pms_eng_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21153, 3, '已暂停', '3', 'pms_eng_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-割接任务状态 (pms_cutover_task_status)
(21200, 0, '待方案', '0', 'pms_cutover_task_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21201, 1, '待审批', '1', 'pms_cutover_task_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21202, 2, '待执行', '2', 'pms_cutover_task_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21203, 3, '执行中', '3', 'pms_cutover_task_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21204, 4, '观察中', '4', 'pms_cutover_task_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21205, 5, '已完成', '5', 'pms_cutover_task_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21206, 6, '已回退', '6', 'pms_cutover_task_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-割接方案状态 (pms_cutover_plan_status)
(21210, 0, '待审批', '0', 'pms_cutover_plan_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21211, 1, '已通过', '1', 'pms_cutover_plan_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21212, 2, '已驳回', '2', 'pms_cutover_plan_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-割接类型 (pms_cutover_type)
(21220, 1, '替换', 'REPLACE', 'pms_cutover_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21221, 2, '接入', 'ACCESS', 'pms_cutover_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21222, 3, '升级', 'UPGRADE', 'pms_cutover_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21223, 4, '演练', 'DRILL', 'pms_cutover_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21224, 5, '配置', 'CONFIG', 'pms_cutover_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-组网模式 (pms_network_mode)
(21230, 1, 'VSM', 'VSM', 'pms_network_mode', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21231, 2, '双活', 'DUAL', 'pms_network_mode', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21232, 3, '集群', 'CLUSTER', 'pms_network_mode', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21233, 4, '单机', 'SINGLE', 'pms_network_mode', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-来源类型 (pms_source_type)
(21240, 1, '项目', 'PROJECT', 'pms_source_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21241, 2, '工单', 'ITR', 'pms_source_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21242, 3, '手动', 'MANUAL', 'pms_source_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-割接风险类型 (pms_cutover_risk_type)
(21250, 1, '风险', 'RISK', 'pms_cutover_risk_type', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21251, 2, '调研', 'SURVEY', 'pms_cutover_risk_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-割接风险状态 (pms_cutover_risk_status)
(21260, 0, '待处理', '0', 'pms_cutover_risk_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21261, 1, '处理中', '1', 'pms_cutover_risk_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21262, 2, '已解决', '2', 'pms_cutover_risk_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21263, 3, '已关闭', '3', 'pms_cutover_risk_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-割接执行状态 (pms_cutover_exec_status)
(21270, 0, '待执行', '0', 'pms_cutover_exec_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21271, 1, '执行中', '1', 'pms_cutover_exec_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21272, 2, '已完成', '2', 'pms_cutover_exec_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21273, 3, '已回退', '3', 'pms_cutover_exec_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21274, 4, '已异常', '4', 'pms_cutover_exec_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-割接观察状态 (pms_cutover_observation_status)
(21280, 0, '观察中', '0', 'pms_cutover_observation_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21281, 1, '观察完成', '1', 'pms_cutover_observation_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21282, 2, '观察异常', '2', 'pms_cutover_observation_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-遗留状态 (pms_leftover_status)
(21290, 0, '待处理', '0', 'pms_leftover_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21291, 1, '处理中', '1', 'pms_leftover_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21292, 2, '已解决', '2', 'pms_leftover_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-验收类型 (pms_acceptance_type)
(21300, 1, '初验', 'PRELIMINARY', 'pms_acceptance_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21301, 2, '终验', 'FINAL', 'pms_acceptance_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-文档类型 (pms_document_type)
(21310, 1, '需求文档', 'REQUIREMENT', 'pms_document_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21311, 2, '设计文档', 'DESIGN', 'pms_document_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21312, 3, '实施文档', 'IMPLEMENTATION', 'pms_document_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21313, 4, '验收文档', 'ACCEPTANCE', 'pms_document_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21314, 5, '其他', 'OTHER', 'pms_document_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-验收状态 (pms_acceptance_status)
(21320, 0, '待验收', '0', 'pms_acceptance_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21321, 1, '验收中', '1', 'pms_acceptance_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21322, 2, '已通过', '2', 'pms_acceptance_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21323, 3, '已驳回', '3', 'pms_acceptance_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-巡检模式 (pms_inspection_mode)
(21400, 1, '在线', 'ONLINE', 'pms_inspection_mode', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21401, 2, '离线', 'OFFLINE', 'pms_inspection_mode', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-巡检任务状态 (pms_srv_task_status)
(21410, 0, '待执行', '0', 'pms_srv_task_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21411, 1, '执行中', '1', 'pms_srv_task_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21412, 2, '已完成', '2', 'pms_srv_task_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21413, 3, '已异常', '3', 'pms_srv_task_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21414, 4, '已取消', '4', 'pms_srv_task_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-巡检规则类型 (pms_srv_rule_type)
(21420, 1, '在线', 'ONLINE', 'pms_srv_rule_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21421, 2, '离线', 'OFFLINE', 'pms_srv_rule_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-巡检规则状态 (pms_srv_rule_status)
(21430, 0, '启用', '0', 'pms_srv_rule_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21431, 1, '停用', '1', 'pms_srv_rule_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-巡检报告类型 (pms_srv_report_type)
(21440, 1, '标准', 'STANDARD', 'pms_srv_report_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21441, 2, '详细', 'DETAIL', 'pms_srv_report_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21442, 3, '异常', 'EXCEPTION', 'pms_srv_report_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21443, 4, '汇总', 'SUMMARY', 'pms_srv_report_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-巡检报告状态 (pms_srv_report_status)
(21450, 0, '待生成', '0', 'pms_srv_report_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21451, 1, '已生成', '1', 'pms_srv_report_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21452, 2, '已审核', '2', 'pms_srv_report_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-巡检问题严重等级 (pms_srv_issue_severity)
(21460, 1, '严重', '1', 'pms_srv_issue_severity', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21461, 2, '重要', '2', 'pms_srv_issue_severity', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21462, 3, '一般', '3', 'pms_srv_issue_severity', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-巡检问题状态 (pms_srv_issue_status)
(21470, 0, '待处理', '0', 'pms_srv_issue_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21471, 1, '处理中', '1', 'pms_srv_issue_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21472, 2, '已解决', '2', 'pms_srv_issue_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21473, 3, '已关闭', '3', 'pms_srv_issue_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-维保状态 (pms_srv_maintenance_status)
(21480, 0, '待开始', '0', 'pms_srv_maintenance_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21481, 1, '维保中', '1', 'pms_srv_maintenance_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21482, 2, '已到期', '2', 'pms_srv_maintenance_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21483, 3, '已终止', '3', 'pms_srv_maintenance_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21484, 4, '已完成', '4', 'pms_srv_maintenance_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-服务等级 (pms_service_level)
(21490, 1, '金牌', 'GOLD', 'pms_service_level', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21491, 2, '银牌', 'SILVER', 'pms_service_level', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21492, 3, '铜牌', 'BRONZE', 'pms_service_level', 0, 'default', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21493, 4, '标准', 'STANDARD', 'pms_service_level', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-设备状态 (pms_equipment_status)
(21500, 0, '在库', '0', 'pms_equipment_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21501, 1, '在用', '1', 'pms_equipment_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21502, 2, '维修中', '2', 'pms_equipment_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21503, 3, '已报废', '3', 'pms_equipment_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21504, 4, '已借出', '4', 'pms_equipment_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-批量变更状态 (pms_batch_change_status)
(21510, 0, '待执行', '0', 'pms_batch_change_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21511, 1, '执行中', '1', 'pms_batch_change_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21512, 2, '已完成', '2', 'pms_batch_change_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21513, 3, '已失败', '3', 'pms_batch_change_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0');
