package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStageProgressionTriggerTest {
    final ProjectStageAdmissionService admission = mock(ProjectStageAdmissionService.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final ProjectStageProgressionTrigger trigger = new ProjectStageProgressionTrigger(admission, transactions);
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
    @Test void evaluatesStageAdmissionWithoutSelectingOneSuccessor() {
        trigger.afterChange(10L);
        verify(admission).activateEligible(10L, 9L, "PROJECT_CHANGE:10");
        verify(transactions).commit(any());
    }
    @Test void rollbackDoesNotRunEvaluation() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        trigger.afterChange(10L);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));
        verifyNoInteractions(admission, transactions);
    }
    @Test void combinedOperationWaitsForCommit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        trigger.afterChange(10L); verifyNoInteractions(admission);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
        verify(admission).activateEligible(10L, 9L, "PROJECT_CHANGE:10");
    }
    @Test void persistenceFailureRollsBackButDoesNotMisreportTheSourceWrite() {
        when(admission.activateEligible(anyLong(), anyLong(), anyString())).thenThrow(new IllegalStateException("STATE_CONFLICT"));
        assertDoesNotThrow(() -> trigger.afterChange(10L));
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }
    @Test void missingLoginNeverImpersonatesEventActor() {
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
        trigger.afterChange(10L); verifyNoInteractions(admission, transactions);
    }
}
