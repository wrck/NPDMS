package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.*;
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

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="P35_TEMP_MYSQL_TEST",matches="true")
class DirectSoftwareHistoryTransactionTest {
    java.sql.Connection temporaryConnection;JdbcTemplate jdbc;TransactionTemplate tx;DirectSoftwareHistoryPersistence writer;
    DirectSoftwareHistoryMapper observations;DirectSoftwareBatchMapper batches;DirectDeviceBaseMapper identities;
    final cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi guard=mock(cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi.class);
    final cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi users=mock(cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.class);
    final cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryExtensionApi extensions=mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryExtensionApi.class);
    final java.time.LocalDateTime at=java.time.LocalDateTime.of(2024,1,2,3,4,5);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);var loader=new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(System.getenv("P22_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();var properties=new java.util.Properties();properties.setProperty("user",System.getenv("P22_DB_USER"));properties.setProperty("password",System.getenv("P22_DB_PASSWORD"));temporaryConnection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);var source=new SingleConnectionDataSource(temporaryConnection,true);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TEMPORARY TABLE ast_device(id BIGINT PRIMARY KEY,tenant_id BIGINT,sn VARCHAR(100),deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE ast_device_software_change_batch(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,change_version VARCHAR(100),change_remark VARCHAR(100),latest INT,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE ast_device_software_version_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,device_id BIGINT,device_sn VARCHAR(100),project_id BIGINT,previous_record_id BIGINT,conp_version VARCHAR(100),conp_type VARCHAR(100),conp_series VARCHAR(100),conp_mark VARCHAR(100),boot_version VARCHAR(100),cpld_version VARCHAR(100),pcb_version VARCHAR(100),customized BOOLEAN,release_date DATE,version_desc VARCHAR(100),recorded_at TIMESTAMP,data_source VARCHAR(30),parsed_fact_id BIGINT,request_key VARCHAR(100),change_batch_id BIGINT,contract_no VARCHAR(100),product_code VARCHAR(100),execute_time DATE,data_state INT,custom_info JSON,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN)");
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));for(String name:List.of("DirectSoftwareHistoryMapper","DirectSoftwareBatchMapper","DirectDeviceBaseMapper")){String path="mapper/sync/"+name+".xml";try(var input=new ClassPathResource(path).getInputStream()){new XMLMapperBuilder(input,configuration,path,configuration.getSqlFragments()).parse();}}var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));observations=session.getMapper(DirectSoftwareHistoryMapper.class);batches=session.getMapper(DirectSoftwareBatchMapper.class);identities=session.getMapper(DirectDeviceBaseMapper.class);
        when(guard.validate(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));when(users.getHistoricalIdentitiesByUsername(anyLong(),any())).thenReturn(List.of());
        writer=new DirectSoftwareHistoryPersistence(source,observations,batches,identities,guard,users,extensions);tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @AfterEach void clear() throws Exception {TenantContextHolder.clear();if(temporaryConnection!=null)temporaryConnection.close();}
    SoftwareObservation observation(long id) {return new SoftwareObservation(id,10L,302L,"CONTRACT","ITEM","SN","NEW","TYPE","SERIES","MARK","OLD",0,"CPLD","CPLD-OLD",0,"BOOT","BOOT-OLD",0,"PCB","PCB-OLD",0,null,0,at,null,at,null,"{\"value\":null}").withMapping(null,null,99L);}
    SoftwareBatch batch(Long target){return new SoftwareBatch(302L,10L,"v1","",1,null,at,null,null,target);}
    Command command(List<SoftwareObservation> rows,SoftwareBatch batch){return new Command(1L,7L,100L,"10","sync:1",at,rows,List.of(batch),List.of());}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    @Test void previewAndTypedInsertPreserveBatchNullAuditsEightFieldsAndReplay() {
        var input=command(List.of(observation(301)),batch(null));assertEquals(2,tx.execute(status->writer.previewSourceHistory(input)).size());assertEquals(0,count("ast_device_software_change_batch"));verify(extensions,never()).initialize(any());verify(extensions).preview(argThat(c->c.recordId()<0 && c.values().size()==8));assertThrows(IllegalStateException.class,()->writer.importSourceHistory(input));
        var result=tx.execute(status->writer.importSourceHistory(input));long batchId=result.get(0).targets().getFirst().targetId();long recordId=result.get(1).targets().getFirst().targetId();assertEquals(1,count("ast_device_software_version_record"));assertEquals(batchId,jdbc.queryForObject("SELECT change_batch_id FROM ast_device_software_version_record",Long.class));assertNull(jdbc.queryForObject("SELECT creator FROM ast_device_software_change_batch",String.class));assertNull(jdbc.queryForObject("SELECT device_id FROM ast_device_software_version_record",Long.class));verify(extensions).initialize(argThat(c->c.recordId()==recordId && c.definitionId()==99L && c.values().size()==8));
        var replay=command(List.of(observation(301).withMapping(recordId,batchId,99L)),batch(batchId));assertEquals(2,tx.execute(status->writer.importSourceHistory(replay)).size());assertEquals(1,count("ast_device_software_version_record"));jdbc.update("UPDATE ast_device_software_version_record SET conp_version='manual'");assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.importSourceHistory(replay)));
    }
    @Test void secondVersionSqlFailureRollsBackBatchAndFirstVersion() {
        jdbc.execute("ALTER TABLE ast_device_software_version_record ADD CONSTRAINT second_version_rejected CHECK(product_code<>'reject')");var bad=observation(303);bad=new SoftwareObservation(bad.sourceId(),bad.sourceProjectId(),bad.logId(),bad.contractNo(),"reject",bad.barCode(),bad.conp(),bad.conpType(),bad.conpSeries(),bad.conpMark(),bad.conpBak(),bad.conpChange(),bad.cpld(),bad.cpldBak(),bad.cpldChange(),bad.boot(),bad.bootBak(),bad.bootChange(),bad.pcb(),bad.pcbBak(),bad.pcbChange(),bad.executeTime(),bad.datastate(),bad.sourceCreatedAt(),bad.sourceCreatedBy(),bad.sourceUpdatedAt(),bad.sourceUpdatedBy(),bad.customInfo()).withMapping(null,null,99L);var input=command(List.of(observation(301),bad),batch(null));
        var failure=assertThrows(org.springframework.jdbc.UncategorizedSQLException.class,()->tx.execute(status->writer.importSourceHistory(input)));assertEquals(3819,failure.getSQLException().getErrorCode());assertTrue(failure.getSQLException().getMessage().contains("second_version_rejected"));assertEquals(0,count("ast_device_software_change_batch"));assertEquals(0,count("ast_device_software_version_record"));
    }
    @Test void extensionFailureRollsBackBothTypedRecordsAndMissingDefinitionDoesNotGetInvented() {
        doThrow(new IllegalArgumentException("eight approved properties required")).when(extensions).initialize(any());assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.importSourceHistory(command(List.of(observation(301)),batch(null)))));assertEquals(0,count("ast_device_software_change_batch"));assertEquals(0,count("ast_device_software_version_record"));
        doThrow(new IllegalArgumentException("definition missing")).when(extensions).preview(any());assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.previewSourceHistory(command(List.of(observation(301)),batch(null)))));assertEquals(0,count("ast_device_software_change_batch"));
    }
    @Test void virtualParentProjectAndTenantAreProtected() {
        assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.previewSourceHistory(command(List.of(observation(301).withMapping(null,-999L,99L)),batch(null)))));when(guard.validate(any())).thenReturn(null);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.importSourceHistory(command(List.of(observation(301)),batch(null)))));TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->tx.execute(status->writer.previewSourceHistory(command(List.of(observation(301)),batch(null)))));assertEquals(0,count("ast_device_software_change_batch"));
        assertEquals("DPPMS_SOFTWARE_HISTORY_DIRECT",DppmsDirectSoftwareHistoryTemplate.create(9L).adapter());
    }
}
