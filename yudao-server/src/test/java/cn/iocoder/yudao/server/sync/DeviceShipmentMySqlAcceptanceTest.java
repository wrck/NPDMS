package cn.iocoder.yudao.server.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import cn.iocoder.yudao.module.pms.integration.sync.generic.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.service.migration.*;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.redisson.api.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real MySQL + repository DDL + actual source SQL, cursor, runner, engine, bindings
 * and PLT evidence. Only the scheduler/Redis mutex and connection registration are
 * test substitutes; no sync business writes or evidence calls are mocked.
 * Source values are generated, NOT a production DPPMS export. */
@EnabledIfSystemProperty(named="pms.sync.mysql.business",matches="true")
class DeviceShipmentMySqlAcceptanceTest {
    static final String DB=System.getProperty("pms.sync.mysql.database","npdms_sync_business_20261009");
    static { if(!DB.matches("npdms_sync_business_20261009(?:_v[0-9]+)?"))throw new IllegalArgumentException("Only explicitly namespaced isolated test databases are allowed"); }
    static final String URL="jdbc:mysql://127.0.0.1:23316/"+DB+
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true";
    static boolean failAfterEvidence;
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    SyncTaskService tasks;
    SyncRunService runner;
    Path root;
    @BeforeEach void setup() throws Exception {
        root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("sql/migrations")))root=root.getParent();
        var ds=new DriverManagerDataSource(URL,"root","");jdbc=new JdbcTemplate(ds);
        assertEquals(DB,jdbc.queryForObject("SELECT DATABASE()",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class),
                "Requires a fresh explicitly named isolated database; never cleans existing data");
        new ResourceDatabasePopulator(new FileSystemResource(root.resolve("docs/generated/sync-optimization-20261009/business-schema.sql"))).execute(ds);
        new ResourceDatabasePopulator(new FileSystemResource(root.resolve("sql/migrations/V402__sync_hot_path_business_key_indexes.sql"))).execute(ds);
        sourceFixture();
        TenantContextHolder.setTenantId(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(1L).setTenantId(1L),null,List.of()));
        context=new AnnotationConfigApplicationContext(Config.class);
        tasks=context.getBean(SyncTaskService.class);runner=context.getBean(SyncRunService.class);
        jdbc.update("INSERT INTO int_sync_connection(id,tenant_id,data_source_id,name) VALUES(1,1,1,'isolated generated source'),(2,2,1,'isolated tenant 2')");
    }
    @AfterEach void close(){if(context!=null)context.close();TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();failAfterEvidence=false;}
    void sourceFixture() {
        // These are generated source fixtures, not a claim that DPPMS DDL was exported.
        // All columns referenced by the UNMODIFIED shipped source SQL/mappings exist.
        jdbc.execute("CREATE TABLE fb_contract(contract_id BIGINT PRIMARY KEY,contract_code VARCHAR(64)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE fb_shipment(packlist_id BIGINT PRIMARY KEY,con_id BIGINT,packdate DATETIME(3)) ENGINE=InnoDB");
        jdbc.execute("""
            CREATE TABLE fb_shipment_barcode(
              id BIGINT PRIMARY KEY,barcode VARCHAR(200),barcode2 VARCHAR(100),item VARCHAR(64),item2 VARCHAR(64),
              com_barcode VARCHAR(128),pack_id BIGINT,warrantyStartDate DATE,warrantyMonth INT,rma_no VARCHAR(128),
              rmaBarcode VARCHAR(100),updateTime DATETIME(3),orderNumber VARCHAR(64),lineNum VARCHAR(64),
              isRMA INT,profitCenter VARCHAR(32),soleAgentSuffix VARCHAR(32),syncTime DATETIME,uuid VARCHAR(64),
              source_only_note VARCHAR(100),INDEX(pack_id)) ENGINE=InnoDB
            """);
        jdbc.execute("""
            CREATE TABLE fb_shipment_barcode_relation(id BIGINT PRIMARY KEY,sn1 VARCHAR(100),sn2 VARCHAR(100),
              contract VARCHAR(64),createtime DATETIME(3),updatetime DATETIME(3),item1 VARCHAR(15),item2 VARCHAR(15)) ENGINE=InnoDB
            """);
        jdbc.update("INSERT INTO fb_contract VALUES(1,'CONTRACT-A')");
        jdbc.update("INSERT INTO fb_shipment VALUES(1,1,'2026-10-01 10:00:00')");
        jdbc.update("""
            INSERT INTO com_sales_order(id,tenant_id,source_system,source_record_key,source_version,company_code,
                order_type,order_no,status,creator,updater) VALUES(10,1,'ERP','ORDER-A','1','COMPANY-A','SALE','ORDER-A','ACTIVE','test','test')
            """);
        jdbc.update("""
            INSERT INTO com_sales_order_line(id,tenant_id,order_id,source_system,source_record_key,source_version,
              company_code,order_type,order_no,line_no,product_code,unit_code,unit_scale,quantity_status,status,creator,updater)
            VALUES(11,1,10,'ERP','LINE-A','1','COMPANY-A','SALE','ORDER-A','1','PRODUCT-A','EA',0,'PENDING','ACTIVE','test','test')
            """);
        int count=Integer.getInteger("pms.sync.mysql.rows",1000);assertTrue(count>=3&&count<=10000);
        for(int offset=0;offset<count;offset+=500) {
            List<Object[]> values=new ArrayList<>();
            for(int n=offset;n<Math.min(offset+500,count);n++)values.add(new Object[]{n+1,"SN-"+(n+1),n==0?"SECONDARY-1":null,"PRODUCT-A",n==0?"PRODUCT-B":null,"INTERNAL-"+(n+1),1,24,"ORDER-A","1","UUID-"+(n+1),"raw-"+(n+1)});
            jdbc.batchUpdate("INSERT INTO fb_shipment_barcode(id,barcode,barcode2,item,item2,com_barcode,pack_id,warrantyMonth,orderNumber,lineNum,uuid,source_only_note) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",values);
        }
        jdbc.update("INSERT INTO fb_shipment_barcode_relation VALUES(1,'SN-1','SECONDARY-1','CONTRACT-A','2026-10-01','2026-10-01','PRODUCT-A','PRODUCT-B')");
        jdbc.update("INSERT INTO fb_shipment_barcode_relation VALUES(2,'SELF','SELF','CONTRACT-A','2026-10-01','2026-10-01','A','A')");
    }
    SyncDefinition definition(String key,long connection) {
        return new GenericSyncTemplates().list(connection).stream().filter(t->t.key().equals(key)).findFirst().orElseThrow().definition()
                .toBuilder().evidencePolicy("REUSE_UNCHANGED").chunkSize(500).fetchSize(500).build();
    }
    long task(SyncDefinition d){return tasks.save(new SyncTaskService.Save(null,null,"MySQL actual "+d.taskKey(),d));}
    long start(long task,boolean preview){return runner.start(new SyncRunService.Start(task,tasks.required(task).getVersion(),UUID.randomUUID().toString(),preview,true,false,null,false));}
    long run(long task,boolean preview) throws Exception {
        long id=start(task,preview);long begin=System.nanoTime();runner.execute(id);
        var result=runner.required(id);
        System.out.println("BUSINESS_SYNC_RUN="+JsonUtils.toJsonString(Map.of("run",id,"rows",result.getReadCount(),"seconds",(System.nanoTime()-begin)/1e9,"status",result.getStatus(),"summary",result.getSummaryJson())));
        assertEquals(preview?"PREVIEW_READY":"SUCCESS",result.getStatus());return id;
    }
    long count(String table){assertTrue(table.matches("[a-z_]+"));return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=?",Long.class,TenantContextHolder.getRequiredTenantId());}
    Map<String,Long> evidenceCounts(){return Map.of("source",count("plt_migration_source_record"),"mapping",count("plt_external_key_mapping"),"binding",count("int_sync_binding"));}
    @Test void actualDeviceShipmentBusinessChain() throws Exception {
        int n=Integer.getInteger("pms.sync.mysql.rows",1000);
        long shipment=task(definition("shipment-barcode",1));
        long first=run(shipment,false);
        assertEquals(n+1,count("ast_device"));assertEquals(n,count("ast_device_shipment"));
        assertEquals(n,count("int_sync_binding"));assertEquals(n,count("plt_migration_source_record"));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment s LEFT JOIN ast_device d ON d.tenant_id=s.tenant_id AND d.sn=s.device_sn WHERE s.tenant_id=1 AND d.id IS NULL",Long.class));
        assertEquals(n,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment WHERE tenant_id=1 AND order_line_id=11",Integer.class));
        assertEquals(n,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device d JOIN ast_device_shipment s ON s.id=d.shipment_record_id AND s.tenant_id=d.tenant_id AND s.device_sn=d.sn WHERE d.tenant_id=1",Integer.class));
        var initial=evidenceCounts();
        long replay=run(shipment,false);
        assertEquals(initial,evidenceCounts(),"Replay must not append PLT evidence or bindings");
        assertTrue(runner.required(replay).getSummaryJson().contains("EVIDENCE_REUSED"));
        assertEquals(n,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding WHERE tenant_id=1 AND last_run_id=?",Integer.class,first));
        // Existing shipment events are immutable: never rewrite historical warranty facts.
        var beforeConflict=evidenceCounts();var oldCheckpoint=tasks.required(shipment).getCheckpoint();
        jdbc.update("UPDATE fb_shipment_barcode SET warrantyMonth=36 WHERE id=1");
        long conflict=start(shipment,false);assertThrows(Exception.class,()->runner.execute(conflict));
        assertEquals("FAILED",runner.required(conflict).getStatus());assertEquals(beforeConflict,evidenceCounts());
        assertEquals(oldCheckpoint,tasks.required(shipment).getCheckpoint());
        assertEquals(24,jdbc.queryForObject("SELECT warranty_months FROM ast_device_shipment WHERE tenant_id=1 AND source_key='1'",Integer.class));
        jdbc.update("UPDATE fb_shipment_barcode SET warrantyMonth=24 WHERE id=1");
        // A NEW shipment event for the same SN advances its latest projection, not its historical row.
        jdbc.update("INSERT INTO fb_shipment VALUES(2,1,'2026-10-02 10:00:00')");
        jdbc.update("INSERT INTO fb_shipment_barcode(id,barcode,item,pack_id,warrantyMonth,orderNumber,lineNum) VALUES(999998,'SN-1','PRODUCT-A',2,36,'ORDER-A','1')");
        run(shipment,true);assertEquals(n,count("ast_device_shipment"));
        long beforeChange=count("plt_migration_source_record");run(shipment,false);
        assertEquals(n+1,count("ast_device_shipment"));assertEquals(n+1,count("ast_device"));
        assertEquals(beforeChange+1,count("plt_migration_source_record"));
        assertEquals("999998",jdbc.queryForObject("SELECT s.source_key FROM ast_device d JOIN ast_device_shipment s ON s.id=d.shipment_record_id WHERE d.tenant_id=1 AND d.sn='SN-1'",String.class));
        run(shipment,false); // Record legitimate derived latest-projection changes before measuring one raw change.
        jdbc.update("UPDATE fb_shipment_barcode SET source_only_note='changed-unmapped' WHERE id=2");
        long beforeRaw=count("plt_migration_source_record");run(shipment,false);
        assertEquals(beforeRaw+1,count("plt_migration_source_record"),"Unmapped source changes remain auditable");
        // Target drift in immutable historical facts must be detected, not silently reused or overwritten.
        jdbc.update("UPDATE ast_device_shipment SET warranty_months=99 WHERE tenant_id=1 AND source_key='3'");
        long drift=start(shipment,false);assertThrows(Exception.class,()->runner.execute(drift));
        assertEquals(99,jdbc.queryForObject("SELECT warranty_months FROM ast_device_shipment WHERE tenant_id=1 AND source_key='3'",Integer.class));
        jdbc.update("UPDATE ast_device_shipment SET warranty_months=24 WHERE tenant_id=1 AND source_key='3'");
        var beforeFailure=evidenceCounts();var checkpoint=tasks.required(shipment).getCheckpoint();
        jdbc.update("INSERT INTO fb_shipment_barcode(id,barcode,item,pack_id,warrantyMonth,orderNumber,lineNum) VALUES(999997,'ROLLBACK-SN','PRODUCT-A',2,48,'ORDER-A','1')");
        failAfterEvidence=true;long failed=start(shipment,false);
        assertThrows(Exception.class,()->runner.execute(failed));failAfterEvidence=false;
        assertEquals("FAILED",runner.required(failed).getStatus());assertEquals(beforeFailure,evidenceCounts());
        assertEquals(checkpoint,tasks.required(shipment).getCheckpoint());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE tenant_id=1 AND sn='ROLLBACK-SN'",Integer.class));
        run(shipment,false);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE tenant_id=1 AND sn='ROLLBACK-SN'",Integer.class));
        // An invalid source is retained as an issue, without partial device/event writes.
        jdbc.update("INSERT INTO fb_shipment_barcode(id,barcode,pack_id,item) VALUES(999999,?,1,'PRODUCT-A')","X".repeat(101));
        long beforeIssues=count("plt_migration_issue");run(shipment,false);
        assertEquals(beforeIssues+1,count("plt_migration_issue"));assertEquals(n+2,count("ast_device_shipment"));
        // Separate shipped pure-copy template must NOT invent device masters.
        TenantContextHolder.setTenantId(2L);
        long pure=task(definition("device-sn",2));run(pure,false);
        assertEquals(0,count("ast_device"));assertEquals(n+2,count("ast_device_shipment"));assertEquals(1,count("ast_device_relationship"));
        assertEquals("PRODUCT-A",jdbc.queryForObject("SELECT source_item FROM ast_device_relationship WHERE tenant_id=2",String.class));
        assertEquals(n+2,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment WHERE tenant_id=2 AND product_code='PRODUCT-A' AND order_no='ORDER-A'",Integer.class));
        assertEquals(n+2,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment WHERE tenant_id=1",Integer.class));
        var pureEvidence=evidenceCounts();run(pure,false);
        assertEquals(pureEvidence.get("binding"),count("int_sync_binding"));
        // The bad row is intentionally retried and retained as a new issue, not silently reused.
        assertEquals(pureEvidence.get("source")+1,count("plt_migration_source_record"));
        System.out.println("BUSINESS_MYSQL_ACCEPTANCE=PASS production_schema=16_tables source_data=GENERATED real_cursor_runner_engine_binding_PLT=true");
    }
    static class FaultEvidenceService extends SyncEvidenceService {
        FaultEvidenceService(PlatformMigrationEvidenceApi api){super(api);}
        @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
        public void complete(Long run,SyncDefinition d,List<Evidence> evidence,DataSyncAdapter.Descriptor descriptor,List<DataSyncAdapter.Change> changes,boolean failed) {
            super.complete(run,d,evidence,descriptor,changes,failed);
            if(failAfterEvidence && changes.stream().anyMatch(c->"ROLLBACK-SN".equals(c.after().get("barcode"))))throw new IllegalStateException("TEST_ONLY failure after actual PLT completion");
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    @Import({SyncRunService.class,SyncTaskService.class,SyncConnectionService.class,SyncDefinitionValidator.class,SyncFieldMapper.class,
        GenericSyncEngine.class,GenericTargetCatalog.class,GenericSyncJdbcStore.class,
        PlatformCommandExecutionApiImpl.class,PlatformTransactionalOutboxWriter.class,
        PlatformMigrationEvidenceApiImpl.class,PlatformMigrationEvidenceTransactionExecutor.class})
    @MapperScan({"cn.iocoder.yudao.module.pms.integration.dal.mysql.sync",
        "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration","cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    static class Config {
        @Bean cn.hutool.extra.spring.SpringUtil springUtil(){return new cn.hutool.extra.spring.SpringUtil();}
        @Bean DataSource dataSource(){return new DriverManagerDataSource(URL,"root","");}
        @Bean PlatformTransactionManager transactionManager(DataSource d){return new DataSourceTransactionManager(d);}
        @Bean SyncEvidenceService evidence(PlatformMigrationEvidenceApi api){return new FaultEvidenceService(api);}
        @Bean SyncBatchLauncher launcher(){return mock(SyncBatchLauncher.class);}
        @Bean ExternalDataSourceApi sources(DataSource ds){return new ExternalDataSourceApi(){
            public List<Info> list(){return List.of(new Info(1L,"generated fixture",URL,"root"));}
            public Long save(Save request){throw new UnsupportedOperationException();}
            public Connection openReadOnly(Long id)throws java.sql.SQLException {assertEquals(1L,id);var c=ds.getConnection();c.setReadOnly(true);c.setAutoCommit(false);return c;}
        };}
        @Bean SpringJdbcStreamingReader streamingReader(ExternalDataSourceApi sources){return new SpringJdbcStreamingReader(Optional.of(sources));}
        @Bean MysqlSyncReader reader(ExternalDataSourceApi sources){return new MysqlSyncReader(sources);}
        @Bean RedissonClient redisson()throws Exception {var r=mock(RedissonClient.class);var lock=mock(RLock.class);when(r.getLock(anyString())).thenReturn(lock);when(lock.tryLock(anyLong(),any(TimeUnit.class))).thenReturn(true);when(lock.isHeldByCurrentThread()).thenReturn(true);return r;}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource d)throws Exception {
            var f=new MybatisSqlSessionFactoryBean();f.setDataSource(d);
            var global=new GlobalConfig();global.setMetaObjectHandler(new DefaultDBFieldHandler());f.setGlobalConfig(global);
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);f.setConfiguration(config);
            var resolver=new PathMatchingResourcePatternResolver();var resources=new ArrayList<org.springframework.core.io.Resource>();
            for(String folder:List.of("sync","migration","command"))resources.addAll(List.of(resolver.getResources("classpath*:mapper/"+folder+"/*.xml")));
            f.setMapperLocations(resources.toArray(org.springframework.core.io.Resource[]::new));return f.getObject();
        }
    }
}
