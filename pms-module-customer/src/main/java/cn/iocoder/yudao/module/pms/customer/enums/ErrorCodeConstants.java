package cn.iocoder.yudao.module.pms.customer.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    ErrorCode CUSTOMER_NOT_EXISTS = new ErrorCode(1_014_001_000, "客户不存在");
    ErrorCode CUSTOMER_CODE_DUPLICATE = new ErrorCode(1_014_001_001, "客户编码已存在");
    ErrorCode CUSTOMER_EXTERNAL_MAPPING_DUPLICATE = new ErrorCode(1_014_001_002, "CRM 客户映射已存在");
    ErrorCode CUSTOMER_VERSION_CONFLICT = new ErrorCode(1_014_001_003, "客户版本冲突");
    ErrorCode CUSTOMER_DELETE_GUARD_BLOCKED = new ErrorCode(1_014_001_004, "客户删除被引用守卫阻止");
    ErrorCode CUSTOMER_CLASSIFICATION_INVALID = new ErrorCode(1_014_001_005, "客户办事处或市场行业组合无效");
    ErrorCode CUSTOMER_SCOPE_DENIED = new ErrorCode(1_014_001_006, "客户办事处或市场行业超出授权范围");
    ErrorCode PMS_IDEMPOTENCY_KEY_CONFLICT = new ErrorCode(1_014_024_008,
            "幂等键冲突：同一 Idempotency-Key 已绑定不同请求体（PMS-COMMON-IDEMPOTENCY-0001）");
    ErrorCode PMS_IDEMPOTENCY_IN_PROGRESS = new ErrorCode(1_014_024_012, "相同幂等请求正在处理中，请稍后重试");
}
