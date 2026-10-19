package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="BASE_PRODUCT_TEMP_MYSQL_TEST",matches="true")
class DeviceBaseMySqlTest {
    java.sql.Connection connection;java.net.URLClassLoader loader;JdbcTemplate jdbc;SqlSessionTemplate session;
    TransactionTemplate transaction;P30DeviceBaseSyncTask task;SyncIdentityStore identities;
    final LocalDateTime at=LocalDateTime.of(2026,10,17,12,0);
    @BeforeEach void setup()throws Exception{
        loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,loader).getConstructor().newInstance();
        var properties=new Properties();properties.setProperty("user",System.getenv("BASE_PRODUCT_DB_USER"));properties.setProperty("password",System.getenv("BASE_PRODUCT_DB_PASSWORD"));
        connection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);
        var data=new SingleConnectionDataSource(connection,true);jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TEMPORARY TABLE ast_device_shipment(id BIGINT PRIMARY KEY,tenant_id BIGINT,device_sn VARCHAR(100),source_system VARCHAR(32),source_key VARCHAR(128),contract_no VARCHAR(64),package_no VARCHAR(128),shipment_time DATETIME(3),product_code VARCHAR(64),deleted BOOLEAN)");
        jdbc.execute("CREATE TEMPORARY TABLE ast_product_official_info(id BIGINT PRIMARY KEY,tenant_id BIGINT,product_code VARCHAR(64),product_model VARCHAR(128),product_name VARCHAR(128),deleted BOOLEAN)");
        var columns=new ArrayList<String>();
        for(var field:P30DeviceBaseSyncTask.DeviceRow.class.getDeclaredFields())columns.add("`"+column(field.getName())+"` "+(field.getType()==Long.class?"BIGINT":field.getType()==Boolean.class?"BOOLEAN":field.getType()==LocalDateTime.class?"DATETIME(3)":"VARCHAR(500)"));
        jdbc.execute("CREATE TEMPORARY TABLE ast_device("+String.join(",",columns)+",PRIMARY KEY(id),UNIQUE(tenant_id,sn))");
        var config=new Configuration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("target",new SpringManagedTransactionFactory(),data));
        String resource="mapper/basesync/P30TargetMapper.xml";try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));transaction=new TransactionTemplate(new DataSourceTransactionManager(data));
        identities=mock(SyncIdentityStore.class);when(identities.findByKeys(any())).thenReturn(Map.of());task=new P30DeviceBaseSyncTask(session.getMapper(P30DeviceBaseSyncTask.TargetMapper.class));
    }
    @AfterEach void close()throws Exception{try{if(connection!=null)connection.close();}finally{if(loader!=null)loader.close();}}
    static String column(String name){return name.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);}
    SyncContext<Void> context(boolean preview){return new SyncContext<>(1,2,3,preview,1,100,at,mock(SyncSource.class),identities,mock(SyncReferences.class),"DeviceBase",session,false,true);}
    SyncCounts run(boolean preview){return transaction.execute(status->task.execute(context(preview)));}
    void shipment(long id,String sn,String source,String time){jdbc.update("INSERT INTO ast_device_shipment VALUES(?,1,?,?,?,'CON','PACKAGE',?,'PRODUCT',FALSE)",id,sn,source,"source-"+id,time);}
    @Test void emptyFormalShipmentsAreAnExplicitPrerequisiteGapAndDoNotInventDevices(){
        var context=context(true);assertEquals(SyncCounts.empty(),transaction.execute(status->task.execute(context)));
        assertEquals(List.of("P30_FORMAL_DPPMS_SHIPMENTS_MISSING:0"),context.runState().warnings());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device",Integer.class));
    }
    @Test void latestFormalShipmentCreatesOnlyTheBaseAndPreservesExistingAssignmentsOnReplay(){
        shipment(1,"SN","DPPMS","2020-01-01");shipment(2,"SN","DPPMS","2021-01-01");
        jdbc.update("INSERT INTO ast_product_official_info VALUES(1,1,'PRODUCT','MODEL','原设备名称',FALSE)");
        assertEquals(new SyncCounts(1,1,0,0,0),run(true));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device",Integer.class));
        assertEquals(new SyncCounts(1,1,0,0,0),run(false));assertEquals(2L,jdbc.queryForObject("SELECT shipment_record_id FROM ast_device",Long.class));
        assertEquals("MODEL",jdbc.queryForObject("SELECT product_model FROM ast_device",String.class));
        jdbc.update("UPDATE ast_device SET status='IN_USE',version=10,project_assignment_version=3,remark='formal business'");
        assertEquals(new SyncCounts(1,0,0,1,0),run(false));assertEquals("IN_USE",jdbc.queryForObject("SELECT status FROM ast_device",String.class));
        assertEquals(10L,jdbc.queryForObject("SELECT version FROM ast_device",Long.class));assertEquals("formal business",jdbc.queryForObject("SELECT remark FROM ast_device",String.class));
    }
    @Test void newerForeignShipmentRollsBackAnEarlierBatchAndManualDeviceIsNeverAdopted(){
        shipment(1,"A","DPPMS","2020-01-01");shipment(2,"B","DPPMS","2020-01-01");shipment(3,"B","SAP","2021-01-01");
        assertThrows(IllegalStateException.class,()->run(false));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device",Integer.class));
        jdbc.update("DELETE FROM ast_device_shipment WHERE id IN (2,3)");run(false);
        jdbc.update("UPDATE ast_device SET source_system='MANUAL'");assertThrows(IllegalStateException.class,()->run(true));
        assertEquals("MANUAL",jdbc.queryForObject("SELECT source_system FROM ast_device",String.class));
    }
    @Test void twoBatchesReadSmallProductCatalogueOnlyOncePerRun(){
        shipment(1,"A","DPPMS","2020-01-01");shipment(2,"B","DPPMS","2020-01-01");
        var mapper=mock(P30DeviceBaseSyncTask.TargetMapper.class,org.mockito.AdditionalAnswers.delegatesTo(session.getMapper(P30DeviceBaseSyncTask.TargetMapper.class)));
        task=new P30DeviceBaseSyncTask(mapper);
        assertEquals(new SyncCounts(2,2,0,0,0),run(true));
        verify(mapper,times(1)).selectProducts(any());verify(mapper,times(2)).selectLatestShipments(any());
    }
    @Test void mappedLocalQueriesAreAcceptedByTheProductionSqlParser()throws Exception{
        new cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration();
        String namespace=P30DeviceBaseSyncTask.TargetMapper.class.getName()+".";
        var config=session.getConfiguration();
        var serials=Map.of("query",new P30DeviceBaseSyncTask.TargetMapper.SerialsQuery(1,List.of("SN"),true));
        for(var name:List.of("selectDeviceBase","selectLatestShipments")){
            Object parameter="selectDeviceBase".equals(name)?new AbstractSyncTask.SourceQuery<P30DeviceBaseSyncTask.Parameters>(100,1,true,null):serials;
            var sql=config.getMappedStatement(namespace+name).getBoundSql(parameter).getSql();
            assertNotNull(com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.parse(sql));
        }
    }
}
