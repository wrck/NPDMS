package cn.iocoder.yudao.module.pms.cutover.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 割接域错误码常量
 *
 * 错误码段：
 * - cut-task: 1-012-001-000
 * - cut-risk: 1-012-002-000
 * - cut-plan: 1-012-003-000
 * - cut-execution: 1-012-004-000
 * - cut-observation: 1-012-005-000
 */
public interface ErrorCodeConstants {

    // ========== 割接任务 cut-task 1-012-001-000 ==========
    ErrorCode CUT_TASK_NOT_FOUND = new ErrorCode(1_012_001_000, "割接任务不存在");
    ErrorCode CUT_TASK_CODE_DUPLICATE = new ErrorCode(1_012_001_001, "割接任务编码已存在");
    ErrorCode CUT_TASK_STATUS_INVALID = new ErrorCode(1_012_001_002, "割接任务当前状态不允许该操作");
    ErrorCode CUT_TASK_VERSION_NOT_MATCH = new ErrorCode(1_012_001_003, "割接任务版本号已变更，请刷新后重试");
    ErrorCode CUT_TASK_GATE_NOT_READY = new ErrorCode(1_012_001_004, "割接前置门禁未满足，无法发起割接流程");
    ErrorCode CUT_TASK_NOT_APPROVED = new ErrorCode(1_012_001_005, "割接任务尚未评审通过，无法继续执行");

    // ========== 割接风险 cut-risk 1-012-002-000 ==========
    ErrorCode CUT_RISK_NOT_FOUND = new ErrorCode(1_012_002_000, "割接风险/调研项不存在");
    ErrorCode CUT_RISK_CODE_DUPLICATE = new ErrorCode(1_012_002_001, "风险/调研项编码已存在");
    ErrorCode CUT_RISK_STATUS_INVALID = new ErrorCode(1_012_002_002, "风险/调研项当前状态不允许该操作");
    ErrorCode CUT_RISK_NOT_CLOSED = new ErrorCode(1_012_002_003, "割接任务存在未闭环的风险/调研项，无法提交评审");

    // ========== 割接方案 cut-plan 1-012-003-000 ==========
    ErrorCode CUT_PLAN_NOT_FOUND = new ErrorCode(1_012_003_000, "割接方案不存在");
    ErrorCode CUT_PLAN_CODE_DUPLICATE = new ErrorCode(1_012_003_001, "割接方案编码已存在");
    ErrorCode CUT_PLAN_STATUS_INVALID = new ErrorCode(1_012_003_002, "割接方案当前状态不允许该操作");
    ErrorCode CUT_PLAN_VERSION_NOT_MATCH = new ErrorCode(1_012_003_003, "割接方案版本号已变更，请刷新后重试");
    ErrorCode CUT_PLAN_BASELINE_LOCKED = new ErrorCode(1_012_003_004, "割接方案已基线锁定，关键字段不可变更，请重新提交评审");

    // ========== 割接执行记录 cut-execution 1-012-004-000 ==========
    ErrorCode CUT_EXECUTION_NOT_FOUND = new ErrorCode(1_012_004_000, "割接执行记录不存在");
    ErrorCode CUT_EXECUTION_CODE_DUPLICATE = new ErrorCode(1_012_004_001, "执行记录编码已存在");
    ErrorCode CUT_EXECUTION_STATUS_INVALID = new ErrorCode(1_012_004_002, "执行记录当前状态不允许该操作");

    // ========== 稳定观察 cut-observation 1-012-005-000 ==========
    ErrorCode CUT_OBSERVATION_NOT_FOUND = new ErrorCode(1_012_005_000, "稳定观察记录不存在");
    ErrorCode CUT_OBSERVATION_CODE_DUPLICATE = new ErrorCode(1_012_005_001, "观察记录编码已存在");
    ErrorCode CUT_OBSERVATION_STATUS_INVALID = new ErrorCode(1_012_005_002, "观察记录当前状态不允许该操作");
    ErrorCode CUT_OBSERVATION_LEFTOVER_NOT_CLOSED = new ErrorCode(1_012_005_003, "稳定观察存在未闭环遗留项，无法归档");

}
