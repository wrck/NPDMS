package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi.*;
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

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="P30_TEMP_MYSQL_TEST",matches="true")
class DirectDeviceBaseTransactionTest {
    java.sql.Connection temporaryConnection;JdbcTemplate jdbc;TransactionTemplate tx;DirectDeviceBasePersistence writer;DirectDeviceBaseMapper mapper;
    final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions=mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
    final cn.iocoder.yudao.module.system.api.user.AdminUserApi users=mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var loader=new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(System.getenv("P22_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
        var properties=new java.util.Properties();properties.setProperty("user",System.getenv("P22_DB_USER"));properties.setProperty("password",System.getenv("P22_DB_PASSWORD"));
        temporaryConnection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);
        var source=new SingleConnectionDataSource(temporaryConnection,true);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TEMPORARY TABLE ast_device_shipment(id BIGINT PRIMARY KEY,tenant_id BIGINT,device_sn VARCHAR(100),source_key VARCHAR(100),contract_no VARCHAR(100),package_no VARCHAR(100),product_code VARCHAR(100),source_system VARCHAR(20),shipment_time TIMESTAMP(3),deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE ast_product_official_info(id BIGINT PRIMARY KEY,tenant_id BIGINT,product_code VARCHAR(100),product_model VARCHAR(100),product_name VARCHAR(100),deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE ast_device(id BIGINT PRIMARY KEY,tenant_id BIGINT,version BIGINT,sn VARCHAR(100),name VARCHAR(100),product_name VARCHAR(100),product_model VARCHAR(100),product_code VARCHAR(100),package_no VARCHAR(100),contract_no VARCHAR(100),status VARCHAR(30),source_system VARCHAR(30),source_key VARCHAR(100),source_version VARCHAR(100),sync_status VARCHAR(30),remark VARCHAR(100),shipment_time TIMESTAMP(3),synced_at TIMESTAMP(3),shipment_record_id BIGINT,project_assignment_version BIGINT,customer_assignment_version BIGINT,creator VARCHAR(64),create_time TIMESTAMP(3),updater VARCHAR(64),update_time TIMESTAMP(3),deleted BOOLEAN)");
        jdbc.update("INSERT INTO ast_device_shipment VALUES(10,1,'A','10','C','PK','I','DPPMS','2020-01-01',FALSE),(20,1,'B','20','C','PK','I','DPPMS','2020-01-01',FALSE)");
        jdbc.update("INSERT INTO ast_product_official_info VALUES(1,1,'I','MODEL','产品',FALSE)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String path="mapper/sync/DirectDeviceBaseMapper.xml";try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));mapper=session.getMapper(DirectDeviceBaseMapper.class);
        writer=new DirectDeviceBasePersistence(source,mapper,permissions,users);when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear() throws Exception {TenantContextHolder.clear();if(temporaryConnection!=null)temporaryConnection.close();}
    Seed seed(String sn){return new Seed(sn,"A".equals(sn)?10L:20L,"A".equals(sn)?"10":"20","C","PK",java.time.LocalDateTime.of(2020,1,1,0,0),"I","MODEL","产品");}
    BaseCommand command(String... sns){return new BaseCommand(1L,7L,"sync:1",java.time.LocalDateTime.of(2026,1,1,0,0),Arrays.stream(sns).map(this::seed).toList());}
    int count(){return jdbc.queryForObject("SELECT COUNT(*) FROM ast_device",Integer.class);}
    @Test void latestShipmentPreviewCreateAndReplayPreserveExistingDevice() {
        var preview=tx.execute(status->writer.previewBase(command("A"))).getFirst();assertEquals(-1L,preview.deviceId());assertEquals(0,count());assertThrows(IllegalStateException.class,()->writer.applyBase(command("A")));
        var saved=tx.execute(status->writer.applyBase(command("A"))).getFirst();assertTrue(saved.deviceId()>0);assertEquals(1,count());assertEquals("IN_STOCK",jdbc.queryForObject("SELECT status FROM ast_device",String.class));assertEquals(0L,jdbc.queryForObject("SELECT project_assignment_version FROM ast_device",Long.class));
        jdbc.update("UPDATE ast_device SET name='人工修改',version=5");assertEquals("UNCHANGED",tx.execute(status->writer.applyBase(command("A"))).getFirst().action());assertEquals("人工修改",jdbc.queryForObject("SELECT name FROM ast_device",String.class));assertEquals(5L,jdbc.queryForObject("SELECT version FROM ast_device",Long.class));
    }
    @Test void secondInsertConstraintRollsBackFirstDevice() {
        jdbc.execute("ALTER TABLE ast_device ADD CONSTRAINT second_device_rejected CHECK(sn<>'B')");var failure=assertThrows(org.springframework.jdbc.UncategorizedSQLException.class,()->tx.execute(status->writer.applyBase(command("A","B"))));assertEquals(3819,failure.getSQLException().getErrorCode());assertTrue(failure.getSQLException().getMessage().contains("second_device_rejected"));assertEquals(0,count());
    }
    @Test void latestOtherAuthorityAndChangedProductCannotBeBypassed() {
        jdbc.update("INSERT INTO ast_device_shipment VALUES(30,1,'A','30','C','PK','I','OTHER','2021-01-01',FALSE)");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.applyBase(command("A"))));jdbc.update("DELETE FROM ast_device_shipment WHERE id=30");
        jdbc.update("UPDATE ast_product_official_info SET product_model='changed'");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.applyBase(command("A"))));assertEquals(0,count());
    }
    @Test void permissionTenantDeletedAndEmptyProductScopeStayProtected() {
        when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(false);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->tx.execute(status->writer.applyBase(command("A"))));when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.previewBase(command("A"))));TenantContextHolder.setTenantId(1L);
        tx.execute(status->writer.applyBase(command("A")));jdbc.update("UPDATE ast_device SET deleted=TRUE");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.applyBase(command("A"))));assertEquals(1,count());
        assertTrue(mapper.selectShipmentProducts(new DirectDeviceBaseMapper.Products(1L,Set.of())).isEmpty());
    }
    @Test void directLocalSourceValidationIsIndependentAndReadOnlySnapshotContractIsKept() {
        var original=DppmsDeviceBootstrapSyncTemplate.create(9L);var direct=DppmsDirectDeviceBaseTemplate.create(9L);assertEquals("DPPMS_DEVICE_BOOTSTRAP",original.adapter());assertEquals("DPPMS_DEVICE_BOOTSTRAP_DIRECT",direct.adapter());assertEquals("LOCAL_OWNER",direct.sources().getFirst().readMode());
        assertDoesNotThrow(()->SyncLocalOwnerReader.validate(new SyncLocalOwnerReader.Input(direct,direct.sources().getFirst())));assertEquals("deviceSn",direct.sources().getFirst().sourceKey());
    }
}
