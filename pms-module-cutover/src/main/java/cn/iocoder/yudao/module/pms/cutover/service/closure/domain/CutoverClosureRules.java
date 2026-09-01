package cn.iocoder.yudao.module.pms.cutover.service.closure.domain;

import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureOwnerFactException;

import java.time.LocalDateTime;

/** F-CUT-006 闭环字段与预留端口的最小机器规则。 */
public final class CutoverClosureRules {

    private CutoverClosureRules() {
    }

    public enum AttachmentPurpose {
        POST_COLLECTION_CHECKLIST,
        IMPLEMENTATION_COMMITMENT,
        OTHER_EVIDENCE,
        MANUAL_COLLECTION_RESULT
    }

    public enum CollectionStage {
        PRE_CHECK,
        EXECUTION,
        TEST,
        ROLLBACK,
        POST_COLLECTION
    }

    public enum AuthenticationMode {
        SAVED_CREDENTIAL,
        TRANSIENT_CREDENTIAL
    }

    public static Long positive(Long value, String field) {
        require(value != null && value > 0, field);
        return value;
    }

    public static long nonNegative(Long value, String field) {
        require(value != null && value >= 0, field);
        return value;
    }

    public static int positive(Integer value, String field) {
        require(value != null && value > 0, field);
        return value;
    }

    public static String normalizedText(String value, int maxLength, String field) {
        require(value != null && !value.isBlank() && value.equals(value.trim())
                && value.length() <= maxLength, field);
        return value;
    }

    public static String transientSecret(String value) {
        require(value != null && !value.isBlank(), "transientSecret");
        return value;
    }

    public static String sha256(String value) {
        require(value != null && value.matches("[0-9a-f]{64}"), "sha256");
        return value;
    }

    public static LocalDateTime occurredAt(LocalDateTime value) {
        require(value != null, "occurredAt");
        return value;
    }

    public static <T> T requireValue(T value, String field) {
        require(value != null, field);
        return value;
    }

    public static void require(boolean condition, String field) {
        if (!condition) {
            throw new CutoverClosureOwnerFactException(
                    CutoverClosureOwnerFactException.Code.INVALID_REQUEST,
                    "invalid cutover closure contract field: " + field);
        }
    }
}
