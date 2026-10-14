package cn.iocoder.yudao.module.bpm.service.cutover;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmCutoverActivityBehaviorFactory;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi;
import org.flowable.engine.*;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 实际Flowable/H2验证原生候选组、当前成员变化、受控领取及完成历史。 */
class CutoverRolePoolFlowableTest {
    static ProcessEngine engine;
    static CutoverBpmGuard guard;
    static CutoverRoleTaskAccess access;
    static final AtomicLong IDS = new AtomicLong(500);
    static final Map<Long, List<RoleCandidateFactApi.ActorMembership>> MEMBERS = new HashMap<>();

    @BeforeAll static void startEngine() {
        var config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:cutover-role-groups;DB_CLOSE_DELAY=-1");
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);
        var invoker = mock(BpmTaskCandidateInvoker.class);
        when(invoker.calculateUsersByTask(any())).thenAnswer(call -> {
            var execution = (org.flowable.engine.delegate.DelegateExecution) call.getArgument(0);
            return Set.of("INITIATOR", "SERVICE_MANAGER").contains(execution.getCurrentActivityId()) ? Set.of(7L) : Set.of();
        });
        var behavior = new BpmCutoverActivityBehaviorFactory(); behavior.setTaskCandidateInvoker(invoker);
        config.setActivityBehaviorFactory(behavior);
        Map<Object, Object> beans = new HashMap<>(); config.setBeans(beans);
        engine = config.buildProcessEngine();
        var roles = mock(RoleCandidateFactApi.class);
        when(roles.inspectActor(any())).thenAnswer(call -> memberships(call.getArgument(0)));
        when(roles.lockAndInspectActor(any())).thenAnswer(call -> memberships(call.getArgument(0)));
        access = new CutoverRoleTaskAccess(roles, engine.getTaskService());
        guard = new CutoverBpmGuard(engine.getRuntimeService(), access);
        beans.put("cutoverBpmGuard", guard);
        engine.getRepositoryService().createDeployment().tenantId("1")
                .addClasspathResource("bpmn/cutover/PMS_CUTOVER_APPROVAL.bpmn").deploy();
    }
    @AfterAll static void stopEngine() { if (engine != null) engine.close(); }
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); MEMBERS.clear(); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void oneNativeGroupTaskRecordsOnlyActualCompletingMember() {
        String instance = second(List.of(8L, 9L)); Task second = task(instance);
        assertEquals("SECOND_LINE", second.getTaskDefinitionKey()); assertNull(second.getAssignee());
        assertEquals(1, engine.getTaskService().createTaskQuery().processInstanceId(instance).count());
        var candidates = engine.getTaskService().getIdentityLinksForTask(second.getId());
        assertEquals(1, candidates.size()); assertEquals("ROLE:80", candidates.getFirst().getGroupId());
        assertNull(candidates.getFirst().getUserId());
        assertTrue(engine.getRuntimeService().getIdentityLinksForProcessInstance(instance).stream()
                .noneMatch(link -> "cutover:SECOND_LINE".equals(link.getType()) || "cutoverCurrent:SECOND_LINE".equals(link.getType())));
        for (long user : List.of(8L, 9L)) assertEquals(second.getId(),
                access.todos(new RoleCandidateFactApi.ActorQuery(1L, user)).processInstanceId(instance).singleResult().getId());
        approve(instance, second, 9L);
        assertEquals("RND", task(instance).getTaskDefinitionKey()); assertNull(task(instance).getAssignee());
        assertEquals("9", engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(second.getId()).singleResult().getAssignee());
        assertThrows(RuntimeException.class, () -> approve(instance, second, 8L));
    }

    @Test void addedMemberCanHandleExistingGroupTaskAndRemovedMemberCannot() {
        String instance = second(List.of(8L)); Task pending = task(instance);
        MEMBERS.remove(8L); member(9L, 80L, "probSupporter");
        assertEquals(0, access.todos(new RoleCandidateFactApi.ActorQuery(1L, 8L)).processInstanceId(instance).count());
        assertEquals(1, access.todos(new RoleCandidateFactApi.ActorQuery(1L, 9L)).processInstanceId(instance).count());
        assertThrows(RuntimeException.class, () -> approve(instance, pending, 8L));
        assertNull(task(instance).getAssignee());
        approve(instance, pending, 9L); assertEquals("RND", task(instance).getTaskDefinitionKey());
    }

    @Test void genericCompletionAndClaimCannotBypassCutoverReview() {
        String instance = second(List.of(8L, 9L)); Task pending = task(instance);
        assertThrows(RuntimeException.class, () -> engine.getTaskService().complete(pending.getId()));
        assertThrows(RuntimeException.class, () -> engine.getTaskService().claim(pending.getId(), "8"));
        assertNull(task(instance).getAssignee()); assertEquals(pending.getId(), task(instance).getId());
    }

    @Test void editedGroupVariableAndInjectedUserCandidateCannotReplaceFrozenRole() {
        String instance = second(List.of(8L)); Task pending = task(instance);
        engine.getRuntimeService().setVariable(instance, "cutoverGroup_SECOND_LINE", "ROLE:90");
        assertThrows(RuntimeException.class, () -> approve(instance, pending, 8L));
        engine.getRuntimeService().setVariable(instance, "cutoverGroup_SECOND_LINE", "ROLE:80");
        engine.getTaskService().addCandidateUser(pending.getId(), "7");
        assertThrows(RuntimeException.class, () -> approve(instance, pending, 7L));
        assertThrows(RuntimeException.class, () -> approve(instance, pending, 8L));
        assertNull(task(instance).getAssignee());
    }

    @Test void emptyRoleGroupRemainsPendingAndAcceptsLaterMember() {
        String instance = second(List.of()); Task pending = task(instance);
        assertEquals("SECOND_LINE", pending.getTaskDefinitionKey()); assertNull(pending.getAssignee());
        assertEquals(0, access.todos(new RoleCandidateFactApi.ActorQuery(1L, 7L)).processInstanceId(instance).count());
        assertThrows(RuntimeException.class, () -> approve(instance, pending, 7L));
        assertEquals(0, engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(instance).finished().count());
        member(8L, 80L, "probSupporter"); approve(instance, pending, 8L);
        assertEquals("RND", task(instance).getTaskDefinitionKey());
    }

    @Test void otherTenantCannotSeeOrClaimRoleTask() {
        String instance = second(List.of(8L)); Task pending = task(instance);
        TenantContextHolder.setTenantId(2L);
        assertEquals(0, access.todos(new RoleCandidateFactApi.ActorQuery(2L, 8L)).processInstanceId(instance).count());
        assertThrows(RuntimeException.class, () -> approve(instance, pending, 8L));
        TenantContextHolder.setTenantId(1L); assertNull(task(instance).getAssignee());
    }

    private static List<RoleCandidateFactApi.ActorMembership> memberships(RoleCandidateFactApi.ActorQuery query) {
        return query.tenantId() == 1L ? MEMBERS.getOrDefault(query.actorUserId(), List.of()) : List.of();
    }
    private static void member(long user, long role, String code) {
        MEMBERS.put(user, List.of(new RoleCandidateFactApi.ActorMembership(user, role, code, 1L, 1L)));
    }
    private static String second(List<Long> supporters) {
        supporters.forEach(user -> member(user, 80L, "probSupporter")); member(10L, 90L, "probRD");
        var nodes = List.of(new CutoverWorkflowApi.Node("INITIATOR", 7L), new CutoverWorkflowApi.Node("SERVICE_MANAGER", 7L),
                new CutoverWorkflowApi.Node("SECOND_LINE", null, new CutoverWorkflowApi.RoleReference(80L, "probSupporter")),
                new CutoverWorkflowApi.Node("RND", null, new CutoverWorkflowApi.RoleReference(90L, "probRD")));
        var command = new CutoverWorkflowApi.Start(1L, 7L, 9L, 11L, IDS.incrementAndGet(), "A", nodes);
        var variables = new HashMap<String, Object>(); variables.put("cutoverGrade", "A");
        nodes.forEach(node -> {
            variables.put("cutoverUser_" + node.code(), node.userId());
            if (node.role() != null) {
                variables.put("cutoverRoleId_" + node.code(), node.role().roleId());
                variables.put("cutoverRoleCode_" + node.code(), node.role().roleCode());
                variables.put("cutoverGroup_" + node.code(), node.role().groupKey());
            }
        });
        String instance = guard.start(command, () -> engine.getRuntimeService().startProcessInstanceByKeyAndTenantId(
                CutoverWorkflowApi.DEFINITION_KEY, command.approvalId().toString(), variables, "1").getId());
        approve(instance, task(instance), 7L); approve(instance, task(instance), 7L); return instance;
    }
    private static Task task(String instance) {
        return engine.getTaskService().createTaskQuery().processInstanceId(instance).singleResult();
    }
    private static void approve(String instance, Task task, Long actor) {
        guard.mutate(new CutoverBpmGuard.Operation(instance, task.getTaskDefinitionKey(), actor, "APPROVE"), () ->
                engine.getManagementService().executeCommand(context -> {
                    if (task.getAssignee() == null) engine.getTaskService().claim(task.getId(), actor.toString());
                    engine.getTaskService().setVariableLocal(task.getId(), BpmnVariableConstants.TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
                    engine.getTaskService().complete(task.getId()); return null;
                }));
    }
}
