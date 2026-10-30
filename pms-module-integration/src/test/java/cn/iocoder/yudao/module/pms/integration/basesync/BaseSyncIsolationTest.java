package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper;
import cn.iocoder.yudao.module.pms.integration.sync.SyncConnectionService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the new Mapper XML against a unique disposable H2 database, never an application database. */
class BaseSyncIsolationTest {
    JdbcTemplate jdbc;
    BaseSyncMetadataMapper mapper;
    BaseSyncMetadataMapper actualMapper;
    BaseSyncService service;
    PermissionApi permissions;
    SyncConnectionService connections;
    ExampleTask task;
    final BaseSyncConfiguration config=new BaseSyncConfiguration("BASE_P11",3,2,10);

    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(2L).setTenantId(1L),null,List.of()));
        var source=new DriverManagerDataSource("jdbc:h2:mem:base-sync-isolation-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        Path root=Path.of("").toAbsolutePath();
        while(!Files.isDirectory(root.resolve("sql/migrations"))) root=root.getParent();
        String ddl=Files.readString(root.resolve("sql/migrations/V483__independent_base_sync_metadata.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci","")
                .replace("CHARACTER SET utf8mb4 COLLATE utf8mb4_bin","").replace("b'0'","FALSE");
        new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))).execute(source);
        // Identically numbered original tasks and receipts remain separate from the new namespace.
        jdbc.execute("CREATE TABLE int_sync_task(id BIGINT PRIMARY KEY,tenant_id BIGINT,definition VARCHAR(128),active_run_id BIGINT)");
        jdbc.execute("CREATE TABLE int_sync_run(id BIGINT PRIMARY KEY,task_id BIGINT,status VARCHAR(32))");
        jdbc.execute("CREATE TABLE int_sync_binding(id BIGINT PRIMARY KEY,task_id BIGINT,source_key VARCHAR(128),target_id BIGINT)");
        jdbc.update("INSERT INTO int_sync_task VALUES(100,1,'original-task-definition',900)");
        jdbc.update("INSERT INTO int_sync_run VALUES(900,100,'COMPLETED')");
        jdbc.update("INSERT INTO int_sync_binding VALUES(800,100,'10',999)");
        jdbc.execute("CREATE TABLE example_business(id BIGINT PRIMARY KEY,tenant_id BIGINT)");
        var configuration=new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),source));
        String resource="mapper/basesync/BaseSyncMetadataMapper.xml";
        try(var input=new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        actualMapper=session.getMapper(BaseSyncMetadataMapper.class);
        mapper=mock(BaseSyncMetadataMapper.class,org.mockito.AdditionalAnswers.delegatesTo(actualMapper));
        permissions=mock(PermissionApi.class);
        when(permissions.hasAnyPermissions(2L,"pms:integration:configure")).thenReturn(true);
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        connections=mock(SyncConnectionService.class);
        task=new ExampleTask();
        service=new BaseSyncService(List.of(task),mock(ConfiguredSyncSource.class),permissions,mock(AdminUserApi.class),
                new DataSourceTransactionManager(source),mapper,connections,mock(SyncReferences.class),session);
    }

    @AfterEach void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    class ExampleTask extends AbstractSyncTask<Long,Void> {
        boolean failLast;
        boolean fatalLast;
        boolean warn;
        int transientFailures,attempts,fullReads,lastBatchSize;
        final List<List<String>> attemptedStages=new ArrayList<>();
        @Override public String taskCode(){return "BASE_P11";}
        @Override public String taskName(){return "Independent fixture";}
        @Override public Set<String> requiredPermissions(){return Set.of();}
        @Override public List<String> supportedStages(){return List.of("Rows");}
        @Override public void work(SyncContext<Void> context){attempts++;lastBatchSize=context.batchSize();attemptedStages.add(context.runState().stages());super.work(context);if(warn)context.runState().warning("SOURCE_WARNING:10");}
        @Override protected List<Long> syncDataQuery(SyncContext<Void> context){fullReads++;return List.of(10L,20L,30L);}
        @Override protected void syncDataQueryByRowHandler(SyncContext<Void> context,ResultHandler<Long> handler){AbstractSyncTaskLifecycleTest.emit(List.of(10L,20L,30L),handler);}
        @Override protected void syncDataInsert(SyncBatch<Long,Void> batch){
            for(long id:batch.rows()){
                jdbc.update("INSERT INTO example_business VALUES(?,?)",id,batch.context().tenantId());
                if(failLast && id==30)throw new IllegalStateException("last batch failed");
                if(fatalLast && id==30)throw new StackOverflowError("synthetic error without SQL or credentials");
                if(transientFailures>0 && id==30){transientFailures--;throw new org.springframework.dao.CannotAcquireLockException(
                        "fixture transaction rollback",new java.sql.SQLTransactionRollbackException("deadlock", "40001"));}
            }
        }
        @Override protected void syncDataInsertAfter(SyncBatch<Long,Void> batch){
            for(long id:batch.rows()) batch.context().identities().save(new SyncIdentityStore.Save(
                    "EXAMPLE",Long.toString(id),id,"a".repeat(64),null));
        }
    }

    @Test void originalTaskIdCannotEnterNewExecutionOrPreview() {
        assertThrows(IllegalArgumentException.class,()->service.preview(new BaseSyncService.Run(100)));
        assertThrows(IllegalArgumentException.class,()->service.execute(new BaseSyncService.Run(100)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_run",Integer.class));
        verifyNoInteractions(connections);
        assertOriginalUnchanged();
    }
    @Test void fatalTaskErrorRollsBackAndClosesOnlyItsIndependentRun() {
        long id=service.register(config);task.fatalLast=true;
        assertTrue(assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id))).getMessage().contains("StackOverflowError"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM example_business",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding",Integer.class));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM int_base_sync_run",String.class));
        assertNull(mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).activeRunId());assertOriginalUnchanged();
    }
    @Test void approvedSourceIssueIsRecordedWithoutChangingSuccessfulPreviewStatusOrWritingBusiness(){
        long id=service.register(config);task.warn=true;
        service.preview(new BaseSyncService.Run(id));
        assertEquals("PREVIEW_READY",jdbc.queryForObject("SELECT status FROM int_base_sync_run",String.class));
        assertEquals("SOURCE_WARNING:10",jdbc.queryForObject("SELECT error_message FROM int_base_sync_run",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM example_business",Integer.class));assertOriginalUnchanged();
    }
    @Test void recoveryRejectsOriginalRunAndLiveOrNonPreviewRun() {
        assertThrows(IllegalStateException.class,()->service.recoverPreview(new BaseSyncService.PreviewRecovery(100,900)));
        long id=service.register(config);task.failLast=true;doReturn(0).when(mapper).finishRun(any());
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        long run=mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).activeRunId();
        assertThrows(IllegalStateException.class,()->service.recoverPreview(new BaseSyncService.PreviewRecovery(id,run)));
        assertEquals("READING",jdbc.queryForObject("SELECT status FROM int_base_sync_run",String.class));assertOriginalUnchanged();
    }
    @Test void recoveryClosesOnlyAnOlderAbandonedPreviewAfterLockingItsTask() {
        long id=service.register(config);doReturn(0).when(mapper).finishRun(any());
        assertThrows(IllegalStateException.class,()->service.preview(new BaseSyncService.Run(id)));
        long run=mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).activeRunId();
        assertThrows(IllegalStateException.class,()->service.recoverPreview(new BaseSyncService.PreviewRecovery(id,run)));
        jdbc.update("UPDATE int_base_sync_run SET started_at=TIMESTAMP '2000-01-01 00:00:00'");
        doAnswer(call->actualMapper.finishRun(call.getArgument(0))).when(mapper).finishRun(any());
        assertThrows(IllegalStateException.class,()->service.recoverPreview(new BaseSyncService.PreviewRecovery(id,run+1)));
        service.recoverPreview(new BaseSyncService.PreviewRecovery(id,run));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM int_base_sync_run",String.class));
        assertEquals("PREVIEW_INTERRUPTED_BEFORE_CURRENT_RUNTIME",jdbc.queryForObject("SELECT error_message FROM int_base_sync_run",String.class));
        assertNull(mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).activeRunId());assertOriginalUnchanged();
    }

    @Test void registerCreatesOnlyNewTaskAndReusesConnectionReference() {
        long id=service.register(config);
        assertEquals(config,mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).configuration());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_task",Integer.class));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->service.register(config));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_task",Integer.class));
        verify(connections,times(2)).required(3L);
        assertOriginalUnchanged();
    }

    @Test void previewRecordsOnlyNewRunAndNeverWritesBusinessOrBindings() {
        long id=service.register(config);
        assertEquals(new SyncCounts(3,3,0,0,0),service.preview(new BaseSyncService.Run(id)));
        assertEquals("PREVIEW_READY",jdbc.queryForObject("SELECT status FROM int_base_sync_run",String.class));
        assertEquals(3L,jdbc.queryForObject("SELECT read_count FROM int_base_sync_run",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM example_business",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding",Integer.class));
        assertNull(mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).activeRunId());
        assertOriginalUnchanged();
    }
    @Test void frontendBatchSizeControlsPreviewAndExecuteWithoutChangingRegistrationDefaults() {
        long id=service.register(config);
        assertEquals(new SyncCounts(3,3,0,0,0),service.preview(new BaseSyncService.Run(id,1)));
        assertEquals(1,task.lastBatchSize);
        assertEquals(new SyncCounts(3,3,0,0,0),service.execute(new BaseSyncService.Run(id,3)));
        assertEquals(3,task.lastBatchSize);
        assertEquals(List.of(1,3),jdbc.queryForList("SELECT batch_size FROM int_base_sync_run ORDER BY id",Integer.class));
        assertEquals(config,mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).configuration());
        assertOriginalUnchanged();
    }
    @Test void omittedBatchSizeUsesRegistrationAndExistingBoundsRejectBeforeRunCreation() {
        long id=service.register(config);
        for(int invalid:List.of(0,2001))assertThrows(IllegalArgumentException.class,()->service.preview(new BaseSyncService.Run(id,invalid)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_run",Integer.class));
        service.preview(new BaseSyncService.Run(id));assertEquals(config.batchSize(),task.lastBatchSize);
        assertEquals(config.batchSize(),jdbc.queryForObject("SELECT batch_size FROM int_base_sync_run",Integer.class));
        assertOriginalUnchanged();
    }
    @Test void registrationListIsLimitedToTheNewNamespaceAndCurrentTenant() {
        long id=service.register(config);
        assertEquals(List.of(id),service.registrations().stream().map(BaseSyncMetadataMapper.TaskRow::id).toList());
        TenantContextHolder.setTenantId(2L);assertTrue(service.registrations().isEmpty());
        assertOriginalUnchanged();
    }

    @Test void sameNumericIdStillCannotReadOrWriteOriginalTaskAndReceipts() {
        mapper.createTask(new BaseSyncMetadataMapper.Registration(100,1,"independent","CRM",config,"2"));
        service.execute(new BaseSyncService.Run(100));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM example_business",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding",Integer.class));
        var bindings=mapper.selectBindings(new BaseSyncMetadataMapper.BindingQuery(1,100,"EXAMPLE",List.of("10"),true));
        assertEquals(List.of(new SyncIdentityStore.Identity("10",10,"a".repeat(64))),bindings);
        assertTrue(mapper.selectBindings(new BaseSyncMetadataMapper.BindingQuery(1,100,"EXAMPLE",List.of(),true)).isEmpty());
        assertTrue(mapper.selectBindings(new BaseSyncMetadataMapper.BindingQuery(2,100,"EXAMPLE",List.of("10"),true)).isEmpty());
        assertOriginalUnchanged();
    }

    @Test void lastBatchFailureRollsBackBusinessAndBindingsButKeepsNewFailureReceipt() {
        long id=service.register(config);task.failLast=true;
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM example_business",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding",Integer.class));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM int_base_sync_run",String.class));
        assertEquals(0L,jdbc.queryForObject("SELECT inserted_count FROM int_base_sync_run",Long.class));
        assertNull(mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).activeRunId());
        assertEquals(1,task.attempts,"business failures must not retry");
        assertOriginalUnchanged();
    }

    @Test void retryStartsFreshTransactionAndDoesNotKeepEarlierBusinessOrBindings(){
        long id=service.register(config);task.transientFailures=1;task.setRetryIntervalMillis(0);
        assertEquals(new SyncCounts(3,3,0,0,0),service.execute(new BaseSyncService.Run(id)));
        assertEquals(2,task.attempts);
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM example_business",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding",Integer.class));
        assertEquals(List.of("COMPLETED"),jdbc.queryForList("SELECT status FROM int_base_sync_run",String.class));
        assertOriginalUnchanged();
    }
    @Test void exhaustedRetriesLeaveOneFailureReceiptAndNextRunGetsFreshBudget(){
        long id=service.register(config);task.transientFailures=3;task.setMaxRetries(1);task.setRetryIntervalMillis(0);
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertEquals(2,task.attempts);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM example_business",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding",Integer.class));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM int_base_sync_run",String.class));
        service.execute(new BaseSyncService.Run(id));
        assertEquals(4,task.attempts);
        assertEquals(List.of("COMPLETED","FAILED"),jdbc.queryForList("SELECT status FROM int_base_sync_run ORDER BY status",String.class));
        assertOriginalUnchanged();
    }
    @Test void defaultReadModeSettingIsUsedByRunContext(){
        long id=service.register(config);task.setDefaultUseRowHandler(false);
        service.preview(new BaseSyncService.Run(id));assertEquals(1,task.fullReads);
        task.setDefaultUseRowHandler(true);
        service.preview(new BaseSyncService.Run(id));assertEquals(1,task.fullReads);
    }
    @Test void retryRejectsConstraintAndUnknownConnectionFailuresAndRespectsInterrupt(){
        assertFalse(task.retryAllowed(new IllegalStateException(new java.sql.SQLIntegrityConstraintViolationException())));
        assertFalse(task.retryAllowed(new IllegalStateException(new java.sql.SQLRecoverableException())));
        assertFalse(task.retryAllowed(new IllegalStateException(new java.io.IOException())));
        Thread.currentThread().interrupt();
        try { assertFalse(task.retryAllowed(new IllegalStateException(new java.sql.SQLTransactionRollbackException()))); }
        finally { Thread.interrupted(); }
        assertThrows(IllegalArgumentException.class,()->task.setMaxRetries(-1));
        assertThrows(IllegalArgumentException.class,()->task.setRetryIntervalMillis(-1));
    }
    @Test void failureReceiptConflictIsReportedAndCannotReleaseTheTaskAlone(){
        long id=service.register(config);task.failLast=true;
        doReturn(0).when(mapper).finishRun(any());
        var failure=assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertTrue(failure.getMessage().contains("last batch failed"));
        assertTrue(failure.getMessage().contains("失败回执未能保存"));
        assertNotNull(mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).activeRunId());
        assertEquals("READING",jdbc.queryForObject("SELECT status FROM int_base_sync_run",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM example_business",Integer.class));
    }

    @Test void tenantAndExecutePermissionAreRequiredBeforeAnyRunIsCreated() {
        long id=service.register(config);
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.execute(new BaseSyncService.Run(id)));
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->service.execute(new BaseSyncService.Run(id)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_run",Integer.class));
        assertOriginalUnchanged();
    }

    @Test void selectedStagesSurviveRetryAndAreRecordedWithoutChangingRegistration(){
        long id=service.register(config);task.transientFailures=1;task.setRetryIntervalMillis(0);
        assertEquals(new SyncCounts(3,3,0,0,0),service.execute(new BaseSyncService.Run(id,null,List.of("Rows"))));
        assertEquals(List.of(List.of("Rows"),List.of("Rows")),task.attemptedStages);
        assertEquals("STAGES:Rows",jdbc.queryForObject("SELECT error_message FROM int_base_sync_run",String.class));
        assertEquals(config,mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).configuration());
        assertOriginalUnchanged();
    }
    @Test void invalidStageDoesNotClaimTaskOrCreateRun(){
        long id=service.register(config);
        assertThrows(IllegalArgumentException.class,()->service.preview(new BaseSyncService.Run(id,null,List.of("Unknown"))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_run",Integer.class));
        assertNull(mapper.selectTask(new BaseSyncMetadataMapper.TaskQuery(1,id,false)).activeRunId());
        assertEquals(0,task.attempts);
    }
    @Test void selectedStageFailureRollsBackAndRecordsScope(){
        long id=service.register(config);task.failLast=true;
        assertThrows(IllegalStateException.class,()->service.execute(new BaseSyncService.Run(id,null,List.of("Rows"))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM example_business",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_base_sync_binding",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT error_message FROM int_base_sync_run",String.class).startsWith("STAGES:Rows;"));
    }
    private void assertOriginalUnchanged() {
        assertEquals(List.of("original-task-definition"),jdbc.queryForList("SELECT definition FROM int_sync_task",String.class));
        assertEquals(900L,jdbc.queryForObject("SELECT active_run_id FROM int_sync_task WHERE id=100",Long.class));
        assertEquals(List.of("COMPLETED"),jdbc.queryForList("SELECT status FROM int_sync_run",String.class));
        assertEquals(List.of(999L),jdbc.queryForList("SELECT target_id FROM int_sync_binding",Long.class));
    }
}
