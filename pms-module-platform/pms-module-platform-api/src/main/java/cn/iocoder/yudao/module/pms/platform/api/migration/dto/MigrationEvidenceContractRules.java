package cn.iocoder.yudao.module.pms.platform.api.migration.dto;

import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.time.LocalDateTime;
import java.util.List;

final class MigrationEvidenceContractRules {

    private MigrationEvidenceContractRules() {
    }

    static String text(String value, int maxLength, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(field + " must not be blank");
        }
        String normalized = value.trim();
        if (!normalized.equals(value) || normalized.length() > maxLength) {
            throw invalid(field + " must be normalized and at most " + maxLength + " characters");
        }
        return normalized;
    }

    static String optionalText(String value, int maxLength, String field) {
        return value == null ? null : text(value, maxLength, field);
    }

    static String json(String value, String field) {
        if (value == null || value.trim().isEmpty() || !JsonUtils.isJsonObject(value)) {
            throw invalid(field + " must be a JSON object");
        }
        return value;
    }

    static String optionalJson(String value, String field) {
        return value == null ? null : json(value, field);
    }

    static String sha256(String value, String field) {
        String normalized = text(value, 64, field);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw invalid(field + " must be a 64-character lowercase SHA-256");
        }
        return normalized;
    }

    static Long positive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field + " must be positive");
        }
        return value;
    }

    static long nonNegative(long value, String field) {
        if (value < 0) {
            throw invalid(field + " must be non-negative");
        }
        return value;
    }

    static int nonNegative(int value, String field) {
        if (value < 0) {
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

    static <T> List<T> completeList(List<T> values, String field) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw invalid(field + " must be a complete list");
        }
        return List.copyOf(values);
    }

    static PlatformMigrationEvidenceException invalid(String message) {
        return new PlatformMigrationEvidenceException(
                PlatformMigrationEvidenceException.Code.INVALID_REQUEST, message);
    }

    static PlatformMigrationEvidenceException corrupted(String message) {
        return new PlatformMigrationEvidenceException(
                PlatformMigrationEvidenceException.Code.OWNER_DATA_CORRUPTED, message);
    }
}
