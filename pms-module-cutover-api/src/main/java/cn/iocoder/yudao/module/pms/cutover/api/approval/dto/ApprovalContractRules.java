package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactException;

final class ApprovalContractRules {

    private ApprovalContractRules() {
    }

    static long positive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field + " must be positive");
        }
        return value;
    }

    static int positive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field + " must be positive");
        }
        return value;
    }

    static int nonNegative(Integer value, String field) {
        if (value == null || value < 0) {
            throw invalid(field + " must be non-negative");
        }
        return value;
    }

    static Long nullablePositive(Long value, String field) {
        if (value != null) {
            positive(value, field);
        }
        return value;
    }

    static String normalized(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maxLength) {
            throw invalid(field + " is invalid");
        }
        return value;
    }

    static String nonBlank(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalid(field + " is invalid");
        }
        return value;
    }

    static CutoverApprovalFactException invalid(String message) {
        return new CutoverApprovalFactException(CutoverApprovalFactException.Code.INVALID_REQUEST, message);
    }
}
