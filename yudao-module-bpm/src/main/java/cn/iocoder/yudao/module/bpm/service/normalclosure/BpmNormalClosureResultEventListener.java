package cn.iocoder.yudao.module.bpm.service.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureResultEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Bridges the existing engine result event without a new transaction or after-commit business write. */
@Component
@RequiredArgsConstructor
public class BpmNormalClosureResultEventListener extends BpmProcessInstanceStatusEventListener {
    private final ApplicationEventPublisher publisher;
    private final BpmNormalClosureApi api;

    @Override
    protected String getProcessDefinitionKey() {
        return BpmNormalClosureApi.PROCESS_DEFINITION_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(event.getStatus())) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("NORMAL result must join the BPM approval transaction");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String instanceId = event.getId();
        // The original event is inside the Flowable command. BeforeCommit runs after that command has flushed
        // task/process history, but still uses the exact same DB transaction and propagates consumer failures.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                FlowableUtils.execute(tenantId.toString(), () -> {
                    BpmNormalClosureApi.Result result = api.inspectResult(tenantId, instanceId);
                    if (!"APPROVE".equals(result.status()) && !"REJECT".equals(result.status())
                            && !"CANCEL".equals(result.status())) {
                        throw new IllegalStateException("NORMAL result history is not terminal in the approval transaction");
                    }
                    publisher.publishEvent(new BpmNormalClosureResultEvent(tenantId, instanceId));
                });
            }
        });
    }
}
