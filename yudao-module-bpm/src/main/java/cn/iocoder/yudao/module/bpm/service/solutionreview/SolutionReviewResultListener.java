package cn.iocoder.yudao.module.bpm.service.solutionreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.*;
import cn.iocoder.yudao.module.bpm.api.solutionreview.*;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component @RequiredArgsConstructor
public class SolutionReviewResultListener extends BpmProcessInstanceStatusEventListener {
    private final ApplicationEventPublisher publisher;
    @Override protected String getProcessDefinitionKey() { return SolutionReviewBpmApi.DEFINITION_KEY; }
    @Override protected void onEvent(BpmProcessInstanceStatusEvent event) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(event.getStatus())) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("方案审批结果必须与 BPM 原事务一致");
        Long tenant = TenantContextHolder.getRequiredTenantId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                FlowableUtils.execute(tenant.toString(), () -> publisher.publishEvent(
                        new SolutionReviewResultEvent(tenant, event.getId())));
            }
        });
    }
}
