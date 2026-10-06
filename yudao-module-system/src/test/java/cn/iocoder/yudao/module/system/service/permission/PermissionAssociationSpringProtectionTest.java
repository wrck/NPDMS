package cn.iocoder.yudao.module.system.service.permission;
import cn.iocoder.yudao.module.system.dal.mysql.permission.*;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import cn.iocoder.yudao.module.system.service.user.AdminUserServiceImpl;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.UserPostMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PermissionAssociationSpringProtectionTest {
 @Configuration @EnableCaching @EnableTransactionManagement(proxyTargetClass=true)
 static class Config {
  @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:associationProtection;DB_CLOSE_DELAY=-1","sa","");}
  @Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}
  @Bean CacheManager cacheManager(){return new ConcurrentMapCacheManager(RedisKeyConstants.MENU_ROLE_ID_LIST,RedisKeyConstants.USER_ROLE_ID_LIST);}
  @Bean cn.iocoder.yudao.module.system.service.dept.DeptService deptService(){return mock(cn.iocoder.yudao.module.system.service.dept.DeptService.class);}
  @Bean cn.iocoder.yudao.module.system.service.dept.PostService postService(){return mock(cn.iocoder.yudao.module.system.service.dept.PostService.class);}
  @Bean org.springframework.security.crypto.password.PasswordEncoder passwordEncoder(){return mock(org.springframework.security.crypto.password.PasswordEncoder.class);}
  @Bean cn.iocoder.yudao.module.system.service.tenant.TenantService tenantService(){return mock(cn.iocoder.yudao.module.system.service.tenant.TenantService.class);}
  @Bean cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService oauth2TokenService(){return mock(cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService.class);}
  @Bean cn.iocoder.yudao.module.infra.api.config.ConfigApi configApi(){return mock(cn.iocoder.yudao.module.infra.api.config.ConfigApi.class);}
  @Bean cn.iocoder.yudao.module.system.mq.producer.user.AdminUserProducer adminUserProducer(){return mock(cn.iocoder.yudao.module.system.mq.producer.user.AdminUserProducer.class);}
  @Bean AdminUserMapper adminUserMapper(){return mock(AdminUserMapper.class);}
  @Bean UserPostMapper userPostMapper(){return mock(UserPostMapper.class);}
  @Bean AdminUserServiceImpl adminUsers(AdminUserMapper users,UserPostMapper posts,PermissionAssociationService associations){var service=new AdminUserServiceImpl();ReflectionTestUtils.setField(service,"userMapper",users);ReflectionTestUtils.setField(service,"userPostMapper",posts);ReflectionTestUtils.setField(service,"permissionService",associations);return service;}
  @Bean RoleMenuMapper roleMenuMapper(){return mock(RoleMenuMapper.class);}
  @Bean UserRoleMapper userRoleMapper(){return mock(UserRoleMapper.class);}
  @Bean PermissionAssociationService associations(RoleMenuMapper rm,UserRoleMapper ur){return new PermissionAssociationService(rm,ur);}
 }
 AnnotationConfigApplicationContext context;JdbcTemplate jdbc;PermissionAssociationService service;RoleMenuMapper rm;UserRoleMapper ur;CacheManager caches;
 @BeforeEach void setup(){context=new AnnotationConfigApplicationContext(Config.class);jdbc=new JdbcTemplate(context.getBean(DataSource.class));jdbc.execute("CREATE TABLE IF NOT EXISTS test_association(kind VARCHAR(8),id BIGINT)");jdbc.update("DELETE FROM test_association");jdbc.update("INSERT INTO test_association VALUES('UR',9),('RM',9)");service=context.getBean(PermissionAssociationService.class);rm=context.getBean(RoleMenuMapper.class);ur=context.getBean(UserRoleMapper.class);caches=context.getBean(CacheManager.class);}
 @AfterEach void close(){context.close();}
 void seedCache(){for(String name:new String[]{RedisKeyConstants.MENU_ROLE_ID_LIST,RedisKeyConstants.USER_ROLE_ID_LIST}){caches.getCache(name).put(9L,"stale");caches.getCache(name).put(10L,"other");}}
 @Test void roleDeletionCommitsBothRelationsAndEvictsBothCaches(){seedCache();doAnswer(x->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());return jdbc.update("DELETE FROM test_association WHERE kind='UR' AND id=9");}).when(ur).deleteListByRoleId(9L);doAnswer(x->jdbc.update("DELETE FROM test_association WHERE kind='RM' AND id=9")).when(rm).deleteListByRoleId(9L);service.processRoleDeleted(9L);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_association",Integer.class));for(String name:new String[]{RedisKeyConstants.MENU_ROLE_ID_LIST,RedisKeyConstants.USER_ROLE_ID_LIST}){assertNull(caches.getCache(name).get(9L));assertNull(caches.getCache(name).get(10L));}assertFalse(TransactionSynchronizationManager.isActualTransactionActive());}
 @Test void failedRoleCleanupRollsBackFirstSqlAndRetainsCache(){seedCache();doAnswer(x->jdbc.update("DELETE FROM test_association WHERE kind='UR' AND id=9")).when(ur).deleteListByRoleId(9L);doThrow(new IllegalStateException("synthetic second write failure")).when(rm).deleteListByRoleId(9L);assertThrows(IllegalStateException.class,()->service.processRoleDeleted(9L));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM test_association",Integer.class));assertNotNull(caches.getCache(RedisKeyConstants.USER_ROLE_ID_LIST).get(9L));}
 @Test void menuCleanupEvictsOnlyTargetMenu(){seedCache();service.processMenuDeleted(9L);verify(rm).deleteListByMenuId(9L);assertNull(caches.getCache(RedisKeyConstants.MENU_ROLE_ID_LIST).get(9L));assertNotNull(caches.getCache(RedisKeyConstants.MENU_ROLE_ID_LIST).get(10L));assertNotNull(caches.getCache(RedisKeyConstants.USER_ROLE_ID_LIST).get(9L));}
 @Test void userCleanupEvictsOnlyTargetUser(){seedCache();service.processUserDeleted(9L);verify(ur).deleteListByUserId(9L);assertNull(caches.getCache(RedisKeyConstants.USER_ROLE_ID_LIST).get(9L));assertNotNull(caches.getCache(RedisKeyConstants.USER_ROLE_ID_LIST).get(10L));assertNotNull(caches.getCache(RedisKeyConstants.MENU_ROLE_ID_LIST).get(9L));}
 @Test void userDeletionCommitsUserRoleAndPostSql(){var users=context.getBean(AdminUserMapper.class);var posts=context.getBean(UserPostMapper.class);jdbc.update("INSERT INTO test_association VALUES('USER',9),('POST',9)");when(users.selectById(9L)).thenReturn(new AdminUserDO().setId(9L));doAnswer(x->jdbc.update("DELETE FROM test_association WHERE kind='USER' AND id=9")).when(users).deleteById(9L);doAnswer(x->jdbc.update("DELETE FROM test_association WHERE kind='UR' AND id=9")).when(ur).deleteListByUserId(9L);doAnswer(x->jdbc.update("DELETE FROM test_association WHERE kind='POST' AND id=9")).when(posts).deleteByUserId(9L);context.getBean(AdminUserServiceImpl.class).deleteUser(9L);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_association",Integer.class));assertEquals("RM",jdbc.queryForObject("SELECT kind FROM test_association",String.class));}
 @Test void userAssociationFailureRollsBackUserDeletion(){var users=context.getBean(AdminUserMapper.class);var posts=context.getBean(UserPostMapper.class);jdbc.update("INSERT INTO test_association VALUES('USER',9)");when(users.selectById(9L)).thenReturn(new AdminUserDO().setId(9L));doAnswer(x->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());return jdbc.update("DELETE FROM test_association WHERE kind='USER' AND id=9");}).when(users).deleteById(9L);doThrow(new IllegalStateException("synthetic cleanup failure")).when(ur).deleteListByUserId(9L);assertThrows(IllegalStateException.class,()->context.getBean(AdminUserServiceImpl.class).deleteUser(9L));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_association WHERE kind='USER'",Integer.class));verifyNoInteractions(posts);}
 @Test void batchCleanupFailureRollsBackEarlierUserAndRelations(){var users=context.getBean(AdminUserMapper.class);var posts=context.getBean(UserPostMapper.class);jdbc.update("INSERT INTO test_association VALUES('USER',9),('USER',10),('POST',9)");doAnswer(x->jdbc.update("DELETE FROM test_association WHERE kind='USER'")).when(users).deleteByIds(List.of(9L,10L));doAnswer(x->jdbc.update("DELETE FROM test_association WHERE kind='UR' AND id=9")).when(ur).deleteListByUserId(9L);doAnswer(x->jdbc.update("DELETE FROM test_association WHERE kind='POST' AND id=9")).when(posts).deleteByUserId(9L);doThrow(new IllegalStateException("synthetic second user failure")).when(ur).deleteListByUserId(10L);assertThrows(IllegalStateException.class,()->context.getBean(AdminUserServiceImpl.class).deleteUserList(List.of(9L,10L)));assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM test_association",Integer.class));}

}
