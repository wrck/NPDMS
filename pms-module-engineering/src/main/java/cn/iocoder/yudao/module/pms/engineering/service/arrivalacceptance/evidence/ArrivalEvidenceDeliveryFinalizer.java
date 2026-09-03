package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ImplementationEvidencePublishedMessage;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceFirstWatermarkUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceIdentityQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

@Service
public class ArrivalEvidenceDeliveryFinalizer {

    private static final String PUBLISHED = "PUBLISHED_PENDING_ACC";
    private static final Set<String> CALLBACK_COMPLETED_STATES = Set.of(
            "ACCEPTED_PENDING_ARCHIVE", "ARCHIVE_ACK_PENDING_RETRY", "ARCHIVED");

    private final DeliveryEvidenceMapper evidenceMapper;
    private final PlatformOutboxDeliveryApi outboxDeliveryApi;
    private final Clock clock;

    @Autowired
    public ArrivalEvidenceDeliveryFinalizer(DeliveryEvidenceMapper evidenceMapper,
                                            PlatformOutboxDeliveryApi outboxDeliveryApi) {
        this(evidenceMapper, outboxDeliveryApi, Clock.systemDefaultZone());
    }

    ArrivalEvidenceDeliveryFinalizer(DeliveryEvidenceMapper evidenceMapper,
                                     PlatformOutboxDeliveryApi outboxDeliveryApi,
                                     Clock clock) {
        this.evidenceMapper = evidenceMapper;
        this.outboxDeliveryApi = outboxDeliveryApi;
        this.clock = clock;
    }

    @Transactional
    public void complete(PlatformOutboxMessageDTO outbox,
                         ImplementationEvidencePublishedMessage payload) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(tenantId, outbox.tenantId())
                || !Objects.equals(tenantId, payload.tenantId())
                || !Objects.equals(outbox.eventId(), payload.eventId())) {
            throw new IllegalStateException("arrival evidence delivery identity changed");
        }
        DeliveryEvidenceDO root = lock(payload);
        if (canMarkWithoutWatermark(root, payload)) {
            outboxDeliveryApi.markDelivered(outbox.eventId(), outbox.retryCount());
            return;
        }
        if (!PUBLISHED.equals(root.getAccSyncStatus())
                || !Objects.equals(root.getAccLastEventId(), payload.eventId())
                || root.getAccRetryCount() == null || root.getAccRetryCount() != 0
                || root.getAccNextRetryAt() != null) {
            throw new IllegalStateException("arrival evidence delivery state is not explainable");
        }
        int updated = evidenceMapper.registerFirstCallbackWatermarkIfMatch(
                new DeliveryEvidenceFirstWatermarkUpdate(payload.tenantId(), payload.evidenceId(),
                        payload.evidenceRevision(), root.getVersion(), payload.eventId(),
                        LocalDateTime.now(clock).plusMinutes(1)));
        if (updated != 1) {
            DeliveryEvidenceDO current = lock(payload);
            if (!canMarkWithoutWatermark(current, payload)) {
                throw new IllegalStateException("arrival evidence changed before delivery completion");
            }
        }
        outboxDeliveryApi.markDelivered(outbox.eventId(), outbox.retryCount());
    }

    private DeliveryEvidenceDO lock(ImplementationEvidencePublishedMessage payload) {
        DeliveryEvidenceDO root = evidenceMapper.selectByIdentityForUpdate(
                new DeliveryEvidenceIdentityQuery(payload.tenantId(), payload.evidenceId()));
        if (root == null || !Objects.equals(root.getCurrentRevisionNo(), payload.evidenceRevision())
                || !Objects.equals(root.getAccCorrelationId(), payload.correlationId())) {
            throw new IllegalStateException("arrival evidence delivery fact is stale");
        }
        return root;
    }

    private static boolean canMarkWithoutWatermark(
            DeliveryEvidenceDO root, ImplementationEvidencePublishedMessage payload) {
        if (CALLBACK_COMPLETED_STATES.contains(root.getAccSyncStatus())) {
            return true;
        }
        if (PUBLISHED.equals(root.getAccSyncStatus())
                && Objects.equals(root.getAccLastEventId(), payload.eventId())
                && root.getAccRetryCount() != null && root.getAccRetryCount() > 0
                && root.getAccNextRetryAt() != null) {
            return true;
        }
        return !Objects.equals(root.getAccLastEventId(), payload.eventId())
                && root.getAccLastPublishedAt() != null
                && payload.occurredAt() != null
                && root.getAccLastPublishedAt().isAfter(payload.occurredAt());
    }
}
