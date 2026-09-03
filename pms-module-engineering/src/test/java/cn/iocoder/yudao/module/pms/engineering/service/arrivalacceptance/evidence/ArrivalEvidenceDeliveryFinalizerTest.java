package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ImplementationEvidencePublishedMessage;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.DeliveryEvidenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.DeliveryEvidenceFirstWatermarkUpdate;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArrivalEvidenceDeliveryFinalizerTest {

    @Mock DeliveryEvidenceMapper mapper;
    @Mock PlatformOutboxDeliveryApi outboxApi;
    private ArrivalEvidenceDeliveryFinalizer finalizer;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        finalizer = new ArrivalEvidenceDeliveryFinalizer(mapper, outboxApi,
                Clock.fixed(Instant.parse("2026-08-30T02:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void registersFirstWatermarkAndMarksOutboxInOneCompletion() {
        when(mapper.selectByIdentityForUpdate(any())).thenReturn(root("PUBLISHED_PENDING_ACC", "evt-1"));
        when(mapper.registerFirstCallbackWatermarkIfMatch(any())).thenReturn(1);

        finalizer.complete(outbox("evt-1"), payload("evt-1"));

        ArgumentCaptor<DeliveryEvidenceFirstWatermarkUpdate> update =
                ArgumentCaptor.forClass(DeliveryEvidenceFirstWatermarkUpdate.class);
        verify(mapper).registerFirstCallbackWatermarkIfMatch(update.capture());
        assertEquals(LocalDateTime.of(2026, 8, 30, 2, 1), update.getValue().nextRetryAt());
        verify(outboxApi).markDelivered("evt-1", 0);
    }

    @Test
    void synchronousAcceptedCallbackDoesNotGetOverwritten() {
        when(mapper.selectByIdentityForUpdate(any()))
                .thenReturn(root("ACCEPTED_PENDING_ARCHIVE", "accepted-event"));

        finalizer.complete(outbox("evt-1"), payload("evt-1"));

        verify(mapper, never()).registerFirstCallbackWatermarkIfMatch(any());
        verify(outboxApi).markDelivered("evt-1", 0);
    }

    @Test
    void deliveredBusinessRetryKeepsItsExistingWatermark() {
        DeliveryEvidenceDO root = root("PUBLISHED_PENDING_ACC", "evt-1");
        root.setAccRetryCount(2);
        root.setAccNextRetryAt(LocalDateTime.of(2026, 8, 30, 2, 2));
        when(mapper.selectByIdentityForUpdate(any())).thenReturn(root);

        finalizer.complete(outbox("evt-1"), payload("evt-1"));

        verify(mapper, never()).registerFirstCallbackWatermarkIfMatch(any());
        verify(outboxApi).markDelivered("evt-1", 0);
    }

    @Test
    void staleRevisionFailsWithoutMarkingDelivered() {
        DeliveryEvidenceDO root = root("PUBLISHED_PENDING_ACC", "evt-1");
        root.setCurrentRevisionNo(2);
        when(mapper.selectByIdentityForUpdate(any())).thenReturn(root);

        assertThrows(IllegalStateException.class,
                () -> finalizer.complete(outbox("evt-1"), payload("evt-1")));

        verify(outboxApi, never()).markDelivered(any(), any(Integer.class));
    }

    private static DeliveryEvidenceDO root(String status, String eventId) {
        DeliveryEvidenceDO root = new DeliveryEvidenceDO();
        root.setId(50L);
        root.setTenantId(7L);
        root.setCurrentRevisionNo(1);
        root.setAccSyncStatus(status);
        root.setAccLastEventId(eventId);
        root.setAccCorrelationId("corr-1");
        root.setAccRetryCount(0);
        root.setVersion(3);
        return root;
    }

    private static ImplementationEvidencePublishedMessage payload(String eventId) {
        return new ImplementationEvidencePublishedMessage(eventId, 7L, 50L, 1, 40L, 5,
                "REF-1", "hash", "EXE-01", 900L, 3L, "scope",
                LocalDateTime.of(2026, 8, 30, 1, 59), "corr-1");
    }

    private static PlatformOutboxMessageDTO outbox(String eventId) {
        return new PlatformOutboxMessageDTO(eventId, "ImplementationEvidencePublished", "{}",
                0, 7L, LocalDateTime.of(2026, 8, 30, 1, 59));
    }
}
