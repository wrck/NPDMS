package cn.iocoder.yudao.module.pms.project.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 项目模块错误码枚举类
 *
 * project 模块，使用 1-014-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 客户模块 1-014-001-000 ==========
    ErrorCode CUSTOMER_NOT_EXISTS = new ErrorCode(1_014_001_000, "客户不存在");
    ErrorCode CUSTOMER_CODE_DUPLICATE = new ErrorCode(1_014_001_001, "客户编码已存在");

    // ========== 客户联系人模块 1-014-002-000 ==========
    ErrorCode CUSTOMER_CONTACT_NOT_EXISTS = new ErrorCode(1_014_002_000, "客户联系人不存在");
    ErrorCode CUSTOMER_CONTACT_PRIMARY_DUPLICATE = new ErrorCode(1_014_002_001, "该客户已存在主联系人");
    ErrorCode CUSTOMER_CONTACT_CUSTOMER_NOT_EXISTS = new ErrorCode(1_014_002_002, "所属客户不存在");

    // ========== 项目主表模块 1-014-003-000 ==========
    ErrorCode PROJECT_NOT_EXISTS = new ErrorCode(1_014_003_000, "项目不存在");
    ErrorCode PROJECT_CODE_DUPLICATE = new ErrorCode(1_014_003_001, "项目编码已存在");
    ErrorCode PROJECT_SOURCE_KEY_DUPLICATE = new ErrorCode(1_014_003_002, "项目来源业务键已存在");
    ErrorCode PROJECT_CUSTOMER_NOT_EXISTS = new ErrorCode(1_014_003_003, "项目客户不存在");

    // ========== 项目树模块 1-014-004-000 ==========
    ErrorCode PROJECT_TREE_PARENT_NOT_EXISTS = new ErrorCode(1_014_004_000, "父项目不存在");
    ErrorCode PROJECT_TREE_PARENT_ERROR = new ErrorCode(1_014_004_001, "不能设置自己或子项目为父项目");
    ErrorCode PROJECT_TREE_ROOT_NOT_EXISTS = new ErrorCode(1_014_004_002, "根项目不存在");
    ErrorCode PROJECT_TREE_HAS_CHILDREN = new ErrorCode(1_014_004_003, "存在子项目，无法操作");

    // ========== 项目团队模块 1-014-005-000 ==========
    ErrorCode PROJECT_TEAM_MEMBER_NOT_EXISTS = new ErrorCode(1_014_005_000, "项目团队成员不存在");
    ErrorCode PROJECT_TEAM_MEMBER_DUPLICATE = new ErrorCode(1_014_005_001, "该项目已存在相同角色与用户的成员");
    ErrorCode PROJECT_TEAM_PROJECT_NOT_EXISTS = new ErrorCode(1_014_005_002, "所属项目不存在");

    // ========== 项目任务 WBS 模块 1-014-006-000 ==========
    ErrorCode PROJECT_TASK_NOT_EXISTS = new ErrorCode(1_014_006_000, "项目任务不存在");
    ErrorCode PROJECT_TASK_CODE_DUPLICATE = new ErrorCode(1_014_006_001, "项目内任务编码已存在");
    ErrorCode PROJECT_TASK_PARENT_NOT_EXISTS = new ErrorCode(1_014_006_002, "父任务不存在");
    ErrorCode PROJECT_TASK_PARENT_ERROR = new ErrorCode(1_014_006_003, "不能设置自己或子任务为父任务");
    ErrorCode PROJECT_TASK_HAS_CHILDREN = new ErrorCode(1_014_006_004, "存在子任务，无法删除");
    ErrorCode PROJECT_TASK_PROJECT_NOT_EXISTS = new ErrorCode(1_014_006_005, "所属项目不存在");

    // ========== 项目阶段模块 1-014-007-000 ==========
    ErrorCode PROJECT_PHASE_NOT_EXISTS = new ErrorCode(1_014_007_000, "项目阶段不存在");
    ErrorCode PROJECT_PHASE_CODE_DUPLICATE = new ErrorCode(1_014_007_001, "项目内阶段编码已存在");
    ErrorCode PROJECT_PHASE_TEMPLATE_NOT_EXISTS = new ErrorCode(1_014_007_002, "阶段模板不存在");
    ErrorCode PROJECT_PHASE_GATE_NOT_PASSED = new ErrorCode(1_014_007_003, "阶段【{}】门禁校验未通过：{}");
    ErrorCode PROJECT_PHASE_ALREADY_COMPLETED = new ErrorCode(1_014_007_004, "阶段已完成，无法再次完成");
    ErrorCode PROJECT_PHASE_PROJECT_NOT_EXISTS = new ErrorCode(1_014_007_005, "所属项目不存在");
    ErrorCode PROJECT_PHASE_SEQUENCE_INVALID = new ErrorCode(1_014_007_006, "阶段顺序校验未通过：前序阶段尚未完成或跳过");

    // ========== 阶段模板模块 1-014-008-000 ==========
    ErrorCode PHASE_TEMPLATE_NOT_EXISTS = new ErrorCode(1_014_008_000, "阶段模板不存在");
    ErrorCode PHASE_TEMPLATE_CODE_DUPLICATE = new ErrorCode(1_014_008_001, "阶段模板编码已存在");
    ErrorCode PHASE_TEMPLATE_IN_USE = new ErrorCode(1_014_008_002, "阶段模板已被项目阶段引用，无法删除");

    // ========== 项目风险模块 1-014-009-000 ==========
    ErrorCode PROJECT_RISK_NOT_EXISTS = new ErrorCode(1_014_009_000, "项目风险不存在");
    ErrorCode PROJECT_RISK_STATUS_TRANSITION_INVALID = new ErrorCode(1_014_009_001, "风险状态流转不合法");
    ErrorCode PROJECT_RISK_PROJECT_NOT_EXISTS = new ErrorCode(1_014_009_002, "所属项目不存在");

    // ========== 项目全景模块 1-014-010-000 ==========
    ErrorCode PROJECT_PANORAMIC_NOT_EXISTS = new ErrorCode(1_014_010_000, "项目全景数据不存在");

    // ========== 验收闭环域 ACC 1-014-011-000 ~ 1-014-016-999 ==========
    // 注：任务规范 ACC 域使用 1-014-000-000 段、每实体 1000 段；为避免与现有 001-010 段冲突，
    //     ACC 域从 1-014-011-000 起按 1000 段递进分配。

    // ========== 电子完工证明 1-014-011-000 ==========
    ErrorCode ACC_COMPLETION_CERTIFICATE_NOT_EXISTS = new ErrorCode(1_014_011_000, "电子完工证明不存在");
    ErrorCode ACC_COMPLETION_CERTIFICATE_CODE_DUPLICATE = new ErrorCode(1_014_011_001, "项目内完工证明编码已存在");
    ErrorCode ACC_COMPLETION_CERTIFICATE_STATUS_INVALID = new ErrorCode(1_014_011_002, "完工证明状态流转不合法");

    // ========== 初验/终验 1-014-012-000 ==========
    ErrorCode ACC_ACCEPTANCE_NOT_EXISTS = new ErrorCode(1_014_012_000, "验收记录不存在");
    ErrorCode ACC_ACCEPTANCE_CODE_DUPLICATE = new ErrorCode(1_014_012_001, "项目内验收编码已存在");
    ErrorCode ACC_ACCEPTANCE_STATUS_INVALID = new ErrorCode(1_014_012_002, "验收状态流转不合法");
    ErrorCode ACC_ACCEPTANCE_DELIVERABLE_INCOMPLETE = new ErrorCode(1_014_012_003, "交付件完整性校验未通过，存在未通过的必交交付件");

    // ========== 交付件检查 1-014-013-000 ==========
    ErrorCode ACC_DELIVERABLE_CHECKLIST_NOT_EXISTS = new ErrorCode(1_014_013_000, "交付件检查记录不存在");
    ErrorCode ACC_DELIVERABLE_CHECKLIST_CODE_DUPLICATE = new ErrorCode(1_014_013_001, "项目内交付件编码已存在");
    ErrorCode ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID = new ErrorCode(1_014_013_002, "交付件状态流转不合法");

    // ========== 项目闭环 1-014-014-000 ==========
    ErrorCode ACC_PROJECT_CLOSURE_NOT_EXISTS = new ErrorCode(1_014_014_000, "项目闭环记录不存在");
    ErrorCode ACC_PROJECT_CLOSURE_CODE_DUPLICATE = new ErrorCode(1_014_014_001, "项目内闭环编码已存在");
    ErrorCode ACC_PROJECT_CLOSURE_STATUS_INVALID = new ErrorCode(1_014_014_002, "项目闭环状态流转不合法");
    ErrorCode ACC_PROJECT_CLOSURE_VALIDATION_FAILED = new ErrorCode(1_014_014_003, "项目闭环校验未通过：{}");

    // ========== 归档文档 1-014-015-000 ==========
    ErrorCode ACC_ARCHIVE_DOCUMENT_NOT_EXISTS = new ErrorCode(1_014_015_000, "归档文档不存在");
    ErrorCode ACC_ARCHIVE_DOCUMENT_CODE_DUPLICATE = new ErrorCode(1_014_015_001, "项目内归档文档编码已存在");
    ErrorCode ACC_ARCHIVE_DOCUMENT_STATUS_INVALID = new ErrorCode(1_014_015_002, "归档文档状态流转不合法");

    // ========== 转维保 1-014-016-000 ==========
    ErrorCode ACC_MAINTENANCE_TRANSITION_NOT_EXISTS = new ErrorCode(1_014_016_000, "转维保记录不存在");
    ErrorCode ACC_MAINTENANCE_TRANSITION_CODE_DUPLICATE = new ErrorCode(1_014_016_001, "项目内转维保编码已存在");
    ErrorCode ACC_MAINTENANCE_TRANSITION_STATUS_INVALID = new ErrorCode(1_014_016_002, "转维保状态流转不合法");

    // ========== 项目组合模块 1-014-017-000 ==========
    ErrorCode PORTFOLIO_NOT_EXISTS = new ErrorCode(1_014_017_000, "项目组合不存在");
    ErrorCode PORTFOLIO_CODE_DUPLICATE = new ErrorCode(1_014_017_001, "项目组合编码已存在");
    ErrorCode PORTFOLIO_STATUS_INVALID = new ErrorCode(1_014_017_002, "项目组合状态流转不合法");
    ErrorCode PORTFOLIO_PROJECT_NOT_EXISTS = new ErrorCode(1_014_017_003, "组合成员项目不存在");

    // ========== 客户服务等级模块 1-014-018-000 ==========
    ErrorCode CUSTOMER_SERVICE_LEVEL_NOT_EXISTS = new ErrorCode(1_014_018_000, "客户服务等级不存在");
    ErrorCode CUSTOMER_SERVICE_LEVEL_CUSTOMER_NOT_EXISTS = new ErrorCode(1_014_018_001, "服务等级关联客户不存在");
    ErrorCode CUSTOMER_SERVICE_LEVEL_STATUS_INVALID = new ErrorCode(1_014_018_002, "客户服务等级状态流转不合法");

    // ========== 团队批量变更模块 1-014-019-000 ==========
    ErrorCode TEAM_BATCH_CHANGE_NOT_EXISTS = new ErrorCode(1_014_019_000, "批量变更批次不存在");
    ErrorCode TEAM_BATCH_CHANGE_ITEM_NOT_EXISTS = new ErrorCode(1_014_019_001, "批量变更明细不存在");
    ErrorCode TEAM_BATCH_CHANGE_SOURCE_EQUALS_TARGET = new ErrorCode(1_014_019_002, "源用户与目标用户不能相同");
    ErrorCode TEAM_BATCH_CHANGE_NO_ITEMS = new ErrorCode(1_014_019_003, "未找到待变更的团队成员记录");
    ErrorCode TEAM_BATCH_CHANGE_STATUS_INVALID = new ErrorCode(1_014_019_004, "批量变更状态流转不合法");

    // ========== 工期倒排模块 1-014-020-000 ==========
    ErrorCode SCHEDULE_BACKWARD_NOT_EXISTS = new ErrorCode(1_014_020_000, "工期倒排记录不存在");
    ErrorCode SCHEDULE_BACKWARD_NO_PHASES = new ErrorCode(1_014_020_001, "项目暂无阶段，无法倒排");
    ErrorCode SCHEDULE_BACKWARD_HAS_CONFLICT = new ErrorCode(1_014_020_002, "工期倒排存在冲突，请先处理冲突后再应用");
    ErrorCode SCHEDULE_BACKWARD_STATUS_INVALID = new ErrorCode(1_014_020_003, "工期倒排状态流转不合法");

    // ========== 计划变更审批模块 1-014-021-000 ==========
    ErrorCode PLAN_CHANGE_NOT_EXISTS = new ErrorCode(1_014_021_000, "计划变更记录不存在");
    ErrorCode PLAN_CHANGE_NO_DUPLICATE = new ErrorCode(1_014_021_001, "变更单号已存在");
    ErrorCode PLAN_CHANGE_STATUS_INVALID = new ErrorCode(1_014_021_002, "计划变更状态流转不合法");
    ErrorCode PLAN_CHANGE_NO_SNAPSHOTS = new ErrorCode(1_014_021_003, "计划变更必须包含至少一条阶段快照");
    ErrorCode PLAN_CHANGE_PROJECT_NOT_EXISTS = new ErrorCode(1_014_021_004, "所属项目不存在");
    ErrorCode PLAN_CHANGE_PHASE_NOT_EXISTS = new ErrorCode(1_014_021_005, "快照关联的阶段不存在");

    // ========== 项目治理动作模块 1-014-022-000 ==========
    ErrorCode GOVERNANCE_ACTION_NOT_EXISTS = new ErrorCode(1_014_022_000, "项目治理动作记录不存在");
    ErrorCode GOVERNANCE_ACTION_NO_DUPLICATE = new ErrorCode(1_014_022_001, "治理动作单号已存在");
    ErrorCode GOVERNANCE_ACTION_STATUS_INVALID = new ErrorCode(1_014_022_002, "项目治理动作状态流转不合法");
    ErrorCode GOVERNANCE_ACTION_PROJECT_NOT_EXISTS = new ErrorCode(1_014_022_003, "所属项目不存在");
    ErrorCode GOVERNANCE_ACTION_TYPE_INVALID = new ErrorCode(1_014_022_004, "项目治理动作类型不合法");

    // ========== 项目模板模块 1-014-023-000 ==========
    ErrorCode PROJECT_TEMPLATE_NOT_EXISTS = new ErrorCode(1_014_023_000, "项目模板不存在");
    ErrorCode PROJECT_TEMPLATE_CODE_DUPLICATE = new ErrorCode(1_014_023_001, "项目模板编码已存在");
    ErrorCode PROJECT_TEMPLATE_IN_USE = new ErrorCode(1_014_023_002, "项目模板已被项目引用，无法删除");
    ErrorCode PROJECT_TEMPLATE_NOT_ENABLED = new ErrorCode(1_014_023_003, "项目模板未启用");
    ErrorCode PROJECT_TEMPLATE_SNAPSHOT_INVALID = new ErrorCode(1_014_023_004, "项目模板快照校验未通过：{}");
    ErrorCode PROJECT_TEMPLATE_CANDIDATE_NOT_FOUND = new ErrorCode(1_014_023_005, "没有适用的已发布项目模板");
    ErrorCode PROJECT_TEMPLATE_CANDIDATE_AMBIGUOUS = new ErrorCode(1_014_023_006, "存在多个同优先级默认项目模板");
    ErrorCode PROJECT_TEMPLATE_CANDIDATE_CHANGED = new ErrorCode(1_014_023_007, "项目模板候选已变化，请重新加载");
    ErrorCode PROJECT_TEMPLATE_CRITERIA_INVALID = new ErrorCode(1_014_023_008, "项目模板匹配条件不完整：{}");

}
