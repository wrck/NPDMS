package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectRuntimeCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectRuleOutboxDeliveryJobTest {
    @Test void childClosureWakeupUsesDedicatedDeliveryAndRetriesUnknownWithoutConsumingNotifications() {
        TenantContextHolder.setTenantId(7L);
        var outbox = mock(PlatformOutboxDeliveryApi.class);
        var childWait = mock(ProjectChildWaitDelivery.class);
        var coordinator = mock(ProjectRuntimeCoordinator.class);
        var timers = mock(ProjectRuleTimerDelivery.class);
        var event = new ProjectChildWaitEvents.Changed("child-change", 7L, 9L, 11L, "corr");
        when(outbox.claimDue(any())).thenReturn(List.of(new PlatformOutboxMessageDTO(event.eventId(), ProjectChildWaitEvents.EVENT_TYPE,
                JsonUtils.toJsonString(event), 0, 7L, LocalDateTime.now())));
        var job = new ProjectRuleOutboxDeliveryJob(outbox, coordinator, timers);
        org.springframework.test.util.ReflectionTestUtils.setField(job, "childWait", childWait);
        when(childWait.deliver(event)).thenReturn(false, true);
        job.execute(""); job.execute("");
        verify(outbox).scheduleRetry(eq(event.eventId()), eq(0), any());
        verify(outbox).markDelivered(event.eventId(), 0);
        verifyNoInteractions(coordinator, timers);
        clearInvocations(childWait);
        TenantContextHolder.setTenantId(8L); job.execute("");
        verifyNoInteractions(childWait);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void timerDeliveryKeepsItsFrozenIdentityAndDoesNotBecomeAnUnscopedReevaluation() {
        TenantContextHolder.setTenantId(7L);
        var outbox = mock(PlatformOutboxDeliveryApi.class);
        var coordinator = mock(ProjectRuntimeCoordinator.class);
        var timers = mock(ProjectRuleTimerDelivery.class);
        var timer = ProjectRuleTimer.create(7L,9L,51L,61L,List.of(),ProjectRuleTimer.Purpose.COMPLETION,"finish",java.time.Instant.now().minusSeconds(1),null);
        var event = timer.event();
        when(outbox.claimDue(any())).thenReturn(List.of(new PlatformOutboxMessageDTO(event.eventId(),event.eventType(),event.eventPayload(),0,7L,LocalDateTime.now())));
        when(timers.deliver(timer)).thenReturn(false, true);
        var job = new ProjectRuleOutboxDeliveryJob(outbox, coordinator, timers);
        job.execute(""); job.execute("");
        verify(outbox).scheduleRetry(eq(event.eventId()),eq(0),any());
        verify(outbox).markDelivered(event.eventId(),0);
        verifyNoInteractions(coordinator);
    }
    @Test void longExecutionIdentitiesCannotOverflowThePlatformEventId() {
        var event = new ProjectRuleReevaluation(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                "task:" + Long.MAX_VALUE + ":" + java.util.UUID.randomUUID()).event();
        assertTrue(event.eventId().length() <= 64);
        assertEquals(event.eventId(),JsonUtils.parseTree(event.eventPayload()).path("eventId").asText());
        assertEquals(Long.MAX_VALUE,JsonUtils.parseTree(event.eventPayload()).path("projectId").asLong());
    }
    @Test void unknownRulesRetryWithoutCompetingForNotificationEvents() {
        TenantContextHolder.setTenantId(7L);
        var outbox = mock(PlatformOutboxDeliveryApi.class);
        var coordinator = mock(ProjectRuntimeCoordinator.class);
        var event = new ProjectRuleReevaluation(7L, 9L, 11L, "corr").event();
        var message = new PlatformOutboxMessageDTO(event.eventId(), event.eventType(),
                event.eventPayload(), 0, 7L, LocalDateTime.now());
        when(outbox.claimDue(any())).thenReturn(List.of(message));
        when(coordinator.reevaluate(9L, 11L, "corr")).thenReturn(new ProjectRuntimeCoordinator.Result(true, 1, 0));
        var job = new ProjectRuleOutboxDeliveryJob(outbox, coordinator, mock(ProjectRuleTimerDelivery.class));
        assertTrue(job.execute("").endsWith("待重试 1"));
        verify(outbox).claimDue(argThat(query -> query.eventTypes().equals(Set.of(ProjectRuleReevaluation.EVENT_TYPE, ProjectRuleTimer.EVENT_TYPE, ProjectChildWaitEvents.EVENT_TYPE))));
        verify(outbox).scheduleRetry(eq(event.eventId()), eq(0), any());
        verify(outbox, never()).markDelivered(any(), anyInt());
        when(coordinator.reevaluate(9L, 11L, "corr")).thenReturn(new ProjectRuntimeCoordinator.Result(false, 0, 0));
        job.execute("");
        verify(outbox).markDelivered(event.eventId(), 0);
        verify(coordinator, times(2)).reevaluate(9L, 11L, "corr");
    }

    @Test void failureIsRetriedWithoutLoggingOriginalExceptionData() {
        TenantContextHolder.setTenantId(7L);
        var outbox = mock(PlatformOutboxDeliveryApi.class);
        var coordinator = mock(ProjectRuntimeCoordinator.class);
        var event = new ProjectRuleReevaluation(7L, 9L, 11L, "corr").event();
        when(outbox.claimDue(any())).thenReturn(List.of(new PlatformOutboxMessageDTO(event.eventId(), event.eventType(),
                event.eventPayload(), 9, 7L, LocalDateTime.now())));
        when(coordinator.reevaluate(anyLong(), anyLong(), anyString())).thenThrow(new IllegalStateException("source detail"));
        var before = LocalDateTime.now();
        new ProjectRuleOutboxDeliveryJob(outbox, coordinator, mock(ProjectRuleTimerDelivery.class)).execute("");
        verify(outbox).scheduleRetry(eq(event.eventId()), eq(9), argThat(time -> !time.isBefore(before.plusMinutes(60))
                && time.isBefore(before.plusMinutes(61))));
    }

    @Test void eventPayloadIncludesTheIdentityRequiredByThePlatformCommandContract() {
        var event = new ProjectRuleReevaluation(7L, 9L, 11L, "corr").event();
        var payload = JsonUtils.parseTree(event.eventPayload());
        assertEquals(event.eventId(), payload.path("eventId").asText());
        assertEquals(7L, payload.path("tenantId").asLong());
        assertEquals(9L, payload.path("projectId").asLong());
    }
}
