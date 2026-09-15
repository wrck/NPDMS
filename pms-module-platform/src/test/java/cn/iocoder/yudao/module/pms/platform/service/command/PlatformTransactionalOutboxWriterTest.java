package cn.iocoder.yudao.module.pms.platform.service.command;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformTransactionalOutboxWriterTest {

    @Mock PlatformOutboxEventMapper mapper;
    @Mock org.springframework.context.ApplicationEventPublisher publisher;

    @Test void scheduledDeliveryPersistsDueTimeWithoutPretendingItIsARetry() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(7L);
        try {
            when(mapper.insert(any(PlatformOutboxEventDO.class))).thenReturn(1);
            var writer = new PlatformTransactionalOutboxWriter(mapper, publisher);
            var due = LocalDateTime.of(2027,1,1,9,0);
            writer.appendAt("ProjectPlan", "51", new PlatformCommandExecutionApi.BusinessEvent(
                    "time-1", "ProjectRuleTimerRequested", "{\"eventId\":\"time-1\"}"), due);
            var row = ArgumentCaptor.forClass(PlatformOutboxEventDO.class);
            verify(mapper).insert(row.capture());
            assertEquals(due, row.getValue().getNextRetryTime());
            assertEquals(0, row.getValue().getRetryCount());
            assertEquals(7L, row.getValue().getTenantId());
            org.junit.jupiter.api.Assertions.assertTrue(row.getValue().getOccurredAt().isBefore(due));
        } finally { cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
    }

    @Test
    void writesProducerEventInTheSurroundingTransaction() {
        when(mapper.insert(any(PlatformOutboxEventDO.class))).thenReturn(1);
        var writer = new PlatformTransactionalOutboxWriter(mapper, publisher);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 27, 10, 0);

        writer.write(7L, new PlatformCommandExecutionApi.BusinessEvent(
                        "event-1", "FileReferenceAttached", "{\"eventId\":\"event-1\"}"),
                "FileArtifact", "11", occurredAt);

        ArgumentCaptor<PlatformOutboxEventDO> captor = ArgumentCaptor.forClass(PlatformOutboxEventDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals("event-1", captor.getValue().getEventId());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getRetryCount());
        assertEquals(occurredAt, captor.getValue().getOccurredAt());
        verify(publisher).publishEvent(new cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended(
                new cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO(
                        "event-1", "FileReferenceAttached", "{\"eventId\":\"event-1\"}", 0, 7L, occurredAt), null));
    }
}
