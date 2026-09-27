package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SolutionApprovalCompletionProviderTest {
    private final SolutionCompletionMapper mapper = mock(SolutionCompletionMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);

    private final cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService tieredReviews = mock(cn.iocoder.yudao.module.pms.engineering.service.solutionreview.SolutionTieredReviewService.class);
    private final SolutionApprovalCompletionProvider provider = new SolutionApprovalCompletionProvider(mapper, scope, permissions, executions, tieredReviews);
    private final SolutionDO row = new SolutionDO();
    private final ProjectTaskExecutionContext execution = new ProjectTaskExecutionContext(9L,1L,91L,1,92L,1,93L,94L,1,1,95L,1,true,java.time.LocalDateTime.now());
    private final TaskBusinessObjectProvider.Context context = new TaskBusinessObjectProvider.Context(1L,7L,9L,91L,"test");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);

        row.setId(42L); row.setTenantId(1L); row.setProjectId(9L); row.setVersion(3L); row.setStatus(3);
        row.setSolutionType("IMPLEMENTATION"); row.setReviewLevel(0); row.setApprovedBy(7L);
        row.setApprovedTime(java.time.LocalDateTime.now()); row.setBaselineVersion(3);
        when(mapper.one(any())).thenReturn(row);

    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void designerAssociationTargetIsSupportedAndOtherBindingsAreRejected() {
        assertNotNull(provider.associationCandidates(new TaskBusinessObjectProvider.AssociationContext(
                1L, 9L, "PROJECT_" + provider.objectType(), "{}"), null, 100));
        assertThrows(RuntimeException.class, () -> provider.associationCandidates(
                new TaskBusinessObjectProvider.AssociationContext(1L, 9L, "OTHER_OWNER_OBJECT", "{}"), null, 100));
    }
    private boolean completed() { return provider.lockCompletionFact(new TaskBusinessObjectProvider.CompletionContext(1L,execution), "42").handlingCompleted(); }

    @Test void majorReviewRejectionOrMissingFrozenEvidenceCannotCompleteSolution() {
        assertTrue(completed());
        row.setReviewLevel(1); assertFalse(completed());
        when(tieredReviews.approved(row, true)).thenReturn(true);
        assertTrue(completed());
        row.setReviewLevel(0); row.setStatus(4); assertFalse(completed());
        row.setStatus(3); row.setBaselineVersion(2); assertFalse(completed());
        row.setBaselineVersion(3); row.setApprovedBy(null); assertFalse(completed());
    }

    @Test void foreignProjectTenantAndStaleExecutionAreRejected() {
        row.setProjectId(10L);
        assertThrows(RuntimeException.class, this::completed);
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class, this::completed);
        verify(executions, times(1)).lockAndRevalidate(execution);
        TenantContextHolder.setTenantId(1L);
        when(executions.lockAndRevalidate(execution)).thenThrow(new IllegalStateException("stale execution"));
        assertThrows(IllegalStateException.class, this::completed);
    }
    @Test void userReadsRequireScopeAndLockedCommandsRequireExactVersion() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class, () -> provider.inspect(context,"42"));
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
        assertThrows(RuntimeException.class, () -> provider.inspect(context,"42"));
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        var fact = provider.inspect(context,"42");
        assertNotNull(provider.lockAndRevalidate(context,"42",fact.factVersion()));
        assertThrows(IllegalArgumentException.class, () -> provider.lockAndRevalidate(context,"42","obsolete"));
    }
}
