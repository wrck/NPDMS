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

    @Test
    void writesProducerEventInTheSurroundingTransaction() {
        when(mapper.insert(any(PlatformOutboxEventDO.class))).thenReturn(1);
        var writer = new PlatformTransactionalOutboxWriter(mapper);
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
    }
}
