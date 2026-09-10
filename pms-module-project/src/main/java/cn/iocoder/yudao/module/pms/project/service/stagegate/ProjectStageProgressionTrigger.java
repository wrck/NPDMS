package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.stagegate.command.ProjectStageAdvanceCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Set;

/** 业务事务提交后独立重判，不改变原命令结果，也不以登录外的身份自动授权。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectStageProgressionTrigger {
    private final ProjectStageReadinessService readiness;
    private final ProjectStageAdvanceApplicationService advancement;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    public void afterChange(Long projectId) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (projectId == null || tenantId == null || actorId == null) return;
        Runnable evaluate = () -> {
            var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                transaction.executeWithoutResult(status -> progress(projectId, tenantId, actorId));
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

    private void progress(Long projectId, Long tenantId, Long actorId) {
        // 异常必须先退出TransactionTemplate完成回滚，再由外层隔离来源业务结果。
        Set<String> visited = new HashSet<>();
        while (true) {
            var state = readiness.evaluate(projectId, actorId);
            if (!state.advanceAllowed() || state.nextStage() == null) return;
            if (!visited.add(state.currentStage())) {
                throw new IllegalStateException("Frozen stage graph revisits a stage");
            }
            String intent = projectId + ":" + state.projectVersion() + ":" + state.currentStage();
            advancement.advance(new ProjectStageAdvanceCommand(projectId, state.projectVersion(),
                            state.currentStage(), state.treeVersion(), "AUTO_STAGE:" + intent,
                            DigestUtil.sha256Hex(intent)),
                    new ProjectStageAdvanceApplicationService.Actor(tenantId, actorId, "AUTO_STAGE:" + intent));
        }
    }
}
