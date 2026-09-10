package cn.iocoder.yudao.module.bpm.service.normalclosure;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi;
import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureResultEvent;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmProcessDefinitionInfoMapper;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmActivityBehaviorFactory;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.other.BpmTaskCandidateExpressionStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.event.BpmProcessInstanceEventPublisher;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmProcessInstanceEventListener;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmTaskEventListener;
import cn.iocoder.yudao.module.bpm.service.comment.BpmCommentServiceImpl;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelServiceImpl;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionServiceImpl;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceServiceImpl;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskServiceImpl;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureApi.PROCESS_DEFINITION_KEY;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Actual Flowable + H2, Spring transactions, upstream create/deploy/approve/reject and upstream listeners.
 * Only external System/message APIs and the definition metadata persistence adapter are test doubles.
 * No runtime/history/task/approval result is mocked; production createProcessDefinition performs registration.
 */
class BpmNormalClosureFlowableTest {
    private ProcessEngine engine;
    private GenericApplicationContext context;
    private TransactionTemplate tx;
    private BpmNormalClosureApi api;
    private BpmTaskServiceImpl tasks;
    private BpmProcessInstanceServiceImpl instances;
    private BpmNormalClosureDeploymentService deployment;
    private BpmNormalClosureResultEventListener bridge;
    private final List<BpmNormalClosureApi.Result> delivered = new ArrayList<>();
    private final Map<String, BpmProcessDefinitionInfoDO> metadata = new HashMap<>();
    private boolean failConsumer;
    private boolean explicitGrant = true;
    private final Set<Long> disabled = new HashSet<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:normal_" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        var transactionManager = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(transactionManager);
        AdminUserApi users = mock(AdminUserApi.class);
        when(users.getUser(anyLong())).thenAnswer(inv -> user(inv.getArgument(0)));
        when(users.getUserList(anyCollection())).thenAnswer(inv -> ((Collection<Long>) inv.getArgument(0))
                .stream().map(this::user).toList());
        when(users.getUserMap(anyCollection())).thenCallRealMethod();
        doAnswer(inv -> {
            for (Long id : (Collection<Long>) inv.getArgument(0)) {
                if (id == null || id <= 0 || disabled.contains(id)) {
                    throw new IllegalArgumentException("Unavailable tenant user");
                }
            }
            return null;
        }).when(users).validateUserList(anyCollection());
        doCallRealMethod().when(users).validateUser(anyLong());
        var permissions = mock(ExplicitPermissionApi.class);
        when(permissions.lockAndCheck(eq(7L), anyLong(), eq(BpmNormalClosureService.MATERIAL_PERMISSION)))
                .thenAnswer(inv -> explicitGrant);
        var department = mock(DeptApi.class);
        when(department.getDeptMap(anyCollection())).thenReturn(Map.of());
        var messages = mock(BpmMessageService.class);
        var candidates = new BpmTaskCandidateInvoker(List.of(new BpmTaskCandidateExpressionStrategy()), users);
        var behavior = new BpmActivityBehaviorFactory();
        behavior.setTaskCandidateInvoker(candidates);
        var processListener = new BpmProcessInstanceEventListener();
        var taskListener = new BpmTaskEventListener();
        Map<Object, Object> expressionBeans = new HashMap<>();
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(dataSource);
        config.setTransactionManager(transactionManager);
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        config.setHistoryLevel(HistoryLevel.FULL);
        config.setAsyncExecutorActivate(false);
        config.setActivityBehaviorFactory(behavior);
        config.setBeans(expressionBeans);
        config.setEventListeners(List.of(processListener, taskListener));
        engine = config.buildProcessEngine();

