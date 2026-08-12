package cn.iocoder.yudao.module.pms.engineering.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 工程实施域错误码常量
 *
 * engineering 模块，使用 1-011-000-000 段，每个子域 1000 个码：
 * - site-survey:    1-011-001-000
 * - requirement:    1-011-002-000
 * - solution:       1-011-003-000
 * - arrival:        1-011-004-000
 * - installation:   1-011-005-000
 * - configuration:  1-011-006-000
 * - joint-test:     1-011-007-000
 * - issue:          1-011-008-000
 * - resource:       1-011-009-000
 * - deliverable:    1-011-010-000
 * - outsource:      1-011-011-000 (V2 FR-ENG-002)
 * - material-req:   1-011-012-000 (V2 FR-ENG-002)
 * - ext-proc:       1-011-013-000 (V2 FR-ENG-002)
 * - material-exch:  1-011-014-000 (V2 FR-ENG-003)
 * - briefing:       1-011-015-000 (V2 FR-ENG-006)
 * - form-template:  1-011-016-000 (V2 FR-ENG-007)
 * - form-instance:  1-011-017-000 (V2 FR-ENG-007)
 * - risk:           1-011-018-000 (V2 FR-ENG-008)
 * - announcement:   1-011-019-000 (V2 FR-ENG-009)
 * - ann-check:      1-011-020-000 (V2 FR-ENG-009)
 * - authorization:  1-011-021-000 (V2 FR-ENG-010)
 * - doc-template:   1-011-022-000 (V36 结构化文档模板)
 */
public interface ErrorCodeConstants {

    // ========== 现场工勘 site-survey 1-011-001-000 ==========
    ErrorCode SITE_SURVEY_NOT_EXISTS = new ErrorCode(1_011_001_000, "现场工勘不存在");
    ErrorCode SITE_SURVEY_CODE_DUPLICATE = new ErrorCode(1_011_001_001, "工勘编码已存在");
    ErrorCode SITE_SURVEY_STATUS_INVALID = new ErrorCode(1_011_001_002, "工勘当前状态不允许该操作");
    ErrorCode SITE_SURVEY_VERSION_NOT_MATCH = new ErrorCode(1_011_001_003, "工勘版本号已变更，请刷新后重试");

    // ========== 需求分析 requirement 1-011-002-000 ==========
    ErrorCode REQUIREMENT_NOT_EXISTS = new ErrorCode(1_011_002_000, "需求分析不存在");
    ErrorCode REQUIREMENT_CODE_DUPLICATE = new ErrorCode(1_011_002_001, "需求编码已存在");
    ErrorCode REQUIREMENT_STATUS_INVALID = new ErrorCode(1_011_002_002, "需求当前状态不允许该操作");
    ErrorCode REQUIREMENT_VERSION_NOT_MATCH = new ErrorCode(1_011_002_003, "需求版本号已变更，请刷新后重试");

    // ========== 实施方案 solution 1-011-003-000 ==========
    ErrorCode SOLUTION_NOT_EXISTS = new ErrorCode(1_011_003_000, "实施方案不存在");
    ErrorCode SOLUTION_CODE_DUPLICATE = new ErrorCode(1_011_003_001, "方案编码已存在");
    ErrorCode SOLUTION_STATUS_INVALID = new ErrorCode(1_011_003_002, "方案当前状态不允许该操作");
    ErrorCode SOLUTION_VERSION_NOT_MATCH = new ErrorCode(1_011_003_003, "方案版本号已变更，请刷新后重试");
    ErrorCode SOLUTION_PROJECT_NOT_EXISTS = new ErrorCode(1_011_003_004, "所属项目不存在");

    // ========== 到货签收 arrival 1-011-004-000 ==========
    ErrorCode ARRIVAL_NOT_EXISTS = new ErrorCode(1_011_004_000, "到货签收不存在");
    ErrorCode ARRIVAL_CODE_DUPLICATE = new ErrorCode(1_011_004_001, "签收编码已存在");
    ErrorCode ARRIVAL_STATUS_INVALID = new ErrorCode(1_011_004_002, "签收当前状态不允许该操作");
    ErrorCode ARRIVAL_VERSION_NOT_MATCH = new ErrorCode(1_011_004_003, "签收版本号已变更，请刷新后重试");

    // ========== 硬件安装 installation 1-011-005-000 ==========
    ErrorCode INSTALLATION_NOT_EXISTS = new ErrorCode(1_011_005_000, "硬件安装记录不存在");
    ErrorCode INSTALLATION_CODE_DUPLICATE = new ErrorCode(1_011_005_001, "安装编码已存在");
    ErrorCode INSTALLATION_STATUS_INVALID = new ErrorCode(1_011_005_002, "安装当前状态不允许该操作");
    ErrorCode INSTALLATION_VERSION_NOT_MATCH = new ErrorCode(1_011_005_003, "安装版本号已变更，请刷新后重试");

