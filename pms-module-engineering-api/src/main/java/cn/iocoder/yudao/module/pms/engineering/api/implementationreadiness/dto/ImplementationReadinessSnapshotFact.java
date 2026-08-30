package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

public record ImplementationReadinessSnapshotFact(
        Long tenantId,
        Long projectId,
        Long snapshotId,
        Integer snapshotNo,
        Long snapshotVersion,
        String readinessType,
        Decision decision,
        ImplementationReadinessContextFact context,
        List<String> unmetCodes,
        LocalDateTime evaluatedAt) {

    public ImplementationReadinessSnapshotFact {
        if (tenantId == null || tenantId <= 0 || projectId == null || projectId <= 0
                || snapshotId == null || snapshotId <= 0 || snapshotNo == null || snapshotNo <= 0
                || snapshotVersion == null || snapshotVersion < 0 || !"CUTOVER".equals(readinessType)
                || decision == null || context == null || unmetCodes == null || evaluatedAt == null) {
            throw ImplementationReadinessContextFact.corrupted("invalid readiness snapshot fact");
        }
        unmetCodes = unmetCodes.stream().sorted().toList();
        if (new HashSet<>(unmetCodes).size() != unmetCodes.size()
                || unmetCodes.stream().anyMatch(code -> code == null || code.isBlank()
                || !code.equals(code.trim()) || code.length() > 64 || !code.matches("[A-Z][A-Z0-9_]*"))
                || decision == Decision.READY && (!context.isReady() || !unmetCodes.isEmpty())
                || decision == Decision.NOT_READY && (context.isReady() || unmetCodes.isEmpty())) {
            throw ImplementationReadinessContextFact.corrupted("invalid readiness snapshot unmet codes");
        }
    }

    public enum Decision { READY, NOT_READY }
}
