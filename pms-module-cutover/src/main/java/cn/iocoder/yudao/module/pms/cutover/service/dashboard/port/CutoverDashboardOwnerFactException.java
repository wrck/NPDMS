package cn.iocoder.yudao.module.pms.cutover.service.dashboard.port;

import java.util.Map;
import java.util.Set;

/** Stable error boundary for physical Owner facts consumed by CUT dashboard. */
public final class CutoverDashboardOwnerFactException extends RuntimeException {
    private static final Map<String, Set<String>> REASON_CODES = Map.of(
            "OWNER_PROVIDER_UNAVAILABLE", Set.of(
                    "PROJ_PROVIDER_UNAVAILABLE", "PROJECT_SCOPE_PROVIDER_UNAVAILABLE",
                    "AST_PROVIDER_UNAVAILABLE", "CUS_PROVIDER_UNAVAILABLE", "IMP_PROVIDER_UNAVAILABLE",
                    "SOURCE_PROVIDER_UNAVAILABLE", "PLT_PROVIDER_UNAVAILABLE", "CUT05_PROVIDER_UNAVAILABLE",
                    "PROJ_OR_SYSTEM_PROVIDER_UNAVAILABLE", "INT12_PROVIDER_UNAVAILABLE"),
            "OWNER_DATA_CORRUPTED", Set.of("OWNER_FACT_CORRUPTED"));
    private static final Set<String> OWNERS = Set.of(
            "CUT", "PROJ", "AST", "CUS", "IMP", "PLT", "SYSTEM", "INT12");

    private final String category;
    private final String reasonCode;
    private final String ownerContext;

    public CutoverDashboardOwnerFactException(String category, String reasonCode,
                                              String ownerContext, Throwable cause) {
        super(reasonCode, cause);
        if (!REASON_CODES.getOrDefault(category, Set.of()).contains(reasonCode)
                || !OWNERS.contains(ownerContext)) {
            throw new IllegalArgumentException("dashboard owner error is invalid");
        }
        this.category = category;
        this.reasonCode = reasonCode;
        this.ownerContext = ownerContext;
    }

    public String category() {
        return category;
    }

    public String reasonCode() {
        return reasonCode;
    }

    public String ownerContext() {
        return ownerContext;
    }
}
