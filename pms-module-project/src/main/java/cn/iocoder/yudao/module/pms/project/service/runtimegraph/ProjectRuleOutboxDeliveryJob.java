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

    @Override
    @TenantJob
    public String execute(String param) {
        var now = LocalDateTime.now();
        int delivered = 0;
        int retry = 0;
        for (var message : outbox.claimDue(new PlatformOutboxClaimQuery(now, 50, Set.of(ProjectRuleReevaluation.EVENT_TYPE)))) {
            boolean completed = false;
            try {
                var event = JsonUtils.parseObject(message.payload(), cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested.class);
                if (!Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())
                        || !Objects.equals(event.tenantId(), message.tenantId()) || event.projectId() == null
                        || !Objects.equals(event.eventId(), message.eventId())
                        || !ProjectRuleReevaluation.EVENT_TYPE.equals(message.eventType()))
                    throw new IllegalArgumentException("REEVALUATION_EVENT_IDENTITY_INVALID");
                // Each attempt rereads current frozen contracts. The event is a wakeup, not a stale transition command.
                completed = !coordinator.reevaluate(event.projectId(), event.actorId(), event.correlationId()).unknown();
            } catch (RuntimeException unavailable) {
                // Retry only through Outbox. Do not log owner facts or native expression exception messages.
            }
            if (completed) {
                outbox.markDelivered(message.eventId(), message.retryCount());
                delivered++;
            } else {
                long delay = Math.min(60, 1L << Math.min(Math.max(message.retryCount(), 0), 6));
                outbox.scheduleRetry(message.eventId(), message.retryCount(), now.plusMinutes(delay));
                retry++;
            }
        }
        return "规则重评已处理 " + delivered + "，待重试 " + retry;
    }
}
