package cn.iocoder.yudao.module.bpm.service.solutionreview;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.flowable.engine.*;
import org.junit.jupiter.api.*;
import java.util.List;
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
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(1L,8L,9L,"ENGINEERING_MANAGEMENT"));
        when(permissions.lockAndCheck(1L,8L,"pms:sol-solution:major-review")).thenReturn(true);
        assertDoesNotThrow(() -> guard.authorize(1L,8L,9L,"ENGINEERING_MANAGEMENT"));
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(1L,8L,9L,"ENGINEERING_MANAGEMENT"));
    }
    @Test void selfApprovalIsAllowedButForeignTenantIsDenied() {
        // 服务经理本人提交方案时不排除自审：申请人与审批人相同不再拒绝
        var member = new ProjectParticipantFact(9L,7L,Set.of("SERVICE_MANAGER"),"PRIMARY","ACTIVE","S3",1L,1L);
        when(participants.inspect(any())).thenReturn(member); when(participants.lockAndRevalidate(any())).thenReturn(member);
        assertDoesNotThrow(() -> guard.authorize(1L,7L,9L,"SERVICE_MANAGER"));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(2L,8L,9L,"SERVICE_MANAGER"));
    }
    @Test void managerRoleDoesNotGrantEngineeringReview() {
        var member = new ProjectParticipantFact(9L,8L,Set.of("SERVICE_MANAGER"),"PRIMARY","ACTIVE","S3",1L,1L);
        when(participants.inspect(any())).thenReturn(member); when(participants.lockAndRevalidate(any())).thenReturn(member);
        assertDoesNotThrow(() -> guard.authorize(1L,8L,9L,"SERVICE_MANAGER"));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(1L,8L,9L,"ENGINEERING_MANAGEMENT"));
    }
    @Test void lostProjectScopeDuringLockFailsClosed() {
        when(scope.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L,2L,Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(1L,8L,9L,"SERVICE_MANAGER"));
    }
    @Test void serviceManagerResolutionRequiresExactlyOneEffectiveManager() {
        var member = new ProjectParticipantFact(9L,7L,Set.of("SERVICE_MANAGER"),"PRIMARY","ACTIVE","S3",1L,1L);
        when(participants.inspect(any())).thenReturn(member);
        assertEquals(7L, guard.resolveServiceManager(1L, 9L));
        when(participants.inspect(any())).thenThrow(new ServiceException(400, "项目树范围禁止"));
        var failure = assertThrows(IllegalArgumentException.class, () -> guard.resolveServiceManager(1L, 9L));
        assertTrue(failure.getMessage().contains("服务经理"));
    }
    @Test void engineeringReviewerResolutionRequiresExactlyOneGrantHolder() {
        when(permissions.listUsersWithPermission(1L, "pms:sol-solution:major-review")).thenReturn(List.of(8L));
        assertEquals(8L, guard.resolveEngineeringReviewer(1L));
        when(permissions.listUsersWithPermission(1L, "pms:sol-solution:major-review")).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> guard.resolveEngineeringReviewer(1L));
        when(permissions.listUsersWithPermission(1L, "pms:sol-solution:major-review")).thenReturn(List.of(8L, 10L));
        var failure = assertThrows(IllegalArgumentException.class, () -> guard.resolveEngineeringReviewer(1L));
        assertTrue(failure.getMessage().contains("多名持有人"));
    }
}
