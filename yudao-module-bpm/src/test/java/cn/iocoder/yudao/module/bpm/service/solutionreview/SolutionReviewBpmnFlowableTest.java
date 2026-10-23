package cn.iocoder.yudao.module.bpm.service.solutionreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.solutionreview.SolutionReviewBpmApi;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmAutoApproveTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.TASK_VARIABLE_STATUS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the bundled BPMN with its real business guard in an isolated H2 engine. */
class SolutionReviewBpmnFlowableTest {
    static EmbeddedDatabase database;
    static ProcessEngine engine;
    static TransactionTemplate tx;
    static String definitionId;
    static final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    static final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    static final ExplicitPermissionApi permissions = mock(ExplicitPermissionApi.class);
    static SolutionReviewBpmGuard guard;
    SolutionReviewBpmService service;

    @BeforeAll static void createEngine() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var manager = new DataSourceTransactionManager(database);
        tx = new TransactionTemplate(manager);
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(database);
        config.setTransactionManager(manager);
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);
        Map<Object, Object> beans = new HashMap<>();
        config.setBeans(beans);
        engine = config.buildProcessEngine();
        guard = new SolutionReviewBpmGuard(engine.getRuntimeService(), engine.getRepositoryService(),
                scope, participants, permissions, mock(AdminUserApi.class));
        beans.put("solutionReviewBpmGuard", guard);
        var deployment = engine.getRepositoryService().createDeployment().tenantId("1")
                .addClasspathResource("bpmn/solutionreview/PMS_DELIVERY_SOLUTION_REVIEW.bpmn").deploy();
        definitionId = engine.getRepositoryService().createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).singleResult().getId();
    }

    @AfterAll static void closeEngine() {
        try { if (engine != null) engine.close(); }
        finally { if (database != null) database.shutdown(); }
    }

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        reset(scope, participants, permissions);
        var visible = new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of());
        when(scope.resolveCurrent(any())).thenReturn(visible);
        when(scope.lockAndRevalidate(any())).thenReturn(visible);
        var manager = new ProjectParticipantFact(9L, 7L, Set.of("SERVICE_MANAGER"), "PRIMARY", "ACTIVE", "S3", 1L, 1L);
        when(participants.inspect(any())).thenReturn(manager);
        when(participants.lockAndRevalidate(any())).thenReturn(manager);
        var definitions = mock(BpmProcessDefinitionService.class);
        when(definitions.getActiveProcessDefinition(SolutionReviewBpmApi.DEFINITION_KEY))
                .thenReturn(engine.getRepositoryService().getProcessDefinition(definitionId));
        when(definitions.getProcessDefinitionInfo(definitionId)).thenReturn(new BpmProcessDefinitionInfoDO()
                .setAutoApprovalType(BpmAutoApproveTypeEnum.NONE.getType()));
        service = new SolutionReviewBpmService(engine.getRepositoryService(), engine.getHistoryService(), definitions,
                command -> engine.getRuntimeService().createProcessInstanceBuilder()
                        .processDefinitionId(command.definitionId()).tenantId(command.tenantId().toString())
                        .businessKey(command.businessKey()).variables(command.variables()).start().getId(), guard);
    }

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void bundledDefinitionPassesBusinessValidation() {
        assertEquals(definitionId, service.definition(1L).id());
    }

    @Test void ordinaryReviewEndsAfterServiceManagerWithoutEngineeringGrant() {
        var started = start(0);
        assertEquals(Map.of("serviceManagerReview", 7L), started.candidates());
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).count());
        assertEquals(0, engine.getHistoryService().createHistoricTaskInstanceQuery()
                .processInstanceId(started.instanceId()).taskDefinitionKey("engineeringManagementReview").count());
        verifyNoInteractions(permissions);
    }

    @Test void majorReviewWaitsForEngineeringManagementAfterServiceManager() {
        when(permissions.listUsersWithPermission(1L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)).thenReturn(List.of(8L));
        when(permissions.lockAndCheck(1L, 8L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)).thenReturn(true);
        var started = start(1);
        assertEquals(Map.of("serviceManagerReview", 7L, "engineeringManagementReview", 8L), started.candidates());
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        var second = currentTask(started.instanceId(), "engineeringManagementReview");
        assertEquals("8", second.getAssignee());
        assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).count());
        approve(second);
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).count());
        assertEquals(2, engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(started.instanceId()).finished().count());
    }

    @Test void majorReviewWithoutEngineeringGrantIsRejectedBeforeStarting() {
        when(permissions.listUsersWithPermission(1L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)).thenReturn(List.of());
        var failure = assertThrows(IllegalArgumentException.class, () -> start(1));
        assertTrue(failure.getMessage().contains("复审授权"));
    }

    @Test void missingServiceManagerDecisionCannotAdvanceToBranch() {
        var started = start(0);
        var task = currentTask(started.instanceId(), "serviceManagerReview");
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(ignored -> engine.getTaskService().complete(task.getId())));
        assertEquals(task.getId(), currentTask(started.instanceId(), "serviceManagerReview").getId());
    }

    @Test void unknownReviewLevelCannotFallThroughToOrdinaryApproval() {
        var started = start(2);
        var task = currentTask(started.instanceId(), "serviceManagerReview");
        assertThrows(RuntimeException.class, () -> approve(task));
        assertEquals(task.getId(), currentTask(started.instanceId(), "serviceManagerReview").getId());
    }

    private SolutionReviewBpmApi.Started start(int level) {
        return tx.execute(ignored -> service.start(new SolutionReviewBpmApi.Start(1L, 6L, 9L,
                "SOL_REVIEW:1:" + UUID.randomUUID(), definitionId, level)));
    }

    private Task currentTask(String instanceId, String key) {
        var task = engine.getTaskService().createTaskQuery().processInstanceId(instanceId).singleResult();
        assertNotNull(task);
        assertEquals(key, task.getTaskDefinitionKey());
        return task;
    }

    private void approve(Task task) {
        tx.executeWithoutResult(ignored -> {
            engine.getTaskService().setVariableLocal(task.getId(), TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
            engine.getTaskService().complete(task.getId());
        });
    }
}
