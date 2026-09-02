package cn.iocoder.yudao.module.pms.cutover.service.spare.port;

import java.util.Objects;

/** INT-06备件协同消费端口的稳定失败。 */
public final class SpareApplicationGatewayException extends RuntimeException {

    public enum Code {
        PROVIDER_UNAVAILABLE,
        OWNER_DATA_CORRUPTED,
        REFERENCE_IDENTITY_CONFLICT,
        STATUS_VERSION_CONFLICT
    }

    private final Code code;

    public SpareApplicationGatewayException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public SpareApplicationGatewayException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}
