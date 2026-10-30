package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectOrganizationScopeService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Project-member maintenance retains Owner authority; it does not grant task execution. */
class ProjectAssignmentOperationAccessTest {
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final ProjectTaskAssignmentMapper assignments = mock(ProjectTaskAssignmentMapper.class);
    private final TaskBusinessAccess access = new TaskBusinessAccess(scopes, mock(ProjectMasterMapper.class), members,
            assignments, mock(ProjectTaskRuntimeMapper.class), permissions, mock(ProjectTreeScopeService.class),
            mock(ProjectOrganizationScopeService.class), org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.class));
    private final ProjectMasterDO project = new ProjectMasterDO();
    private final ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
    private final TemplateExecutionSnapshot.BindingContract binding = new TemplateExecutionSnapshot.BindingContract();
    private Context context = new Context(1L, 5L, 20L, 10L, null);

    @BeforeEach void setup() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(5L).setTenantId(1L), new MockHttpServletRequest());
        project.setId(20L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        task.setId(10L); task.setProjectId(20L); task.setTenantId(1L); task.setStatus("IN_PROGRESS");
        binding.setType("BUSINESS_OBJECT"); binding.setTargetContextCode("PROJ");
        binding.setTargetObjectType("PROJECT_ASSIGNMENT"); binding.setTargetObjectKey("PROJECT_ASSIGNMENT");
        binding.setComponentKey("PROJ_PROJECT_ASSIGNMENT");
        binding.setParameters(JsonUtils.parseTree("{\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\"}"));
        when(permissions.hasAnyPermissions(5L, "pms:project:assign", "pms:project-team:create")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(20L), Set.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private boolean allowed() {
        return access.assignmentOperationAllowed(new TaskBusinessAccess.PlanningOperationQuery(task, project, context, binding));
    }
    @Test void ownerCanMaintainAssignmentWithoutTaskMembershipOrTaskMetadataPermission() {
        assertTrue(allowed());
        assertFalse(access.writable(task, project, context));
        assertFalse(access.executable(task, project, context));
        verifyNoInteractions(members, assignments);
        verify(scopes).resolveCurrent(argThat(query -> ProjectScopeApi.ACTION_MANAGE.equals(query.actionCode())));
    }
    @Test void ownerPermissionAndManageScopeRemainMandatory() {
        when(permissions.hasAnyPermissions(5L, "pms:project:assign", "pms:project-team:create")).thenReturn(false);
        assertFalse(allowed());
        when(permissions.hasAnyPermissions(5L, "pms:project:assign", "pms:project-team:create")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 1L, Set.of(), Set.of()));
        assertFalse(allowed());
    }
    @Test void exactFrozenOwnerAndReferenceExistingStrategyAreRequired() {
        binding.setTargetObjectType("CONSTRUCTION_PLAN"); assertFalse(allowed());
        binding.setTargetObjectType("PROJECT_ASSIGNMENT"); binding.setTargetContextCode("PLN"); assertFalse(allowed());
        binding.setTargetContextCode("PROJ"); binding.setTargetObjectKey("OTHER"); assertFalse(allowed());
        binding.setTargetObjectKey("PROJECT_ASSIGNMENT"); binding.setComponentKey("OTHER"); assertFalse(allowed());
        binding.setComponentKey("PROJ_PROJECT_ASSIGNMENT"); binding.setType("PAGE"); assertFalse(allowed());
        binding.setType("BUSINESS_OBJECT");
        binding.setParameters(JsonUtils.parseTree("{\"instanceResolutionStrategy\":\"READ_ONLY_AGGREGATE\"}"));
        assertFalse(allowed());
    }
    @Test void tenantActorProjectAndTerminalStateCannotBeBypassed() {
        task.setTenantId(2L); assertFalse(allowed()); task.setTenantId(1L);
        project.setTenantId(2L); assertFalse(allowed()); project.setTenantId(1L);
        task.setProjectId(21L); assertFalse(allowed()); task.setProjectId(20L);
        task.setId(11L); assertFalse(allowed()); task.setId(10L);
        context = new Context(1L, 6L, 20L, 10L, null); assertFalse(allowed());
        context = new Context(1L, 5L, 20L, 10L, null);
        for (String status : Set.of("DONE", "CLOSED", "CANCELLED", "CANCELED")) {
            task.setStatus(status); assertFalse(allowed());
        }
        task.setStatus("IN_PROGRESS"); project.setLifecycleStatus("NORMAL_CLOSED"); assertFalse(allowed());
    }
}