    // ========== 配置调试 configuration 1-011-006-000 ==========
    ErrorCode CONFIGURATION_NOT_EXISTS = new ErrorCode(1_011_006_000, "配置调试记录不存在");
    ErrorCode CONFIGURATION_CODE_DUPLICATE = new ErrorCode(1_011_006_001, "配置编码已存在");
    ErrorCode CONFIGURATION_STATUS_INVALID = new ErrorCode(1_011_006_002, "配置当前状态不允许该操作");
    ErrorCode CONFIGURATION_VERSION_NOT_MATCH = new ErrorCode(1_011_006_003, "配置版本号已变更，请刷新后重试");

    // ========== 业务联调 joint-test 1-011-007-000 ==========
    ErrorCode JOINT_TEST_NOT_EXISTS = new ErrorCode(1_011_007_000, "业务联调记录不存在");
    ErrorCode JOINT_TEST_CODE_DUPLICATE = new ErrorCode(1_011_007_001, "联调编码已存在");
    ErrorCode JOINT_TEST_STATUS_INVALID = new ErrorCode(1_011_007_002, "联调当前状态不允许该操作");
    ErrorCode JOINT_TEST_VERSION_NOT_MATCH = new ErrorCode(1_011_007_003, "联调版本号已变更，请刷新后重试");

    // ========== 实施问题 issue 1-011-008-000 ==========
    ErrorCode ISSUE_NOT_EXISTS = new ErrorCode(1_011_008_000, "实施问题不存在");
    ErrorCode ISSUE_CODE_DUPLICATE = new ErrorCode(1_011_008_001, "问题编码已存在");
    ErrorCode ISSUE_STATUS_INVALID = new ErrorCode(1_011_008_002, "问题当前状态不允许该操作");
    ErrorCode ISSUE_VERSION_NOT_MATCH = new ErrorCode(1_011_008_003, "问题版本号已变更，请刷新后重试");
    ErrorCode ISSUE_ACCEPTANCE_NOT_PASSED = new ErrorCode(1_011_008_004, "存在未关闭的实施问题，无法通过验收");

    // ========== 资源就绪 resource 1-011-009-000 ==========
    ErrorCode RESOURCE_READY_NOT_EXISTS = new ErrorCode(1_011_009_000, "资源就绪记录不存在");
    ErrorCode RESOURCE_READY_CODE_DUPLICATE = new ErrorCode(1_011_009_001, "就绪编码已存在");
    ErrorCode RESOURCE_READY_STATUS_INVALID = new ErrorCode(1_011_009_002, "资源当前状态不允许该操作");
    ErrorCode RESOURCE_READY_VERSION_NOT_MATCH = new ErrorCode(1_011_009_003, "资源版本号已变更，请刷新后重试");

    // ========== 交付件归集 deliverable 1-011-010-000 ==========
    ErrorCode DELIVERABLE_NOT_EXISTS = new ErrorCode(1_011_010_000, "交付件不存在");
    ErrorCode DELIVERABLE_CODE_DUPLICATE = new ErrorCode(1_011_010_001, "交付件编码已存在");
    ErrorCode DELIVERABLE_STATUS_INVALID = new ErrorCode(1_011_010_002, "交付件当前状态不允许该操作");
    ErrorCode DELIVERABLE_VERSION_NOT_MATCH = new ErrorCode(1_011_010_003, "交付件版本号已变更，请刷新后重试");

    // ========== 外包申请 outsource 1-011-011-000 ==========
    ErrorCode OUTSOURCE_NOT_EXISTS = new ErrorCode(1_011_011_000, "外包申请不存在");
    ErrorCode OUTSOURCE_CODE_DUPLICATE = new ErrorCode(1_011_011_001, "外包单号已存在");
    ErrorCode OUTSOURCE_STATUS_INVALID = new ErrorCode(1_011_011_002, "外包申请当前状态不允许该操作");
    ErrorCode OUTSOURCE_VERSION_NOT_MATCH = new ErrorCode(1_011_011_003, "外包申请版本号已变更，请刷新后重试");
    ErrorCode OUTSOURCE_PROJECT_NOT_EXISTS = new ErrorCode(1_011_011_004, "所属项目不存在");

    // ========== OA领料 material-req 1-011-012-000 ==========
    ErrorCode MATERIAL_REQ_NOT_EXISTS = new ErrorCode(1_011_012_000, "领料申请不存在");
    ErrorCode MATERIAL_REQ_CODE_DUPLICATE = new ErrorCode(1_011_012_001, "领料单号已存在");
    ErrorCode MATERIAL_REQ_STATUS_INVALID = new ErrorCode(1_011_012_002, "领料申请当前状态不允许该操作");
    ErrorCode MATERIAL_REQ_VERSION_NOT_MATCH = new ErrorCode(1_011_012_003, "领料申请版本号已变更，请刷新后重试");
    ErrorCode MATERIAL_REQ_PROJECT_NOT_EXISTS = new ErrorCode(1_011_012_004, "所属项目不存在");

