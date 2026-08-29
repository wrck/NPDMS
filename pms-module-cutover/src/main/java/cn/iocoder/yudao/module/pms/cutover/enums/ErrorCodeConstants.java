package cn.iocoder.yudao.module.pms.cutover.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 割接域错误码常量
 *
 * 错误码段：
 * - cut-task: 1-012-001-000
 * - cut-risk: 1-012-002-000
 * - cut-plan: 1-012-003-000
 * - cutover-config: 1-012-004-000
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

    // ========== CUT-07割接配置 cutover-config 1-012-004-000 ==========
    ErrorCode CUTOVER_CONFIG_NOT_FOUND = new ErrorCode(1_012_004_000, "割接配置修订不存在");
    ErrorCode CUTOVER_CONFIG_CODE_CHANGED = new ErrorCode(1_012_004_001, "配置稳定编码创建后不可修改");
    ErrorCode CUTOVER_CONFIG_NOT_EDITABLE = new ErrorCode(1_012_004_002, "只有草稿配置可以编辑");
    ErrorCode CUTOVER_CONFIG_VERSION_CONFLICT = new ErrorCode(1_012_004_003, "配置版本已变化，请刷新后重试");
    ErrorCode CUTOVER_CONFIG_VALIDATION_FAILED = new ErrorCode(1_012_004_004, "配置发布校验失败：{}处存在问题");
    ErrorCode CUTOVER_CONFIG_STATUS_INVALID = new ErrorCode(1_012_004_005, "配置当前状态不允许该操作");

}
