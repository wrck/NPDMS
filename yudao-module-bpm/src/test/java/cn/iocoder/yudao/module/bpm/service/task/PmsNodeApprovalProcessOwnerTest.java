package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real original BPM creation + Flowable 8 in a unique H2/Spring transaction; no shared environment. */
class PmsNodeApprovalProcessOwnerTest {
    static EmbeddedDatabase database;
    static ProcessEngine engine;
    static TransactionTemplate tx;
    static String pinned;
    static final AtomicLong ids = new AtomicLong(100);
    PmsNodeApprovalProcessOwner owner;
    BpmProcessInstanceServiceImpl processes;
    BpmProcessDefinitionService definitions;
    ProjectNodeExecutionApi executions;
    Scope scope;
    ProjectTaskExecutionContext context;

    @BeforeAll static void engine() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var manager = new DataSourceTransactionManager(database);
        tx = new TransactionTemplate(manager);
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(database); config.setTransactionManager(manager);
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE); config.setAsyncExecutorActivate(false);
        engine = config.buildProcessEngine();
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="pms-test">
                  <process id="task-approval" name="任务审批" isExecutable="true">
                    <startEvent id="start"/><userTask id="approve"/><endEvent id="end"/>
                    <sequenceFlow id="a" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="b" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
        var repository = engine.getRepositoryService();
        repository.createDeployment().tenantId("7").addString("task.bpmn20.xml", bpmn).deploy();
        pinned = repository.createProcessDefinitionQuery().processDefinitionKey("task-approval").singleResult().getId();
        repository.createDeployment().tenantId("7").addString("task.bpmn20.xml", bpmn).deploy();
    }
    @AfterAll static void stop() { try { engine.close(); } finally { database.shutdown(); } }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        scope = new Scope(NodeKind.TASK, 7L, 9L, 21L, ids.incrementAndGet(), 91L, "task-approval", pinned, LocalDateTime.now().minusSeconds(1));
        context = new ProjectTaskExecutionContext(9L, 1, 21L, 2, 91L, 1, 51L, scope.executionId(), 1, 1, 62L, 1, true, scope.startedAt());
        executions = mock(ProjectNodeExecutionApi.class);
        when(executions.lockAndRevalidate(context)).thenReturn(context);
        definitions = mock(BpmProcessDefinitionService.class);
        var info = new BpmProcessDefinitionInfoDO();
        when(definitions.getProcessDefinitionInfo(pinned)).thenReturn(info);
        when(definitions.canUserStartProcessDefinition(info, 1L)).thenReturn(true);
        processes = spy(new BpmProcessInstanceServiceImpl());
        ReflectionTestUtils.setField(processes, "runtimeService", engine.getRuntimeService());
        ReflectionTestUtils.setField(processes, "processDefinitionService", definitions);
        // This fixture excludes candidate prediction, but retains the original selected-approver validation below.
        doReturn(new BpmApprovalDetailRespVO().setActivityNodes(new ArrayList<>())).when(processes).getApprovalDetail(eq(1L), any());
        owner = new PmsNodeApprovalProcessOwner(new PmsApprovalProcessCreationService(processes, engine.getRepositoryService()),
                engine.getHistoryService(), executions);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    Fact start() { return start("first"); }
    Fact start(String operation) { return tx.execute(ignored -> owner.start(new Start(scope, context, 1L, operation, Map.of("formText", "private-value"), Map.of()))); }
    Fact fact() { return tx.execute(ignored -> owner.inspect(scope)); }
    void end(String id, int status) {
        tx.executeWithoutResult(ignored -> {
            engine.getRuntimeService().setVariable(id, "PROCESS_STATUS", status);
            var task = engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
            engine.getTaskService().complete(task.getId());
        });
    }

    @Test void startsPinnedDefinitionThroughOriginalBpmAndReplaysTheSameRound() {
        var first = start();
        assertEquals(Outcome.NOT_SATISFIED, first.outcome()); assertEquals("RUNNING", first.status());
        assertEquals(pinned, first.definitionId()); assertEquals(first, start());
        var process = engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(first.processInstanceId())
                .includeProcessVariables().singleResult();
        assertEquals(scope.businessKey(), process.getBusinessKey()); assertEquals("1", process.getStartUserId());
        assertEquals("任务审批", process.getName()); assertEquals("private-value", process.getProcessVariables().get("formText"));
        assertEquals(1L, process.getProcessVariables().get("PROCESS_START_USER_ID"));
        assertFalse(first.toString().contains("private-value"));
        verify(definitions).canUserStartProcessDefinition(any(), eq(1L));
        assertNotEquals(pinned, engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("task-approval")
                .latestVersion().singleResult().getId());
    }
    @Test void requiresBothApprovedStatusAndEndedProcessAndNeverReusesAnOldRound() {
        var first = start();
        engine.getRuntimeService().setVariable(first.processInstanceId(), "PROCESS_STATUS", 2);
        assertEquals(Outcome.NOT_SATISFIED, fact().outcome());
        end(first.processInstanceId(), 2);
        assertEquals(Outcome.SATISFIED, fact().outcome()); assertEquals(fact(), start());
        var next = new Scope(NodeKind.TASK,7L,9L,21L,ids.incrementAndGet(),92L,"task-approval",pinned,scope.startedAt());
        assertEquals("NOT_STARTED", tx.execute(ignored -> owner.inspect(next)).status());
        assertEquals(Outcome.SATISFIED, fact().outcome()); // old history is untouched
    }
    @Test void originalStartPermissionDenialDoesNotCreateAProcess() {
        when(definitions.canUserStartProcessDefinition(any(), eq(1L))).thenReturn(false);
        assertThrows(RuntimeException.class, this::start);
        assertEquals("NOT_STARTED", fact().status());
    }
    @Test void sharedCreationPreservesGateIdentityFormAndOriginalSelectedApprovers() {
        var creation = new PmsApprovalProcessCreationService(processes, engine.getRepositoryService());
        var node = new BpmApprovalDetailRespVO.ActivityNode().setId("approve").setName("审核").setCandidateStrategy(35);
        doReturn(new BpmApprovalDetailRespVO().setActivityNodes(new ArrayList<>(List.of(node))))
                .when(processes).getApprovalDetail(eq(1L), any());
        var users = mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        ReflectionTestUtils.setField(processes, "adminUserApi", users);
        when(users.getUserMap(List.of(12L))).thenReturn(Map.of(12L, new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO().setId(12L)));
        String businessKey = "PROJECT_STAGE_GATE:" + scope.executionId();
        var variables = new HashMap<String, Object>();
        variables.put("formText", "gate-form"); variables.put("optionalField", null);
        var command = new cn.iocoder.yudao.module.pms.project.api.approval.ProjectApprovalProcessCreationApi.Command(
                7L, 1L, "task-approval", pinned, businessKey, variables, Map.of("approve", List.of(12L)));
        String id = tx.execute(ignored -> creation.create(command));
        var process = engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(id).includeProcessVariables().singleResult();
        assertEquals(pinned, process.getProcessDefinitionId()); assertEquals(businessKey, process.getBusinessKey());
        assertEquals("1", process.getStartUserId()); assertEquals("gate-form", process.getProcessVariables().get("formText"));
        assertEquals(1, process.getProcessVariables().get("PROCESS_STATUS"));
        verify(users).getUserMap(List.of(12L));
        when(definitions.canUserStartProcessDefinition(any(), eq(1L))).thenReturn(false);
        assertThrows(RuntimeException.class, () -> tx.execute(ignored -> creation.create(command)));
        assertEquals(1, engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceBusinessKey(businessKey).count());
    }

    @Test void originalSelectedApproverValidationStillRuns() {
        var node = new BpmApprovalDetailRespVO.ActivityNode().setId("approve").setName("审核").setCandidateStrategy(35);
        doReturn(new BpmApprovalDetailRespVO().setActivityNodes(new ArrayList<>(List.of(node))))
                .when(processes).getApprovalDetail(eq(1L), any());
        assertThrows(RuntimeException.class, this::start);
        assertEquals("NOT_STARTED", fact().status());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"3,REJECTED", "4,CANCELLED"})
    void rejectionAndCancellationDoNotApproveTheTask(int result, String status) {
        var started = start(); end(started.processInstanceId(), result);
        assertEquals(status, fact().status()); assertEquals(Outcome.NOT_SATISFIED, fact().outcome());
    }
    @Test void anEndedProcessWithoutAnOriginalBpmResultIsUnknown() {
        var started = start(); end(started.processInstanceId(),1);
        assertEquals(Outcome.UNKNOWN,fact().outcome());
        assertEquals("TASK_APPROVAL_RESULT_UNAVAILABLE",fact().reason());
    }
    @Test void ambiguousInstancesCannotBeChosenAsApprovedOrReplayed() {
        var first = start();
        var variables = engine.getRuntimeService().getVariables(first.processInstanceId());
        tx.executeWithoutResult(ignored -> engine.getRuntimeService().createProcessInstanceBuilder()
                .processDefinitionId(pinned).businessKey(scope.businessKey()).variables(variables).start());
        assertEquals(Outcome.UNKNOWN,fact().outcome());
        assertEquals("TASK_APPROVAL_ATTEMPT_IDENTITY_INVALID",fact().reason());
        assertThrows(IllegalStateException.class,this::start);
    }
    @Test void invalidIdentityMissingStatusAndOldTimeAreUnknown() {
        var started = start(); var runtime = engine.getRuntimeService();
        runtime.setVariable(started.processInstanceId(), VAR_CONTRACT, 90L);
        assertEquals(Outcome.UNKNOWN, fact().outcome());
        runtime.setVariable(started.processInstanceId(), VAR_CONTRACT, 91L);
        runtime.removeVariable(started.processInstanceId(), "PROCESS_STATUS");
        assertEquals(Outcome.UNKNOWN, fact().outcome());
        var future = new Scope(NodeKind.TASK,7L,9L,21L,scope.executionId(),91L,"task-approval",pinned,LocalDateTime.now().plusDays(1));
        assertEquals("TASK_APPROVAL_ROUND_EVIDENCE_UNAVAILABLE", tx.execute(ignored -> owner.inspect(future)).reason());
    }
    @Test void staleExecutionAndForeignTenantCannotStart() {
        when(executions.lockAndRevalidate(context)).thenThrow(new IllegalStateException("stale round"));
        assertThrows(RuntimeException.class, this::start);
        TenantContextHolder.setTenantId(8L);
        assertThrows(IllegalArgumentException.class, this::start);
        TenantContextHolder.setTenantId(7L); assertEquals("NOT_STARTED", fact().status());
    }
    @Test void failedOuterCommandRollsBackTheRealApprovalProcess() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(ignored -> {
            start(); throw new IllegalStateException("task transaction failed");
        }));
        assertEquals("NOT_STARTED", fact().status());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {3,4})
    void aNewIntentAfterRejectionOrCancellationUsesANewAttemptInTheSameRound(int status) {
        var first = start(); end(first.processInstanceId(),status);
        var second = start("second");
        assertNotEquals(first.processInstanceId(),second.processInstanceId());
        assertEquals("RUNNING",fact().status()); assertEquals(second,start("second"));
        assertEquals(first.processInstanceId(),start().processInstanceId()); // retry does not become another submission
        assertEquals(second.processInstanceId(),fact().processInstanceId()); // current fact is not that older replay
        end(second.processInstanceId(),2);
        assertEquals(Outcome.SATISFIED,fact().outcome());
        assertEquals(second.processInstanceId(),fact().processInstanceId());
        assertEquals(2,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceBusinessKey(scope.businessKey()).count());
    }
    @Test void aDifferentIntentCannotDuplicateARunningOrApprovedAttempt() {
        var first = start();
        assertThrows(IllegalStateException.class,() -> start("second"));
        end(first.processInstanceId(),2);
        assertThrows(IllegalStateException.class,() -> start("third"));
        assertEquals(1,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceBusinessKey(scope.businessKey()).count());
    }
}
