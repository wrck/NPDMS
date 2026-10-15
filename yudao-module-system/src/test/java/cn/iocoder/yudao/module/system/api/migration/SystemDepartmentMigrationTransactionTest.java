package cn.iocoder.yudao.module.system.api.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DepartmentMigrationMapper;
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
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SystemDepartmentMigrationTransactionTest {
    private AnnotationConfigApplicationContext context;
    private SystemDepartmentMigrationApi api;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private final LocalDateTime historical=LocalDateTime.of(2015,5,20,15,40,5);
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);context=new AnnotationConfigApplicationContext(Config.class);
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql")).execute(context.getBean(DataSource.class));
        api=context.getBean(SystemDepartmentMigrationApi.class);jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        jdbc.update("INSERT INTO system_dept(id,tenant_id,code,name,status) VALUES(1,1,'160000','市场部',0)");
    }
    @AfterEach void close(){context.close();TenantContextHolder.clear();}
    private Entry entry(String code,Long target,Department last){return new Entry("fnd_department","9",code,"企业网市场部","160000",0,historical,historical,target,last,false);}
    private Request request(Entry entry){return new Request(1L,"integration:1:12",List.of(entry));}
    private cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Result proposedParent(String owner,Long tenant,Long id,Integer status,String action) {
        var department=new cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO().setId(id).setCode("160000")
                .setName("拟建父").setParentId(0L).setSort(0).setStatus(status).setVersion(0);
        department.setTenantId(tenant);
        return new cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Result("parent",null,
                new cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Node(null,department,owner),action);
    }
    @Test void validatedSameBatchParentAllowsOnlyPreviewWithoutInventingIdsOrWriting() {
        jdbc.update("DELETE FROM system_dept");
        var input=request(entry("160300",null,null));
        var proposed=proposedParent(input.owner(),1L,null,0,"CREATED");
        var result=api.previewAfterOrganization(new OrganizationPreview(input,List.of(proposed))).getFirst();
        assertEquals("CREATED",result.action());assertNull(result.after().id());assertNull(result.after().parentId());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
        assertThrows(IllegalArgumentException.class,()->api.preview(input));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->api.apply(input)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
    }
    @Test void untrustedOrUnavailableContextCannotSupplyAParent() {
        jdbc.update("DELETE FROM system_dept");var input=request(entry("160300",null,null));
        assertThrows(IllegalArgumentException.class,()->api.previewAfterOrganization(new OrganizationPreview(input,List.of())));
        for(var parent:List.of(proposedParent("other",1L,null,0,"CREATED"),proposedParent(input.owner(),2L,null,0,"CREATED"),
                proposedParent(input.owner(),1L,11L,0,"CREATED"),proposedParent(input.owner(),1L,null,1,"CREATED"),
                proposedParent(input.owner(),1L,null,0,"CONFLICT")))
            assertThrows(IllegalArgumentException.class,()->api.previewAfterOrganization(new OrganizationPreview(input,List.of(parent))));
        var parent=proposedParent(input.owner(),1L,null,0,"CREATED");
        assertThrows(IllegalArgumentException.class,()->api.previewAfterOrganization(new OrganizationPreview(input,List.of(parent,parent))));
        var deleted=proposedParent(input.owner(),1L,null,0,"CREATED");deleted.after().department().setDeleted(true);
        assertThrows(IllegalArgumentException.class,()->api.previewAfterOrganization(new OrganizationPreview(input,List.of(deleted))));
        var before=new cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi.Result("parent",parent.after(),parent.after(),"CREATED");
        assertThrows(IllegalArgumentException.class,()->api.previewAfterOrganization(new OrganizationPreview(input,List.of(before))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
    }
    @Test void contextCannotOverrideAnExistingFormalParent() {
        var input=request(entry("160300",null,null));
        assertThrows(IllegalArgumentException.class,()->api.previewAfterOrganization(new OrganizationPreview(input,List.of(proposedParent(input.owner(),1L,null,0,"CREATED")))));
        assertEquals(1L,api.preview(input).getFirst().after().parentId());
        assertEquals("市场部",jdbc.queryForObject("SELECT name FROM system_dept WHERE id=1",String.class));
    }
    @Test void sameChildCodeCannotAliasTwoDistinctUnassignedParentCodes() {
        jdbc.update("DELETE FROM system_dept");
        var first=entry("160300",null,null);
        var second=new Entry("fnd_department","10","160300",first.name(),"162007",first.status(),
                first.createTime(),first.updateTime(),null,null,false);
        var input=new Request(1L,"integration:1:12",List.of(first,second));
        var parentA=proposedParent(input.owner(),1L,null,0,"CREATED");
        var parentB=proposedParent(input.owner(),1L,null,0,"CREATED");parentB.after().department().setCode("162007");
        assertThrows(IllegalArgumentException.class,()->api.previewAfterOrganization(new OrganizationPreview(input,List.of(parentA,parentB))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
    }
    @Test void sameChildCodeWithSameProposedParentRetainsAliasBehavior() {
        jdbc.update("DELETE FROM system_dept");var first=entry("160300",null,null);
        var second=new Entry("fnd_department","10",first.code(),first.name(),first.parentCode(),first.status(),
                first.createTime(),first.updateTime(),null,null,false);
        var input=new Request(1L,"integration:1:12",List.of(first,second));
        var results=api.previewAfterOrganization(new OrganizationPreview(input,List.of(proposedParent(input.owner(),1L,null,0,"CREATED"))));
        assertEquals(2,results.size());assertEquals(results.getFirst().after(),results.getLast().after());
        results.forEach(r->{assertNull(r.after().id());assertNull(r.after().parentId());});
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
    }
    @Test void approvedParentHistoryAndNullAttributesAreUsedWithoutSourceIdReuse(){
        var input=request(entry("160300",null,null));assertNull(api.preview(input).getFirst().after().id());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
        var after=tx.execute(status->api.apply(input)).getFirst().after();assertNotEquals(9L,after.id());assertEquals(1L,after.parentId());
        assertNull(after.creator());assertNull(after.updater());assertNull(after.leaderUserId());assertNull(after.phone());assertEquals(0,after.sort());
        assertEquals(historical,after.createTime());assertEquals(historical,after.updateTime());
        assertEquals("UNCHANGED",tx.execute(status->api.apply(request(entry("160300",after.id(),after)))).getFirst().action());
    }
    @Test void existingDefinitionsAndAuditRemainReferenceOnly(){
        jdbc.update("UPDATE system_dept SET creator='current-owner',phone='13800138000' WHERE id=1");
        var result=tx.execute(status->api.apply(request(entry("160000",null,null)))).getFirst();
        assertEquals("REFERENCED",result.action());assertEquals("市场部",result.after().name());
        assertEquals("current-owner",result.after().creator());assertEquals("13800138000",result.after().phone());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
    }
    @Test void ehrAuditAndDeferredLeaderCompleteAtomicallyWithoutOverwritingOtherFields(){
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,password,nickname,status) VALUES(77,1,'x01218','unusable','主管',1)");
        var incoming=new Entry("ehr_department","595","167010","原部门","160000",0,historical,historical,null,null,false,"7","7",null);
        var created=tx.execute(s->api.apply(request(incoming))).getFirst().after();assertEquals("7",created.creator());assertNull(created.leaderUserId());
        var updatedAt=historical.plusDays(1);
        var complete=new Entry("ehr_department","595","167010","原部门","160000",0,historical,updatedAt,created.id(),created,false,"7","7",77L);
        assertThrows(IllegalStateException.class,()->tx.execute(s->{api.apply(request(complete));throw new IllegalStateException("binding failure");}));
        assertNull(jdbc.queryForObject("SELECT leader_user_id FROM system_dept WHERE id=?",Long.class,created.id()));
        var after=tx.execute(s->api.apply(request(complete))).getFirst().after();assertEquals(77L,after.leaderUserId());assertEquals(1,after.version());
        assertEquals(created.createTime(),after.createTime());assertEquals(updatedAt,after.updateTime());assertEquals(created.parentId(),after.parentId());
        assertEquals("UNCHANGED",tx.execute(s->api.apply(request(new Entry("ehr_department","595","167010","原部门","160000",0,historical,updatedAt,
                after.id(),after,false,"7","7",77L)))).getFirst().action());
        assertThrows(IllegalArgumentException.class,()->api.preview(request(new Entry("ehr_department","595","167010","原部门","160000",0,historical,updatedAt,
                after.id(),after,false,"7","7",78L))));
        jdbc.update("UPDATE system_users SET tenant_id=2 WHERE id=77");assertThrows(IllegalArgumentException.class,()->api.preview(request(complete)));
    }
    @Test void unavailableParentManualDriftDeletionAndEvidenceFailureBlock(){
        jdbc.update("UPDATE system_dept SET tenant_id=2 WHERE id=1");assertThrows(IllegalArgumentException.class,()->api.preview(request(entry("160300",null,null))));
        jdbc.update("UPDATE system_dept SET tenant_id=1,status=1 WHERE id=1");assertThrows(IllegalArgumentException.class,()->api.preview(request(entry("160300",null,null))));
        jdbc.update("UPDATE system_dept SET status=0 WHERE id=1");
        assertThrows(IllegalStateException.class,()->tx.execute(status->{api.apply(request(entry("160300",null,null)));throw new IllegalStateException("binding failed");}));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
        var after=tx.execute(status->api.apply(request(entry("160300",null,null)))).getFirst().after();
        jdbc.update("UPDATE system_dept SET phone='13800138000' WHERE id=?",after.id());
        assertThrows(IllegalArgumentException.class,()->api.preview(request(entry("160300",after.id(),after))));
        jdbc.update("UPDATE system_dept SET deleted=TRUE WHERE id=?",after.id());assertThrows(IllegalArgumentException.class,()->api.preview(request(entry("160300",after.id(),after))));
        assertThrows(IllegalTransactionStateException.class,()->api.apply(request(entry("160300",null,null))));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);assertThrows(IllegalStateException.class,()->tx.execute(status->api.apply(request(entry("160300",null,null)))));
    }
    @Configuration @EnableTransactionManagement @Import(SystemDepartmentMigrationApiImpl.class)
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:departmentMigration"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");}
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource){return new DataSourceTransactionManager(dataSource);}
        @Bean CacheManager cacheManager(){return mock(CacheManager.class);}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource)throws Exception{
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(dataSource);var configuration=new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/dept/DepartmentMigrationMapper.xml"));return factory.getObject();
        }
        @Bean MapperFactoryBean<DepartmentMigrationMapper> migrationMapper(SqlSessionFactory factory){var bean=new MapperFactoryBean<>(DepartmentMigrationMapper.class);bean.setSqlSessionFactory(factory);return bean;}
    }
}
