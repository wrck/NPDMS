package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ImplementationEvidencePublishedMessage;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRetryClaimQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRetryUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceRevisionQuery;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
public class ArrivalEvidenceRetryService {

    private static final String SCOPE = "IMP:ARRIVAL_EVIDENCE_RETRY";
    private static final String PUBLISHED = "PUBLISHED_PENDING_ACC";
    private static final String PUBLISH_RETRY = "ARCHIVE_PENDING_RETRY";
    private static final String ACCEPTED = "ACCEPTED_PENDING_ARCHIVE";
    private static final String ARCHIVE_RETRY = "ARCHIVE_ACK_PENDING_RETRY";

    private final DeliveryEvidenceMapper evidenceMapper;
    private final DeliveryEvidenceRevisionMapper revisionMapper;
    private final ArrivalAcceptanceMapper acceptanceMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final ArrivalEvidenceEventFactory eventFactory;
    private final Clock clock;

    @Autowired
    public ArrivalEvidenceRetryService(DeliveryEvidenceMapper evidenceMapper,
                                       DeliveryEvidenceRevisionMapper revisionMapper,
                                       ArrivalAcceptanceMapper acceptanceMapper,
                                       PlatformCommandExecutionApi commandExecutionApi) {
        this(evidenceMapper, revisionMapper, acceptanceMapper, commandExecutionApi,
                new ArrivalEvidenceEventFactory(), Clock.systemDefaultZone());
    }

    ArrivalEvidenceRetryService(DeliveryEvidenceMapper evidenceMapper,
                                DeliveryEvidenceRevisionMapper revisionMapper,
                                ArrivalAcceptanceMapper acceptanceMapper,
                                PlatformCommandExecutionApi commandExecutionApi,
                                ArrivalEvidenceEventFactory eventFactory,
                                Clock clock) {
        this.evidenceMapper = evidenceMapper;
        this.revisionMapper = revisionMapper;
        this.acceptanceMapper = acceptanceMapper;
        this.commandExecutionApi = commandExecutionApi;
        this.eventFactory = eventFactory;
        this.clock = clock;
    }

