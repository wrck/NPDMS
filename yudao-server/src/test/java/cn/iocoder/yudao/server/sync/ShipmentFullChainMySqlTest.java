package cn.iocoder.yudao.server.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.GenericSyncJdbcStore;
import cn.iocoder.yudao.module.pms.integration.sync.generic.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.service.migration.*;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import cn.iocoder.yudao.module.pms.asset.service.device.*;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ContractDeviceVisibilityApi;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi;
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
import org.redisson.api.RedissonClient;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in generated-data acceptance: exact production target DDL, unchanged shipped
 * mappings/SQL, real Spring Batch JDBC metadata/async launch, Redis mutex, cursor,
 * target business writes, contract organization projection, binding and PLT evidence.
 * No production source export is represented. Existing databases are never cleared.
 * Project-linked devices and UI/HTTP are not part of this contract shipment scenario. */
@EnabledIfSystemProperty(named="pms.sync.mysql.fullChain",matches="true")
class ShipmentFullChainMySqlTest {
    static final String DB=System.getProperty("pms.sync.mysql.fullChain.database","npdms_sync_full_chain_20261009_smoke");
    static { if(!DB.matches("npdms_sync_full_chain_20261009_[a-z0-9]+"))throw new IllegalArgumentException("Dedicated isolated database required"); }
    static final String REUSE_SOURCE_DB=System.getProperty("pms.sync.mysql.fullChain.reuseSourceDatabase","");
    static { if(!REUSE_SOURCE_DB.isEmpty() && (!REUSE_SOURCE_DB.matches("npdms_sync_full_chain_20261009_[a-z0-9]+") || REUSE_SOURCE_DB.equals(DB)))
        throw new IllegalArgumentException("Reusable generated sources must use a different isolated database"); }
    static String reuseName(String table) { return "reuse_"+DB.substring("npdms_sync_full_chain_20261009_".length())+"_"+table; }
    static String reuseQualified(String table) { return "`"+REUSE_SOURCE_DB+"`.`"+reuseName(table)+"`"; }
    static SyncDefinition.Source reuseDefinition(SyncDefinition.Source source) {
        if(REUSE_SOURCE_DB.isEmpty())return source;
        String sql=source.sql(),table=source.table();
        for(String name:List.of("fb_shipment_barcode","fb_shipment","fb_contract")) {
            if(sql!=null)sql=sql.replaceAll("\\b"+name+"\\b",reuseName(name));
            if(name.equals(table))table=reuseName(name);
        }
        return source.toBuilder().sql(sql).table(table).build();
    }
    static final boolean PRODUCTION_SQL_PARSER_CACHE=Boolean.getBoolean("pms.sync.mysql.fullChain.productionSqlParserCache");
    static final boolean COMPRESS_BINLOG=Boolean.getBoolean("pms.sync.mysql.fullChain.compressBinlog");
    static final boolean COMPRESS_EVIDENCE_TABLES=Boolean.getBoolean("pms.sync.mysql.fullChain.compressEvidenceTables");
    static final boolean COMPRESS_BINDING_TABLE=Boolean.getBoolean("pms.sync.mysql.fullChain.compressBindingTable");
    static final int COMPRESSED_PAGE_KIB=Integer.getInteger("pms.sync.mysql.fullChain.compressedPageKiB",8);
    static { if(COMPRESSED_PAGE_KIB!=4 && COMPRESSED_PAGE_KIB!=8)throw new IllegalArgumentException("Only measured 4/8 KiB compressed pages are supported by this test profile"); }
    static final String URL="jdbc:mysql://127.0.0.1:23316/"+DB+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true"
            +(COMPRESS_BINLOG?"&sessionVariables=binlog_transaction_compression=ON,binlog_transaction_compression_level_zstd=1":"");
    static volatile String activePhase="INITIALIZING";
    static final boolean INITIAL_ONLY=Boolean.getBoolean("pms.sync.mysql.fullChain.initialOnly");
    void reviewInitialPerformance(Map<String,Object> nativeResult) throws Exception {
        String configured=System.getProperty("pms.sync.mysql.fullChain.initialReviewFile","");
        if(configured.isBlank())throw new IllegalArgumentException("An explicit fresh initial review file is required");
        Path review=Path.of(configured);
        if(!review.isAbsolute())throw new IllegalArgumentException("Absolute review file required");
        print("FULL_CHAIN_INITIAL_REVIEW_REQUIRED",Map.of("nativeResult",nativeResult,"reviewFile",review.toString(),
                "reason","Compare equivalent task stages and data; elapsed time alone does not authorize stopping"));
        while(!Files.exists(review))Thread.sleep(250);
        if(InitialMigrationReview.decision(Files.readString(review))==InitialMigrationReview.Decision.STOP) {
            print("FULL_CHAIN_PERFORMANCE_STOP",Map.of("reason","Operator confirmed significant regression on equivalent work", "nativeResult",nativeResult));
            throw new IllegalStateException("Confirmed performance regression; remaining correctness checks skipped");
        }
    }
    static final int ROWS=Integer.getInteger("pms.sync.mysql.fullChain.rows",1000);
    static final int CONTRACTS=Integer.getInteger("pms.sync.mysql.fullChain.contracts",Math.max(10,Math.min(1000,ROWS/4000)));
    static final int SHIPMENT_CONTRACTS=Integer.getInteger("pms.sync.mysql.fullChain.shipmentContracts",CONTRACTS);
    static final int PACKAGES=Integer.getInteger("pms.sync.mysql.fullChain.packages",Math.max(100,Math.min(40000,ROWS/100)));
    static final List<String> KEYS=List.of("shipment-complete");
    JdbcTemplate jdbc; Path root; AnnotationConfigApplicationContext context;
    SyncTaskService tasks; SyncRunService runs; Map<String,Long> ids=new LinkedHashMap<>();
    Map<String,Object> metrics=new LinkedHashMap<>();
    long suiteStart;
    void print(String tag,Object value){System.out.println(tag+"="+JsonUtils.toJsonString(value));System.out.flush();}
    long count(String table){assertTrue(table.matches("[a-z_]+"));return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=1",Long.class);}
    Map<String,Long> counts(){var m=new LinkedHashMap<String,Long>();for(String t:List.of("com_contract","com_shipment_contract_reference","com_shipment_package","ast_device","ast_device_shipment","int_sync_binding","plt_migration_source_record","plt_external_key_mapping","plt_migration_issue","plt_outbox_event"))m.put(t,count(t));return m;}
    @BeforeEach void setup() throws Exception {
        suiteStart=System.nanoTime();assertTrue(ROWS>=1000 && ROWS<=4_000_000);
        if(INITIAL_ONLY) {
            assertEquals(4_000_000,ROWS);assertEquals(81522,CONTRACTS);
            assertEquals(120639,SHIPMENT_CONTRACTS);assertEquals(400000,PACKAGES);
            assertFalse(REUSE_SOURCE_DB.isEmpty(),"Retain existing source; do not regenerate it");
            String reviewFile=System.getProperty("pms.sync.mysql.fullChain.initialReviewFile","");
            assertFalse(reviewFile.isBlank());assertTrue(Path.of(reviewFile).isAbsolute());
            assertFalse(Files.exists(Path.of(reviewFile)),"Stale review decision cannot authorize a new run");
        }
        assertTrue(CONTRACTS>0 && SHIPMENT_CONTRACTS>=CONTRACTS && SHIPMENT_CONTRACTS<=1_000_000 && PACKAGES>0 && PACKAGES<=1_000_000);
        root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("sql/migrations")))root=root.getParent();
        var ds=new DriverManagerDataSource(URL,"root","");jdbc=new JdbcTemplate(ds);
        assertEquals(DB,jdbc.queryForObject("SELECT DATABASE()",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class),"Must use fresh namespaced DB; never cleans data");
        long begin=System.nanoTime();
        for(String file:List.of("docs/generated/sync-full-chain-20261009/full-chain-schema.sql","sql/migrations/V238__integration_spring_batch_repository.sql","sql/migrations/V402__sync_hot_path_business_key_indexes.sql"))
            new ResourceDatabasePopulator(new FileSystemResource(root.resolve(file))).execute(ds);
        if(COMPRESS_EVIDENCE_TABLES || COMPRESS_BINDING_TABLE) {
            assertEquals(1,jdbc.queryForObject("SELECT @@innodb_file_per_table",Integer.class));
            assertEquals(16384,jdbc.queryForObject("SELECT @@innodb_page_size",Integer.class));
            // Isolated storage experiment only; defaults preserve the prior profile.
            // Binding compression is independently opt-in so its CPU/space cost is measured.
            var compressedTables=new ArrayList<String>();
            if(COMPRESS_EVIDENCE_TABLES)compressedTables.addAll(List.of("plt_migration_source_record","plt_external_key_mapping"));
            if(COMPRESS_BINDING_TABLE)compressedTables.add("int_sync_binding");
            for(String table:compressedTables) {
                jdbc.execute("ALTER TABLE "+table+" ROW_FORMAT=COMPRESSED KEY_BLOCK_SIZE="+COMPRESSED_PAGE_KIB);
                assertEquals("Compressed",jdbc.queryForObject("SELECT ROW_FORMAT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",String.class,table));
            }
        }
        verifyStorageSession(jdbc,"fixture");
        metrics.put("schemaSeconds",(System.nanoTime()-begin)/1e9);
        begin=System.nanoTime();
        if(REUSE_SOURCE_DB.isEmpty())sourceFixture();else reuseSourceFixture();
        metrics.put("sourceAndReferenceSeedSeconds",(System.nanoTime()-begin)/1e9);
        metrics.put("sourceFixtureMode",REUSE_SOURCE_DB.isEmpty()?"GENERATED":"REUSED_WITHOUT_REGENERATION");
        if(!REUSE_SOURCE_DB.isEmpty())metrics.put("reusedSourceDatabase",REUSE_SOURCE_DB);
        TenantContextHolder.setTenantId(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(1L).setTenantId(1L),null,List.of()));
        begin=System.nanoTime();configureSqlParserProfile("beforeContext");metrics.put("sqlParserProfileSeconds",(System.nanoTime()-begin)/1e9);
        begin=System.nanoTime();context=new AnnotationConfigApplicationContext(Config.class);metrics.put("applicationContextSeconds",(System.nanoTime()-begin)/1e9);
        configureSqlParserProfile("afterContext");
        verifyStorageSession(new JdbcTemplate(context.getBean(DataSource.class)),"businessPool");
        begin=System.nanoTime();verifyOrganizationBatchSql();verifyMappingPageSql();metrics.put("organizationBatchVerificationSeconds",(System.nanoTime()-begin)/1e9);
        begin=System.nanoTime();verifyJdbcEvidenceBulkSql();metrics.put("jdbcEvidenceVerificationSeconds",(System.nanoTime()-begin)/1e9);
        tasks=context.getBean(SyncTaskService.class);runs=context.getBean(SyncRunService.class);
        jdbc.update("INSERT INTO int_sync_connection(id,tenant_id,data_source_id,name) VALUES(1,1,1,'isolated complete shipment source')");
        var definitions=new LinkedHashMap<String,SyncDefinition>();
        new GenericSyncTemplates().list(1L).forEach(t->definitions.put(t.key(),t.definition()));
        var shipment=definitions.get("shipment-contract").sources().getFirst();
        var reference=shipment.targets().getLast();
        var link=new GenericTargetStep("contractLink","com_shipment_contract_reference",List.of("source_system","source_record_key"),
            "UPDATE_ONLY","IGNORE",List.of("contract_id"),"company_code",null,null,
            reference.mappings().stream().filter(m->Set.of("source_system","source_record_key","company_code","contract_no").contains(m.target())).toList(),
            List.of(new GenericTargetStep.Lookup("com_contract",Map.of("company_code","company_code","contract_no","contract_no"),Map.of("contract_id","id"),"SKIP_STEP")));
        var sources=List.of(shipment,definitions.get("contract-master").sources().getFirst(),
            shipment.toBuilder().object("SHIPMENT_CONTRACT_LINK").sourceObject("fb_contract_master_link").targets(List.of(link)).primaryTarget("contractLink").build(),
            definitions.get("shipment-package").sources().getFirst(),definitions.get("shipment-barcode").sources().getFirst());
        var definition=definitions.get("shipment-contract").toBuilder().taskKey("shipment-complete").sources(sources.stream().map(ShipmentFullChainMySqlTest::reuseDefinition).toList())
            .evidencePolicy("REUSE_UNCHANGED").chunkSize(500).fetchSize(2000).build();
        ids.put("shipment-complete",tasks.save(new SyncTaskService.Save(null,null,"Full shipment chain",definition)));
        assertEquals(0,count("com_contract"));assertEquals(0,count("com_shipment_contract_reference"));
        print("FULL_CHAIN_DEFINITION",definition);
        print("FULL_CHAIN_PREPARATION",Map.of("database",DB,"sourceData","GENERATED_NOT_PRODUCTION_EXPORT","deviceRows",ROWS,"contracts",CONTRACTS,"shipmentContracts",SHIPMENT_CONTRACTS,"packages",PACKAGES,"timing",metrics));
    }
    @AfterEach void close(){if(context!=null)context.close();TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    void configureSqlParserProfile(String role) throws Exception {
        // The plain Spring harness does not run Boot auto-configuration. Record its
        // actual parser-cache state; an explicit comparison profile loads the same
        // existing production initializer instead of inventing a faster test cache.
        var field=com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal.class.getDeclaredField("jsqlParseCache");
        field.setAccessible(true);
        Object before=field.get(null);
        if(PRODUCTION_SQL_PARSER_CACHE)
            Class.forName("cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration");
        Object after=field.get(null);
        if(PRODUCTION_SQL_PARSER_CACHE)assertNotNull(after,"Production cache initializer must take effect");
        print("FULL_CHAIN_SQL_PARSER_PROFILE",Map.of("role",role,"productionInitializerRequested",PRODUCTION_SQL_PARSER_CACHE,
                "before",before==null?"NONE":before.getClass().getName(),
                "after",after==null?"NONE":after.getClass().getName()));
    }
    void verifyOrganizationBatchSql() {
        var connection=new JdbcTemplate(context.getBean(DataSource.class));
        var mapper=context.getBean(cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceOrganizationMapper.class);
        var transaction=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status->{
            for(int n=1;n<=5;n++)connection.update("INSERT INTO ast_device(id,tenant_id,sn,status,source_system,deleted,company_id,company_name,department_id,department_code,department_name,organization_source) VALUES(?,? ,?,'ACTIVE','TEST',?,99,'OLD',99,'OLD','OLD','CONTRACT')",
                    -100L-n,n==4?2L:1L,"ROLLBACK-ORG-"+n,n==5);
            var rows=List.of(
                    new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationUpdate(1L,-101L,null,null,null,null,null,"UNRESOLVED"),
                    new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationUpdate(1L,-102L,1L,"公司甲",2L,"x|y","z","CONTRACT"),
                    new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationUpdate(1L,-103L,3L,"公司乙",4L,"x","y|z","PROJECT"),
                    new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationUpdate(1L,-104L,1L,"BLOCKED",2L,"BLOCKED","BLOCKED","CONTRACT"),
                    new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationUpdate(1L,-105L,1L,"BLOCKED",2L,"BLOCKED","BLOCKED","CONTRACT"));
            assertEquals(3,mapper.updateOrganizationRows(1L,rows));
            for(int n=1;n<=3;n++) {
                var expected=rows.get(n-1);
                var actual=connection.queryForMap("SELECT company_id,company_name,department_id,department_code,department_name,organization_source,organization_updated_at FROM ast_device WHERE id=?",expected.deviceId());
                assertEquals(expected.companyId(),actual.get("company_id"));assertEquals(expected.companyName(),actual.get("company_name"));
                assertEquals(expected.departmentId(),actual.get("department_id"));assertEquals(expected.departmentCode(),actual.get("department_code"));
                assertEquals(expected.departmentName(),actual.get("department_name"));assertEquals(expected.source(),actual.get("organization_source"));
                assertNotNull(actual.get("organization_updated_at"));
            }
            for(long id:List.of(-104L,-105L)) {
                assertEquals(99L,connection.queryForObject("SELECT company_id FROM ast_device WHERE id=?",Long.class,id));
                assertNull(connection.queryForObject("SELECT organization_updated_at FROM ast_device WHERE id=?",java.sql.Timestamp.class,id));
            }
            assertEquals(0,mapper.updateOrganizationRows(1L,List.of()));
            assertEquals(0,mapper.updateOrganizationRows(1L,List.of(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceOrganizationUpdate(1L,-999L,null,null,null,null,null,"UNRESOLVED"))));
            status.setRollbackOnly();
        });
        assertEquals(0L,connection.queryForObject("SELECT COUNT(*) FROM ast_device",Long.class));
        print("FULL_CHAIN_ORGANIZATION_BATCH_SQL",Map.of("status","PASS","checks","MIXED_FACTS_NULL_CLEAR_TENANT_DELETED_EMPTY_MISSING_ROLLBACK"));
    }
    void verifyMappingPageSql() {
        var jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        var mapper=context.getBean(cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.ExternalKeyMappingMapper.class);
        var transaction=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status->{
            for(int n=1;n<=4;n++)jdbc.update("INSERT INTO plt_external_key_mapping(id,tenant_id,batch_id,source_record_id,result_type,creator,updater,create_time,update_time,deleted) VALUES(?,?,?,?,'RETAINED','test','test',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),?)",
                    -200L-n,n==3?2L:1L,n==2?-702L:-701L,-900L-n,n==4);
            var ids=List.of(-901L,-902L,-903L,-904L);
            var rows=mapper.selectSourcePage(new cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourcePageQuery(1L,-701L,ids));
            assertEquals(1,rows.size());assertEquals(-201L,rows.getFirst().getId());
            assertEquals(1,mapper.selectSourcePage(new cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourcePageQuery(1L,-701L,List.of(-901L,-901L))).size());
            for(List<Long> empty:java.util.Arrays.asList(List.<Long>of(),null,List.of(-999L)))
                assertTrue(mapper.selectSourcePage(new cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourcePageQuery(1L,-701L,empty)).isEmpty());
            assertTrue(mapper.selectSourcePage(new cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourcePageQuery(2L,-701L,ids)).isEmpty());
            try {
                TenantContextHolder.setTenantId(2L);
                var second=mapper.selectSourcePage(new cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourcePageQuery(2L,-701L,ids));
                assertEquals(1,second.size());assertEquals(-203L,second.getFirst().getId());
            } finally { TenantContextHolder.setTenantId(1L); }
            status.setRollbackOnly();
        });
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM plt_external_key_mapping",Long.class));
        print("FULL_CHAIN_MAPPING_PAGE_SQL",Map.of("status","PASS","checks","TENANT_BATCH_DELETED_EMPTY_NULL_DUPLICATE_MISSING_ROLLBACK"));
    }
    void verifyJdbcEvidenceBulkSql() {
        var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        var writer = context.getBean(cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationEvidenceJdbcWriter.class);
        var sources = context.getBean(cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.class);
        var mappings = context.getBean(cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.ExternalKeyMappingMapper.class);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var time = java.time.LocalDateTime.of(2026, 10, 10, 11, 0, 0, 123456000);
        var source = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationSourceRecordDO();
        source.setId(-4001L); source.setTenantId(1L); source.setBatchId(-4002L);
        source.setSourceSystem("ERP"); source.setSourceTable("bulk_equivalence");
        source.setSourceRecordKey("unicode-中文-'\\-key"); source.setSourceBusinessKey(null);
        source.setSourcePayload("{\"中文\":\"a'\\\\b\",\"missing\":null,\"number\":1.25}");
        source.setSourceChecksum("a".repeat(64)); source.setExtractedAt(time);
        source.setCreator("test"); source.setUpdater("test"); source.setCreateTime(time); source.setUpdateTime(time);
        var mapping = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.ExternalKeyMappingDO();
        mapping.setId(-4003L); mapping.setTenantId(1L); mapping.setBatchId(-4002L); mapping.setSourceRecordId(-4001L);
        mapping.setResultType("RETAINED"); mapping.setCreator("test"); mapping.setUpdater("test");
        mapping.setCreateTime(time); mapping.setUpdateTime(time);
        var mapped = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.ExternalKeyMappingDO();
        mapped.setId(-4004L); mapped.setTenantId(1L); mapped.setBatchId(-4002L); mapped.setSourceRecordId(-4001L);
        mapped.setResultType("MAPPED"); mapped.setTargetContext("AST"); mapped.setTargetObjectType("DEVICE");
        mapped.setTargetTable("ast_device"); mapped.setTargetId(4005L); mapped.setTargetRole("PRIMARY"); mapped.setTargetSequence(0);
        mapped.setCreator("test"); mapped.setUpdater("test"); mapped.setCreateTime(time); mapped.setUpdateTime(time);
        var expected = transaction.execute(status -> {
            sources.insertRows(List.of(source)); mappings.insertRows(List.of(mapping, mapped));
            var facts = List.of(jdbc.queryForMap("SELECT * FROM plt_migration_source_record WHERE id=-4001"),
                    jdbc.queryForMap("SELECT * FROM plt_external_key_mapping WHERE id=-4003"),
                    jdbc.queryForMap("SELECT * FROM plt_external_key_mapping WHERE id=-4004"));
            status.setRollbackOnly(); return facts;
        });
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_migration_source_record", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_external_key_mapping", Long.class));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> writer.insertSources(List.of(source)));
        var actual = transaction.execute(status -> {
            assertEquals(1, writer.insertSources(List.of(source)));
            assertEquals(2, writer.insertMappings(List.of(mapping, mapped)));
            var facts = List.of(jdbc.queryForMap("SELECT * FROM plt_migration_source_record WHERE id=-4001"),
                    jdbc.queryForMap("SELECT * FROM plt_external_key_mapping WHERE id=-4003"),
                    jdbc.queryForMap("SELECT * FROM plt_external_key_mapping WHERE id=-4004"));
            status.setRollbackOnly(); return facts;
        });
        assertEquals(expected, actual, "JDBC must preserve every persisted column including JSON, null, audit and time");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> transaction.executeWithoutResult(status -> {
            writer.insertSources(List.of(source)); writer.insertMappings(List.of(mapping));
            writer.insertMappings(List.of(mapping));
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_migration_source_record", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_external_key_mapping", Long.class));
        transaction.executeWithoutResult(status -> {
            source.setTenantId(2L);
            assertThrows(IllegalArgumentException.class, () -> writer.insertSources(List.of(source)));
            source.setTenantId(1L);
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_migration_source_record", Long.class));
            status.setRollbackOnly();
        });
        try {
            TenantContextHolder.setTenantId(2L); source.setTenantId(2L);
            transaction.executeWithoutResult(status -> {
                writer.insertSources(List.of(source));
                assertEquals(2L, jdbc.queryForObject("SELECT tenant_id FROM plt_migration_source_record WHERE id=-4001", Long.class));
                status.setRollbackOnly();
            });
        } finally { TenantContextHolder.setTenantId(1L); source.setTenantId(1L); }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_migration_source_record", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM plt_external_key_mapping", Long.class));
        print("FULL_CHAIN_JDBC_EVIDENCE_SQL", Map.of("status", "PASS", "checks",
                "MAPPER_EQUIVALENCE_JSON_UNICODE_NULL_TIME_AUDIT_MANDATORY_TX_DUPLICATE_ROLLBACK_TENANT"));
    }
    void sourceFixture() throws Exception {
        jdbc.execute("CREATE TABLE sync_test_digits(n INT PRIMARY KEY) ENGINE=InnoDB");
        for(int n=0;n<10;n++)jdbc.update("INSERT INTO sync_test_digits VALUES(?)",n);
        new ResourceDatabasePopulator(new FileSystemResource(root.resolve("docs/generated/sync-full-chain-20261009/source-schema.sql"))).execute(jdbc.getDataSource());
        verifySourceSchema();
        for(int n=1;n<=10;n++)jdbc.update("INSERT INTO system_company(id,tenant_id,code,name,status) VALUES(?,1,?,?,0)",n,"COMPANY-"+n,"Generated company "+n);
        for(int n=1;n<=100;n++)jdbc.update("INSERT INTO system_dept(id,tenant_id,code,name,status) VALUES(?,1,?,?,0)",n,"DEPT-"+n,"Generated department "+n);
        String series="SELECT a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+100000*f.n AS n FROM sync_test_digits a CROSS JOIN sync_test_digits b CROSS JOIN sync_test_digits c CROSS JOIN sync_test_digits d CROSS JOIN sync_test_digits e CROSS JOIN sync_test_digits f";
        String masterSeries="SELECT n, n+1 AS k FROM ("+series+") numbered WHERE n<"+CONTRACTS;
        String shipmentSeries="SELECT n,MOD(n,"+CONTRACTS+")+1 AS k FROM ("+series+") numbered WHERE n<"+SHIPMENT_CONTRACTS;
        // Bulk seed generated sources/prerequisites; these statements are measured separately
        // and do not insert any of the migration's target contract/package/device records.
        jdbc.execute("INSERT INTO fb_contract(contract_id,contract_code,project_name,marketCode,marketName,office_code,systemName,contract_type,customer_name,warranty,systemId,remark) SELECT n+1,CONCAT('CONTRACT-',k),CONCAT('Generated project ',k),'MARKET','Market name',CONCAT('DEPT-',MOD(k-1,100)+1),'System name',1,'Generated buyer',1,1,'generated fixture' FROM ("+shipmentSeries+") seed");
        jdbc.execute("INSERT INTO pm_order_data_from_erp(id,contractNo,compCode,customerCode,customerName) SELECT k,CONCAT('CONTRACT-',k),CONCAT('COMPANY-',MOD(k-1,10)+1),CONCAT('BUYER-',k),CONCAT('Generated buyer ',k) FROM ("+masterSeries+") seed");
        jdbc.execute("INSERT INTO sms_ofst_contract_head_sap(id,contract_num,currency_name,contract_money_amount,delivered_money_amount,collected_money_amount,contract_create_date,project_name,projectCode,officeCode,office_name,update_time) SELECT k,CONCAT('CONTRACT-',k),'CNY',100000,0,0,'2026-10-01',CONCAT('Generated project ',k),CONCAT('PROJECT-',k),CONCAT('DEPT-',MOD(k-1,100)+1),CONCAT('Department ',MOD(k-1,100)+1),'2026-10-01' FROM ("+masterSeries+") seed");
        jdbc.execute("INSERT INTO com_sales_order(id,tenant_id,source_system,source_record_key,source_version,company_code,order_type,order_no,status,creator,updater) SELECT k,1,'ERP',CONCAT('ORDER-',k),'1',CONCAT('COMPANY-',MOD(k-1,10)+1),'SALE',CONCAT('ORDER-',k),'ACTIVE','test','test' FROM ("+masterSeries+") seed");
        jdbc.execute("INSERT INTO com_sales_order_line(id,tenant_id,order_id,source_system,source_record_key,source_version,company_code,order_type,order_no,line_no,product_code,unit_code,unit_scale,quantity_status,status,creator,updater) SELECT k,1,k,'ERP',CONCAT('LINE-',k),'1',CONCAT('COMPANY-',MOD(k-1,10)+1),'SALE',CONCAT('ORDER-',k),'1','PRODUCT-A','EA',0,'PENDING','ACTIVE','test','test' FROM ("+masterSeries+") seed");
        jdbc.update("INSERT INTO fb_shipment(packlist_id,con_id,packdate,warrantyStartTime,warrantyEndTime,receiveName,emsNum,emsCompany) SELECT n+1,MOD(n,?)+1,'2026-10-01','2026-10-01','2028-10-01','Generated receiver',CONCAT('EXP-',n+1),'Generated carrier' FROM ("+series+") seq WHERE n<?",SHIPMENT_CONTRACTS,PACKAGES);
        for(int offset=0;offset<ROWS;offset+=100000){int take=Math.min(100000,ROWS-offset);
            jdbc.update("INSERT INTO fb_shipment_barcode(id,barcode,item,com_barcode,pack_id,warrantyStartDate,warrantyMonth,updateTime,orderNumber,lineNum,isRMA,uuid) SELECT ?+n+1,CONCAT('CHAIN-SN-',?+n+1),'PRODUCT-A',CONCAT('INTERNAL-',?+n+1),MOD(?+n,?)+1,'2026-10-01',24,'2026-10-01',CONCAT('ORDER-',MOD(MOD(MOD(?+n,?),?),?)+1),'1',0,CONCAT('UUID-',?+n+1) FROM ("+series+") seq WHERE n<?",offset,offset,offset,offset,PACKAGES,offset,PACKAGES,SHIPMENT_CONTRACTS,CONTRACTS,offset,take);
            print("FULL_CHAIN_SEED_PROGRESS",Map.of("rows",offset+take));}
    }
    /** Reuse the completed generated fixture without changing or regenerating its source rows.
     * The large barcode/packing tables remain in their original database. A uniquely named source-catalog
     * phase gate exposes their already-recorded delta only in the fourth acceptance phase.
     * Only the small contract source is copied so its metadata-change test remains real DML.
     */
    void reuseSourceFixture() throws Exception {
        verifySourceSchema(REUSE_SOURCE_DB);
        int delta=Math.min(1000,ROWS);
        Map<String,Long> expected=new LinkedHashMap<>();
        expected.put("fb_contract",(long)SHIPMENT_CONTRACTS);
        expected.put("pm_order_data_from_erp",(long)CONTRACTS);
        expected.put("sms_ofst_contract_head_sap",(long)CONTRACTS);
        expected.put("fb_shipment",(long)PACKAGES+1);
        expected.put("fb_shipment_barcode",(long)ROWS+delta);
        expected.put("fb_shipment_barcode_relation",0L);
        for(var entry:expected.entrySet()) {
            assertEquals("BASE TABLE",jdbc.queryForObject("SELECT TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?",String.class,REUSE_SOURCE_DB,entry.getKey()));
            assertEquals(entry.getValue(),jdbc.queryForObject("SELECT COUNT(*) FROM `"+REUSE_SOURCE_DB+"`.`"+entry.getKey()+"`",Long.class),"Reusable source count: "+entry.getKey());
        }
        assertEquals((long)delta,jdbc.queryForObject("SELECT COUNT(*) FROM `"+REUSE_SOURCE_DB+"`.fb_shipment_barcode WHERE id>? AND id<=? AND warrantyMonth=36 AND pack_id=?",Long.class,ROWS,(long)ROWS+delta,PACKAGES+1));
        assertEquals((long)ROWS,jdbc.queryForObject("SELECT COUNT(*) FROM `"+REUSE_SOURCE_DB+"`.fb_shipment_barcode WHERE id<=? AND warrantyMonth=24",Long.class,ROWS));
        for(String table:List.of("system_company","system_dept","com_sales_order","com_sales_order_line")) {
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM `"+table+"`",Long.class));
            jdbc.execute("INSERT INTO `"+table+"` SELECT * FROM `"+REUSE_SOURCE_DB+"`.`"+table+"`");
        }
        assertEquals(10L,count("system_company"));assertEquals(100L,count("system_dept"));
        assertEquals((long)CONTRACTS,count("com_sales_order"));assertEquals((long)CONTRACTS,count("com_sales_order_line"));
        // Helpers live in the source catalog so the real reader retains its strict
        // same-catalog transactional-table verification. Original source rows stay frozen.
        for(String name:List.of("fb_contract","phase","fb_shipment","fb_shipment_barcode")) {
            assertTrue(reuseName(name).length()<=64);
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?",Long.class,REUSE_SOURCE_DB,reuseName(name)),"Never overwrite existing reuse helpers");
        }
        jdbc.execute("CREATE TABLE "+reuseQualified("fb_contract")+" LIKE `"+REUSE_SOURCE_DB+"`.fb_contract");
        jdbc.execute("INSERT INTO "+reuseQualified("fb_contract")+" SELECT * FROM `"+REUSE_SOURCE_DB+"`.fb_contract");
        assertEquals(1,jdbc.update("UPDATE "+reuseQualified("fb_contract")+" SET remark='generated fixture' WHERE contract_id='1'"));
        jdbc.execute("CREATE TABLE "+reuseQualified("phase")+"(id TINYINT PRIMARY KEY,include_delta TINYINT NOT NULL) ENGINE=InnoDB");
        jdbc.update("INSERT INTO "+reuseQualified("phase")+" VALUES(1,0)");
        for(String table:List.of("fb_shipment","fb_shipment_barcode")) {
            String key=table.equals("fb_shipment")?"packlist_id":"id";
            int limit=table.equals("fb_shipment")?PACKAGES:ROWS;
            jdbc.execute("CREATE ALGORITHM=MERGE VIEW "+reuseQualified(table)+" AS SELECT s.* FROM `"+REUSE_SOURCE_DB+"`.`"+table+"` s JOIN "+reuseQualified("phase")+" p ON p.id=1 WHERE s.`"+key+"`<="+limit+" OR p.include_delta=1");
        }
        // These local views are only for schema/assertion queries, never reader input.
        for(String table:List.of("fb_contract","pm_order_data_from_erp","sms_ofst_contract_head_sap","fb_shipment","fb_shipment_barcode","fb_shipment_barcode_relation")) {
            String physical=Set.of("fb_contract","fb_shipment","fb_shipment_barcode").contains(table)?reuseName(table):table;
            jdbc.execute("CREATE ALGORITHM=MERGE VIEW `"+table+"` AS SELECT s.* FROM `"+REUSE_SOURCE_DB+"`.`"+physical+"` s");
        }
        verifySourceSchema();
        assertEquals((long)ROWS,jdbc.queryForObject("SELECT COUNT(*) FROM fb_shipment_barcode",Long.class));
        assertEquals((long)PACKAGES,jdbc.queryForObject("SELECT COUNT(*) FROM fb_shipment",Long.class));
        print("FULL_CHAIN_REUSED_SOURCE",Map.of("database",REUSE_SOURCE_DB,"generatedRows",0,
                "sharedSourceTables",5,"copiedContractRows",SHIPMENT_CONTRACTS,"baseRows",ROWS,"storedDeltaRows",delta));
    }
    void verifyStorageSession(JdbcTemplate connection,String role) {
        assertEquals(1,connection.queryForObject("SELECT @@SESSION.sql_log_bin",Integer.class),"Binary logging must remain enabled");
        assertEquals("ROW",connection.queryForObject("SELECT @@SESSION.binlog_format",String.class));
        assertEquals("FULL",connection.queryForObject("SELECT @@SESSION.binlog_row_image",String.class));
        assertEquals(COMPRESS_BINLOG?1:0,connection.queryForObject("SELECT @@SESSION.binlog_transaction_compression",Integer.class));
        if(COMPRESS_BINLOG)assertEquals(1,connection.queryForObject("SELECT @@SESSION.binlog_transaction_compression_level_zstd",Integer.class));
        print("FULL_CHAIN_STORAGE_PROFILE",Map.of("role",role,"binlogEnabled",true,"rowImage","FULL",
                "transactionCompression",COMPRESS_BINLOG,"evidenceTableCompression",COMPRESS_EVIDENCE_TABLES,
                "bindingTableCompression",COMPRESS_BINDING_TABLE,"compressedPageKiB",COMPRESSED_PAGE_KIB));
    }
    void verifySourceSchema() throws Exception { verifySourceSchema(DB); }
    void verifySourceSchema(String database) throws Exception {
        var manifest=JsonUtils.parseTree(Files.readString(root.resolve("docs/generated/sync-full-chain-20261009/source-schema-manifest.json")));
        var evidence=root.resolve(manifest.get("evidence").asText());
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(evidence)));
        assertEquals(manifest.get("evidenceSha256").asText(),hash,"Historical field evidence changed; regenerate source fixture");
        int checked=0;
        for(var table:manifest.get("tables")) {
            String name=table.get("name").asText();
            var actual=jdbc.queryForList("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION",database,name);
            assertEquals(table.get("columns").size(),actual.size(),name+" complete source column count");
            for(int i=0;i<actual.size();i++) {
                var expected=table.get("columns").get(i);var column=actual.get(i);
                assertEquals(expected.get("name").asText(),column.get("COLUMN_NAME"),name);
                assertEquals(expected.get("type").asText(),column.get("COLUMN_TYPE"),name+"."+expected.get("name").asText());
                assertEquals(expected.get("nullable").asText(),column.get("IS_NULLABLE"),name);
                checked++;
            }
        }
        print("FULL_CHAIN_SOURCE_SCHEMA",Map.of("tables",manifest.get("tables").size(),"columnsVerified",checked,"evidenceSha256",hash,"scope","HISTORICAL_FIELDS_NOT_LIVE_DDL_EXPORT","database",database));
    }
    Map<String,Object> run(String phase,String key)throws Exception {
        activePhase=phase;long task=ids.get(key),begin=System.nanoTime();String wallStart=Instant.now().toString();
        long id=runs.start(new SyncRunService.Start(task,tasks.required(task).getVersion(),UUID.randomUUID().toString(),false,true,false,null,false));
        long last=0;
        while(true){var run=runs.required(id);String status=run.getStatus();
            if(Set.of("SUCCESS","FAILED","CANCELLED","PREVIEW_READY").contains(status)){
                assertEquals("SUCCESS",status,()->"Run failed: "+JsonUtils.toJsonString(run));
                // Include completion of Spring Batch execution metadata, not only business status.
                while(jdbc.queryForObject("SELECT COUNT(*) FROM int_batch_JOB_EXECUTION WHERE STATUS IN ('STARTING','STARTED','STOPPING')",Long.class)>0)Thread.sleep(100);
                var result=new LinkedHashMap<String,Object>();result.put("phase",phase);result.put("task",key);result.put("runId",id);result.put("startedAt",wallStart);result.put("endedAt",Instant.now().toString());result.put("seconds",(System.nanoTime()-begin)/1e9);result.put("rows",run.getReadCount());result.put("summary",run.getSummaryJson());print("FULL_CHAIN_TASK",result);return result;
            }
            if(System.nanoTime()-last>30_000_000_000L){print("FULL_CHAIN_PROGRESS",Map.of("phase",phase,"task",key,"runId",id,"rows",run.getReadCount(),"seconds",(System.nanoTime()-begin)/1e9));last=System.nanoTime();}
            Thread.sleep(250);
        }
    }
    void verify(long expectedRows,int expectedPackages){
        assertEquals(CONTRACTS,count("com_contract"));assertEquals(SHIPMENT_CONTRACTS,count("com_shipment_contract_reference"));assertEquals(expectedPackages,count("com_shipment_package"));
        assertEquals(ROWS,count("ast_device"));assertEquals(expectedRows,count("ast_device_shipment"));assertEquals(0,count("plt_migration_issue"));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM com_shipment_package p LEFT JOIN com_shipment_contract_reference r ON r.id=p.shipment_contract_ref_id AND r.tenant_id=p.tenant_id LEFT JOIN com_contract c ON c.id=r.contract_id AND c.tenant_id=r.tenant_id WHERE p.tenant_id=1 AND (r.id IS NULL OR c.id IS NULL OR r.contract_no<>c.contract_no)",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device d LEFT JOIN ast_device_shipment s ON s.id=d.shipment_record_id AND s.tenant_id=d.tenant_id LEFT JOIN com_shipment_package p ON p.package_no=s.package_no COLLATE utf8mb4_0900_ai_ci AND p.tenant_id=s.tenant_id AND p.source_system='DPPMS' LEFT JOIN com_shipment_contract_reference r ON r.id=p.shipment_contract_ref_id AND r.tenant_id=p.tenant_id WHERE d.tenant_id=1 AND (s.id IS NULL OR p.id IS NULL OR r.id IS NULL OR d.sn<>s.device_sn OR d.company_id IS NULL OR d.department_id IS NULL OR d.organization_updated_at IS NULL OR d.organization_source<>'CONTRACT' OR d.company_id<>r.company_id OR d.department_id<>r.department_id OR s.order_line_id IS NULL)",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device WHERE tenant_id<>1",Long.class));
    }
    @Test void completeContractPackageDeviceShipmentMigrationAndSync()throws Exception {
        var all=new ArrayList<Map<String,Object>>();long start=System.nanoTime();
        for(String key:KEYS)all.add(run("INITIAL_MIGRATION",key));metrics.put("initialMigrationSeconds",(System.nanoTime()-start)/1e9);
        if(INITIAL_ONLY)reviewInitialPerformance(all.getLast());
        start=System.nanoTime();verify(ROWS,PACKAGES);metrics.put("initialVerificationSeconds",(System.nanoTime()-start)/1e9);var initial=counts();
        if(INITIAL_ONLY) {
            print("FULL_CHAIN_INITIAL_ONLY_RESULT",Map.of("status","PASS_INITIAL_ONLY","runs",all,"counts",initial,"timing",metrics,
                    "laterPhasesExecuted",false,"performanceAssessment","OPERATOR_REVIEWED_EQUIVALENT_WORK"));
            return;
        }
        // The first source ran BEFORE the master and link phases. Its original evidence
        // must truthfully record missing master lineage. The first replay reconciles that
        // legitimate derived-fact change; it is not categorized as redundant raw evidence.
        start=System.nanoTime();for(String key:KEYS)all.add(run("POST_LINK_RECONCILIATION_REPLAY",key));metrics.put("postLinkReconciliationReplaySeconds",(System.nanoTime()-start)/1e9);
        var reconciled=counts();
        for(String table:List.of("com_contract","com_shipment_contract_reference","com_shipment_package","ast_device","ast_device_shipment","int_sync_binding","plt_migration_issue"))assertEquals(initial.get(table),reconciled.get(table),table);
        assertEquals(initial.get("plt_migration_source_record")+SHIPMENT_CONTRACTS,reconciled.get("plt_migration_source_record"));
        start=System.nanoTime();for(String key:KEYS)all.add(run("UNCHANGED_REPLAY",key));metrics.put("unchangedReplaySeconds",(System.nanoTime()-start)/1e9);
        assertEquals(reconciled,counts(),"Stable replay must not inflate targets, bindings or PLT evidence");
        int delta=Math.min(1000,ROWS);start=System.nanoTime();
        jdbc.update("UPDATE "+(REUSE_SOURCE_DB.isEmpty()?"fb_contract":reuseQualified("fb_contract"))+" SET remark='generated changed contract metadata' WHERE contract_id=1");
        if(REUSE_SOURCE_DB.isEmpty()) {
            jdbc.update("INSERT INTO fb_shipment(packlist_id,con_id,packdate,warrantyStartTime,warrantyEndTime,receiveName,emsNum,emsCompany) VALUES(?,1,'2026-10-02','2026-10-02','2029-10-02','Generated changed receiver','DELTA-EXP','Generated carrier')",PACKAGES+1);
            jdbc.update("INSERT INTO fb_shipment_barcode(id,barcode,item,pack_id,warrantyStartDate,warrantyMonth,updateTime,orderNumber,lineNum,uuid) SELECT id+?,barcode,item,?,'2026-10-02',36,'2026-10-02','ORDER-1','1',CONCAT('DELTA-',id) FROM fb_shipment_barcode WHERE id<=?",ROWS,PACKAGES+1,delta);
        } else {
            assertEquals(1,jdbc.update("UPDATE "+reuseQualified("phase")+" SET include_delta=1 WHERE id=1 AND include_delta=0"));
            assertEquals((long)ROWS+delta,jdbc.queryForObject("SELECT COUNT(*) FROM fb_shipment_barcode",Long.class));
            assertEquals((long)PACKAGES+1,jdbc.queryForObject("SELECT COUNT(*) FROM fb_shipment",Long.class));
        }
        metrics.put("deltaSourcePreparationSeconds",(System.nanoTime()-start)/1e9);
        start=System.nanoTime();for(String key:KEYS)all.add(run("CHANGED_SOURCE_FULL_SCAN",key));metrics.put("changedSourceSyncSeconds",(System.nanoTime()-start)/1e9);
        start=System.nanoTime();verify((long)ROWS+delta,PACKAGES+1);
        assertEquals(ROWS,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment WHERE tenant_id=1 AND warranty_months=24",Long.class));
        assertEquals(delta,jdbc.queryForObject("SELECT COUNT(*) FROM ast_device_shipment WHERE tenant_id=1 AND warranty_months=36",Long.class));
        metrics.put("finalVerificationSeconds",(System.nanoTime()-start)/1e9);metrics.put("suiteElapsedSeconds",(System.nanoTime()-suiteStart)/1e9);
        verifyNoInteractions(context.getBean(ProjectDeviceOrganizationApi.class));
        print("FULL_CHAIN_RESULT",Map.of("status","PASS","rows",ROWS,"contracts",CONTRACTS,"shipmentContracts",SHIPMENT_CONTRACTS,"packages",PACKAGES,"deltaShipments",delta,"timing",metrics,"tasks",all,"counts",counts(),"included","SPRING_BATCH_REDIS_SOURCE_SQL_DEVICE_SHIPMENT_CONTRACT_ORGANIZATION_BINDING_PLT"));
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    @Import({cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationEvidenceJdbcWriter.class,SyncRunService.class,SyncTaskService.class,SyncConnectionService.class,SyncDefinitionValidator.class,SyncFieldMapper.class,
        GenericSyncEngine.class,GenericTargetCatalog.class,GenericSyncJdbcStore.class,SyncBatchConfiguration.class,SyncBatchLauncher.class,
        PlatformCommandExecutionApiImpl.class,PlatformTransactionalOutboxWriter.class,PlatformMigrationEvidenceApiImpl.class,PlatformMigrationEvidenceTransactionExecutor.class,
        DeviceOrganizationSyncListener.class,DeviceOrganizationProjectionService.class,DeviceOrganizationService.class})
    @MapperScan({"cn.iocoder.yudao.module.pms.integration.dal.mysql.sync","cn.iocoder.yudao.module.pms.platform.dal.mysql.migration","cn.iocoder.yudao.module.pms.platform.dal.mysql.command","cn.iocoder.yudao.module.pms.asset.dal.mysql.device","cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract"})
    static class Config {
        @Bean cn.hutool.extra.spring.SpringUtil springUtil(){return new cn.hutool.extra.spring.SpringUtil();}
        @Bean(destroyMethod="close") DataSource dataSource()throws Exception {
            var d=new com.alibaba.druid.pool.DruidDataSource();d.setUrl(URL);d.setUsername("root");d.setPassword("");
            d.setInitialSize(1);d.setMinIdle(1);d.setMaxActive(20);d.setMaxWait(60000);
            d.setTimeBetweenEvictionRunsMillis(60000);d.setMinEvictableIdleTimeMillis(600000);d.setMaxEvictableIdleTimeMillis(1800000);
            d.setValidationQuery("SELECT 1 FROM DUAL");d.setTestWhileIdle(true);d.setTestOnBorrow(false);d.setTestOnReturn(false);
            d.setPoolPreparedStatements(true);d.setMaxPoolPreparedStatementPerConnectionSize(20);d.init();return d;
        }
        @Bean PlatformTransactionManager transactionManager(DataSource d){return new DataSourceTransactionManager(d);}
        @Bean SyncEvidenceService evidence(PlatformMigrationEvidenceApi api){return new SyncEvidenceService(api);}
        @Bean ExternalDataSourceApi sources(){
            var registry=mock(cn.iocoder.yudao.module.infra.service.db.DataSourceConfigService.class);
            var c=new cn.iocoder.yudao.module.infra.dal.dataobject.db.DataSourceConfigDO();
            c.setId(1L);c.setName("generated complete fixture");c.setUrl(REUSE_SOURCE_DB.isEmpty()?URL:URL.replace("/"+DB+"?","/"+REUSE_SOURCE_DB+"?"));c.setUsername("root");c.setPassword("");
            when(registry.getDataSourceConfig(1L)).thenReturn(c);
            return new cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApiImpl(registry);
        }
        @Bean SpringJdbcStreamingReader streamingReader(ExternalDataSourceApi sources){return new SpringJdbcStreamingReader(Optional.of(sources)){
            @Override public StreamResult stream(SyncDefinition d,java.time.LocalDateTime lower,boolean full,SyncStreamingState resume,
                    java.util.function.Consumer<StreamChunk> consumer)throws java.sql.SQLException {
                long[] began={System.nanoTime()},rows={0};
                return super.stream(d,lower,full,resume,chunk->{
                    consumer.accept(chunk);rows[0]+=chunk.rows().size();
                    if(chunk.sourceComplete()){
                        System.out.println("FULL_CHAIN_SOURCE="+JsonUtils.toJsonString(Map.of("phase",activePhase,"sourceIndex",chunk.sourceIndex(),"object",chunk.object(),"sourceObject",chunk.sourceObject(),"rows",rows[0],"seconds",(System.nanoTime()-began[0])/1e9,"endedAt",Instant.now().toString())));
                        System.out.flush();began[0]=System.nanoTime();rows[0]=0;
                    }
                });
            }
        };}
        @Bean MysqlSyncReader reader(ExternalDataSourceApi sources){return new MysqlSyncReader(sources);}
        @Bean(destroyMethod="shutdown") RedissonClient redisson(){var c=new org.redisson.config.Config();c.useSingleServer().setAddress("redis://127.0.0.1:23379");return org.redisson.Redisson.create(c);}
        @Bean ProjectDeviceOrganizationApi projects(){return mock(ProjectDeviceOrganizationApi.class);}
        @Bean ContractDeviceVisibilityApi contracts(ContractMapper mapper){
            // The exercised getOrganizations method is the real service + Mapper XML.
            // Other constructor dependencies belong to interactive authorization paths not invoked by projection.
            return new ContractAccessService(mock(cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi.class),mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class),mapper,mock(cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper.class),mock(cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper.class),mock(cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectContractRelationMapper.class),mock(cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper.class),mock(cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi.class));
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource d)throws Exception {
            var f=new MybatisSqlSessionFactoryBean();f.setDataSource(d);var global=new GlobalConfig();global.setMetaObjectHandler(new DefaultDBFieldHandler());f.setGlobalConfig(global);
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);f.setConfiguration(config);
            var resolver=new PathMatchingResourcePatternResolver();var resources=new ArrayList<org.springframework.core.io.Resource>();
            for(String folder:List.of("sync","migration","command","device","contract"))resources.addAll(List.of(resolver.getResources("classpath*:mapper/"+folder+"/*.xml")));
            f.setMapperLocations(resources.toArray(org.springframework.core.io.Resource[]::new));
            var plugins=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
            plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(new cn.iocoder.yudao.framework.tenant.config.TenantProperties())));
            f.setPlugins(plugins);return f.getObject();
        }
    }
}