    // ========== 外采 ext-proc 1-011-013-000 ==========
    ErrorCode EXT_PROC_NOT_EXISTS = new ErrorCode(1_011_013_000, "外采申请不存在");
    ErrorCode EXT_PROC_CODE_DUPLICATE = new ErrorCode(1_011_013_001, "外采单号已存在");
    ErrorCode EXT_PROC_STATUS_INVALID = new ErrorCode(1_011_013_002, "外采申请当前状态不允许该操作");
    ErrorCode EXT_PROC_VERSION_NOT_MATCH = new ErrorCode(1_011_013_003, "外采申请版本号已变更，请刷新后重试");
    ErrorCode EXT_PROC_PROJECT_NOT_EXISTS = new ErrorCode(1_011_013_004, "所属项目不存在");

    // ========== 物料换货 material-exch 1-011-014-000 ==========
    ErrorCode MATERIAL_EXCH_NOT_EXISTS = new ErrorCode(1_011_014_000, "换货协同单不存在");
    ErrorCode MATERIAL_EXCH_CODE_DUPLICATE = new ErrorCode(1_011_014_001, "换货单号已存在");
    ErrorCode MATERIAL_EXCH_STATUS_INVALID = new ErrorCode(1_011_014_002, "换货协同单当前状态不允许该操作");
    ErrorCode MATERIAL_EXCH_VERSION_NOT_MATCH = new ErrorCode(1_011_014_003, "换货协同单版本号已变更，请刷新后重试");
    ErrorCode MATERIAL_EXCH_PROJECT_NOT_EXISTS = new ErrorCode(1_011_014_004, "所属项目不存在");
    ErrorCode MATERIAL_EXCH_CRM_ALREADY_PUSHED = new ErrorCode(1_011_014_005, "换货协同单已推送CRM，无法重复推送");

    // ========== 工程交底书 briefing 1-011-015-000 ==========
    ErrorCode BRIEFING_NOT_EXISTS = new ErrorCode(1_011_015_000, "工程交底书不存在");
    ErrorCode BRIEFING_CODE_DUPLICATE = new ErrorCode(1_011_015_001, "交底书编号已存在");
    ErrorCode BRIEFING_STATUS_INVALID = new ErrorCode(1_011_015_002, "交底书当前状态不允许该操作");
    ErrorCode BRIEFING_VERSION_NOT_MATCH = new ErrorCode(1_011_015_003, "交底书版本号已变更，请刷新后重试");
    ErrorCode BRIEFING_PROJECT_NOT_EXISTS = new ErrorCode(1_011_015_004, "所属项目不存在");
    ErrorCode BRIEFING_TEMPLATE_NOT_EXISTS = new ErrorCode(1_011_015_005, "关联交底书模板不存在");

    // ========== 准备数据表单模板 form-template 1-011-016-000 ==========
    ErrorCode FORM_TEMPLATE_NOT_EXISTS = new ErrorCode(1_011_016_000, "表单模板不存在");
    ErrorCode FORM_TEMPLATE_CODE_DUPLICATE = new ErrorCode(1_011_016_001, "模板编号已存在");
    ErrorCode FORM_TEMPLATE_STATUS_INVALID = new ErrorCode(1_011_016_002, "表单模板当前状态不允许该操作");
    ErrorCode FORM_TEMPLATE_VERSION_NOT_MATCH = new ErrorCode(1_011_016_003, "表单模板版本号已变更，请刷新后重试");

    // ========== 表单实例 form-instance 1-011-017-000 ==========
    ErrorCode FORM_INSTANCE_NOT_EXISTS = new ErrorCode(1_011_017_000, "表单实例不存在");
    ErrorCode FORM_INSTANCE_CODE_DUPLICATE = new ErrorCode(1_011_017_001, "实例编号已存在");
    ErrorCode FORM_INSTANCE_STATUS_INVALID = new ErrorCode(1_011_017_002, "表单实例当前状态不允许该操作");
    ErrorCode FORM_INSTANCE_VERSION_NOT_MATCH = new ErrorCode(1_011_017_003, "表单实例版本号已变更，请刷新后重试");
    ErrorCode FORM_INSTANCE_PROJECT_NOT_EXISTS = new ErrorCode(1_011_017_004, "所属项目不存在");
    ErrorCode FORM_INSTANCE_TEMPLATE_NOT_EXISTS = new ErrorCode(1_011_017_005, "关联表单模板不存在");

