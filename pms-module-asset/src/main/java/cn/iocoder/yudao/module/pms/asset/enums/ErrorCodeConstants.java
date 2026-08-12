package cn.iocoder.yudao.module.pms.asset.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * PMS 资产域错误码常量
 *
 * 错误码段分配：
 * - equipment:           1_015_001_000
 * - equipment-version:   1_015_002_000
 * - equipment-config-log:1_015_003_000
 */
public interface ErrorCodeConstants {

    // ========== 设备档案 1_015_001_000 ==========
    ErrorCode AST_EQUIPMENT_NOT_EXISTS = new ErrorCode(1_015_001_000, "设备档案不存在");
    ErrorCode AST_EQUIPMENT_SERIAL_NUMBER_DUPLICATE = new ErrorCode(1_015_001_001, "设备序列号已存在");
    ErrorCode AST_EQUIPMENT_STATUS_INVALID = new ErrorCode(1_015_001_002, "设备状态不允许当前操作");
    ErrorCode AST_EQUIPMENT_SCRAPPED = new ErrorCode(1_015_001_003, "设备已报废，不允许修改");

    // ========== 设备版本历史 1_015_002_000 ==========
    ErrorCode AST_EQUIPMENT_VERSION_NOT_EXISTS = new ErrorCode(1_015_002_000, "设备版本记录不存在");

    // ========== 设备配置日志 1_015_003_000 ==========
    ErrorCode AST_EQUIPMENT_CONFIG_LOG_NOT_EXISTS = new ErrorCode(1_015_003_000, "设备配置日志不存在");

}
