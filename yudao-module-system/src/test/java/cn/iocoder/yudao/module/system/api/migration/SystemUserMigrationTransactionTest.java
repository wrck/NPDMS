package cn.iocoder.yudao.module.system.api.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.UserMigrationMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.UserPostMapper;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantInfoHandler;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.UserMobileLookupQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationPlanTest.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemUserMigrationTransactionTest {
    private AnnotationConfigApplicationContext context;
    private SystemUserMigrationApi api;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        context = new AnnotationConfigApplicationContext(Config.class);
        DataSource dataSource = context.getBean(DataSource.class);
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource); api = context.getBean(SystemUserMigrationApi.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void close() { context.close(); TenantContextHolder.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    private Request create() { return request(List.of(entry("t_user", "0", profile("historical", 1), null, null, false, null)), false); }
    @Test void historicalActorLookupIncludesDisabledIdentityButNeverCrossesTenantOrDeletedRows() {
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,status,deleted) VALUES(11,1,'historic','停用历史人',1,FALSE),(12,2,'other','其他租户',0,FALSE),(13,1,'deleted','已删除',0,TRUE)");
        assertEquals(List.of(new HistoricalIdentity(11L,"historic")),api.getHistoricalIdentities(1L,Set.of(11L,12L,13L)));
        assertEquals(List.of(),api.getHistoricalIdentities(1L,Set.of()));
        assertThrows(IllegalArgumentException.class,()->api.getHistoricalIdentities(2L,Set.of(12L)));
        assertThrows(IllegalArgumentException.class,()->api.getHistoricalIdentities(1L,Set.of(-1L)));
    }
    @Test void historicalNullActorsJsonAndUniqueCredentialsSurviveAndReplayIsUnchanged() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(1L).setUserType(2), new org.springframework.mock.web.MockHttpServletRequest());
        var first = tx.execute(status -> api.apply(create())).getFirst();
        var values = jdbc.queryForMap("SELECT tenant_id,employee_no,custom_info,status,creator,updater,password,create_time FROM system_users WHERE id=?", first.after().id());
        assertEquals(1L, ((Number)values.get("tenant_id")).longValue());
        assertEquals("02791", values.get("employee_no")); assertTrue(values.get("custom_info").toString().contains("\"optional\":null"));
        assertNull(values.get("creator")); assertNull(values.get("updater")); assertEquals(1, ((Number) values.get("status")).intValue());
        String encoded = values.get("password").toString(); assertTrue(encoded.startsWith("$2a$"));
        assertFalse(context.getBean(PasswordEncoder.class).matches("123456", encoded));
        var repeated = tx.execute(status -> api.apply(request(List.of(entry("t_user", "0", first.after().profile(),
                first.after().id(), first.after(), false, null)), false))).getFirst();
        assertEquals("UNCHANGED", repeated.action());
        assertEquals(encoded, jdbc.queryForObject("SELECT password FROM system_users WHERE id=?", String.class, first.after().id()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role", Integer.class));
    }
    @Test void accountAndEvidenceFailureRollBackAndUnsafeInvocationCannotWrite() {
        assertThrows(IllegalTransactionStateException.class, () -> api.apply(create()));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> api.apply(create())));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> { api.apply(create()); throw new IllegalStateException("binding/evidence failed"); }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM system_users", Integer.class));
    }
    @Test void adjudicatedAdoptionPreservesExistingPasswordLoginAndRoleRelations() {
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,password,nickname,status,login_ip) VALUES(1,1,'admin','retained-hash','当前管理员',0,'127.0.0.2')");
        jdbc.update("INSERT INTO system_user_role(id,user_id,role_id,tenant_id) VALUES(8,1,1,1)");
        var input = entry("t_user", "0", profile("admin", 0), null, null, false, 1L);
        var result = tx.execute(status -> api.apply(request(List.of(input), true))).getFirst();
        assertEquals("ADOPTED", result.action());
        assertEquals("retained-hash", jdbc.queryForObject("SELECT password FROM system_users WHERE id=1", String.class));
        assertEquals("127.0.0.2", jdbc.queryForObject("SELECT login_ip FROM system_users WHERE id=1", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role WHERE user_id=1 AND role_id=1", Integer.class));
    }
    @Test void cyclicAuditActorsAreResolvedBeforeAnyAccountIsInserted() {
        var audit = create().entries().getFirst().audit();
        Profile first = profile("first", 0), second = profile("second", 1);
        var entries = List.of(new Entry("t_user", "10", first, new SourceAudit("t_user:20", audit.createTime(), "t_user:10", audit.updateTime()), null, null, false, null),
                new Entry("t_user", "20", second, new SourceAudit("t_user:10", audit.createTime(), null, audit.updateTime()), null, null, false, null));
        var result = tx.execute(status -> api.apply(request(entries, false)));
        assertEquals(result.getLast().after().id().toString(), jdbc.queryForObject("SELECT creator FROM system_users WHERE id=?", String.class, result.getFirst().after().id()));
        assertEquals(result.getFirst().after().id().toString(), jdbc.queryForObject("SELECT creator FROM system_users WHERE id=?", String.class, result.getLast().after().id()));
        assertNotEquals(jdbc.queryForObject("SELECT password FROM system_users WHERE id=?", String.class, result.getFirst().after().id()),
                jdbc.queryForObject("SELECT password FROM system_users WHERE id=?", String.class, result.getLast().after().id()));
    }
    @Test void quotasForeignReferencesAndManualChangesBlockWithoutPartialWrites() {
        doAnswer(invocation -> { ((TenantInfoHandler) invocation.getArgument(0)).handle(new TenantDO().setAccountCount(0)); return null; })
                .when(context.getBean(TenantService.class)).handleTenantInfo(any());
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> api.apply(create())));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM system_users", Integer.class));
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class, () -> api.preview(create()));
        TenantContextHolder.setTenantId(1L);
        var base = profile("historical", 1);
        Profile invalid = new Profile(base.username(), base.nickname(), base.remark(), 99L, base.postIds(), base.email(), base.mobile(), base.sex(), base.avatar(), base.status(), base.employeeNo(), base.customInfo());
        jdbc.update("INSERT INTO system_dept(id,tenant_id,status) VALUES(99,2,0)");
        assertThrows(IllegalArgumentException.class, () -> api.preview(request(List.of(entry("t_user", "0", invalid, null, null, false, null)), false)));
    }
    @Test void actualSharedContactsRemainFormalAndMobileLookupDoesNotCrossTenants() {
        var base = profile("first", 0);
        Profile first = new Profile(base.username(), base.nickname(), null, null, Set.of(), "shared@example.invalid", "13800000000", 1, null, 0, null, base.customInfo());
        Profile second = new Profile("second", base.nickname(), null, null, Set.of(), first.email(), first.mobile(), 1, null, 1, null, base.customInfo());
        tx.execute(status -> api.apply(request(List.of(entry("t_user", "10", first, null, null, false, null),
                entry("t_user", "20", second, null, null, false, null)), false)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM system_users WHERE tenant_id=1 AND email=? AND mobile=?", Integer.class, first.email(), first.mobile()));
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,status,mobile) VALUES(99,2,'foreign','另一租户',0,'13800000000')");
        var mapper = context.getBean(AdminUserMapper.class);
        assertEquals(2, mapper.selectMobileLoginCandidates(new UserMobileLookupQuery(1L,"13800000000")).size());
        assertEquals(1, mapper.selectMobileLoginCandidates(new UserMobileLookupQuery(2L,"13800000000")).size());
        assertTrue(mapper.selectMobileLoginCandidates(new UserMobileLookupQuery(null,"13800000000")).isEmpty());
        assertTrue(mapper.selectMobileLoginCandidates(new UserMobileLookupQuery(1L,"")).isEmpty());
    }
    @Test void sourceDisablingRevokesSessionsWithoutResettingCredentialsOrOtherPermissions() {
        var initial = entry("t_user","0",profile("historical",0),null,null,false,null);
        var created = tx.execute(status -> api.apply(request(List.of(initial),false))).getFirst();
        String password = jdbc.queryForObject("SELECT password FROM system_users WHERE id=?",String.class,created.after().id());
        var disabled = entry("t_user","0",profile("historical",1),created.after().id(),created.after(),false,null);
        tx.execute(status -> api.apply(request(List.of(disabled),false)));
        verify(context.getBean(OAuth2TokenService.class)).removeAccessToken(created.after().id(),2);
        assertEquals(password,jdbc.queryForObject("SELECT password FROM system_users WHERE id=?",String.class,created.after().id()));
    }
    @Test void excludedAdminIsOnlyAnAuditReferenceAndItsCurrentAccountIsNeverImported() {
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,password,nickname,status,login_ip) VALUES(1,1,'admin','retained-hash','当前管理员',0,'127.0.0.2')");
        jdbc.update("INSERT INTO system_user_role(id,user_id,role_id,tenant_id) VALUES(8,1,1,1)");
        var source = create().entries().getFirst();
        var historical = new Entry(source.object(), "10", source.profile(),
                new SourceAudit("fnd_user_info:81", source.audit().createTime(), "t_user:0", source.audit().updateTime()),
                null, null, false, null);
        var input = new Request(1L, "integration:1:12", List.of(historical), false,
                List.of(new AuditReference("fnd_user_info", "81", 1L, "admin", null),
                        new AuditReference("t_user", "0", 1L, "admin", null)));
        var result = tx.execute(status -> api.apply(input));
        assertEquals(2, result.stream().filter(value -> "REFERENCED".equals(value.action())).count());
        assertEquals("1", jdbc.queryForObject("SELECT creator FROM system_users WHERE id=?", String.class, result.getFirst().after().id()));
        assertEquals("1", jdbc.queryForObject("SELECT updater FROM system_users WHERE id=?", String.class, result.getFirst().after().id()));
        assertEquals("当前管理员", jdbc.queryForObject("SELECT nickname FROM system_users WHERE id=1", String.class));
        assertEquals("retained-hash", jdbc.queryForObject("SELECT password FROM system_users WHERE id=1", String.class));
        assertEquals("127.0.0.2", jdbc.queryForObject("SELECT login_ip FROM system_users WHERE id=1", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role", Integer.class));
        verifyNoInteractions(context.getBean(OAuth2TokenService.class));
    }
    @Test void auditReferencesCannotCrossTenantsRebindOrOverlapImportedIdentities() {
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,status) VALUES(1,2,'admin','外租户',0)");
        var source = create().entries().getFirst();
        var reference = new AuditReference("fnd_user_info", "81", 1L, "admin", null);
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> api.apply(
                new Request(1L, "integration:1:12", List.of(source), false, List.of(reference)))));
        jdbc.update("UPDATE system_users SET tenant_id=1 WHERE id=1");
        assertThrows(IllegalArgumentException.class, () -> api.preview(new Request(1L, "integration:1:12", List.of(source), false,
                List.of(new AuditReference("fnd_user_info", "81", 1L, "admin", 99L)))));
        assertThrows(IllegalArgumentException.class, () -> api.preview(new Request(1L, "integration:1:12", List.of(source), false,
                List.of(new AuditReference(source.object(), source.sourceKey(), 1L, "admin", null)))));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM system_users", Integer.class));
    }
    @Test void reviewedNullUpdaterCorrectionRollsBackAndPreservesAllOtherHistoricalFacts() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(1L).setUserType(2), new org.springframework.mock.web.MockHttpServletRequest());
        var restored=tx.execute(status->api.apply(create())).getFirst().after();
        var original=jdbc.queryForMap("SELECT * FROM system_users WHERE id=?",restored.id());
        jdbc.update("UPDATE system_users SET updater='1' WHERE id=?",restored.id());
        jdbc.execute("ALTER TABLE system_users ALTER COLUMN update_time SET ON UPDATE CURRENT_TIMESTAMP");
        var audit=restored.audit();var before=new User(restored.id(),restored.profile(),new Audit(audit.creator(),audit.createTime(),"1",audit.updateTime()));
        var correction=new AuditCorrectionRequest(1L,"integration:1:99","1",List.of(new AuditCorrection(before,restored)));
        assertEquals("UPDATED",api.previewAuditCorrection(correction).getFirst().action());
        assertThrows(IllegalTransactionStateException.class,()->api.applyAuditCorrection(correction));
        assertThrows(IllegalStateException.class,()->tx.execute(status->{api.applyAuditCorrection(correction);throw new IllegalStateException("corrective receipt failed");}));
        assertEquals("1",jdbc.queryForObject("SELECT updater FROM system_users WHERE id=?",String.class,restored.id()));
        assertEquals("UPDATED",tx.execute(status->api.applyAuditCorrection(correction)).getFirst().action());
        assertEquals(original,jdbc.queryForMap("SELECT * FROM system_users WHERE id=?",restored.id()));
        assertEquals("UNCHANGED",tx.execute(status->api.applyAuditCorrection(correction)).getFirst().action());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role",Integer.class));verifyNoInteractions(context.getBean(OAuth2TokenService.class));
    }
    @Test void updaterCorrectionCannotOverwriteManualChangesOrAcceptOtherAuditChanges() {
        var restored=tx.execute(status->api.apply(create())).getFirst().after();var audit=restored.audit();
        var before=new User(restored.id(),restored.profile(),new Audit(audit.creator(),audit.createTime(),"1",audit.updateTime()));
        var request=new AuditCorrectionRequest(1L,"integration:1:99","1",List.of(new AuditCorrection(before,restored)));
        jdbc.update("UPDATE system_users SET updater='1',nickname='人工姓名' WHERE id=?",restored.id());
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->api.applyAuditCorrection(request)));
        assertEquals("人工姓名",jdbc.queryForObject("SELECT nickname FROM system_users WHERE id=?",String.class,restored.id()));
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->api.previewAuditCorrection(request));TenantContextHolder.setTenantId(1L);
        var invalid=new User(restored.id(),restored.profile(),new Audit("different",audit.createTime(),null,audit.updateTime()));
        assertThrows(IllegalArgumentException.class,()->api.previewAuditCorrection(new AuditCorrectionRequest(1L,"integration:1:99","1",List.of(new AuditCorrection(before,invalid)))));
        assertEquals("1",jdbc.queryForObject("SELECT updater FROM system_users WHERE id=?",String.class,restored.id()));
    }
    @Test void generatedUpdateTimeRequiresAnExactPriorCorrectionWindowAndRestoresOriginalTime() {
        var restored=tx.execute(status->api.apply(create())).getFirst().after();
        var original=jdbc.queryForMap("SELECT * FROM system_users WHERE id=?",restored.id());
        jdbc.execute("ALTER TABLE system_users ALTER COLUMN update_time SET ON UPDATE CURRENT_TIMESTAMP");
        jdbc.update("UPDATE system_users SET updater='1' WHERE id=?",restored.id());
        jdbc.update("UPDATE system_users SET updater=NULL WHERE id=?",restored.id());
        var generated=jdbc.queryForObject("SELECT update_time FROM system_users WHERE id=?",java.sql.Timestamp.class,restored.id()).toLocalDateTime();
        var audit=restored.audit();var before=new User(restored.id(),restored.profile(),new Audit(audit.creator(),audit.createTime(),null,generated));
        var entry=new AuditCorrection(before,restored);
        assertThrows(IllegalArgumentException.class,()->api.previewAuditCorrection(new AuditCorrectionRequest(1L,"integration:1:99","1",List.of(entry))));
        assertThrows(IllegalArgumentException.class,()->api.previewAuditCorrection(new AuditCorrectionRequest(1L,"integration:1:99","1",List.of(entry),generated.minusHours(1),generated.minusMinutes(1))));
        var request=new AuditCorrectionRequest(1L,"integration:1:99","1",List.of(entry),generated.minusSeconds(1),generated.plusSeconds(1));
        assertEquals("UPDATED",tx.execute(status->api.applyAuditCorrection(request)).getFirst().action());
        assertEquals(original,jdbc.queryForMap("SELECT * FROM system_users WHERE id=?",restored.id()));
        assertEquals("UNCHANGED",tx.execute(status->api.applyAuditCorrection(request)).getFirst().action());
    }
    @Configuration @EnableTransactionManagement @Import(SystemUserMigrationApiImpl.class)
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:userMigration" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean OAuth2TokenService tokens() { return mock(OAuth2TokenService.class); }
        @Bean TenantService tenantService() {
            TenantService service = mock(TenantService.class);
            doAnswer(invocation -> { ((TenantInfoHandler) invocation.getArgument(0)).handle(new TenantDO().setAccountCount(10000)); return null; })
                    .when(service).handleTenantInfo(any());
            return service;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(dataSource);
            var global = new GlobalConfig(); global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
            factory.setGlobalConfig(global);
            var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true); factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/user/UserMigrationMapper.xml"));
            return factory.getObject();
        }
        @Bean MapperFactoryBean<UserMigrationMapper> migrationMapper(SqlSessionFactory factory) {
            var bean = new MapperFactoryBean<>(UserMigrationMapper.class); bean.setSqlSessionFactory(factory); return bean;
        }
        @Bean MapperFactoryBean<UserPostMapper> postMapper(SqlSessionFactory factory) {
            var bean = new MapperFactoryBean<>(UserPostMapper.class); bean.setSqlSessionFactory(factory); return bean;
        }
        @Bean MapperFactoryBean<AdminUserMapper> userMapper(SqlSessionFactory factory) {
            var bean = new MapperFactoryBean<>(AdminUserMapper.class); bean.setSqlSessionFactory(factory); return bean;
        }
    }
}
