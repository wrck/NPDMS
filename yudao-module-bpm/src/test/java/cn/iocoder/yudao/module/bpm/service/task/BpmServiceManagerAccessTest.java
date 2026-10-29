package cn.iocoder.yudao.module.bpm.service.task;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BpmServiceManagerAccessTest {
    final java.util.Set<String> actorRoles=new java.util.HashSet<>();
    final PermissionApi permissions=mock(PermissionApi.class);
    final HistoryService history=mock(HistoryService.class);
    final org.flowable.engine.history.HistoricProcessInstanceQuery processQuery=mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class,RETURNS_SELF);
    final org.flowable.task.api.history.HistoricTaskInstanceQuery taskHistory=mock(org.flowable.task.api.history.HistoricTaskInstanceQuery.class,RETURNS_SELF);
    final org.flowable.task.api.TaskQuery taskQuery=mock(org.flowable.task.api.TaskQuery.class,RETURNS_SELF);
    final TaskService tasks=mock(TaskService.class);
    final cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi projectScope=mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
    final BpmServiceManagerAccess access=new BpmServiceManagerAccess(permissions,history,tasks,projectScope);
    final Task task=mock(Task.class);
    final HistoricProcessInstance process=mock(HistoricProcessInstance.class);
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(10L).setTenantId(1L).setUserType(2),null));
        actorRoles.add("SERVICE_MANAGER");
        when(permissions.hasAnyRoles(eq(10L),any(String[].class))).thenAnswer(call ->
                java.util.Arrays.stream((String[])call.getRawArguments()[1]).anyMatch(actorRoles::contains));
        when(tasks.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(history.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(process);
        when(history.createHistoricTaskInstanceQuery()).thenReturn(taskHistory);
        when(task.getTenantId()).thenReturn("1");when(task.getAssignee()).thenReturn("10");
        when(task.getProcessInstanceId()).thenReturn("p1");

        when(process.getStartUserId()).thenReturn("20");
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    @Test void assignedTaskCanBeApproved(){assertDoesNotThrow(()->access.requireAssignedTask("t1"));}
    @Test void otherUsersTaskCannotBeApproved(){when(task.getAssignee()).thenReturn("20");assertThrows(ServiceException.class,()->access.requireAssignedTask("t1"));}
    @Test void unassignedTaskCannotBeClaimedByApproval(){when(task.getAssignee()).thenReturn(null);assertThrows(ServiceException.class,()->access.requireAssignedTask("t1"));}
    @Test void crossTenantTaskCannotBeApproved(){when(task.getTenantId()).thenReturn("2");assertThrows(ServiceException.class,()->access.requireAssignedTask("t1"));}
    @Test void unrelatedProcessCannotBeRead(){assertThrows(ServiceException.class,()->access.requireProcess("p1"));}
    @Test void ownStartedProcessCanBeRead(){when(process.getStartUserId()).thenReturn("10");assertDoesNotThrow(()->access.requireProcess("p1"));}
    @Test void participatedProcessCanBeRead(){when(taskHistory.count()).thenReturn(1L);assertDoesNotThrow(()->access.requireProcess("p1"));}
    @Test void missingOrOtherTenantProcessIsDenied(){when(processQuery.singleResult()).thenReturn(null);assertThrows(ServiceException.class,()->access.requireProcess("p2"));}
    @Test void approvalGrantDoesNotGrantAdvancedActions(){assertThrows(ServiceException.class,access::requireAdvancedAction);}
    @Test void existingBpmManagerRetainsAdvancedActions(){when(permissions.hasAnyPermissions(10L,"bpm:task:manager-query")).thenReturn(true);assertDoesNotThrow(access::requireAdvancedAction);verifyNoInteractions(history);}
    @Test void otherRolesAreUnchanged(){actorRoles.clear();assertDoesNotThrow(access::requireAdvancedAction);}
    @Test void projectManagerReadsOnlyVisibleSolutionProcesses(){
        actorRoles.clear();actorRoles.add("PROJECT_MANAGER");
        when(process.getBusinessKey()).thenReturn("SOL_REVIEW:1:42");
        when(process.getProcessVariables()).thenReturn(java.util.Map.of("projectId",9L));
        when(projectScope.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(9L,1L,java.util.Set.of(9L),java.util.Set.of()));
        assertDoesNotThrow(()->access.requireProcess("p1"));
        when(projectScope.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(9L,1L,java.util.Set.of(),java.util.Set.of()));
        assertThrows(ServiceException.class,()->access.requireProcess("p1"));
        assertThrows(ServiceException.class,access::requireAdvancedAction);
    }
    @ParameterizedTest
    @ValueSource(strings={"SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2"})
    void serviceManagerReadsVisibleSolutionProcessWithoutBecomingItsAssignee(String role){
        actorRoles.clear();actorRoles.add(role);
        when(process.getBusinessKey()).thenReturn("SOL_REVIEW:1:42");
        when(process.getProcessVariables()).thenReturn(java.util.Map.of("projectId","9007199254741999"));
        when(projectScope.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(
                9007199254741999L,1L,java.util.Set.of(9007199254741999L),java.util.Set.of()));
        assertDoesNotThrow(()->access.requireProcess("p1"));
        verify(projectScope).resolveCurrent(cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery.builder()
                .tenantId(1L).subjectUserId(10L).anchorProjectId(9007199254741999L)
                .actionCode(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW).build());
        when(task.getAssignee()).thenReturn("20");
        assertThrows(ServiceException.class,()->access.requireAssignedTask("t1"));
        assertThrows(ServiceException.class,access::requireAdvancedAction);
        when(projectScope.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(
                9007199254741999L,1L,java.util.Set.of(),java.util.Set.of()));
        assertThrows(ServiceException.class,()->access.requireProcess("p1"));
    }
    @ParameterizedTest
    @ValueSource(strings={"SERVICE_MANAGER_L1","SERVICE_MANAGER_L2"})
    void serviceManagerAliasesKeepParticipationAndAssignedTaskRestrictions(String role){
        actorRoles.clear();actorRoles.add(role);
        assertThrows(ServiceException.class,()->access.requireProcess("p1"));
        assertThrows(ServiceException.class,access::requireAdvancedAction);
        when(task.getAssignee()).thenReturn("20");
        assertThrows(ServiceException.class,()->access.requireAssignedTask("t1"));
        when(task.getAssignee()).thenReturn("10");
        assertDoesNotThrow(()->access.requireAssignedTask("t1"));
        when(process.getStartUserId()).thenReturn("10");
        assertDoesNotThrow(()->access.requireProcess("p1"));
    }
    @Test void serviceManagerProjectReadExceptionDoesNotApplyToAnotherTenantOrMalformedProject(){
        when(process.getBusinessKey()).thenReturn("SOL_REVIEW:2:42");
        assertThrows(ServiceException.class,()->access.requireProcess("p1"));
        verifyNoInteractions(projectScope);
        when(process.getBusinessKey()).thenReturn("SOL_REVIEW:1:42");
        when(process.getProcessVariables()).thenReturn(java.util.Map.of("projectId","invalid"));
        assertThrows(ServiceException.class,()->access.requireProcess("p1"));
        verifyNoInteractions(projectScope);
    }
}
