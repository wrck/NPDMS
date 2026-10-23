package cn.iocoder.yudao.module.pms.service.service.inspection;

import lombok.Getter;

/** Stable business failures; messages must not contain credentials or raw device output. */
@Getter
public class InspectionException extends RuntimeException {
    public enum Reason {
        INVALID_REQUEST, NOT_FOUND, FORBIDDEN, STATE_CONFLICT, VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT, IN_PROGRESS, FILE_INVALID, PARSE_FAILED, PRECHECK_REQUIRED,
        DEPENDENCY_UNAVAILABLE
    }

    private final Reason reason;
    private final Long currentVersion;

    public InspectionException(Reason reason, String message) {
        this(reason, message, null);
    }

    public InspectionException(Reason reason, String message, Long currentVersion) {
        super(message);
        this.reason = reason;
        this.currentVersion = currentVersion;
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new InspectionException(Reason.INVALID_REQUEST, message);
    }
}
