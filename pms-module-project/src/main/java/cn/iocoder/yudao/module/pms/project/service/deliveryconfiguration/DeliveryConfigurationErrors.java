package cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
/** PM-03 / F-PROJ-009: local stable errors. */
public interface DeliveryConfigurationErrors {
    ErrorCode INVALID = new ErrorCode(1_014_040_000, "交付配置无效：{}");
    ErrorCode NOT_FOUND = new ErrorCode(1_014_040_001, "交付定义不存在");
    ErrorCode VERSION_CONFLICT = new ErrorCode(1_014_040_002, "交付配置版本冲突，请刷新后重试");
    ErrorCode STATE_INVALID = new ErrorCode(1_014_040_003, "交付配置状态不允许该操作");
    ErrorCode REFERENCE_INVALID = new ErrorCode(1_014_040_004, "交付定义引用无效：{}");
    ErrorCode DUPLICATE = new ErrorCode(1_014_040_005, "交付定义身份或草稿已存在");
    ErrorCode IDEMPOTENCY_CONFLICT = new ErrorCode(1_014_040_006, "幂等键已用于不同交付配置命令");
    ErrorCode IN_PROGRESS = new ErrorCode(1_014_040_007, "交付配置命令正在处理");
}
