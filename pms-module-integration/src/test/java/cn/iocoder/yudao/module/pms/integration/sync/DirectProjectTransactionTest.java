package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectProjectTransactionTest {
    JdbcTemplate jdbc;TransactionTemplate tx;DirectProjectPersistence writer;DirectProjectRootProjection projection;
    DirectProjectMapper mapper;DirectProjectRootMapper roots;
    final PlatformBusinessEventApi events=mock(PlatformBusinessEventApi.class);
    final PermissionCommonApi permission=mock(PermissionCommonApi.class);
    final ProjectScopeApi scope=mock(ProjectScopeApi.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var source=new DriverManagerDataSource("jdbc:h2:mem:p16-direct-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("""
CREATE TABLE proj_project(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_code VARCHAR(64),project_name VARCHAR(200),code_root_id BIGINT,project_sequence INTEGER,code_rule_version VARCHAR(20),parent_id BIGINT,root_id BIGINT,tree_path VARCHAR(200),tree_depth INTEGER,tree_sort INTEGER,department_id BIGINT,department_code VARCHAR(64),department_name VARCHAR(100),company_id BIGINT,company_code VARCHAR(64),company_name VARCHAR(100),project_type VARCHAR(20),project_category VARCHAR(30),signing_method VARCHAR(30),implementation_mode VARCHAR(30),customer_project_name VARCHAR(200),sales_type VARCHAR(50),major_project_level VARCHAR(50),market_name VARCHAR(100),system_name VARCHAR(100),expend_name VARCHAR(100),industry_name VARCHAR(100),not_track_reason VARCHAR(200),project_start_time TIMESTAMP,project_refresh_time TIMESTAMP,project_close_time TIMESTAMP,order_create_time TIMESTAMP,custom_info VARCHAR(1000),disabled BOOLEAN,effective_from TIMESTAMP,effective_to TIMESTAMP,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,source_type VARCHAR(20),status VARCHAR(30),lifecycle_status VARCHAR(30),current_stage VARCHAR(10),closed_at TIMESTAMP,assignment_status VARCHAR(30),version BIGINT,deleted BOOLEAN,UNIQUE(tenant_id,project_code))
""");
        jdbc.execute("""
CREATE TABLE proj_project_tree_version(id BIGINT AUTO_INCREMENT PRIMARY KEY,root_project_id BIGINT,tree_version BIGINT,status VARCHAR(20),change_batch_id VARCHAR(100),node_count INTEGER,path_count INTEGER,activated_at TIMESTAMP,failed_reason VARCHAR(500),version BIGINT,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN,tenant_id BIGINT,UNIQUE(tenant_id,root_project_id,tree_version))
""");
        jdbc.execute("""
CREATE TABLE proj_project_tree_path(id BIGINT PRIMARY KEY,tenant_id BIGINT,tree_version BIGINT,root_project_id BIGINT,ancestor_project_id BIGINT,descendant_project_id BIGINT,distance INTEGER,version INTEGER,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP,deleted BOOLEAN)
""");
        jdbc.execute("CREATE TABLE test_event_receipt(project_id BIGINT,payload VARCHAR(4000))");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        for(String name:List.of("DirectProjectMapper","DirectProjectRootMapper")) {
            String path="mapper/sync/"+name+".xml";
            try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        }
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        mapper=session.getMapper(DirectProjectMapper.class);roots=session.getMapper(DirectProjectRootMapper.class);
        projection=new DirectProjectRootProjection(roots,events);
        when(permission.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
        when(permission.hasAnyRoles(eq(7L),any(String[].class))).thenReturn(true);
        doAnswer(call->{var event=(cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent)call.getArgument(2);
            jdbc.update("INSERT INTO test_event_receipt VALUES(?,?)",Long.valueOf(call.getArgument(1)),event.eventPayload());return null;}).when(events).append(anyString(),anyString(),any());
        writer=new DirectProjectPersistence(source,mapper,mock(DirectProjectEvidenceMapper.class),new DirectProjectAccess(mapper,scope),
            mock(cn.iocoder.yudao.module.system.api.company.CompanyApi.class),mock(cn.iocoder.yudao.module.system.api.dept.DeptApi.class),
            mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class),mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class),
            mock(cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi.class),new DirectProjectCreationAuthorization(permission),
            mock(cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi.class),projection);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Entry entry(String key,String code) {
        var fields=new LinkedHashMap<String,String>();ProjectMasterMigrationApi.ORIGINAL_COLUMNS.forEach(c->fields.put(c,null));
        fields.put("projectId",key);fields.put("projectCode",code);fields.put("projectType","10");fields.put("projectState","100");
        fields.put("createTime","2020-01-02 03:04:05");fields.put("createBy","sys");fields.put("disabled","0");
        return new Entry(key,null,null,fields,null,null,"sys",null,null);
    }
    Request request(Entry... entries){return new Request(1L,7L,"integration:1:12",List.of(entries),false);}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    void empty(){for(String table:List.of("proj_project","proj_project_tree_version","proj_project_tree_path","test_event_receipt"))assertEquals(0,count(table),table);}
    @Test void previewDoesNotWriteAndCreationAtomicallyPublishesHistoricalRoot() {
        var request=request(entry("9001","P-HISTORY"));
        assertEquals("CREATED",tx.execute(status->writer.preview(request)).getFirst().action());empty();
        assertThrows(IllegalStateException.class,()->writer.apply(request));
        var saved=tx.execute(status->writer.apply(request)).getFirst();assertEquals("CREATED",saved.action());
        assertNotEquals(9001L,saved.after().id());assertEquals("NORMAL_CLOSED",saved.after().lifecycleStatus());assertEquals("S6",saved.after().currentStage());assertNull(saved.after().closedAt());
        assertEquals("sys",saved.after().creator());assertEquals("P-HISTORY",saved.after().projectName());
        assertEquals(saved.after().id(),jdbc.queryForObject("SELECT root_id FROM proj_project",Long.class));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM proj_project_tree_version",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT distance FROM proj_project_tree_path",Integer.class));
        assertEquals(1,count("test_event_receipt"));assertNull(jdbc.queryForObject("SELECT parent_id FROM proj_project",Long.class));
    }
    @Test void eventFailureRollsBackProjectVersionPathAndReceipt() {
        doThrow(new IllegalStateException("event rejected")).when(events).append(anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->tx.execute(status->writer.apply(request(entry("9001","P-HISTORY")))));empty();
    }
    @Test void lateSecondProjectionConstraintFailureRollsBackFirstProjectAndEvent() {
        // Reject the second path in actual XML SQL, after the first project and event have been written.
        jdbc.execute("ALTER TABLE proj_project_tree_path ADD CONSTRAINT one_root UNIQUE(tenant_id,tree_version)");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->writer.apply(request(entry("9001","P-ONE"),entry("9002","P-TWO")))));empty();
    }
    @Test void authorizationTenantAndExistingTreeCannotBeBypassed() {
        when(permission.hasAnyRoles(eq(7L),any(String[].class))).thenReturn(false);
        assertThrows(RuntimeException.class,()->tx.execute(status->writer.apply(request(entry("9001","P-HISTORY")))));empty();
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.preview(new Request(2L,7L,"integration:2:12",List.of(),false))));
        when(permission.hasAnyRoles(eq(7L),any(String[].class))).thenReturn(true);
        var id=tx.execute(status->writer.apply(request(entry("9001","P-HISTORY")))).getFirst().after().id();
        assertThrows(IllegalStateException.class,()->tx.execute(status->projection.publish(new DirectProjectRootProjection.Root(1L,id,7L))));
        assertEquals(1,count("proj_project_tree_version"));assertEquals(1,count("proj_project_tree_path"));
    }
    @Test void reviewedDefinitionUsesIndependentAdapterAndNativeProjectId() {
        var adapter=new DppmsDirectProjectTask(mapper,mock(DirectProjectEvidenceMapper.class),roots,new DriverManagerDataSource(),
            mock(cn.iocoder.yudao.module.system.api.company.CompanyApi.class),mock(cn.iocoder.yudao.module.system.api.dept.DeptApi.class),
            mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class),mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class),
            mock(cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi.class),mock(cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi.class),
            scope,permission,events,mock(SyncTaskService.class),mock(SyncRunMapper.class),mock(SyncBindingMapper.class));
        var definition=DppmsDirectProjectTemplate.create(9L);
        assertEquals("DPPMS_PROJECT_MASTER_DIRECT",definition.adapter());assertEquals("projectId",definition.sources().getFirst().sourceKey());
        assertEquals("DPPMS_PROJECT_MASTER",DppmsProjectMasterSyncTemplate.create(9L).adapter());
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
    }
}
