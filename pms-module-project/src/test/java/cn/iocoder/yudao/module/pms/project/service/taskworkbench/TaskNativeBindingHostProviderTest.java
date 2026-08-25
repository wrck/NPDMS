package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskNativeBindingHostProviderTest {

    @Mock ProjectTaskRuntimeMapper taskMapper;
    @Mock ProjectTaskExecutionContractMapper contractMapper;
    @Mock ProjectTaskAssignmentMapper assignmentMapper;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock TaskStateMachineMapper stateMachineMapper;
    @Mock PermissionCommonApi permissionApi;
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectTreeVersionMapper projectTreeVersionMapper;
    @Mock ProjectTreeScopeService projectTreeScopeService;

    private TaskNativeBindingHostProvider provider;
    private ProjectTaskExecutionContractDO contract;

    @BeforeEach
    void setUp() {
        provider = new TaskNativeBindingHostProvider(taskMapper, contractMapper, assignmentMapper,
                memberMapper, stateMachineMapper, permissionApi, projectMapper,
                projectTreeVersionMapper, projectTreeScopeService);
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
        task.setId(11L);
        task.setTenantId(0L);
        task.setProjectId(100L);
        task.setStatus("PENDING_START");
        task.setStateMachineRevisionId(81L);
        task.setVersion(3);
        when(taskMapper.selectTask(any())).thenReturn(task);
        contract = new ProjectTaskExecutionContractDO();
        contract.setId(91L);
        contract.setTenantId(0L);
        contract.setProjectTaskId(11L);
        contract.setWorkBindingTypeCode("TASK_NATIVE");
        contract.setContractVersion(2);
        when(contractMapper.selectCurrentByTaskId(11L)).thenReturn(contract);
    }

    @Test
    void shouldExposeOnlyRoleAndPermissionApprovedAction() {
        ProjectTaskAssignmentDO assignment = new ProjectTaskAssignmentDO();
        assignment.setProjectTaskId(11L);
        assignment.setAssigneeUserId(9L);
        assignment.setVersion(1);
        when(assignmentMapper.selectCurrent(any())).thenReturn(List.of(assignment));
        when(memberMapper.selectActiveByUser(any())).thenReturn(List.of());
        TaskStateTransitionDO transition = new TaskStateTransitionDO();
        transition.setFromStatusCode("PENDING_START");
        transition.setActionCode("START");
        transition.setAllowedRoleCode("CURRENT_EFFECTIVE_ASSIGNEE");
        when(stateMachineMapper.selectTransitions(any())).thenReturn(List.of(transition));
        when(permissionApi.hasAnyPermissions(eq(9L), eq("pms:project-task:execute"))).thenReturn(true);

        TaskBindingInspection result = provider.inspect(new TaskBindingInspectionQuery(0L, 11L, 9L, "test"));

        assertEquals(java.util.Set.of("START"), result.allowedActions());
        assertEquals("3:2:1", result.factVersion());
        assertNull(result.recoverableError());
    }

    @Test
    void shouldFailClosedForInvalidNativeExternalTarget() {
        contract.setTargetObjectKey("external-object");

        TaskBindingInspection result = provider.inspect(new TaskBindingInspectionQuery(0L, 11L, 9L, "test"));

        assertEquals("BINDING_CONTRACT_INVALID", result.recoverableError());
        assertTrue(result.allowedActions().isEmpty());
    }
}
