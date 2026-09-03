package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Task 5B应用读模型；HTTP序列化由Task 8负责。 */
public final class ArrivalAcceptanceViews {

    public static final String PERMISSION_EDIT_OWN_DRAFT = "pms:arrival-acceptance:edit-own-draft";
    public static final String PERMISSION_CONFIRM = "pms:arrival-acceptance:confirm";
    public static final String PERMISSION_RESOLVE_DIFFERENCE = "pms:arrival-acceptance:resolve-difference";

    private ArrivalAcceptanceViews() {
    }

    public record AccessContext(Long actorUserId, Set<String> functionPermissions,
                                Set<Long> visibleProjectIds, Set<Long> editableProjectIds,
                                Set<Long> currentManagerProjectIds, Set<Long> authorizedTeamProjectIds) {
        public AccessContext {
            if (actorUserId == null || actorUserId <= 0 || functionPermissions == null
                    || visibleProjectIds == null || editableProjectIds == null
                    || currentManagerProjectIds == null || authorizedTeamProjectIds == null) {
                throw new IllegalArgumentException("invalid arrival access context");
            }
            functionPermissions = Set.copyOf(functionPermissions);
            visibleProjectIds = Set.copyOf(visibleProjectIds);
            editableProjectIds = Set.copyOf(editableProjectIds);
            currentManagerProjectIds = Set.copyOf(currentManagerProjectIds);
            authorizedTeamProjectIds = Set.copyOf(authorizedTeamProjectIds);
        }
    }

    public record PageRequest(Long tenantId, Long projectId, String batchCode, String status,
                              int pageNo, int pageSize, AccessContext access) {
    }

    public record DetailRequest(Long tenantId, Long arrivalAcceptanceId, AccessContext access) {
    }

    public record ArrivalListItem(Long id, Long projectId, String batchCode, String logisticsNo,
                                  LocalDateTime arrivedAt, String signerName, String status,
                                  String evidenceSyncStatus, Integer version,
                                  List<String> allowedActions, LocalDateTime createTime) {
    }

    public record ArrivalDetail(Long id, Long projectId, String batchCode, String logisticsNo,
                                LocalDateTime arrivedAt, String signerName, String status,
                                Long deliveryScopeVersion, ScopeWatermarkData scopeWatermark,
                                Long evidenceId, Integer evidenceRevision, Long projectFactVersion,
                                Long predecessorAcceptanceId, String successorReason,
                                Long submittedBy, LocalDateTime submittedAt,
                                Long confirmedBy, LocalDateTime confirmedAt, Integer version,
                                List<String> allowedActions, List<ArrivalLineData> currentLines,
                                List<ArrivalDifferenceData> differences, DeliveryEvidenceData evidence) {
    }

    public record ArrivalLineData(Long id, Integer lineNo, Integer lineRevision, String scopeType,
                                  Long deviceId, Long deviceAssignmentVersion, Long orderLineId,
                                  String productCode, String modelCode, BigDecimal expectedQuantity,
                                  BigDecimal acceptedQuantity, String unitCode, String status,
                                  Integer version) {
    }

    public record ArrivalDifferenceData(Long id, Long arrivalLineId, Integer differenceNo,
                                        Integer revisionNo, String differenceType,
                                        String resolutionStatus, String reason, String riskDescription,
                                        String scopeSnapshot, Long approvedBy, LocalDateTime approvedAt,
                                        LocalDateTime exemptionExpiresAt, Long evidenceId,
                                        Integer evidenceRevision, boolean current,
                                        Long projectFactVersion, String factImpactType, Integer version) {
    }

    public record DeliveryEvidenceData(Long evidenceId, Integer currentRevision, Long artifactId,
                                       String referenceKey, Integer fileVersionNo,
                                       FileFactVersion fileFactVersion, Long fileScopeVersion,
                                       String fileHash, String syncStatus, LocalDateTime nextRetryAt,
                                       Integer retryCount) {
    }

    public record ScopeWatermarkData(Long deliveryScopeVersion,
                                     List<DeviceAssignmentVersionData> deviceAssignmentVersions) {
    }

    public record DeviceAssignmentVersionData(Long deviceId, Long projectAssignmentVersion) {
    }

    public record SignerSnapshot(String signerName) {
    }
}
