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
    ErrorCode COMMERCE_SCOPE_LINE_INVALID =
            new ErrorCode(1_016_001_004, "设备清单行无效或不属于当前项目");

    ErrorCode COMMERCE_CONTRACT_ALREADY_BOUND =
            new ErrorCode(1_016_002_000, "该合同已关联其他项目，不能重复创建项目");
    ErrorCode COMMERCE_EXECUTION_ORDER_ALREADY_BOUND =
            new ErrorCode(1_016_002_001, "执行单【{}】已关联其他项目，请转人工核对");
    ErrorCode COMMERCE_CREATION_SOURCE_CONTRACT_NOT_FOUND =
            new ErrorCode(1_016_002_002, "合同主档不存在或不在当前公司范围内");
}
