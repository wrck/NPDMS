package cn.iocoder.yudao.module.system.api.organization;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.organization.*;
import cn.iocoder.yudao.module.system.service.organization.*;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ManagedOrganizationTransactionTest {
    AnnotationConfigApplicationContext context; ManagedOrganizationApi api; JdbcTemplate jdbc; TransactionTemplate tx;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        context=new AnnotationConfigApplicationContext(Config.class);
        jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        new ResourceDatabasePopulator(new ClassPathResource("sql/create_tables.sql")).execute(context.getBean(DataSource.class));
        jdbc.execute("CREATE TABLE system_organization_ownership (id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,object_type VARCHAR(16),target_id BIGINT,managed_by VARCHAR(128),creator VARCHAR(64) DEFAULT '',create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updater VARCHAR(64) DEFAULT '',update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,UNIQUE(tenant_id,object_type,target_id))");
        api=context.getBean(ManagedOrganizationApi.class);tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }
    @AfterEach void close(){context.close();TenantContextHolder.clear();}
    ManagedOrganizationApi.Command command(Long companyId,Long departmentId,int status) {
        var company=new CompanyDO().setId(companyId).setCode("TEST-COMPANY").setName("测试公司").setStatus(status).setVersion(0);
        var department=new DeptDO().setId(departmentId).setCode("TEST-DEPT").setName("测试部门").setStatus(status).setSort(10).setVersion(0);
        return new ManagedOrganizationApi.Command("test-owner",List.of(
                new ManagedOrganizationApi.Entry("company",null,new ManagedOrganizationApi.Node(company,null,null)),
                new ManagedOrganizationApi.Entry("department",null,new ManagedOrganizationApi.Node(null,department,null))),false,Set.of(),"UPSERT",Set.of("company","department"));
    }
    @Test void createsThenNoOpThenDisablesWithoutDeletingHistory() {
        var first=tx.execute(s->api.apply(command(null,null,0)));
        assertEquals(2,first.size());
        long companyId=first.get(0).after().id(),deptId=first.get(1).after().id();
        var unchanged=tx.execute(s->api.apply(command(companyId,deptId,0)));
        assertTrue(unchanged.stream().allMatch(r->r.action().equals("UNCHANGED")));
        var disabled=tx.execute(s->api.apply(command(companyId,deptId,1)));
        assertTrue(disabled.stream().allMatch(r->r.action().equals("DISABLED")));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM system_organization_ownership",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT status FROM system_dept WHERE id=?",Integer.class,deptId));
    }
    @Test void failureRollsBackBusinessRowsAndOwnershipTogether() {
        assertThrows(IllegalStateException.class,()->tx.execute(s->{
            api.apply(command(null,null,0));throw new IllegalStateException("injected failure");
        }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_organization_ownership",Integer.class));
    }
    @Test void manualChangesAreAllowedAndNextSyncRestoresSourceWhilePreservingPhone() {
        var created=tx.execute(s->api.apply(command(null,null,0)));
        long id=created.get(1).after().id();
        var service=context.getBean(cn.iocoder.yudao.module.system.service.dept.DeptService.class);
        var request=new DeptSaveReqVO().setId(id).setCode("TEST-DEPT").setName("测试部门")
                .setParentId(0L).setSort(10).setStatus(0).setPhone("13800000000").setVersion(0);
        service.updateDept(request);
        assertEquals("13800000000",jdbc.queryForObject("SELECT phone FROM system_dept WHERE id=?",String.class,id));
        request.setName("人工改名").setCode("MANUAL-DEPT").setStatus(1).setSort(20);
        service.updateDept(request);
        assertEquals("人工改名",jdbc.queryForObject("SELECT name FROM system_dept WHERE id=?",String.class,id));
        var companyService=context.getBean(cn.iocoder.yudao.module.system.service.company.CompanyService.class);
        var companyRequest=new cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanySaveReqVO()
                .setId(created.getFirst().after().id()).setExpectedVersion(0)
                .setCode("MANUAL-COMPANY").setName("人工公司").setStatus(1);
        companyService.updateCompany(companyRequest);
        assertEquals("人工公司",companyService.getCompany(companyRequest.getId()).getName());
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->companyService.updateCompany(companyRequest));
        var restored=tx.execute(s->api.apply(command(companyRequest.getId(),id,0)));
        assertTrue(restored.stream().allMatch(r->"UPDATED".equals(r.action())));
        assertEquals("测试公司",companyService.getCompany(companyRequest.getId()).getName());
        var department=service.getDept(id);
        assertEquals("测试部门",department.getName());assertEquals("TEST-DEPT",department.getCode());
        assertEquals(0,department.getStatus());assertEquals(10,department.getSort());
        assertEquals("13800000000",department.getPhone());
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.deleteDept(id));
    }
    @Test void foreignTenantTargetCannotBeSelectedOrUpdated() {
        var created=tx.execute(s->api.apply(command(null,null,0)));
        TenantContextHolder.setTenantId(2L);
        assertTrue(api.list().isEmpty());
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->api.apply(command(created.get(0).after().id(),created.get(1).after().id(),1))));
    }
    @Test void assignedIdsAndParentRelationshipUseOriginalEntityInsert() {
        var base=command(101L,201L,0);
        var entries=new ArrayList<>(base.entries());
        entries.add(new ManagedOrganizationApi.Entry("child","department",new ManagedOrganizationApi.Node(null,
                new DeptDO().setId(202L).setCode("CHILD").setName("子部门").setSort(20).setStatus(0),null)));
        var exact=new ManagedOrganizationApi.Command(base.owner(),entries,false,Set.of("company","department","child"),"UPSERT",Set.of("company","department","child"));
        assertEquals(201L,api.preview(exact).get(2).after().parentId());
        tx.execute(s->api.apply(exact));
        assertEquals(101L,jdbc.queryForObject("SELECT id FROM system_company",Long.class));
        assertEquals(201L,jdbc.queryForObject("SELECT parent_id FROM system_dept WHERE id=202",Long.class));
    }
    @Test void assignedIdCollisionInAnotherTenantRollsBackEarlierInserts() {
        jdbc.update("INSERT INTO system_dept(id,tenant_id,code,name,parent_id,sort,status,version) VALUES(201,2,'FOREIGN','其他租户',0,0,0,0)");
        var base=command(101L,201L,0);
        var exact=new ManagedOrganizationApi.Command(base.owner(),base.entries(),false,Set.of("company","department"),"UPSERT",Set.of("company","department"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(s->api.apply(exact)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_organization_ownership",Integer.class));
        assertEquals("其他租户",jdbc.queryForObject("SELECT name FROM system_dept WHERE id=201",String.class));
    }
    @Test void replacementClearsAllTenantOrganizationsButPreservesOtherTenant() {
        tx.execute(s->api.apply(command(null,null,0)));
        jdbc.update("INSERT INTO system_company(id,tenant_id,code,name,status,version) VALUES(901,1,'LOCAL','未受管公司',0,0),(902,2,'FOREIGN','其他租户公司',0,0)");
        var before=api.previewReplacement(command(null,null,0));
        assertEquals(3,before.stream().filter(r->"CLEARED".equals(r.action())).count());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        var after=tx.execute(s->api.replaceAll(command(null,null,0)));
        assertEquals(3,after.stream().filter(r->"CLEARED".equals(r.action())).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_company WHERE tenant_id=1",Integer.class));
        assertEquals("其他租户公司",jdbc.queryForObject("SELECT name FROM system_company WHERE id=902",String.class));
    }
    @Test void referencedDepartmentBlocksReplacementBeforeDeletion() {
        var created=tx.execute(s->api.apply(command(null,null,0)));
        jdbc.update("INSERT INTO system_users(id,tenant_id,username,nickname,dept_id,status) VALUES(1,1,'test','测试用户',?,0)",created.get(1).after().id());
        assertThrows(IllegalArgumentException.class,()->api.previewReplacement(command(null,null,0)));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->api.replaceAll(command(null,null,0))));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_dept",Integer.class));
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    @Import({ManagedOrganizationApiImpl.class,ManagedOrganizationGuard.class,ManagedDeptService.class,ManagedCompanyService.class,
            cn.iocoder.yudao.module.system.service.dept.DeptServiceImpl.class,
            cn.iocoder.yudao.module.system.service.company.CompanyServiceImpl.class})
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:org_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");}
        @Bean PlatformTransactionManager transactionManager(DataSource d){return new DataSourceTransactionManager(d);}
        @Bean CacheManager cacheManager(){return new ConcurrentMapCacheManager();}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource d)throws Exception {
            var f=new MybatisSqlSessionFactoryBean();f.setDataSource(d);
            var global=new com.baomidou.mybatisplus.core.config.GlobalConfig();
            global.setMetaObjectHandler(new cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler());
            f.setGlobalConfig(global);
            var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);f.setConfiguration(configuration);
            f.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/organization/*.xml"));return f.getObject();
        }
        static <T> MapperFactoryBean<T> mapper(Class<T> type,SqlSessionFactory f){var b=new MapperFactoryBean<T>(type);b.setSqlSessionFactory(f);return b;}
        @Bean MapperFactoryBean<CompanyMapper> companyMapper(SqlSessionFactory f){return mapper(CompanyMapper.class,f);}
        @Bean MapperFactoryBean<DeptMapper> deptMapper(SqlSessionFactory f){return mapper(DeptMapper.class,f);}
        @Bean MapperFactoryBean<ManagedOrganizationMapper> managedOrganizationMapper(SqlSessionFactory f){return mapper(ManagedOrganizationMapper.class,f);}
        @Bean MapperFactoryBean<OrganizationOwnershipMapper> organizationOwnershipMapper(SqlSessionFactory f){return mapper(OrganizationOwnershipMapper.class,f);}
    }
}
