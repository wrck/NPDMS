package cn.iocoder.yudao.module.pms.platform.service.outbox;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.PlatformOutboxDeliveryMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.DueOutboxListQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.OutboxDeliveryUpdateQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.OutboxRetryUpdateQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformOutboxDeliveryApiImplTest {

    @Mock
    private PlatformOutboxDeliveryMapper mapper;
    @InjectMocks
    private PlatformOutboxDeliveryApiImpl service;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void claimDueLocksOnlySupportedTenantEventAndMapsFrozenPayload() {
        LocalDateTime dueAt = LocalDateTime.of(2026, 8, 25, 9, 0);
        PlatformOutboxEventDO row = new PlatformOutboxEventDO();
        row.setEventId("evt-1");
        row.setEventType("ProjectServiceManagerAssigned");
        row.setPayload("{\"assignmentId\":1}");
        row.setRetryCount(2);
        row.setTenantId(7L);
        row.setOccurredAt(dueAt.minusMinutes(1));
        when(mapper.selectDueForUpdate(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(row));

        var result = service.claimDue(new PlatformOutboxClaimQuery(
                dueAt, 20, Set.of("ProjectServiceManagerAssigned", "TaskAssigned")));

        assertEquals(1, result.size());
        assertEquals("evt-1", result.getFirst().eventId());
        assertEquals("{\"assignmentId\":1}", result.getFirst().payload());
        ArgumentCaptor<DueOutboxListQuery> captor = ArgumentCaptor.forClass(DueOutboxListQuery.class);
        verify(mapper).selectDueForUpdate(captor.capture());
        assertEquals(7L, captor.getValue().tenantId());
        assertEquals(Set.of("ProjectServiceManagerAssigned", "TaskAssigned"),
                captor.getValue().eventTypes());
        assertEquals(20, captor.getValue().limit());
    }

    @Test
    void claimDueSupportsAcceptanceReportVersionChangedWithoutOpeningClosureEvents() {
        LocalDateTime dueAt = LocalDateTime.of(2026, 8, 30, 12, 0);
        when(mapper.selectDueForUpdate(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        service.claimDue(new PlatformOutboxClaimQuery(
                "AcceptanceReportVersionChanged", dueAt, 10));

        verify(mapper).selectDueForUpdate(new DueOutboxListQuery(
                7L, Set.of("AcceptanceReportVersionChanged"), dueAt, 10));
        assertThrows(IllegalArgumentException.class, () -> service.claimDue(
                new PlatformOutboxClaimQuery("ClosureGateRecheckRequested", dueAt, 10)));
    }

    @Test
    void claimDueSupportsDeviceAssignedEventType() {
        LocalDateTime dueAt = LocalDateTime.of(2026, 8, 27, 12, 0);
        when(mapper.selectDueForUpdate(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        service.claimDue(new PlatformOutboxClaimQuery("DeviceAssigned", dueAt, 10));

        verify(mapper).selectDueForUpdate(new DueOutboxListQuery(7L, Set.of("DeviceAssigned"), dueAt, 10));
    }

    @Test
    void claimDueSupportsImplementationEvidencePublishedEventType() {
        LocalDateTime dueAt = LocalDateTime.of(2026, 8, 30, 12, 0);
        when(mapper.selectDueForUpdate(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        service.claimDue(new PlatformOutboxClaimQuery(
                "ImplementationEvidencePublished", dueAt, 10));

        verify(mapper).selectDueForUpdate(new DueOutboxListQuery(
                7L, Set.of("ImplementationEvidencePublished"), dueAt, 10));
    }

    @Test
    void completionAndRetryUseTenantRetryCountCas() {
        LocalDateTime next = LocalDateTime.of(2026, 8, 25, 9, 5);
        when(mapper.markDeliveredIfPending(new OutboxDeliveryUpdateQuery(7L, "evt-2", 3))).thenReturn(1);
        when(mapper.scheduleRetryIfPending(new OutboxRetryUpdateQuery(7L, "evt-3", 4, next))).thenReturn(1);

        service.markDelivered("evt-2", 3);
        service.scheduleRetry("evt-3", 4, next);

        verify(mapper).markDeliveredIfPending(new OutboxDeliveryUpdateQuery(7L, "evt-2", 3));
        verify(mapper).scheduleRetryIfPending(new OutboxRetryUpdateQuery(7L, "evt-3", 4, next));
    }

    @Test
    void rejectsStaleDeliveryAndRetryCas() {
        LocalDateTime next = LocalDateTime.of(2026, 8, 25, 9, 5);

        assertThrows(IllegalStateException.class, () -> service.markDelivered("evt-stale", 1));
        assertThrows(IllegalStateException.class, () -> service.scheduleRetry("evt-stale", 1, next));
    }

    @Test
    void rejectsUnboundedClaimAndInvalidCas() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 9, 0);
        assertThrows(IllegalArgumentException.class,
                () -> service.claimDue(new PlatformOutboxClaimQuery(
                        now, 101, Set.of("ProjectServiceManagerAssigned"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.claimDue(new PlatformOutboxClaimQuery(now, 10, Set.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.claimDue(new PlatformOutboxClaimQuery(now, 10, Set.of("UNKNOWN"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.claimDue(new PlatformOutboxClaimQuery("DeviceAssigned", now, 101)));
        assertThrows(IllegalArgumentException.class, () -> service.markDelivered("", 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.scheduleRetry("evt-1", -1, now));
    }
}
