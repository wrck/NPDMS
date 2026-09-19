package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectResultSubscriptionTaskHostTest {
    private final PermissionCommonApi permissions=mock(PermissionCommonApi.class);
    private final ProjectTreeScopeService scopes=mock(ProjectTreeScopeService.class);
    private final TaskStateMachineMapper transitions=mock(TaskStateMachineMapper.class);
    private final ProjectMasterDO project=new ProjectMasterDO();
    private final ProjectTaskInstanceDO task=new ProjectTaskInstanceDO();
    private ProjectTaskExecutionContractDO contract;
    private TaskNativeBindingHostProvider host;
    @BeforeEach void setUp(){
        contract=ResultSubscriptionTaskFixture.contract(ResultSubscriptionTaskFixture.snapshot());
        var tasks=mock(ProjectTaskRuntimeMapper.class);var contracts=mock(ProjectTaskExecutionContractMapper.class);var projects=mock(ProjectMasterMapper.class);
        var versions=mock(ProjectTreeVersionMapper.class);var version=new ProjectTreeVersionDO();version.setTreeVersion(1L);
        project.setId(3L);project.setTenantId(1L);project.setLifecycleStatus("ACTIVE");
        task.setId(4L);task.setTenantId(1L);task.setProjectId(3L);task.setStatus("IN_PROGRESS");task.setVersion(1);task.setStateMachineRevisionId(1L);
        when(tasks.selectTask(any())).thenReturn(task);when(contracts.selectCurrentByTaskId(4L)).thenReturn(contract);when(projects.selectById(3L)).thenReturn(project);when(versions.selectLatestActive(3L)).thenReturn(version);
        when(scopes.isTenantSuperAdmin(1L,9L)).thenReturn(true);when(scopes.resolve(any())).thenReturn(new ProjectTreeScopeService.ProjectTreeScope(3L,1L,Set.of(3L),Set.of(),Set.of()));
        when(permissions.hasAnyPermissions(eq(9L),any(String.class))).thenReturn(true);
        host=new TaskNativeBindingHostProvider(tasks,contracts,mock(ProjectTaskAssignmentMapper.class),mock(ProjectMemberAssignmentMapper.class),transitions,permissions,projects,versions,scopes);
    }
    @ParameterizedTest @ValueSource(strings={"PENDING_START","IN_PROGRESS","PENDING_ACCEPT"})
    void queryAndManagementDoNotExposeFakeSubmissionCompletionOrBusinessOperations(String status){
        task.setStatus(status);when(transitions.selectTransitions(any())).thenReturn(List.of(transition(status,"START"),transition(status,"SUBMIT"),transition(status,"COMPLETE"),transition(status,"CANCEL")));
        var found=host.inspect(new TaskBindingInspectionQuery(1L,4L,9L,"test"));
        assertEquals("RESULT_SUBSCRIPTION",found.bindingType());assertNull(found.recoverableError());assertTrue(found.allowedActions().contains("CANCEL"));
        for(var forbidden:List.of("SUBMIT","COMPLETE","APPROVAL","UPDATE_PROGRESS"))assertFalse(found.allowedActions().contains(forbidden));
        when(permissions.hasAnyPermissions(eq(9L),any(String.class))).thenReturn(false);
        assertTrue(host.inspect(new TaskBindingInspectionQuery(1L,4L,9L,"revoked")).allowedActions().isEmpty());
    }
    @Test void projectClosureAndLossOfScopeStillRemoveEveryMutatingAction(){
        when(transitions.selectTransitions(any())).thenReturn(List.of(transition("IN_PROGRESS","CANCEL")));
        project.setLifecycleStatus("CLOSED");assertTrue(host.inspect(new TaskBindingInspectionQuery(1L,4L,9L,"closed")).allowedActions().isEmpty());
        project.setLifecycleStatus("ACTIVE");when(scopes.resolve(any())).thenReturn(new ProjectTreeScopeService.ProjectTreeScope(3L,1L,Set.of(),Set.of(),Set.of()));
        assertTrue(host.inspect(new TaskBindingInspectionQuery(1L,4L,9L,"no-scope")).allowedActions().isEmpty());
    }
    @Test void malformedRuntimeMarkerCannotUseTheNativeTaskProviderAsAFallback(){
        contract.setBindingParameterSnapshot("{}");var found=host.inspect(new TaskBindingInspectionQuery(1L,4L,9L,"bad"));
        assertEquals("RESULT_SUBSCRIPTION_TASK_CONTRACT_INVALID",found.recoverableError());assertTrue(found.allowedActions().isEmpty());
    }
    private TaskStateTransitionDO transition(String status,String action){var row=new TaskStateTransitionDO();row.setFromStatusCode(status);row.setToStatusCode("DONE");row.setActionCode(action);row.setAllowedRoleCode("CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER");return row;}
}
