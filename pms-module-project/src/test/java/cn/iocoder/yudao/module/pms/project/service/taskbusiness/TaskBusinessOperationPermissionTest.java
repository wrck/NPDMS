package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskBusinessOperationPermissionTest {
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class, RETURNS_DEEP_STUBS);
    private final ProjectTreeScopeService trees = mock(ProjectTreeScopeService.class);
    private final TaskBusinessAccess access = new TaskBusinessAccess(scopes, mock(ProjectMasterMapper.class),
            mock(ProjectMemberAssignmentMapper.class), mock(ProjectTaskAssignmentMapper.class),
            mock(ProjectTaskRuntimeMapper.class), permissions, trees);
    private final Context context = new Context(1L, 2L, 3L, 4L, null);

    private ProjectMasterDO project() {
        var project = new ProjectMasterDO();
        project.setId(3L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        return project;
    }
    private ProjectTaskInstanceDO task() {
        var task = new ProjectTaskInstanceDO();
        task.setId(4L); task.setTenantId(1L); task.setProjectId(3L); task.setStatus("IN_PROGRESS");
        return task;
    }
    private void scope() {
        // Bypass assignment variability, not the functional permission under test.
        when(trees.isTenantSuperAdmin(1L, 2L)).thenReturn(true);
        when(scopes.resolveCurrent(any()).fullProjectIds()).thenReturn(Set.of(3L));
    }
    @Test void metadataUpdateDoesNotGrantExecution() {
        scope();
        when(permissions.hasAnyPermissions(2L, "pms:project-task:update")).thenReturn(true);
        assertTrue(access.writable(task(), project(), context));
        assertFalse(access.executable(task(), project(), context));
    }
    @Test void executionDoesNotRequireOrGrantMetadataUpdate() {
        scope();
        when(permissions.hasAnyPermissions(2L, "pms:project-task:execute")).thenReturn(true);
        assertTrue(access.executable(task(), project(), context));
        assertFalse(access.writable(task(), project(), context));
    }
    @Test void executionPermissionDoesNotBypassProjectScope() {
        scope();
        when(permissions.hasAnyPermissions(2L, "pms:project-task:execute")).thenReturn(true);
        when(scopes.resolveCurrent(any()).fullProjectIds()).thenReturn(Set.of());
        assertFalse(access.executable(task(), project(), context));
    }
    @Test void executionPermissionDoesNotBypassTerminalStateOrTenant() {
        scope();
        when(permissions.hasAnyPermissions(2L, "pms:project-task:execute")).thenReturn(true);
        var task = task(); task.setStatus("DONE");
        assertFalse(access.executable(task, project(), context));
        task.setStatus("IN_PROGRESS"); task.setTenantId(9L);
        assertFalse(access.executable(task, project(), context));
    }
}
