package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.*;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.*;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.CreateInitialDurationCommand;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.pms.project.api.scope.*;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.system.api.permission.*;
import cn.iocoder.yudao.module.system.api.permission.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.*;
import java.time.LocalDate;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class RegionalInitialPmPrerequisiteTest {
 @AfterEach void cleanup(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
 @Test void regionalOperatorCannotCreateFirstDurationWhenProjectHasNoCurrentPm(){
  TenantContextHolder.setTenantId(1L);var user=new LoginUser();user.setId(9L);user.setTenantId(1L);SecurityFrameworkUtils.setLoginUser(user,new MockHttpServletRequest());
  var projects=mock(ProjectMasterMapper.class);var members=mock(ProjectMemberAssignmentMapper.class);var organizations=mock(OrganizationScopeApi.class);
  var project=new ProjectMasterDO();project.setId(100L);project.setTenantId(1L);project.setVersion(3L);project.setLifecycleStatus("ACTIVE");project.setCompanyId(11L);project.setDepartmentId(21L);
  when(projects.selectByIdForUpdate(100L)).thenReturn(project);when(projects.selectById(100L)).thenReturn(project);when(organizations.inspectRoleScope(any())).thenReturn(new UserCompanyDepartmentScopeRespDTO());when(organizations.lockRoleScope(any())).thenReturn(new UserCompanyDepartmentScopeRespDTO());
  var permissions=mock(PermissionApi.class);when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenReturn(true);
  var scope=mock(ProjectScopeApi.class);when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,7L,Set.of(100L),Set.of()));
  var tx=mock(TransactionTemplate.class);when(tx.execute(any())).thenAnswer(i->((TransactionCallback<?>)i.getArgument(0)).doInTransaction(mock(org.springframework.transaction.TransactionStatus.class)));
  var commands=mock(PlatformCommandExecutionApi.class);when(commands.execute(any(),any(),any(),any(),any())).thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,new ConstructionPlanRespVO()));
  var service=new ConstructionPlanApplicationService(mock(ConstructionPlanMapper.class),mock(ConstructionPlanRevisionMapper.class),commands,permissions,scope,
      new ProjectDurationOperatorApiImpl(projects,members,new ProjectManagementQualificationApiImpl(mock(ProjectParticipantFactApi.class),new ProjectRegionalManagerFactApiImpl(projects,organizations),projects,mock(cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectCurrentStageService.class))),mock(OperationAuditApi.class),tx,mock(cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateApi.class),mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.class));
  assertThrows(ServiceException.class,()->service.createInitial(new CreateInitialDurationCommand(100L,"DATE_RANGE",LocalDate.of(2026,10,1),LocalDate.of(2026,10,5),5,3L,"first-pm","a".repeat(64)),new ConstructionPlanApplicationService.Actor(1L,9L,"first-pm")));
  verify(organizations).lockRoleScope(any());
  verify(members).selectCurrentDurationProjectManagersForUpdate(any());
  verify(commands,never()).execute(any(),any(),any(),any(),any());
 }
}
