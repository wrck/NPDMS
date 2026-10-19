package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.task.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Unmodified production XML on MySQL; all writes are connection-local TEMPORARY table writes. */
@EnabledIfEnvironmentVariable(named="BASE_PRODUCT_TEMP_MYSQL_TEST",matches="true")
class ProductBaseSyncMySqlTest {
    static final LocalDateTime AT=LocalDateTime.of(2026,10,17,12,0,0,123_000_000);
    Connection connection;
    java.net.URLClassLoader driverLoader;
    JdbcTemplate jdbc;
    SqlSessionTemplate target;
    TransactionTemplate transaction;
    List<P11CrmProductSyncTask.ProductSyncRow> crmRows=List.of();
    final P11CrmProductSyncTask crm=new P11CrmProductSyncTask();
    ProductSyncArchitectureTest.SqlTrace trace=new ProductSyncArchitectureTest.SqlTrace();

    @BeforeEach void open() throws Exception {
        driverLoader=new java.net.URLClassLoader(new java.net.URL[]{java.nio.file.Path.of(System.getenv("BASE_PRODUCT_MYSQL_DRIVER_JAR")).toUri().toURL()},getClass().getClassLoader());
        var driver=(java.sql.Driver)Class.forName("com.mysql.cj.jdbc.Driver",true,driverLoader).getConstructor().newInstance();
        var properties=new Properties();properties.setProperty("user",System.getenv("BASE_PRODUCT_DB_USER"));properties.setProperty("password",System.getenv("BASE_PRODUCT_DB_PASSWORD"));
        connection=driver.connect("jdbc:mysql://127.0.0.1:24306/npdms_reverify_20261014?useSSL=false&serverTimezone=Asia/Shanghai",properties);
        var data=new SingleConnectionDataSource(connection,true);jdbc=new JdbcTemplate(data);
        // Deliberately not CREATE TABLE LIKE: the fixture cannot copy or mutate a formal business table.
        jdbc.execute("""
            CREATE TEMPORARY TABLE ast_product_official_info(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,
              product_code VARCHAR(100) NOT NULL,product_model VARCHAR(128),product_name VARCHAR(500),product_desc TEXT,
              technical_spec TEXT,document_id BIGINT,product_category VARCHAR(100),product_type VARCHAR(100),
              list_price DECIMAL(23,10),crm_status INT,business_unit VARCHAR(100),product_line VARCHAR(100),org_id INT,
              source_created_at DATETIME(3),source_updated_at DATETIME(3),crm_state_code INT,crm_status_code INT,
              product_stage INT,end_of_sale_date DATETIME(3),end_of_support_date DATETIME(3),end_of_life_date DATETIME(3),
              last_renewal_date DATETIME(3),source_system VARCHAR(32) NOT NULL,source_key VARCHAR(128) NOT NULL,
              source_version VARCHAR(64),status VARCHAR(32) NOT NULL,version BIGINT NOT NULL DEFAULT 0,synced_at DATETIME(3),
              sync_status VARCHAR(32) NOT NULL,creator VARCHAR(64),updater VARCHAR(64),create_time DATETIME(3),
              update_time DATETIME(3),deleted BOOLEAN NOT NULL DEFAULT FALSE,
              UNIQUE(tenant_id,product_code),UNIQUE(tenant_id,source_system,source_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));
        var configuration=new Configuration();configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(trace);
        configuration.setEnvironment(new Environment("product-temporary",new SpringManagedTransactionFactory(),data));
        for(String name:List.of("P11TargetMapper","P12TargetMapper")) {
            String resource="mapper/basesync/"+name+".xml";
            try(var input=new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        target=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        transaction=new TransactionTemplate(new DataSourceTransactionManager(data));
    }
    @AfterEach void close() throws Exception {
        try {if(connection!=null)connection.close();} finally {if(driverLoader!=null)driverLoader.close();}
    }
    P11CrmProductSyncTask.ProductSyncRow row(String code,String name) {
        return P11CrmProductSyncTask.ProductSyncRow.builder().productCode(code).productName(name)
                .productModel("MODEL").crmStateCode(0).sourceCreatedAt(AT).sourceUpdatedAt(AT).build();
    }
    SyncContext<Void> context(boolean preview,int batchSize,long tenant,SyncSource source) {
        return new SyncContext<>(tenant,2,3,preview,batchSize,10000,AT,source,mock(SyncIdentityStore.class),
                mock(SyncReferences.class),"BASE_P11",target,false,true);
    }
    SyncCounts execute(boolean preview,int batchSize,long tenant) {
        var source=mock(SyncSource.class);var session=mock(SyncSource.Session.class);var sql=mock(SqlSession.class);
        when(source.open(3)).thenReturn(session);when(session.sqlSession()).thenReturn(sql);
        doAnswer(call->{
            ResultHandler<P11CrmProductSyncTask.ProductSyncRow> handler=call.getArgument(2);
            var result=new org.apache.ibatis.executor.result.DefaultResultContext<P11CrmProductSyncTask.ProductSyncRow>();
            for(var row:crmRows) {result.nextResultObject(row.toBuilder().build());handler.handleResult(result);}
            return null;
        }).when(sql).select(anyString(),any(),any(ResultHandler.class));
        try {return transaction.execute(status->crm.execute(context(preview,batchSize,tenant,source)));}
        finally {verify(session).close();}
    }
    @Test void nativeRowAliasUpsertCountsInsertUpdateAndRepeatedKeysWithOneStatementPerBatch() {
        crmRows=List.of(row("A","first"),row("B","second"));
        assertEquals(new SyncCounts(2,2,0,0,0),execute(false,128,1));assertEquals(1,trace.productWrites());
        assertEquals(new SyncCounts(2,0,2,0,0),execute(false,128,1));assertEquals(2,trace.productWrites());
        crmRows=List.of(row("A","third"),row("A","last"),row("C","new"));
        assertEquals(new SyncCounts(3,1,2,0,0),execute(false,128,1));assertEquals(3,trace.productWrites());
        assertEquals("last",jdbc.queryForObject("SELECT product_name FROM ast_product_official_info WHERE product_code='A'",String.class));
        assertEquals(3L,jdbc.queryForObject("SELECT version FROM ast_product_official_info WHERE product_code='A'",Long.class));
    }
    @Test void previewOnlyValidatesSourceAndDoesNotWriteOrPredictTargetActions() {
        crmRows=List.of(row("A","one"),row("B","two"));
        assertEquals(new SyncCounts(2,0,0,0,0),execute(true,1,1));assertEquals(0,trace.productWrites());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));
    }
    @Test void updatesPreserveNullFallbackManualFieldsAndCreationFactsWithoutVersionFiltering() {
        crmRows=List.of(row("A","first"));execute(false,128,1);
        var old=jdbc.queryForMap("SELECT id,creator,create_time FROM ast_product_official_info");
        jdbc.update("UPDATE ast_product_official_info SET document_id=77");
        crmRows=List.of(row("A","last").toBuilder().productModel(null).sourceUpdatedAt(AT.minusDays(1)).build());
        assertEquals(new SyncCounts(1,0,1,0,0),execute(false,128,1));
        assertEquals(old,jdbc.queryForMap("SELECT id,creator,create_time FROM ast_product_official_info"));
        assertEquals("MODEL",jdbc.queryForObject("SELECT product_model FROM ast_product_official_info",String.class));
        assertEquals(77L,jdbc.queryForObject("SELECT document_id FROM ast_product_official_info",Long.class));
        assertEquals(AT.minusDays(1),jdbc.queryForObject("SELECT source_updated_at FROM ast_product_official_info",LocalDateTime.class));
    }
    @Test void lateProtectedManualIdentityRollsBackEarlierBatch() {
        crmRows=List.of(row("Z","manual"));execute(false,128,1);
        jdbc.update("UPDATE ast_product_official_info SET source_system='MANUAL'");
        crmRows=List.of(row("A","would insert"),row("Z","forbidden"));
        assertThrows(RuntimeException.class,()->execute(false,1,1));
        assertEquals(3,trace.productWrites());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));
        assertEquals("MANUAL",jdbc.queryForObject("SELECT source_system FROM ast_product_official_info",String.class));
    }
    @Test void nativeCollationCannotMergeDifferentIdentityOrReviveDeletedProducts() {
        crmRows=List.of(row("A","first"));execute(false,128,1);
        crmRows=List.of(row("a","different"));assertThrows(RuntimeException.class,()->execute(false,128,1));
        jdbc.update("UPDATE ast_product_official_info SET deleted=TRUE");
        crmRows=List.of(row("A","revive"));assertThrows(RuntimeException.class,()->execute(false,128,1));
        assertEquals("first",jdbc.queryForObject("SELECT product_name FROM ast_product_official_info",String.class));
        assertTrue(jdbc.queryForObject("SELECT deleted FROM ast_product_official_info",Boolean.class));
    }
    @Test void sameCodeInAnotherTenantRemainsIndependent() {
        crmRows=List.of(row("A","tenant one"));execute(false,128,1);
        crmRows=List.of(row("A","tenant two"));assertEquals(new SyncCounts(1,1,0,0,0),execute(false,128,2));
        assertEquals("tenant one",jdbc.queryForObject("SELECT product_name FROM ast_product_official_info WHERE tenant_id=1",String.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));
    }
    @Test void supplementOnlyInsertsMissingProductsAndPreservesCrmFacts() {
        crmRows=List.of(row("A","CRM"));execute(false,128,1);
        var existing=jdbc.queryForMap("SELECT * FROM ast_product_official_info WHERE product_code='A'");
        var source=mock(SyncSource.class);var session=mock(SyncSource.Session.class);var sql=mock(SqlSession.class);
        when(source.open(3)).thenReturn(session);when(session.sqlSession()).thenReturn(sql);
        when(sql.selectList(anyString(),any())).thenAnswer(call->new ArrayList<>(List.of(
                P12SupplementProductSyncTask.ProductSyncRow.builder().productCode("A").productName("legacy").build(),
                P12SupplementProductSyncTask.ProductSyncRow.builder().productCode("B").productName("missing").build())));
        var task=new P12SupplementProductSyncTask(target.getMapper(P12SupplementProductSyncTask.TargetMapper.class));
        assertEquals(new SyncCounts(2,1,0,1,0),transaction.execute(status->task.execute(context(false,128,1,source))));
        assertEquals(existing,jdbc.queryForMap("SELECT * FROM ast_product_official_info WHERE product_code='A'"));
        assertEquals("DPPMS",jdbc.queryForObject("SELECT source_system FROM ast_product_official_info WHERE product_code='B'",String.class));
        verify(session).close();
    }
}