    // ========== 单机风险 risk 1-011-018-000 ==========
    ErrorCode RISK_NOT_EXISTS = new ErrorCode(1_011_018_000, "单机风险不存在");
    ErrorCode RISK_CODE_DUPLICATE = new ErrorCode(1_011_018_001, "风险编号已存在");
    ErrorCode RISK_STATUS_INVALID = new ErrorCode(1_011_018_002, "风险当前状态不允许该操作");
    ErrorCode RISK_VERSION_NOT_MATCH = new ErrorCode(1_011_018_003, "风险版本号已变更，请刷新后重试");
    ErrorCode RISK_PROJECT_NOT_EXISTS = new ErrorCode(1_011_018_004, "所属项目不存在");
    ErrorCode RISK_CRM_ALREADY_SYNCED = new ErrorCode(1_011_018_005, "风险已同步CRM，无法重复同步");

    // ========== 技术公告 announcement 1-011-019-000 ==========
    ErrorCode ANNOUNCEMENT_NOT_EXISTS = new ErrorCode(1_011_019_000, "技术公告不存在");
    ErrorCode ANNOUNCEMENT_CODE_DUPLICATE = new ErrorCode(1_011_019_001, "公告编号已存在");
    ErrorCode ANNOUNCEMENT_STATUS_INVALID = new ErrorCode(1_011_019_002, "技术公告当前状态不允许该操作");
    ErrorCode ANNOUNCEMENT_VERSION_NOT_MATCH = new ErrorCode(1_011_019_003, "技术公告版本号已变更，请刷新后重试");

    // ========== 公告预检查 announcement-check 1-011-020-000 ==========
    ErrorCode ANN_CHECK_NOT_EXISTS = new ErrorCode(1_011_020_000, "预检查记录不存在");
    ErrorCode ANN_CHECK_CODE_DUPLICATE = new ErrorCode(1_011_020_001, "检查编号已存在");
    ErrorCode ANN_CHECK_STATUS_INVALID = new ErrorCode(1_011_020_002, "预检查记录当前状态不允许该操作");
    ErrorCode ANN_CHECK_VERSION_NOT_MATCH = new ErrorCode(1_011_020_003, "预检查记录版本号已变更，请刷新后重试");
    ErrorCode ANN_CHECK_PROJECT_NOT_EXISTS = new ErrorCode(1_011_020_004, "所属项目不存在");
    ErrorCode ANN_CHECK_ANNOUNCEMENT_NOT_EXISTS = new ErrorCode(1_011_020_005, "关联技术公告不存在");

    // ========== 授权与借货 authorization 1-011-021-000 ==========
    ErrorCode AUTHORIZATION_NOT_EXISTS = new ErrorCode(1_011_021_000, "授权记录不存在");
    ErrorCode AUTHORIZATION_CODE_DUPLICATE = new ErrorCode(1_011_021_001, "授权编号已存在");
    ErrorCode AUTHORIZATION_STATUS_INVALID = new ErrorCode(1_011_021_002, "授权当前状态不允许该操作");
    ErrorCode AUTHORIZATION_VERSION_NOT_MATCH = new ErrorCode(1_011_021_003, "授权版本号已变更，请刷新后重试");
    ErrorCode AUTHORIZATION_PROJECT_NOT_EXISTS = new ErrorCode(1_011_021_004, "所属项目不存在");

    // ========== 文档模板 doc-template 1-011-022-000 ==========
    ErrorCode DOC_TEMPLATE_NOT_EXISTS = new ErrorCode(1_011_022_000, "文档模板不存在");
    ErrorCode DOC_TEMPLATE_CODE_DUPLICATE = new ErrorCode(1_011_022_001, "模板编号已存在");
    ErrorCode DOC_TEMPLATE_STATUS_INVALID = new ErrorCode(1_011_022_002, "文档模板当前状态不允许该操作");
    ErrorCode DOC_TEMPLATE_VERSION_NOT_MATCH = new ErrorCode(1_011_022_003, "文档模板版本号已变更，请刷新后重试");
    ErrorCode DOC_TEMPLATE_VERSION_NOT_EXISTS = new ErrorCode(1_011_022_004, "模板版本不存在");
    ErrorCode DOC_TEMPLATE_VERSION_LABEL_DUPLICATE = new ErrorCode(1_011_022_005, "版本标签已存在");
    ErrorCode DOC_TEMPLATE_VERSION_PUBLISHED = new ErrorCode(1_011_022_006, "已发布版本不可修改");
    ErrorCode DOC_TEMPLATE_PARENT_NOT_EXISTS = new ErrorCode(1_011_022_007, "父模板不存在");
    ErrorCode DOC_TEMPLATE_NO_PUBLISHED_VERSION = new ErrorCode(1_011_022_008, "模板尚无已发布版本，无法使用");

}
