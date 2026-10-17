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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BpmServiceManagerAccessTest {
    final PermissionApi permissions=mock(PermissionApi.class);
    final HistoryService history=mock(HistoryService.class);
    final org.flowable.engine.history.HistoricProcessInstanceQuery processQuery=mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class,RETURNS_SELF);
    final org.flowable.task.api.history.HistoricTaskInstanceQuery taskHistory=mock(org.flowable.task.api.history.HistoricTaskInstanceQuery.class,RETURNS_SELF);
    final org.flowable.task.api.TaskQuery taskQuery=mock(org.flowable.task.api.TaskQuery.class,RETURNS_SELF);
    final TaskService tasks=mock(TaskService.class);
    final BpmServiceManagerAccess access=new BpmServiceManagerAccess(permissions,history,tasks);
    final Task task=mock(Task.class);
    final HistoricProcessInstance process=mock(HistoricProcessInstance.class);
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(10L).setTenantId(1L).setUserType(2),null));
        when(permissions.hasAnyRoles(10L,"SERVICE_MANAGER")).thenReturn(true);
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
    @Test void otherRolesAreUnchanged(){when(permissions.hasAnyRoles(10L,"SERVICE_MANAGER")).thenReturn(false);assertDoesNotThrow(access::requireAdvancedAction);}
}
