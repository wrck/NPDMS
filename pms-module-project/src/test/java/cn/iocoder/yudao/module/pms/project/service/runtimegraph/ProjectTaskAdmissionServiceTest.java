package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTaskAdmissionServiceTest {
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectTaskExecutionContractMapper contracts = mock(ProjectTaskExecutionContractMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectStageAdmissionService rules = mock(ProjectStageAdmissionService.class);
    final OperationAuditApi audit = mock(OperationAuditApi.class);
    final ProjectRuleTimerScheduler timers = mock(ProjectRuleTimerScheduler.class);
    final cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService lifecycle =
            mock(cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService.class);
    final ProjectTaskAdmissionService service = new ProjectTaskAdmissionService(projects, contracts, executions, rules, audit);
    final ProjectMasterDO project = new ProjectMasterDO();
    final ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
    final ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
    final ProjectNodeExecutionDO round = new ProjectNodeExecutionDO();

    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "timers", timers);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "lifecycle", lifecycle);
        TenantContextHolder.setTenantId(7L);
        project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(51L);
        task.setId(11L); task.setTenantId(7L); task.setProjectId(9L); task.setStatus("PENDING_ASSIGN");
        contract.setId(21L); contract.setTenantId(7L); contract.setProjectTaskId(11L); contract.setSourceNodeKey("survey");
        round.setId(61L); round.setNodeInstanceId(11L); round.setNodeKind("TASK"); round.setNodeKey("survey");
        round.setStatus("PENDING"); round.setPlanVersionId(51L); round.setContractId(21L);
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(projects.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void laterOrdinaryReevaluationAdmitsAndStartsOnceWithoutSubmittingTask() {
        when(lifecycle.startAdmittedTask(eq(project), eq(task), eq(contract), anyString())).thenAnswer(call -> {
            task.setStatus("IN_PROGRESS"); return true;
        });
        when(rules.taskAdmissionFact(project, task, contract)).thenReturn(RuleFact.known(false), RuleFact.known(true));
        when(executions.activateIfPending(any())).thenAnswer(call -> {
            var write = call.getArgument(0, ProjectNodeExecutionMapper.Activation.class);
            assertEquals(51L, write.planVersionId()); assertEquals(11L, write.nodeInstanceId()); assertEquals("TASK", write.nodeKind());
            round.setStatus("ACTIVE"); round.setAdmittedAt(write.occurredAt()); return 1;
        });
        assertEquals(new ProjectTaskAdmissionService.Result(false, false), service.activateEligible(9L, 11L, "time-arrived-stage-pending"));
        assertTrue(service.activateEligible(9L, 11L, "stage-now-active").activated());
        assertFalse(service.activateEligible(9L, 11L, "duplicate-event").activated());
        verify(executions).activateIfPending(any());
        verify(timers).scheduleFromNode(9L, "TASK", 11L);
        verify(audit).record(eq(7L), eq(0L), eq("stage-now-active"), eq("PROJECT_TASK_ADMITTED"), eq("ProjectTask"), eq("11"), eq("SUCCESS"), anyMap());
        assertEquals("IN_PROGRESS", task.getStatus());
        verify(lifecycle).startAdmittedTask(project, task, contract, "stage-now-active");
        assertNull(round.getStartedAt()); assertNull(round.getSubmittedAt());
        verify(executions, never()).recordTaskTransition(any());
    }

    @Test void unknownAndUnmatchedFactsNeverWriteAdmission() {
        when(rules.taskAdmissionFact(project, task, contract)).thenReturn(RuleFact.unknown("OWNER_UNAVAILABLE"), RuleFact.known(false));
        assertTrue(service.activateEligible(9L, 11L, "unknown").unknown());
        assertEquals(new ProjectTaskAdmissionService.Result(false, false), service.activateEligible(9L, 11L, "false"));
        verify(executions, never()).activateIfPending(any()); verifyNoInteractions(audit, timers);
    }

    @Test void previouslyAdmittedTaskCanStartWithoutReadmittingItsRound() {
        round.setStatus("ACTIVE");
        when(rules.taskAdmissionFact(project, task, contract)).thenReturn(RuleFact.known(true));
        when(lifecycle.startAdmittedTask(project, task, contract, "resume")).thenReturn(true);
        assertTrue(service.activateEligible(9L, 11L, "resume").activated());
        verify(executions, never()).activateIfPending(any());
        verify(lifecycle).startAdmittedTask(project, task, contract, "resume");
    }

    @Test void stalePlanContractAndForeignTaskCannotBeAdmitted() {
        round.setPlanVersionId(50L); assertTrue(service.activateEligible(9L, 11L, "stale-plan").unknown());
        round.setPlanVersionId(51L); contract.setId(22L); assertTrue(service.activateEligible(9L, 11L, "stale-contract").unknown());
        contract.setId(21L); contract.setSourceNodeKey("other"); assertTrue(service.activateEligible(9L, 11L, "wrong-node").unknown());
        contract.setSourceNodeKey("survey"); task.setTenantId(8L); assertTrue(service.activateEligible(9L, 11L, "foreign-tenant").unknown());
        task.setTenantId(7L); task.setProjectId(10L); assertTrue(service.activateEligible(9L, 11L, "foreign-project").unknown());
        verifyNoInteractions(rules, audit); verify(executions, never()).activateIfPending(any());
    }

    @Test void finishedHistoryAndClosedProjectRemainUntouched() {
        round.setPlanVersionId(40L);
        for (String status : List.of("DONE", "TERMINATED")) {
            round.setStatus(status); assertEquals(new ProjectTaskAdmissionService.Result(false, false), service.activateEligible(9L, 11L, "history"));
        }
        round.setStatus("PENDING"); project.setLifecycleStatus("CLOSED");
        assertEquals(new ProjectTaskAdmissionService.Result(false, false), service.activateEligible(9L, 11L, "closed"));
        verifyNoInteractions(rules, audit); verify(executions, never()).activateIfPending(any());
    }

    @Test void persistenceConflictEscapesToTransactionBoundaryAndCannotWriteSuccessAudit() {
        when(rules.taskAdmissionFact(project, task, contract)).thenReturn(RuleFact.known(true));
        when(executions.activateIfPending(any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.activateEligible(9L, 11L, "conflict"));
        verifyNoInteractions(audit);
    }

    @Test void auditFailureAndOuterTimerFailureRollBackAdmissionWithRealSpringTransactions() {
        // Isolated transaction test: in-memory H2 ledger, no application profile or external database.
        // The production mapper SQL and MySQL concurrency are not simulated as proven here.
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE admission_write (task_id BIGINT PRIMARY KEY)");
            // Match the platform audit's NOT NULL actor contract, not a permissive no-op mock.
            jdbc.execute("CREATE TABLE admission_audit (actor_id BIGINT NOT NULL)");
            doAnswer(call -> jdbc.update("INSERT INTO admission_audit VALUES (?)", call.getArgument(1, Long.class)))
                    .when(audit).record(eq(7L), nullable(Long.class), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
            when(rules.taskAdmissionFact(project, task, contract)).thenReturn(RuleFact.known(true));
            when(executions.activateIfPending(any())).thenAnswer(call -> jdbc.update("INSERT INTO admission_write VALUES (?)", 11L));
            var manager = new DataSourceTransactionManager(database);
            var factory = new ProxyFactory(service);
            factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            var command = (ProjectTaskAdmissionService) factory.getProxy();
            doThrow(new IllegalStateException("audit unavailable")).when(audit).record(eq(7L), eq(0L), eq("failed-audit"),
                    anyString(), anyString(), anyString(), anyString(), anyMap());
            assertThrows(IllegalStateException.class, () -> command.activateEligible(9L, 11L, "failed-audit"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admission_write", Integer.class));
            var outerTimer = new TransactionTemplate(manager);
            doThrow(new IllegalStateException("relative timer registration failed")).when(timers).scheduleFromNode(9L, "TASK", 11L);
            assertThrows(IllegalStateException.class, () -> command.activateEligible(9L, 11L, "failed-relative-timer"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admission_write", Integer.class));
            doNothing().when(timers).scheduleFromNode(9L, "TASK", 11L);
            assertThrows(IllegalStateException.class, () -> outerTimer.executeWithoutResult(status -> {
                assertTrue(command.activateEligible(9L, 11L, "timer").activated());
                throw new IllegalStateException("timer outbox append failed");
            }));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admission_write", Integer.class));
            assertTrue(command.activateEligible(9L, 11L, "ordinary").activated());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM admission_write", Integer.class));
            assertEquals(List.of(0L), jdbc.queryForList("SELECT actor_id FROM admission_audit", Long.class));
        } finally { database.shutdown(); }
    }
}
