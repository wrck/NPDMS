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
import cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmCutoverActivityBehaviorFactory;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
    static DataSourceTransactionManager transactionManager;
    static String definitionId;
    static final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    static final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    static final ExplicitPermissionApi permissions = mock(ExplicitPermissionApi.class);
    static final RoleCandidateFactApi roles = mock(RoleCandidateFactApi.class);
    static SolutionReviewBpmGuard guard;
    static SolutionReviewRoleTasks roleTasks;
    SolutionReviewBpmService service;
    BpmProcessDefinitionService definitions;

    @BeforeAll static void createEngine() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var manager = new DataSourceTransactionManager(database);
        transactionManager = manager;
        tx = new TransactionTemplate(manager);
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(database);
        config.setTransactionManager(manager);
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setAsyncExecutorActivate(false);
        var invoker = mock(BpmTaskCandidateInvoker.class);
        when(invoker.calculateUsersByTask(any())).thenAnswer(call -> {
            var execution = (org.flowable.engine.delegate.DelegateExecution) call.getArgument(0);
            var candidates = (Map<?, ?>) execution.getVariable("solutionReviewCandidates");
            var actor = candidates.get(execution.getCurrentActivityId());
            return actor == null ? Set.of() : Set.of((Long) actor);
        });
        var behavior = new BpmCutoverActivityBehaviorFactory();
        behavior.setTaskCandidateInvoker(invoker);
        config.setActivityBehaviorFactory(behavior);
        Map<Object, Object> beans = new HashMap<>();
        config.setBeans(beans);
        engine = config.buildProcessEngine();
        roleTasks = new SolutionReviewRoleTasks(roles, permissions, engine.getRuntimeService(), engine.getTaskService(), engine.getHistoryService());
        guard = new SolutionReviewBpmGuard(engine.getRuntimeService(), engine.getRepositoryService(),
                scope, participants, permissions, mock(AdminUserApi.class), roleTasks);
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
        reset(scope, participants, permissions, roles);
        var visible = new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of());
        when(scope.resolveCurrent(any())).thenReturn(visible);
        when(scope.lockAndRevalidate(any())).thenReturn(visible);
        var manager = new ProjectParticipantFact(9L, 7L, Set.of("SERVICE_MANAGER"), "PRIMARY", "ACTIVE", "S3", 1L, 1L);
        when(participants.inspect(any())).thenReturn(manager);
        when(participants.lockAndRevalidate(any())).thenReturn(manager);
        definitions = mock(BpmProcessDefinitionService.class);
        when(definitions.getActiveProcessDefinition(SolutionReviewBpmApi.DEFINITION_KEY))
                .thenReturn(engine.getRepositoryService().getProcessDefinition(definitionId));
        when(definitions.getProcessDefinitionInfo(definitionId)).thenReturn(new BpmProcessDefinitionInfoDO()
                .setAutoApprovalType(BpmAutoApproveTypeEnum.NONE.getType()));
        service = new SolutionReviewBpmService(engine.getRepositoryService(), engine.getHistoryService(), definitions,
                command -> engine.getRuntimeService().createProcessInstanceBuilder()
                        .processDefinitionId(command.definitionId()).tenantId(command.tenantId().toString())
                        .businessKey(command.businessKey()).variables(command.variables()).start().getId(), guard, roleTasks);
    }

    @AfterEach void clearTenant() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

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

    @Test void serviceManagerCanManuallyApproveTheirOwnOrdinarySubmission() {
        var started = tx.execute(ignored -> service.start(new SolutionReviewBpmApi.Start(1L, 7L, 9L,
                "SOL_REVIEW:1:" + UUID.randomUUID(), definitionId, 0)));
        assertEquals(Map.of("serviceManagerReview", 7L), started.candidates());
        var pending = currentTask(started.instanceId(), "serviceManagerReview");
        assertEquals("7", pending.getAssignee());
        assertEquals("RUNNING", service.result(1L, started.instanceId()).status());
        approve(pending);
        var completed = engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(pending.getId()).singleResult();
        assertEquals("7", completed.getAssignee());
        assertNotNull(completed.getEndTime());
        assertEquals(BpmTaskStatusEnum.APPROVE.getStatus(), engine.getHistoryService().createHistoricVariableInstanceQuery()
                .taskId(pending.getId()).variableName(TASK_VARIABLE_STATUS).singleResult().getValue());
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).count());
    }

    @Test void existingPersonalMajorInstanceKeepsFrozenEngineeringReviewer() {
        when(permissions.listUsersWithPermission(1L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)).thenReturn(List.of(8L));
        when(permissions.lockAndCheck(1L, 8L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)).thenReturn(true);
        var command = new SolutionReviewBpmApi.Start(1L, 6L, 9L, "SOL_REVIEW:1:" + UUID.randomUUID(), definitionId, 1);
        var candidates = Map.of("serviceManagerReview", 7L, "engineeringManagementReview", 8L);
        var instance = tx.execute(ignored -> guard.starting(new SolutionReviewBpmGuard.StartAuthorization(command, candidates, Map.of()),
                () -> engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definitionId)
                        .tenantId("1").businessKey(command.businessKey()).variables(Map.of("solutionReviewLevel", 1,
                                "solutionReviewCandidates", candidates)).start().getId()));
        var started = new SolutionReviewBpmApi.Started(instance, definitionId, candidates);
        assertEquals(Map.of("serviceManagerReview", 7L, "engineeringManagementReview", 8L), started.candidates());
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        var second = currentTask(started.instanceId(), "engineeringManagementReview");
        assertEquals("8", second.getAssignee());
        assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).count());
        approve(second);
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).count());
        assertEquals(2, engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(started.instanceId()).finished().count());
        login(8L);
        assertNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, command.businessKey())),
                "旧个人审批实例继续沿用既有项目读取契约，不转成角色历史授权");
    }

    @Test void newMajorReviewCannotSelectLegacyPersonalDefinition() {
        when(permissions.listUsersWithPermission(1L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)).thenReturn(List.of());
        var failure = assertThrows(IllegalArgumentException.class, () -> start(1));
        assertTrue(failure.getMessage().contains("角色共享"));
        verify(permissions, never()).listUsersWithPermission(anyLong(), anyString());
    }

    @Test void majorReviewKeepsEngineeringRoleSharedWhenFiveMembersAreEligible() {
        // TF-032: 工程管理部任一有效成员均可复审；此例成员都已具备项目VIEW，
        // 不依赖尚未裁定的无项目VIEW成员访问范围，也不改变旧个人审批历史。
        when(permissions.listUsersWithPermission(1L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION))
                .thenReturn(List.of(8L, 10L, 12L, 14L, 16L));
        when(permissions.lockAndCheck(eq(1L), anyLong(), eq(SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)))
                .thenReturn(true);

        var started = startRole(6L);
        assertFalse(started.candidates().containsKey("engineeringManagementReview"),
                "角色复审不能提前选定或冻结某一成员");
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        var second = currentTask(started.instanceId(), "engineeringManagementReview");
        assertNull(second.getAssignee(), "角色待办在实际成员办理前应保持未领取");
        var candidates = engine.getTaskService().getIdentityLinksForTask(second.getId()).stream()
                .filter(link -> "candidate".equals(link.getType())).toList();
        assertEquals(1, candidates.size(), "只保存一个原生候选组引用，不展开整组人员");
        assertNull(candidates.getFirst().getUserId());
        assertNotNull(candidates.getFirst().getGroupId());
        assertTrue(candidates.getFirst().getGroupId().startsWith("ROLE:"));
        verify(permissions, never()).listUsersWithPermission(anyLong(), anyString());
        verify(roles, never()).inspect(any());
        verify(roles, never()).lockAndInspect(any());
    }

    @Test void roleMemberCanReviewOwnSubmissionWithoutAcquiringGeneralProjectView() {
        var started = startRole(8L);
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        var second = currentTask(started.instanceId(), "engineeringManagementReview");
        login(8L);
        when(scope.resolveCurrent(argThat(query -> query != null && Long.valueOf(8L).equals(query.subjectUserId()))))
                .thenReturn(new ProjectScopeResult(9L, 1L, Set.of(), Set.of()));
        var businessKey = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).singleResult().getBusinessKey();
        assertNotNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey)));
        tx.executeWithoutResult(ignored -> {
            roleTasks.claimForDecision(new SolutionReviewRoleTasks.DecisionActor(1L, 8L, second.getId()));
            engine.getTaskService().setVariableLocal(second.getId(), TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
            // The production BPM completion listener writes this owner status; this isolated engine has no application listener.
            engine.getRuntimeService().setVariable(started.instanceId(), "PROCESS_STATUS", 2);
            engine.getTaskService().complete(second.getId());
        });
        var historyRead = service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey));
        assertNotNull(historyRead, "本人实际复审通过后仍可只读回看本流程");
        assertTrue(historyRead.historical());
        assertEquals(second.getId(), historyRead.taskId());
        assertFalse(roleTasks.canReadTask(new SolutionReviewRoleTasks.DecisionActor(1L, 8L, second.getId())),
                "历史读取不能作为审批写入资格");
        doReturn(List.of()).when(roles).inspectActor(any());
        when(permissions.hasExplicitPermission(1L, 8L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)).thenReturn(false);
        assertNotNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey)), "历史实际办理不依赖当前候选组成员资格");
        assertNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 10L, businessKey)), "不得伪造读取人");
        login(10L);
        assertNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 10L, businessKey)), "同角色未实际办理者不能读取整组历史");
        login(8L);
        TenantContextHolder.setTenantId(2L);
        assertNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(2L, 8L, businessKey)), "相同用户ID不能跨租户读取");
        TenantContextHolder.setTenantId(1L);
        assertEquals("8", engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(second.getId()).singleResult().getAssignee());
        assertEquals(80L, engine.getHistoryService().createHistoricVariableInstanceQuery().taskId(second.getId())
                .variableName("solutionReviewRoleId").singleResult().getValue());
        assertEquals("APPROVE", service.result(1L, started.instanceId()).status());
    }

    @Test void roleReadRequiresCurrentNodeMembershipAndExplicitGrant() {
        var started = startRole(6L);
        var businessKey = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).singleResult().getBusinessKey();
        login(8L);
        assertNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey)), "未轮到复审不得读资料");
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        assertNotNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey)));
        when(permissions.hasExplicitPermission(1L, 8L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)).thenReturn(false);
        assertNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey)));
        when(permissions.hasExplicitPermission(1L, 8L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION)).thenReturn(true);
        doReturn(List.of()).when(roles).inspectActor(any());
        assertNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey)));
        assertNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 10L, businessKey)), "不得伪造读取人");
    }

    @Test void claimedButCanceledRoleTaskDoesNotGrantCompletedDecisionHistory() {
        var started = startRole(6L);
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        var second = currentTask(started.instanceId(), "engineeringManagementReview");
        var businessKey = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).singleResult().getBusinessKey();
        login(8L);
        tx.executeWithoutResult(ignored -> {
            roleTasks.claimForDecision(new SolutionReviewRoleTasks.DecisionActor(1L, 8L, second.getId()));
            engine.getTaskService().setVariableLocal(second.getId(), TASK_VARIABLE_STATUS, BpmTaskStatusEnum.CANCEL.getStatus());
            engine.getRuntimeService().deleteProcessInstance(started.instanceId(), "TEST_ONLY canceled before decision");
        });
        assertNull(service.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey)),
                "候选身份或领取痕迹不能代替本人已完成审批");
    }

    @Test void revokedMembershipCannotClaimOrCompleteAndTaskRemainsPending() {
        var started = startRole(6L);
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        var second = currentTask(started.instanceId(), "engineeringManagementReview");
        login(8L);
        doReturn(List.of()).when(roles).lockAndInspectActor(any());
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(ignored ->
                roleTasks.claimForDecision(new SolutionReviewRoleTasks.DecisionActor(1L, 8L, second.getId()))));
        assertNull(currentTask(started.instanceId(), "engineeringManagementReview").getAssignee());
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(ignored -> {
            engine.getTaskService().setVariableLocal(second.getId(), TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
            engine.getTaskService().complete(second.getId());
        }));
        assertNotNull(currentTask(started.instanceId(), "engineeringManagementReview"));
    }

    @Test void approvalVariablesCannotDowngradeFrozenMajorReviewToOrdinary() {
        var started = startRole(6L);
        var first = currentTask(started.instanceId(), "serviceManagerReview");
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(ignored -> {
            engine.getTaskService().setVariableLocal(first.getId(), TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
            engine.getTaskService().complete(first.getId(), Map.of("solutionReviewLevel", 0));
        }));
        assertEquals(first.getId(), currentTask(started.instanceId(), "serviceManagerReview").getId());
        assertEquals(1, engine.getRuntimeService().getVariable(started.instanceId(), "solutionReviewLevel"));
        approve(first);
        assertNotNull(currentTask(started.instanceId(), "engineeringManagementReview"));
    }

    @Test void callerCannotOverwriteActualReviewerQualificationAudit() {
        var started = startRole(6L);
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        var second = currentTask(started.instanceId(), "engineeringManagementReview");
        login(8L);
        var request = new cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO();
        request.setId(second.getId());
        request.setVariables(Map.of("solutionReviewRoleId", 99L, "solutionReviewMembershipWatermark", 999L));
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(ignored -> {
            roleTasks.claimForDecision(new SolutionReviewRoleTasks.DecisionActor(1L, 8L, second.getId()));
            roleTasks.validateApprovalVariables(request);
        }));
        assertNull(currentTask(started.instanceId(), "engineeringManagementReview").getAssignee());
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(ignored -> {
            roleTasks.claimForDecision(new SolutionReviewRoleTasks.DecisionActor(1L, 8L, second.getId()));
            engine.getTaskService().setVariableLocal(second.getId(), TASK_VARIABLE_STATUS, BpmTaskStatusEnum.APPROVE.getStatus());
            engine.getTaskService().complete(second.getId(), request.getVariables(), true);
        }));
        assertNull(currentTask(started.instanceId(), "engineeringManagementReview").getAssignee());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"membership", "permission", "task"})
    void pendingReadRechecksCommittedRevocationDespiteOuterRepeatableRead(String revoked) throws Exception {
        var started = startRole(6L);
        approve(currentTask(started.instanceId(), "serviceManagerReview"));
        var second = currentTask(started.instanceId(), "engineeringManagementReview");
        var businessKey = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(started.instanceId()).singleResult().getBusinessKey();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(database);
        jdbc.execute("CREATE TABLE IF NOT EXISTS tf032_role_read_fact (actor BIGINT PRIMARY KEY, member_enabled BOOLEAN, permission_enabled BOOLEAN)");
        jdbc.update("MERGE INTO tf032_role_read_fact KEY(actor) VALUES (8,TRUE,TRUE)");
        doAnswer(call -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT member_enabled FROM tf032_role_read_fact WHERE actor=8", Boolean.class))
                ? member(call.getArgument(0)) : List.of()).when(roles).inspectActor(any());
        doAnswer(call -> jdbc.queryForObject("SELECT permission_enabled FROM tf032_role_read_fact WHERE actor=8", Boolean.class))
                .when(permissions).hasExplicitPermission(1L, 8L, SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION);
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactionManager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional = (SolutionReviewBpmApi) factory.getProxy();
        var outer = new TransactionTemplate(transactionManager);
        outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        login(8L);
        outer.executeWithoutResult(ignored -> {
            assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("SELECT member_enabled AND permission_enabled FROM tf032_role_read_fact WHERE actor=8", Boolean.class)));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ACT_RU_TASK WHERE ID_=?", Integer.class, second.getId()));
            assertNotNull(transactional.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey)));
            var concurrent = java.util.concurrent.CompletableFuture.runAsync(() -> {
                TenantContextHolder.setTenantId(1L);
                try {
                    tx.executeWithoutResult(update -> {
                        if ("task".equals(revoked)) engine.getRuntimeService().deleteProcessInstance(started.instanceId(), "TEST_ONLY current todo ended");
                        else jdbc.update("membership".equals(revoked)
                                ? "UPDATE tf032_role_read_fact SET member_enabled=FALSE WHERE actor=8"
                                : "UPDATE tf032_role_read_fact SET permission_enabled=FALSE WHERE actor=8");
                    });
                } finally { TenantContextHolder.clear(); }
            });
            assertDoesNotThrow(() -> concurrent.get(10, java.util.concurrent.TimeUnit.SECONDS));
            // The outer RR snapshot still sees the old facts; the real Spring proxy must use a fresh RC transaction.
            assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("SELECT member_enabled AND permission_enabled FROM tf032_role_read_fact WHERE actor=8", Boolean.class)));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ACT_RU_TASK WHERE ID_=?", Integer.class, second.getId()));
            assertNull(transactional.readAccess(new SolutionReviewBpmApi.ReadQuery(1L, 8L, businessKey)));
        });
    }

    private SolutionReviewBpmApi.Started startRole(Long actor) {
        when(roles.inspectRole(any())).thenReturn(new RoleCandidateFactApi.RoleReference(80L, SolutionReviewBpmApi.ENGINEERING_REVIEW_ROLE));
        when(roles.inspectActor(any())).thenAnswer(call -> member(call.getArgument(0)));
        when(roles.lockAndInspectActor(any())).thenAnswer(call -> member(call.getArgument(0)));
        when(permissions.hasExplicitPermission(eq(1L), anyLong(), eq(SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION))).thenReturn(true);
        when(permissions.lockAndCheck(eq(1L), anyLong(), eq(SolutionReviewBpmApi.ENGINEERING_REVIEW_PERMISSION))).thenReturn(true);
        var deployment = engine.getRepositoryService().createDeployment().tenantId("1")
                .addClasspathResource("bpmn/solutionreview/PMS_DELIVERY_SOLUTION_REVIEW_ROLE.bpmn").deploy();
        var definition = engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
        when(definitions.getProcessDefinitionInfo(definition.getId())).thenReturn(new BpmProcessDefinitionInfoDO()
                .setAutoApprovalType(BpmAutoApproveTypeEnum.NONE.getType()));
        return tx.execute(ignored -> service.start(new SolutionReviewBpmApi.Start(1L, actor, 9L,
                "SOL_REVIEW:1:" + UUID.randomUUID(), definition.getId(), 1)));
    }

    private static List<RoleCandidateFactApi.ActorMembership> member(RoleCandidateFactApi.ActorQuery query) {
        return List.of(8L, 10L, 12L, 14L, 16L).contains(query.actorUserId())
                ? List.of(new RoleCandidateFactApi.ActorMembership(query.actorUserId(), 80L,
                SolutionReviewBpmApi.ENGINEERING_REVIEW_ROLE, 100L, 200L)) : List.of();
    }

    private static void login(Long user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(user).setTenantId(1L).setUserType(2), null));
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
