package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.businessmodel.TenantCallerContext;
import cn.iocoder.yudao.module.pms.platform.service.command.*;
import cn.iocoder.yudao.module.system.api.permission.ExplicitPermissionApi;
import cn.iocoder.yudao.module.pms.platform.service.file.NativeGeneratedFileService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileAccessTicketService;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.mock.web.MockHttpServletRequest;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Production CAS/XML, real Spring transactions/ledger/audit/outbox; explicit permission and native Owner are fixtures. */
class DeliveryMaterialWithdrawalPersistenceTest {
    static final ThreadLocal<DataSource> SOURCES = new ThreadLocal<>();
    static final AtomicBoolean permission = new AtomicBoolean(), failAudit = new AtomicBoolean(), failOutbox = new AtomicBoolean();
    static volatile CountDownLatch downloadOwnerAttempt;
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    PlatformDeliveryMaterialApi api;
    DataSource source;
    static final List<Class<?>> TABLES = List.of(DeliveryMaterialDO.class, DeliveryFulfillmentDO.class,
            PlatformIdempotencyRecordDO.class, PlatformOperationAuditDO.class, PlatformOutboxEventDO.class);

    protected DataSource database() { return new DriverManagerDataSource("jdbc:h2:mem:withdraw_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
    protected boolean mysql() { return false; }
    void login(long tenant, long user) {
        TenantContextHolder.setTenantId(tenant);
        var principal = new LoginUser(); principal.setId(user); principal.setTenantId(tenant); principal.setUserType(2);
        SecurityFrameworkUtils.setLoginUser(principal, new MockHttpServletRequest());
    }
    @BeforeEach void start() throws Exception {
        downloadOwnerAttempt=null;source=database(); SOURCES.set(source); jdbc=new JdbcTemplate(source);
        for(var type:TABLES) schema(jdbc,type,mysql());
        jdbc.execute("CREATE UNIQUE INDEX ledger_scope ON plt_idempotency_record(tenant_id,scope_code,actor_id,idempotency_key)");
        jdbc.execute("CREATE TABLE native_owner (id BIGINT PRIMARY KEY, tenant_id BIGINT, writable BOOLEAN, version BIGINT)");
        jdbc.update("INSERT INTO native_owner VALUES(101,7,TRUE,3)");
        jdbc.execute("CREATE TABLE immutable_submission (id BIGINT PRIMARY KEY, evidence VARCHAR(256))");
        jdbc.update("INSERT INTO immutable_submission VALUES(301,'original:901:file2:revision5')");
        // Run the actual forward migration on a pre-version table with existing evidence.
        jdbc.update("INSERT INTO plt_delivery_material(id,tenant_id,owner_module,entity_type,entity_id,type_code,project_id,status,archive_status,file_artifact_id,file_version_no,file_sha256,business_revision_no) VALUES(901,7,'NATIVE','note',101,'DOC',99,'ACTIVE','NOT_REQUIRED',9007199254740993,2,'frozen-sha',5)");
        var migration=Files.readString(Path.of(System.getProperty("user.dir"),"../sql/migrations/V398__delivery_material_version.sql"));
        jdbc.execute(migration);
        assertEquals(0L,jdbc.queryForObject("SELECT version FROM plt_delivery_material WHERE id=901",Long.class));
        permission.set(true);failAudit.set(false);failOutbox.set(false);login(7,9);
        context=new AnnotationConfigApplicationContext(Config.class); api=context.getBean(PlatformDeliveryMaterialApi.class);
    }
    @AfterEach void close() {
        SecurityContextHolder.clearContext();TenantContextHolder.clear();SOURCES.remove();
        if(context!=null)context.close();
        if(jdbc!=null) {
            if(mysql()) { for(var type:TABLES)jdbc.execute("DROP TABLE IF EXISTS "+type.getAnnotation(TableName.class).value());jdbc.execute("DROP TABLE IF EXISTS native_owner");jdbc.execute("DROP TABLE IF EXISTS immutable_submission"); }
            else jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }
    Map<String,Long> counts() {
        var result=new LinkedHashMap<String,Long>();for(String table:List.of("plt_idempotency_record","plt_operation_audit","plt_outbox_event","plt_delivery_fulfillment"))result.put(table,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));return result;
    }
    void anchors() {
        assertEquals(9007199254740993L,jdbc.queryForObject("SELECT file_artifact_id FROM plt_delivery_material WHERE id=901",Long.class));
        assertEquals(2,jdbc.queryForObject("SELECT file_version_no FROM plt_delivery_material WHERE id=901",Integer.class));
        assertEquals("frozen-sha",jdbc.queryForObject("SELECT file_sha256 FROM plt_delivery_material WHERE id=901",String.class));
        assertEquals(5L,jdbc.queryForObject("SELECT business_revision_no FROM plt_delivery_material WHERE id=901",Long.class));
        assertEquals("original:901:file2:revision5",jdbc.queryForObject("SELECT evidence FROM immutable_submission WHERE id=301",String.class));
    }
    @Test void casReplayPreservesAnchorsAndRecordsExactlyOneAuditAndDeliveryEvent() {
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(context.getBean(DeliveryMaterialWithdrawalService.class)));
        var result=api.withdrawMaterial(901L,0L,"one","obsolete evidence");assertEquals(1L,result.version());assertEquals("WITHDRAWN",result.status());
        assertEquals(result,api.withdrawMaterial(901L,0L,"one","obsolete evidence"));
        assertEquals(Map.of("plt_idempotency_record",1L,"plt_operation_audit",1L,"plt_outbox_event",1L,"plt_delivery_fulfillment",0L),counts());
        assertEquals("pms.delivery.changed",jdbc.queryForObject("SELECT event_type FROM plt_outbox_event",String.class));anchors();
        assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"one","different reason"));
        assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"other","obsolete evidence"));assertEquals(1L,counts().get("plt_idempotency_record"));
    }
    @Test void currentOwnerAndOriginalPermissionAreRequiredBeforeCompletedReplay() {
        api.withdrawMaterial(901L,0L,"one","obsolete");var before=counts();
        permission.set(false);assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"one","obsolete"));permission.set(true);
        jdbc.update("UPDATE native_owner SET writable=FALSE WHERE id=101");
        assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"one","obsolete"));assertEquals(before,counts());anchors();
    }
    @Test void missingPrincipalWrongTenantAndRolelessAreDeniedWithoutLedgerReservation() {
        SecurityContextHolder.clearContext();assertThrows(RuntimeException.class,()->api.withdrawMaterial(901L,0L,"denied","reason"));
        login(8,9);assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"denied","reason"));
        login(7,9);permission.set(false);assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"denied","reason"));
        assertTrue(counts().values().stream().allMatch(v->v==0));anchors();
    }
    @Test void staleVersionTemplateArchiveAndExistingFulfillmentCannotWithdraw() {
        assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,1L,"stale","reason"));
        jdbc.update("UPDATE plt_delivery_material SET requirement_id=1001 WHERE id=901");assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"template","reason"));
        jdbc.update("UPDATE plt_delivery_material SET requirement_id=NULL,archive_status='ARCHIVED' WHERE id=901");assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"archive","reason"));
        jdbc.update("UPDATE plt_delivery_material SET archive_status='NOT_REQUIRED',archive_time=CURRENT_TIMESTAMP WHERE id=901");assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"archive-time","reason"));
        jdbc.update("UPDATE plt_delivery_material SET archive_time=NULL WHERE id=901");
        jdbc.update("INSERT INTO plt_delivery_fulfillment(id,tenant_id,requirement_id,material_id,status) VALUES(1001,7,1002,901,'WITHDRAWN')");
        assertThrows(BusinessContractException.class,()->api.withdrawMaterial(901L,0L,"relation","reason"));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=901",String.class));assertEquals(0L,counts().get("plt_idempotency_record"));anchors();
    }
    @Test void auditAndOutboxFailuresRollBackMaterialVersionAndLedgerAndAllowRetry() {
        for(var failure:List.of(failAudit,failOutbox)) {
            failure.set(true);assertThrows(IllegalStateException.class,()->api.withdrawMaterial(901L,0L,"retry","reason"));failure.set(false);
            assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=901",String.class));assertEquals(0L,jdbc.queryForObject("SELECT version FROM plt_delivery_material WHERE id=901",Long.class));
            assertTrue(counts().values().stream().allMatch(v->v==0));anchors();
        }
        assertEquals(1L,api.withdrawMaterial(901L,0L,"retry","reason").version());
    }
    @Test void concurrentDifferentKeysHaveExactlyOneVersionWinner() throws Exception {
        var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)) {
            var jobs=new ArrayList<Future<Boolean>>();for(String key:List.of("a","b"))jobs.add(pool.submit(()->{login(7,9);try{start.await();api.withdrawMaterial(901L,0L,key,"reason");return true;}catch(BusinessContractException denied){return false;}finally{SecurityContextHolder.clearContext();TenantContextHolder.clear();}}));
            start.countDown();int winners=0;for(var job:jobs)if(job.get(15,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
        }
        assertEquals(1L,jdbc.queryForObject("SELECT version FROM plt_delivery_material WHERE id=901",Long.class));assertEquals(1L,counts().get("plt_outbox_event"));assertEquals(1L,counts().get("plt_idempotency_record"));anchors();
    }
    @Test void staleActiveMaterialCannotBeAssociatedAfterWithdrawal() {
        var material=context.getBean(DeliveryMaterialMapper.class).selectById(901L);
        api.withdrawMaterial(901L,0L,"one","reason");
        var requirement=new DeliveryRequirementDO();requirement.setId(1001L);requirement.setTenantId(7L);requirement.setProjectId(99L);
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(BusinessContractException.class,()->tx.executeWithoutResult(status->context.getBean(DeliveryFulfillmentService.class).associate(requirement,material)));
        assertEquals(0L,counts().get("plt_delivery_fulfillment"));anchors();
    }
    @Test void simultaneousAssociationAndWithdrawalCannotLeaveWithdrawnMaterialWithAnActiveRelation() throws Exception {
        var material=context.getBean(DeliveryMaterialMapper.class).selectById(901L);
        var requirement=new DeliveryRequirementDO();requirement.setId(1001L);requirement.setTenantId(7L);requirement.setProjectId(99L);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var withdrawn=pool.submit(()->{login(7,9);try{start.await();api.withdrawMaterial(901L,0L,"race","reason");return true;}catch(BusinessContractException denied){return false;}finally{SecurityContextHolder.clearContext();TenantContextHolder.clear();}});
            var associated=pool.submit(()->{login(7,9);try{start.await();new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status->context.getBean(DeliveryFulfillmentService.class).associate(requirement,material));return true;}catch(BusinessContractException denied){return false;}finally{SecurityContextHolder.clearContext();TenantContextHolder.clear();}});
            start.countDown();boolean w=withdrawn.get(15,TimeUnit.SECONDS),a=associated.get(15,TimeUnit.SECONDS);assertNotEquals(w,a);
            assertEquals(w?"WITHDRAWN":"ACTIVE",jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=901",String.class));
            assertEquals(w?0L:1L,counts().get("plt_delivery_fulfillment"));assertEquals(w?1L:0L,counts().get("plt_idempotency_record"));
        }
        anchors();
    }

    @Test void bothMaterialLockOrdersRejectTheLosingConcurrentCommand() throws Exception {
        for(boolean associationFirst:List.of(true,false)) {
            jdbc.update("DELETE FROM plt_delivery_fulfillment");jdbc.update("DELETE FROM plt_idempotency_record");jdbc.update("DELETE FROM plt_operation_audit");jdbc.update("DELETE FROM plt_outbox_event");
            jdbc.update("UPDATE plt_delivery_material SET status='ACTIVE',version=0 WHERE id=901");
            var material=context.getBean(DeliveryMaterialMapper.class).selectById(901L);
            var requirement=new DeliveryRequirementDO();requirement.setId(1001L);requirement.setTenantId(7L);requirement.setProjectId(99L);
            var attempted=new CountDownLatch(1);var pending=new java.util.concurrent.atomic.AtomicReference<Future<Boolean>>();
            try(var pool=Executors.newSingleThreadExecutor()) {
                new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status->{
                    if(associationFirst)context.getBean(DeliveryFulfillmentService.class).associate(requirement,material);
                    else api.withdrawMaterial(901L,0L,"first","reason");
                    pending.set(pool.submit(()->{login(7,9);try{attempted.countDown();if(associationFirst)api.withdrawMaterial(901L,0L,"second","reason");else new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(other->context.getBean(DeliveryFulfillmentService.class).associate(requirement,material));return true;}catch(BusinessContractException denied){return false;}finally{SecurityContextHolder.clearContext();TenantContextHolder.clear();}}));
                    try{assertTrue(attempted.await(5,TimeUnit.SECONDS));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    assertFalse(pending.get().isDone(),"second command must wait for the first transaction's material lock");
                });
                assertFalse(pending.get().get(15,TimeUnit.SECONDS),"loser must recheck current material/relations after the lock");
            }
            assertEquals(associationFirst?"ACTIVE":"WITHDRAWN",jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=901",String.class));
            assertEquals(associationFirst?1L:0L,counts().get("plt_delivery_fulfillment"));anchors();
        }
    }

    @Test void associationOuterRollbackAndCrossTenantNeverLeaveAnActiveRelation() {
        var material=context.getBean(DeliveryMaterialMapper.class).selectById(901L);
        var requirement=new DeliveryRequirementDO();requirement.setId(1001L);requirement.setTenantId(7L);requirement.setProjectId(99L);
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{context.getBean(DeliveryFulfillmentService.class).associate(requirement,material);throw new IllegalStateException("caller failure");}));
        assertEquals(0L,counts().get("plt_delivery_fulfillment"));
        login(8,9);assertThrows(BusinessContractException.class,()->tx.executeWithoutResult(status->context.getBean(DeliveryFulfillmentService.class).associate(requirement,material)));
        assertEquals(0L,counts().get("plt_delivery_fulfillment"));login(7,9);
        assertEquals(1L,api.withdrawMaterial(901L,0L,"after-association-rollback","reason").version());anchors();
    }

    @Test void nativeDownloadRechecksWithdrawalAfterOwnerLockBeforeIssuingATicket() throws Exception {
        jdbc.update("UPDATE plt_delivery_material SET file_reference_id=501,material_kind='FILE' WHERE id=901");
        downloadOwnerAttempt=new CountDownLatch(1);
        var tickets=context.getBean(FileAccessTicketService.class);
        var attempted=new CountDownLatch(1);var pending=new java.util.concurrent.atomic.AtomicReference<Future<String>>();
        try(var pool=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status->{
                jdbc.queryForList("SELECT id FROM native_owner WHERE id=101 FOR UPDATE");
                pending.set(pool.submit(()->{login(7,9);try{attempted.countDown();return context.getBean(NativeGeneratedFileService.class).requestDownload("NATIVE","note",101L,901L);}finally{SecurityContextHolder.clearContext();TenantContextHolder.clear();}}));
                try{assertTrue(attempted.await(5,TimeUnit.SECONDS));assertTrue(downloadOwnerAttempt.await(5,TimeUnit.SECONDS),"download must acquire its actual Owner before file authorization");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                assertFalse(pending.get().isDone(),"download must wait for the Owner transaction");
                jdbc.update("UPDATE plt_delivery_material SET status='WITHDRAWN',version=1 WHERE id=901");
            });
            var failure=assertThrows(ExecutionException.class,()->pending.get().get(15,TimeUnit.SECONDS));
            assertInstanceOf(BusinessContractException.class,failure.getCause());
        }
        verify(tickets,never()).create(any());assertTrue(counts().values().stream().allMatch(v->v==0));anchors();
    }

    @Test void nativeDownloadLocksCurrentMaterialAndRejectsWithdrawnAndWrongTenantWithoutATicket() {
        jdbc.update("UPDATE plt_delivery_material SET file_reference_id=501,material_kind='FILE' WHERE id=901");
        var downloads=context.getBean(NativeGeneratedFileService.class);var tickets=context.getBean(FileAccessTicketService.class);
        assertEquals("https://fixture.invalid/internal",downloads.requestDownload("NATIVE","note",101L,901L));verify(tickets,times(1)).create(any());clearInvocations(tickets);
        api.withdrawMaterial(901L,0L,"withdraw-download","reason");
        assertThrows(BusinessContractException.class,()->downloads.requestDownload("NATIVE","note",101L,901L));
        login(8,9);assertThrows(BusinessContractException.class,()->downloads.requestDownload("NATIVE","note",101L,901L));verify(tickets,never()).create(any());anchors();
    }

    @Test void withdrawalInsideOuterTransactionRollsBackWithItsCaller() {
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{api.withdrawMaterial(901L,0L,"outer","reason");throw new IllegalStateException("caller failure");}));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=901",String.class));assertTrue(counts().values().stream().allMatch(v->v==0));
        assertEquals(1L,api.withdrawMaterial(901L,0L,"outer","reason").version());
    }
    static void schema(JdbcTemplate jdbc,Class<?> type,boolean mysql) {
        var columns=new LinkedHashMap<String,String>();
        for(Class<?> current=type;current!=Object.class;current=current.getSuperclass())for(Field field:current.getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())||field.isSynthetic())continue;
            var annotation=field.getAnnotation(TableField.class);if(annotation!=null&&!annotation.exist())continue;
            String name=annotation!=null&&!annotation.value().isBlank()?annotation.value():field.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
            if(type==DeliveryMaterialDO.class && name.equals("version"))continue;
            Class<?> kind=field.getType();String sql=kind==Long.class||kind==Integer.class?"BIGINT":kind==Boolean.class?"BOOLEAN":kind==java.time.LocalDateTime.class?"TIMESTAMP":mysql?"VARCHAR(256)":"VARCHAR(4000)";
            if(name.equals("id"))sql="BIGINT AUTO_INCREMENT PRIMARY KEY";else if(name.equals("deleted"))sql="BOOLEAN DEFAULT FALSE";else if(name.equals("create_time")||name.equals("update_time"))sql="TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
            if(name.equals("response_payload")||name.equals("detail_snapshot")||name.equals("payload"))sql="TEXT";
            columns.putIfAbsent(name,name+" "+sql);
        }
        jdbc.execute("CREATE TABLE "+type.getAnnotation(TableName.class).value()+" ("+String.join(",",columns.values())+")");
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    @Import({DeliveryMaterialWithdrawalService.class,PlatformCommandExecutionApiImpl.class,PlatformTransactionalOutboxWriter.class,TenantCallerContext.class})
    static class Config {
        @Bean static org.springframework.core.convert.ConversionService conversionService(){return org.springframework.boot.convert.ApplicationConversionService.getSharedInstance();}
        @Bean DataSource dataSource(){return SOURCES.get();}
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean SqlSessionFactory sessions(DataSource source) throws Exception {
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);
            var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);factory.setConfiguration(configuration);
            var resources=new ArrayList<Resource>();
            boolean h2; try(var connection=source.getConnection()){h2=connection.getMetaData().getURL().startsWith("jdbc:h2:");}
            for(String file:List.of("mapper/delivery/DeliveryMaterialMapper.xml","mapper/delivery/DeliveryFulfillmentMapper.xml","mapper/command/PlatformIdempotencyRecordMapper.xml")) {
                try(var input=new ClassPathResource(file).getInputStream()) {
                    String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8);if(h2)xml=xml.replace("b'0'","0");resources.add(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8),file));
                }
            }
            factory.setMapperLocations(resources.toArray(Resource[]::new));var result=factory.getObject();
            result.getConfiguration().addMapper(PlatformOperationAuditMapper.class);result.getConfiguration().addMapper(PlatformOutboxEventMapper.class);return result;
        }
        @Bean DeliveryMaterialMapper materials(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(DeliveryMaterialMapper.class);}
        @Bean DeliveryFulfillmentMapper fulfillmentMapper(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(DeliveryFulfillmentMapper.class);}
        @Bean DeliveryFulfillmentService fulfillment(DeliveryFulfillmentMapper f,DeliveryMaterialMapper m){return new DeliveryFulfillmentService(f,m);}
        @Bean PlatformIdempotencyRecordMapper ledger(SqlSessionFactory f){return new SqlSessionTemplate(f).getMapper(PlatformIdempotencyRecordMapper.class);}
        @Bean PlatformOperationAuditMapper audits(SqlSessionFactory f) {
            var real=new SqlSessionTemplate(f).getMapper(PlatformOperationAuditMapper.class);var spy=mock(PlatformOperationAuditMapper.class,org.mockito.AdditionalAnswers.delegatesTo(real));
            doAnswer(call->{int result=real.insert(call.getArgument(0,PlatformOperationAuditDO.class));return failAudit.get()?0:result;}).when(spy).insert(any(PlatformOperationAuditDO.class));return spy;
        }
        @Bean PlatformOutboxEventMapper outbox(SqlSessionFactory f) {
            var real=new SqlSessionTemplate(f).getMapper(PlatformOutboxEventMapper.class);var spy=mock(PlatformOutboxEventMapper.class,org.mockito.AdditionalAnswers.delegatesTo(real));
            doAnswer(call->{int result=real.insert(call.getArgument(0,PlatformOutboxEventDO.class));return failOutbox.get()?0:result;}).when(spy).insert(any(PlatformOutboxEventDO.class));return spy;
        }
        @Bean ExplicitPermissionApi permission() {
            var api=mock(ExplicitPermissionApi.class);when(api.lockAndCheck(anyLong(),anyLong(),anyString())).thenAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertEquals("pms:delivery:operate",call.getArgument(2));return permission.get();});return api;
        }
        @Bean DeliveryOwnerAccess owners(DataSource ds) {
            var owner=new DeliveryMaterialUploadPolicyValidator() {
                public String ownerModule(){return "NATIVE";}
                public boolean supportsEntityType(String type){return "note".equals(type);}
                public cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact validateUpload(Long t,Long a,String e,String id,String p,String action,boolean lock,Long scope){throw new AssertionError("withdrawal is not file upload");}
                public Long requireDeliveryAccess(Long tenant,Long actor,String type,String id,String purpose,boolean write,boolean lock,Long expected) {
                    assertTrue(lock);assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    if(!write && downloadOwnerAttempt!=null)downloadOwnerAttempt.countDown();
                    var rows=new JdbcTemplate(ds).queryForList("SELECT tenant_id,writable,version FROM native_owner WHERE id=? FOR UPDATE",Long.valueOf(id));
                    if(rows.size()!=1||!Objects.equals(tenant,((Number)rows.getFirst().get("tenant_id")).longValue())||write&&!Boolean.TRUE.equals(rows.getFirst().get("writable")))throw new BusinessContractException("DELIVERY_ACCESS_DENIED","Owner denied");return ((Number)rows.getFirst().get("version")).longValue();
                }
            };
            return new DeliveryOwnerAccess(List.of(owner),null,null,null,null,null);
        }
        @Bean FileEvidenceApi evidence() {
            var api=mock(FileEvidenceApi.class);when(api.inspectDocument(7L,501L)).thenReturn(new FileEvidenceApi.Document(501L,"NATIVE","note","101","DOC","slot",9007199254740993L,2,"frozen-sha","native.txt",true));return api;
        }
        @Bean FileAccessTicketService tickets() {
            var api=mock(FileAccessTicketService.class);var result=new cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileAccessTicketRespVO(701L,"https://fixture.invalid/internal",java.time.LocalDateTime.now().plusSeconds(60));when(api.create(any())).thenReturn(result);return api;
        }
        @Bean NativeGeneratedFileService nativeDownloads(DeliveryMaterialMapper materials,FileEvidenceApi evidence,FileAccessTicketService tickets,DeliveryOwnerAccess owners) {
            return new NativeGeneratedFileService(mock(cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry.class),mock(cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService.class),mock(DeliveryMaterialService.class),materials,evidence,tickets,mock(cn.iocoder.yudao.module.system.api.permission.PermissionApi.class),owners);
        }
        @Bean PlatformDeliveryMaterialApi materialApi(DeliveryMaterialMapper m,DeliveryMaterialWithdrawalService s){return new PlatformDeliveryMaterialApiImpl(mock(DeliveryMaterialService.class),m,s);}
    }
}
