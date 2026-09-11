package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessContext;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskBusinessBindingHostProviderTest {
    private final ProjectTaskBusinessService business = mock(ProjectTaskBusinessService.class);
    private final ProjectTaskRuntimeMapper tasks = mock(ProjectTaskRuntimeMapper.class);
    private final ProjectTaskAssignmentMapper assignments = mock(ProjectTaskAssignmentMapper.class);
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final TaskStateMachineMapper states = mock(TaskStateMachineMapper.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final TaskBusinessBindingHostProvider host = new TaskBusinessBindingHostProvider(
            business, tasks, assignments, members, states, scopes, permissions);
    private final TaskBindingInspectionQuery query = new TaskBindingInspectionQuery(1L, 10L, 5L, "corr");

    @BeforeEach void setup() {
        when(business.getContext(10L, 1L, 5L, "corr")).thenReturn(context(null));
        var task = new ProjectTaskInstanceDO();
        task.setId(10L); task.setTenantId(1L); task.setProjectId(20L);
        task.setStatus("PENDING_ACCEPT"); task.setStateMachineRevisionId(30L);
        when(tasks.selectTask(any())).thenReturn(task);
        var manager = new ProjectMemberAssignmentDO();
        manager.setProjectId(20L); manager.setMemberRole("PROJECT_MANAGER");
        when(members.selectActiveByUser(any())).thenReturn(List.of(manager));
        var transition = new TaskStateTransitionDO();
        transition.setFromStatusCode("PENDING_ACCEPT"); transition.setActionCode("COMPLETE");
        transition.setAllowedRoleCode("CURRENT_PROJECT_MANAGER_OR_RULE_APPROVER");
        when(states.selectTransitions(any())).thenReturn(List.of(transition));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(20L), Set.of()));
        when(permissions.hasAnyPermissions(5L, "pms:project-task:complete")).thenReturn(true);
    }

    @Test void bothBindingTypesExposeAggregateVersionButNotOwnerCommands() {
        var registry = new TaskBindingHostRegistry(List.of(host));
        for (String type : TaskBusinessBindingHostProvider.TYPES) {
            var result = registry.inspect(type, query);
            assertEquals(type, result.bindingType());
            assertEquals("a".repeat(64), result.factVersion());
            assertEquals(Set.of("COMPLETE"), result.allowedActions());
        }
        verify(scopes, times(2)).resolveCurrent(argThat(q -> ProjectScopeApi.ACTION_MANAGE.equals(q.actionCode())));
    }

    @Test void ownerActionsNeverGrantMissingTaskPermissionOrProjectScope() {
        when(permissions.hasAnyPermissions(5L, "pms:project-task:complete")).thenReturn(false);
        assertTrue(host.inspect(query).allowedActions().isEmpty());
        when(permissions.hasAnyPermissions(5L, "pms:project-task:complete")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(), Set.of()));
        assertTrue(host.inspect(query).allowedActions().isEmpty());
    }

    @Test void ownerActionsNeverGrantMissingManagerRole() {
        when(members.selectActiveByUser(any())).thenReturn(List.of());
        assertTrue(host.inspect(query).allowedActions().isEmpty());
    }

    @Test void unassignedPendingTaskCanStartButDesignationAndScopeRemainEffective() {
        var task = tasks.selectTask(null); task.setStatus("PENDING_ASSIGN");
        var member = new ProjectMemberAssignmentDO(); member.setProjectId(20L); member.setMemberRole("TEAM_MEMBER");
        when(members.selectActiveByUser(any())).thenReturn(List.of(member));
        var start = new TaskStateTransitionDO(); start.setFromStatusCode("PENDING_START"); start.setActionCode("START");
        start.setAllowedRoleCode("CURRENT_EFFECTIVE_ASSIGNEE");
        when(states.selectTransitions(any())).thenReturn(List.of(start));
        when(permissions.hasAnyPermissions(5L, "pms:project-task:execute")).thenReturn(true);
        assertEquals(Set.of("START"), host.inspect(query).allowedActions());
        var assigned = new cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO();
        assigned.setAssigneeUserId(5L);
        when(assignments.selectCurrent(any())).thenReturn(List.of(assigned));
        assertTrue(host.inspect(query).allowedActions().isEmpty());
        when(assignments.selectCurrent(any())).thenReturn(List.of());
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(), Set.of()));
        assertTrue(host.inspect(query).allowedActions().isEmpty());
    }

    @Test void unavailableContextDoesNotConsultTaskPermissionsOrOfferFallback() {
        when(business.getContext(10L, 1L, 5L, "corr")).thenReturn(context("VIEW_NOT_FROZEN"));
        var result = host.inspect(query);
        assertEquals("VIEW_NOT_FROZEN", result.recoverableError());
        assertTrue(result.allowedActions().isEmpty());
        assertNull(result.factVersion());
        verifyNoInteractions(tasks, assignments, members, states, scopes, permissions);
    }

    private TaskBusinessContext context(String error) {
        return new TaskBusinessContext(10L, 20L, 40L, 2, "SOL", "SITE_SURVEY", "survey-list", 50L,
                "REFERENCE_EXISTING", List.of(), Set.of("LINK"), error, "a".repeat(64),
                Set.of("QUERY", "CREATE", "COMPLETE"), null, true);
    }
}
