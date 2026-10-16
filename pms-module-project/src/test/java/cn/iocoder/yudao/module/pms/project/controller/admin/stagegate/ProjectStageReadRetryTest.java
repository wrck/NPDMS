package cn.iocoder.yudao.module.pms.project.controller.admin.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo.ProjectStageAdvanceReqVO;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageAdvanceApplicationService;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateWorkbench;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateWorkbenchService;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageReadinessResult;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageReadinessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.mock.env.MockEnvironment;

import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectStageReadRetryTest {
    private final ProjectStageReadinessService readiness = mock(ProjectStageReadinessService.class);
    private final ProjectStageAdvanceApplicationService commands = mock(ProjectStageAdvanceApplicationService.class);
    private final ProjectStageGateWorkbenchService workbench = mock(ProjectStageGateWorkbenchService.class);
    private final ProjectStageAdvanceController controller = new ProjectStageAdvanceController(
            readiness, commands, workbench, new MockEnvironment());
    private final ProjectStageReadinessResult result = new ProjectStageReadinessResult(
            201L, 8L, 1L, "S3", "S4", false, "STAGE_COMPLETION_UNSATISFIED", List.of());

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    private DataAccessException deadlock() {
        var failure = new SQLExceptionSubclassTranslator().translate("read gate", "SELECT stage FOR UPDATE",
                new SQLTransactionRollbackException("Deadlock found; try restarting transaction", "40001", 1213));
        assertInstanceOf(CannotAcquireLockException.class, failure);
        return failure;
    }

    @Test void retriesTheCurrentSpringTranslationAfterTheReadTransactionRolledBack() {
        when(readiness.evaluate(eq(201L), any())).thenThrow(deadlock()).thenReturn(result);
        var response = controller.readiness(201L).getData();
        assertFalse(response.advanceAllowed());
        assertEquals(result.guidance(), response.guidance());
        verify(readiness, times(2)).evaluate(eq(201L), any());
        verifyNoInteractions(commands);
    }

    @Test void selectedStageWorkbenchHasTheSameBoundedReadRetry() {
        var expected = new ProjectStageGateWorkbench(201L, 8L, 21L, 31L, "S3", 41L, 1, null, List.of());
        when(workbench.inspect(eq(201L), eq("S3"), any())).thenThrow(deadlock()).thenReturn(expected);
        assertSame(expected, controller.gateWorkbench(201L, "S3").getData());
        verify(workbench, times(2)).inspect(eq(201L), eq("S3"), any());
        verifyNoInteractions(commands);
    }

    @Test void exhaustedDeadlockRetriesPropagateTheFailure() {
        var failure = deadlock();
        when(readiness.evaluate(eq(201L), any())).thenThrow(failure);
        assertSame(failure, assertThrows(CannotAcquireLockException.class, () -> controller.readiness(201L)));
        verify(readiness, times(3)).evaluate(eq(201L), any());
    }

    @Test void lockTimeoutIsNotMisclassifiedAsAnAlreadyRolledBackDeadlock() {
        var failure = new CannotAcquireLockException("Lock wait timeout", new SQLException("timeout", "HY000", 1205));
        when(readiness.evaluate(eq(201L), any())).thenThrow(failure);
        assertSame(failure, assertThrows(CannotAcquireLockException.class, () -> controller.readiness(201L)));
        verify(readiness).evaluate(eq(201L), any());
    }

    @Test void stageAdvanceWriteIsNeverAutomaticallyRetried() {
        var failure = deadlock();
        when(commands.advance(any(), any())).thenThrow(failure);
        var request = new ProjectStageAdvanceReqVO();
        request.setExpectedCurrentStage("S3");
        request.setExpectedTreeVersion(1L);
        assertSame(failure, assertThrows(CannotAcquireLockException.class,
                () -> controller.advance(201L, "8", "native-stage-retry-test", request)));
        verify(commands).advance(any(), any());
        verifyNoInteractions(readiness, workbench);
    }
}
