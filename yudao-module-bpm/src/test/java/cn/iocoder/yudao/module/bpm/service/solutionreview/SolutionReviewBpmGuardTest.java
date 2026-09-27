package cn.iocoder.yudao.module.bpm.service.solutionreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.flowable.engine.*;
import org.junit.jupiter.api.*;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SolutionReviewBpmGuardTest {
    final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    final ExplicitPermissionApi permissions = mock(ExplicitPermissionApi.class);
    final SolutionReviewBpmGuard guard = new SolutionReviewBpmGuard(mock(RuntimeService.class),mock(RepositoryService.class),
            scope,participants,permissions,mock(AdminUserApi.class));
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var visible = new ProjectScopeResult(9L,1L,Set.of(9L),Set.of());
        when(scope.resolveCurrent(any())).thenReturn(visible); when(scope.lockAndRevalidate(any())).thenReturn(visible);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void engineeringReviewRequiresExplicitGrantAndProjectScope() {
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(1L,8L,9L,"ENGINEERING_MANAGEMENT",7L));
        when(permissions.lockAndCheck(1L,8L,"pms:sol-solution:major-review")).thenReturn(true);
        assertDoesNotThrow(() -> guard.authorize(1L,8L,9L,"ENGINEERING_MANAGEMENT",7L));
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(1L,8L,9L,"ENGINEERING_MANAGEMENT",7L));
    }
    @Test void selfApprovalAndForeignTenantAreDenied() {
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(1L,7L,9L,"SERVICE_MANAGER",7L));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(2L,8L,9L,"SERVICE_MANAGER",7L));
    }
    @Test void managerRoleDoesNotGrantEngineeringReview() {
        var member = new ProjectParticipantFact(9L,8L,Set.of("SERVICE_MANAGER"),"PRIMARY","ACTIVE","S3",1L,1L);
        when(participants.inspect(any())).thenReturn(member); when(participants.lockAndRevalidate(any())).thenReturn(member);
        assertDoesNotThrow(() -> guard.authorize(1L,8L,9L,"SERVICE_MANAGER",7L));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(1L,8L,9L,"ENGINEERING_MANAGEMENT",7L));
    }
    @Test void lostProjectScopeDuringLockFailsClosed() {
        when(scope.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L,2L,Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(1L,8L,9L,"SERVICE_MANAGER",7L));
    }
}
