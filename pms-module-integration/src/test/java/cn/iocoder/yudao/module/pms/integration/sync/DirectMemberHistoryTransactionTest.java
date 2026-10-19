package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMemberSourceHistoryApi.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
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
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="P31_TEMP_MYSQL_TEST",matches="true")
class DirectMemberHistoryTransactionTest {
    java.sql.Connection temporaryConnection;JdbcTemplate jdbc;TransactionTemplate tx;DirectMemberHistoryPersistence writer;
    DirectMemberHistoryMapper members;DirectContactHistoryMapper contacts;
    final DirectProjectAccess access=mock(DirectProjectAccess.class);final DirectProjectMapper projects=mock(DirectProjectMapper.class);
    final cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi context=mock(cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi.class);
    final cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi identities=mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class);
    final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions=mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var loader=new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(System.getenv("P22_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();var properties=new java.util.Properties();properties.setProperty("user",System.getenv("P22_DB_USER"));properties.setProperty("password",System.getenv("P22_DB_PASSWORD"));
        temporaryConnection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);var source=new SingleConnectionDataSource(temporaryConnection,true);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TEMPORARY TABLE proj_project_member_assignment(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,user_id BIGINT,employee_no VARCHAR(100),member_name VARCHAR(100),company_id BIGINT,company_code VARCHAR(100),company_name VARCHAR(100),department_id BIGINT,department_code VARCHAR(100),department_name VARCHAR(100),member_role VARCHAR(100),assignment_type VARCHAR(100),site_id BIGINT,responsibility VARCHAR(100),change_reason VARCHAR(100),remark VARCHAR(100),end_reason VARCHAR(100),effective_from TIMESTAMP,effective_to TIMESTAMP,status VARCHAR(30),version INT,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN,deleted_time TIMESTAMP,member_code VARCHAR(100),phone_number VARCHAR(100),email VARCHAR(100),registration_source VARCHAR(100),authorization_confirmed BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE cus_project_customer_contact_relation(id BIGINT PRIMARY KEY,project_id BIGINT,customer_id BIGINT,customer_contact_id BIGINT,name VARCHAR(100),department VARCHAR(100),title VARCHAR(100),mobile VARCHAR(100),phone VARCHAR(100),email VARCHAR(100),primary_flag BOOLEAN,primary_set_time TIMESTAMP,status INT,remark VARCHAR(100),version INT,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN,deleted_by BIGINT,deleted_at TIMESTAMP,tenant_id BIGINT,project_party_id BIGINT,contact_code VARCHAR(100),registration_source VARCHAR(100),effective_from TIMESTAMP,effective_to TIMESTAMP)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        for(String name:List.of("DirectMemberHistoryMapper","DirectContactHistoryMapper")){String path="mapper/sync/"+name+".xml";try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}}
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));members=session.getMapper(DirectMemberHistoryMapper.class);contacts=session.getMapper(DirectContactHistoryMapper.class);
        var project=new DirectProjectRow();project.setId(100L);project.setTenantId(1L);project.setVersion(3L);project.setLifecycleStatus("NORMAL_CLOSED");project.setProjectType("10");project.setCompanyId(4L);project.setCompanyCode("001");project.setCompanyName("公司");project.setManagerId(999L);when(projects.selectIdentityForUpdate(any())).thenReturn(project);
        when(context.lockHistoricalFinalCustomer(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi.FinalCustomer(50L,100L,"客户","101"));
        when(identities.getHistoricalMemberIdentities(anyLong(),any())).thenReturn(List.of());when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        writer=new DirectMemberHistoryPersistence(source,access,projects,members,identities,new DirectContactHistoryPersistence(source,context,contacts),permissions);tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear() throws Exception {TenantContextHolder.clear();if(temporaryConnection!=null)temporaryConnection.close();}
    Entry entry(String id,String role) {var raw=new LinkedHashMap<String,String>();ProjectMemberSourceHistoryApi.COLUMNS.forEach(k->raw.put(k,null));raw.putAll(Map.of("id",id,"projectId","101","projectType","10","memberRole",role,"memberCode","missing","memberName","原姓名","phoneNum","原座机","email","","fromFlag","1"));if("60".equals(role))raw.put("createTime","2020-01-01 00:00:00");return new Entry(id,raw,null,null);}
    Command command(Entry... entries){return new Command(1L,7L,100L,List.of(entries));}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    @Test void mixedHistoryPreviewWriteAndReplayNeverGrantAuthorizationOrSetPrimary() {
        var m=entry("1","30");var c=entry("2","60");var input=command(m,c);assertEquals(2,tx.execute(status->writer.preview(input)).size());assertEquals(0,count("proj_project_member_assignment"));assertEquals(0,count("cus_project_customer_contact_relation"));assertThrows(IllegalStateException.class,()->writer.apply(input));
        var saved=tx.execute(status->writer.apply(input));assertEquals(1,count("proj_project_member_assignment"));assertEquals(1,count("cus_project_customer_contact_relation"));assertFalse(jdbc.queryForObject("SELECT authorization_confirmed FROM proj_project_member_assignment",Boolean.class));assertNull(jdbc.queryForObject("SELECT user_id FROM proj_project_member_assignment",Long.class));assertNull(jdbc.queryForObject("SELECT creator FROM proj_project_member_assignment",String.class));assertNull(jdbc.queryForObject("SELECT create_time FROM proj_project_member_assignment",java.sql.Timestamp.class));assertEquals("原座机",jdbc.queryForObject("SELECT phone_number FROM proj_project_member_assignment",String.class));assertFalse(jdbc.queryForObject("SELECT primary_flag FROM cus_project_customer_contact_relation",Boolean.class));
        var replay=command(new Entry("1",m.originalFields(),saved.get(0).targetId(),saved.get(0).formalFieldsJson()),new Entry("2",c.originalFields(),saved.get(1).targetId(),saved.get(1).formalFieldsJson()));assertTrue(tx.execute(status->writer.apply(replay)).stream().allMatch(r->"UNCHANGED".equals(r.action())));
        jdbc.update("UPDATE cus_project_customer_contact_relation SET phone='manual'");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(replay)));
        verify(projects,never()).initializeFormalIfMatch(any());assertEquals(999L,projects.selectIdentityForUpdate(new DirectProjectMapper.IdentityQuery(1L,100L)).getManagerId());
    }
    @Test void memberSqlFailureRollsBackEarlierContactAndMember() {
        jdbc.execute("ALTER TABLE proj_project_member_assignment ADD CONSTRAINT second_member_rejected CHECK(member_code<>'reject')");var second=entry("3","20");second.originalFields().put("memberCode","reject");var failure=assertThrows(org.springframework.jdbc.UncategorizedSQLException.class,()->tx.execute(status->writer.apply(command(entry("1","30"),entry("2","60"),second))));assertEquals(3819,failure.getSQLException().getErrorCode());assertTrue(failure.getSQLException().getMessage().contains("second_member_rejected"));assertEquals(0,count("proj_project_member_assignment"));assertEquals(0,count("cus_project_customer_contact_relation"));
    }
    @Test void auditAccountDateScopeAndPermissionRemainRequired() {
        var missing=entry("1","30");missing.originalFields().put("createBy","unresolved");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(missing))));
        var invalid=entry("2","60");invalid.originalFields().put("createTime",null);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(invalid))));
        when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(false);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->tx.execute(status->writer.apply(command(entry("1","30")))));when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        doThrow(new IllegalArgumentException("forbidden")).when(access).getProjectForManage(any(),any());assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(entry("1","30")))));TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(entry("1","30")))));assertEquals(0,count("proj_project_member_assignment"));
    }
    @Test void preciseAccountIntervalsAndChangedMemberAreProtected() {
        when(identities.getHistoricalMemberIdentities(anyLong(),any())).thenReturn(List.of(new cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.MemberIdentity(44L,"missing","E44")));
        var first=entry("1","30");first.originalFields().put("effectiveFrom","2020-01-01 00:00:00");var second=entry("2","30");second.originalFields().put("effectiveFrom","2021-01-01 00:00:00");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(first,second))));assertEquals(0,count("proj_project_member_assignment"));
        var saved=tx.execute(status->writer.apply(command(first))).getFirst();assertEquals(44L,jdbc.queryForObject("SELECT user_id FROM proj_project_member_assignment",Long.class));assertFalse(jdbc.queryForObject("SELECT authorization_confirmed FROM proj_project_member_assignment",Boolean.class));jdbc.update("UPDATE proj_project_member_assignment SET deleted=TRUE");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(new Entry("1",first.originalFields(),saved.targetId(),saved.formalFieldsJson())))));
    }
}
