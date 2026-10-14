package cn.iocoder.yudao.module.bpm.service.cutover;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.cutover.CutoverWorkflowApi;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import org.flowable.engine.*;
import cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** 真正运行 BPMN，验证网关路由及通用 BPM 操作无法绕过业务保护。仅使用内存数据库。 */
class CutoverBpmGuardFlowableTest {
    static ProcessEngine engine;
    static CutoverBpmGuard guard;
    static final AtomicLong IDS = new AtomicLong(100);

    @BeforeAll static void startEngine() {
        var config = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:cutover-guard;DB_CLOSE_DELAY=-1");
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);
        Map<Object, Object> beans = new HashMap<>();
        config.setBeans(beans);
        engine = config.buildProcessEngine();
        var roles = mock(RoleCandidateFactApi.class);
        when(roles.lockAndInspectActor(any())).thenAnswer(call -> {
            RoleCandidateFactApi.ActorQuery query = call.getArgument(0);
            return query.tenantId() == 1L && query.actorUserId() == 7L
                    ? List.of(new RoleCandidateFactApi.ActorMembership(7L, 80L, "probSupporter", 1L, 1L),
                              new RoleCandidateFactApi.ActorMembership(7L, 90L, "probRD", 1L, 1L)) : List.of();
        });
        guard = new CutoverBpmGuard(engine.getRuntimeService(), new CutoverRoleTaskAccess(roles, engine.getTaskService()));
        beans.put("cutoverBpmGuard", guard);
        engine.getRepositoryService().createDeployment().tenantId("1")
                .addClasspathResource("bpmn/cutover/PMS_CUTOVER_APPROVAL.bpmn").deploy();
    }

    @AfterAll static void stopEngine() { if (engine != null) engine.close(); }
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void eachGradeKeepsEveryRequiredManualNode() {
        for (String grade : List.of("A", "B", "C", "D")) {
            String instance = start(grade);
            for (String expected : route(grade)) {
                Task task = task(instance);
                assertNotNull(task);
                assertEquals(expected, task.getTaskDefinitionKey());
                if (List.of("SECOND_LINE", "RND").contains(expected)) assertNull(task.getAssignee());
                else assertEquals("7", task.getAssignee()); // 同一人仍必须逐节点审查。
                approve(instance, task);
            }
            assertNull(task(instance));
            assertEquals(1L, engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(instance).finished().count());
        }
    }

    @Test void genericCompletionAndCancellationCannotSkipReview() {
        String instance = start("B");
        Task task = task(instance);
        assertThrows(RuntimeException.class, () -> engine.getTaskService().complete(task.getId()));
        assertThrows(RuntimeException.class, () -> engine.getRuntimeService().deleteProcessInstance(instance, "绕过割接"));
        assertEquals(task.getId(), task(instance).getId());
        approve(instance, task(instance));
        assertEquals("SERVICE_MANAGER", task(instance).getTaskDefinitionKey());
    }

    @Test void changedGradeOrAssigneeCannotAlterFrozenRoute() {
        String instance = start("A");
        Task task = task(instance);
        assertThrows(RuntimeException.class, () -> engine.getTaskService().setAssignee(task.getId(), "8"));
        engine.getRuntimeService().setVariable(instance, "cutoverGrade", "D");
        assertThrows(RuntimeException.class, () -> approve(instance, task));
        assertEquals(task.getId(), task(instance).getId());
        engine.getRuntimeService().setVariable(instance, "cutoverGrade", "A");
        approve(instance, task(instance));
        assertEquals("SERVICE_MANAGER", task(instance).getTaskDefinitionKey());
    }

    @Test void nativeStartNeedsValidatedCutoverCommand() {
        assertThrows(RuntimeException.class, () -> engine.getRuntimeService().startProcessInstanceByKeyAndTenantId(
                CutoverWorkflowApi.DEFINITION_KEY, "untrusted", variables("D"), "1"));
    }

    @Test void changedBranchOrAutomaticApprovalIsRejectedBeforeStarting() throws Exception {
        org.flowable.bpmn.model.BpmnModel model;
        try (var input = getClass().getResourceAsStream("/bpmn/cutover/PMS_CUTOVER_APPROVAL.bpmn")) {
            model = new org.flowable.bpmn.converter.BpmnXMLConverter().convertToBpmnModel(
                    new org.flowable.common.engine.impl.util.io.BytesStreamSource(input.readAllBytes()), false, false);
        }
        CutoverWorkflowDefinitionPolicy.validate(model);
        var branch = (org.flowable.bpmn.model.SequenceFlow) model.getMainProcess().getFlowElement("f3");
        String condition = branch.getConditionExpression();
        branch.setConditionExpression("${false}");
        assertThrows(IllegalArgumentException.class, () -> CutoverWorkflowDefinitionPolicy.validate(model));
        branch.setConditionExpression(condition);
        var manager = (org.flowable.bpmn.model.UserTask) model.getMainProcess().getFlowElement("SERVICE_MANAGER");
        manager.getExtensionElements().get("approveType").getFirst().setElementText("2");
        assertThrows(IllegalArgumentException.class, () -> CutoverWorkflowDefinitionPolicy.validate(model));
    }

    private static String start(String grade) {
        var command = new CutoverWorkflowApi.Start(1L, 7L, 9L, 11L, IDS.incrementAndGet(), grade,
                route(grade).stream().map(CutoverBpmGuardFlowableTest::node).toList());
        return guard.start(command, () -> engine.getRuntimeService().startProcessInstanceByKeyAndTenantId(
                CutoverWorkflowApi.DEFINITION_KEY, command.approvalId().toString(), variables(grade), "1").getId());
    }

    private static CutoverWorkflowApi.Node node(String code) {
        return switch (code) {
            case "SECOND_LINE" -> new CutoverWorkflowApi.Node(code, null, new CutoverWorkflowApi.RoleReference(80L, "probSupporter"));
            case "RND" -> new CutoverWorkflowApi.Node(code, null, new CutoverWorkflowApi.RoleReference(90L, "probRD"));
            default -> new CutoverWorkflowApi.Node(code, 7L);
        };
    }
    private static Map<String, Object> variables(String grade) {
        var variables = new HashMap<String, Object>(); variables.put("cutoverGrade", grade);
        route(grade).stream().map(CutoverBpmGuardFlowableTest::node).forEach(node -> {
            variables.put("cutoverUser_" + node.code(), node.userId());
            if (node.role() != null) {
                variables.put("cutoverRoleId_" + node.code(), node.role().roleId());
                variables.put("cutoverRoleCode_" + node.code(), node.role().roleCode());
                variables.put("cutoverGroup_" + node.code(), node.role().groupKey());
            }
        });
        return variables;
    }

    private static List<String> route(String grade) {
        return switch (grade) {
            case "A" -> List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE", "RND");
            case "B" -> List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE");
            default -> List.of("INITIATOR", "SERVICE_MANAGER");
        };
    }

    private static Task task(String instance) {
        return engine.getTaskService().createTaskQuery().processInstanceId(instance).singleResult();
    }

    private static void approve(String instance, Task task) {
        guard.mutate(new CutoverBpmGuard.Operation(instance, task.getTaskDefinitionKey(), 7L, "APPROVE"), () -> {
            if (task.getAssignee() == null) engine.getTaskService().claim(task.getId(), "7");
            engine.getTaskService().setVariableLocal(task.getId(), BpmnVariableConstants.TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
            engine.getTaskService().complete(task.getId());
        });
    }
}
