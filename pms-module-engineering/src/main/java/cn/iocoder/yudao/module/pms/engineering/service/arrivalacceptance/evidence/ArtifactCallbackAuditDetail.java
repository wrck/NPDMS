package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import java.time.LocalDateTime;

public record ArtifactCallbackAuditDetail(
        String callbackType,
        String eventId,
        Long tenantId,
        Long evidenceId,
        Integer evidenceRevision,
        Long artifactId,
        Integer fileVersion,
        String recordId,
        LocalDateTime occurredAt,
        String correlationId,
        ArtifactCallbackResult.Outcome outcome,
        Reason reason,
        Integer currentRevision,
        Long currentArtifactId,
        Integer currentFileVersion,
        String currentAcceptedRecordId,
        String currentStatus) {

    public enum Reason {
        APPLIED,
        DUPLICATE_RECORD,
        EVIDENCE_NOT_FOUND,
        ROOT_IDENTITY_MISMATCH,
        CURRENT_REVISION_MISMATCH,
        CURRENT_REVISION_NOT_FOUND,
        CURRENT_REVISION_IDENTITY_MISMATCH,
        ARTIFACT_MISMATCH,
        FILE_VERSION_MISMATCH,
        RECORD_ID_MISMATCH,
        ACCEPTED_RECORD_MISSING,
        OUT_OF_ORDER_STATE,
        CONCURRENT_STATE_CHANGED
    }
}
