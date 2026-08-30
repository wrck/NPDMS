package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ImplementationEvidencePublishedMessage;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArrivalEvidenceOutboxDeliveryJobTest {

    @Mock PlatformOutboxDeliveryApi outboxApi;
    @Mock ApplicationEventPublisher publisher;
    @Mock ArrivalEvidenceDeliveryFinalizer finalizer;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void publishesValidatedEvidenceEventAndMarksDelivered() {
        ImplementationEvidencePublishedMessage payload = payload("evt-1", 7L);
        when(outboxApi.claimDue(any())).thenReturn(List.of(message(payload, 0)));

        String result = new ArrivalEvidenceOutboxDeliveryJob(
                outboxApi, publisher, finalizer, new MockEnvironment()).execute(null);

        assertEquals("到货签收证据事件投递成功 1 条，待重试 0 条", result);
        verify(publisher).publishEvent(payload);
        verify(finalizer).complete(message(payload, 0), payload);
        ArgumentCaptor<PlatformOutboxClaimQuery> claim =
                ArgumentCaptor.forClass(PlatformOutboxClaimQuery.class);
        verify(outboxApi).claimDue(claim.capture());
        assertEquals(50, claim.getValue().limit());
        assertEquals(java.util.Set.of("ImplementationEvidencePublished"),
                claim.getValue().eventTypes());
    }

    @Test
    void retriesSameEventWithExponentialDelayWhenConsumerFails() {
        ImplementationEvidencePublishedMessage payload = payload("evt-2", 7L);
        when(outboxApi.claimDue(any())).thenReturn(List.of(message(payload, 2)));
        doThrow(new IllegalStateException("ACC consumer failed"))
                .when(publisher).publishEvent(payload);

        String result = new ArrivalEvidenceOutboxDeliveryJob(
                outboxApi, publisher, finalizer, new MockEnvironment()).execute(null);

        assertEquals("到货签收证据事件投递成功 0 条，待重试 1 条", result);
        ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxApi).scheduleRetry(org.mockito.ArgumentMatchers.eq("evt-2"),
                org.mockito.ArgumentMatchers.eq(2), retryAt.capture());
        verify(finalizer, org.mockito.Mockito.never()).complete(any(), any());
        ArgumentCaptor<PlatformOutboxClaimQuery> claim =
                ArgumentCaptor.forClass(PlatformOutboxClaimQuery.class);
        verify(outboxApi).claimDue(claim.capture());
        long delaySeconds = java.time.Duration.between(
                claim.getValue().dueAt(), retryAt.getValue()).getSeconds();
        assertTrue(delaySeconds >= 239 && delaySeconds <= 241);
    }

    @Test
    void retriesIdentityMismatchWithoutPublishing() {
        ImplementationEvidencePublishedMessage payload = payload("evt-3", 8L);
        when(outboxApi.claimDue(any())).thenReturn(List.of(message(payload, 0)));

        String result = new ArrivalEvidenceOutboxDeliveryJob(
                outboxApi, publisher, finalizer, new MockEnvironment()).execute(null);

        assertEquals("到货签收证据事件投递成功 0 条，待重试 1 条", result);
        verify(outboxApi).scheduleRetry(org.mockito.ArgumentMatchers.eq("evt-3"),
                org.mockito.ArgumentMatchers.eq(0), any(LocalDateTime.class));
        verify(publisher, org.mockito.Mockito.never()).publishEvent(any());
    }

    private static ImplementationEvidencePublishedMessage payload(String eventId, Long tenantId) {
        return new ImplementationEvidencePublishedMessage(
                eventId, tenantId, 50L, 1, 40L, 5, "REF-1", "hash",
                "EXE-01", 900L, 3L, "{\"deliveryScopeVersion\":8}",
                LocalDateTime.of(2026, 8, 30, 10, 0), "corr-1");
    }

    private static PlatformOutboxMessageDTO message(
            ImplementationEvidencePublishedMessage payload, int retryCount) {
        return new PlatformOutboxMessageDTO(
                payload.eventId(), "ImplementationEvidencePublished",
                JsonUtils.toJsonString(payload), retryCount, 7L, payload.occurredAt());
    }
}
