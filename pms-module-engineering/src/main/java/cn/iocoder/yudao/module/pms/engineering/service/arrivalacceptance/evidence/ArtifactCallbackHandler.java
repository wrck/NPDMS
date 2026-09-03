package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactAcceptedMessage;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactArchivedMessage;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactCallbackConflictException;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactCallbackInProgressException;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceAcceptedUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceArchivedUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceIdentityQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRevisionQuery;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
public class ArtifactCallbackHandler {

    static final String ACCEPTED = "ArtifactAccepted";
    static final String ARCHIVED = "ArtifactArchived";
    private static final String SCOPE_PREFIX = "IMP:ARRIVAL_EVIDENCE_CALLBACK:";
    private static final String STATUS_PUBLISHED = "PUBLISHED_PENDING_ACC";
    private static final String STATUS_PUBLISH_RETRY = "ARCHIVE_PENDING_RETRY";
    private static final String STATUS_ACCEPTED = "ACCEPTED_PENDING_ARCHIVE";
    private static final String STATUS_ARCHIVE_RETRY = "ARCHIVE_ACK_PENDING_RETRY";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final DeliveryEvidenceMapper evidenceMapper;
    private final DeliveryEvidenceRevisionMapper revisionMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final Clock clock;

    @Autowired
    public ArtifactCallbackHandler(DeliveryEvidenceMapper evidenceMapper,
                                   DeliveryEvidenceRevisionMapper revisionMapper,
                                   PlatformCommandExecutionApi commandExecutionApi) {
        this(evidenceMapper, revisionMapper, commandExecutionApi, Clock.systemDefaultZone());
    }

    ArtifactCallbackHandler(DeliveryEvidenceMapper evidenceMapper,
                            DeliveryEvidenceRevisionMapper revisionMapper,
                            PlatformCommandExecutionApi commandExecutionApi,
                            Clock clock) {
        this.evidenceMapper = evidenceMapper;
        this.revisionMapper = revisionMapper;
        this.commandExecutionApi = commandExecutionApi;
        this.clock = clock;
    }

    public ArtifactCallbackResult handle(ArtifactAcceptedMessage message) {
        validateAccepted(message);
        requireRuntimeTenant(message.tenantId());
        CallbackEnvelope envelope = new CallbackEnvelope(ACCEPTED, message.eventId(),
                message.tenantId(), message.evidenceId(), message.evidenceRevision(),
                message.artifactId(), message.fileVersion(), message.reviewRecordId(),
                message.occurredAt(), message.correlationId());
        return execute(envelope, () -> accept(envelope));
    }

    public ArtifactCallbackResult handle(ArtifactArchivedMessage message) {
        validateArchived(message);
        requireRuntimeTenant(message.tenantId());
        CallbackEnvelope envelope = new CallbackEnvelope(ARCHIVED, message.eventId(),
                message.tenantId(), message.evidenceId(), message.evidenceRevision(),
                message.artifactId(), message.fileVersion(), message.archiveRecordId(),
                message.occurredAt(), message.correlationId());
        return execute(envelope, () -> archive(envelope));
    }

