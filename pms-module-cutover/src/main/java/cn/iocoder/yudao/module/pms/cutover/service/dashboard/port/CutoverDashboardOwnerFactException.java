package cn.iocoder.yudao.module.pms.cutover.service.dashboard.port;

import java.util.Set;

/** Stable error boundary for physical Owner facts consumed by CUT dashboard. */
public final class CutoverDashboardOwnerFactException extends RuntimeException {
    private static final Set<String> CATEGORIES = Set.of(
            "OWNER_PROVIDER_UNAVAILABLE", "OWNER_DATA_CORRUPTED");
    private static final Set<String> OWNERS = Set.of(
            "CUT", "PROJ", "AST", "CUS", "IMP", "PLT", "SYSTEM", "INT12");

    private final String category;
    private final String reasonCode;
    private final String ownerContext;

    public CutoverDashboardOwnerFactException(String category, String reasonCode,
                                              String ownerContext, Throwable cause) {
        super(reasonCode, cause);
        if (!CATEGORIES.contains(category) || reasonCode == null || reasonCode.isBlank()
                || reasonCode.length() > 64 || !OWNERS.contains(ownerContext)) {
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
