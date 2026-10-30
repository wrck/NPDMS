package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.DURATION_CHANGE_BPM_ASSOCIATION_INVALID;

/** 同步消费项目工期审批流程终态。 */
@Component
@RequiredArgsConstructor
public class DurationChangeBpmListener extends BpmProcessInstanceStatusEventListener {

    private final DurationChangeProperties properties;
    private final DurationChangeBpmResultService resultService;
    private final Environment environment;

    @Override
    protected String getProcessDefinitionKey() {
        return properties.getProcessDefinitionKey();
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null && environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(DURATION_CHANGE_BPM_ASSOCIATION_INVALID);
        }
        Long resultTenantId = tenantId == null ? 0L : tenantId;
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(event.getStatus())) {
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                    || !TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("工期审批结果必须与 BPM 原事务一致");
            }
            // Flowable completes/flushed task history after the process-ended event. Read the
            // actual task opinion before the same transaction commits, so failures roll back both sides.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    TenantUtils.execute(resultTenantId, () -> resultService.handle(event));
                }
            });
        } else {
            TenantUtils.execute(resultTenantId, () -> resultService.handle(event));
        }
    }

}
