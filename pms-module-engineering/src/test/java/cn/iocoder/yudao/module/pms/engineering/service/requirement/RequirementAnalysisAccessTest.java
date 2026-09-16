package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisAccessTest {
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final RequirementAnalysisAccess access = new RequirementAnalysisAccess(null, scopes, participants, permissions, null, null);
    private final EntityActor actor = new EntityActor(1L, 9L, null);

    private void allowed() {
        when(permissions.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(20L), Set.of()));
    }

    @Test void missingManagerDoesNotPreventReadingButStillDeniesDraftAccess() {
        allowed();
        when(participants.inspect(any())).thenThrow(new ServiceException(1014024033, "scope denied"));
        assertDoesNotThrow(() -> access.requireRead(20L, actor, false));
        assertFalse(access.isManager(20L, actor));
        assertThrows(ServiceException.class, () -> access.requireRead(20L, actor, true));
    }

    @Test void viewerWithoutManageScopeCannotSeeDraftOrEdit() {
        allowed();
        when(scopes.resolveCurrent(argThat(q -> ProjectScopeApi.ACTION_MANAGE.equals(q.actionCode()))))
                .thenReturn(new ProjectScopeResult(20L, 1L, Set.of(), Set.of()));
        assertFalse(access.isManager(20L, actor));
        assertThrows(ServiceException.class, () -> access.requireRead(20L, actor, true));
        verify(participants, never()).lockAndRevalidate(any());
    }

    @Test void activeManagerCanEditButClosedProjectCannot() {
        allowed();
        when(participants.inspect(any())).thenReturn(new ProjectParticipantFact(20L, 9L, Set.of("PROJECT_MANAGER"), "PRIMARY", "ACTIVE", "S1", 3, 3L));
        assertTrue(access.isManager(20L, actor));
        assertDoesNotThrow(() -> access.requireRead(20L, actor, true));
        when(participants.inspect(any())).thenReturn(new ProjectParticipantFact(20L, 9L, Set.of("PROJECT_MANAGER"), "PRIMARY", "NORMAL_CLOSED", "S1", 3, 3L));
        assertFalse(access.isManager(20L, actor));
    }

    @Test void unavailableManagerFactDisablesEditingAsInTheExistingQueryPath() {
        allowed();
        when(participants.inspect(any())).thenThrow(new IllegalStateException("connection failed"));
        assertFalse(access.isManager(20L, actor));
    }
}
