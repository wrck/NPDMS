package cn.iocoder.yudao.module.pms.project.service.taskbusiness;
import cn.iocoder.yudao.module.pms.project.dal.mysql.completion.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
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

class ProjectAssignmentCompletionProviderTest {
    private final ProjectAssignmentCompletionMapper mapper = mock(ProjectAssignmentCompletionMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);

    private final ProjectAssignmentCompletionProvider provider = new ProjectAssignmentCompletionProvider(mapper, scope, permissions, executions);
    private final ProjectMasterDO row = new ProjectMasterDO();
    private final ProjectTaskExecutionContext execution = new ProjectTaskExecutionContext(9L,1L,91L,1,92L,1,93L,94L,1,1,95L,1,true,java.time.LocalDateTime.now());
    private final TaskBusinessObjectProvider.Context context = new TaskBusinessObjectProvider.Context(1L,7L,9L,91L,"test");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);

        row.setId(9L); row.setTenantId(1L); row.setVersion(1L); row.setManagerId(7L);
        when(mapper.project(any())).thenReturn(row);
        var service = member(1L, "SERVICE_MANAGER", 8L);
        var manager = member(2L, "PROJECT_MANAGER", 7L);
        members = new java.util.ArrayList<>(List.of(service,manager));
        when(mapper.members(any())).thenAnswer(call -> members);

    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void designerAssociationTargetIsSupportedAndOtherBindingsAreRejected() {
        assertNotNull(provider.associationCandidates(new TaskBusinessObjectProvider.AssociationContext(
                1L, 9L, "PROJECT_" + provider.objectType(), "{}"), null, 100));
        assertThrows(RuntimeException.class, () -> provider.associationCandidates(
                new TaskBusinessObjectProvider.AssociationContext(1L, 9L, "OTHER_OWNER_OBJECT", "{}"), null, 100));
    }
    private boolean completed() { return provider.lockCompletionFact(new TaskBusinessObjectProvider.CompletionContext(1L,execution), "9").handlingCompleted(); }

    private java.util.List<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO> members;
    private cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO member(Long id, String role, Long user) {
        var row = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO();
        row.setId(id); row.setTenantId(1L); row.setProjectId(9L); row.setUserId(user); row.setMemberRole(role);
        row.setVersion(1); row.setStatus("ACTIVE"); row.setAssignmentType("PRIMARY");
        row.setEffectiveFrom(java.time.LocalDateTime.now().minusDays(1)); return row;
    }
    @Test void expiredCollaboratingOrMismatchedManagersCannotCompleteAssignment() {
        assertTrue(completed());
        members.getFirst().setEffectiveTo(java.time.LocalDateTime.now().minusSeconds(1)); assertFalse(completed());
        members.getFirst().setEffectiveTo(null); members.getFirst().setAssignmentType("COLLABORATOR"); assertFalse(completed());
        members.getFirst().setAssignmentType("PRIMARY"); row.setManagerId(99L); assertFalse(completed());
        row.setManagerId(7L); members.getLast().setStatus("INACTIVE"); assertFalse(completed());
    }

    @Test void foreignProjectTenantAndStaleExecutionAreRejected() {
        row.setTenantId(2L);
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
        assertThrows(RuntimeException.class, () -> provider.inspect(context,"9"));
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
        assertThrows(RuntimeException.class, () -> provider.inspect(context,"9"));
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        var fact = provider.inspect(context,"9");
        assertNotNull(provider.lockAndRevalidate(context,"9",fact.factVersion()));
        assertThrows(IllegalArgumentException.class, () -> provider.lockAndRevalidate(context,"9","obsolete"));
    }
}
