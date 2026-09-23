package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.completion.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
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

class CutoverCompletionProviderTest {
    private final CutoverCompletionMapper mapper = mock(CutoverCompletionMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);

    private final cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper closures = mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper.class);
    private final cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO closure = new cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO();

    private final CutoverCompletionProvider provider = new CutoverCompletionProvider(mapper, scope, permissions, executions, closures);
    private final CutoverTaskDO row = new CutoverTaskDO();
    private final ProjectTaskExecutionContext execution = new ProjectTaskExecutionContext(9L,1,91L,1,92L,1,93L,94L,1,1,95L,1,true,java.time.LocalDateTime.now());
    private final TaskBusinessObjectProvider.Context context = new TaskBusinessObjectProvider.Context(1L,7L,9L,91L,"test");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);

        row.setId(42L); row.setTenantId(1L); row.setProjectId(9L); row.setVersion(3); row.setTaskStatus("ARCHIVED"); row.setCurrentStage("P6");
        closure.setId(43L); closure.setTaskId(42L); closure.setTenantId(1L); closure.setProjectId(9L); closure.setVersion(1);
        closure.setStatusCode("ARCHIVED"); closure.setFinalResultCode("SUCCESS"); closure.setResultRef("closure:43:1");
        closure.setSubmittedAt(java.time.LocalDateTime.now()); closure.setArchivedAt(java.time.LocalDateTime.now());
        when(mapper.one(any())).thenReturn(row); when(closures.selectByTaskForUpdate(any())).thenReturn(closure);
        when(closures.selectByTask(any())).thenReturn(closure);

    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void designerAssociationTargetIsSupportedAndOtherBindingsAreRejected() {
        assertNotNull(provider.associationCandidates(new TaskBusinessObjectProvider.AssociationContext(
                1L, 9L, "PROJECT_" + provider.objectType(), "{}"), null, 100));
        assertThrows(RuntimeException.class, () -> provider.associationCandidates(
                new TaskBusinessObjectProvider.AssociationContext(1L, 9L, "OTHER_OWNER_OBJECT", "{}"), null, 100));
    }
    private boolean completed() { return provider.lockCompletionFact(new TaskBusinessObjectProvider.CompletionContext(1L,execution), "42").handlingCompleted(); }

    @Test void failedOrForeignClosureAndUnarchivedTaskCannotCompleteCutover() {
        assertTrue(completed());
        closure.setFinalResultCode("FAILED"); assertFalse(completed());
        closure.setFinalResultCode("SUCCESS"); closure.setProjectId(10L); assertFalse(completed());
        closure.setProjectId(9L); row.setTaskStatus("IN_PROGRESS"); assertFalse(completed());
        row.setTaskStatus("ARCHIVED"); closure.setArchivedAt(null); assertFalse(completed());
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
