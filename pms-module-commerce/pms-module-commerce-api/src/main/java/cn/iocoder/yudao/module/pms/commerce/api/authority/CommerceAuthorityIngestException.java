package cn.iocoder.yudao.module.pms.commerce.api.authority;

import java.util.Objects;

/** CommerceAuthorityIngestApi的稳定公共失败。 */
public final class CommerceAuthorityIngestException extends RuntimeException {

    private final Code code;

    public CommerceAuthorityIngestException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public CommerceAuthorityIngestException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code getCode() {
        return code;
    }

    public enum Code {
        INVALID_REQUEST,
        TENANT_CONTEXT_MISMATCH,
        EVENT_PAYLOAD_CONFLICT,
        SOURCE_VERSION_CONFLICT,
        SOURCE_VERSION_PAYLOAD_CONFLICT,
        OWNER_DATA_CORRUPTED,
        PROVIDER_UNAVAILABLE
    }
}
