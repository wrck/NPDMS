package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.permission.ExplicitPermissionMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real, isolated H2 + production XML + Spring transaction proxy; never connects to MySQL. */
class ExplicitPermissionApiImplTest {

    private static final String PERMISSION = "pms:acc-project-closure:audit";
    private AnnotationConfigApplicationContext context;
    private ExplicitPermissionApi api;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        api = context.getBean(ExplicitPermissionApi.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql"))
                .execute(context.getBean(DataSource.class));
        TenantContextHolder.setTenantId(1L);
        jdbc.update("INSERT INTO system_users(id, username, nickname, status, tenant_id) VALUES (101, 'explicit-test', 'test', 0, 1)");
        jdbc.update("INSERT INTO system_role(id, name, code, sort, status, type, tenant_id) VALUES (201, 'test', 'ordinary-test-role', 0, 0, 2, 1)");
        jdbc.update("INSERT INTO system_user_role(id, user_id, role_id, tenant_id) VALUES (301, 101, 201, 1)");
        jdbc.update("INSERT INTO system_menu(id, name, permission, type, status, visible) VALUES (401, 'test', ?, 3, 0, FALSE)", PERMISSION);
        jdbc.update("INSERT INTO system_role_menu(id, role_id, menu_id, tenant_id) VALUES (501, 201, 401, 1)");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (jdbc != null) {
            jdbc.execute("SHUTDOWN");
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void ordinaryExplicitGrantAndHiddenButtonPassBothQueries() {
        assertBoth(true, PERMISSION);
        assertBoth(false, "pms:acc-project-closure:update");
        assertFalse(api.hasExplicitPermission(1L, 999L, PERMISSION));
        assertFalse(Boolean.TRUE.equals(transaction.execute(s -> api.lockAndCheck(1L, 999L, PERMISSION))));
    }

    @Test
    void superAdminStillNeedsActualGrant() {
        jdbc.update("UPDATE system_role SET code = 'super_admin' WHERE id = 201");
        assertBoth(true, PERMISSION);
        jdbc.update("DELETE FROM system_role_menu WHERE id = 501");
        assertBoth(false, PERMISSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE system_users SET deleted = TRUE WHERE id = 101",
            "UPDATE system_users SET status = 1 WHERE id = 101",
            "UPDATE system_role SET deleted = TRUE WHERE id = 201",
            "UPDATE system_role SET status = 1 WHERE id = 201",
            "UPDATE system_user_role SET deleted = TRUE WHERE id = 301",
            "UPDATE system_role_menu SET deleted = TRUE WHERE id = 501",
            "UPDATE system_menu SET deleted = TRUE WHERE id = 401",
            "UPDATE system_menu SET status = 1 WHERE id = 401",
            "DELETE FROM system_users WHERE id = 101",
            "DELETE FROM system_role WHERE id = 201",
            "DELETE FROM system_user_role WHERE id = 301",
            "DELETE FROM system_role_menu WHERE id = 501",
            "DELETE FROM system_menu WHERE id = 401"
    })
    void everyDisabledDeletedOrMissingLinkFailsClosed(String mutation) {
        jdbc.update(mutation);
        assertBoth(false, PERMISSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"system_users", "system_role", "system_user_role", "system_role_menu"})
    void rejectsCrossTenantLinksEvenWhenTenantInterceptorIsIgnored(String table) {
        // Table names are fixed test parameters, not API input.
        jdbc.update("UPDATE " + table + " SET tenant_id = 2");
        assertBoth(false, PERMISSION);
        TenantContextHolder.setIgnore(true);
        assertBoth(false, PERMISSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PMS:acc-project-closure:audit", "pms:acc-project-closure:AUDIT",
            "pms:acc-project-closure:audit ", " pms:acc-project-closure:audit", "pms:%:audit",
            "pms:acc-project-closure:aud_t", "*", "' OR 1=1 --"})
    void permissionIsCaseSensitiveExactAndNeverWildcard(String permission) {
        assertBoth(false, permission);
    }

    @Test
    void cannotCombineUserRoleWithAnotherRolesMenuGrant() {
        jdbc.update("INSERT INTO system_role(id, name, code, sort, status, type, tenant_id) VALUES (202, 'other', 'other-role', 0, 0, 2, 1)");
        jdbc.update("UPDATE system_role_menu SET role_id = 202 WHERE id = 501");
        assertBoth(false, PERMISSION);
        jdbc.update("INSERT INTO system_user_role(id, user_id, role_id, tenant_id) VALUES (302, 101, 202, 1)");
        assertBoth(true, PERMISSION);
        jdbc.update("UPDATE system_user_role SET deleted = TRUE WHERE id = 302");
        assertBoth(false, PERMISSION);
    }

    @Test
    void wildcardMenuDoesNotGrantConcretePermission() {
        jdbc.update("UPDATE system_menu SET permission = '*' WHERE id = 401");
        assertBoth(false, PERMISSION);
    }

    @Test
    void requiresMatchingTenantAndNonEmptyInputs() {
        assertBoth(false, null);
        assertBoth(false, "");
        assertBoth(false, " ");
        assertBoth(false, "a".repeat(101));
        assertFalse(api.hasExplicitPermission(null, 101L, PERMISSION));
        assertFalse(api.hasExplicitPermission(1L, null, PERMISSION));
        assertFalse(api.hasExplicitPermission(1L, 0L, PERMISSION));
        assertFalse(api.hasExplicitPermission(2L, 101L, PERMISSION));
        assertFalse(Boolean.TRUE.equals(transaction.execute(s -> api.lockAndCheck(2L, 101L, PERMISSION))));
        TenantContextHolder.clear();
        assertBoth(false, PERMISSION);
    }

    @Test
    void lockVariantRequiresExistingTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> api.lockAndCheck(1L, 101L, PERMISSION));
    }

