package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import java.util.Map;
import java.util.Set;

/** HTTP无关的到货签收机器错误；Controller只负责把结构化事实投影为REST合同。 */
public final class ArrivalAcceptanceContractException extends RuntimeException {

    private static final Map<String, Set<String>> REASONS = Map.of(
            "DATA_SCOPE_FORBIDDEN", Set.of("PROJECT_DATA_SCOPE_DENIED"),
            "IDEMPOTENCY_CONFLICT", Set.of("IDEMPOTENCY_PAYLOAD_CONFLICT"),
            "IDEMPOTENCY_IN_PROGRESS", Set.of("IDEMPOTENCY_COMMAND_IN_PROGRESS"),
            "SCOPE_STALE", Set.of("PROJECT_FACT_STALE", "DELIVERY_SCOPE_STALE",
                    "DEVICE_ASSIGNMENT_STALE", "FILE_SCOPE_STALE"),
            "EVIDENCE_INVALID", Set.of("EVIDENCE_MISSING", "EVIDENCE_REVISION_STALE",
                    "EVIDENCE_UNAVAILABLE", "EVIDENCE_SCOPE_INVALID"),
            "BUSINESS_GATE_INVALID", Set.of("PROJECT_NOT_ACTIVE", "PROJECT_STAGE_NOT_S4",
                    "PROJECT_SUBJECT_NOT_ELIGIBLE", "ASSIGNED_SCOPE_EMPTY", "DIFFERENCE_REMAINS_OPEN"),
            "OWNER_PROVIDER_UNAVAILABLE", Set.of("PROJ_PROVIDER_UNAVAILABLE", "COM_PROVIDER_UNAVAILABLE",
                    "AST_PROVIDER_UNAVAILABLE", "PLT_PROVIDER_UNAVAILABLE"));
    private static final Set<String> OWNERS = Set.of("PROJ", "COM", "AST", "PLT");

    private final String category;
    private final String reasonCode;
    private final String ownerContext;
    private final Integer currentAggregateVersion;
    private final Integer currentLineVersion;
    private final Integer currentDifferenceRevision;
    private final Integer currentDifferenceVersion;

    public ArrivalAcceptanceContractException(String category, String reasonCode, String message,
                                              String ownerContext, Integer currentAggregateVersion,
                                              Integer currentLineVersion, Integer currentDifferenceRevision,
                                              Integer currentDifferenceVersion) {
        super(message);
        this.category = required(category, "category");
        this.reasonCode = required(reasonCode, "reasonCode");
        if (!REASONS.getOrDefault(this.category, Set.of()).contains(this.reasonCode)) {
            throw new IllegalArgumentException("arrival contract reason does not belong to category");
        }
        if (ownerContext != null && !OWNERS.contains(ownerContext)) {
            throw new IllegalArgumentException("invalid arrival contract ownerContext");
        }
        this.ownerContext = ownerContext;
        this.currentAggregateVersion = currentAggregateVersion;
        this.currentLineVersion = currentLineVersion;
        this.currentDifferenceRevision = currentDifferenceRevision;
        this.currentDifferenceVersion = currentDifferenceVersion;
    }

    public static ArrivalAcceptanceContractException simple(String category, String reasonCode, String message) {
        return new ArrivalAcceptanceContractException(category, reasonCode, message,
                null, null, null, null, null);
    }

    public static ArrivalAcceptanceContractException owner(String category, String reasonCode,
                                                           String ownerContext, String message) {
        return new ArrivalAcceptanceContractException(category, reasonCode, message,
                required(ownerContext, "ownerContext"), null, null, null, null);
    }

    public static ArrivalAcceptanceContractException aggregateVersion(Integer currentVersion, String message) {
        return new ArrivalAcceptanceContractException("AGGREGATE_OR_LINE_VERSION_CONFLICT",
                "AGGREGATE_VERSION_STALE", message, null, currentVersion, null, null, null);
    }

    public String category() { return category; }
    public String reasonCode() { return reasonCode; }
    public String ownerContext() { return ownerContext; }
    public Integer currentAggregateVersion() { return currentAggregateVersion; }
    public Integer currentLineVersion() { return currentLineVersion; }
    public Integer currentDifferenceRevision() { return currentDifferenceRevision; }
    public Integer currentDifferenceVersion() { return currentDifferenceVersion; }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("invalid arrival contract " + field);
        }
        return value;
    }
}