    @Transactional
    public boolean retryNext(LocalDateTime dueAt) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        DeliveryEvidenceDO root = evidenceMapper.selectNextDueForRetry(
                new DeliveryEvidenceRetryClaimQuery(tenantId, dueAt));
        if (root == null) {
            return false;
        }
        requireRetryableRoot(root, tenantId);
        String key = root.getId() + ":" + root.getCurrentRevisionNo() + ":"
                + root.getAccSyncStatus() + ":" + root.getAccRetryCount();
        PlatformCommandExecutionApi.ExecutionResult<ArrivalEvidenceRetryResult> execution =
                commandExecutionApi.execute(
                        new PlatformCommandExecutionApi.IdempotencyScope(tenantId, SCOPE, 0L, key),
                        sha256(key), ArrivalEvidenceRetryResult.class,
                        () -> retry(root), result -> successFacts(root, result));
        return switch (execution.decision()) {
            case NEW, REPLAY_COMPLETED -> true;
            case CONFLICT -> throw new IllegalStateException("arrival evidence retry key conflict");
            case IN_PROGRESS -> throw new IllegalStateException("arrival evidence retry is in progress");
        };
    }

    private ArrivalEvidenceRetryResult retry(DeliveryEvidenceDO root) {
        LocalDateTime retriedAt = LocalDateTime.now(clock);
        int oldCount = root.getAccRetryCount();
        int newCount = Math.addExact(oldCount, 1);
        String targetStatus = targetStatus(root.getAccSyncStatus());
        boolean queuesEvent = PUBLISH_RETRY.equals(root.getAccSyncStatus())
                || ARCHIVE_RETRY.equals(root.getAccSyncStatus());
        ImplementationEvidencePublishedMessage message = queuesEvent
                ? rebuildMessage(root, retriedAt) : null;
        int updated = evidenceMapper.advanceRetryIfMatch(new DeliveryEvidenceRetryUpdate(
                root.getTenantId(), root.getId(), root.getCurrentRevisionNo(), root.getVersion(),
                root.getAccSyncStatus(), targetStatus, oldCount, newCount,
                retriedAt.plusMinutes(delayMinutes(oldCount)),
                message == null ? null : message.eventId(), message == null ? null : retriedAt));
        if (updated != 1) {
            throw new IllegalStateException("arrival evidence changed before retry");
        }
        return new ArrivalEvidenceRetryResult(root.getId(), root.getCurrentRevisionNo(),
                targetStatus, newCount, message == null ? null : message.eventId(), retriedAt,
                message);
    }

    private ImplementationEvidencePublishedMessage rebuildMessage(
            DeliveryEvidenceDO root, LocalDateTime retriedAt) {
        DeliveryEvidenceRevisionDO revision = revisionMapper.selectRevision(
                new DeliveryEvidenceRevisionQuery(root.getTenantId(), root.getId(),
                        root.getCurrentRevisionNo()));
        if (revision == null || !Objects.equals(revision.getEvidenceId(), root.getId())
                || !Objects.equals(revision.getRevisionNo(), root.getCurrentRevisionNo())
                || !Objects.equals(revision.getTenantId(), root.getTenantId())
                || !Objects.equals(revision.getSourceRecordId(), root.getSourceObjectId())
                || revision.getFileArtifactId() == null || revision.getFileArtifactId() <= 0
                || revision.getFileVersionNo() == null || revision.getFileVersionNo() <= 0
                || blank(revision.getFileReferenceId()) || blank(revision.getFileHash())
                || revision.getSourceVersion() == null || revision.getSourceVersion() < 0) {
            throw new IllegalStateException("arrival evidence revision source is inconsistent");
        }
        ArrivalAcceptanceDO acceptance = acceptanceMapper.selectRow(
                new ArrivalRowQuery(root.getTenantId(), revision.getSourceRecordId()));
        if (acceptance == null || !Objects.equals(acceptance.getTenantId(), root.getTenantId())
                || !"CONFIRMED".equals(acceptance.getStatus())
                || !Objects.equals(acceptance.getId(), root.getSourceObjectId())
                || !Objects.equals(acceptance.getEvidenceId(), root.getId())
                || !Objects.equals(acceptance.getEvidenceRevision(), root.getCurrentRevisionNo())
                || blank(acceptance.getScopeWatermark())) {
            throw new IllegalStateException("confirmed arrival evidence source is inconsistent");
        }
        String eventId = eventFactory.nextEventId();
        return new ImplementationEvidencePublishedMessage(eventId, root.getTenantId(), root.getId(),
                root.getCurrentRevisionNo(), revision.getFileArtifactId(),
                revision.getFileVersionNo(), revision.getFileReferenceId(), revision.getFileHash(),
                "EXE-01", acceptance.getId(), revision.getSourceVersion(),
                acceptance.getScopeWatermark(), retriedAt, root.getAccCorrelationId());
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            DeliveryEvidenceDO root, ArrivalEvidenceRetryResult result) {
        List<PlatformCommandExecutionApi.BusinessEvent> events = result.message() == null
                ? List.of() : List.of(eventFactory.published(result.message()));
        return new PlatformCommandExecutionApi.SuccessFacts(
                "ARRIVAL_EVIDENCE_RETRY", "DeliveryEvidence", String.valueOf(root.getId()),
                root.getAccCorrelationId(), JsonUtils.toJsonString(result), events);
    }

    private static void requireRetryableRoot(DeliveryEvidenceDO root, Long tenantId) {
        if (!Objects.equals(root.getTenantId(), tenantId) || root.getId() == null
                || root.getCurrentRevisionNo() == null || root.getCurrentRevisionNo() <= 0
                || root.getVersion() == null || root.getAccRetryCount() == null
                || root.getAccRetryCount() < 0 || root.getAccNextRetryAt() == null
                || blank(root.getAccCorrelationId()) || blank(root.getSourceRequirement())
                || !"EXE-01".equals(root.getSourceRequirement())
                || !"ARRIVAL_ACCEPTANCE".equals(root.getSourceObjectType())
                || root.getSourceObjectId() == null) {
            throw new IllegalStateException("arrival evidence retry fact is incomplete");
        }
        targetStatus(root.getAccSyncStatus());
    }

    private static String targetStatus(String status) {
        return switch (status) {
            case PUBLISHED -> PUBLISH_RETRY;
            case PUBLISH_RETRY -> PUBLISHED;
            case ACCEPTED, ARCHIVE_RETRY -> ARCHIVE_RETRY;
            default -> throw new IllegalStateException("arrival evidence is not retryable");
        };
    }

    static long delayMinutes(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(1L << exponent, 60L);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ArrivalEvidenceRetryResult(
            Long evidenceId,
            Integer evidenceRevision,
            String status,
            Integer retryCount,
            String eventId,
            LocalDateTime retriedAt,
            ImplementationEvidencePublishedMessage message) {
    }
}
