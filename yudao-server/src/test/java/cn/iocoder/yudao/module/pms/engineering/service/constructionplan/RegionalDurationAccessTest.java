package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.*;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.api.scope.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.*;
import cn.iocoder.yudao.module.system.api.permission.dto.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class RegionalDurationAccessTest {
 ConstructionPlanQueryService service;ConstructionPlanDO plan;ProjectMasterDO project;ConstructionPlanChangeMapper changes;
 OrganizationScopeApi organizations;PermissionApi permissions;ProjectScopeApi scopes;
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(1L);
  var login=new cn.iocoder.yudao.framework.security.core.LoginUser();login.setId(9L);login.setTenantId(1L);
  cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(login,new org.springframework.mock.web.MockHttpServletRequest());
  var plans=mock(ConstructionPlanMapper.class);var revisions=mock(ConstructionPlanRevisionMapper.class);changes=mock(ConstructionPlanChangeMapper.class);
  permissions=mock(PermissionApi.class);scopes=mock(ProjectScopeApi.class);organizations=mock(OrganizationScopeApi.class);
  var projects=mock(ProjectMasterMapper.class);var members=mock(ProjectMemberAssignmentMapper.class);
  project=new ProjectMasterDO();project.setId(100L);project.setTenantId(1L);project.setLifecycleStatus("ACTIVE");project.setVersion(3L);project.setCompanyId(11L);project.setDepartmentId(21L);
  when(projects.selectById(100L)).thenReturn(project);
  when(organizations.inspectRoleScope(any())).thenReturn(new UserCompanyDepartmentScopeRespDTO());
  when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);
  when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,7L,Set.of(100L),Set.of()));
  plan=new ConstructionPlanDO();plan.setId(501L);plan.setProjectId(100L);plan.setTenantId(1L);plan.setVersion(1L);plan.setCurrentDurationRevisionId(701L);
  when(plans.selectByLockQuery(any())).thenReturn(plan);
  var revision=new ConstructionPlanRevisionDO();revision.setId(701L);revision.setTenantId(1L);revision.setPlanId(501L);when(revisions.selectById(any())).thenReturn(revision);
  var executionGuard=mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.class);
  when(executionGuard.inspectForWrite(any())).thenReturn(true);
  service=new ConstructionPlanQueryService(plans,revisions,changes,permissions,scopes,new ProjectDurationOperatorApiImpl(projects,members,new ProjectManagementQualificationApiImpl(mock(ProjectParticipantFactApi.class),new ProjectRegionalManagerFactApiImpl(projects,organizations),projects,mock(cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectCurrentStageService.class))),executionGuard);
 }
 @AfterEach void clean(){TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 @Test void regionalServiceManagerWithNoPmMembershipHasCreateChangeAction(){assertEquals(List.of("CREATE_CHANGE"),service.getById(501L,new ConstructionPlanQueryService.Actor(1L,9L)).getAllowedActions());}
 @Test void scopeRoleAloneCannotReplaceProjectManageScope(){
  when(scopes.resolveCurrent(any())).thenAnswer(i->{ProjectCurrentScopeQuery q=i.getArgument(0);return new ProjectScopeResult(100L,7L,ProjectScopeApi.ACTION_VIEW.equals(q.actionCode())?Set.of(100L):Set.of(),Set.of());});
  assertEquals(List.of(),service.getById(501L,new ConstructionPlanQueryService.Actor(1L,9L)).getAllowedActions());
 }
 @Test void functionalPermissionStillRequired(){when(permissions.hasAnyPermissions(9L,ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(false);assertEquals(List.of(),service.getById(501L,new ConstructionPlanQueryService.Actor(1L,9L)).getAllowedActions());}
 @Test void pendingChangeKeepsExistingNoSecondChangeGuard(){plan.setPendingChangeId(801L);var change=new ConstructionPlanChangeDO();change.setId(801L);change.setPlanId(501L);change.setStatusCode("PENDING_APPROVAL");when(changes.selectById(any())).thenReturn(change);assertEquals(List.of(),service.getById(501L,new ConstructionPlanQueryService.Actor(1L,9L)).getAllowedActions());}
 @Test void closedProjectHasNoWriteAction(){project.setLifecycleStatus("NORMAL_CLOSED");assertEquals(List.of(),service.getById(501L,new ConstructionPlanQueryService.Actor(1L,9L)).getAllowedActions());}
 @Test void removedOrganizationGrantDoesNotLeaveRoleOnlyAction(){when(organizations.inspectRoleScope(any())).thenReturn(null);assertEquals(List.of(),service.getById(501L,new ConstructionPlanQueryService.Actor(1L,9L)).getAllowedActions());}
}
