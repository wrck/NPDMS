package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import cn.iocoder.yudao.module.system.api.permission.*;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectProjectRelationTransactionTest {
    JdbcTemplate jdbc;TransactionTemplate tx;DirectProjectRelationPersistence writer;
    DirectProjectRelationMapper mapper;DirectRelationContractMapper contracts;
    final ProjectMigrationIdentityContextApi identity=mock(ProjectMigrationIdentityContextApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final OrganizationScopeApi organization=mock(OrganizationScopeApi.class);
    final LocalDateTime date=LocalDateTime.parse("2020-01-02T03:04:05");
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source=new DriverManagerDataSource("jdbc:h2:mem:p18-direct-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE com_contract(id BIGINT PRIMARY KEY,tenant_id BIGINT,company_code VARCHAR(10),contract_no VARCHAR(64),deleted BOOLEAN)");
        jdbc.update("INSERT INTO com_contract VALUES(11,1,'001','C1',FALSE),(12,1,'001','C2',FALSE)");
        jdbc.execute("""
CREATE TABLE com_project_contract_relation(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,contract_id BIGINT,relation_role VARCHAR(20),source_system VARCHAR(20),source_table VARCHAR(64),source_record_key VARCHAR(128) CHECK(source_record_key<>'100:201'),effective_from TIMESTAMP,effective_to TIMESTAMP,status VARCHAR(30),version INTEGER,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN,UNIQUE(tenant_id,project_id,contract_id,relation_role))
""");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        for(String name:List.of("DirectProjectRelationMapper","DirectRelationContractMapper")) {
            String path="mapper/sync/"+name+".xml";
            try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        }
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        mapper=session.getMapper(DirectProjectRelationMapper.class);contracts=session.getMapper(DirectRelationContractMapper.class);
        var scope=new UserCompanyDepartmentScopeRespDTO();scope.setId(20L);scope.setVersion(0);scope.setCompanyCode("001");
        when(organization.getActiveScopes(7L)).thenReturn(List.of(scope));when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        var project=new ProjectMigrationIdentityContextApi.Context(10L,3L,"P1","10","001","公司");
        when(identity.resolve(any())).thenReturn(project);when(identity.lockAndRevalidate(any())).thenReturn(project);
        writer=new DirectProjectRelationPersistence(source,mapper,contracts,identity,organization,permissions,
            mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class),mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class),mock(ProjectMasterMigrationApi.class));
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Relation incoming(String key,long contract){return new Relation(null,1L,10L,contract,"RELATED","DPPMS","pm_project_contract",key,date,null,"ACTIVE",0,"sys",date,null,date,false);}
    Entry entry(String key,long contract){return new Entry(key,null,null,null,incoming(key,contract),"9001","P1",3L,"C"+(contract-10),18L);}
    Request request(Entry... entries){return new Request(1L,7L,"integration:1:12",List.of(entries),false);}
    Relation current(){return tx.execute(status->mapper.selectIdentity(new DirectProjectRelationMapper.IdentityQuery(1L,10L,11L)));}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM com_project_contract_relation",Integer.class);}
    @Test void previewIsReadOnlyAndReplayProtectsAllHistoricalColumns() {
        var input=request(entry("100:200",11));assertEquals("CREATED",tx.execute(status->writer.preview(input)).getFirst().action());assertEquals(0,count());
        assertThrows(IllegalStateException.class,()->writer.apply(input));
        var saved=tx.execute(status->writer.apply(input)).getFirst();assertEquals(saved.after(),current());assertEquals("sys",current().creator());assertNull(current().updater());
        var repeat=new Entry("100:200",saved.after().id(),saved.after(),null,incoming("100:200",11),"9001","P1",3L,"C1",18L);
        assertEquals("UNCHANGED",tx.execute(status->writer.apply(request(repeat))).getFirst().action());
        jdbc.update("UPDATE com_project_contract_relation SET updater='manual'");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(request(repeat))));
    }
    @Test void laterInsertConstraintRollsBackEarlierRelationship() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->writer.apply(request(entry("100:200",11),entry("100:201",12)))));assertEquals(0,count());
    }
    @Test void reviewedRestoreChangesOnlyDeletionAndVersionAndRejectsLaterDrift() {
        var saved=tx.execute(status->writer.apply(request(entry("100:200",11)))).getFirst().after();
        jdbc.update("UPDATE com_project_contract_relation SET deleted=TRUE,version=1");var withdrawn=current();
        var restore=new Entry("100:200",saved.id(),saved,null,incoming("100:200",11),"9001","P1",3L,"C1",18L,new Restoration(withdrawn));
        assertEquals("UPDATED",tx.execute(status->writer.preview(request(restore))).getFirst().action());assertTrue(current().deleted());
        var restored=tx.execute(status->writer.apply(request(restore))).getFirst().after();assertEquals(restored,current());assertEquals(2,restored.version());
        assertEquals(saved.createTime(),restored.createTime());assertEquals(saved.updateTime(),restored.updateTime());assertNull(restored.updater());
        jdbc.update("UPDATE com_project_contract_relation SET status='HISTORICAL'");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(request(restore))));
    }
    @Test void tenantPermissionsContractCompanyAndProjectVersionRemainProtected() {
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(false);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(request(entry("100:200",11)))));
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        jdbc.update("UPDATE com_contract SET company_code='002'");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(request(entry("100:200",11)))));
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(new Request(2L,7L,"integration:2:12",List.of(),false))));assertEquals(0,count());
        verify(identity).lockAndRevalidate(new ProjectMigrationIdentityContextApi.Query(1L,7L,10L,3L,"9001",18L));
    }
    @Test void reviewedTemplateUsesIndependentDirectAdapterAndRetainsCompositeRelationshipIdentity() {
        var adapter=new DppmsDirectProjectRelationTask(mapper,contracts,new DriverManagerDataSource(),identity,organization,permissions,
            mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class),mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class),
            mock(ProjectMasterMigrationApi.class),mock(SyncTaskService.class),mock(SyncRunMapper.class),mock(SyncBindingMapper.class),mock(cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi.class));
        var definition=DppmsDirectProjectRelationTemplate.create(9L);
        assertEquals("DPPMS_PROJECT_CONTRACT_RELATION_DIRECT",definition.adapter());
        assertEquals(DppmsProjectContractRelationSyncTemplate.create(9L).sources().getFirst().sourceKey(),definition.sources().getFirst().sourceKey());
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
    }
}
