package cn.iocoder.yudao.module.pms.commerce.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    ErrorCode COMMERCE_SCOPE_BUSINESS_GATE_REJECTED =
            new ErrorCode(1_016_001_000, "BUSINESS_GATE：交付范围业务门禁未通过（{}）");
    ErrorCode COMMERCE_SCOPE_STATE_CONFLICT =
            new ErrorCode(1_016_001_001, "STATE_CONFLICT：交付范围状态已变化（{}）");
    ErrorCode COMMERCE_SCOPE_VERSION_CONFLICT =
            new ErrorCode(1_016_001_002, "VERSION_CONFLICT：交付范围权威版本已变化（{}）");
    ErrorCode COMMERCE_SCOPE_DEPENDENCY_UNAVAILABLE =
            new ErrorCode(1_016_001_003, "DEPENDENCY_UNAVAILABLE：交付范围依赖事实不可用（{}）");
}