    private ArtifactCallbackResult execute(CallbackEnvelope envelope,
                                           Supplier<ArtifactCallbackProcessing> operation) {
        PlatformCommandExecutionApi.ExecutionResult<ArtifactCallbackProcessing> execution =
                commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                                envelope.tenantId(), SCOPE_PREFIX + envelope.eventType(),
                                0L, envelope.eventId()),
                        digest(envelope), ArtifactCallbackProcessing.class, operation,
                        processing -> successFacts(envelope, processing));
        return switch (execution.decision()) {
            case NEW, REPLAY_COMPLETED -> Objects.requireNonNull(
                    execution.response(), "artifact callback processing is missing").result();
            case CONFLICT -> throw new ArtifactCallbackConflictException();
            case IN_PROGRESS -> throw new ArtifactCallbackInProgressException();
        };
    }

    private ArtifactCallbackProcessing accept(CallbackEnvelope message) {
        CurrentSnapshot current = lockCurrentSnapshot(message);
        ArtifactCallbackAuditDetail.Reason mismatch = identityMismatch(message, current);
        if (mismatch != null) {
            return processing(message, current, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH,
                    mismatch);
        }
        DeliveryEvidenceDO root = current.root();
        if (STATUS_ACCEPTED.equals(root.getAccSyncStatus())
                || STATUS_ARCHIVE_RETRY.equals(root.getAccSyncStatus())) {
            boolean duplicate = Objects.equals(root.getAccAcceptedRecordId(), message.recordId());
            return processing(message, current, duplicate
                            ? ArtifactCallbackResult.Outcome.DUPLICATE
                            : ArtifactCallbackResult.Outcome.IGNORED_MISMATCH,
                    duplicate ? ArtifactCallbackAuditDetail.Reason.DUPLICATE_RECORD
                            : ArtifactCallbackAuditDetail.Reason.RECORD_ID_MISMATCH);
        }
        if (!STATUS_PUBLISHED.equals(root.getAccSyncStatus())
                && !STATUS_PUBLISH_RETRY.equals(root.getAccSyncStatus())) {
            return processing(message, current, ArtifactCallbackResult.Outcome.IGNORED_OUT_OF_ORDER,
                    ArtifactCallbackAuditDetail.Reason.OUT_OF_ORDER_STATE);
        }
        int updated = evidenceMapper.markAcceptedPendingArchiveIfMatch(
                new DeliveryEvidenceAcceptedUpdate(message.tenantId(), message.evidenceId(),
                        message.evidenceRevision(), root.getVersion(), message.recordId(),
                        message.eventId(), 0, LocalDateTime.now(clock).plusMinutes(1)));
        if (updated == 1) {
            return processing(message, current, ArtifactCallbackResult.Outcome.APPLIED,
                    ArtifactCallbackAuditDetail.Reason.APPLIED, STATUS_ACCEPTED);
        }
        return classifyAcceptedAfterCasMiss(message);
    }

    private ArtifactCallbackProcessing archive(CallbackEnvelope message) {
        CurrentSnapshot current = lockCurrentSnapshot(message);
        ArtifactCallbackAuditDetail.Reason mismatch = identityMismatch(message, current);
        if (mismatch != null) {
            return processing(message, current, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH,
                    mismatch);
        }
        DeliveryEvidenceDO root = current.root();
        if (!STATUS_ACCEPTED.equals(root.getAccSyncStatus())
                && !STATUS_ARCHIVE_RETRY.equals(root.getAccSyncStatus())) {
            return processing(message, current, ArtifactCallbackResult.Outcome.IGNORED_OUT_OF_ORDER,
                    ArtifactCallbackAuditDetail.Reason.OUT_OF_ORDER_STATE);
        }
        if (blank(root.getAccAcceptedRecordId())) {
            return processing(message, current, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH,
                    ArtifactCallbackAuditDetail.Reason.ACCEPTED_RECORD_MISSING);
        }
        int updated = evidenceMapper.markArchivedIfMatch(new DeliveryEvidenceArchivedUpdate(
                message.tenantId(), message.evidenceId(), message.evidenceRevision(),
                root.getVersion(), message.recordId(), message.eventId()));
        if (updated == 1) {
            return processing(message, current, ArtifactCallbackResult.Outcome.APPLIED,
                    ArtifactCallbackAuditDetail.Reason.APPLIED, STATUS_ARCHIVED);
        }
        return classifyArchivedAfterCasMiss(message);
    }

    private CurrentSnapshot lockCurrentSnapshot(CallbackEnvelope message) {
        DeliveryEvidenceDO root = evidenceMapper.selectByIdentityForUpdate(
                new DeliveryEvidenceIdentityQuery(message.tenantId(), message.evidenceId()));
        if (root == null || root.getCurrentRevisionNo() == null) {
            return new CurrentSnapshot(root, null);
        }
        DeliveryEvidenceRevisionDO revision = revisionMapper.selectRevision(
                new DeliveryEvidenceRevisionQuery(message.tenantId(), message.evidenceId(),
                        root.getCurrentRevisionNo()));
        return new CurrentSnapshot(root, revision);
    }

    private ArtifactCallbackAuditDetail.Reason identityMismatch(
            CallbackEnvelope message, CurrentSnapshot current) {
        DeliveryEvidenceDO root = current.root();
        if (root == null) {
            return ArtifactCallbackAuditDetail.Reason.EVIDENCE_NOT_FOUND;
        }
        if (!Objects.equals(root.getTenantId(), message.tenantId())
                || !Objects.equals(root.getId(), message.evidenceId())) {
            return ArtifactCallbackAuditDetail.Reason.ROOT_IDENTITY_MISMATCH;
        }
        if (!Objects.equals(root.getCurrentRevisionNo(), message.evidenceRevision())) {
            return ArtifactCallbackAuditDetail.Reason.CURRENT_REVISION_MISMATCH;
        }
        DeliveryEvidenceRevisionDO revision = current.revision();
        if (revision == null) {
            return ArtifactCallbackAuditDetail.Reason.CURRENT_REVISION_NOT_FOUND;
        }
        if (!Objects.equals(revision.getTenantId(), message.tenantId())
                || !Objects.equals(revision.getEvidenceId(), message.evidenceId())
                || !Objects.equals(revision.getRevisionNo(), message.evidenceRevision())) {
            return ArtifactCallbackAuditDetail.Reason.CURRENT_REVISION_IDENTITY_MISMATCH;
        }
        if (!Objects.equals(revision.getFileArtifactId(), message.artifactId())) {
            return ArtifactCallbackAuditDetail.Reason.ARTIFACT_MISMATCH;
        }
        if (!Objects.equals(revision.getFileVersionNo(), message.fileVersion())) {
            return ArtifactCallbackAuditDetail.Reason.FILE_VERSION_MISMATCH;
        }
        return null;
    }

    private ArtifactCallbackProcessing classifyAcceptedAfterCasMiss(CallbackEnvelope message) {
        CurrentSnapshot latest = lockCurrentSnapshot(message);
        ArtifactCallbackAuditDetail.Reason mismatch = identityMismatch(message, latest);
        if (mismatch == null && (STATUS_ACCEPTED.equals(latest.root().getAccSyncStatus())
                || STATUS_ARCHIVE_RETRY.equals(latest.root().getAccSyncStatus()))
                && Objects.equals(latest.root().getAccAcceptedRecordId(), message.recordId())) {
            return processing(message, latest, ArtifactCallbackResult.Outcome.DUPLICATE,
                    ArtifactCallbackAuditDetail.Reason.DUPLICATE_RECORD);
        }
        return processing(message, latest, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH,
                mismatch == null ? ArtifactCallbackAuditDetail.Reason.CONCURRENT_STATE_CHANGED
                        : mismatch);
    }

    private ArtifactCallbackProcessing classifyArchivedAfterCasMiss(CallbackEnvelope message) {
        CurrentSnapshot latest = lockCurrentSnapshot(message);
        ArtifactCallbackAuditDetail.Reason mismatch = identityMismatch(message, latest);
        if (mismatch == null && STATUS_ARCHIVED.equals(latest.root().getAccSyncStatus())
                && Objects.equals(latest.root().getAccArchivedRecordId(), message.recordId())) {
            return processing(message, latest, ArtifactCallbackResult.Outcome.DUPLICATE,
                    ArtifactCallbackAuditDetail.Reason.DUPLICATE_RECORD);
        }
        return processing(message, latest, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH,
                mismatch == null ? ArtifactCallbackAuditDetail.Reason.CONCURRENT_STATE_CHANGED
                        : mismatch);
    }

    private ArtifactCallbackProcessing processing(
            CallbackEnvelope message, CurrentSnapshot current,
            ArtifactCallbackResult.Outcome outcome, ArtifactCallbackAuditDetail.Reason reason) {
        String status = current.root() == null ? null : current.root().getAccSyncStatus();
        return processing(message, current, outcome, reason, status);
    }

    private ArtifactCallbackProcessing processing(
            CallbackEnvelope message, CurrentSnapshot current,
            ArtifactCallbackResult.Outcome outcome, ArtifactCallbackAuditDetail.Reason reason,
            String resultStatus) {
        ArtifactCallbackResult result = new ArtifactCallbackResult(
                outcome, message.evidenceId(), message.evidenceRevision(), resultStatus);
        DeliveryEvidenceDO root = current.root();
        DeliveryEvidenceRevisionDO revision = current.revision();
        ArtifactCallbackAuditDetail audit = new ArtifactCallbackAuditDetail(
                message.eventType(), message.eventId(), message.tenantId(), message.evidenceId(),
                message.evidenceRevision(), message.artifactId(), message.fileVersion(),
                message.recordId(), message.occurredAt(), message.correlationId(), outcome, reason,
                root == null ? null : root.getCurrentRevisionNo(),
                revision == null ? null : revision.getFileArtifactId(),
                revision == null ? null : revision.getFileVersionNo(),
                root == null ? null : root.getAccAcceptedRecordId(),
                root == null ? null : root.getAccSyncStatus());
        return new ArtifactCallbackProcessing(result, audit);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            CallbackEnvelope message, ArtifactCallbackProcessing processing) {
        return new PlatformCommandExecutionApi.SuccessFacts(
                "FIMP002_ARTIFACT_CALLBACK", "DeliveryEvidence",
                String.valueOf(message.evidenceId()), message.correlationId(),
                JsonUtils.toJsonString(processing.audit()), List.of());
    }

    private static String digest(CallbackEnvelope envelope) {
        try {
            byte[] canonical = JsonUtils.toJsonString(envelope).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static void requireRuntimeTenant(Long messageTenantId) {
        Long runtimeTenantId = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(runtimeTenantId, messageTenantId)) {
            throw new IllegalArgumentException("artifact callback tenant does not match runtime tenant");
        }
    }

    private static void validateAccepted(ArtifactAcceptedMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("artifact accepted callback is required");
        }
        validate(message.eventId(), message.tenantId(), message.evidenceId(),
                message.evidenceRevision(), message.artifactId(), message.fileVersion(),
                message.reviewRecordId(), message.occurredAt(), message.correlationId());
    }

    private static void validateArchived(ArtifactArchivedMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("artifact archived callback is required");
        }
        validate(message.eventId(), message.tenantId(), message.evidenceId(),
                message.evidenceRevision(), message.artifactId(), message.fileVersion(),
                message.archiveRecordId(), message.occurredAt(), message.correlationId());
    }

    private static void validate(String eventId, Long tenantId, Long evidenceId,
                                 Integer revision, Long artifactId, Integer fileVersion,
                                 String recordId, LocalDateTime occurredAt, String correlationId) {
        if (invalidText(eventId, 128) || invalidId(tenantId) || invalidId(evidenceId)
                || invalidPositive(revision) || invalidId(artifactId)
                || invalidPositive(fileVersion) || invalidText(recordId, 128)
                || occurredAt == null || invalidText(correlationId, 128)) {
            throw new IllegalArgumentException("artifact callback contract fields are invalid");
        }
    }

    private static boolean invalidText(String value, int maxLength) {
        return blank(value) || value.length() > maxLength || !value.equals(value.trim());
    }

    private static boolean invalidId(Long value) {
        return value == null || value <= 0;
    }

    private static boolean invalidPositive(Integer value) {
        return value == null || value <= 0;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record CallbackEnvelope(
            String eventType,
            String eventId,
            Long tenantId,
            Long evidenceId,
            Integer evidenceRevision,
            Long artifactId,
            Integer fileVersion,
            String recordId,
            LocalDateTime occurredAt,
            String correlationId) {
    }

    private record CurrentSnapshot(
            DeliveryEvidenceDO root,
            DeliveryEvidenceRevisionDO revision) {
    }
}
