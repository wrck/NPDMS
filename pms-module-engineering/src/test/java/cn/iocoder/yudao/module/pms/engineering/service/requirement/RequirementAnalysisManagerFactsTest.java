package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisManagerFactsTest {
    @Test void superAdminUsesTheRealPrimaryManagerFactRatherThanInventingMembership() {
        var participants = mock(ProjectParticipantFactApi.class);
        var permissions = mock(PermissionApi.class);
        var actual = new ProjectParticipantFact(20L, 9L, Set.of("PROJECT_MANAGER"), "PRIMARY", "ACTIVE", "S1", 3, 3L);
        when(permissions.hasAnyRoles(1L, "super_admin")).thenReturn(true);
        when(participants.inspect(any())).thenReturn(actual);
        assertSame(actual, RequirementAnalysisManagerFacts.inspect(participants, permissions, 20L, 1L));
        verify(participants).inspect(argThat(query -> query.subjectUserId() == null && query.projectId().equals(20L)));
        assertEquals(9L, actual.userId());
    }
    @Test void ordinaryOperatorsStillNeedTheirOwnManagerMembership() {
        var participants = mock(ProjectParticipantFactApi.class);
        var permissions = mock(PermissionApi.class);
        when(participants.inspect(any())).thenThrow(new IllegalStateException("no membership"));
        assertThrows(IllegalStateException.class, () -> RequirementAnalysisManagerFacts.inspect(participants, permissions, 20L, 1L));
        verify(participants).inspect(argThat((ProjectParticipantFactQuery query) -> query.subjectUserId().equals(1L)));
    }
}
