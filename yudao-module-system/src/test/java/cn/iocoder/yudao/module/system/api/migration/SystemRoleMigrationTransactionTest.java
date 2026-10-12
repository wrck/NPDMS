package cn.iocoder.yudao.module.system.api.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMigrationMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.junit.jupiter.api.*;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SystemRoleMigrationTransactionTest {
    private AnnotationConfigApplicationContext context;
    private SystemRoleMigrationApi api;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private static final LocalDateTime HISTORY = LocalDateTime.of(2015, 3, 24, 11, 22, 4);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(1L).setUserType(2),
                new org.springframework.mock.web.MockHttpServletRequest());
        context = new AnnotationConfigApplicationContext(Config.class);
        DataSource dataSource = context.getBean(DataSource.class);
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        api = context.getBean(SystemRoleMigrationApi.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void close() { context.close(); TenantContextHolder.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    private SystemRoleMigrationApi.Definition definition(String name, String creator) {
        return new SystemRoleMigrationApi.Definition("projectViewer", name, 106, "原角色备注", creator, HISTORY, null, HISTORY);
    }
    private SystemRoleMigrationApi.Request request(SystemRoleMigrationApi.Definition value, Long targetId,
                                                  SystemRoleMigrationApi.Definition previous) {
        return new SystemRoleMigrationApi.Request(1L, "integration:1:12", List.of(
                new SystemRoleMigrationApi.Entry("FND_ROLE", "6", value, targetId, previous, false)), false);
    }
    @Test void actualInsertPreservesNullActorsHistoricalTimesAndNarrowScope() {
        var regular = new cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO()
                .setName("正常新建角色").setCode("regularRole").setSort(0).setStatus(0).setType(2).setDataScope(5);
        regular.setTenantId(1L);
        try (var session = context.getBean(SqlSessionFactory.class).openSession(true)) {
            session.getMapper(cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper.class).insert(regular);
        }
        assertEquals("1", jdbc.queryForObject("SELECT creator FROM system_role WHERE id=?", String.class, regular.getId()));
        jdbc.update("DELETE FROM system_role WHERE id=?", regular.getId());
        var created = tx.execute(status -> api.apply(request(definition("项目查阅人员", null), null, null))).getFirst();
        var values = jdbc.queryForMap("SELECT creator,updater,create_time,update_time,type,data_scope FROM system_role WHERE id=?", created.after().id());
        assertNull(values.get("creator")); assertNull(values.get("updater"));
        assertEquals(HISTORY, ((java.sql.Timestamp) values.get("create_time")).toLocalDateTime());
        assertEquals(HISTORY, ((java.sql.Timestamp) values.get("update_time")).toLocalDateTime());
        assertEquals(2, ((Number) values.get("type")).intValue()); assertEquals(5, ((Number) values.get("data_scope")).intValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM system_role_menu", Integer.class));
        var repeated = tx.execute(status -> api.apply(request(created.after().definition(), created.after().id(), created.after().definition()))).getFirst();
        assertEquals("UNCHANGED", repeated.action()); assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM system_role", Integer.class));
    }
    @Test void failureInBindingOrEvidenceTransactionRollsBackRoleInsert() {
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            api.apply(request(definition("项目查阅人员", null), null, null));
            throw new IllegalStateException("binding/evidence failed");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM system_role", Integer.class));
    }
    @Test void apiCannotWriteOutsideCallerTransactionOrWithUnsafeIsolation() {
        assertThrows(IllegalTransactionStateException.class, () -> api.apply(request(definition("项目查阅人员", null), null, null)));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> api.apply(request(definition("项目查阅人员", null), null, null))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM system_role", Integer.class));
    }
    @Test void historicalActorCanBeDisabledButCannotComeFromAnotherTenant() {
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,status) VALUES(9,1,'historical','旧操作者',1),(19,2,'foreign','其他租户',0)");
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> api.apply(request(definition("项目查阅人员", "19"), null, null))));
        var created = tx.execute(status -> api.apply(request(definition("项目查阅人员", "9"), null, null))).getFirst();
        assertEquals("9", jdbc.queryForObject("SELECT creator FROM system_role WHERE id=?", String.class, created.after().id()));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM system_users WHERE id=9", Integer.class));
        assertNull(created.after().definition().updater());
    }
    @Test void metadataSyncPreservesCurrentDepartmentScopeAndOtherTenantRoles() {
        var created = tx.execute(status -> api.apply(request(definition("项目查阅人员", null), null, null))).getFirst();
        jdbc.update("UPDATE system_role SET data_scope=2,data_scope_dept_ids='[27,39]' WHERE id=?", created.after().id());
        jdbc.update("INSERT INTO system_role(id,tenant_id,name,code,sort,status,type,data_scope) VALUES(99,2,'其他租户角色','projectViewer',0,0,1,1)");
        var updated = tx.execute(status -> api.apply(request(definition("项目查阅新名称", null), created.after().id(), created.after().definition()))).getFirst();
        assertEquals("UPDATED", updated.action()); assertEquals(2, updated.after().dataScope());
        assertEquals(Set.of(27L, 39L), updated.after().dataScopeDeptIds());
        assertEquals("[27,39]", jdbc.queryForObject("SELECT data_scope_dept_ids FROM system_role WHERE id=?", String.class, created.after().id()));
        assertEquals("其他租户角色", jdbc.queryForObject("SELECT name FROM system_role WHERE id=99", String.class));
    }
    private SystemRoleMigrationApi.AuditCorrectionRequest correction(SystemRoleMigrationApi.Role restored) {
        var value = restored.definition();
        var generated = new SystemRoleMigrationApi.Role(restored.id(), new SystemRoleMigrationApi.Definition(
                value.code(), value.name(), value.sort(), value.remark(), "1", value.createTime(), "1", value.updateTime()),
                restored.status(), restored.type(), restored.dataScope(), restored.dataScopeDeptIds());
        return new SystemRoleMigrationApi.AuditCorrectionRequest(1L, "integration:1:13", "1",
                List.of(new SystemRoleMigrationApi.AuditCorrection(generated, restored)));
    }
    private SystemRoleMigrationApi.Role generatedRole() {
        var created = tx.execute(status -> api.apply(request(definition("项目查阅人员", null), null, null))).getFirst().after();
        jdbc.update("UPDATE system_role SET creator='1',updater='1',update_time=? WHERE id=?", HISTORY, created.id());
        return created;
    }
    @Test void explicitCorrectionPreservesTimesPermissionsAndReplaysWithoutWrites() {
        var restored = generatedRole();
        jdbc.update("INSERT INTO system_role_menu(id,role_id,menu_id,tenant_id) VALUES(87,?,33,1)", restored.id());
        var corrected = tx.execute(status -> api.applyAuditCorrection(correction(restored))).getFirst();
        assertEquals("UPDATED", corrected.action());
        var row = jdbc.queryForMap("SELECT creator,updater,update_time FROM system_role WHERE id=?", restored.id());
        assertNull(row.get("creator")); assertNull(row.get("updater"));
        assertEquals(HISTORY, ((java.sql.Timestamp) row.get("update_time")).toLocalDateTime());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM system_role_menu", Integer.class));
        assertEquals("UNCHANGED", tx.execute(status -> api.applyAuditCorrection(correction(restored))).getFirst().action());
    }
    @Test void correctionCannotOverwriteLaterDefinitionOrScopeChanges() {
        var restored = generatedRole();
        jdbc.update("UPDATE system_role SET remark='人工修改',data_scope=2,data_scope_dept_ids='[27]' WHERE id=?", restored.id());
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> api.applyAuditCorrection(correction(restored))));
        assertEquals("1", jdbc.queryForObject("SELECT updater FROM system_role WHERE id=?", String.class, restored.id()));
        assertEquals("人工修改", jdbc.queryForObject("SELECT remark FROM system_role WHERE id=?", String.class, restored.id()));
    }
    @Test void correctionReceiptFailureRollsBackAndRequiresOwnerTransaction() {
        var restored = generatedRole();
        assertThrows(IllegalTransactionStateException.class, () -> api.applyAuditCorrection(correction(restored)));
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            api.applyAuditCorrection(correction(restored)); throw new IllegalStateException("receipt failed");
        }));
        assertEquals("1", jdbc.queryForObject("SELECT creator FROM system_role WHERE id=?", String.class, restored.id()));
    }
    @Test void correctionCannotChangeBusinessDefinitionOrHistoricalDates() {
        var restored = generatedRole(); var request = correction(restored); var value = restored.definition();
        var changed = new SystemRoleMigrationApi.Role(restored.id(), new SystemRoleMigrationApi.Definition(value.code(),
                value.name(), value.sort(), value.remark(), null, value.createTime(), null, HISTORY.plusDays(1)),
                restored.status(), restored.type(), restored.dataScope(), restored.dataScopeDeptIds());
        var invalid = new SystemRoleMigrationApi.AuditCorrectionRequest(1L, request.owner(), "1",
                List.of(new SystemRoleMigrationApi.AuditCorrection(request.entries().getFirst().expectedCurrent(), changed)));
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> api.applyAuditCorrection(invalid)));
        assertThrows(IllegalArgumentException.class, () -> api.previewAuditCorrection(
                new SystemRoleMigrationApi.AuditCorrectionRequest(2L, "integration:2:13", "1", request.entries())));
    }
    @Configuration @EnableTransactionManagement
    @Import(SystemRoleMigrationApiImpl.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:roleMigration" + System.nanoTime()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager(); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
            global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
            factory.setGlobalConfig(global);
            var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/permission/RoleMigrationMapper.xml"));
            SqlSessionFactory result = factory.getObject();
            result.getConfiguration().addMapper(cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper.class);
            return result;
        }
        @Bean MapperFactoryBean<RoleMigrationMapper> roleMigrationMapper(SqlSessionFactory factory) {
            var bean = new MapperFactoryBean<>(RoleMigrationMapper.class); bean.setSqlSessionFactory(factory); return bean;
        }
    }
}