    @Test
    void secondCheckDoesNotReuseMyBatisLocalCacheAfterRevocation() {
        transaction.executeWithoutResult(s -> {
            assertTrue(api.hasExplicitPermission(1L, 101L, PERMISSION));
            assertTrue(api.lockAndCheck(1L, 101L, PERMISSION));
            assertEquals(1, jdbc.update("UPDATE system_role_menu SET deleted = TRUE WHERE id = 501"));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM system_role_menu WHERE id = 501 AND deleted = FALSE", Integer.class));
            assertFalse(api.hasExplicitPermission(1L, 101L, PERMISSION));
            assertFalse(api.lockAndCheck(1L, 101L, PERMISSION));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE system_users SET status = 1 WHERE id = 101",
            "UPDATE system_role SET status = 1 WHERE id = 201",
            "UPDATE system_user_role SET deleted = TRUE WHERE id = 301",
            "UPDATE system_role_menu SET deleted = TRUE WHERE id = 501",
            "UPDATE system_menu SET status = 1 WHERE id = 401"
    })
    void holdsEveryGrantLinkUntilOuterTransactionEnds(String revoke) {
        try (var executor = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(s -> {
                assertTrue(api.lockAndCheck(1L, 101L, PERMISSION));
                var concurrentRevoke = executor.submit(() -> jdbc.update(revoke));
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> concurrentRevoke.get(5, TimeUnit.SECONDS));
                assertInstanceOf(DataAccessException.class, failure.getCause());
                Throwable cause = failure.getCause();
                while (!(cause instanceof SQLException) && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                SQLException sqlFailure = assertInstanceOf(SQLException.class, cause);
                assertEquals(50200, sqlFailure.getErrorCode(), "must fail specifically on H2 lock timeout");
                assertTrue(api.lockAndCheck(1L, 101L, PERMISSION));
            });
            // Same mutation succeeds once the protected transaction has released its locks.
            assertEquals(1, jdbc.update(revoke));
            assertBoth(false, PERMISSION);
        }
    }

    @Test
    void databaseFailurePropagatesRatherThanGranting() {
        jdbc.execute("DROP TABLE system_role_menu");
        assertThrows(DataAccessException.class, () -> api.hasExplicitPermission(1L, 101L, PERMISSION));
        assertThrows(DataAccessException.class,
                () -> transaction.execute(s -> api.lockAndCheck(1L, 101L, PERMISSION)));
    }

    private void assertBoth(boolean expected, String permission) {
        assertEquals(expected, api.hasExplicitPermission(1L, 101L, permission));
        assertEquals(expected, Boolean.TRUE.equals(transaction.execute(s -> api.lockAndCheck(1L, 101L, permission))));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(ExplicitPermissionApiImpl.class)
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:explicit_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=300", "sa", "");
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new ClassPathResource("mapper/permission/ExplicitPermissionMapper.xml"));
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                @Override
                public Expression getTenantId() {
                    return new LongValue(TenantContextHolder.getRequiredTenantId());
                }

                @Override
                public boolean ignoreTable(String tableName) {
                    return TenantContextHolder.isIgnore() || "system_menu".equalsIgnoreCase(tableName);
                }
            }));
            factory.setPlugins(interceptor);
            return factory.getObject();
        }

        @Bean
        MapperFactoryBean<ExplicitPermissionMapper> explicitPermissionMapper(SqlSessionFactory factory) {
            MapperFactoryBean<ExplicitPermissionMapper> bean = new MapperFactoryBean<>(ExplicitPermissionMapper.class);
            bean.setSqlSessionFactory(factory);
            return bean;
        }
    }
}
