package cn.iocoder.yudao.module.pms.project.service.taskworkbench.event;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskOutboxDeliveryJobTest {

    @Mock private PlatformOutboxDeliveryApi outboxDeliveryApi;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ProjectTaskOutboxDeliveryJob job;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(9L);
        job = new ProjectTaskOutboxDeliveryJob(outboxDeliveryApi, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void claimsOnlyProjectTaskEventsPublishesAssignedAndMarksDelivered() {
        when(outboxDeliveryApi.claimDue(any())).thenReturn(List.of(message("evt-1", 0)));

        String result = job.execute(null);

        ArgumentCaptor<PlatformOutboxClaimQuery> queryCaptor =
                ArgumentCaptor.forClass(PlatformOutboxClaimQuery.class);
        verify(outboxDeliveryApi).claimDue(queryCaptor.capture());
        assertEquals(Set.of("TaskAssigned", "TaskCompleted"), queryCaptor.getValue().eventTypes());
        ArgumentCaptor<TaskAssignedMessage> eventCaptor = ArgumentCaptor.forClass(TaskAssignedMessage.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("evt-1", eventCaptor.getValue().eventId());
        assertEquals(100L, eventCaptor.getValue().projectTaskId());
        verify(outboxDeliveryApi).markDelivered("evt-1", 0);
        assertEquals("项目任务事件投递成功 1 条，待重试 0 条", result);
    }

    @Test
    void publishesTaskCompletedWithOriginalEventId() {
        when(outboxDeliveryApi.claimDue(any())).thenReturn(List.of(completedMessage("evt-complete", 0)));

        job.execute(null);

        ArgumentCaptor<TaskCompletedMessage> eventCaptor = ArgumentCaptor.forClass(TaskCompletedMessage.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("evt-complete", eventCaptor.getValue().eventId());
        assertEquals(800L, eventCaptor.getValue().completionEvaluationId());
        verify(outboxDeliveryApi).markDelivered("evt-complete", 0);
    }

    @Test
    void publishFailureSchedulesRetryAndDoesNotMarkDelivered() {
        when(outboxDeliveryApi.claimDue(any())).thenReturn(List.of(message("evt-2", 3)));
        doThrow(new IllegalStateException("listener unavailable")).when(eventPublisher)
                .publishEvent(any(TaskAssignedMessage.class));

        String result = job.execute(null);

        verify(outboxDeliveryApi).scheduleRetry(
                org.mockito.ArgumentMatchers.eq("evt-2"), org.mockito.ArgumentMatchers.eq(3),
                any(LocalDateTime.class));
        verify(outboxDeliveryApi, never()).markDelivered(any(), org.mockito.ArgumentMatchers.anyInt());
        assertEquals("项目任务事件投递成功 0 条，待重试 1 条", result);
    }

    private PlatformOutboxMessageDTO message(String eventId, int retryCount) {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 26, 9, 30);
        TaskAssignedMessage.Payload payload = new TaskAssignedMessage.Payload(
                9L, 1L, 100L, 66L, 900L, 1, 7L, occurredAt);
        return new PlatformOutboxMessageDTO(eventId, "TaskAssigned", JsonUtils.toJsonString(payload),
                retryCount, 9L, occurredAt);
    }

    private PlatformOutboxMessageDTO completedMessage(String eventId, int retryCount) {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 26, 10, 30);
        TaskCompletedMessage.Payload payload = new TaskCompletedMessage.Payload(
                9L, 1L, 100L, 800L, 5, 91L, 2, 4L, 7L, occurredAt);
        return new PlatformOutboxMessageDTO(eventId, "TaskCompleted", JsonUtils.toJsonString(payload),
                retryCount, 9L, occurredAt);
    }
}
