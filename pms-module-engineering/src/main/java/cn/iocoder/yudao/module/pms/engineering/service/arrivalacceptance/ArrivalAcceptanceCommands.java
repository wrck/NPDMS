package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalDifferenceScopeCodec;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Task 5B应用命令；tenant、actor和服务端字段均由可信边界注入。 */
public final class ArrivalAcceptanceCommands {

    private ArrivalAcceptanceCommands() {
    }

    public record FileRevision(Long artifactId, String referenceKey, Integer versionNo,
                               Long scopeVersion, FileFactVersion fileFactVersion, String hash) {
        public FileRevision {
            referenceKey = normalized(referenceKey, 128, "referenceKey");
            hash = normalized(hash, 128, "hash");
            if (artifactId == null || artifactId <= 0 || versionNo == null || versionNo <= 0
                    || scopeVersion == null || scopeVersion < 0 || fileFactVersion == null) {
                throw new IllegalArgumentException("invalid file revision");
            }
        }
    }

    public sealed interface DraftLine permits DeviceDraftLine, QuantityDraftLine {
        Long lineId();
        Integer expectedLineVersion();
    }

    public record DeviceDraftLine(Long lineId, Integer expectedLineVersion,
                                  Long deviceId, boolean received) implements DraftLine {
        public DeviceDraftLine {
            requireLineIdentity(lineId, expectedLineVersion);
            if (deviceId == null || deviceId <= 0) throw new IllegalArgumentException("invalid device line");
        }
    }

    public record QuantityDraftLine(Long lineId, Integer expectedLineVersion, Long orderLineId,
                                    String productCode, String modelCode, BigDecimal acceptedQuantity,
                                    String unitCode) implements DraftLine {
        public QuantityDraftLine {
            requireLineIdentity(lineId, expectedLineVersion);
            productCode = normalizedNullable(productCode, 128);
            modelCode = normalizedNullable(modelCode, 128);
            unitCode = normalized(unitCode, 32, "unitCode");
            if (orderLineId == null || orderLineId <= 0 || productCode == null && modelCode == null
                    || acceptedQuantity == null || acceptedQuantity.signum() < 0) {
                throw new IllegalArgumentException("invalid quantity line");
            }
        }
    }

    public record PatchDraftCommand(Long tenantId, Long arrivalAcceptanceId, Long actorUserId,
                                    Integer expectedVersion, String logisticsNo,
                                    LocalDateTime arrivedAt, String signerName,
                                    List<DraftLine> lines, FileRevision evidenceRevision) {
        public PatchDraftCommand {
            requireIdentity(tenantId, arrivalAcceptanceId, actorUserId, expectedVersion);
            logisticsNo = normalizedNullable(logisticsNo, 128);
            signerName = normalizedNullable(signerName, 128);
            lines = lines == null ? null : List.copyOf(lines);
            if (logisticsNo == null && arrivedAt == null && signerName == null
                    && lines == null && evidenceRevision == null) {
                throw new IllegalArgumentException("empty draft patch");
            }
            if (lines != null && lines.isEmpty()) throw new IllegalArgumentException("empty line patch");
        }
    }

    public record RaiseDifferenceCommand(Long tenantId, Long arrivalAcceptanceId, Long actorUserId,
                                         Integer expectedVersion, Long arrivalLineId,
                                         Integer expectedLineVersion, String differenceTypeCode,
                                         ArrivalDifferenceScopeCodec.Scope scope, String reason,
                                         String riskDescription, FileRevision evidenceRevision,
                                         String idempotencyKey) {
        public RaiseDifferenceCommand {
            requireIdentity(tenantId, arrivalAcceptanceId, actorUserId, expectedVersion);
            differenceTypeCode = normalized(differenceTypeCode, 64, "differenceTypeCode");
            reason = normalized(reason, 1000, "reason");
            riskDescription = normalized(riskDescription, 1000, "riskDescription");
            idempotencyKey = normalized(idempotencyKey, 128, "idempotencyKey");
            if (arrivalLineId == null || arrivalLineId <= 0 || expectedLineVersion == null
                    || expectedLineVersion < 0 || scope == null || evidenceRevision == null) {
                throw new IllegalArgumentException("invalid raise difference command");
            }
        }
    }

