-- =============================================================================
-- V46: PMS 字典类型补充（V43/V44/V45 遗漏的业务字段字典化）
-- 背景：项目风险等级/状态、项目阶段状态、实施问题来源、项目组合类型/分类/规则、
--      需求类型、治理动作类型、货币在前端为硬编码选项，需字典化统一管理。
--      前端通过 DICT_TYPE.PMS_* 引用，使用 getIntDictOptions/getStrDictOptions 获取选项。
-- =============================================================================

-- ============================================================
-- 1. 字典类型（system_dict_type）
-- ============================================================
INSERT IGNORE INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
(2104, 'PMS-项目风险等级', 'pms_project_risk_level', 0, '项目风险等级(高/中/低)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2105, 'PMS-项目风险状态', 'pms_project_risk_status', 0, '项目风险状态(已识别/处理中/已关闭/已发生)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2106, 'PMS-项目阶段状态', 'pms_project_phase_status', 0, '项目阶段状态(未开始/进行中/已完成/已跳过)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2107, 'PMS-问题来源', 'pms_issue_source', 0, '实施问题来源(安装/配置/联调/其他)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2108, 'PMS-项目组合类型', 'pms_portfolio_type', 0, '项目组合类型(静态/动态)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2109, 'PMS-项目组合分类', 'pms_portfolio_category', 0, '项目组合分类(战略/客户/区域/计划/专项)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2110, 'PMS-组合规则维度', 'pms_portfolio_rule_dimension', 0, '组合规则维度(客户/区域/项目类型/状态)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2111, 'PMS-组合规则操作符', 'pms_portfolio_rule_operator', 0, '组合规则操作符(等于/不等于/包含/模糊)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2112, 'PMS-需求类型', 'pms_requirement_type', 0, '需求类型(业务需求/接口规划)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2113, 'PMS-治理动作类型', 'pms_governance_action_type', 0, '治理动作类型(回退总部/直接关闭)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2114, 'PMS-货币', 'pms_currency', 0, '货币类型(人民币/美元/欧元)', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

-- ============================================================
-- 2. 字典数据（system_dict_data）
-- ============================================================
INSERT IGNORE INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- PMS-项目风险等级 (pms_project_risk_level)
(22000, 1, '高', 'HIGH', 'pms_project_risk_level', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22001, 2, '中', 'MEDIUM', 'pms_project_risk_level', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22002, 3, '低', 'LOW', 'pms_project_risk_level', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-项目风险状态 (pms_project_risk_status)
(22010, 0, '已识别', '0', 'pms_project_risk_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22011, 1, '处理中', '1', 'pms_project_risk_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22012, 2, '已关闭', '2', 'pms_project_risk_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22013, 3, '已发生', '3', 'pms_project_risk_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-项目阶段状态 (pms_project_phase_status)
(22020, 0, '未开始', '0', 'pms_project_phase_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22021, 1, '进行中', '1', 'pms_project_phase_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22022, 2, '已完成', '2', 'pms_project_phase_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22023, 3, '已跳过', '3', 'pms_project_phase_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-问题来源 (pms_issue_source)
(22030, 1, '安装', 'INSTALLATION', 'pms_issue_source', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22031, 2, '配置', 'CONFIGURATION', 'pms_issue_source', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22032, 3, '联调', 'JOINT_TEST', 'pms_issue_source', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22033, 4, '其他', 'OTHER', 'pms_issue_source', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-项目组合类型 (pms_portfolio_type)
(22040, 1, '静态', 'STATIC', 'pms_portfolio_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22041, 2, '动态', 'DYNAMIC', 'pms_portfolio_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-项目组合分类 (pms_portfolio_category)
(22050, 1, '战略', '战略', 'pms_portfolio_category', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22051, 2, '客户', '客户', 'pms_portfolio_category', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22052, 3, '区域', '区域', 'pms_portfolio_category', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22053, 4, '计划', '计划', 'pms_portfolio_category', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22054, 5, '专项', '专项', 'pms_portfolio_category', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-组合规则维度 (pms_portfolio_rule_dimension)
(22060, 1, '客户', 'CUSTOMER', 'pms_portfolio_rule_dimension', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22061, 2, '区域(行业)', 'REGION', 'pms_portfolio_rule_dimension', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22062, 3, '项目类型', 'TYPE', 'pms_portfolio_rule_dimension', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22063, 4, '状态', 'STATUS', 'pms_portfolio_rule_dimension', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-组合规则操作符 (pms_portfolio_rule_operator)
(22070, 1, '等于', 'EQ', 'pms_portfolio_rule_operator', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22071, 2, '不等于', 'NE', 'pms_portfolio_rule_operator', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22072, 3, '包含', 'IN', 'pms_portfolio_rule_operator', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22073, 4, '模糊', 'LIKE', 'pms_portfolio_rule_operator', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-需求类型 (pms_requirement_type)
(22080, 1, '业务需求', 'BUSINESS', 'pms_requirement_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22081, 2, '接口规划', 'INTERFACE', 'pms_requirement_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-治理动作类型 (pms_governance_action_type)
(22090, 1, '回退总部', 'ROLLBACK', 'pms_governance_action_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22091, 2, '直接关闭', 'DIRECT_CLOSE', 'pms_governance_action_type', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-货币 (pms_currency)
(22100, 1, '人民币(CNY)', 'CNY', 'pms_currency', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22101, 2, '美元(USD)', 'USD', 'pms_currency', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(22102, 3, '欧元(EUR)', 'EUR', 'pms_currency', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0');
