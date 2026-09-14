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
    static String frozenDefinitionId;

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
        frozenDefinitionId = engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey("gate-test").processDefinitionTenantId("7").singleResult().getId();
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

    @Test void taskRoundApprovalStartAndCompletionWakeTheSameProjectReevaluationConsumer() {
        String id = startTaskApproval();
        assertEquals(1,count());
        tx.executeWithoutResult(ignored -> {
            engine.getRuntimeService().setVariable(id,"PROCESS_STATUS",2);
            engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult().getId());
        });
        assertEquals(2,count());
        var payloads = jdbc.queryForList("SELECT payload FROM rule_events",String.class);
        assertTrue(payloads.stream().allMatch(payload -> payload.contains("bpm:" + id)));
        assertTrue(payloads.stream().noneMatch(payload -> payload.contains("private-form-value") || payload.contains("PROCESS_STATUS")));
    }
    @Test void taskApprovalOutboxFailureRollsBackEngineStart() {
        long before = engine.getRuntimeService().createProcessInstanceQuery().count();
        failAppend.set(true);
        assertThrows(RuntimeException.class,this::startTaskApproval);
        assertEquals(before,engine.getRuntimeService().createProcessInstanceQuery().count());
        assertEquals(0,count());
    }
    private String startTaskApproval() {
        var definition = engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("gate-test").singleResult();
        var variables = new java.util.HashMap<String,Object>();
        variables.put(cn.iocoder.yudao.module.pms.project.api.approval.ProjectTaskApprovalApi.VAR_TENANT,7L);
        variables.put(cn.iocoder.yudao.module.pms.project.api.approval.ProjectTaskApprovalApi.VAR_PROJECT,9L);
        variables.put(cn.iocoder.yudao.module.pms.project.api.approval.ProjectTaskApprovalApi.VAR_TASK,21L);
        variables.put(cn.iocoder.yudao.module.pms.project.api.approval.ProjectTaskApprovalApi.VAR_EXECUTION,referenceId);
        variables.put(cn.iocoder.yudao.module.pms.project.api.approval.ProjectTaskApprovalApi.VAR_CONTRACT,91L);
        variables.put(cn.iocoder.yudao.module.pms.project.api.approval.ProjectTaskApprovalApi.VAR_DEFINITION,definition.getId());
        variables.put(cn.iocoder.yudao.module.pms.project.api.approval.ProjectTaskApprovalApi.VAR_ACTOR,1L);
        variables.put("PROCESS_STATUS",1); variables.put("formText","private-form-value");
        return tx.execute(ignored -> engine.getRuntimeService().createProcessInstanceBuilder().tenantId("7")
                .processDefinitionId(definition.getId()).businessKey(
                        cn.iocoder.yudao.module.pms.project.api.approval.ProjectTaskApprovalApi.BUSINESS_KEY_PREFIX + referenceId)
                .variables(variables).start().getId());
    }

    @Test void exactDefinitionInspectionDoesNotDriftAfterRedeploymentOrCrossTenantBoundaries() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="pms-test">
                  <process id="pin-test" isExecutable="true">
                    <startEvent id="start"/><userTask id="review"/><endEvent id="end"/>
                    <sequenceFlow id="a" sourceRef="start" targetRef="review"/>
                    <sequenceFlow id="b" sourceRef="review" targetRef="end"/>
                  </process>
                </definitions>
                """;
        var repository = engine.getRepositoryService();
        repository.createDeployment().tenantId("7").addString("pin.bpmn20.xml", bpmn).deploy();
        var first = provider.inspectDefinitionKey(new ProjectStageGateProcessDefinitionQuery(7L, "pin-test", null));
        repository.createDeployment().tenantId("7").addString("pin.bpmn20.xml", bpmn).deploy();
        var latest = provider.inspectDefinitionKey(new ProjectStageGateProcessDefinitionQuery(7L, "pin-test", null));
        assertNotEquals(first.processDefinitionId(), latest.processDefinitionId());
        var pinned = new ProjectStageGateProcessDefinitionQuery(7L, "pin-test", first.processDefinitionId());
        assertEquals(first, provider.inspectDefinitionKey(pinned));
        assertThrows(IllegalArgumentException.class, () -> provider.inspectDefinitionKey(
                new ProjectStageGateProcessDefinitionQuery(7L, "gate-test", first.processDefinitionId())));
        assertThrows(IllegalArgumentException.class, () -> provider.inspectDefinitionKey(
                new ProjectStageGateProcessDefinitionQuery(7L, "pin-test", "missing")));
        TenantContextHolder.setTenantId(8L);
        try {
            assertThrows(IllegalArgumentException.class, () -> provider.inspectDefinitionKey(
                    new ProjectStageGateProcessDefinitionQuery(8L, "pin-test", first.processDefinitionId())));
        } finally { TenantContextHolder.setTenantId(7L); }
        assertEquals(0, count()); // Definition validation must not start work or send reevaluation events.
        var command = new ProjectStageGateProcessStartCommand(7L, 11L, 9L, "PREP", 21L, referenceId, "APPROVAL",
                "pin-test", first.processDefinitionId(), FlowableProjectStageGateProvider.businessKey(referenceId),
                "frozen-start", "request", Map.of());
        var started = provider.startProcess(command);
        assertEquals(first.processDefinitionId(), started.processDefinitionId());
        assertEquals("REPLAYED", provider.startProcess(command).outcome());
        tx.executeWithoutResult(ignored -> {
            engine.getRuntimeService().setVariable(started.processInstanceId(), "PROCESS_STATUS", 2);
            engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(started.processInstanceId()).singleResult().getId());
        });
        var accepted = tx.execute(ignored -> provider.lockAndRevalidate(new ProjectStageGateFactQuery(
                7L, 9L, "PREP", 21L, "approval", 1, referenceId, 1, "APPROVAL", "pin-test", first.processDefinitionId(), java.time.Instant.EPOCH)));
        assertEquals(ProjectStageGateOutcome.SATISFIED, accepted.outcome());
        var differentPin = tx.execute(ignored -> provider.lockAndRevalidate(new ProjectStageGateFactQuery(
                7L, 9L, "PREP", 21L, "approval", 1, referenceId, 1, "APPROVAL", "pin-test", latest.processDefinitionId(), java.time.Instant.EPOCH)));
        assertEquals(ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE, differentPin.outcome());
        assertEquals("BPM_INSTANCE_IDENTITY_MISMATCH", differentPin.unmetCode());
        repository.suspendProcessDefinitionById(first.processDefinitionId());
        assertThrows(IllegalArgumentException.class, () -> provider.inspectDefinitionKey(pinned));
        assertEquals(latest, provider.inspectDefinitionKey(new ProjectStageGateProcessDefinitionQuery(7L, "pin-test", null)));
        assertThrows(IllegalArgumentException.class, () -> provider.startProcess(new ProjectStageGateProcessStartCommand(
                7L, 11L, 9L, "PREP", 21L, referenceId, "APPROVAL", "pin-test", first.processDefinitionId(),
                FlowableProjectStageGateProvider.businessKey(referenceId), "unavailable-start", "request", Map.of())));
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
        return command("op:" + referenceId);
    }

    private ProjectStageGateProcessStartCommand command(String operation) {
        return new ProjectStageGateProcessStartCommand(7L, 11L, 9L, "PREP", 21L, referenceId, "APPROVAL",
                "gate-test", frozenDefinitionId, FlowableProjectStageGateProvider.businessKey(referenceId), operation,
                "request:" + referenceId, Map.of());
    }
    private ProjectStageGateFact fact() {
        return fact(java.time.Instant.EPOCH);
    }

    private ProjectStageGateFact fact(java.time.Instant roundCreatedAt) {
        return tx.execute(ignored -> provider.lockAndRevalidate(new ProjectStageGateFactQuery(
                7L, 9L, "PREP", 21L, "approval", 1, referenceId, 1, "APPROVAL", "gate-test", frozenDefinitionId, roundCreatedAt)));
    }

    @Test void oldRoundCompletionCannotReleaseReworkButANewProcessCan() {
        var clock = engine.getProcessEngineConfiguration().getClock();
        var original = java.time.Instant.parse("2026-09-15T00:00:00Z");
        var rework = original.plusSeconds(60);
        try {
            clock.setCurrentTime(java.util.Date.from(original));
            var old = provider.startProcess(command());
            clock.setCurrentTime(java.util.Date.from(rework.plusSeconds(10)));
            // This old process finishes AFTER rework, but it was started in the prior round.
            completeApproved(old.processInstanceId());
            assertEquals(ProjectStageGateOutcome.SATISFIED, fact(original).outcome());
            assertEquals(ProjectStageGateOutcome.UNSATISFIED, fact(rework).outcome());
            var current = provider.startProcess(command("rework:" + referenceId));
            assertEquals(ProjectStageGateOutcome.UNSATISFIED, fact(rework).outcome());
            completeApproved(current.processInstanceId());
            var approved = fact(rework);
            assertEquals(ProjectStageGateOutcome.SATISFIED, approved.outcome());
            assertEquals(current.processInstanceId(), approved.ownerObjectKey());
            assertEquals(2, engine.getHistoryService().createHistoricProcessInstanceQuery()
                    .processInstanceBusinessKey(command().businessKey()).finished().count());
            assertEquals(4, count());
        } finally {
            clock.reset();
        }
    }

    private void completeApproved(String processId) {
        var task = engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult();
        tx.executeWithoutResult(ignored -> {
            engine.getRuntimeService().setVariable(processId, "PROCESS_STATUS", 2);
            engine.getTaskService().complete(task.getId());
        });
    }

    @Test void runningWorkInspectionIsProjectScopedAndTracksCompletionAndCancellation() {
        var started = provider.startProcess(command());
        var query = new ProjectStageGateRunningProcessQuery(7L, 9L);
        var running = tx.execute(ignored -> provider.inspectRunning(query));
        assertTrue(running.stream().anyMatch(process -> process.processInstanceId().equals(started.processInstanceId())
                && process.gateReferenceId().equals(referenceId) && process.stageCode().equals("PREP")));
        assertTrue(tx.execute(ignored -> provider.inspectRunning(new ProjectStageGateRunningProcessQuery(7L, 999L))).isEmpty());
        assertThrows(RuntimeException.class, () -> tx.execute(ignored -> provider.inspectRunning(new ProjectStageGateRunningProcessQuery(8L, 9L))));
        completeApproved(started.processInstanceId());
        assertFalse(tx.execute(ignored -> provider.inspectRunning(query)).stream()
                .anyMatch(process -> process.processInstanceId().equals(started.processInstanceId())));
        var cancelled = provider.startProcess(command("cancel:" + referenceId));
        tx.executeWithoutResult(ignored -> engine.getRuntimeService().deleteProcessInstance(cancelled.processInstanceId(), "cancelled"));
        assertFalse(tx.execute(ignored -> provider.inspectRunning(query)).stream()
                .anyMatch(process -> process.processInstanceId().equals(cancelled.processInstanceId())));
    }

    @Test void unfinishedOldProcessDoesNotMakeCurrentRoundAmbiguous() {
        var clock = engine.getProcessEngineConfiguration().getClock();
        var original = java.time.Instant.parse("2026-09-15T00:00:00Z");
        var rework = original.plusSeconds(60);
        try {
            clock.setCurrentTime(java.util.Date.from(original));
            var old = provider.startProcess(command());
            clock.setCurrentTime(java.util.Date.from(rework));
            var current = provider.startProcess(command("rework:" + referenceId));
            assertEquals(ProjectStageGateOutcome.UNSATISFIED, fact(rework).outcome());
            completeApproved(current.processInstanceId());
            assertEquals(ProjectStageGateOutcome.SATISFIED, fact(rework).outcome());
            assertNotNull(engine.getRuntimeService().createProcessInstanceQuery()
                    .processInstanceId(old.processInstanceId()).singleResult());
        } finally {
            clock.reset();
        }
    }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM rule_events", Integer.class); }
}
