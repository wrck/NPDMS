package cn.iocoder.yudao.module.system.api.permission;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.datapermission.core.aop.*;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.permission.dto.*;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataPermission
class OrganizationRoleScopeApiTest {
 OrganizationScopeApi proxy;UserCompanyDepartmentScopeMapper mapper;
 OrganizationRoleScopeQuery query=new OrganizationRoleScopeQuery(1L,9L,11L,21L,Set.of("SERVICE_MANAGER","SERVICE_MANAGER_L1","SERVICE_MANAGER_L2"));
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(1L);var user=new LoginUser();user.setId(9L);user.setTenantId(1L);SecurityFrameworkUtils.setLoginUser(user,new MockHttpServletRequest());
  mapper=mock(UserCompanyDepartmentScopeMapper.class);var target=new OrganizationScopeApiImpl();ReflectionTestUtils.setField(target,"scopeMapper",mapper);
  var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvisor(new DataPermissionAnnotationAdvisor());proxy=(OrganizationScopeApi)factory.getProxy();
 }
 @AfterEach void cleanup(){TenantContextHolder.clear();SecurityContextHolder.clearContext();DataPermissionContextHolder.clear();}
 @Test void realAdvisorOnlyDisablesOrdinaryDirectoryScopeForExactRoleScopeMethodsAndRestoresCaller(){
  when(mapper.selectRoleScope(any())).thenAnswer(i->{assertFalse(DataPermissionContextHolder.get().enable());return new UserCompanyDepartmentScopeDO();});
  when(mapper.selectRoleScopeForUpdate(any())).thenAnswer(i->{assertFalse(DataPermissionContextHolder.get().enable());return new UserCompanyDepartmentScopeDO();});
  var ordinary=OrganizationRoleScopeApiTest.class.getAnnotation(DataPermission.class);DataPermissionContextHolder.add(ordinary);
  assertNotNull(proxy.inspectRoleScope(query));assertSame(ordinary,DataPermissionContextHolder.get());
  assertNotNull(proxy.lockRoleScope(query));assertSame(ordinary,DataPermissionContextHolder.get());
  // 普通范围列表仍沿用调用方DP，并不被新方法的ignore泄漏影响。
  when(mapper.selectActiveListByUserId(any())).thenAnswer(i->{assertTrue(DataPermissionContextHolder.get().enable());return List.of();});
  proxy.getActiveScopes(9L);assertSame(ordinary,DataPermissionContextHolder.get());
 }
 @Test void advisorRestoresPermissionStackAfterOwnerFailure(){
  when(mapper.selectRoleScopeForUpdate(any())).thenAnswer(i->{assertFalse(DataPermissionContextHolder.get().enable());throw new IllegalStateException("query failed");});
  assertThrows(IllegalStateException.class,()->proxy.lockRoleScope(query));assertNull(DataPermissionContextHolder.get());
 }
 @Test void tenantAndActorCannotBeProvidedByAnotherClientSubject(){
  assertThrows(ServiceException.class,()->proxy.inspectRoleScope(new OrganizationRoleScopeQuery(2L,9L,11L,21L,Set.of("SERVICE_MANAGER"))));
  assertThrows(ServiceException.class,()->proxy.inspectRoleScope(new OrganizationRoleScopeQuery(1L,10L,11L,21L,Set.of("SERVICE_MANAGER"))));
  verifyNoInteractions(mapper);assertNull(DataPermissionContextHolder.get());
 }
 @Test void emptyRoleOrMissingCompanyFailsBeforeQuery(){
  assertThrows(ServiceException.class,()->proxy.inspectRoleScope(new OrganizationRoleScopeQuery(1L,9L,11L,21L,Set.of())));
  assertThrows(ServiceException.class,()->proxy.inspectRoleScope(new OrganizationRoleScopeQuery(1L,9L,null,21L,Set.of("SERVICE_MANAGER"))));
  verifyNoInteractions(mapper);
 }
 @Test void internalSubjectReadLocksApplicantWhileOrdinaryEndpointsRemainSelfOnly(){
  var applicant=new OrganizationRoleScopeQuery(1L,10L,11L,21L,Set.of("SERVICE_MANAGER"));
  when(mapper.selectRoleScopeForUpdate(applicant)).thenAnswer(i->{assertFalse(DataPermissionContextHolder.get().enable());return new UserCompanyDepartmentScopeDO();});
  assertNotNull(proxy.lockSubjectRoleScope(applicant));
  assertEquals(9L,SecurityFrameworkUtils.getLoginUserId());assertNull(DataPermissionContextHolder.get());
  assertThrows(ServiceException.class,()->proxy.inspectRoleScope(applicant));
  assertThrows(ServiceException.class,()->proxy.lockRoleScope(applicant));
  verify(mapper).selectRoleScopeForUpdate(applicant);
  verify(mapper,never()).selectRoleScope(any());
 }
 @Test void internalSubjectReadRejectsNoPrincipalIgnoredTenantAndForeignTenant(){
  SecurityContextHolder.clearContext();assertThrows(ServiceException.class,()->proxy.lockSubjectRoleScope(query));
  SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(9L).setTenantId(1L),new MockHttpServletRequest());
  TenantContextHolder.setIgnore(true);assertThrows(ServiceException.class,()->proxy.lockSubjectRoleScope(query));
  TenantContextHolder.setIgnore(false);TenantContextHolder.setTenantId(2L);assertThrows(ServiceException.class,()->proxy.lockSubjectRoleScope(query));
  verifyNoInteractions(mapper);
 }
}
