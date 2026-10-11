package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanReworkActivationServiceTest {
    final ProjectPlanDraftService drafts = mock(ProjectPlanDraftService.class);
    final ProjectReworkService reworks = mock(ProjectReworkService.class);
    final ProjectPlanActivationService activation = mock(ProjectPlanActivationService.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectPlanReworkActivationService service = new ProjectPlanReworkActivationService(drafts, reworks, activation, commands, permissions);
    ProjectPlanDraftService.Preview before;
    ProjectPlanDraftService.Preview after;
    ProjectReworkService.Apply rework;
    final ProjectReworkService.Result reworked = new ProjectReworkService.Result(9L, 51L, 5L,
            List.of(new ProjectReworkService.NewExecution("task:PRE_CONTACT", 71L, 72L, 2)));
    final ProjectPlanActivationService.Applied applied = new ProjectPlanActivationService.Applied(9L, 52L, 2, 6L);

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        before = preview(4L, List.of(new Issue("nodes.task:PRE_CONTACT.binding", "STARTED_BINDING_IMMUTABLE", "已开始工作")),
                List.of(change("task:PRE_CONTACT", "UPDATE")));
        after = preview(5L, List.of(), before.changes());
        rework = new ProjectReworkService.Apply(9L, 51L, 4L, List.of("task:PRE_CONTACT"),
                List.of(new ProjectReworkService.ExpectedExecution("task:PRE_CONTACT", 71L, 3)), "修复联系人绑定，保留原完成结果");
        when(drafts.authorize(9L, 1L)).thenReturn(new ProjectPlanScopeQuery(7L, 9L));
        when(permissions.hasAnyPermissions(1L, ProjectReworkService.PERMISSION)).thenReturn(true);
        prepare(before);
        when(drafts.preview(9L, 52L, 3L, 1L)).thenReturn(after);
        when(reworks.apply(eq(rework), eq(1L), anyString())).thenReturn(reworked);
        when(activation.apply(eq(new ProjectPlanActivationService.Apply(9L, 52L, after)), eq(1L), anyString())).thenReturn(applied);
        when(commands.execute(any(), anyString(), eq(ProjectPlanReworkActivationService.Applied.class), any(), any())).thenAnswer(call ->
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        ((Supplier<ProjectPlanReworkActivationService.Applied>) call.getArgument(3)).get()));
    }

    @Test void repairsTheNewRoundThroughTheExistingServicesInOrder() {
        assertEquals(new ProjectPlanReworkActivationService.Applied(applied, reworked), apply(service));
        var order = inOrder(drafts, reworks, activation);
        order.verify(drafts).authorize(9L, 1L);
        order.verify(drafts).prepare(9L, 52L, 3L, 1L, true);
        order.verify(reworks).apply(eq(rework), eq(1L), argThat(key -> key.startsWith("plan-rework:") && key.length() <= 128));
        order.verify(drafts).preview(9L, 52L, 3L, 1L);
        order.verify(activation).apply(eq(new ProjectPlanActivationService.Apply(9L, 52L, after)), eq(1L), anyString());
    }

    @Test void staleRuntimePreviewRejectsBeforeRework() {
        prepare(preview(5L, before.issues(), before.changes()));
        assertThrows(RuntimeException.class, () -> apply(service));
        verifyNoInteractions(reworks, activation);
    }

    @Test void reworkMustUseTheObservedPlanAndProjectVersion() {
        rework = new ProjectReworkService.Apply(9L, 50L, 4L, rework.selectedNodeKeys(), rework.expectedExecutions(), rework.reason());
        assertThrows(RuntimeException.class, () -> apply(service));
        verifyNoInteractions(reworks, activation);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"OWNER_FACT_REQUIRED", "INVALID_RULE", "RULE_NATIVE_BINDING_UNAVAILABLE"})
    void compilerIssuesCannotBeBypassedByEchoingThem(String code) {
        before = preview(4L, List.of(new Issue("tasks[0].completionRuleKey", code, "编译失败")), before.changes());
        prepare(before);
        assertThrows(RuntimeException.class, () -> apply(service));
        verifyNoInteractions(reworks, activation);
    }

    @Test void unselectedBindingAndUnrelatedPlanChangesRejectBeforeRework() {
        before = preview(4L, List.of(new Issue("nodes.task:OTHER.binding", "STARTED_BINDING_IMMUTABLE", "其他任务")), before.changes());
        prepare(before);
        assertThrows(RuntimeException.class, () -> apply(service));
        before = preview(4L, List.of(), List.of(change("task:PRE_CONTACT", "UPDATE"), change("task:OTHER", "UPDATE")));
        prepare(before);
        assertThrows(RuntimeException.class, () -> apply(service));
        verifyNoInteractions(reworks, activation);
    }

    @Test void failedPostReworkValidationCannotActivateThePlan() {
        when(drafts.preview(9L, 52L, 3L, 1L)).thenReturn(preview(5L, before.issues(), before.changes()));
        assertThrows(RuntimeException.class, () -> apply(service));
        verifyNoInteractions(activation);
    }

    @Test void completedReplayDoesNotCreateAnotherRoundAndStillChecksBothPermissions() {
        var saved = new ProjectPlanReworkActivationService.Applied(applied, reworked);
        when(commands.execute(any(), anyString(), eq(ProjectPlanReworkActivationService.Applied.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, saved));
        assertEquals(saved, apply(service));
        verify(drafts, never()).prepare(any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(reworks, activation);
        when(permissions.hasAnyPermissions(1L, ProjectReworkService.PERMISSION)).thenReturn(false);
        assertThrows(RuntimeException.class, () -> apply(service));
        verify(commands, times(1)).execute(any(), anyString(), eq(ProjectPlanReworkActivationService.Applied.class), any(), any());
    }

    @Test void differentProjectCannotReserveACommand() {
        var foreign = new ProjectReworkService.Apply(10L, 51L, 4L, rework.selectedNodeKeys(), rework.expectedExecutions(), rework.reason());
        assertThrows(RuntimeException.class, () -> service.apply(new ProjectPlanReworkActivationService.Apply(9L, 52L, before, foreign), 1L, "repair"));
        verifyNoInteractions(commands, reworks, activation);
    }

    @Test void activationFailureRollsBackReworkAndSuppressesAfterCommitDispatch() {
        var fixture = transactionFixture(true);
        assertThrows(IllegalStateException.class, () -> apply(fixture.proxy()));
        assertEquals(1, fixture.jdbc().queryForObject("select round_no from repair_state", Integer.class));
        assertEquals(51L, fixture.jdbc().queryForObject("select plan_id from repair_state", Long.class));
        assertEquals(0, fixture.dispatched().get());
    }

    @Test void bothAfterCommitEventsObserveTheRepairedPlanAndNewRound() {
        var fixture = transactionFixture(false);
        apply(fixture.proxy());
        assertEquals(2, fixture.jdbc().queryForObject("select round_no from repair_state", Integer.class));
        assertEquals(52L, fixture.jdbc().queryForObject("select plan_id from repair_state", Long.class));
        assertEquals(2, fixture.dispatched().get());
    }

    private record TransactionFixture(ProjectPlanReworkActivationService proxy, JdbcTemplate jdbc, AtomicInteger dispatched) { }
    private TransactionFixture transactionFixture(boolean failActivation) {
        // Isolated H2 tests the actual Spring transaction advice, not the live project's data.
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:repair_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table repair_state (round_no int, plan_id bigint)");
        jdbc.update("insert into repair_state values (1,51)");
        var dispatched = new AtomicInteger();
        Runnable registerEvent = () -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    assertEquals(52L, jdbc.queryForObject("select plan_id from repair_state", Long.class));
                    assertEquals(2, jdbc.queryForObject("select round_no from repair_state", Integer.class));
                    dispatched.incrementAndGet();
                }
            });
        };
        when(reworks.apply(eq(rework), eq(1L), anyString())).thenAnswer(call -> {
            jdbc.update("update repair_state set round_no=2");
            registerEvent.run();
            assertEquals(0, dispatched.get());
            return reworked;
        });
        when(activation.apply(eq(new ProjectPlanActivationService.Apply(9L, 52L, after)), eq(1L), anyString())).thenAnswer(call -> {
            jdbc.update("update repair_state set plan_id=52");
            registerEvent.run();
            assertEquals(0, dispatched.get());
            if (failActivation) throw new IllegalStateException("PLAN_VERSION_CONFLICT");
            return applied;
        });
        var factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        return new TransactionFixture((ProjectPlanReworkActivationService) factory.getProxy(), jdbc, dispatched);
    }

    private ProjectPlanReworkActivationService.Applied apply(ProjectPlanReworkActivationService target) {
        return target.apply(new ProjectPlanReworkActivationService.Apply(9L, 52L, before, rework), 1L, "repair");
    }
    private ProjectPlanDraftService.Preview preview(long projectVersion, List<Issue> issues, List<ProjectPlanImpactAnalyzer.Change> changes) {
        return new ProjectPlanDraftService.Preview(51L, 52L, 3L, projectVersion, changes, List.of("CONTACT_REPAIR"),
                List.of(), List.of(), List.of(), List.of(), issues);
    }
    private ProjectPlanImpactAnalyzer.Change change(String node, String action) {
        return new ProjectPlanImpactAnalyzer.Change(node, "TASK", "联系人", action, true, true, List.of());
    }
    private void prepare(ProjectPlanDraftService.Preview preview) {
        var prepared = mock(ProjectPlanDraftService.Prepared.class);
        when(prepared.preview()).thenReturn(preview);
        when(drafts.prepare(9L, 52L, 3L, 1L, true)).thenReturn(prepared);
    }
}
