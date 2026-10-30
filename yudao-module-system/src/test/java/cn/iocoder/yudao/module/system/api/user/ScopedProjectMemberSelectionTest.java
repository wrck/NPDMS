package cn.iocoder.yudao.module.system.api.user;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.ScopedActiveUserPageQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScopedProjectMemberSelectionTest {
 AdminUserMapper users; ObjectProvider<ProjectMemberIdentityScopeProvider> providers; ProjectMemberIdentityScopeProvider owner;
 PermissionApi permissions; ActiveUserSelectionApiImpl api; cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper departments;
 @BeforeEach void setup(){
  users=mock(AdminUserMapper.class);providers=mock(ObjectProvider.class);owner=mock(ProjectMemberIdentityScopeProvider.class);permissions=mock(PermissionApi.class);
  api=new ActiveUserSelectionApiImpl(users,providers,permissions);departments=mock(cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper.class);org.springframework.test.util.ReflectionTestUtils.setField(api,"memberDepartments",departments);
  TenantContextHolder.setTenantId(1L);SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L),new MockHttpServletRequest());
  when(permissions.hasAnyPermissions(7L,"pms:project:query")).thenReturn(true);when(providers.getIfAvailable()).thenReturn(owner);
 }
 @AfterEach void clear(){TenantContextHolder.clear();SecurityContextHolder.clearContext();DataPermissionContextHolder.clear();}
 ActiveUserSelectionApi.MemberFilterQuery query(){return new ActiveUserSelectionApi.MemberFilterQuery(2,20," h06523 ",ProjectMemberIdentityScopeProvider.Filter.MANAGER);}
 @Test void resolvesScopeIndependentlyBeforeNarrowSqlAndRestoresDataPermissions(){
  when(owner.resolveCurrent(any())).thenAnswer(invocation->{assertNull(DataPermissionContextHolder.get());assertEquals(new ProjectMemberIdentityScopeProvider.Query(1L,7L,ProjectMemberIdentityScopeProvider.Filter.MANAGER),invocation.getArgument(0));return Set.of(2L);});
  when(users.selectScopedActiveUserCount(any())).thenAnswer(i->{assertFalse(DataPermissionContextHolder.get().enable());return 1L;});
  var person=new AdminUserDO();person.setId(2L);person.setUsername("h06523");person.setNickname("Other");person.setMobile("never-exposed");
  when(users.selectScopedActiveUserPage(any())).thenReturn(List.of(person));
  var page=api.pageProjectMembers(query());assertEquals(new ActiveUserSelectionApi.MemberOption(2L,"h06523","Other",null,null),page.getList().getFirst());
  var captured=ArgumentCaptor.forClass(ScopedActiveUserPageQuery.class);verify(users).selectScopedActiveUserPage(captured.capture());
  assertEquals(Set.of(2L),captured.getValue().userIds());assertEquals("h06523",captured.getValue().keyword());assertEquals(20,captured.getValue().offset());assertNull(DataPermissionContextHolder.get());
  assertEquals(List.of("pageNo","pageSize","keyword","field"),Arrays.stream(ActiveUserSelectionApi.MemberFilterQuery.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
 }
 @Test void noPermissionMissingIdentityOrForeignTenantNeverResolvesOrReadsDirectory(){
  when(permissions.hasAnyPermissions(7L,"pms:project:query")).thenReturn(false);assertThrows(RuntimeException.class,()->api.pageProjectMembers(query()));
  when(permissions.hasAnyPermissions(7L,"pms:project:query")).thenReturn(true);TenantContextHolder.setTenantId(2L);assertThrows(RuntimeException.class,()->api.pageProjectMembers(query()));
  TenantContextHolder.setTenantId(1L);SecurityContextHolder.clearContext();assertThrows(RuntimeException.class,()->api.pageProjectMembers(query()));
  verifyNoInteractions(users,owner);
 }
 @Test void emptyUnavailableOrInvalidOwnerNeverOpensDirectory(){
  when(owner.resolveCurrent(any())).thenReturn(Set.of());assertTrue(api.pageProjectMembers(query()).getList().isEmpty());
  when(owner.resolveCurrent(any())).thenReturn(null);assertThrows(IllegalStateException.class,()->api.pageProjectMembers(query()));
  when(owner.resolveCurrent(any())).thenThrow(new IllegalStateException("owner offline"));assertThrows(IllegalStateException.class,()->api.pageProjectMembers(query()));
  when(providers.getIfAvailable()).thenReturn(null);assertThrows(IllegalStateException.class,()->api.pageProjectMembers(query()));verifyNoInteractions(users);assertNull(DataPermissionContextHolder.get());
 }
 @Test void invalidFieldPageAndKeywordRejectBeforeScopeLookup(){
  for(var q:List.of(new ActiveUserSelectionApi.MemberFilterQuery(0,20,null,ProjectMemberIdentityScopeProvider.Filter.MANAGER),new ActiveUserSelectionApi.MemberFilterQuery(1,101,null,ProjectMemberIdentityScopeProvider.Filter.MANAGER),new ActiveUserSelectionApi.MemberFilterQuery(1,20,"x".repeat(65),ProjectMemberIdentityScopeProvider.Filter.MANAGER),new ActiveUserSelectionApi.MemberFilterQuery(1,20,null,null)))assertThrows(RuntimeException.class,()->api.pageProjectMembers(q));
  verifyNoInteractions(users,owner);
 }
 @Test void sqlFailureDoesNotLeaveDataPermissionDisabled(){
  when(owner.resolveCurrent(any())).thenReturn(Set.of(2L));when(users.selectScopedActiveUserCount(any())).thenThrow(new IllegalStateException("database offline"));
  assertThrows(IllegalStateException.class,()->api.pageProjectMembers(query()));assertNull(DataPermissionContextHolder.get());
 }

 @Test void departmentLookupAndDisplayNeverDisableDepartmentPermissions(){
  when(owner.resolveCurrent(any())).thenReturn(Set.of(2L,6L));
  when(users.selectScopedActiveUserDepartmentIds(any())).thenAnswer(i->{assertFalse(DataPermissionContextHolder.get().enable());return Set.of(10L,20L);});
  var dept=new cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO();dept.setId(10L);dept.setName("可见部门");
  when(departments.selectProjectMemberDepartments(any())).thenAnswer(i->{assertNull(DataPermissionContextHolder.get());var q=(cn.iocoder.yudao.module.system.dal.mysql.dept.query.ProjectMemberDepartmentsQuery)i.getArgument(0);assertEquals(1L,q.tenantId());return List.of(dept);});
  when(users.selectScopedActiveUserCount(any())).thenAnswer(i->{assertFalse(DataPermissionContextHolder.get().enable());return 1L;});
  var user=new AdminUserDO();user.setId(2L);user.setDeptId(10L);user.setUsername("account");when(users.selectScopedActiveUserPage(any())).thenReturn(List.of(user));
  var page=api.pageProjectMembers(query());assertEquals("可见部门",page.getList().getFirst().deptName());
  var captured=ArgumentCaptor.forClass(ScopedActiveUserPageQuery.class);verify(users).selectScopedActiveUserPage(captured.capture());assertEquals(Set.of(10L),captured.getValue().matchedDepartmentIds());
  verify(departments,times(1)).selectProjectMemberDepartments(any());assertNull(DataPermissionContextHolder.get());
 }
 @Test void noKeywordSkipsDepartmentSearchAndEmptyPageSkipsNameDisplay(){
  when(owner.resolveCurrent(any())).thenReturn(Set.of(2L));when(users.selectScopedActiveUserCount(any())).thenReturn(0L);
  assertTrue(api.pageProjectMembers(new ActiveUserSelectionApi.MemberFilterQuery(1,20,null,ProjectMemberIdentityScopeProvider.Filter.MANAGER)).getList().isEmpty());
  verify(users,never()).selectScopedActiveUserDepartmentIds(any());verifyNoInteractions(departments);
 }

 @Test void realDataPermissionAdvisorRestoresOrdinaryOwnerAndDepartmentScopeInsideAnIgnoringCaller(){
  var factory=new org.springframework.aop.framework.ProxyFactory(api);factory.setProxyTargetClass(true);
  factory.addAdvisor(new cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionAnnotationAdvisor());
  var proxy=(ActiveUserSelectionApi)factory.getProxy();
  when(owner.resolveCurrent(any())).thenAnswer(i->{assertTrue(DataPermissionContextHolder.get().enable());return Set.of(2L);});
  when(users.selectScopedActiveUserDepartmentIds(any())).thenAnswer(i->{assertFalse(DataPermissionContextHolder.get().enable());return Set.of(10L);});
  when(departments.selectProjectMemberDepartments(any())).thenAnswer(i->{assertTrue(DataPermissionContextHolder.get().enable());return List.of();});
  when(users.selectScopedActiveUserCount(any())).thenAnswer(i->{assertFalse(DataPermissionContextHolder.get().enable());return 0L;});
  cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils.executeIgnore(()->{
   assertFalse(DataPermissionContextHolder.get().enable());assertTrue(proxy.pageProjectMembers(query()).getList().isEmpty());assertFalse(DataPermissionContextHolder.get().enable());
  });
  assertNull(DataPermissionContextHolder.get());
 }

 @Test void usernameMatchOnlyFetchesPageDepartmentNamesMissingFromTheSameRequestSearch(){
  when(owner.resolveCurrent(any())).thenReturn(Set.of(2L));when(users.selectScopedActiveUserDepartmentIds(any())).thenReturn(Set.of(10L,20L));
  when(departments.selectProjectMemberDepartments(any())).thenAnswer(i->{assertNull(DataPermissionContextHolder.get());var q=(cn.iocoder.yudao.module.system.dal.mysql.dept.query.ProjectMemberDepartmentsQuery)i.getArgument(0);var d=new cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO();if(q.keyword()!=null){d.setId(10L);d.setName("匹配部门");}else{assertEquals(Set.of(20L),q.departmentIds());d.setId(20L);d.setName("用户当前部门");}return List.of(d);});
  when(users.selectScopedActiveUserCount(any())).thenReturn(1L);var user=new AdminUserDO();user.setId(2L);user.setDeptId(20L);when(users.selectScopedActiveUserPage(any())).thenReturn(List.of(user));
  assertEquals("用户当前部门",api.pageProjectMembers(query()).getList().getFirst().deptName());verify(departments,times(2)).selectProjectMemberDepartments(any());
 }
}
