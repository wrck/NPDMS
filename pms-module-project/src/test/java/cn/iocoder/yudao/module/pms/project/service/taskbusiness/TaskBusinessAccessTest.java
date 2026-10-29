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
    @Test void regionalUnassignedExecutionLocksAuthorityAndCannotOverrideAnotherAssignee() {
        var scopes = mock(ProjectScopeApi.class); var members = mock(ProjectMemberAssignmentMapper.class);
        var assignments = mock(ProjectTaskAssignmentMapper.class); var permissions = mock(PermissionApi.class);
        var regional = mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.class);
        var access = new TaskBusinessAccess(scopes, mock(ProjectMasterMapper.class), members, assignments,
                mock(ProjectTaskRuntimeMapper.class), permissions,
                mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService.class),
                mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectOrganizationScopeService.class), regional);
        var task = new ProjectTaskInstanceDO().setId(10L).setProjectId(20L).setStatus("IN_PROGRESS"); task.setTenantId(1L);
        var project = new ProjectMasterDO(); project.setId(20L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        var context = new Context(1L,5L,20L,10L,"regional");
        when(permissions.hasAnyPermissions(5L,"pms:project-task:execute")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,1L,Set.of(20L),Set.of()));
        when(regional.permits(any())).thenReturn(true);
        assertTrue(access.executable(task,project,context));
        assertFalse(access.executableLocked(task,project,context));
        when(regional.lockCurrent(any())).thenAnswer(call -> cn.iocoder.yudao.module.pms.project.api.participant.ManagementQualificationTestFacts.locked(call.getArgument(0)));
        assertTrue(access.executableLocked(task,project,context));
        var assigned = new ProjectTaskAssignmentDO(); assigned.setAssigneeUserId(6L);
        when(assignments.selectCurrent(any())).thenReturn(List.of(assigned));
        when(assignments.selectCurrentForUpdate(any())).thenReturn(assigned);
        assertFalse(access.executable(task,project,context));
        assertFalse(access.executableLocked(task,project,context));
        verify(regional, times(2)).lockCurrent(any());
    }

    @Test void regionalOwnerQualificationSupportsIndependentDurationWithoutInventingTaskMembership() {
        var scopes = mock(ProjectScopeApi.class);
        var members = mock(ProjectMemberAssignmentMapper.class);
        var assignments = mock(ProjectTaskAssignmentMapper.class);
        var permissions = mock(PermissionApi.class);
        var organizations = mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectOrganizationScopeService.class);
        var access = new TaskBusinessAccess(scopes,mock(ProjectMasterMapper.class),members,assignments,
                mock(ProjectTaskRuntimeMapper.class),permissions,
                mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService.class),organizations, org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.class));
        var login = new cn.iocoder.yudao.framework.security.core.LoginUser(); login.setId(5L); login.setTenantId(1L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(login,new org.springframework.mock.web.MockHttpServletRequest());
        try {
            var task = new ProjectTaskInstanceDO().setId(10L).setProjectId(20L).setStatus("IN_PROGRESS"); task.setTenantId(1L);
            var project = new ProjectMasterDO(); project.setId(20L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
            var context = new Context(1L,5L,20L,10L,"duration");
            var binding = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract();
            binding.setType("BUSINESS_OBJECT"); binding.setTargetContextCode("PLN"); binding.setTargetObjectType("CONSTRUCTION_PLAN");
            binding.setTargetObjectKey("CONSTRUCTION_PLAN"); binding.setComponentKey("PLN_CONSTRUCTION_PLAN");
            binding.setParameters(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree("{\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\"}"));
            when(permissions.hasAnyPermissions(eq(5L),any(String[].class))).thenReturn(true);
            when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,1L,Set.of(20L),Set.of()));
            when(organizations.projectIds(any())).thenReturn(Set.of(20L));
            var query = new TaskBusinessAccess.PlanningOperationQuery(task,project,context,binding);
            assertTrue(access.planningOperationAllowed(query));
            verifyNoInteractions(members,assignments);
            binding.setType("BUSINESS_COMPONENT");
            binding.setParameters(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree("{\"instanceResolutionStrategy\":\"CREATE_ON_FIRST_ACTION\"}"));
            assertTrue(access.planningOperationAllowed(query));
            binding.setComponentKey("PLN_STAGE_PLAN_APPROVAL"); assertFalse(access.planningOperationAllowed(query));
            binding.setComponentKey("PLN_CONSTRUCTION_PLAN");
            when(organizations.projectIds(any())).thenReturn(Set.of()); assertFalse(access.planningOperationAllowed(query));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    @Test void membersCanWriteUnassignedTasksButManagersCannotBypassDesignation() {
        var scopes = mock(ProjectScopeApi.class); var members = mock(ProjectMemberAssignmentMapper.class);
        var assignments = mock(ProjectTaskAssignmentMapper.class); var permissions = mock(PermissionApi.class);
        var access = new TaskBusinessAccess(scopes,mock(ProjectMasterMapper.class),members,assignments,
                mock(ProjectTaskRuntimeMapper.class),permissions,mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService.class), mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectOrganizationScopeService.class), org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.class));
        var task = new ProjectTaskInstanceDO().setId(10L).setProjectId(20L).setStatus("IN_PROGRESS"); task.setTenantId(1L);
        var project = new ProjectMasterDO(); project.setId(20L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        when(permissions.hasAnyPermissions(5L,"pms:project-task:update")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,1L,Set.of(20L),Set.of()));
        var member = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO(); member.setProjectId(20L); member.setMemberRole("TEAM_MEMBER");
        when(members.selectAuthorizedByUser(any())).thenReturn(List.of(member));
        var context = new Context(1L,5L,20L,10L,"member");
        assertTrue(access.writable(task,project,context));
        member.setMemberRole("PROJECT_MANAGER"); var assigned = new ProjectTaskAssignmentDO(); assigned.setAssigneeUserId(6L);
        when(assignments.selectCurrent(any())).thenReturn(List.of(assigned));
        assertFalse(access.writable(task,project,context));
    }
    @Test void assigneeNeedsEditScopeRatherThanViewScopeToMutateLinks() {
        var scopes = mock(ProjectScopeApi.class);
        var assignments = mock(ProjectTaskAssignmentMapper.class);
        var permissions = mock(PermissionApi.class);
        var access = new TaskBusinessAccess(scopes, mock(ProjectMasterMapper.class),
                mock(ProjectMemberAssignmentMapper.class), assignments, mock(ProjectTaskRuntimeMapper.class), permissions,
                mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService.class), mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectOrganizationScopeService.class), org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.class));
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

    @Test void superAdminUsesExistingScopeButCannotBypassTenantOrTerminalState() {
        var scopes = mock(ProjectScopeApi.class);
        var projects = mock(ProjectMasterMapper.class);
        var tasks = mock(ProjectTaskRuntimeMapper.class);
        var members = mock(ProjectMemberAssignmentMapper.class);
        var permissions = mock(PermissionApi.class);
        var treeScopes = mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService.class);
        var access = new TaskBusinessAccess(scopes, projects, members, mock(ProjectTaskAssignmentMapper.class), tasks, permissions, treeScopes, mock(cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectOrganizationScopeService.class), org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.class));
        var task = new ProjectTaskInstanceDO().setId(10L).setProjectId(20L).setStatus("PENDING_ASSIGN");
        task.setTenantId(1L);
        var project = new ProjectMasterDO(); project.setId(20L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        when(tasks.selectTask(any())).thenReturn(task); when(projects.selectById(20L)).thenReturn(project);
        when(permissions.hasAnyPermissions(eq(5L), any())).thenReturn(true);
        when(treeScopes.isTenantSuperAdmin(1L, 5L)).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(20L), Set.of()));
        var context = new Context(1L, 5L, 20L, 10L, "admin");
        assertSame(task, access.read(10L, 1L, 5L));
        assertTrue(access.writable(task, project, context));
        verifyNoInteractions(members);
        verify(tasks, never()).selectFullTaskIds(any());
        task.setStatus("DONE"); assertFalse(access.writable(task, project, context));
        task.setStatus("PENDING_ASSIGN"); project.setLifecycleStatus("CLOSED"); assertFalse(access.writable(task, project, context));
        project.setLifecycleStatus("ACTIVE");
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(), Set.of()));
        assertThrows(RuntimeException.class, () -> access.read(10L, 1L, 5L));
        assertFalse(access.writable(task, project, context));
        task.setTenantId(2L); assertThrows(RuntimeException.class, () -> access.read(10L, 1L, 5L));
    }
}
