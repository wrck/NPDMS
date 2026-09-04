package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto;

import java.util.HashSet;
import java.util.List;

public record ImplementationReadinessResult(
        Decision decision,
        ImplementationReadinessSnapshotFact snapshot,
        ImplementationReadinessContextFact currentContext,
        List<String> currentUnmetCodes,
        List<String> staleReasonCodes) {

    public ImplementationReadinessResult {
        if (decision == null || snapshot == null || currentUnmetCodes == null || staleReasonCodes == null) {
            throw ImplementationReadinessContextFact.corrupted("invalid readiness result");
        }
        currentUnmetCodes = normalizedCodes(currentUnmetCodes);
        staleReasonCodes = normalizedCodes(staleReasonCodes);
        if (decision == Decision.READY && (snapshot.decision() != ImplementationReadinessSnapshotFact.Decision.READY
                || currentContext == null || !currentContext.equals(snapshot.context())
                || !currentUnmetCodes.isEmpty() || !staleReasonCodes.isEmpty())
                || decision == Decision.NOT_READY
                && (snapshot.decision() != ImplementationReadinessSnapshotFact.Decision.NOT_READY
                || currentContext == null || !currentContext.equals(snapshot.context())
                || !currentUnmetCodes.equals(snapshot.unmetCodes()) || !staleReasonCodes.isEmpty())
                || decision == Decision.STALE && staleReasonCodes.isEmpty()) {
            throw ImplementationReadinessContextFact.corrupted("inconsistent readiness result");
        }
    }

    private static List<String> normalizedCodes(List<String> codes) {
        List<String> normalized = codes.stream().sorted().toList();
        if (new HashSet<>(normalized).size() != normalized.size()
                || normalized.stream().anyMatch(code -> code == null || code.isBlank()
                || !code.equals(code.trim()) || code.length() > 64 || !code.matches("[A-Z][A-Z0-9_]*"))) {
            throw ImplementationReadinessContextFact.corrupted("invalid readiness result code");
        }
        return normalized;
    }

    public enum Decision { READY, NOT_READY, STALE }
}
