package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleOutboxDeliveryJob;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.LocalDateTime;

@Component
public class ProjectOperationCommittedResultListener {
    private final ObjectProvider<ProjectRuleOutboxDeliveryJob> delivery;
    private final TaskExecutor executor;
    public ProjectOperationCommittedResultListener(ObjectProvider<ProjectRuleOutboxDeliveryJob> delivery,
            @Qualifier("projectRuleEventExecutor") TaskExecutor executor) { this.delivery=delivery; this.executor=executor; }
    @TransactionalEventListener
    public void committed(PlatformOutboxAppended event) {
        if (!ProjectOperationResultDelivery.eventTypes().contains(event.message().eventType())) return;
        if (event.notBefore() != null && event.notBefore().isAfter(LocalDateTime.now())) return;
        try { executor.execute(() -> {
            try { TenantUtils.execute(event.message().tenantId(),() -> delivery.getObject().deliver(event.message(),LocalDateTime.now())); }
            catch (RuntimeException unavailable) { /* Persisted delivery remains pending for recovery. */ }
        }); } catch (RuntimeException rejected) { /* No producer rollback or false business failure after commit. */ }
    }
}
