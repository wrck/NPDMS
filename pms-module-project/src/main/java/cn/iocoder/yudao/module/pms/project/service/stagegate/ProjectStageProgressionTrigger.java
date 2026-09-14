package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 业务事务提交后独立重判，不改变原命令结果，也不以登录外的身份自动授权。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectStageProgressionTrigger {
    private final ProjectStageAdmissionService admission;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    public void afterChange(Long projectId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (projectId == null || tenantId == null || actorId == null) return;
        Runnable evaluate = () -> {
            var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                transaction.executeWithoutResult(status -> admission.activateEligible(projectId, actorId, "PROJECT_CHANGE:" + projectId));
            } catch (RuntimeException failure) {
                log.warn("Automatic stage transaction failed: projectId={}", projectId, failure);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evaluate.run();
                }
            });
        } else {
            evaluate.run();
        }
    }

}
