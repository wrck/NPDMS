package cn.iocoder.yudao.module.pms.integration.stagegate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.*;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Flowable 8/Spring transactions in unique H2 memory; no application configuration or shared database. */
class FlowableProjectRuleEventListenerTest {
    static EmbeddedDatabase database;
    static ProcessEngine engine;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static FlowableProjectStageGateProvider provider;
    static final AtomicBoolean failAppend = new AtomicBoolean();
    static final AtomicLong ids = new AtomicLong(100);
    Long referenceId;

    @BeforeAll static void startEngine() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE rule_events (event_id VARCHAR(64) PRIMARY KEY, tenant_id BIGINT, project_id VARCHAR(32), payload VARCHAR(4000))");
        var outbox = mock(PlatformBusinessEventApi.class);
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            BusinessEvent event = call.getArgument(2);
            assertEquals(ProjectRuleReevaluationRequested.EVENT_TYPE, event.eventType());
            jdbc.update("INSERT INTO rule_events VALUES (?,?,?,?)", event.eventId(),
                    TenantContextHolder.getRequiredTenantId(), call.getArgument(1), event.eventPayload());
            if (failAppend.get()) throw new IllegalStateException("outbox unavailable");
            return null;
        }).when(outbox).append(eq("Project"), anyString(), any());
        var runtime = mock(RuntimeService.class);
        var listener = new FlowableProjectRuleEventListener(runtime, outbox, true);
        var manager = new DataSourceTransactionManager(database);
        tx = new TransactionTemplate(manager);
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(database);
        config.setTransactionManager(manager);
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);
        config.setEventListeners(List.of(listener));
        engine = config.buildProcessEngine();
        when(runtime.createProcessInstanceQuery()).thenAnswer(call -> engine.getRuntimeService().createProcessInstanceQuery());
        provider = new FlowableProjectStageGateProvider(engine.getRepositoryService(), engine.getRuntimeService(), engine.getHistoryService(), true);
        engine.getRepositoryService().createDeployment().tenantId("7").addString("gate.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="pms-test">
                  <process id="gate-test" isExecutable="true">
                    <startEvent id="start"/><userTask id="approve"/><endEvent id="end"/>
                    <sequenceFlow id="a" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="b" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """).deploy();
    }

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        referenceId = ids.incrementAndGet();
        failAppend.set(false);
        jdbc.update("DELETE FROM rule_events"); // Only this class's unique disposable H2 ledger.
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @AfterAll static void stopEngine() {
        try { if (engine != null) engine.close(); }
        finally { if (database != null) database.shutdown(); }
    }

    @ParameterizedTest @ValueSource(ints = {2, 3})
    void committedApprovalAndRejectionBothWakeRulesButKeepDifferentOwnerResults(int status) {
        var started = provider.startProcess(command());
        assertEquals(1, count());
        assertEquals(ProjectStageGateOutcome.UNSATISFIED, fact().outcome());
        var task = engine.getTaskService().createTaskQuery().processInstanceId(started.processInstanceId()).singleResult();
        // Emulate the original BPM result variable; this test does not replace or claim to test its user authorization.
        tx.executeWithoutResult(ignored -> {
            engine.getRuntimeService().setVariable(started.processInstanceId(), "PROCESS_STATUS", status);
            engine.getTaskService().complete(task.getId());
        });
        assertEquals(2, count());
        assertEquals(status == 2 ? ProjectStageGateOutcome.SATISFIED : ProjectStageGateOutcome.UNSATISFIED, fact().outcome());
        assertEquals("REPLAYED", provider.startProcess(command()).outcome());
        assertEquals(2, count());
        for (var payload : jdbc.queryForList("SELECT payload FROM rule_events", String.class)) {
            var event = JsonUtils.parseObject(payload, ProjectRuleReevaluationRequested.class);
            assertEquals(7L, event.tenantId()); assertEquals(9L, event.projectId()); assertEquals(11L, event.actorId());
            assertFalse(payload.contains("PROCESS_STATUS"));
        }
    }

    @Test void cancellationCommitsItsWakeupWithoutTreatingItAsApproval() {
        var started = provider.startProcess(command());
        tx.executeWithoutResult(ignored -> {
            engine.getRuntimeService().setVariable(started.processInstanceId(), "PROCESS_STATUS", 4);
            engine.getRuntimeService().deleteProcessInstance(started.processInstanceId(), "cancelled");
        });
        assertEquals(2, count());
        assertEquals(ProjectStageGateOutcome.UNSATISFIED, fact().outcome());
    }

    @Test void appendFailureRollsBackCompletionAndItsEventThenRetryCanFinish() {
        var started = provider.startProcess(command());
        var task = engine.getTaskService().createTaskQuery().processInstanceId(started.processInstanceId()).singleResult();
        failAppend.set(true);
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(ignored -> {
            engine.getRuntimeService().setVariable(started.processInstanceId(), "PROCESS_STATUS", 2);
            engine.getTaskService().complete(task.getId());
        }));
        assertEquals(1, count());
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
        assertEquals(1, engine.getRuntimeService().getVariable(started.processInstanceId(), "PROCESS_STATUS"));
        failAppend.set(false);
        tx.executeWithoutResult(ignored -> {
            engine.getRuntimeService().setVariable(started.processInstanceId(), "PROCESS_STATUS", 2);
            engine.getTaskService().complete(task.getId());
        });
        assertEquals(2, count());
        assertEquals(ProjectStageGateOutcome.SATISFIED, fact().outcome());
    }

    @Test void appendFailureRollsBackInitialProcessCreation() {
        failAppend.set(true);
        assertThrows(RuntimeException.class, () -> provider.startProcess(command()));
        assertEquals(0, count());
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey(command().businessKey()).count());
        assertEquals(0, engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceBusinessKey(command().businessKey()).count());
    }

    @Test void unrelatedProcessesDoNotPublishProjectEvents() {
        var process = engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("gate-test", "unrelated", Map.of(), "7");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(process.getId()).singleResult();
        engine.getTaskService().complete(task.getId());
        assertEquals(0, count());
    }

    @Test void nativeCompletionWithoutRequestContextRestoresTenantAfterAppending() {
        var started = provider.startProcess(command());
        TenantContextHolder.clear();
        var task = engine.getTaskService().createTaskQuery().processInstanceId(started.processInstanceId()).singleResult();
        engine.getTaskService().complete(task.getId());
        assertNull(TenantContextHolder.getTenantId());
        assertEquals(2, count());
        assertEquals(List.of(7L, 7L), jdbc.queryForList("SELECT tenant_id FROM rule_events", Long.class));
    }

    private ProjectStageGateProcessStartCommand command() {
        return new ProjectStageGateProcessStartCommand(7L, 11L, 9L, "PREP", 21L, referenceId, "APPROVAL",
                "gate-test", null, FlowableProjectStageGateProvider.businessKey(referenceId), "op:" + referenceId,
                "request:" + referenceId, Map.of());
    }
    private ProjectStageGateFact fact() {
        return tx.execute(ignored -> provider.lockAndRevalidate(new ProjectStageGateFactQuery(
                7L, 9L, "PREP", 21L, "approval", 1, referenceId, 1, "APPROVAL", "gate-test")));
    }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM rule_events", Integer.class); }
}
