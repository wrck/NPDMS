package cn.iocoder.yudao.module.pms.service.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 持续服务域错误码枚举类
 *
 * service 持续服务域，使用 1-013-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 巡检任务 1-013-001-000 ==========
    ErrorCode SRV_TASK_NOT_EXISTS = new ErrorCode(1_013_001_000, "巡检任务不存在");
    ErrorCode SRV_TASK_CODE_DUPLICATE = new ErrorCode(1_013_001_001, "巡检任务编码已存在：{}");
    ErrorCode SRV_TASK_STATUS_INVALID = new ErrorCode(1_013_001_002, "巡检任务当前状态不允许该操作");
    ErrorCode SRV_TASK_EQUIPMENT_ACCOUNT_INVALID = new ErrorCode(1_013_001_003, "设备账号有效性校验失败");

    // ========== 巡检规则 1-013-002-000 ==========
    ErrorCode SRV_RULE_NOT_EXISTS = new ErrorCode(1_013_002_000, "巡检规则不存在");
    ErrorCode SRV_RULE_CODE_DUPLICATE = new ErrorCode(1_013_002_001, "巡检规则编码已存在：{}");
    ErrorCode SRV_RULE_STATUS_INVALID = new ErrorCode(1_013_002_002, "巡检规则当前状态不允许该操作");
    ErrorCode INSPECTION_RULE_DETECTION_ID_DUPLICATE = new ErrorCode(1_013_002_003, "检测ID已存在：{}");
    ErrorCode INSPECTION_RULE_NAME_DUPLICATE = new ErrorCode(1_013_002_004, "巡检规则名称已存在：{}");
    ErrorCode INSPECTION_RULE_REVISION_NOT_EXISTS = new ErrorCode(1_013_002_005, "巡检规则修订不存在");
    ErrorCode INSPECTION_RULE_DRAFT_INVALID = new ErrorCode(1_013_002_006, "巡检规则草稿数据无效");
    ErrorCode INSPECTION_RULE_REVISION_VERSION_CONFLICT = new ErrorCode(1_013_002_007, "巡检规则修订版本冲突");
    ErrorCode INSPECTION_RULE_MANAGE_FORBIDDEN = new ErrorCode(1_013_002_008, "无巡检规则维护权限");
    ErrorCode INSPECTION_RULE_DISABLE_FORBIDDEN = new ErrorCode(1_013_002_009, "无巡检规则停用权限");
    ErrorCode INSPECTION_RULE_PUBLISH_FORBIDDEN = new ErrorCode(1_013_002_010, "无巡检规则发布权限");
    ErrorCode INSPECTION_RULE_IDEMPOTENCY_CONFLICT = new ErrorCode(1_013_002_011, "巡检规则操作幂等键冲突");
    ErrorCode INSPECTION_RULE_IDEMPOTENCY_IN_PROGRESS = new ErrorCode(1_013_002_012, "巡检规则操作正在处理中");

    // ========== 巡检执行记录 1-013-003-000 ==========
    ErrorCode SRV_EXECUTION_NOT_EXISTS = new ErrorCode(1_013_003_000, "巡检执行记录不存在");
    ErrorCode SRV_EXECUTION_CODE_DUPLICATE = new ErrorCode(1_013_003_001, "巡检执行编码已存在：{}");
    ErrorCode SRV_EXECUTION_STATUS_INVALID = new ErrorCode(1_013_003_002, "巡检执行记录当前状态不允许该操作");

    // ========== 离线巡检文件 1-013-004-000 ==========
    ErrorCode SRV_OFFLINE_FILE_NOT_EXISTS = new ErrorCode(1_013_004_000, "离线巡检文件不存在");
    ErrorCode SRV_OFFLINE_FILE_CODE_DUPLICATE = new ErrorCode(1_013_004_001, "离线巡检文件编码已存在：{}");
    ErrorCode SRV_OFFLINE_FILE_STATUS_INVALID = new ErrorCode(1_013_004_002, "离线巡检文件当前状态不允许该操作");

    // ========== 巡检报告 1-013-005-000 ==========
    ErrorCode SRV_REPORT_NOT_EXISTS = new ErrorCode(1_013_005_000, "巡检报告不存在");
    ErrorCode SRV_REPORT_CODE_DUPLICATE = new ErrorCode(1_013_005_001, "巡检报告编码已存在：{}");
    ErrorCode SRV_REPORT_STATUS_INVALID = new ErrorCode(1_013_005_002, "巡检报告当前状态不允许该操作");

    // ========== 巡检问题与整改 1-013-006-000 ==========
    ErrorCode SRV_ISSUE_NOT_EXISTS = new ErrorCode(1_013_006_000, "巡检问题不存在");
    ErrorCode SRV_ISSUE_CODE_DUPLICATE = new ErrorCode(1_013_006_001, "巡检问题编码已存在：{}");
    ErrorCode SRV_ISSUE_STATUS_INVALID = new ErrorCode(1_013_006_002, "巡检问题当前状态不允许该操作");
    ErrorCode SRV_ISSUE_CLOSURE_VALIDATION_FAILED = new ErrorCode(1_013_006_003, "巡检闭环校验失败：存在未关闭的问题");

}
