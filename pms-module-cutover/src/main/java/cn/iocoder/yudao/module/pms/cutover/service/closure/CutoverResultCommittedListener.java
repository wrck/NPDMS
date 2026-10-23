package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.cutover.api.result.CutoverCompleted;
import cn.iocoder.yudao.module.pms.cutover.job.CutoverResultDeliveryJob;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component @Slf4j
public class CutoverResultCommittedListener {
    private final CutoverResultDeliveryJob delivery;
    private final org.springframework.core.task.TaskExecutor executor;
    public CutoverResultCommittedListener(CutoverResultDeliveryJob delivery,
            @org.springframework.beans.factory.annotation.Qualifier("cutoverEventExecutor") org.springframework.core.task.TaskExecutor executor) {
        this.delivery = delivery; this.executor = executor;
    }
    @TransactionalEventListener
    public void committed(PlatformOutboxAppended event) {
        if (!CutoverCompleted.EVENT_TYPE.equals(event.message().eventType())) return;
        try {
            executor.execute(() -> {
                try { TenantUtils.execute(event.message().tenantId(), () -> delivery.deliver(event.message())); }
                catch (RuntimeException deferred) { log.warn("Cutover result remains pending: {}", event.message().eventId()); }
            });
        } catch (RuntimeException deferred) { log.warn("Cutover result remains pending: {}", event.message().eventId()); }
    }
}
