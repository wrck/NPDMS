package cn.iocoder.yudao.module.pms.acceptance.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 验收模块错误码枚举类
 *
 * acceptance 模块，沿用原 1-014-011-000 起的验收域错误码段，数值保持不变
 */
public interface ErrorCodeConstants {

    // ========== 电子完工证明 1-014-011-000 ==========
    ErrorCode ACC_COMPLETION_CERTIFICATE_NOT_EXISTS = new ErrorCode(1_014_011_000, "电子完工证明不存在");
    ErrorCode ACC_COMPLETION_CERTIFICATE_CODE_DUPLICATE = new ErrorCode(1_014_011_001, "项目内完工证明编码已存在");
    ErrorCode ACC_COMPLETION_CERTIFICATE_STATUS_INVALID = new ErrorCode(1_014_011_002, "完工证明状态流转不合法");

    // ========== 初验/终验 1-014-012-000 ==========
    ErrorCode ACC_ACCEPTANCE_NOT_EXISTS = new ErrorCode(1_014_012_000, "验收记录不存在");
    ErrorCode ACC_ACCEPTANCE_CODE_DUPLICATE = new ErrorCode(1_014_012_001, "项目内验收编码已存在");
    ErrorCode ACC_ACCEPTANCE_STATUS_INVALID = new ErrorCode(1_014_012_002, "验收状态流转不合法");
    ErrorCode ACC_ACCEPTANCE_DELIVERABLE_INCOMPLETE = new ErrorCode(1_014_012_003, "交付件完整性校验未通过，存在未通过的必交交付件");
    ErrorCode ACC_ACCEPTANCE_SCOPE_REQUEST_INVALID = new ErrorCode(1_014_012_004, "验收范围绑定请求不合法");
    ErrorCode ACC_ACCEPTANCE_SCOPE_BINDING_CONFLICT = new ErrorCode(1_014_012_005, "验收范围绑定身份或状态冲突");
    ErrorCode ACC_REPORT_NOT_EXISTS = new ErrorCode(1_014_012_006, "验收报告版本不存在");
    ErrorCode ACC_REPORT_STATE_INVALID = new ErrorCode(1_014_012_007, "验收报告当前状态不允许该操作");
    ErrorCode ACC_REPORT_VERSION_CONFLICT = new ErrorCode(1_014_012_008, "验收报告或活动版本冲突");
    ErrorCode ACC_REPORT_INCOMPLETE = new ErrorCode(1_014_012_009, "验收报告时间、结论、验收人或附件不完整");
    ErrorCode ACC_REPORT_DEPENDENCY_UNAVAILABLE = new ErrorCode(1_014_012_010, "验收报告Owner事实暂不可用");
    ErrorCode ACC_REPORT_SCOPE_FORBIDDEN = new ErrorCode(1_014_012_011, "无权访问该项目验收报告");

    // ========== 交付件检查 1-014-013-000 ==========
    ErrorCode ACC_DELIVERABLE_CHECKLIST_NOT_EXISTS = new ErrorCode(1_014_013_000, "交付件检查记录不存在");
    ErrorCode ACC_DELIVERABLE_CHECKLIST_CODE_DUPLICATE = new ErrorCode(1_014_013_001, "项目内交付件编码已存在");
    ErrorCode ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID = new ErrorCode(1_014_013_002, "交付件状态流转不合法");

    // ========== 归档文档 1-014-015-000 ==========
    ErrorCode ACC_ARCHIVE_DOCUMENT_NOT_EXISTS = new ErrorCode(1_014_015_000, "归档文档不存在");
    ErrorCode ACC_ARCHIVE_DOCUMENT_CODE_DUPLICATE = new ErrorCode(1_014_015_001, "项目内归档文档编码已存在");
    ErrorCode ACC_ARCHIVE_DOCUMENT_STATUS_INVALID = new ErrorCode(1_014_015_002, "归档文档状态流转不合法");

    // ========== 迁移期沿用的 PROJ 段通用错误码（同号同文案，行为保持；ACC 后续独立分配时段时再收敛）==========
    ErrorCode PROJECT_TASK_QUERY_INVALID = new ErrorCode(1_014_024_044, "项目任务查询参数无效");
    ErrorCode PMS_IDEMPOTENCY_KEY_CONFLICT = new ErrorCode(1_014_024_008, "幂等键冲突：同一 Idempotency-Key 已绑定不同请求体（PMS-COMMON-IDEMPOTENCY-0001）");
    ErrorCode PMS_IDEMPOTENCY_IN_PROGRESS = new ErrorCode(1_014_024_012, "相同幂等请求正在处理中，请稍后重试");

}
