package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.ibatis.session.SqlSession;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="npdms.device-relation.mysql",matches="true")
class DeviceRelationshipAuditEvidenceMySqlTest {
    Connection db;SqlSession session;DeviceRelationshipAuditEvidenceMapper mapper;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD")));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.addMapper(SyncRunMapper.class);factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/sync/DeviceRelationshipAuditEvidenceMapper.xml"));
        session=factory.getObject().openSession(db);mapper=session.getMapper(DeviceRelationshipAuditEvidenceMapper.class);
        String approvedReceipt=System.getenv("DEVICE_RELATION_RECEIPT_PROOF");
        if(approvedReceipt!=null) {
            db.setAutoCommit(false);
            var proof=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                    java.nio.file.Files.readString(java.nio.file.Path.of(approvedReceipt)),
                    DppmsDeviceRelationshipAuditCorrectionAdapter.RunProof.class);
            var actual=mapper.selectReceiptForUpdate(new SyncQueries.Id(1L,proof.receipt().id()));
            assertEquals(proof.receipt(),actual,"Real approved receipt must survive config conversion exactly");
            db.rollback();
            db.setAutoCommit(true);
        }
        for(String table:List.of("int_sync_run","int_sync_binding","int_sync_task")) {
            String ddl;try(var s=db.createStatement();var rows=s.executeQuery("SHOW CREATE TABLE "+table)){assertTrue(rows.next());ddl=rows.getString(2);}
            try(var s=db.createStatement()){s.execute(ddl.replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"));}
        }
        try(var s=db.createStatement()) {
            s.execute("INSERT INTO int_sync_task(id,tenant_id,name,connection_id,adapter,source_system,definition,version,active_run_id) VALUES(8,1,'reviewed-correction',18,'DPPMS_DEVICE_RELATIONSHIP_AUDIT_CORRECTION','DPPMS','{\"mode\":\"ONCE\",\"resetMappingsBeforeLoad\":false}',1,9)");
            s.execute("INSERT INTO int_sync_run(id,tenant_id,task_id,request_key,status,preview,full_snapshot,adopt_existing,config_version,result_json,config_snapshot,summary_json,read_count,started_at,create_time,update_time,deleted) VALUES(9,1,8,'relation-audit-test','SUCCESS',0,1,0,1,'[]','{}','{}',100,'2023-01-02 03:04:05.123','2023-01-02 03:04:05.123','2023-01-02 03:04:05.123',0)");
            s.execute("INSERT INTO int_sync_binding(id,tenant_id,task_id,object_key,source_object,source_key,target_id,fields_json,last_run_id,target_shared,creator,updater,create_time,update_time,deleted) VALUES(90,1,8,'DEVICE_RELATION','fb_shipment_barcode_relation','20',40,'{}',9,1,'data_sync','data_sync','2023-01-02 03:04:05.123','2023-01-02 03:04:05.123',0)");
        }
        db.setAutoCommit(false);
    }
    @AfterEach void close() throws Exception {if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();}
    @Test void receiptUsesExactTenantAndTypedDatesWithRawResultDigest() {
        var r=mapper.selectReceiptForUpdate(new SyncQueries.Id(1L,9L));assertEquals(8L,r.taskId());assertFalse(r.preview());assertFalse(r.deleted());
        assertEquals("4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945",r.resultDigest());
        assertEquals(123000000,r.createTime().getNano());assertNull(mapper.selectReceiptForUpdate(new SyncQueries.Id(2L,9L)));
    }
    @Test void bindingEmptyKeysForeignTenantAndWrongObjectCannotExpandScope() {
        assertTrue(mapper.selectBindingsForUpdate(new SyncQueries.SourceKeys(1L,8L,"DEVICE_RELATION","fb_shipment_barcode_relation",List.of())).isEmpty());
        assertTrue(mapper.selectBindingsForUpdate(new SyncQueries.SourceKeys(2L,8L,"DEVICE_RELATION","fb_shipment_barcode_relation",List.of("20"))).isEmpty());
        assertTrue(mapper.selectBindingsForUpdate(new SyncQueries.SourceKeys(1L,8L,"OTHER","fb_shipment_barcode_relation",List.of("20"))).isEmpty());
        var b=mapper.selectBindingsForUpdate(new SyncQueries.SourceKeys(1L,8L,"DEVICE_RELATION","fb_shipment_barcode_relation",List.of("20"))).getFirst();
        assertEquals(90L,b.getId());assertEquals(40L,b.getTargetId());assertTrue(b.getTargetShared());assertEquals("data_sync",b.getCreator());
    }
    @Test void correctionContextLoadsOnlyLockedMetadataAndKeepsModeResetAndTenantChecks() throws Exception {
        var context=mapper.selectCorrectionContextForUpdate(new SyncQueries.Id(1L,8L));
        assertEquals(8L,context.taskId());assertEquals(9L,context.activeRunId());assertEquals(8L,context.runTaskId());
        assertEquals(1,context.runConfigVersion());assertEquals("ONCE",context.mode());assertFalse(context.resetMappingsBeforeLoad());
        assertFalse(context.runPreview());assertEquals("SUCCESS",context.runStatus());
        assertNull(mapper.selectCorrectionContextForUpdate(new SyncQueries.Id(2L,8L)));
        try(var s=db.createStatement()){s.execute("UPDATE int_sync_task SET definition='{\"mode\":\"INCREMENTAL\",\"resetMappingsBeforeLoad\":true}' WHERE id=8");}
        session.clearCache();
        context=mapper.selectCorrectionContextForUpdate(new SyncQueries.Id(1L,8L));
        assertEquals("INCREMENTAL",context.mode());assertTrue(context.resetMappingsBeforeLoad());
    }
    @Test void streamingProgressPreservesFrozenConfigAndEvidenceAndRejectsForeignTenant() throws Exception {
        try(var s=db.createStatement()){s.execute("UPDATE int_sync_run SET evidence_json='{\"kept\":true}' WHERE id=9");}
        session.clearCache();var runMapper=session.getMapper(SyncRunMapper.class);
        var progress=runMapper.selectStreamingState(new SyncQueries.Id(1L,9L));
        assertNull(progress.getConfigSnapshot());assertNull(progress.getEvidenceJson());
        assertNull(runMapper.selectStreamingState(new SyncQueries.Id(2L,9L)));
        progress.setReadCount(101).setStatus("VALIDATING").setSummaryJson("{\"UPDATED\":101}").setResultJson("[]");
        assertEquals(1,runMapper.updateById(progress));
        try(var s=db.createStatement();var rows=s.executeQuery("SELECT config_snapshot,evidence_json,read_count,status FROM int_sync_run WHERE id=9")) {
            assertTrue(rows.next());assertEquals("{}",rows.getString(1));assertEquals("{\"kept\": true}",rows.getString(2));
            assertEquals(101,rows.getInt(3));assertEquals("VALIDATING",rows.getString(4));
        }
    }
}
