package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStageProgressionTriggerTest {
    final ProjectStageReadinessService readiness = mock(ProjectStageReadinessService.class);
    final ProjectStageAdvanceApplicationService advance = mock(ProjectStageAdvanceApplicationService.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final ProjectStageProgressionTrigger trigger = new ProjectStageProgressionTrigger(readiness, advance, transactions);
    MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }
    @AfterEach void cleanup() {
        security.close(); TenantContextHolder.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    ProjectStageReadinessResult state(String current, String next, boolean allowed, int version) {
        return new ProjectStageReadinessResult(10L, version, 1L, current, next, allowed, null, List.of());
    }
    @Test void advancesOnlyFromReevaluatedFactsAndStopsAtTerminal() {
        when(readiness.evaluate(10L,9L)).thenReturn(state("S0","S1",true,2),state("S1",null,false,3));
        trigger.afterChange(10L);
        verify(advance).advance(argThat(c -> c.expectedProjectVersion()==2 && c.expectedCurrentStage().equals("S0")
                && c.idempotencyKey().equals("AUTO_STAGE:10:2:S0")),argThat(a -> a.actorUserId()==9L && a.tenantId()==1L));
        verify(transactions).commit(any());
    }
    @Test void missingConditionsNeverAdvance() {
        when(readiness.evaluate(10L,9L)).thenReturn(state("S0","S1",false,1));
        trigger.afterChange(10L); verifyNoInteractions(advance);
    }
    @Test void rollbackDoesNotRunEvaluation() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        trigger.afterChange(10L);
        verifyNoInteractions(readiness);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));
        verifyNoInteractions(readiness, advance);
    }
    @Test void combinedOperationWaitsForCommit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        when(readiness.evaluate(10L,9L)).thenReturn(state("S0","S1",false,1));
        trigger.afterChange(10L); verifyNoInteractions(readiness);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        verify(readiness).evaluate(10L,9L);
    }
    @Test void evaluationFailureDoesNotMisreportSourceWriteFailure() {
        when(readiness.evaluate(10L,9L)).thenThrow(new IllegalStateException("Owner unavailable"));
        assertDoesNotThrow(() -> trigger.afterChange(10L)); verifyNoInteractions(advance);
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }
    @Test void advancementFailureRollsBackBeforeReturningSavedBusinessSuccess() {
        when(readiness.evaluate(10L, 9L)).thenReturn(state("S0", "S4", true, 2));
        when(advance.advance(any(), any())).thenThrow(new IllegalStateException("snapshot write failed"));
        assertDoesNotThrow(() -> trigger.afterChange(10L));
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }
    @Test void unavailableNextEvaluationRollsBackTheAutomaticChain() {
        when(readiness.evaluate(10L, 9L)).thenReturn(state("S0", "S4", true, 2))
                .thenThrow(new IllegalStateException("Owner unavailable"));
        assertDoesNotThrow(() -> trigger.afterChange(10L));
        verify(advance).advance(any(), any());
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }
    @Test void missingLoginNeverImpersonatesEventActor() {
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
        trigger.afterChange(10L); verifyNoInteractions(readiness, advance, transactions);
    }
}
