package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileOutboxDeliveryJob;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileReferenceAttachedMessage;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileVersionCommittedMessage;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileOutboxDeliveryJobTest {

    @Mock PlatformOutboxDeliveryApi outboxApi;
    @Mock ApplicationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void publishesValidatedFileEventsAndMarksDelivered() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 26, 10, 0);
        var version = new FileVersionCommittedMessage("evt-v", 7L, 11L, 1,
                "a".repeat(64), "PASSED", occurredAt, "op-1");
        var reference = new FileReferenceAttachedMessage("evt-r", 7L, 21L, 11L, 1,
                "SOL", "CHANGE", "900", "EVIDENCE", occurredAt, "op-1");
        when(outboxApi.claimDue(any())).thenReturn(List.of(
                message("evt-v", "FileVersionCommitted", JsonUtils.toJsonString(version), 0, occurredAt),
                message("evt-r", "FileReferenceAttached", JsonUtils.toJsonString(reference), 1, occurredAt)));

        String result = new FileOutboxDeliveryJob(outboxApi, publisher, new MockEnvironment()).execute(null);

        assertEquals("文件事件投递成功 2 条，待重试 0 条", result);
        verify(publisher).publishEvent(version);
        verify(publisher).publishEvent(reference);
        verify(outboxApi).markDelivered("evt-v", 0);
        verify(outboxApi).markDelivered("evt-r", 1);
    }

    @Test
    void schedulesTheSameEventForRetryWhenPublishingFails() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 26, 10, 0);
        var version = new FileVersionCommittedMessage("evt-v", 7L, 11L, 1,
                "a".repeat(64), "PASSED", occurredAt, "op-1");
        when(outboxApi.claimDue(any())).thenReturn(List.of(
                message("evt-v", "FileVersionCommitted", JsonUtils.toJsonString(version), 2, occurredAt)));
        doThrow(new IllegalStateException("consumer failed")).when(publisher)
                .publishEvent(any(FileVersionCommittedMessage.class));

        String result = new FileOutboxDeliveryJob(outboxApi, publisher, new MockEnvironment()).execute(null);

        assertEquals("文件事件投递成功 0 条，待重试 1 条", result);
        ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxApi).scheduleRetry(org.mockito.ArgumentMatchers.eq("evt-v"),
                org.mockito.ArgumentMatchers.eq(2), retryAt.capture());
    }

    @Test
    void establishesTenantZeroOnlyWhenSingleTenantModeIsExplicitlyConfigured() {
        TenantContextHolder.clear();
        when(outboxApi.claimDue(any())).thenReturn(List.of());

        String result = new FileOutboxDeliveryJob(outboxApi, publisher,
                new MockEnvironment().withProperty("yudao.tenant.enable", "false")).execute(null);

        assertEquals("文件事件投递成功 0 条，待重试 0 条", result);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void rejectsMissingTenantContextWhenMultiTenantModeIsEnabled() {
        TenantContextHolder.clear();

        assertThrows(NullPointerException.class, () -> new FileOutboxDeliveryJob(outboxApi, publisher,
                new MockEnvironment().withProperty("yudao.tenant.enable", "true")).execute(null));
    }

    private PlatformOutboxMessageDTO message(String eventId, String eventType, String payload,
                                             int retryCount, LocalDateTime occurredAt) {
        return new PlatformOutboxMessageDTO(eventId, eventType, payload, retryCount, 7L, occurredAt);
    }
}
