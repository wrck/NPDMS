package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Task 8 到货签收精确响应模型。 */
public final class ArrivalAcceptanceRespVO {

    private ArrivalAcceptanceRespVO() {
    }

    public record Page(List<ListItem> list, Long total) {
    }

    public record ListItem(Long id, Long projectId, String batchCode, String logisticsNo,
                           LocalDateTime arrivedAt, String signerName, String status,
                           String evidenceSyncStatus, Integer version,
                           List<String> allowedActions, LocalDateTime createTime) {
    }

    public record Command(Long id, Long projectId, String status, Integer version,
                          Long deliveryScopeVersion, List<Long> changedLineIds,
                          Long evidenceId, Integer evidenceRevision, Long projectFactVersion,
                          String evidenceSyncStatus, String eventId, Long successorAcceptanceId,
                          List<String> allowedActions) {
    }

    public record Detail(Long id, Long projectId, String batchCode, String logisticsNo,
                         LocalDateTime arrivedAt, String signerName, String status,
                         Long deliveryScopeVersion, ScopeWatermark scopeWatermark,
                         Long evidenceId, Integer evidenceRevision, Long projectFactVersion,
                         Long predecessorAcceptanceId, String successorReason,
                         Long submittedBy, LocalDateTime submittedAt,
                         Long confirmedBy, LocalDateTime confirmedAt, Integer version,
                         List<String> allowedActions, List<Line> currentLines,
                         List<Difference> differences, Evidence evidence) {
    }

    public record Line(Long id, Integer lineNo, Integer lineRevision, String scopeType,
                       Long deviceId, Long deviceAssignmentVersion, Long orderLineId,
                       String productCode, String modelCode, BigDecimal expectedQuantity,
                       BigDecimal acceptedQuantity, String unitCode, String status, Integer version) {
    }

    public record Difference(Long id, Long arrivalLineId, Integer differenceNo,
                             Integer revisionNo, String differenceType, String resolutionStatus,
                             String reason, String riskDescription, Scope scopeSnapshot,
                             Long approvedBy, LocalDateTime approvedAt, LocalDateTime exemptionExpiresAt,
                             Long evidenceId, Integer evidenceRevision, boolean current,
                             Long projectFactVersion, String factImpactType, Integer version) {
    }

    public sealed interface Scope permits DeviceScope, QuantityScope {
        String scopeType();
    }

    public record DeviceScope(String scopeType, Long deviceId) implements Scope {
    }

    public record QuantityScope(String scopeType, Long orderLineId, String productCode,
                                String modelCode, BigDecimal quantity, String unitCode) implements Scope {
    }

    public record Evidence(Long evidenceId, Integer currentRevision, Long artifactId,
                           String referenceKey, Integer fileVersionNo,
                           FileFactVersion fileFactVersion, Long fileScopeVersion,
                           String fileHash, String syncStatus, LocalDateTime nextRetryAt,
                           Integer retryCount) {
    }

    public record FileFactVersion(Long artifactVersion, Long referenceVersion,
                                  Long availabilityVersion) {
    }

    public record ScopeWatermark(Long deliveryScopeVersion,
                                 List<DeviceAssignmentVersion> deviceAssignmentVersions) {
    }

    public record DeviceAssignmentVersion(Long deviceId, Long projectAssignmentVersion) {
    }

    public record DifferenceCommand(Long arrivalAcceptanceId, Long differenceId,
                                    Integer differenceNo, Integer revisionNo,
                                    String resolutionStatus, String aggregateStatus,
                                    Integer aggregateVersion, Long successorAcceptanceId,
                                    Long projectFactVersion, String factImpactType,
                                    Scope remainingScope, List<String> allowedActions) {
    }

    public record ErrorData(String category, String reasonCode, String recoveryAction,
                            Integer currentAggregateVersion, Integer currentLineVersion,
                            Integer currentDifferenceRevision, Integer currentDifferenceVersion,
                            String ownerContext) {
    }
}
