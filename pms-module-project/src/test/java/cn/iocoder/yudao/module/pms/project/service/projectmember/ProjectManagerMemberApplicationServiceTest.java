package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationAuthorizationService;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectManagerMemberApplicationServiceTest {
    @Test @SuppressWarnings("unchecked")
    void changedManagersProduceDedicatedEventWhileNoOpAndReplayDoNot() {
        var projects = mock(ProjectMasterMapper.class);
        var members = mock(ProjectMemberAssignmentMapper.class);
        var users = mock(ActiveUserSelectionApi.class);
        var authorization = mock(ProjectCreationAuthorizationService.class);
        var scope = mock(ProjectAuthorizationGuard.class);
        var commands = mock(PlatformCommandExecutionApi.class);
        var service = new ProjectManagerMemberApplicationService(projects, members, users, authorization, scope, commands);
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setVersion(0);
        project.setCompanyId(8L); project.setLifecycleStatus("ACTIVE");
        var current = new ArrayList<ProjectMemberAssignmentDO>();
        when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        when(members.selectProjectManagersForUpdate(any())).thenReturn(current);
        when(members.insert(any(ProjectMemberAssignmentDO.class))).thenAnswer(call -> {
            var member = call.getArgument(0, ProjectMemberAssignmentDO.class); member.setId(99L); current.add(member); return 1;
        });
        when(projects.updateManagerMembersIfMatch(any())).thenReturn(1);
        when(users.page(any())).thenReturn(new PageResult<>(List.of(new ActiveUserSelectionApi.User(42L, "manager", "经理", 5L)), 1L));
        var facts = new ArrayList<PlatformCommandExecutionApi.SuccessFacts>();
        when(commands.execute(any(), anyString(), eq(ProjectManagerMemberResult.class), any(), any())).thenAnswer(call -> {
            var result = ((Supplier<ProjectManagerMemberResult>) call.getArgument(3)).get();
            facts.add(((Function<ProjectManagerMemberResult, PlatformCommandExecutionApi.SuccessFacts>) call.getArgument(4)).apply(result));
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
        var actor = new ProjectManagerMemberApplicationService.Actor(7L, 11L, "member-event");
        var initial = new ProjectManagerMemberCommand(9L, 0, Set.of(42L), Set.of(), 42L, "指定经理", "first");
        var saved = service.update(initial, actor);
        assertTrue(saved.changed()); assertEquals(1, facts.getFirst().businessEvents().size());
        assertEquals("ProjectManagersChanged", facts.getFirst().eventType());
        var event = facts.getFirst().businessEvents().getFirst();
        assertEquals("ProjectRuleReevaluationRequested", event.eventType());
        var context = JsonUtils.parseTree(event.eventPayload());
        assertEquals(7L, context.path("tenantId").asLong()); assertEquals(9L, context.path("projectId").asLong());
        assertEquals(11L, context.path("actorId").asLong()); assertEquals("member-event", context.path("correlationId").asText());
        assertFalse(service.update(new ProjectManagerMemberCommand(9L, 1, Set.of(), Set.of(), 42L, "未变更", "noop"), actor).changed());
        assertTrue(facts.getLast().businessEvents().isEmpty()); assertNull(facts.getLast().eventType());
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, saved))
                .when(commands).execute(any(), anyString(), eq(ProjectManagerMemberResult.class), any(), any());
        assertEquals(saved, service.update(initial, actor)); assertEquals(2, facts.size());
        verify(members).insert(any(ProjectMemberAssignmentDO.class)); verify(projects).updateManagerMembersIfMatch(any());
        verify(authorization, times(3)).assertCanAssign(11L);
    }
}
