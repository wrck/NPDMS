package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationHistoryInsertApi.*;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
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

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="P33_TEMP_MYSQL_TEST",matches="true")
class DirectInstallationTransactionTest {
    java.sql.Connection temporaryConnection;JdbcTemplate jdbc;TransactionTemplate tx;DirectInstallationPersistence writer;DirectInstallationMapper mapper;
    final cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi devices=mock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class);
    final cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi locations=mock(cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi.class);
    final cn.iocoder.yudao.module.pms.project.api.reference.ProjectCodeQueryApi codes=mock(cn.iocoder.yudao.module.pms.project.api.reference.ProjectCodeQueryApi.class);
    final cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi historicalUsers=mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class);
    final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions=mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class);
    final cn.iocoder.yudao.module.system.api.user.AdminUserApi users=mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);var loader=new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(System.getenv("P22_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();var properties=new java.util.Properties();properties.setProperty("user",System.getenv("P22_DB_USER"));properties.setProperty("password",System.getenv("P22_DB_PASSWORD"));temporaryConnection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);var source=new SingleConnectionDataSource(temporaryConnection,true);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TEMPORARY TABLE imp_eng_installation(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,code VARCHAR(100),equipment_id BIGINT,install_location VARCHAR(255),address_id BIGINT,address_version BIGINT,site_id BIGINT,site_version BIGINT,site_location_id BIGINT,site_location_version BIGINT,location_resolution_status VARCHAR(30),address_snapshot TEXT,location_snapshot TEXT,effective_from TIMESTAMP(3),effective_to TIMESTAMP(3),install_time TIMESTAMP(3),installer_user_id BIGINT,environment_check TEXT,spec_check TEXT,photo_url VARCHAR(200),result VARCHAR(100),status INT,remark TEXT,version BIGINT,creator VARCHAR(64),create_time TIMESTAMP(3),updater VARCHAR(64),update_time TIMESTAMP(3),deleted BOOLEAN,UNIQUE KEY(tenant_id,project_id,code))");
        jdbc.execute("CREATE TEMPORARY TABLE fixture_location(id BIGINT PRIMARY KEY,kind VARCHAR(20))");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));String path="mapper/sync/DirectInstallationMapper.xml";try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));mapper=session.getMapper(DirectInstallationMapper.class);
        when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);when(codes.getProjectCode(100L)).thenReturn("P");
        when(devices.validateSelection(any())).thenReturn(List.of(new cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice(50L,"SN",null,null,null,null)));when(historicalUsers.getHistoricalIdentitiesByUsername(anyLong(),any())).thenReturn(List.of());
        when(locations.maintain(any())).thenAnswer(call->{long id=80L+jdbc.queryForObject("SELECT COUNT(*) FROM fixture_location",Long.class);jdbc.update("INSERT INTO fixture_location VALUES(?,'address')",id);return new LocationReferenceDTO("UNRESOLVED",id,0L,null,null,null,null,"原地址");});
        when(locations.getAddress(anyLong(),anyLong())).thenAnswer(call->new AddressRespDTO(call.getArgument(0),null,null,null,null,null,null,null,null,null,"原地址",null,null,0,0L));
        doAnswer(call->{jdbc.update("INSERT INTO fixture_location VALUES(999,'current')");return null;}).when(locations).effectEquipmentLocationIfAbsent(any());
        writer=new DirectInstallationPersistence(source,mapper,devices,locations,codes,historicalUsers,permissions,users);tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear() throws Exception {TenantContextHolder.clear();if(temporaryConnection!=null)temporaryConnection.close();}
    Row row(String id,String date) {var raw=new LinkedHashMap<String,String>();cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS.forEach(k->raw.put(k,null));raw.putAll(Map.of("id",id,"projectId","101","barcode","SN","installAddress","原地址","transferFlag","-1","createTime",date));return new Row(id,50L,raw);}
    Command command(Row... rows){return new Command(1L,7L,100L,List.of(rows));}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    @Test void completeDeviceHistoryPreviewCreateAndReplayPreserveLatestAndNullAudits() {
        var input=command(row("1","2019-01-01 00:00:00"),row("2","2020-01-01 00:00:00"));var preview=tx.execute(status->writer.preview(input));assertEquals("2",preview.getFirst().sourceKey());assertEquals(0,count("imp_eng_installation"));verifyNoInteractions(locations);assertThrows(IllegalStateException.class,()->writer.insert(input));
        var saved=tx.execute(status->writer.insert(input));assertEquals(2,count("imp_eng_installation"));assertEquals(3,count("fixture_location"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation WHERE effective_to IS NULL",Integer.class));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM imp_eng_installation WHERE creator IS NULL AND updater IS NULL AND status=2",Integer.class));assertNotEquals(saved.get(0).code(),saved.get(1).code());
        var existing=saved.stream().map(r->new Existing(r.sourceKey(),r.installationId(),50L,r.formalFieldsJson())).toList();assertEquals(2,tx.execute(status->writer.verify(new Verification(1L,7L,100L,existing))).size());jdbc.update("UPDATE imp_eng_installation SET install_location='manual' WHERE id=?",saved.get(0).installationId());assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.verify(new Verification(1L,7L,100L,existing))));
    }
    @Test void secondInsertFailureRollsBackFirstRecordAndSharedLocationSideEffects() {
        jdbc.execute("ALTER TABLE imp_eng_installation ADD CONSTRAINT older_installation_rejected CHECK(create_time>'2019-01-01')");var failure=assertThrows(org.springframework.jdbc.UncategorizedSQLException.class,()->tx.execute(status->writer.insert(command(row("1","2019-01-01 00:00:00"),row("2","2020-01-01 00:00:00")))));assertEquals(3819,failure.getSQLException().getErrorCode());assertTrue(failure.getSQLException().getMessage().contains("older_installation_rejected"));assertEquals(0,count("imp_eng_installation"));assertEquals(0,count("fixture_location"));
    }
    @Test void equalTimesTransfersAndExistingNewerCurrentAreProtected() {
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.insert(command(row("1","2020-01-01"+" 00:00:00"),row("2","2020-01-01 00:00:00")))));var transfer=row("3","2020-01-01 00:00:00");transfer.originalFields().put("transferProjectId","200");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.insert(command(transfer))));assertEquals(0,count("fixture_location"));
        tx.execute(status->writer.insert(command(row("3","2021-01-01 00:00:00"))));clearInvocations(locations);var older=tx.execute(status->writer.insert(command(row("1","2020-01-01 00:00:00")))).getFirst();assertEquals(java.time.LocalDateTime.of(2021,1,1,0,0),older.effectiveTo());verify(locations,never()).effectEquipmentLocationIfAbsent(any());assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.insert(command(row("4","2022-01-01 00:00:00")))));
    }
    @Test void permissionTenantAndUnresolvedAuditOrDeviceCannotWrite() {
        var missing=row("1","2020-01-01 00:00:00");missing.originalFields().put("createBy","unresolved");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.insert(command(missing))));when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(false);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->tx.execute(status->writer.insert(command(row("1","2020-01-01 00:00:00")))));TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.preview(command(row("1","2020-01-01 00:00:00")))));assertEquals(0,count("imp_eng_installation"));
        var definition=DppmsDirectInstallationTemplate.create(9L);assertDoesNotThrow(()->DppmsInstallationStream.validate(definition));assertEquals("id",definition.sources().getFirst().sourceKey());
    }
}
