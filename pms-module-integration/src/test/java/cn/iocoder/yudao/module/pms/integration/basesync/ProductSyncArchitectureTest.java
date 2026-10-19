package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.task.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncConnectionDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper;
import cn.iocoder.yudao.module.pms.integration.sync.SyncConnectionService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real product classes, production XML, configured source routing and Spring transactions on disposable H2. */
class ProductSyncArchitectureTest {
    static final LocalDateTime CREATED=LocalDateTime.of(2025,1,2,3,4,5,123_000_000);
    static final LocalDateTime UPDATED=CREATED.plusDays(2);
    JdbcTemplate crm,dppms,target;
    BaseSyncService service;
    SqlSessionTemplate session;
    SyncConnectionService connections;
    ExternalDataSourceApi external;
    ConfiguredSyncSource source;
    final List<Connection> keepers=new ArrayList<>(),openedSources=new ArrayList<>();
    final SqlTrace trace=new SqlTrace();

    @Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
    static class SqlTrace implements Interceptor {
        final Map<String,Integer> writes=new HashMap<>();
        int maxInsertBatch;
        Runnable beforeProductUpdate;
        public Object intercept(Invocation invocation) throws Throwable {
            String id=((MappedStatement)invocation.getArgs()[0]).getId();
            writes.merge(id,1,Integer::sum);
            if(invocation.getArgs()[1] instanceof SyncBatch<?,?> batch)maxInsertBatch=Math.max(maxInsertBatch,batch.rows().size());
            if(id.endsWith(".insertCrmProduct") && beforeProductUpdate!=null){
                var action=beforeProductUpdate;beforeProductUpdate=null;action.run();
            }
            return invocation.proceed();
        }
        int productWrites(){return writes.entrySet().stream().filter(e->e.getKey().contains("SyncTask$TargetMapper")).mapToInt(Map.Entry::getValue).sum();}
    }
    DriverManagerDataSource database(String mode) throws Exception {
        var data=new DriverManagerDataSource("jdbc:h2:mem:product-"+UUID.randomUUID()+";MODE="+mode
                +(mode.equals("MSSQLServer")?";CASE_INSENSITIVE_IDENTIFIERS=TRUE":""),"sa","");
        keepers.add(data.getConnection());return data;
    }
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(2L).setTenantId(1L),null,List.of()));
        var crmData=database("MSSQLServer");var dppmsData=database("MySQL");var targetData=database("MySQL");
        crm=new JdbcTemplate(crmData);dppms=new JdbcTemplate(dppmsData);target=new JdbcTemplate(targetData);
        crm.execute("""
            CREATE TABLE DPtech_v_product_info_single(itemCode NVARCHAR(500),itemModel VARCHAR(128),itemDesc VARCHAR(500),
              remark VARCHAR(4000),productCode VARCHAR(100),productSubCode VARCHAR(100),listPrice DECIMAL(23,10),
              status INT,BU VARCHAR(100),productLine VARCHAR(100),orgId INT,createTime TIMESTAMP(7),updateTime TIMESTAMP(7),
              statecode INT,statuscode INT,productStage INT,endOfSaleDate TIMESTAMP(7),endOfSupportDate TIMESTAMP(7),
              endOfLifeDate TIMESTAMP(7),lastRenewalDate TIMESTAMP(7))
            """);
        dppms.execute("CREATE TABLE fb_items(id BIGINT,item VARCHAR(100),itemname VARCHAR(128),describe_ VARCHAR(500))");
        // The approved target shape, including nullable additions and both identity constraints.
        target.execute("""
            CREATE TABLE ast_product_official_info(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,
              product_code VARCHAR(100) NOT NULL,product_model VARCHAR(128),product_name VARCHAR(500),product_desc CLOB,
              technical_spec CLOB,document_id BIGINT,product_category VARCHAR(100),product_type VARCHAR(100),
              list_price DECIMAL(23,10),crm_status INT,business_unit VARCHAR(100),product_line VARCHAR(100),org_id INT,
              source_created_at TIMESTAMP(3),source_updated_at TIMESTAMP(3),crm_state_code INT,crm_status_code INT,
              product_stage INT,end_of_sale_date TIMESTAMP(3),end_of_support_date TIMESTAMP(3),end_of_life_date TIMESTAMP(3),
              last_renewal_date TIMESTAMP(3),source_system VARCHAR(32) NOT NULL,source_key VARCHAR(128) NOT NULL,
              source_version VARCHAR(64),status VARCHAR(32) NOT NULL,version BIGINT NOT NULL DEFAULT 0,synced_at TIMESTAMP(3),
              sync_status VARCHAR(32) NOT NULL,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP(3),
              update_time TIMESTAMP(3),deleted BOOLEAN NOT NULL DEFAULT FALSE,
              UNIQUE(tenant_id,product_code),UNIQUE(tenant_id,source_system,source_key))
            """);
        Path root=Path.of("").toAbsolutePath();while(!Files.isDirectory(root.resolve("sql/migrations")))root=root.getParent();
        // Read existing metadata DDL as a disposable test fixture; no Flyway or application database.
        String metadata=Files.readString(root.resolve("sql/migrations/V483__independent_base_sync_metadata.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci","")
                .replace("CHARACTER SET utf8mb4 COLLATE utf8mb4_bin","").replace("b'0'","FALSE");
        new ResourceDatabasePopulator(new ByteArrayResource(metadata.getBytes(StandardCharsets.UTF_8))).execute(targetData);
        target.execute("CREATE TABLE int_sync_task(id BIGINT PRIMARY KEY,definition VARCHAR(128),active_run_id BIGINT)");
        target.update("INSERT INTO int_sync_task VALUES(100,'original remains untouched',900)");
        target.execute("CREATE TABLE int_sync_run(id BIGINT PRIMARY KEY,status VARCHAR(32))");
        target.update("INSERT INTO int_sync_run VALUES(900,'COMPLETED')");
        var config=new Configuration();config.setMapUnderscoreToCamelCase(true);config.addInterceptor(trace);
        config.setEnvironment(new Environment("product-target",new SpringManagedTransactionFactory(),targetData));
        for(String name:List.of("P11TargetMapper","P12TargetMapper","BaseSyncMetadataMapper")){
            String resource="mapper/basesync/"+name+".xml";
            try(var input=new ClassPathResource(resource).getInputStream()){
                // H2 treats BINARY/CHAR without length as length 1; MySQL keeps the whole value.
                // Translate only unbounded casts, not predicates or business decisions.
                String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8)
                        .replace(" AS BINARY)"," AS VARBINARY)")
                        .replace(" AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci"," AS VARCHAR)")
                        // H2 supports VALUES(column), not MySQL 8 row aliases. Production XML uses native aliases.
                        .replace("</foreach> AS incoming","</foreach>")
                        .replaceAll("incoming\\.([a-z_]+)","VALUES($1)");
                new XMLMapperBuilder(new java.io.StringReader(xml),config,resource,config.getSqlFragments()).parse();
            }
        }
        session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        connections=mock(SyncConnectionService.class);external=mock(ExternalDataSourceApi.class);
        for(long id:List.of(3L,4L)){
            var reference=new SyncConnectionDO();reference.setId(id);reference.setDataSourceId(id+4);
            when(connections.required(id)).thenReturn(reference);
        }
        when(external.openReadOnly(anyLong())).thenAnswer(call->{
            var connection=(call.<Long>getArgument(0)==7L?crmData:dppmsData).getConnection();
            connection.setReadOnly(true);connection.setAutoCommit(false);openedSources.add(connection);
            return connection;
        });
        var permissions=mock(PermissionApi.class);
        when(permissions.hasAnyPermissions(eq(2L),any(String[].class))).thenReturn(true);
        var tasks=List.<AbstractSyncTask<?,?>>of(new P11CrmProductSyncTask(),
                new P12SupplementProductSyncTask(session.getMapper(P12SupplementProductSyncTask.TargetMapper.class)));
        source=new ConfiguredSyncSource(connections,external);
        service=new BaseSyncService(tasks,source,permissions,mock(AdminUserApi.class),
                new DataSourceTransactionManager(targetData),session.getMapper(BaseSyncMetadataMapper.class),connections,
                mock(SyncReferences.class),session);
    }
    @AfterEach void close() throws Exception {
        try {
            for(var connection:openedSources)assertTrue(connection.isClosed(),"configured source must be closed");
            if(target!=null){
                assertEquals("original remains untouched",target.queryForObject("SELECT definition FROM int_sync_task WHERE id=100",String.class));
                assertEquals(900L,target.queryForObject("SELECT active_run_id FROM int_sync_task WHERE id=100",Long.class));
                assertEquals("COMPLETED",target.queryForObject("SELECT status FROM int_sync_run WHERE id=900",String.class));
            }
        } finally {
            for(var connection:openedSources)if(!connection.isClosed())connection.close();
            for(var connection:keepers)connection.close();
            TenantContextHolder.clear();SecurityContextHolder.clearContext();
        }
    }
    long register(String code,int batch,int limit){return service.register(new BaseSyncConfiguration(code,code.equals("BASE_P11")?3:4,batch,limit));}
    void crmRow(String code,String name,LocalDateTime updated){
        crm.update("INSERT INTO DPtech_v_product_info_single VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                code,"MODEL",name,"technical","CATEGORY","TYPE",new BigDecimal("1234567890123.1234567890"),7,
                "BU","LINE",12,CREATED,updated,0,8,9,CREATED.plusYears(1),CREATED.plusYears(2),CREATED.plusYears(3),CREATED.plusYears(4));
    }
    P11CrmProductSyncTask.ProductSyncRow product(String code){
        return session.getMapper(P11CrmProductSyncTask.TargetMapper.class).selectCrmTargets(
                new P11CrmProductSyncTask.TargetMapper.ProductCodesQuery(1,List.of(code),true)).getFirst();
    }
    void completed(long id,String status,long read){
        assertEquals(status,target.queryForObject("SELECT status FROM int_base_sync_run WHERE task_id=? ORDER BY id DESC LIMIT 1",String.class,id));
        assertEquals(read,target.queryForObject("SELECT read_count FROM int_base_sync_run WHERE task_id=? ORDER BY id DESC LIMIT 1",Long.class,id));
        assertNull(target.queryForObject("SELECT active_run_id FROM int_base_sync_task WHERE id=?",Long.class,id));
    }
    @Test void previewReadsSourceWithoutQueryingOrWritingTargetProducts(){
        crmRow("A","name",UPDATED);
        long id=register("BASE_P11",128,100);
        assertEquals(new SyncCounts(1,0,0,0,0),service.preview(new BaseSyncService.Run(id)));
        assertEquals(0,trace.productWrites());
        assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));
    }
    @Test void targetCollationCannotSilentlyMergeDifferentSourceIdentity(){
        target.execute("ALTER TABLE ast_product_official_info ALTER COLUMN product_code VARCHAR_IGNORECASE(100) NOT NULL");
        crmRow("A","name",UPDATED);long id=register("BASE_P11",1,100);service.execute(new BaseSyncService.Run(id));
        crm.update("UPDATE DPtech_v_product_info_single SET itemCode='a',updateTime=?",UPDATED.plusDays(1));
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertEquals("A",product("A").getProductCode());assertEquals("A",product("A").getSourceKey());
    }
    @Test void crmPreviewThenWriteMapsEveryApprovedFieldAndReplayUpdatesByKey() throws Exception {
        crmRow("A","product name",UPDATED);long id=register("BASE_P11",1,100);
        assertEquals(new SyncCounts(1,0,0,0,0),service.preview(new BaseSyncService.Run(id)));
        assertEquals(0,trace.productWrites());completed(id,"PREVIEW_READY",1);
        assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));
        assertEquals(new SyncCounts(1,1,0,0,0),service.execute(new BaseSyncService.Run(id)));
        var row=product("A");
        assertAll(()->assertEquals("A",row.getProductCode()),()->assertEquals("MODEL",row.getProductModel()),
                ()->assertEquals("product name",row.getProductName()),()->assertEquals("product name",row.getProductDesc()),
                ()->assertEquals("technical",row.getTechnicalSpec()),()->assertEquals("CATEGORY",row.getProductCategory()),
                ()->assertEquals("TYPE",row.getProductType()),()->assertEquals(new BigDecimal("1234567890123.1234567890"),row.getListPrice()),
                ()->assertEquals(7,row.getCrmStatus()),()->assertEquals("BU",row.getBusinessUnit()),()->assertEquals("LINE",row.getProductLine()),
                ()->assertEquals(12,row.getOrgId()),()->assertEquals(CREATED,row.getSourceCreatedAt()),()->assertEquals(UPDATED,row.getSourceUpdatedAt()),
                ()->assertEquals(0,row.getCrmStateCode()),()->assertEquals(8,row.getCrmStatusCode()),()->assertEquals(9,row.getProductStage()),
                ()->assertEquals(CREATED.plusYears(1),row.getEndOfSaleDate()),()->assertEquals(CREATED.plusYears(2),row.getEndOfSupportDate()),
                ()->assertEquals(CREATED.plusYears(3),row.getEndOfLifeDate()),()->assertEquals(CREATED.plusYears(4),row.getLastRenewalDate()));
        assertEquals("CRM",row.getSourceSystem());assertEquals("A",row.getSourceKey());assertEquals("ACTIVE",row.getStatus());
        assertEquals("2",row.getCreator());assertEquals(0L,row.getVersion());assertEquals(1L,row.getTenantId());
        assertEquals(new SyncCounts(1,0,1,0,0),service.execute(new BaseSyncService.Run(id)));completed(id,"COMPLETED",1);
        assertEquals(2,trace.productWrites());verify(external,times(3)).openReadOnly(7L);verify(external,never()).openReadOnly(8L);
    }
    @Test void keyedUpdatesPreserveManualAndCreateFieldsWithoutSourceVersionFiltering(){
        crmRow("A","latest source",UPDATED);long id=register("BASE_P11",1,100);
        assertEquals(1,service.execute(new BaseSyncService.Run(id)).inserted());var before=product("A");
        assertEquals("latest source",before.getProductName());
        target.update("UPDATE ast_product_official_info SET document_id=77 WHERE product_code='A'");
        crm.update("UPDATE DPtech_v_product_info_single SET itemDesc='updated',remark=NULL,updateTime=?,statecode=1 WHERE updateTime=?",UPDATED.plusDays(1),UPDATED);
        assertEquals(new SyncCounts(1,0,1,0,0),service.execute(new BaseSyncService.Run(id)));var after=product("A");
        assertEquals("updated",after.getProductName());assertEquals("technical",after.getTechnicalSpec());assertEquals("INACTIVE",after.getStatus());
        assertEquals(before.getId(),after.getId());assertEquals(before.getCreateTime(),after.getCreateTime());assertEquals(1L,after.getVersion());
        assertEquals(77L,target.queryForObject("SELECT document_id FROM ast_product_official_info",Long.class));
        crm.update("UPDATE DPtech_v_product_info_single SET updateTime=?",CREATED.minusDays(1));
        assertEquals(new SyncCounts(1,0,1,0,0),service.execute(new BaseSyncService.Run(id)));assertEquals(CREATED.minusDays(1),product("A").getSourceUpdatedAt());
    }
    @Test void sameVersionFillsMissingColumnsAndUpdatesChangedSourceFields(){
        crmRow("A","name",UPDATED);long id=register("BASE_P11",1,100);service.execute(new BaseSyncService.Run(id));
        target.update("UPDATE ast_product_official_info SET product_category=NULL,product_type=NULL,list_price=NULL,business_unit=NULL");
        assertEquals(1,service.execute(new BaseSyncService.Run(id)).updated());assertEquals("CATEGORY",product("A").getProductCategory());
        crm.update("UPDATE DPtech_v_product_info_single SET productCode='conflicting category'");
        assertEquals(new SyncCounts(1,0,1,0,0),service.execute(new BaseSyncService.Run(id)));
        assertEquals("conflicting category",product("A").getProductCategory());assertEquals(2L,product("A").getVersion());completed(id,"COMPLETED",1);
    }
    @Test void supplementFullQueryMergesDuplicatesAndOnlyInsertsMissingProducts() throws Exception {
        crmRow("A","CRM authority",UPDATED);service.execute(new BaseSyncService.Run(register("BASE_P11",1,100)));var original=product("A");
        dppms.update("INSERT INTO fb_items VALUES(1,'A','legacy','wrong'),(2,'A','other','conflict'),(3,'B','model B','name B'),(4,' B ','model B','name B')");
        long id=register("BASE_P12",1,100);
        assertEquals(new SyncCounts(4,1,0,1,0),service.preview(new BaseSyncService.Run(id)));
        assertEquals(new SyncCounts(4,1,0,1,0),service.execute(new BaseSyncService.Run(id)));
        assertEquals(original,product("A"));assertEquals("DPPMS",product("B").getSourceSystem());assertEquals("name B",product("B").getProductName());
        assertEquals(new SyncCounts(4,0,0,2,0),service.execute(new BaseSyncService.Run(id)));
        completed(id,"COMPLETED",4);verify(external,times(3)).openReadOnly(8L);
    }
    @Test void conflictingMissingSupplementDoesNotPartiallyInsertOtherProducts(){
        dppms.update("INSERT INTO fb_items VALUES(1,'A','model','first'),(2,'B','model','ok'),(3,' A ','model','conflict')");
        long id=register("BASE_P12",1,100);
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));assertEquals(0,trace.productWrites());completed(id,"FAILED",0);
    }
    @Test void invalidLastCrmBatchRollsBackEarlierInsertAndPreservesOtherTenant(){
        crmRow("A","good",UPDATED);crmRow("Z","invalid",UPDATED);
        crm.update("UPDATE DPtech_v_product_info_single SET statecode=9 WHERE itemCode='Z'");
        target.update("INSERT INTO ast_product_official_info(id,tenant_id,product_code,source_system,source_key,status,sync_status) VALUES(90,2,'A','CRM','A','ACTIVE','SUCCESS')");
        long id=register("BASE_P11",1,100);
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertEquals(1,trace.productWrites(),"the first batch really attempted INSERT before the last batch failed");
        assertEquals(List.of(90L),target.queryForList("SELECT id FROM ast_product_official_info",Long.class));completed(id,"FAILED",0);
    }
    @Test void atomicUpsertIncrementsCurrentVersionWithoutReadThenUpdateRace(){
        crmRow("B","old",UPDATED);long id=register("BASE_P11",128,100);service.execute(new BaseSyncService.Run(id));
        crmRow("A","new",UPDATED);crm.update("UPDATE DPtech_v_product_info_single SET itemDesc='new B' WHERE itemCode='B'");
        trace.beforeProductUpdate=()->target.update("UPDATE ast_product_official_info SET version=version+1 WHERE product_code='B'");
        assertEquals(new SyncCounts(2,1,1,0,0),service.execute(new BaseSyncService.Run(id)));
        assertEquals("new B",product("B").getProductName());assertEquals(2L,product("B").getVersion());
        assertEquals(2,trace.productWrites());completed(id,"COMPLETED",2);
    }
    @Test void missingApprovedTargetColumnFailsWithoutFallbackOrBusinessWrite(){
        crmRow("A","name",UPDATED);long id=register("BASE_P11",1,100);
        target.execute("ALTER TABLE ast_product_official_info DROP COLUMN product_category");
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertEquals(1,trace.productWrites());
        assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));completed(id,"FAILED",0);
    }
    @ParameterizedTest
    @ValueSource(strings={"createTime","updateTime","endOfSaleDate","endOfSupportDate","endOfLifeDate","lastRenewalDate"})
    void crmTimestampMustNotBeSilentlyRoundedByTheTarget(String column){
        crmRow("A","name",UPDATED);long id=register("BASE_P11",1,100);
        crm.update("UPDATE DPtech_v_product_info_single SET "+column+"=?",UPDATED.plusNanos(100));
        assertTrue(assertThrows(IllegalStateException.class,()->service.preview(new BaseSyncService.Run(id)))
                .getMessage().contains("同步时间不能无损写入毫秒精度"));
        assertTrue(assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)))
                .getMessage().contains("同步时间不能无损写入毫秒精度"));
        assertEquals(0,trace.productWrites());completed(id,"FAILED",0);
    }
    @Test void tenThousandProductsUseInheritedBatchUpserts(){
        crm.update("""
            INSERT INTO DPtech_v_product_info_single(itemCode,itemModel,itemDesc,createTime,updateTime,statecode)
            SELECT CONCAT('P',RIGHT(CONCAT('00000',X),5)),'MODEL','name',?,?,0 FROM SYSTEM_RANGE(1,10000)
            """,CREATED,UPDATED);
        long id=register("BASE_P11",128,10000);
        assertEquals(new SyncCounts(10000,0,0,0,0),service.preview(new BaseSyncService.Run(id)));assertEquals(0,trace.productWrites());
        assertEquals(new SyncCounts(10000,10000,0,0,0),service.execute(new BaseSyncService.Run(id)));
        assertEquals(79,trace.productWrites());assertEquals(128,trace.maxInsertBatch);
        assertEquals(10000,target.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));
        assertEquals(new SyncCounts(10000,0,10000,0,0),service.execute(new BaseSyncService.Run(id)));
        assertEquals(158,trace.productWrites());completed(id,"COMPLETED",10000);
    }
    @Test void sourceSqlTrimsOnlyEdgesAndPreservesAllApprovedAliases(){
        crmRow("  A  ","name",UPDATED);crmRow("B C","interior",UPDATED);crmRow(" DÚP ","unicode",UPDATED);
        try(var read=source.open(3)){
            List<P11CrmProductSyncTask.ProductSyncRow> rows=read.sqlSession().selectList(
                    P11CrmProductSyncTask.SourceMapper.class.getName()+".selectCrmProduct",
                    new AbstractSyncTask.SourceQuery<Void>(1000,1,true,null));
            assertEquals(Set.of("A","B C","DÚP"),rows.stream().map(P11CrmProductSyncTask.ProductSyncRow::getProductCode)
                    .collect(java.util.stream.Collectors.toSet()));
        }
        long id=register("BASE_P11",2,100);
        assertEquals(new SyncCounts(3,3,0,0,0),service.execute(new BaseSyncService.Run(id)));
        assertEquals(new SyncCounts(3,0,3,0,0),service.execute(new BaseSyncService.Run(id)));
    }
    @ParameterizedTest
    @ValueSource(strings={"CATEGORY ","category","CÁTEGORY","CATEGORY-X"})
    void changedBusinessStringsUpdateExactlyWithoutVersionAdjudication(String value){
        crmRow("A","name",UPDATED);long id=register("BASE_P11",1,100);service.execute(new BaseSyncService.Run(id));
        crm.update("UPDATE DPtech_v_product_info_single SET productCode=?",value);
        assertEquals(new SyncCounts(1,0,1,0,0),service.execute(new BaseSyncService.Run(id)));
        assertEquals(value,product("A").getProductCategory());assertEquals(1L,product("A").getVersion());
    }
    @Test void uniqueTargetIdentityRejectsUnapprovedSourceAndDeletedRecords(){
        crmRow("A","name",UPDATED);long id=register("BASE_P11",1,100);service.execute(new BaseSyncService.Run(id));
        target.update("UPDATE ast_product_official_info SET source_system='MANUAL'");
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertEquals("MANUAL",product("A").getSourceSystem());
        target.update("UPDATE ast_product_official_info SET source_system='CRM',deleted=TRUE");
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertTrue(product("A").getDeleted());assertEquals(3,trace.productWrites());
    }
    @Test void oneBatchUpsertsRepeatedKeyWithoutDiscardingSourceRows(){
        crmRow("A","first",UPDATED);crmRow("A","second",CREATED);
        long id=register("BASE_P11",128,100);
        assertEquals(new SyncCounts(2,1,1,0,0),service.execute(new BaseSyncService.Run(id)));
        assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM ast_product_official_info",Integer.class));
        assertEquals(1L,product("A").getVersion());
        assertTrue(Set.of("first","second").contains(product("A").getProductName()));
        assertEquals(1,trace.productWrites());completed(id,"COMPLETED",2);
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings={""," ","\t\r","\u2000"})
    void sourceQueryDoesNotHideMissingProductCodes(String code){
        crmRow(code,"invalid",UPDATED);long id=register("BASE_P11",1,100);
        assertTrue(assertThrows(IllegalStateException.class,()->service.preview(new BaseSyncService.Run(id)))
                .getMessage().contains("产品编码缺失"));
        assertEquals(0,trace.productWrites());
    }
}
