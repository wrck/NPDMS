package cn.iocoder.yudao.module.system.api.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMigrationMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.UserRoleMigrationQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.junit.jupiter.api.*;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserRoleMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemUserRoleMigrationTransactionTest {
    private AnnotationConfigApplicationContext context;
    private SystemUserRoleMigrationApi api;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); context=new AnnotationConfigApplicationContext(Config.class);
        var dataSource=context.getBean(DataSource.class);
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql")).execute(dataSource);
        jdbc=new JdbcTemplate(dataSource); api=context.getBean(SystemUserRoleMigrationApi.class);
        tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,status) VALUES(1,1,'c02791','旧姓名',1)");
        jdbc.update("INSERT INTO system_role(id,tenant_id,name,code,sort,status,type) VALUES(2,1,'普通用户','user',0,0,2)");
    }
    @AfterEach void close() {context.close();TenantContextHolder.clear();}
    private Request request(Long target) {return new Request(1L,"integration:1:12",List.of(new Entry("T_USER_ROLE","271",1L,2L,"user",target)));}
    @Test void sameRoundPreviewUsesPlannedReferencesWithoutWritingOrWeakeningApply() {
        var input=new Request(1L,"integration:1:12",List.of(new Entry("T_USER_ROLE","271",-1L,-2L,"projectViewer",null),
                new Entry("FND_USER_ROLE","82:6",-1L,-2L,"projectViewer",null)));
        var preceding=new SystemAccountMigrationPreview(1L,input.owner(),Set.of(-1L),Map.of(-2L,"projectViewer"),Set.of());
        var result=api.previewAfterFoundation(new FoundationPreview(input,preceding));
        assertEquals(result.getFirst().after(),result.getLast().after());assertNull(result.getFirst().after().id());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role",Integer.class));
        assertThrows(IllegalArgumentException.class,()->api.preview(input));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->api.apply(input)));
        assertThrows(IllegalArgumentException.class,()->api.previewAfterFoundation(new FoundationPreview(request(null),
                new SystemAccountMigrationPreview(2L,input.owner(),Set.of(-1L),Map.of(),Set.of()))));
        jdbc.update("UPDATE system_users SET tenant_id=2 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->api.previewAfterFoundation(new FoundationPreview(request(null),preceding)));
    }
    @Test void aliasesCreateOneFormalRelationWithNoFabricatedAuditOrOfficeAuthorization() {
        var input=new Request(1L,"integration:1:12",List.of(new Entry("T_USER_ROLE","271",1L,2L,"user",null),new Entry("FND_USER_ROLE","82:3",1L,2L,"user",null)));
        var results=tx.execute(status->api.apply(input));
        assertEquals(results.getFirst().after(),results.getLast().after());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role",Integer.class));
        var relation=jdbc.queryForMap("SELECT tenant_id,creator,create_time,updater,update_time FROM system_user_role");
        assertEquals(1L,((Number)relation.get("tenant_id")).longValue());
        assertNull(relation.get("creator"));assertNull(relation.get("create_time"));assertNull(relation.get("updater"));assertNull(relation.get("update_time"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_role_menu",Integer.class));
        assertEquals("UNCHANGED",tx.execute(status->api.apply(request(results.getFirst().after().id()))).getFirst().action());
    }
    @Test void existingRolesAndTheirAuditArePreservedWhileApprovedSuperAdminAssignmentUsesExistingRole() {
        jdbc.update("INSERT INTO system_role(id,tenant_id,name,code,sort,status,type) VALUES(3,1,'超级管理员','super_admin',0,0,1)");
        jdbc.update("INSERT INTO system_user_role(id,tenant_id,user_id,role_id,creator) VALUES(99,1,1,2,'current-owner')");
        tx.execute(status->api.apply(new Request(1L,"integration:1:12",List.of(new Entry("T_USER_ROLE","8",1L,3L,"super_admin",null)))));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role WHERE user_id=1",Integer.class));
        assertEquals("current-owner",jdbc.queryForObject("SELECT creator FROM system_user_role WHERE id=99",String.class));
        assertEquals("UNCHANGED",api.preview(request(null)).getFirst().action());
    }
    @Test void foreignOrDisabledReferencesAndChangedRoleCodesCannotBeGranted() {
        jdbc.update("UPDATE system_role SET tenant_id=2 WHERE id=2");
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->api.apply(request(null))));
        jdbc.update("UPDATE system_role SET tenant_id=1,status=1 WHERE id=2");
        assertThrows(IllegalArgumentException.class,()->api.preview(request(null)));
        jdbc.update("UPDATE system_role SET status=0,code='other' WHERE id=2");
        assertThrows(IllegalArgumentException.class,()->api.preview(request(null)));
        jdbc.update("UPDATE system_role SET code='user' WHERE id=2");jdbc.update("UPDATE system_users SET tenant_id=2 WHERE id=1");
        assertThrows(IllegalArgumentException.class,()->api.preview(request(null)));
        var mapper=context.getBean(UserRoleMigrationMapper.class);
        assertTrue(mapper.selectUserIds(new UserRoleMigrationQuery(1L,Set.of(),Set.of())).isEmpty());
        assertTrue(mapper.selectRoles(new UserRoleMigrationQuery(1L,Set.of(),Set.of())).isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role",Integer.class));
    }
    @Test void failedEvidenceDeletedBindingsAndUnsafeIsolationDoNotLeavePermissionsGranted() {
        assertThrows(IllegalTransactionStateException.class,()->api.apply(request(null)));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThrows(IllegalStateException.class,()->tx.execute(status->api.apply(request(null))));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThrows(IllegalStateException.class,()->tx.execute(status->{api.apply(request(null));throw new IllegalStateException("evidence failed");}));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->api.apply(request(99L))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role",Integer.class));
    }
    @Configuration @EnableTransactionManagement @Import(SystemUserRoleMigrationApiImpl.class)
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:userRoleMigration"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");}
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource){return new DataSourceTransactionManager(dataSource);}
        @Bean CacheManager cacheManager(){return mock(CacheManager.class);}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource)throws Exception {
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(dataSource);
            var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/permission/UserRoleMigrationMapper.xml"));return factory.getObject();
        }
        @Bean MapperFactoryBean<UserRoleMigrationMapper> migrationMapper(SqlSessionFactory factory){var bean=new MapperFactoryBean<>(UserRoleMigrationMapper.class);bean.setSqlSessionFactory(factory);return bean;}
    }
}