    public sealed interface Resolution permits Supplement, KeepRejected, Exempt, Close {
        Long differenceId();
        Integer expectedDifferenceRevision();
        Integer expectedDifferenceVersion();
        String reason();
        FileRevision evidenceRevision();
    }

    public record Supplement(Long differenceId, Integer expectedDifferenceRevision,
                             Integer expectedDifferenceVersion, ArrivalDifferenceScopeCodec.Scope supplementScope,
                             String reason, FileRevision evidenceRevision) implements Resolution {
        public Supplement {
            requireResolution(differenceId, expectedDifferenceRevision, expectedDifferenceVersion,
                    reason, evidenceRevision);
            if (supplementScope == null) throw new IllegalArgumentException("supplement scope required");
            reason = normalized(reason, 1000, "reason");
        }
    }

    public record KeepRejected(Long differenceId, Integer expectedDifferenceRevision,
                               Integer expectedDifferenceVersion, String reason,
                               FileRevision evidenceRevision) implements Resolution {
        public KeepRejected {
            requireResolution(differenceId, expectedDifferenceRevision, expectedDifferenceVersion,
                    reason, evidenceRevision);
            reason = normalized(reason, 1000, "reason");
        }
    }

    public record Exempt(Long differenceId, Integer expectedDifferenceRevision,
                         Integer expectedDifferenceVersion, String reason, String riskDescription,
                         LocalDateTime expiresAt, FileRevision evidenceRevision) implements Resolution {
        public Exempt {
            requireResolution(differenceId, expectedDifferenceRevision, expectedDifferenceVersion,
                    reason, evidenceRevision);
            reason = normalized(reason, 1000, "reason");
            riskDescription = normalized(riskDescription, 1000, "riskDescription");
            if (expiresAt == null) throw new IllegalArgumentException("exemption expiry required");
        }
    }

    public record Close(Long differenceId, Integer expectedDifferenceRevision,
                        Integer expectedDifferenceVersion, String reason,
                        FileRevision evidenceRevision) implements Resolution {
        public Close {
            requireResolution(differenceId, expectedDifferenceRevision, expectedDifferenceVersion,
                    reason, evidenceRevision);
            reason = normalized(reason, 1000, "reason");
        }
    }

    public record ResolveDifferenceCommand(Long tenantId, Long arrivalAcceptanceId, Long actorUserId,
                                           Integer expectedVersion, Resolution resolution,
                                           String idempotencyKey) {
        public ResolveDifferenceCommand {
            requireIdentity(tenantId, arrivalAcceptanceId, actorUserId, expectedVersion);
            idempotencyKey = normalized(idempotencyKey, 128, "idempotencyKey");
            if (resolution == null) throw new IllegalArgumentException("resolution required");
        }
    }

    public record CommandResult(Long arrivalAcceptanceId, Long differenceId, Integer differenceNo,
                                Integer revisionNo, String resolutionStatus, String aggregateStatus,
                                Integer aggregateVersion, Long successorAcceptanceId,
                                ArrivalDifferenceScopeCodec.Scope remainingScope) {
    }

    private static void requireIdentity(Long tenantId, Long id, Long actorId, Integer version) {
        if (tenantId == null || tenantId < 0 || id == null || id <= 0 || actorId == null || actorId <= 0
                || version == null || version < 0) throw new IllegalArgumentException("invalid command identity");
    }

    private static void requireLineIdentity(Long lineId, Integer version) {
        if (lineId == null && version == null) return;
        if (lineId == null || lineId <= 0 || version == null || version < 0) {
            throw new IllegalArgumentException("line identity and version must be paired");
        }
    }

    private static void requireResolution(Long id, Integer revision, Integer version,
                                          String reason, FileRevision evidence) {
        if (id == null || id <= 0 || revision == null || revision <= 0 || version == null || version < 0
                || reason == null || evidence == null) throw new IllegalArgumentException("invalid resolution");
    }

    private static String normalized(String value, int max, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException("invalid " + field);
        }
        return value;
    }

    private static String normalizedNullable(String value, int max) {
        if (value == null) return null;
        return normalized(value, max, "optional text");
    }
}
