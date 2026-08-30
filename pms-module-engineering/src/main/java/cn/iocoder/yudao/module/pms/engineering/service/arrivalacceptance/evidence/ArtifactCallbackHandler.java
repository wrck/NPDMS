package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactAcceptedMessage;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ArtifactArchivedMessage;
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
        CallbackEnvelope envelope = new CallbackEnvelope(ACCEPTED, message.eventId(),
                message.tenantId(), message.evidenceId(), message.evidenceRevision(),
                message.artifactId(), message.fileVersion(), message.reviewRecordId(),
                message.occurredAt(), message.correlationId());
        return execute(envelope, () -> accept(envelope));
    }

    public ArtifactCallbackResult handle(ArtifactArchivedMessage message) {
        validateArchived(message);
        CallbackEnvelope envelope = new CallbackEnvelope(ARCHIVED, message.eventId(),
                message.tenantId(), message.evidenceId(), message.evidenceRevision(),
                message.artifactId(), message.fileVersion(), message.archiveRecordId(),
                message.occurredAt(), message.correlationId());
        return execute(envelope, () -> archive(envelope));
    }

    private ArtifactCallbackResult execute(CallbackEnvelope envelope,
                                           java.util.function.Supplier<ArtifactCallbackResult> operation) {
        PlatformCommandExecutionApi.ExecutionResult<ArtifactCallbackResult> execution =
                commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                                envelope.tenantId(), SCOPE_PREFIX + envelope.eventType(),
                                0L, envelope.eventId()),
                        digest(envelope), ArtifactCallbackResult.class, operation,
                        result -> successFacts(envelope, result));
        return switch (execution.decision()) {
            case NEW, REPLAY_COMPLETED -> Objects.requireNonNull(
                    execution.response(), "artifact callback result is missing");
            case CONFLICT -> throw new IllegalStateException(
                    "artifact callback event id was reused with a different payload");
            case IN_PROGRESS -> throw new IllegalStateException(
                    "artifact callback event is already in progress");
        };
    }

    private ArtifactCallbackResult accept(CallbackEnvelope message) {
        DeliveryEvidenceDO root = lockRoot(message);
        if (!matchesCurrentRevision(root, message)) {
            return ignored(message, root, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH);
        }
        if (STATUS_ACCEPTED.equals(root.getAccSyncStatus())
                || STATUS_ARCHIVE_RETRY.equals(root.getAccSyncStatus())) {
            ArtifactCallbackResult.Outcome outcome = Objects.equals(
                    root.getAccAcceptedRecordId(), message.recordId())
                    ? ArtifactCallbackResult.Outcome.DUPLICATE
                    : ArtifactCallbackResult.Outcome.IGNORED_MISMATCH;
            return ignored(message, root, outcome);
        }
        if (!STATUS_PUBLISHED.equals(root.getAccSyncStatus())
                && !STATUS_PUBLISH_RETRY.equals(root.getAccSyncStatus())) {
            return ignored(message, root, ArtifactCallbackResult.Outcome.IGNORED_OUT_OF_ORDER);
        }
        int updated = evidenceMapper.markAcceptedPendingArchiveIfMatch(
                new DeliveryEvidenceAcceptedUpdate(message.tenantId(), message.evidenceId(),
                        message.evidenceRevision(), root.getVersion(), message.recordId(),
                        message.eventId(), 0, LocalDateTime.now(clock).plusMinutes(1)));
        if (updated == 1) {
            return result(message, ArtifactCallbackResult.Outcome.APPLIED, STATUS_ACCEPTED);
        }
        return classifyAcceptedAfterCasMiss(message);
    }

    private ArtifactCallbackResult archive(CallbackEnvelope message) {
        DeliveryEvidenceDO root = lockRoot(message);
        if (!matchesCurrentRevision(root, message)) {
            return ignored(message, root, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH);
        }
        if (!STATUS_ACCEPTED.equals(root.getAccSyncStatus())
                && !STATUS_ARCHIVE_RETRY.equals(root.getAccSyncStatus())) {
            return ignored(message, root, ArtifactCallbackResult.Outcome.IGNORED_OUT_OF_ORDER);
        }
        if (blank(root.getAccAcceptedRecordId())) {
            return ignored(message, root, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH);
        }
        int updated = evidenceMapper.markArchivedIfMatch(new DeliveryEvidenceArchivedUpdate(
                message.tenantId(), message.evidenceId(), message.evidenceRevision(),
                root.getVersion(), message.recordId(), message.eventId()));
        if (updated == 1) {
            return result(message, ArtifactCallbackResult.Outcome.APPLIED, STATUS_ARCHIVED);
        }
        return classifyArchivedAfterCasMiss(message);
    }

    private DeliveryEvidenceDO lockRoot(CallbackEnvelope message) {
        return evidenceMapper.selectByIdentityForUpdate(
                new DeliveryEvidenceIdentityQuery(message.tenantId(), message.evidenceId()));
    }

    private boolean matchesCurrentRevision(DeliveryEvidenceDO root, CallbackEnvelope message) {
        if (root == null || !Objects.equals(root.getTenantId(), message.tenantId())
                || !Objects.equals(root.getId(), message.evidenceId())
                || !Objects.equals(root.getCurrentRevisionNo(), message.evidenceRevision())) {
            return false;
        }
        DeliveryEvidenceRevisionDO revision = revisionMapper.selectRevision(
                new DeliveryEvidenceRevisionQuery(message.tenantId(), message.evidenceId(),
                        message.evidenceRevision()));
        return revision != null
                && Objects.equals(revision.getTenantId(), message.tenantId())
                && Objects.equals(revision.getEvidenceId(), message.evidenceId())
                && Objects.equals(revision.getRevisionNo(), message.evidenceRevision())
                && Objects.equals(revision.getFileArtifactId(), message.artifactId())
                && Objects.equals(revision.getFileVersionNo(), message.fileVersion());
    }

    private ArtifactCallbackResult classifyAcceptedAfterCasMiss(CallbackEnvelope message) {
        DeliveryEvidenceDO latest = lockRoot(message);
        if (latest != null && matchesCurrentRevision(latest, message)
                && (STATUS_ACCEPTED.equals(latest.getAccSyncStatus())
                || STATUS_ARCHIVE_RETRY.equals(latest.getAccSyncStatus()))
                && Objects.equals(latest.getAccAcceptedRecordId(), message.recordId())) {
            return result(message, ArtifactCallbackResult.Outcome.DUPLICATE,
                    latest.getAccSyncStatus());
        }
        return ignored(message, latest, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH);
    }

    private ArtifactCallbackResult classifyArchivedAfterCasMiss(CallbackEnvelope message) {
        DeliveryEvidenceDO latest = lockRoot(message);
        if (latest != null && matchesCurrentRevision(latest, message)
                && STATUS_ARCHIVED.equals(latest.getAccSyncStatus())
                && Objects.equals(latest.getAccArchivedRecordId(), message.recordId())) {
            return result(message, ArtifactCallbackResult.Outcome.DUPLICATE, STATUS_ARCHIVED);
        }
        return ignored(message, latest, ArtifactCallbackResult.Outcome.IGNORED_MISMATCH);
    }

    private ArtifactCallbackResult ignored(CallbackEnvelope message, DeliveryEvidenceDO root,
                                           ArtifactCallbackResult.Outcome outcome) {
        return result(message, outcome, root == null ? null : root.getAccSyncStatus());
    }

    private ArtifactCallbackResult result(CallbackEnvelope message,
                                          ArtifactCallbackResult.Outcome outcome,
                                          String status) {
        return new ArtifactCallbackResult(outcome, message.evidenceId(),
                message.evidenceRevision(), status);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            CallbackEnvelope message, ArtifactCallbackResult result) {
        return new PlatformCommandExecutionApi.SuccessFacts(
                "FIMP002_ARTIFACT_CALLBACK", "DeliveryEvidence",
                String.valueOf(message.evidenceId()), message.correlationId(),
                JsonUtils.toJsonString(result), List.of());
    }

    private static String digest(CallbackEnvelope envelope) {
        try {
            byte[] canonical = JsonUtils.toJsonString(envelope).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
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
}
