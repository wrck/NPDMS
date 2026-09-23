package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO;
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

class StagePlanApprovalCompletionProviderTest {
    private final StagePlanCompletionMapper mapper = mock(StagePlanCompletionMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);

    private final cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper plans = mock(cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper.class);
    private final cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO plan = new cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO();

    private final StagePlanApprovalCompletionProvider provider = new StagePlanApprovalCompletionProvider(mapper, scope, permissions, executions, plans);
    private final StagePlanBatchDO row = new StagePlanBatchDO();
    private final ProjectTaskExecutionContext execution = new ProjectTaskExecutionContext(9L,1,91L,1,92L,1,93L,94L,1,1,95L,1,true,java.time.LocalDateTime.now());
    private final TaskBusinessObjectProvider.Context context = new TaskBusinessObjectProvider.Context(1L,7L,9L,91L,"test");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);

        row.setId(42L); row.setTenantId(1L); row.setProjectId(9L); row.setVersion(3); row.setStatus(2);
        row.setDurationRevisionId(20L); row.setEffectiveAt(java.time.LocalDateTime.now()); row.setBpmProcessInstanceId("bpm-approved");
        plan.setId(19L); plan.setProjectId(9L); plan.setTenantId(1L); plan.setVersion(1); plan.setCurrentDurationRevisionId(20L);
        when(mapper.current(any())).thenReturn(row); when(plans.selectByProjectId(1L,9L)).thenReturn(plan);
        when(plans.selectForUpdate(any())).thenReturn(plan);

    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void designerAssociationTargetIsSupportedAndOtherBindingsAreRejected() {
        assertNotNull(provider.associationCandidates(new TaskBusinessObjectProvider.AssociationContext(
                1L, 9L, "PROJECT_" + provider.objectType(), "{}"), null, 100));
        assertThrows(RuntimeException.class, () -> provider.associationCandidates(
                new TaskBusinessObjectProvider.AssociationContext(1L, 9L, "OTHER_OWNER_OBJECT", "{}"), null, 100));
    }
    private boolean completed() { return provider.lockCompletionFact(new TaskBusinessObjectProvider.CompletionContext(1L,execution), "42").handlingCompleted(); }

    @Test void rejectionMissingApprovalEvidenceAndChangedDurationCannotCompletePlan() {
        assertTrue(completed());
        row.setStatus(3); assertFalse(completed());
        row.setStatus(2); row.setBpmProcessInstanceId(null); assertFalse(completed());
        row.setBpmProcessInstanceId("approved"); plan.setCurrentDurationRevisionId(21L); assertFalse(completed());
        plan.setCurrentDurationRevisionId(20L); row.setEffectiveAt(null); assertFalse(completed());
    }
    @Test void supersededBatchCannotBeReused() {
        row.setId(43L);
        assertThrows(IllegalArgumentException.class, this::completed);
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
