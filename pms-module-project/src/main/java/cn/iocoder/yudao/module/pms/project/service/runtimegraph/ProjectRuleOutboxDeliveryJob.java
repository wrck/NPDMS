package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProjectRuleOutboxDeliveryJob implements JobHandler {
    private final PlatformOutboxDeliveryApi outbox;
    private final cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectRuntimeCoordinator coordinator;
    private final ProjectRuleTimerDelivery timers;
    @jakarta.annotation.Resource
    private ProjectChildWaitDelivery childWait;

    @Override
    @TenantJob
    public String execute(String param) {
        var now = LocalDateTime.now();
        int delivered = 0;
        int retry = 0;
        for (var message : outbox.claimDue(new PlatformOutboxClaimQuery(now, 50, Set.of(ProjectRuleReevaluation.EVENT_TYPE, ProjectRuleTimer.EVENT_TYPE, ProjectChildWaitEvents.EVENT_TYPE)))) {
            if (deliver(message, now)) delivered++;
            else retry++;
        }
        return "规则重评已处理 " + delivered + "，待重试 " + retry;
    }

    /** Shared single-event delivery; normal commits call this immediately, Quartz only recovers pending events/timers. */
    public boolean deliver(cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO message,
            LocalDateTime now) {
        boolean completed = false;
        try {
            if (ProjectChildWaitEvents.EVENT_TYPE.equals(message.eventType())) {
                var event = JsonUtils.parseObject(message.payload(), ProjectChildWaitEvents.Changed.class);
                if (!Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())
                        || !Objects.equals(event.tenantId(), message.tenantId()) || event.projectId() == null
                        || !Objects.equals(event.eventId(), message.eventId()))
                    throw new IllegalArgumentException("CHILD_WAIT_EVENT_IDENTITY_INVALID");
                completed = childWait.deliver(event);
            } else if (ProjectRuleTimer.EVENT_TYPE.equals(message.eventType())) {
                var event = JsonUtils.parseObject(message.payload(), ProjectRuleTimer.class);
                if (!Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())
                        || !Objects.equals(event.tenantId(), message.tenantId()) || !Objects.equals(event.eventId(), message.eventId()))
                    throw new IllegalArgumentException("TIMER_EVENT_IDENTITY_INVALID");
                completed = timers.deliver(event);
            } else {
                var event = JsonUtils.parseObject(message.payload(), cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested.class);
                if (!Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())
                        || !Objects.equals(event.tenantId(), message.tenantId()) || event.projectId() == null
                        || !Objects.equals(event.eventId(), message.eventId())
                        || !ProjectRuleReevaluation.EVENT_TYPE.equals(message.eventType()))
                    throw new IllegalArgumentException("REEVALUATION_EVENT_IDENTITY_INVALID");
                // Each attempt rereads current frozen contracts. The event is a wakeup, not a stale transition command.
                completed = !coordinator.reevaluate(event.projectId(), event.actorId(), event.correlationId()).unknown();
            }
        } catch (RuntimeException unavailable) {
            // Retry only through Outbox. Do not log owner facts or native expression exception messages.
        }
        try {
            if (completed) {
                outbox.markDelivered(message.eventId(), message.retryCount());
            } else {
                long delay = Math.min(60, 1L << Math.min(Math.max(message.retryCount(), 0), 6));
                outbox.scheduleRetry(message.eventId(), message.retryCount(), now.plusMinutes(delay));
            }
        } catch (IllegalStateException conflict) {
            // Immediate delivery and recovery can observe the same pending event. Node commands are idempotent;
            // only the CAS winner updates delivery state, and the loser must not abort unrelated events.
            if (!Set.of("OUTBOX_DELIVERY_CAS_CONFLICT", "OUTBOX_RETRY_CAS_CONFLICT").contains(conflict.getMessage())) throw conflict;
        }
        return completed;
    }
}
