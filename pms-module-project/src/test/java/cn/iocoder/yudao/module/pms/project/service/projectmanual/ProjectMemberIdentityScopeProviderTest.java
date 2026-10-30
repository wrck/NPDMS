package cn.iocoder.yudao.module.pms.project.service.projectmanual;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.ProjectMemberIdentityScopeProvider;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class ProjectMemberIdentityScopeProviderTest {
 public cn.iocoder.yudao.framework.common.pojo.CommonResult<String> read(){return cn.iocoder.yudao.framework.common.pojo.CommonResult.success("ok");}
 @Test void realOfficeInterceptorContextDoesNotBecomeOrdinaryMemberCandidateScope() throws Exception {
  var search=mock(cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectSearchService.class);
  var interceptor=new cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectReadInterceptor(search);
  when(search.validate(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectReadContext.Scope(1L,7L,12L));
  when(scopes.prepareViewReadScope(any())).thenReturn(scope());when(members.selectMemberFilterUserIds(any())).thenReturn(Set.of(2L));
  var request=new MockHttpServletRequest("GET","/admin-api/api/v1/pms/projects/member-filter-options");
  request.addHeader(cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectReadInterceptor.PROJECT_HEADER,"12");
  request.addHeader(cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectReadInterceptor.NAME_HEADER,"exact-name");
  var response=new org.springframework.mock.web.MockHttpServletResponse();
  var handler=new org.springframework.web.method.HandlerMethod(this,getClass().getMethod("read"));
  try {
   assertTrue(interceptor.preHandle(request,response,handler));
   assertNotNull(cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectReadContext.current());
   assertTrue(provider.resolveCurrent(q(ProjectMemberIdentityScopeProvider.Filter.MANAGER)).isEmpty());
   verifyNoInteractions(scopes,members);
  } finally {interceptor.afterCompletion(request,response,handler,null);}
  assertNull(cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectReadContext.current());
  assertEquals(Set.of(2L),provider.resolveCurrent(q(ProjectMemberIdentityScopeProvider.Filter.MANAGER)));
 }
 @Test void officeFlagFromOwnerCannotBecomeOrdinaryFullCandidateScope(){
  when(scopes.prepareViewReadScope(any())).thenReturn(new ProjectViewReadScopeApi.ReadScope(1L,7L,ProjectViewReadScopeApi.Mode.SCOPED,LocalDateTime.now(),List.of(new ProjectViewReadScopeApi.Tree(10L,7L)),List.of(),12L));
  assertTrue(provider.resolveCurrent(q(ProjectMemberIdentityScopeProvider.Filter.MANAGER)).isEmpty());verifyNoInteractions(members);
 }

 ProjectTreeScopeService scopes;ProjectMemberAssignmentMapper members;PermissionApi permissions;ProjectMemberIdentityScopeProviderImpl provider;
 @BeforeEach void setup(){scopes=mock(ProjectTreeScopeService.class);members=mock(ProjectMemberAssignmentMapper.class);permissions=mock(PermissionApi.class);provider=new ProjectMemberIdentityScopeProviderImpl(scopes,members,permissions);TenantContextHolder.setTenantId(1L);SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L),new MockHttpServletRequest());when(permissions.hasAnyPermissions(7L,"pms:project:query")).thenReturn(true);}
 @AfterEach void clear(){TenantContextHolder.clear();SecurityContextHolder.clearContext();}
 ProjectMemberIdentityScopeProvider.Query q(ProjectMemberIdentityScopeProvider.Filter f){return new ProjectMemberIdentityScopeProvider.Query(1L,7L,f);}
 ProjectViewReadScopeApi.ReadScope scope(){return new ProjectViewReadScopeApi.ReadScope(1L,7L,ProjectViewReadScopeApi.Mode.SCOPED,LocalDateTime.of(2026,10,28,12,0),List.of(new ProjectViewReadScopeApi.Tree(10L,7L)));}
 @Test void preservesAllManagementAliasesAndResolvesScopeWithoutProjectIdExpansion(){
  var scope=scope();when(scopes.prepareViewReadScope(any())).thenReturn(scope);when(members.selectMemberFilterUserIds(any())).thenReturn(Set.of(2L));
  for(var f:ProjectMemberIdentityScopeProvider.Filter.values()){assertEquals(Set.of(2L),provider.resolveCurrent(q(f)));var expected=switch(f){case MANAGER->ProjectMemberRoles.MANAGEMENT_CODES;case SERVICE_MANAGER->ProjectMemberRoles.SERVICE_CODES;case SALES->ProjectMemberRoles.SALES_CODES;};verify(members).selectMemberFilterUserIds(argThat(query->query.roleCodes().equals(expected)&&query.readScope().projectScope()==scope&&query.tenantId().equals(1L)&&query.effectiveAt().equals(scope.effectiveAt())));}
  verify(scopes,never()).resolveAllFullProjectIds(any(),any(),any());
 }
 @Test void forgedActorTenantMissingPermissionAndIdentityNeverReadScope(){
  assertThrows(SecurityException.class,()->provider.resolveCurrent(new ProjectMemberIdentityScopeProvider.Query(1L,8L,ProjectMemberIdentityScopeProvider.Filter.MANAGER)));
  assertThrows(SecurityException.class,()->provider.resolveCurrent(new ProjectMemberIdentityScopeProvider.Query(2L,7L,ProjectMemberIdentityScopeProvider.Filter.MANAGER)));
  when(permissions.hasAnyPermissions(7L,"pms:project:query")).thenReturn(false);assertThrows(SecurityException.class,()->provider.resolveCurrent(q(ProjectMemberIdentityScopeProvider.Filter.MANAGER)));
  SecurityContextHolder.clearContext();assertThrows(SecurityException.class,()->provider.resolveCurrent(q(ProjectMemberIdentityScopeProvider.Filter.MANAGER)));verifyNoInteractions(scopes,members);
 }
 @Test void emptyScopeDoesNotScanMembersAndForeignOwnerContextFailsClosed(){
  when(scopes.prepareViewReadScope(any())).thenReturn(new ProjectViewReadScopeApi.ReadScope(1L,7L,ProjectViewReadScopeApi.Mode.SCOPED,LocalDateTime.now(),List.of()));
  assertTrue(provider.resolveCurrent(q(ProjectMemberIdentityScopeProvider.Filter.MANAGER)).isEmpty());verifyNoInteractions(members);
  when(scopes.prepareViewReadScope(any())).thenReturn(new ProjectViewReadScopeApi.ReadScope(2L,7L,ProjectViewReadScopeApi.Mode.TENANT_ADMIN,LocalDateTime.now(),List.of()));
  assertThrows(SecurityException.class,()->provider.resolveCurrent(q(ProjectMemberIdentityScopeProvider.Filter.MANAGER)));verifyNoInteractions(members);
 }
 @Test void ownerFailureDoesNotFallBackToTenantAdminOrSnapshots(){when(scopes.prepareViewReadScope(any())).thenThrow(new IllegalStateException("owner offline"));assertThrows(IllegalStateException.class,()->provider.resolveCurrent(q(ProjectMemberIdentityScopeProvider.Filter.MANAGER)));verifyNoInteractions(members);}
}
