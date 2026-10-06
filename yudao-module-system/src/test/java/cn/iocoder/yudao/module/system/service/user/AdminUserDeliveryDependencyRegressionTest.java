package cn.iocoder.yudao.module.system.service.user;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.*;
import cn.iocoder.yudao.module.system.dal.mysql.dept.UserPostMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserRoleDO;
import cn.iocoder.yudao.module.system.service.permission.PermissionAssociationService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.mq.producer.user.AdminUserProducer;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminUserDeliveryDependencyRegressionTest {
 AdminUserServiceImpl service;AdminUserMapper users;UserPostMapper posts;UserRoleMapper roles;RoleMenuMapper menus;OAuth2TokenService tokens;AdminUserProducer producer;
 @BeforeEach void setup(){service=new AdminUserServiceImpl();users=mock(AdminUserMapper.class);posts=mock(UserPostMapper.class);roles=mock(UserRoleMapper.class);menus=mock(RoleMenuMapper.class);tokens=mock(OAuth2TokenService.class);producer=mock(AdminUserProducer.class);ReflectionTestUtils.setField(service,"userMapper",users);ReflectionTestUtils.setField(service,"userPostMapper",posts);ReflectionTestUtils.setField(service,"permissionService",new PermissionAssociationService(menus,roles));ReflectionTestUtils.setField(service,"oauth2TokenService",tokens);ReflectionTestUtils.setField(service,"adminUserProducer",producer);}
 AdminUserDO user(){return new AdminUserDO().setId(9L).setNickname("synthetic").setAvatar("local.png").setStatus(0);}
 @Test void singleDeletionKeepsAssociationAndPostCleanup(){when(users.selectById(9L)).thenReturn(user());service.deleteUser(9L);var order=inOrder(users,roles,posts);order.verify(users).deleteById(9L);order.verify(roles).deleteListByUserId(9L);order.verify(posts).deleteByUserId(9L);verifyNoInteractions(producer,tokens,menus);}
 @Test void nonexistentUserCannotDeleteRelations(){assertThrows(RuntimeException.class,()->service.deleteUser(9L));verify(users,never()).deleteById(9L);verifyNoInteractions(roles,posts,producer);}
 @Test void batchDeletionCleansEveryUser(){service.deleteUserList(List.of(9L,10L));verify(users).deleteByIds(List.of(9L,10L));for(Long id:List.of(9L,10L)){verify(roles).deleteListByUserId(id);verify(posts).deleteByUserId(id);}verifyNoInteractions(menus,producer);}
 @Test void cleanupFailurePropagatesAndStopsPostDeletion(){when(users.selectById(9L)).thenReturn(user());doThrow(new IllegalStateException("synthetic failure")).when(roles).deleteListByUserId(9L);assertThrows(IllegalStateException.class,()->service.deleteUser(9L));verifyNoInteractions(posts);}
 @Test void emptyRoleSelectionReturnsEmptyWithoutWideningQuery(){var query=new UserPageReqVO();query.setRoleId(7L);when(roles.selectListByRoleIds(Set.of(7L))).thenReturn(List.of());assertEquals(0L,service.getUserPage(query).getTotal());verifyNoInteractions(users);}
 @Test void roleSelectionUsesExactUsers(){var query=new UserPageReqVO();query.setRoleId(7L);when(roles.selectListByRoleIds(Set.of(7L))).thenReturn(List.of(new UserRoleDO().setUserId(9L).setRoleId(7L)));when(users.selectPage(query,Set.of(),Set.of(9L))).thenReturn(new PageResult<>(List.of(user()),1L));assertEquals(1L,service.getUserPage(query).getTotal());verify(users).selectPage(query,Set.of(),Set.of(9L));}
 @Test void disablingUserStillRevokesTokens(){when(users.selectById(9L)).thenReturn(user());service.updateUserStatus(9L,1);verify(tokens).removeAccessToken(9L,2);verifyNoInteractions(roles);}
 @Test void enablingUserDoesNotRevokeTokens(){when(users.selectById(9L)).thenReturn(user());service.updateUserStatus(9L,0);verifyNoInteractions(tokens);}
 @Test void unchangedProfileSendsNoNotification(){when(users.selectById(9L)).thenReturn(user());var request=new UserProfileUpdateReqVO();request.setNickname("synthetic");request.setAvatar("local.png");service.updateUserProfile(9L,request);verifyNoInteractions(producer);}
 @Test void changedProfileRetainsExactNotification(){when(users.selectById(9L)).thenReturn(user());var request=new UserProfileUpdateReqVO();request.setNickname("synthetic changed");service.updateUserProfile(9L,request);verify(producer).sendUserProfileUpdateMessage(9L,"synthetic changed",null);}
}
