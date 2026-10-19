package cn.iocoder.yudao.module.system.api.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserScopeMigrationMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.UserScopeMigrationReferenceQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserScopeMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;

class SystemUserScopeMigrationTransactionTest {
    private AnnotationConfigApplicationContext context;
    private SystemUserScopeMigrationApi api;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private final LocalDateTime historical=LocalDateTime.of(2018,7,10,10,21,2,123000000);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(1L).setUserType(2),
                new org.springframework.mock.web.MockHttpServletRequest());
        context=new AnnotationConfigApplicationContext(Config.class);
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql")).execute(context.getBean(DataSource.class));
        jdbc=new JdbcTemplate(context.getBean(DataSource.class));api=context.getBean(SystemUserScopeMigrationApi.class);
        tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,status) VALUES(1,1,'c02791','历史人员',1)");
        jdbc.update("INSERT INTO system_company(id,tenant_id,code,name,status) VALUES(2,1,'001','公司',0)");
        jdbc.update("INSERT INTO system_dept(id,tenant_id,code,name,status) VALUES(3,1,'162023','办事处',1)");
        jdbc.update("INSERT INTO system_role(id,tenant_id,code,name,sort,status,type) VALUES(4,1,'user','普通用户',0,0,2)");
        jdbc.update("INSERT INTO system_user_role(id,tenant_id,user_id,role_id) VALUES(5,1,1,4)");
    }
    @AfterEach void close(){context.close();TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    private Scope scope(){return new Scope(1L,2L,"001","公司",3L,"162023","办事处","user",true,historical,null,0);}
    private Audit audit(){return new Audit("1",historical,null,historical);}
    private Request request(Long target,Relation last){return new Request(1L,"integration:1:12",List.of(new Entry("fnd_user_power","470:001:162023:user",scope(),audit(),target,last)));}
    @Test void plannedAccountAndMembershipPreviewKeepsDirectoryGuardsAndMakesNoGrant() {
        var planned=new Scope(-1L,2L,"001","公司",3L,"162023","办事处","user",true,historical,null,0);
        var input=new Request(1L,"integration:1:12",List.of(new Entry("fnd_user_info","10:OWN:001:162023:user",planned,
                new Audit("-1",historical,null,historical),null,null)));
        var preceding=new SystemAccountMigrationPreview(1L,input.owner(),Set.of(-1L),Map.of(),
                Set.of(new SystemAccountMigrationPreview.Membership(-1L,"user")));
        assertNull(api.previewAfterFoundation(new FoundationPreview(input,preceding)).getFirst().after().id());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_company_department_scope",Integer.class));
        assertThrows(IllegalArgumentException.class,()->api.preview(input));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->api.apply(input)));
        assertThrows(IllegalArgumentException.class,()->api.previewAfterFoundation(new FoundationPreview(input,
                new SystemAccountMigrationPreview(1L,input.owner(),Set.of(-1L),Map.of(),Set.of()))));
        jdbc.update("UPDATE system_company SET tenant_id=2 WHERE id=2");
        assertThrows(IllegalArgumentException.class,()->api.previewAfterFoundation(new FoundationPreview(input,preceding)));
    }
    @Test void historicalDatesAndNullAuditAreKeptWithOneRelationForExactAliases() {
        var regular=new cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO();
        regular.setTenantId(1L);regular.setUserId(1L);regular.setCompanyId(2L);regular.setCompanyCode("001");regular.setCompanyName("公司");
        regular.setDepartmentId(3L);regular.setDepartmentCode("162023");regular.setDepartmentName("办事处");
        regular.setScopeRole("user");regular.setIsPrimary(true);regular.setEffectiveFrom(historical);regular.setStatus(0);regular.setVersion(0);
        try(var session=context.getBean(SqlSessionFactory.class).openSession(true)) {
            session.getMapper(cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper.class).insert(regular);
        }
        assertEquals("1",jdbc.queryForObject("SELECT creator FROM system_user_company_department_scope WHERE id=?",String.class,regular.getId()));
        jdbc.update("DELETE FROM system_user_company_department_scope WHERE id=?",regular.getId());
        var entry=request(null,null).entries().getFirst();
        var input=new Request(1L,"integration:1:12",List.of(entry,new Entry("fnd_user_info","473:001:162023:user",scope(),audit(),null,null)));
        assertNull(api.preview(input).getFirst().after().id());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_company_department_scope",Integer.class));
        var results=tx.execute(status->api.apply(input));assertEquals(results.getFirst().after(),results.getLast().after());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_company_department_scope",Integer.class));
        var row=jdbc.queryForMap("SELECT create_time,update_time,effective_from,updater,department_id,department_code FROM system_user_company_department_scope");
        assertEquals(historical,((java.sql.Timestamp)row.get("effective_from")).toLocalDateTime());
        assertEquals(row.get("create_time"),row.get("update_time"));assertNull(row.get("updater"));assertEquals("162023",row.get("department_code"));
        assertEquals("UNCHANGED",tx.execute(status->api.apply(request(results.getFirst().after().id(),results.getFirst().after()))).getFirst().action());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_role",Integer.class));
    }
    @Test void CrossTenantReferencesCodesUnassignedRolesAndUnmatchedDepartmentsBlock() {
        jdbc.update("UPDATE system_company SET tenant_id=2 WHERE id=2");assertThrows(IllegalArgumentException.class,()->api.preview(request(null,null)));
        jdbc.update("UPDATE system_company SET tenant_id=1,code='002' WHERE id=2");assertThrows(IllegalArgumentException.class,()->api.preview(request(null,null)));
        jdbc.update("UPDATE system_company SET code='001' WHERE id=2");jdbc.update("DELETE FROM system_user_role");
        assertThrows(IllegalArgumentException.class,()->api.preview(request(null,null)));
        jdbc.update("INSERT INTO system_user_role(id,tenant_id,user_id,role_id) VALUES(5,1,1,4)");jdbc.update("UPDATE system_dept SET deleted=TRUE WHERE id=3");
        assertThrows(IllegalArgumentException.class,()->api.preview(request(null,null)));
        var mapper=context.getBean(UserScopeMigrationMapper.class);var query=new UserScopeMigrationReferenceQuery(1L,Set.of(),Set.of(),Set.of(),Set.of());
        assertTrue(mapper.selectUserIds(query).isEmpty());assertTrue(mapper.selectCompanies(query).isEmpty());
        assertTrue(mapper.selectDepartments(query).isEmpty());assertTrue(mapper.selectRoles(query).isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_company_department_scope",Integer.class));
    }
    @Test void AtomicRollbackBoundDeletionAndManualChangesDoNotRewriteScopeHistory() {
        assertThrows(IllegalStateException.class,()->tx.execute(status->{api.apply(request(null,null));throw new IllegalStateException("binding failed");}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_company_department_scope",Integer.class));
        var original=tx.execute(status->api.apply(request(null,null))).getFirst().after();
        jdbc.update("UPDATE system_user_company_department_scope SET status=1,version=version+1 WHERE id=?",original.id());
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->api.apply(request(original.id(),original))));
        assertEquals(1,jdbc.queryForObject("SELECT status FROM system_user_company_department_scope",Integer.class));
        jdbc.update("UPDATE system_user_company_department_scope SET deleted=TRUE WHERE id=?",original.id());
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->api.apply(request(original.id(),original))));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_company_department_scope",Integer.class));
        assertThrows(IllegalTransactionStateException.class,()->api.apply(request(null,null)));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThrows(IllegalStateException.class,()->tx.execute(status->api.apply(request(null,null))));
    }
    @Test void AdjudicatedBoundUpdatesRetainIdentityAndVersionAndClearTheEndDateExplicitly() {
        var original=tx.execute(status->api.apply(request(null,null))).getFirst().after();
        Scope changed=new Scope(1L,2L,"001","公司",3L,"162023","办事处","user",false,historical,historical.plusDays(1),1);
        Request update=new Request(1L,"integration:1:12",List.of(new Entry("fnd_user_power","470:001:162023:user",changed,audit(),original.id(),original)));
        var next=tx.execute(status->api.apply(update)).getFirst().after();assertEquals(1,next.version());assertEquals(original.id(),next.id());
        var cleared=tx.execute(status->api.apply(request(next.id(),next))).getFirst().after();assertNull(cleared.scope().effectiveTo());assertEquals(2,cleared.version());
        assertNull(jdbc.queryForObject("SELECT effective_to FROM system_user_company_department_scope",java.sql.Timestamp.class));
    }
    @Test void SameOfficeAndPeriodInOwnAndAdditionalSourceGrantsKeepDistinctHistoricalFacts() {
        Scope additional=new Scope(1L,2L,"001","公司",3L,"162023","办事处","user",false,historical,null,0);
        Audit additionalAudit=new Audit("1",historical,"1",historical.plusYears(1));
        Request input=new Request(1L,"integration:1:12",List.of(request(null,null).entries().getFirst(),
                new Entry("fnd_user_power","470:001:162023:user:additional",additional,additionalAudit,null,null)));
        var results=tx.execute(status->api.apply(input));assertNotEquals(results.getFirst().after().id(),results.getLast().after().id());
        assertTrue(results.getFirst().after().scope().primary());assertFalse(results.getLast().after().scope().primary());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_company_department_scope",Integer.class));
        Request replay=new Request(1L,"integration:1:12",results.stream().map(result->new Entry(result.object(),result.sourceKey(),result.after().scope(),
                result.after().audit(),result.after().id(),result.after())).toList());
        assertTrue(tx.execute(status->api.apply(replay)).stream().allMatch(result->"UNCHANGED".equals(result.action())));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM system_user_company_department_scope",Integer.class));
    }
    @Configuration @EnableTransactionManagement @Import(SystemUserScopeMigrationApiImpl.class)
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:userScopeMigration"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");}
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource){return new DataSourceTransactionManager(dataSource);}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource)throws Exception {
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(dataSource);
            var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();
            global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
            factory.setGlobalConfig(global);
            var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/permission/UserScopeMigrationMapper.xml"));
            SqlSessionFactory result=factory.getObject();
            result.getConfiguration().addMapper(cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper.class);
            return result;
        }
        @Bean MapperFactoryBean<UserScopeMigrationMapper> migrationMapper(SqlSessionFactory factory){var bean=new MapperFactoryBean<>(UserScopeMigrationMapper.class);bean.setSqlSessionFactory(factory);return bean;}
    }
}
