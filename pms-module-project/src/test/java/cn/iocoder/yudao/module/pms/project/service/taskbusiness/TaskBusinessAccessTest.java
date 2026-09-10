package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PM-11: viewing an assigned task is not relationship write authority. */
class TaskBusinessAccessTest {
    @Test void assigneeNeedsEditScopeRatherThanViewScopeToMutateLinks() {
        var scopes = mock(ProjectScopeApi.class);
        var assignments = mock(ProjectTaskAssignmentMapper.class);
        var permissions = mock(PermissionApi.class);
        var access = new TaskBusinessAccess(scopes, mock(ProjectMasterMapper.class),
                mock(ProjectMemberAssignmentMapper.class), assignments, mock(ProjectTaskRuntimeMapper.class), permissions);
        var task = new ProjectTaskInstanceDO();
        task.setId(10L); task.setTenantId(1L); task.setProjectId(20L); task.setStatus("IN_PROGRESS");
        var project = new ProjectMasterDO();
        project.setId(20L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        var assignment = new ProjectTaskAssignmentDO();
        assignment.setTenantId(1L); assignment.setProjectTaskId(10L); assignment.setAssigneeUserId(5L);
        when(assignments.selectCurrent(any())).thenReturn(List.of(assignment));
        when(permissions.hasAnyPermissions(5L, "pms:project-task:update")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenAnswer(i -> {
            cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery query = i.getArgument(0);
            return new ProjectScopeResult(20L, 1L,
                    ProjectScopeApi.ACTION_VIEW.equals(query.actionCode()) ? Set.of(20L) : Set.of(), Set.of());
        });
        var context = new Context(1L, 5L, 20L, 10L, "corr");

        assertFalse(access.writable(task, project, context));

        org.mockito.Mockito.doReturn(new ProjectScopeResult(20L, 1L, Set.of(20L), Set.of()))
                .when(scopes).resolveCurrent(any());
        assertTrue(access.writable(task, project, context));
        verify(scopes, times(2)).resolveCurrent(argThat(q -> ProjectScopeApi.ACTION_EDIT.equals(q.actionCode())));
        project.setLifecycleStatus("CLOSED");
        assertFalse(access.writable(task, project, context));
    }
}
