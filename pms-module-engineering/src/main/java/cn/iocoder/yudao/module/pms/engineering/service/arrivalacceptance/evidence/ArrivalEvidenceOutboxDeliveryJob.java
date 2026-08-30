package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ImplementationEvidencePublishedMessage;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class ArrivalEvidenceOutboxDeliveryJob implements JobHandler {

    static final int BATCH_SIZE = 50;
    static final long MAX_RETRY_DELAY_MINUTES = 60;

    private final PlatformOutboxDeliveryApi outboxDeliveryApi;
    private final ApplicationEventPublisher eventPublisher;
    private final ArrivalEvidenceDeliveryFinalizer deliveryFinalizer;
    private final Environment environment;

    @Override
    @TenantJob
    public String execute(String param) {
        if (TenantContextHolder.getTenantId() != null) {
            return deliverDueEvents();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            TenantContextHolder.getRequiredTenantId();
        }
        String[] result = new String[1];
        TenantUtils.execute(0L, () -> result[0] = deliverDueEvents());
        return result[0];
    }

    private String deliverDueEvents() {
        LocalDateTime dueAt = LocalDateTime.now();
        List<PlatformOutboxMessageDTO> messages = outboxDeliveryApi.claimDue(
                new PlatformOutboxClaimQuery(dueAt, BATCH_SIZE,
                        Set.of(ArrivalEvidenceEventFactory.IMPLEMENTATION_EVIDENCE_PUBLISHED)));
        int delivered = 0;
        int retried = 0;
        for (PlatformOutboxMessageDTO message : messages) {
            try {
                ImplementationEvidencePublishedMessage payload = toPublishedMessage(message);
                eventPublisher.publishEvent(payload);
                deliveryFinalizer.complete(message, payload);
                delivered++;
            } catch (RuntimeException exception) {
                LocalDateTime nextRetryTime = dueAt.plusMinutes(retryDelayMinutes(message.retryCount()));
                outboxDeliveryApi.scheduleRetry(
                        message.eventId(), message.retryCount(), nextRetryTime);
                retried++;
                log.warn("[execute][到货签收证据事件({})投递失败，计划于({})重试]",
                        message.eventId(), nextRetryTime, exception);
            }
        }
        return String.format("到货签收证据事件投递成功 %d 条，待重试 %d 条", delivered, retried);
    }

    private ImplementationEvidencePublishedMessage toPublishedMessage(
            PlatformOutboxMessageDTO message) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (blank(message.eventId())
                || !ArrivalEvidenceEventFactory.IMPLEMENTATION_EVIDENCE_PUBLISHED.equals(message.eventType())
                || !Objects.equals(tenantId, message.tenantId())) {
            throw new IllegalArgumentException("到货签收证据事件元数据不完整");
        }
        ImplementationEvidencePublishedMessage payload = JsonUtils.parseObject(
                message.payload(), ImplementationEvidencePublishedMessage.class);
        if (payload == null || !Objects.equals(message.eventId(), payload.eventId())
                || !Objects.equals(message.tenantId(), payload.tenantId())
                || invalidId(payload.evidenceId()) || invalidPositive(payload.evidenceRevision())
                || invalidId(payload.artifactId()) || invalidPositive(payload.fileVersion())
                || blank(payload.fileReference()) || blank(payload.hash())
                || !"EXE-01".equals(payload.sourceRequirement())
                || invalidId(payload.sourceRecordId())
                || payload.sourceVersion() == null || payload.sourceVersion() < 0
                || blank(payload.sourceScopeWatermark()) || payload.occurredAt() == null
                || blank(payload.correlationId())) {
            throw new IllegalArgumentException("到货签收证据事件载荷不完整或身份不一致");
        }
        return payload;
    }

    private static long retryDelayMinutes(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(1L << exponent, MAX_RETRY_DELAY_MINUTES);
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
}
