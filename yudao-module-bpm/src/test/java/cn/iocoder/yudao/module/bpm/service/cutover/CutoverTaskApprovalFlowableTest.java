package cn.iocoder.yudao.module.bpm.service.cutover;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmActivityBehaviorFactory;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.other.BpmTaskCandidateExpressionStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.springframework.context.support.GenericApplicationContext;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.TASK_VARIABLE_STATUS;
import static cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the real BPMN and listeners, with isolated H2 and doubles only for module APIs. */
class CutoverTaskApprovalFlowableTest {
    ProcessEngine engine;
    GenericApplicationContext context;
    String definition;
    final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final AdminUserApi users = mock(AdminUserApi.class);

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(7L);
        var current = new ProjectTaskExecutionContext(9L, 1L, 21L, 1, 91L, 1, 51L, 101L, 1, 1, 62L, 1, true, LocalDateTime.now().minusSeconds(2));
        when(executions.inspect(any())).thenReturn(current);
        when(executions.lockAndRevalidate(current)).thenReturn(current);
        when(participants.inspect(any())).thenReturn(member(12L));
        var scope = new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope);
        when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        when(users.getUserList(anyCollection())).thenAnswer(call -> ((Collection<Long>) call.getArgument(0)).stream()
                .map(id -> new AdminUserRespDTO().setId(id).setStatus(0)).toList());
        when(users.getUserMap(anyCollection())).thenCallRealMethod();
        var candidates = new BpmTaskCandidateInvoker(List.of(new BpmTaskCandidateExpressionStrategy()), users);
        var behavior = new BpmActivityBehaviorFactory();
        behavior.setTaskCandidateInvoker(candidates);
        var config = (org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:cutover_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);
        config.setActivityBehaviorFactory(behavior);
        Map<Object, Object> beans = new HashMap<>();
        config.setBeans(beans);
        engine = config.buildProcessEngine();
        beans.put("cutoverTaskApprovalGuard", new CutoverTaskApprovalGuard(engine.getRuntimeService(), executions, participants, scopes, users));
        var processes = mock(BpmProcessInstanceService.class);
        when(processes.getProcessInstance(anyString())).thenAnswer(call -> engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(call.getArgument(0)).includeProcessVariables().singleResult());
        context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("bpmProcessInstanceService", processes);
        context.refresh();
        var spring = new SpringUtil();
        spring.postProcessBeanFactory(context.getBeanFactory());
        spring.setApplicationContext(context);
        engine.getRepositoryService().createDeployment().tenantId("7").addClasspathResource("bpmn/cutover/pms-cutover-done-approval.bpmn").deploy();
        definition = engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("pms-cutover-done-approval").singleResult().getId();
    }

    @AfterEach void close() {
        if (engine != null) engine.close();
        if (context != null) context.close();
        TenantContextHolder.clear();
    }

    @Test void bindsCurrentServiceManagerAndRequiresRealManualApproval() {
        var instance = start();
        var task = task(instance);
        assertEquals("12", task.getAssignee());
        assertThrows(RuntimeException.class, () -> engine.getTaskService().complete(task.getId()));
        assertNotNull(task(instance));
        approve(task);
        assertNull(task(instance));
        assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(instance.getId()).singleResult().getEndTime());
    }
    @Test void standaloneStartCannotCreateBusinessEvidence() {
        assertThrows(RuntimeException.class, () -> engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition).variables(variables()).start());
        assertEquals(0, engine.getHistoryService().createHistoricProcessInstanceQuery().count());
        verifyNoInteractions(participants);
    }
    @Test void missingOrAmbiguousManagerRollsBackCreation() {
        when(participants.inspect(any())).thenThrow(new IllegalArgumentException("not unique"));
        assertThrows(RuntimeException.class, this::start);
        assertEquals(0, engine.getTaskService().createTaskQuery().count());
        assertEquals(0, engine.getHistoryService().createHistoricProcessInstanceQuery().count());
    }
    @Test void disabledOrOutOfScopeManagerCannotStart() {
        doThrow(new IllegalArgumentException("disabled")).when(users).validateUser(12L);
        assertThrows(RuntimeException.class, this::start);
        doNothing().when(users).validateUser(12L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(), Set.of(9L)));
        assertThrows(RuntimeException.class, this::start);
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().count());
    }
    @Test void changedScopeOrRoundCannotStart() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L, 2L, Set.of(9L), Set.of()));
        assertThrows(RuntimeException.class, this::start);
        when(executions.lockAndRevalidate(any())).thenThrow(new IllegalArgumentException("stale round"));
        assertThrows(RuntimeException.class, this::start);
        assertEquals(0, engine.getHistoryService().createHistoricProcessInstanceQuery().count());
    }
    @Test void mutableVariablesAndReassignmentCannotReplaceIdentityOrApprover() {
        var instance = start();
        var task = task(instance);
        engine.getRuntimeService().setVariable(instance.getId(), VAR_PROJECT, 99L);
        assertThrows(RuntimeException.class, () -> approve(task));
        engine.getRuntimeService().setVariable(instance.getId(), VAR_PROJECT, 9L);
        engine.getTaskService().setAssignee(task.getId(), "99");
        assertThrows(RuntimeException.class, () -> approve(task));
        assertNotNull(task(instance));
    }
    @Test void changedManagerCannotApproveOrRejectWhileCancellationRemainsAvailable() {
        var instance = start();
        var task = task(instance);
        when(participants.inspect(any())).thenReturn(member(13L));
        assertThrows(RuntimeException.class, () -> approve(task));
        engine.getTaskService().setVariableLocal(task.getId(), TASK_VARIABLE_STATUS, 3);
        assertThrows(RuntimeException.class, () -> engine.getRuntimeService().deleteProcessInstance(instance.getId(), "rejected"));
        engine.getTaskService().setVariableLocal(task.getId(), TASK_VARIABLE_STATUS, 4);
        engine.getRuntimeService().deleteProcessInstance(instance.getId(), "cancelled");
        assertNull(task(instance));
    }
    private ProcessInstance start() {
        return FlowableUtils.executeAuthenticatedUserId(11L, () -> engine.getRuntimeService().createProcessInstanceBuilder()
                .processDefinitionId(definition).businessKey(NodeKind.TASK.businessKey(101L)).variables(variables()).start());
    }
    private Map<String, Object> variables() {
        var values = new HashMap<String, Object>(new Scope(NodeKind.TASK, 7L, 9L, 21L, 101L, 91L,
                "pms-cutover-done-approval", definition, LocalDateTime.now().minusSeconds(2)).identity());
        values.put("serviceManagerUserId", 99L);
        return values;
    }
    private Task task(ProcessInstance instance) { return engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult(); }
    private void approve(Task task) {
        engine.getTaskService().setVariableLocal(task.getId(), TASK_VARIABLE_STATUS, 2);
        engine.getTaskService().complete(task.getId());
    }
    private ProjectParticipantFact member(Long user) { return new ProjectParticipantFact(9L, user, Set.of("SERVICE_MANAGER"), "PRIMARY", "ACTIVE", "S4", 1L, 1L); }
}
