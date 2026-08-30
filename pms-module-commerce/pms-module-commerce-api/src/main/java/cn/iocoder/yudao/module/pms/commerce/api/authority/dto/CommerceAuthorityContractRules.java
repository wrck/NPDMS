package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

final class CommerceAuthorityContractRules {

    private CommerceAuthorityContractRules() {
    }

    static String text(String value, int maxLength, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalid(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    static String optionalText(String value, int maxLength, String field) {
        return value == null ? null : text(value, maxLength, field);
    }

    static String version(String value, String field) {
        return text(value, 64, field);
    }

    static String expectedVersion(String value) {
        return value == null ? null : version(value, "expectedPreviousSourceVersion");
    }

    static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw invalid(field + " must be non-negative");
        }
        return value;
    }

    static LocalDateTime time(LocalDateTime value, String field) {
        if (value == null) {
            throw invalid(field + " must not be null");
        }
        return value;
    }

    static void positive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field + " must be positive");
        }
    }

    static CommerceAuthorityIngestException invalid(String message) {
        return new CommerceAuthorityIngestException(CommerceAuthorityIngestException.Code.INVALID_REQUEST, message);
    }
}
