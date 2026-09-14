package cn.iocoder.yudao.module.pms.project.service.taskworkbench;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ProjectTaskMaintenanceServiceTest {

    @Test
    void eventFailureRollsBackSourceWriteAndSuccessfulRetryCommits() {
        when(tasks.incrementTaskVersionIfMatch(any())).thenReturn(1);
        // Unique in-memory ledger: proves Spring transaction boundaries, not production Mapper SQL.
        var database = new org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(database);
            jdbc.execute("CREATE TABLE write_ledger (id INT PRIMARY KEY)");
            doAnswer(call -> jdbc.update("INSERT INTO write_ledger VALUES (1)"))
                    .when(maintenance).insertResponsible(any());
            var proxy = new org.springframework.aop.framework.ProxyFactory(service);
            proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(database),
                    new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
            var command = (ProjectTaskMaintenanceService) proxy.getProxy();
            org.mockito.Mockito.doThrow(new IllegalStateException("outbox unavailable"))
                    .when(ruleEvents).append(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), any());
            assertThrows(IllegalStateException.class, () -> command.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE, 11L), actor));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM write_ledger", Integer.class));
            org.mockito.Mockito.doNothing().when(ruleEvents).append(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), any());
            command.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE, 11L), actor);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM write_ledger", Integer.class));
        } finally {
            database.shutdown();
        }
    }
    final ProjectTaskRuntimeMapper tasks = mock(ProjectTaskRuntimeMapper.class);
    final ProjectTaskMaintenanceMapper maintenance = mock(ProjectTaskMaintenanceMapper.class);
    final ProjectTaskAssignmentMapper assignments = mock(ProjectTaskAssignmentMapper.class);
    final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    final ProjectTaskAssignmentService support = mock(ProjectTaskAssignmentService.class);
    final ProjectTaskQueryService queries = mock(ProjectTaskQueryService.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final AdminUserApi users = mock(AdminUserApi.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final PlatformBusinessEventApi ruleEvents = mock(PlatformBusinessEventApi.class);
    final TaskWorkbenchActor actor = new TaskWorkbenchActor(1L, 9L, "maintenance-test");
    final ProjectTaskMaintenanceService service = new ProjectTaskMaintenanceService(tasks, maintenance, assignments, members, support, queries, permissions, users, commands, ruleEvents);
    ProjectTaskInstanceDO task; ProjectMasterDO project;

    @Test void maintenanceReadUsesOneScopeCheckAndPreservesHistoryAndActionGrants() {
        when(queries.getMaintenanceAccess(3L, actor))
                .thenReturn(new ProjectTaskQueryService.TaskMaintenanceAccess(4, true, false));
        var result = service.get(3L, actor);
        assertEquals(4, result.version());
        assertTrue(result.canAssign()); assertFalse(result.canEdit());
        verify(queries).getMaintenanceAccess(3L, actor);
        verify(queries, never()).getWorkbench(any(), any());
        verify(queries, never()).getTask(any(), any());
        verify(maintenance).selectHistory(any());
        verify(maintenance).selectExecutorHistory(any());
        when(queries.getMaintenanceAccess(3L, actor)).thenThrow(new IllegalStateException("scope denied"));
        assertThrows(RuntimeException.class, () -> service.get(3L, actor));
        verify(maintenance, times(1)).selectDescription(any());
    }
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(1L);
        task = new ProjectTaskInstanceDO().setId(3L).setProjectId(2L).setStatus("PENDING_ASSIGN").setVersion(0); task.setTenantId(1L);
        project = new ProjectMasterDO(); project.setId(2L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE"); project.setTaskTreeVersion(4L);
        when(tasks.selectTask(any())).thenReturn(task); when(tasks.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(tasks.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(permissions.hasAnyPermissions(eq(9L), any())).thenReturn(true);
        var member = new ProjectMemberAssignmentDO(); member.setTenantId(1L); member.setUserId(11L);
        when(members.selectActiveForAssignmentState(any())).thenReturn(List.of(member));
        var user = new AdminUserRespDTO(); user.setId(11L); user.setNickname("成员"); user.setStatus(0);
        when(users.getUserList(any())).thenReturn(List.of(user));
        doAnswer(call -> {
            TaskCommandResult result = ((Supplier<TaskCommandResult>)call.getArgument(3)).get();
            ((Function<TaskCommandResult, PlatformCommandExecutionApi.SuccessFacts>)call.getArgument(4)).apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        }).when(commands).execute(any(), anyString(), eq(TaskCommandResult.class), any(), any());
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private ProjectTaskMaintenanceService.RoleChange role(ProjectTaskMaintenanceService.Role role, Long id) {
        return new ProjectTaskMaintenanceService.RoleChange(3L,0,role,id,"职责调整","key");
    }
    @Test void responsibleDoesNotAssignExecutorOrChangeTaskStatus() {
        when(maintenance.insertResponsible(any())).thenReturn(1); when(tasks.incrementTaskVersionIfMatch(any())).thenReturn(1);
        var result = service.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE,11L),actor);
        assertEquals("PENDING_ASSIGN",result.status()); assertEquals(1,result.taskVersion());
        verifyNoInteractions(assignments); verify(tasks,never()).assignTaskIfMatch(any());
        verify(ruleEvents).append(eq("Project"), eq("2"), argThat(event ->
                event.eventType().equals(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleReevaluation.EVENT_TYPE)));
    }
    @Test void executorRetainsExistingAssignmentStateTransitionAndEventFactory() {
        when(assignments.insertAssignment(any())).thenReturn(1); when(tasks.assignTaskIfMatch(any())).thenReturn(1);
        when(support.assignmentAuditDetail(any(),any(),any(),any())).thenReturn(Map.of());
        var result = service.changeRole(role(ProjectTaskMaintenanceService.Role.EXECUTOR,11L),actor);
        assertEquals("PENDING_START",result.status()); verify(maintenance,never()).insertResponsible(any());
        verify(support).assignmentFacts(eq(result),eq(actor),any());
    }
    @Test void nonMembersAndLostPermissionCannotChangeResponsibility() {
        assertThrows(RuntimeException.class,()->service.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE,12L),actor));
        verify(maintenance,never()).insertResponsible(any());
        when(permissions.hasAnyPermissions(eq(9L),any())).thenReturn(false);
        assertThrows(RuntimeException.class,()->service.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE,11L),actor));
    }
    @Test void wrongTenantAndStaleTaskVersionAreRejected() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class,()->service.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE,11L),actor));
        TenantContextHolder.setTenantId(1L); task.setVersion(2);
        assertThrows(RuntimeException.class,()->service.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE,11L),actor));
        verify(maintenance,never()).insertResponsible(any());
    }
    @Test void previousResponsibleIntervalIsClosedAndPreserved() {
        var old = new TaskResponsibleRow(); old.setId(20L); old.setUserId(10L); old.setVersion(0);
        when(maintenance.selectCurrentForUpdate(any())).thenReturn(old); when(maintenance.closeResponsible(any())).thenReturn(1);
        when(maintenance.insertResponsible(any())).thenReturn(1); when(tasks.incrementTaskVersionIfMatch(any())).thenReturn(1);
        service.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE,11L),actor);
        verify(maintenance).closeResponsible(argThat(query->query.id().equals(20L)));
        verify(maintenance).insertResponsible(argThat(row->row.getUserId().equals(11L)));
        assertEquals(10L,old.getUserId());
    }
    @Test void failedVersionWriteDoesNotReportSuccessOrAppendEvent() {
        when(maintenance.insertResponsible(any())).thenReturn(1); when(tasks.incrementTaskVersionIfMatch(any())).thenReturn(0);
        assertThrows(RuntimeException.class,()->service.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE,11L),actor));
        verifyNoInteractions(ruleEvents);
    }

    @Test void replayDoesNotRepeatResponsibilityWriteOrReevaluationEvent() {
        var saved = new TaskCommandResult(3L, 1, 4L, "PENDING_ASSIGN", "NEW");
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, saved))
                .when(commands).execute(any(), anyString(), eq(TaskCommandResult.class), any(), any());
        assertEquals(saved, service.changeRole(role(ProjectTaskMaintenanceService.Role.RESPONSIBLE, 11L), actor));
        verifyNoInteractions(maintenance, ruleEvents);
    }
}
