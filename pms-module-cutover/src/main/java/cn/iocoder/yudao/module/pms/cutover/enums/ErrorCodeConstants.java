package cn.iocoder.yudao.module.pms.cutover.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 割接域错误码常量
 *
 * 错误码段：
 * - cutover-config: 1-012-004-000
 */
public interface ErrorCodeConstants {

    // ========== CUT-07割接配置 cutover-config 1-012-004-000 ==========
    ErrorCode CUTOVER_CONFIG_NOT_FOUND = new ErrorCode(1_012_004_000, "割接配置修订不存在");
    ErrorCode CUTOVER_CONFIG_CODE_CHANGED = new ErrorCode(1_012_004_001, "配置稳定编码创建后不可修改");
    ErrorCode CUTOVER_CONFIG_NOT_EDITABLE = new ErrorCode(1_012_004_002, "只有草稿配置可以编辑");
    ErrorCode CUTOVER_CONFIG_VERSION_CONFLICT = new ErrorCode(1_012_004_003, "配置版本已变化，请刷新后重试");
    ErrorCode CUTOVER_CONFIG_VALIDATION_FAILED = new ErrorCode(1_012_004_004, "配置发布校验失败：{}处存在问题");
    ErrorCode CUTOVER_CONFIG_STATUS_INVALID = new ErrorCode(1_012_004_005, "配置当前状态不允许该操作");
    ErrorCode CUTOVER_NAVIGATION_RULE_INVALID = new ErrorCode(1_012_004_006, "导航规则非法");

}
