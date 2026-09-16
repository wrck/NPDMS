package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Set;

/** Each committed change dispatches immediately; the durable Outbox owns recovery, not the executor queue. */
@Component
@Slf4j
public class ProjectRuleCommittedEventListener {
    private final ProjectRuleOutboxDeliveryJob delivery;
    private final org.springframework.core.task.TaskExecutor executor;

    public ProjectRuleCommittedEventListener(ProjectRuleOutboxDeliveryJob delivery,
            @org.springframework.beans.factory.annotation.Qualifier("projectRuleEventExecutor")
            org.springframework.core.task.TaskExecutor executor) {
        this.delivery = delivery;
        this.executor = executor;
    }

    // Dispatch after commit on another thread, so producer connections/locks are released independently.
    // https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
    @TransactionalEventListener
    public void onCommitted(PlatformOutboxAppended event) {
        var message = event.message();
        if (!Set.of(ProjectRuleReevaluation.EVENT_TYPE, ProjectChildWaitEvents.EVENT_TYPE).contains(message.eventType())) return;
        var now = LocalDateTime.now();
        if (event.notBefore() != null && event.notBefore().isAfter(now)) return;
        try {
            executor.execute(() -> {
                try {
                    TenantUtils.execute(message.tenantId(), () -> delivery.deliver(message, LocalDateTime.now()));
                } catch (RuntimeException unavailable) {
                    log.warn("Rule event delivery deferred: tenant={}, event={}", message.tenantId(), message.eventId());
                }
            });
        } catch (RuntimeException unavailable) {
            // The producer already committed. Never report its business write as failed or expose Owner payloads.
            // Outbox remains durable; command locks and delivery CAS protect concurrent recovery/replay.
            log.warn("Rule event delivery deferred: tenant={}, event={}", message.tenantId(), message.eventId());
        }
    }
}
