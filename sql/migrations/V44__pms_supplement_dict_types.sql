-- =============================================================================
-- V44: PMS 字典类型补充（V43 遗漏的业务状态/类型）
-- 背景：V43 仅创建了通用字典类型，大量业务实体有独立的状态枚举未被覆盖。
--      本迁移补充缺失的字典类型及数据，使前端可全面字典化。
--      前端通过 DICT_TYPE.PMS_* 引用，使用 getIntDictOptions/getStrDictOptions 获取选项。
-- =============================================================================

-- ============================================================
-- 1. 字典类型（system_dict_type）
-- ============================================================
INSERT IGNORE INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
-- 项目域补充
(2070, 'PMS-审批流转状态', 'pms_approval_status', 0, '通用审批流转状态(草稿/已提交/审批中/已通过/已驳回/已撤回/已终止)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2071, 'PMS-项目组合状态', 'pms_portfolio_status', 0, '项目组合状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2072, 'PMS-治理动作状态', 'pms_governance_status', 0, '项目治理动作状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2073, 'PMS-转维保状态', 'pms_maint_transition_status', 0, '转维保状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2074, 'PMS-服务等级状态', 'pms_srv_level_status', 0, '客户服务等级状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2075, 'PMS-完工证明状态', 'pms_completion_cert_status', 0, '完工证明状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2076, 'PMS-项目闭环状态', 'pms_closure_status', 0, '项目闭环状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2077, 'PMS-倒排计划状态', 'pms_schedule_status', 0, '倒排计划状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2078, 'PMS-计划变更类型', 'pms_plan_change_type', 0, '计划变更类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
-- 工程域补充
(2079, 'PMS-到货签收状态', 'pms_arrival_status', 0, '到货签收状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2080, 'PMS-工勘状态', 'pms_site_survey_status', 0, '现场工勘状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2081, 'PMS-交底状态', 'pms_briefing_status', 0, '工程交底状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2082, 'PMS-公告检查状态', 'pms_ann_check_status', 0, '公告检查状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2083, 'PMS-公告状态', 'pms_announcement_status', 0, '技术公告状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2084, 'PMS-联调状态', 'pms_joint_test_status', 0, '联调测试状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2085, 'PMS-需求状态', 'pms_requirement_status', 0, '需求状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2086, 'PMS-资源就绪状态', 'pms_resource_status', 0, '工程资源就绪状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2087, 'PMS-工程风险状态', 'pms_eng_risk_status', 0, '工程风险状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2088, 'PMS-文档模板状态', 'pms_doc_template_status', 0, '文档模板状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2089, 'PMS-外采类型', 'pms_ext_proc_type', 0, '外采类型(物资/服务)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2090, 'PMS-物料申请类型', 'pms_material_req_type', 0, '物料申请类型(备件/工具/耗材)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2091, 'PMS-物料更换类型', 'pms_material_exch_type', 0, '物料更换类型', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2092, 'PMS-外协类型', 'pms_outsource_type', 0, '外协类型(劳务/服务/子项目)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2093, 'PMS-工程风险类型', 'pms_eng_risk_type', 0, '工程风险类型(单机/场景)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2094, 'PMS-公告严重等级', 'pms_announcement_severity', 0, '技术公告严重等级', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2095, 'PMS-公告命中结果', 'pms_ann_check_match', 0, '公告命中结果', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2096, 'PMS-文档分类', 'pms_doc_category', 0, '文档分类', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2097, 'PMS-工程风险等级', 'pms_eng_risk_level', 0, '工程风险等级(高/中/低)', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2098, 'PMS-CRM同步状态', 'pms_crm_sync_status', 0, 'CRM同步状态', 'admin', NOW(), 'admin', NOW(), b'0', NULL),
(2099, 'PMS-生命周期标识', 'pms_eom_type', 0, '产品生命周期标识(EOS/EOM/NONE)', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

-- ============================================================
-- 2. 字典数据（system_dict_data）
-- ============================================================
INSERT IGNORE INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- PMS-审批流转状态 (pms_approval_status)
(21600, 0, '草稿', '0', 'pms_approval_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21601, 1, '已提交', '1', 'pms_approval_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21602, 2, '审批中', '2', 'pms_approval_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21603, 3, '已通过', '3', 'pms_approval_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21604, 4, '已驳回', '4', 'pms_approval_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21605, 5, '已撤回', '5', 'pms_approval_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21606, 6, '已终止', '6', 'pms_approval_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-项目组合状态 (pms_portfolio_status)
(21610, 0, '草稿', '0', 'pms_portfolio_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21611, 1, '已发布', '1', 'pms_portfolio_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21612, 2, '已归档', '2', 'pms_portfolio_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-治理动作状态 (pms_governance_status)
(21620, 0, '草稿', '0', 'pms_governance_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21621, 1, '已提交', '1', 'pms_governance_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21622, 2, '审批中', '2', 'pms_governance_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21623, 3, '已执行', '3', 'pms_governance_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21624, 4, '已驳回', '4', 'pms_governance_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21625, 5, '已撤回', '5', 'pms_governance_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-转维保状态 (pms_maint_transition_status)
(21630, 0, '草稿', '0', 'pms_maint_transition_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21631, 1, '待生效', '1', 'pms_maint_transition_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21632, 2, '生效中', '2', 'pms_maint_transition_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21633, 3, '已过期', '3', 'pms_maint_transition_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21634, 4, '已续保', '4', 'pms_maint_transition_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-服务等级状态 (pms_srv_level_status)
(21640, 0, '草稿', '0', 'pms_srv_level_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21641, 1, '已生效', '1', 'pms_srv_level_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21642, 2, '已停用', '2', 'pms_srv_level_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21643, 3, '已归档', '3', 'pms_srv_level_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-完工证明状态 (pms_completion_cert_status)
(21650, 0, '草稿', '0', 'pms_completion_cert_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21651, 1, '待客户确认', '1', 'pms_completion_cert_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21652, 2, '客户已确认', '2', 'pms_completion_cert_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21653, 3, '已归档', '3', 'pms_completion_cert_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21654, 4, '已驳回', '4', 'pms_completion_cert_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-项目闭环状态 (pms_closure_status)
(21660, 0, '草稿', '0', 'pms_closure_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21661, 1, '待审批', '1', 'pms_closure_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21662, 2, '审批中', '2', 'pms_closure_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21663, 3, '已通过', '3', 'pms_closure_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21664, 4, '已驳回', '4', 'pms_closure_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21665, 5, '已归档', '5', 'pms_closure_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-倒排计划状态 (pms_schedule_status)
(21670, 0, '草稿', '0', 'pms_schedule_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21671, 1, '已计算', '1', 'pms_schedule_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21672, 2, '已应用', '2', 'pms_schedule_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21673, 3, '已驳回', '3', 'pms_schedule_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-计划变更类型 (pms_plan_change_type)
(21680, 1, '计划调整', 'PLAN_ADJUST', 'pms_plan_change_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21681, 2, '范围变更', 'SCOPE_CHANGE', 'pms_plan_change_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21682, 3, '工期顺延', 'DATE_SHIFT', 'pms_plan_change_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21683, 4, '其他', 'OTHER', 'pms_plan_change_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-到货签收状态 (pms_arrival_status)
(21690, 0, '待签收', '0', 'pms_arrival_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21691, 1, '已签收', '1', 'pms_arrival_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21692, 2, '异常', '2', 'pms_arrival_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-工勘状态 (pms_site_survey_status)
(21700, 0, '草稿', '0', 'pms_site_survey_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21701, 1, '已确认', '1', 'pms_site_survey_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21702, 2, '已驳回', '2', 'pms_site_survey_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21703, 3, '已归档', '3', 'pms_site_survey_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-交底状态 (pms_briefing_status)
(21710, 0, '草稿', '0', 'pms_briefing_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21711, 1, '已生成', '1', 'pms_briefing_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21712, 2, '已审核', '2', 'pms_briefing_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21713, 3, '已发布', '3', 'pms_briefing_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21714, 4, '已作废', '4', 'pms_briefing_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-公告检查状态 (pms_ann_check_status)
(21720, 0, '待检查', '0', 'pms_ann_check_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21721, 1, '已检查', '1', 'pms_ann_check_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21722, 2, '已处置', '2', 'pms_ann_check_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21723, 3, '已忽略', '3', 'pms_ann_check_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-公告状态 (pms_announcement_status)
(21730, 0, '草稿', '0', 'pms_announcement_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21731, 1, '已发布', '1', 'pms_announcement_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21732, 2, '已停用', '2', 'pms_announcement_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-联调状态 (pms_joint_test_status)
(21740, 0, '待联调', '0', 'pms_joint_test_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21741, 1, '进行中', '1', 'pms_joint_test_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21742, 2, '通过', '2', 'pms_joint_test_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21743, 3, '失败', '3', 'pms_joint_test_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-需求状态 (pms_requirement_status)
(21750, 0, '草稿', '0', 'pms_requirement_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21751, 1, '已提交', '1', 'pms_requirement_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21752, 2, '已生效', '2', 'pms_requirement_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21753, 3, '已归档', '3', 'pms_requirement_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-资源就绪状态 (pms_resource_status)
(21760, 0, '未就绪', '0', 'pms_resource_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21761, 1, '已就绪', '1', 'pms_resource_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21762, 2, '异常', '2', 'pms_resource_status', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-工程风险状态 (pms_eng_risk_status)
(21770, 0, '草稿', '0', 'pms_eng_risk_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21771, 1, '已识别', '1', 'pms_eng_risk_status', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21772, 2, '已确认', '2', 'pms_eng_risk_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21773, 3, '已同步CRM', '3', 'pms_eng_risk_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21774, 4, '已关闭', '4', 'pms_eng_risk_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-文档模板状态 (pms_doc_template_status)
(21780, 0, '草稿', '0', 'pms_doc_template_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21781, 1, '已发布', '1', 'pms_doc_template_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21782, 2, '已停用', '2', 'pms_doc_template_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-外采类型 (pms_ext_proc_type)
(21790, 1, '物资', 'GOODS', 'pms_ext_proc_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21791, 2, '服务', 'SERVICE', 'pms_ext_proc_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-物料申请类型 (pms_material_req_type)
(21800, 1, '备件', 'SPARE', 'pms_material_req_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21801, 2, '工具', 'TOOL', 'pms_material_req_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21802, 3, '耗材', 'CONSUMABLE', 'pms_material_req_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-物料更换类型 (pms_material_exch_type)
(21810, 1, '物料不适配', 'INCOMPATIBLE', 'pms_material_exch_type', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21811, 2, '到货损坏', 'DAMAGE', 'pms_material_exch_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21812, 3, '发货错误', 'WRONG', 'pms_material_exch_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21813, 4, '其他', 'OTHER', 'pms_material_exch_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-外协类型 (pms_outsource_type)
(21820, 1, '劳务', 'LABOR', 'pms_outsource_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21821, 2, '服务', 'SERVICE', 'pms_outsource_type', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21822, 3, '子项目', 'PROJECT_SUBPROJ', 'pms_outsource_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-工程风险类型 (pms_eng_risk_type)
(21830, 1, '单机', 'SINGLE_DEVICE', 'pms_eng_risk_type', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21831, 2, '场景', 'SCENARIO', 'pms_eng_risk_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-公告严重等级 (pms_announcement_severity)
(21840, 1, '严重', 'CRITICAL', 'pms_announcement_severity', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21841, 2, '高', 'HIGH', 'pms_announcement_severity', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21842, 3, '中', 'MEDIUM', 'pms_announcement_severity', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21843, 4, '低', 'LOW', 'pms_announcement_severity', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-公告命中结果 (pms_ann_check_match)
(21850, 1, '命中', 'HIT', 'pms_ann_check_match', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21851, 2, '未命中', 'MISS', 'pms_ann_check_match', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21852, 3, '未知', 'UNKNOWN', 'pms_ann_check_match', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-文档分类 (pms_doc_category)
(21860, 1, '需求分析', 'REQUIREMENT', 'pms_doc_category', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21861, 2, '实施方案', 'SOLUTION', 'pms_doc_category', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-工程风险等级 (pms_eng_risk_level)
(21870, 1, '高', 'HIGH', 'pms_eng_risk_level', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21871, 2, '中', 'MEDIUM', 'pms_eng_risk_level', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21872, 3, '低', 'LOW', 'pms_eng_risk_level', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-CRM同步状态 (pms_crm_sync_status)
(21880, 1, '待推送', 'PENDING', 'pms_crm_sync_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21881, 2, '已推送', 'SENT', 'pms_crm_sync_status', 0, 'primary', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21882, 3, '已接收', 'RECEIVED', 'pms_crm_sync_status', 0, 'success', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21883, 4, '已关闭', 'CLOSED', 'pms_crm_sync_status', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
-- PMS-生命周期标识 (pms_eom_type)
(21890, 1, 'EOS', 'EOS', 'pms_eom_type', 0, 'danger', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21891, 2, 'EOM', 'EOM', 'pms_eom_type', 0, 'warning', '', '', 'admin', NOW(), 'admin', NOW(), b'0'),
(21892, 3, 'NONE', 'NONE', 'pms_eom_type', 0, 'info', '', '', 'admin', NOW(), 'admin', NOW(), b'0');
