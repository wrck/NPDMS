package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsFactoryHistoryMigrationApi.*;
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

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="P34_TEMP_MYSQL_TEST",matches="true")
class DirectFactoryHistoryTransactionTest {
    java.sql.Connection temporaryConnection;JdbcTemplate jdbc;TransactionTemplate tx;DirectFactoryHistoryPersistence writer;DirectFactoryHistoryMapper mapper;
    final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions=mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
    final cn.iocoder.yudao.module.system.api.user.AdminUserApi users=mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var loader=new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(System.getenv("P22_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
        var properties=new java.util.Properties();properties.setProperty("user",System.getenv("P22_DB_USER"));properties.setProperty("password",System.getenv("P22_DB_PASSWORD"));
        temporaryConnection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);
        var source=new SingleConnectionDataSource(temporaryConnection,true);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TEMPORARY TABLE ast_device_factory_version_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,device_sn VARCHAR(100),conp_version VARCHAR(100),boot_version VARCHAR(100),cpld_version VARCHAR(100),pcb_version VARCHAR(100),creator VARCHAR(64),create_time TIMESTAMP(3),updater VARCHAR(64),update_time TIMESTAMP(3),deleted BOOLEAN)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String path="mapper/sync/DirectFactoryHistoryMapper.xml";try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));mapper=session.getMapper(DirectFactoryHistoryMapper.class);
        writer=new DirectFactoryHistoryPersistence(source,mapper,permissions,users);when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear() throws Exception {TenantContextHolder.clear();if(temporaryConnection!=null)temporaryConnection.close();}
    Values values=new Values(null,"v1",null,"",null);
    Entry entry(int ordinal,Long target){return new Entry("a".repeat(64)+":"+ordinal,values,target);}
    Command command(Entry... entries){return new Command(1L,7L,List.of(entries));}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_factory_version_record",Integer.class);}
    @Test void completeDuplicatesNullsAndEmptyValuesAreRetainedWithoutCurrentProjection() {
        var input=command(entry(1,null),entry(2,null));assertEquals(2,tx.execute(status->writer.preview(input)).size());assertEquals(0,count());assertThrows(IllegalStateException.class,()->writer.apply(input));
        var saved=tx.execute(status->writer.apply(input));assertNotEquals(saved.get(0).targetId(),saved.get(1).targetId());assertEquals(2,count());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_factory_version_record WHERE project_id IS NULL AND device_sn IS NULL AND boot_version=''",Integer.class));
        assertTrue(tx.execute(status->writer.apply(command(entry(1,saved.get(0).targetId()),entry(2,saved.get(1).targetId())))).stream().allMatch(r->"UNCHANGED".equals(r.action())));assertEquals(2,count());
    }
    @Test void laterInsertFailureRollsBackEarlierDuplicate() {
        jdbc.execute("ALTER TABLE ast_device_factory_version_record ADD CONSTRAINT second_factory_rejected CHECK(conp_version<>'reject')");var second=new Entry("a".repeat(64)+":2",new Values(null,"reject",null,"",null),null);
        var failure=assertThrows(org.springframework.jdbc.UncategorizedSQLException.class,()->tx.execute(status->writer.apply(command(entry(1,null),second))));assertEquals(3819,failure.getSQLException().getErrorCode());assertTrue(failure.getSQLException().getMessage().contains("second_factory_rejected"));assertEquals(0,count());
    }
    @Test void changedDeletedAndProjectScopedTargetsCannotBeOverwritten() {
        var saved=tx.execute(status->writer.apply(command(entry(1,null)))).getFirst();var input=command(entry(1,saved.targetId()));
        jdbc.update("UPDATE ast_device_factory_version_record SET conp_version='manual'");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(input)));jdbc.update("UPDATE ast_device_factory_version_record SET conp_version='v1',deleted=TRUE");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(input)));jdbc.update("UPDATE ast_device_factory_version_record SET deleted=FALSE,project_id=100");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(input)));assertEquals(1,count());
    }
    @Test void identityPermissionAndTenantAreRequired() {
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(entry(1,null),entry(1,null)))));when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(false);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->tx.execute(status->writer.apply(command(entry(1,null)))));TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.apply(command(entry(1,null)))));assertEquals(0,count());
        assertEquals("DPPMS_FACTORY_HISTORY",DppmsFactoryHistorySyncTemplate.create(9L).adapter());assertEquals("DPPMS_FACTORY_HISTORY_DIRECT",DppmsDirectFactoryHistoryTemplate.create(9L).adapter());
    }
}