        var definitions = new BpmProcessDefinitionServiceImpl();
        var mapper = mock(BpmProcessDefinitionInfoMapper.class);
        when(mapper.insert(any(BpmProcessDefinitionInfoDO.class))).thenAnswer(inv -> {
            BpmProcessDefinitionInfoDO info = inv.getArgument(0);
            metadata.put(info.getProcessDefinitionId(), info);
            return 1;
        });
        when(mapper.selectByProcessDefinitionId(anyString())).thenAnswer(inv -> metadata.get(inv.getArgument(0)));
        wire(definitions, "repositoryService", engine.getRepositoryService(), "processDefinitionMapper", mapper,
                "adminUserApi", users);
        var models = new BpmModelServiceImpl();
        wire(models, "repositoryService", engine.getRepositoryService(), "processDefinitionService", definitions,
                "taskCandidateInvoker", candidates);
        instances = new BpmProcessInstanceServiceImpl();
        tasks = new BpmTaskServiceImpl();
        var comments = new BpmCommentServiceImpl();
        wire(comments, "taskService", engine.getTaskService(), "bpmTaskService", tasks);
        wire(tasks, "taskService", engine.getTaskService(), "historyService", engine.getHistoryService(),
                "runtimeService", engine.getRuntimeService(), "managementService", engine.getManagementService(),
                "processInstanceService", instances, "bpmProcessDefinitionService", definitions,
                "modelService", models, "commentService", comments, "messageService", messages, "adminUserApi", users);
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof BpmProcessInstanceStatusEvent statusEvent) {
                bridge.onApplicationEvent(statusEvent);
            } else if (event instanceof BpmNormalClosureResultEvent resultEvent) {
                // A consumer re-reads history in the same, not-yet-committed approval transaction.
                delivered.add(api.inspectResult(resultEvent.tenantId(), resultEvent.processInstanceId()));
                if (failConsumer) {
                    throw new IllegalStateException("Owner revalidation rejected closure");
                }
            }
        };
        wire(instances, "runtimeService", engine.getRuntimeService(), "historyService", engine.getHistoryService(),
                "processDefinitionService", definitions, "taskService", tasks, "messageService", messages,
                "adminUserApi", users, "deptApi", department, "taskCandidateInvoker", candidates,
                "processInstanceEventPublisher", new BpmProcessInstanceEventPublisher(publisher));
        wire(processListener, "processInstanceService", instances);
        wire(taskListener, "taskService", tasks, "modelService", models);
        var guard = new BpmNormalClosureGuard(engine.getRuntimeService(), users, permissions);
        expressionBeans.put("bpmNormalClosureGuard", guard);
        var target = new BpmNormalClosureService(engine.getRepositoryService(), engine.getRuntimeService(),
                engine.getHistoryService(), definitions, instances, users, permissions, guard);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        api = (BpmNormalClosureApi) proxy.getProxy();
        bridge = new BpmNormalClosureResultEventListener(publisher, api);
        deployment = new BpmNormalClosureDeploymentService(models, users, api);
        context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("bpmProcessInstanceService", instances);
        context.getBeanFactory().registerSingleton("bpmTaskService", tasks);
        context.getBeanFactory().registerSingleton("managementService", engine.getManagementService());
        context.refresh();
        SpringUtil springUtil = new SpringUtil();
        springUtil.postProcessBeanFactory(context.getBeanFactory());
        springUtil.setApplicationContext(context);
        tx.executeWithoutResult(s -> deployment.deploy(7L, 11L, "normal", "/test/closure/create", "/test/closure/view"));
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
        if (context != null) {
            context.close();
        }
        TenantContextHolder.clear();
    }

    @Test
    void requiresTwoActualManualApprovalsAndReturnsImmutableRepeatableHistory() {
        var started = start("closure:101", 12L, 13L);
        assertEquals(2, started.nodes().size());
        assertTrue(metadata.containsKey(started.actualDefinitionId()));
        assertEquals("RUNNING", result(started).status());
        assertTrue(result(started).reviews().isEmpty());
        assertEquals("serviceManagerReview", current(started).getTaskDefinitionKey());
        assertEquals("12", current(started).getAssignee());
        approve(started, 12L, "服务经理核验", null);
        assertEquals("RUNNING", result(started).status());
        assertEquals(1, result(started).reviews().size());
        assertTrue(delivered.isEmpty());
        assertEquals("materialReview", current(started).getTaskDefinitionKey());
        assertEquals("13", current(started).getAssignee());
        approve(started, 13L, "材料齐全", null);
        var result = result(started);
        assertEquals("APPROVE", result.status());
        assertEquals("closure:101", result.businessKey());
        assertEquals(started.actualDefinitionId(), result.actualDefinitionId());
        assertEquals(List.of("服务经理核验", "材料齐全"), result.reviews().stream().map(BpmNormalClosureApi.Review::reason).toList());
        assertTrue(result.reviews().stream().allMatch(r -> r.taskId() != null && r.completedAt() != null));
        assertEquals(result, result(started));
        assertEquals(List.of(result), delivered);
        assertThrows(UnsupportedOperationException.class, () -> result.reviews().clear());
        assertThrows(IllegalArgumentException.class, () -> start("closure:101", 12L, 13L));
    }

    @Test
    void samePersonAndStarterAreNeverAutomaticallyApproved() {
        var started = start("closure:same", 11L, 11L);
        assertEquals("serviceManagerReview", current(started).getTaskDefinitionKey());
        approve(started, 11L, "第一审核", null);
        assertEquals("materialReview", current(started).getTaskDefinitionKey());
        assertEquals("RUNNING", result(started).status());
        approve(started, 11L, "第二审核", null);
        assertEquals("APPROVE", result(started).status());
    }

    @Test
    void realRejectEndsAttemptWithoutCreatingNextTask() {
        var started = start("closure:reject", 12L, 13L);
        tx.executeWithoutResult(s -> tasks.rejectTask(12L, new BpmTaskRejectReqVO()
                .setId(current(started).getId()).setReason("材料不满足")));
        assertNull(current(started));
        assertEquals("REJECT", result(started).status());
        assertEquals("REJECT", result(started).reviews().getFirst().decision());
        assertEquals("材料不满足", result(started).reviews().getFirst().reason());
        assertEquals(result(started), delivered.getFirst());
    }

    @Test
    void secondNodeRejectionPreservesBothActualReviews() {
        var started = start("closure:second-reject", 12L, 13L);
        approve(started, 12L, "经理同意", null);
        tx.executeWithoutResult(s -> tasks.rejectTask(13L, new BpmTaskRejectReqVO()
                .setId(current(started).getId()).setReason("材料退回")));
        assertEquals("REJECT", result(started).status());
        assertEquals(List.of("APPROVE", "REJECT"), result(started).reviews().stream()
                .map(BpmNormalClosureApi.Review::decision).toList());
        assertNull(current(started));
    }

    @Test
    void cancellationIsNeverApprovalAndPublishesActualCancelledHistory() {
        var started = start("closure:cancel", 12L, 13L);
        assertThrows(RuntimeException.class, () -> cancel(started, 99L));
        cancel(started, 11L);
        assertEquals("CANCEL", result(started).status());
        assertEquals("CANCEL", result(started).reviews().getFirst().decision());
        assertEquals(result(started), delivered.getFirst());
    }

    @Test
    void callerBusinessRollbackAlsoRemovesStartedProcessAndFrozenCandidates() {
        String[] instanceId = new String[1];
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            instanceId[0] = api.start(command("closure:start-rollback", 12L, 13L)).instanceId();
            throw new IllegalStateException("Caller business write failed");
        }));
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanceId[0]).count());
        assertEquals(0, engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(instanceId[0]).count());
        assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(instanceId[0]).count());
    }

    @Test
    void wrongAssigneeAndCandidateVariableTamperingRollBackApproval() {
        var started = start("closure:tamper", 12L, 13L);
        String taskId = current(started).getId();
        var originalComments = engine.getTaskService().getProcessInstanceComments(started.instanceId()).stream()
                .map(org.flowable.engine.task.Comment::getId).toList();
        assertThrows(RuntimeException.class, () -> approve(started, 99L, "越权", null));
        assertThrows(RuntimeException.class, () -> approve(started, 12L, "篡改候选", Map.of("materialReviewerUserId", 99L)));
        assertEquals(taskId, current(started).getId());
        assertTrue(result(started).reviews().isEmpty());
        assertEquals(13L, engine.getRuntimeService().getVariable(started.instanceId(), "materialReviewerUserId"));
        assertEquals(originalComments, engine.getTaskService().getProcessInstanceComments(started.instanceId()).stream()
                .map(org.flowable.engine.task.Comment::getId).toList());
    }

    @Test
    void revokedExplicitMaterialGrantRollsBackFinalApproval() {
        var started = start("closure:revoked", 12L, 13L);
        approve(started, 12L, "审核", null);
        explicitGrant = false;
        assertThrows(RuntimeException.class, () -> approve(started, 13L, "审核", null));
        assertEquals("materialReview", current(started).getTaskDefinitionKey());
        assertEquals("RUNNING", result(started).status());
        assertEquals(1, result(started).reviews().size());
    }

    @Test
    void ownerFailureInBeforeCommitRollsBackTaskProcessAndHistoryTogether() {
        var started = start("closure:rollback", 12L, 13L);
        approve(started, 12L, "通过", null);
        String taskId = current(started).getId();
        failConsumer = true;
        assertThrows(RuntimeException.class, () -> approve(started, 13L, "待回滚意见", null));
        assertEquals("APPROVE", delivered.getFirst().status()); // real history was visible before rollback
        assertEquals(taskId, current(started).getId());
        assertEquals("RUNNING", result(started).status());
        assertEquals(1, result(started).reviews().size());
        failConsumer = false;
        delivered.clear();
        approve(started, 13L, "重新审核", null);
        assertEquals("APPROVE", result(started).status());
    }

    @Test
    void failedFinalRevalidationCanBeCancelledByApplicantAndReappliedWithNewBusinessKey() {
        var old = start("closure:stale", 12L, 13L);
        approve(old, 12L, "经理通过", null);
        failConsumer = true;
        assertThrows(RuntimeException.class, () -> approve(old, 13L, "材料通过但事实已变化", null));
        assertEquals("RUNNING", result(old).status());
        failConsumer = false;
        delivered.clear();
        cancel(old, 11L);
        var cancelled = result(old);
        assertEquals("CANCEL", cancelled.status());
        var fresh = start("closure:fresh-snapshot", 12L, 13L);
        assertNotEquals(old.instanceId(), fresh.instanceId());
        approve(fresh, 12L, "新快照经理通过", null);
        approve(fresh, 13L, "新快照材料通过", null);
        assertEquals("APPROVE", result(fresh).status());
        assertEquals(cancelled, result(old));
    }

    @Test
    void tenantWrongBusinessKeyEmptyCandidatesAndMissingTransactionAreRejected() {
        var started = start("closure:tenant", 12L, 13L);
        assertThrows(IllegalArgumentException.class, () -> api.inspectResult(8L, started.instanceId()));
        TenantContextHolder.setTenantId(8L);
        assertThrows(IllegalArgumentException.class, () -> api.inspectResult(8L, started.instanceId()));
        TenantContextHolder.setTenantId(7L);
        assertNotEquals("another-closure", result(started).businessKey()); // caller compares, never supplies result facts
        assertThrows(IllegalArgumentException.class, () -> start("", 12L, 13L));
        assertThrows(IllegalArgumentException.class, () -> start("closure:empty", null, 13L));
        assertThrows(IllegalTransactionStateException.class, () -> api.start(command("closure:no-tx", 12L, 13L)));
        disabled.add(13L);
        assertThrows(IllegalArgumentException.class, () -> start("closure:disabled", 12L, 13L));
    }

    @Test
    void directGenericEngineStartAndRequestSuppliedSuccessAreNotBusinessApproval() {
        var definition = api.inspectDefinition(7L, PROCESS_DEFINITION_KEY);
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> engine.getRuntimeService()
                .startProcessInstanceById(definition.actualDefinitionId(), "closure:bypass", Map.of(
                        "serviceManagerUserId", 12L, "materialReviewerUserId", 13L, PROCESS_INSTANCE_VARIABLE_STATUS, 2))));
        var started = start("closure:status", 12L, 13L);
        tx.executeWithoutResult(s -> engine.getRuntimeService().setVariable(started.instanceId(), PROCESS_INSTANCE_VARIABLE_STATUS, 2));
        assertEquals("RUNNING", result(started).status());
        assertTrue(result(started).reviews().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> api.inspectDefinition(7L, "caller-xml"));
    }

    private BpmNormalClosureApi.Started start(String key, Long manager, Long reviewer) {
        return tx.execute(s -> api.start(command(key, manager, reviewer)));
    }

    private BpmNormalClosureApi.StartCommand command(String key, Long manager, Long reviewer) {
        return new BpmNormalClosureApi.StartCommand(7L, 11L, key, PROCESS_DEFINITION_KEY, manager, reviewer, 101L);
    }

    private Task current(BpmNormalClosureApi.Started started) {
        return engine.getTaskService().createTaskQuery().processInstanceId(started.instanceId()).singleResult();
    }

    private BpmNormalClosureApi.Result result(BpmNormalClosureApi.Started started) {
        return api.inspectResult(7L, started.instanceId());
    }

    private void approve(BpmNormalClosureApi.Started started, Long user, String reason, Map<String, Object> variables) {
        tx.executeWithoutResult(s -> tasks.approveTask(user, new BpmTaskApproveReqVO().setId(current(started).getId())
                .setReason(reason).setVariables(variables)));
    }

    private void cancel(BpmNormalClosureApi.Started started, Long userId) {
        tx.executeWithoutResult(s -> instances.cancelProcessInstanceByStartUser(userId,
                new BpmProcessInstanceCancelReqVO().setId(started.instanceId()).setReason("事实变化，取消后重新校验")));
    }

    private AdminUserRespDTO user(Long id) {
        return new AdminUserRespDTO().setId(id).setNickname("用户" + id).setStatus(disabled.contains(id) ? 1 : 0);
    }

    private static void wire(Object target, Object... fields) {
        for (int i = 0; i < fields.length; i += 2) {
            ReflectionTestUtils.setField(target, (String) fields[i], fields[i + 1]);
        }
    }
}
