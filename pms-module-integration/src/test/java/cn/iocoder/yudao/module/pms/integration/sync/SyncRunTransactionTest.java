package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.system.api.organization.*;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.organization.*;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.redisson.api.*;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncRunTransactionTest {
    AnnotationConfigApplicationContext context; JdbcTemplate jdbc; SyncRunService runner; Long taskId;
    LocalDateTime upper=LocalDateTime.of(2026,9,14,10,0);
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);context=new AnnotationConfigApplicationContext(Config.class);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new cn.iocoder.yudao.framework.security.core.LoginUser().setId(1L).setTenantId(1L),null,List.of()));
        var source=context.getBean(DataSource.class);jdbc=new JdbcTemplate(source);
        var root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("sql/migrations")))root=root.getParent();
        new ResourceDatabasePopulator(new org.springframework.core.io.FileSystemResource(
                root.resolve("yudao-module-system/src/test/resources/sql/create_tables.sql"))).execute(source);
        String ddl=Files.readString(root.resolve("sql/migrations/V237__integration_data_sync_foundation.sql"))
                .replaceAll("(?i)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci","")
                .replace("COMMENT='组织外部字段归属元数据'","").replace(" json "," longtext ").replace("b'0'","FALSE")
                .replace("`target_id` bigint NOT NULL,", "`target_id` bigint NOT NULL, target_shared boolean NOT NULL DEFAULT FALSE, exclusive_target_id bigint GENERATED ALWAYS AS (CASE WHEN target_shared = FALSE THEN target_id ELSE NULL END),")
                .replace("(`tenant_id`,`task_id`,`object_key`,`target_id`)", "(`tenant_id`,`task_id`,`object_key`,`exclusive_target_id`)");
        new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))).execute(source);
        jdbc.execute("ALTER TABLE int_sync_run ADD COLUMN page_number int NULL");
        jdbc.execute("ALTER TABLE int_sync_run ADD COLUMN paging_json longtext NULL");
        var reader=context.getBean(MysqlSyncReader.class);
        when(reader.read(any(),any(),anyBoolean())).thenAnswer(i->snapshot());
        runner=context.getBean(SyncRunService.class);
        taskId=context.getBean(SyncTaskService.class).save(new SyncTaskService.Save(null,null,"测试同步",EhrSyncTemplate.create(1L)));
    }
    MysqlSyncReader.Snapshot snapshot() {
        return new MysqlSyncReader.Snapshot(upper,List.of(
                new MysqlSyncReader.SourceRows("COMPANY","ehr_company",List.of(Map.of("compID",1,"compCode","TEST001","compName","测试公司","isDisabled",0))),
                new MysqlSyncReader.SourceRows("DEPARTMENT","ehr_department",List.of())),100);
    }
    @AfterEach void close(){context.close();TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    Long run(boolean preview)throws Exception {
        Long id=runner.start(new SyncRunService.Start(taskId,context.getBean(SyncTaskService.class).required(taskId).getVersion(),UUID.randomUUID().toString(),preview,true,false,null,false));
        runner.execute(id);return id;
    }
    @Test void previewDoesNotWriteBusinessMappingOrCheckpoint()throws Exception {
        assertEquals("PREVIEW_READY",runner.required(run(true)).getStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding",Integer.class));
        assertNull(context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
    }
    void configurePaging() throws Exception {
        jdbc.execute("CREATE TABLE test_paged_item(id BIGINT PRIMARY KEY,object_key VARCHAR(20),title VARCHAR(100))");
        List<SyncDefinition.Source> sources=new ArrayList<>();
        for(String object:List.of("ORDER","LINE")) sources.add(SyncDefinition.Source.builder()
                .object(object).sourceObject("test_"+object).readMode("TABLE").table("test_"+object).sourceKey("id")
                .columns(List.of("id","label")).filters(List.of()).mappings(List.of(SyncDefinition.Mapping.builder()
                        .source("label").target("title").conversion("DIRECT").build())).build());
        var d=EhrSyncTemplate.create(1L).toBuilder().adapter("TEST_PAGED").mode("ONCE").sourceSystem("PAGING_TEST")
                .missingPolicy("RETAIN").autoPaging(true).maxRows(2).sources(sources).build();
        taskId=context.getBean(SyncTaskService.class).save(new SyncTaskService.Save(null,null,"分页功能测试",d));
        var reader=context.getBean(MysqlSyncReader.class);
        when(reader.pagingBounds(any())).thenReturn(new SyncPagingState(0,0,List.of(9L,11L),0,upper));
        when(reader.readPage(any(),any())).thenAnswer(i->{
            SyncDefinition definition=i.getArgument(0);SyncPagingState state=i.getArgument(1);
            String object=definition.sources().get(state.sourceIndex()).object();
            var ids=state.sourceIndex()==0?List.of(1L,4L,9L):List.of(2L,8L,11L);
            var rows=ids.stream().filter(id->id>state.afterId()&&id<=state.upperIds().get(state.sourceIndex()))
                    .limit(definition.maxRows()).map(id->Map.<String,Object>of("id",id,"label",object+id)).toList();
            return new MysqlSyncReader.Snapshot(upper,List.of(new MysqlSyncReader.SourceRows(object,"test_"+object,rows)),100);
        });
    }
    @Test void automaticallyPagesSparseIdsAndCompletesParentsBeforeChildren() throws Exception {
        configurePaging();Long id=run(false);
        assertEquals("SUCCESS",runner.required(id).getStatus());
        assertEquals(6,runner.required(id).getReadCount());
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM test_paged_item",Integer.class));
        assertEquals(List.of(2,1,2,1),jdbc.queryForList("SELECT read_count FROM int_sync_run WHERE parent_run_id=? ORDER BY page_number",Integer.class,id));
        assertEquals(upper,context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
        assertNull(context.getBean(SyncTaskService.class).required(taskId).getActiveRunId());
        runner.execute(id);assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_run WHERE parent_run_id=?",Integer.class,id));
        var q=new SyncQueries.Page();q.setTenantId(1L);q.setTaskId(taskId);
        assertEquals(1,context.getBean(SyncRunMapper.class).selectPage(q).getList().size());
        q.setParentRunId(id);assertEquals(4,context.getBean(SyncRunMapper.class).selectPage(q).getList().size());
        q.setTenantId(2L);assertTrue(context.getBean(SyncRunMapper.class).selectPage(q).getList().isEmpty());
    }
    @Test void frameworkNonTransactionalSynchronizationDoesNotCacheOldPageProgress() throws Exception {
        configurePaging();
        Long id=runner.start(new SyncRunService.Start(taskId,0,UUID.randomUUID().toString(),false,true,false,null,false));
        var framework=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        framework.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        framework.executeWithoutResult(status->{
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive());
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            try { runner.execute(id); }catch(Exception ex){throw new RuntimeException(ex);}
        });
        assertEquals("SUCCESS",runner.required(id).getStatus());
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM test_paged_item",Integer.class));
    }
    @Test void failedPageRollsBackAndAssociatedRetryStartsAtLastCommittedCursor() throws Exception {
        configurePaging();
        SyncEvidenceService evidence=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(context.getBean(SyncEvidenceService.class));
        var attempt=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(i->{if(!i.<Boolean>getArgument(5)&&attempt.incrementAndGet()==2)throw new IllegalStateException("page two failure");return null;})
                .when(evidence).complete(any(),any(),any(),any(),any(),anyBoolean());
        Long id=runner.start(new SyncRunService.Start(taskId,0,UUID.randomUUID().toString(),false,true,false,null,false));
        assertThrows(IllegalStateException.class,()->runner.execute(id));
        assertEquals("FAILED",runner.required(id).getStatus());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM test_paged_item",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding WHERE task_id=?",Integer.class,taskId));
        assertNull(context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
        var progress=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(runner.required(id).getPagingJson(),SyncPagingState.class);
        assertEquals(4,progress.afterId());assertEquals(1,progress.completedPages());
        doNothing().when(evidence).complete(any(),any(),any(),any(),any(),anyBoolean());
        Long retry=runner.start(new SyncRunService.Start(taskId,0,UUID.randomUUID().toString(),false,true,false,id,false));
        runner.execute(retry);
        assertEquals("SUCCESS",runner.required(retry).getStatus());
        assertEquals(4,runner.required(retry).getReadCount());
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM test_paged_item",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_run WHERE parent_run_id=?",Integer.class,retry));
    }
    @Test void pagedPreviewTraversesBothObjectsWithoutBusinessBindingsOrCheckpoint() throws Exception {
        configurePaging();Long id=run(true);
        assertEquals("PREVIEW_READY",runner.required(id).getStatus());
        assertEquals(6,runner.required(id).getReadCount());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_paged_item",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding",Integer.class));
        assertNull(context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
        assertEquals(0,context.getBean(SyncTaskService.class).required(taskId).getValidatedVersion());
    }
    @Test void pagedSnapshotPreservesScheduleAndRetryRejectsChangedConfiguration() throws Exception {
        configurePaging();var service=context.getBean(SyncTaskService.class);var t=service.required(taskId);
        var d=service.definition(t).mode("SNAPSHOT");
        service.save(new SyncTaskService.Save(taskId,t.getVersion(),"全量快照分页",d));
        run(true);
        jdbc.update("UPDATE int_sync_task SET enabled=TRUE WHERE id=?",taskId);
        Long id=run(false);
        assertTrue(service.required(taskId).getEnabled());
        assertNotNull(service.required(taskId).getNextRunAt());
        jdbc.update("UPDATE int_sync_run SET status='FAILED' WHERE id=?",id);
        t=service.required(taskId);d.maxRows(1);
        service.save(new SyncTaskService.Save(taskId,t.getVersion(),"修改页大小",d));
        assertThrows(IllegalArgumentException.class,()->runner.start(new SyncRunService.Start(taskId,
                service.required(taskId).getVersion(),UUID.randomUUID().toString(),false,true,false,id,false)));
    }
    @Test void businessMappingCheckpointAndSuccessCommitTogetherAndReplayIsNoOp()throws Exception {
        Long id=run(false);assertEquals("SUCCESS",runner.required(id).getStatus());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding",Integer.class));
        assertEquals(upper,context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
        runner.execute(id);
        run(false);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM system_company",Integer.class));
    }
    @Test void evidenceFailureRollsBackOwnerAndMappingWithoutAdvancingCheckpoint()throws Exception {
        SyncEvidenceService evidence=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(context.getBean(SyncEvidenceService.class));
        doThrow(new IllegalStateException("injected")).doNothing().when(evidence).complete(any(),any(),any(),any(),any(),anyBoolean());
        Long id=runner.start(new SyncRunService.Start(taskId,0,UUID.randomUUID().toString(),false,true,false,null,false));
        assertThrows(IllegalStateException.class,()->runner.execute(id));
        assertEquals("FAILED",runner.required(id).getStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding",Integer.class));
        assertNull(context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
    }
    @Test void runListsAndMaintenanceDoNotSortLargeEvidencePayloads()throws Exception {
        Long id=run(false);
        jdbc.update("UPDATE int_sync_run SET result_json=?,evidence_json=?,cache_pending=TRUE WHERE id=?",
                "x".repeat(512*1024),"y".repeat(512*1024),id);
        var mapper=context.getBean(SyncRunMapper.class);
        var query=new SyncQueries.Page();query.setTenantId(1L);query.setTaskId(taskId);
        var listed=mapper.selectPage(query).getList().getFirst();
        assertEquals(id,listed.getId());assertEquals("SUCCESS",listed.getStatus());
        assertNull(listed.getResultJson());assertNull(listed.getEvidenceJson());assertNull(listed.getConfigSnapshot());
        var maintenance=mapper.selectMaintenance(new SyncQueries.Due(1L,LocalDateTime.now())).getFirst();
        assertEquals(id,maintenance.getId());assertNotNull(maintenance.getConfigSnapshot());
        assertNull(maintenance.getResultJson());assertNull(maintenance.getEvidenceJson());
        assertEquals(512*1024,runner.required(id).getResultJson().length());
        query.setTenantId(2L);assertTrue(mapper.selectPage(query).getList().isEmpty());
    }
    @Test void secondAdapterUsesTheSamePreviewCommitMappingAndReplayEngine()throws Exception {
        jdbc.execute("CREATE TABLE test_sync_item(id BIGINT PRIMARY KEY,title VARCHAR(100))");
        var source=SyncDefinition.Source.builder().object("ITEM").sourceObject("legacy_item").readMode("TABLE")
                .table("legacy_item").sourceKey("item_id").columns(List.of("item_id","label")).filters(List.of())
                .mappings(List.of(SyncDefinition.Mapping.builder().source("label").target("title").conversion("TRIM").build())).build();
        var definition=EhrSyncTemplate.create(1L).toBuilder().adapter("TEST_ITEM").sourceSystem("TEST")
                .missingPolicy("RETAIN").sources(List.of(source)).build();
        taskId=context.getBean(SyncTaskService.class).save(new SyncTaskService.Save(null,null,"第二业务适配器",definition));
        when(context.getBean(MysqlSyncReader.class).read(any(),any(),anyBoolean())).thenReturn(
                new MysqlSyncReader.Snapshot(upper,List.of(new MysqlSyncReader.SourceRows("ITEM","legacy_item",
                        List.of(Map.of("item_id",42,"label"," 测试条目 ")))),100));
        assertEquals("PREVIEW_READY",runner.required(run(true)).getStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_sync_item",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding",Integer.class));
        assertNull(context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
        assertEquals("SUCCESS",runner.required(run(false)).getStatus());
        assertEquals("测试条目",jdbc.queryForObject("SELECT title FROM test_sync_item",String.class));
        var binding=context.getBean(SyncBindingMapper.class).selectTask(new SyncQueries.Task(1L,taskId)).getFirst();
        assertEquals("ITEM",binding.getObjectKey());assertEquals("42",binding.getSourceKey());
        assertEquals(500001L,binding.getTargetId());
        assertEquals(upper,context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
        assertEquals("{\"UNCHANGED\":1}",runner.required(run(false)).getSummaryJson());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_sync_item",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
    }
    @Test void equivalentLegacyRowsShareTargetAndKeepEverySourceBindingAcrossPages()throws Exception {
        jdbc.execute("CREATE TABLE test_sync_item(id BIGINT PRIMARY KEY,title VARCHAR(100))");
        var source=SyncDefinition.Source.builder().object("ITEM").sourceObject("legacy_item").readMode("TABLE")
                .table("legacy_item").sourceKey("item_id").columns(List.of("item_id","label")).filters(List.of())
                .mappings(List.of(SyncDefinition.Mapping.builder().source("label").target("title").conversion("DIRECT").build())).build();
        var definition=EhrSyncTemplate.create(1L).toBuilder().adapter("TEST_ITEM").sourceSystem("TEST")
                .missingPolicy("RETAIN").sources(List.of(source)).build();
        taskId=context.getBean(SyncTaskService.class).save(new SyncTaskService.Save(null,null,"共享目标",definition));
        for(int key:new int[]{41,42,41}) {
            when(context.getBean(MysqlSyncReader.class).read(any(),any(),anyBoolean())).thenReturn(
                    new MysqlSyncReader.Snapshot(upper,List.of(new MysqlSyncReader.SourceRows("ITEM","legacy_item",
                            List.of(Map.of("item_id",key,"label","相同条目")))),100));
            assertEquals("SUCCESS",runner.required(run(false)).getStatus());
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_sync_item",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding WHERE target_shared=TRUE",Integer.class));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()-> {
            jdbc.update("UPDATE int_sync_binding SET target_shared=FALSE");
        });
    }
    void configurePrimaryKeys(String mode) {
        var definition=EhrSyncTemplate.create(1L).loadingMode(mode);
        definition.sources().forEach(s->s.syncPrimaryKey(true));
        var service=context.getBean(SyncTaskService.class);
        service.save(new SyncTaskService.Save(taskId,service.required(taskId).getVersion(),"主键同步验收",definition));
    }
    @Test void exactPrimaryKeyPreviewCommitReplayAndPolicyFreeze()throws Exception {
        configurePrimaryKeys("UPSERT");
        long sourceId=9007199254740993L;
        when(context.getBean(MysqlSyncReader.class).read(any(),any(),anyBoolean())).thenReturn(new MysqlSyncReader.Snapshot(upper,
                List.of(new MysqlSyncReader.SourceRows("COMPANY","ehr_company",List.of(Map.of("compID",sourceId,"compCode","TEST001","compName","测试公司","isDisabled",0))),
                        new MysqlSyncReader.SourceRows("DEPARTMENT","ehr_department",List.of())),100));
        assertTrue(runner.required(run(true)).getResultJson().contains(Long.toString(sourceId)));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals("SUCCESS",runner.required(run(false)).getStatus());
        assertEquals(sourceId,jdbc.queryForObject("SELECT id FROM system_company",Long.class));
        assertEquals(sourceId,jdbc.queryForObject("SELECT target_id FROM int_sync_binding",Long.class));
        assertEquals("{\"UNCHANGED\":1}",runner.required(run(false)).getSummaryJson());
        var service=context.getBean(SyncTaskService.class);
        assertDoesNotThrow(()->service.save(new SyncTaskService.Save(taskId,service.required(taskId).getVersion(),"关闭主键传输仍保留原ID",EhrSyncTemplate.create(1L))));
    }
    @Test void skipPreservesBusinessAndBindingThenUpsertUpdatesSameId()throws Exception {
        configurePrimaryKeys("UPSERT");run(false);
        configurePrimaryKeys("INSERT_IGNORE");
        jdbc.update("UPDATE system_company SET name='人工名称' WHERE id=1");
        String binding=jdbc.queryForObject("SELECT fields_json FROM int_sync_binding",String.class);
        assertEquals("{\"SKIPPED\":1}",runner.required(run(false)).getSummaryJson());
        assertEquals("人工名称",jdbc.queryForObject("SELECT name FROM system_company",String.class));
        assertEquals(binding,jdbc.queryForObject("SELECT fields_json FROM int_sync_binding",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM system_company",Integer.class));
        configurePrimaryKeys("UPSERT");
        assertEquals("{\"UPDATED\":1}",runner.required(run(false)).getSummaryJson());
        assertEquals("测试公司",jdbc.queryForObject("SELECT name FROM system_company",String.class));
        assertEquals(1L,jdbc.queryForObject("SELECT id FROM system_company",Long.class));
    }
    @Test void appendRejectsExistingAndDoesNotCommitNewRows()throws Exception {
        configurePrimaryKeys("INSERT_ONLY");run(false);
        var oldCheckpoint=context.getBean(SyncTaskService.class).required(taskId).getCheckpoint();
        when(context.getBean(MysqlSyncReader.class).read(any(),any(),anyBoolean())).thenReturn(new MysqlSyncReader.Snapshot(upper.plusHours(1),
                List.of(new MysqlSyncReader.SourceRows("COMPANY","ehr_company",List.of(
                        Map.of("compID",1,"compCode","TEST001","compName","测试公司","isDisabled",0),
                        Map.of("compID",2,"compCode","TEST002","compName","第二公司","isDisabled",0))),
                        new MysqlSyncReader.SourceRows("DEPARTMENT","ehr_department",List.of())),200));
        assertThrows(IllegalArgumentException.class,()->run(false));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding",Integer.class));
        assertEquals(oldCheckpoint,context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
    }
    @Test void invalidOrMismatchedSourcePrimaryKeysAreRejected() {
        for(String value:List.of("0","-1","01","1.0","9223372036854775808","uuid"))
            assertThrows(IllegalArgumentException.class,()->SyncFieldMapper.primaryKey(value));
        var definition=EhrSyncTemplate.create(1L);definition.sources().forEach(s->s.syncPrimaryKey(true));
        assertThrows(IllegalArgumentException.class,()->new SyncFieldMapper().transform(definition,snapshot(),
                List.of(new DataSyncAdapter.Binding("COMPANY","1",100L,Map.of()))));
    }
    @Test void duplicateTaskPreflightPointsToExistingTaskAndSaveReportsConflict() {
        var service=context.getBean(SyncTaskService.class);
        var request=new SyncTaskService.Save(null,null,"改名字也不是新来源",EhrSyncTemplate.create(1L));
        var check=service.checkConfiguration(request);
        assertFalse(check.allowed());assertEquals(taskId,check.existingTaskId());
        assertThrows(SyncTaskConflictException.class,()->service.save(request));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_task",Integer.class));
    }
    void configureClear() {
        var service=context.getBean(SyncTaskService.class);
        var definition=EhrSyncTemplate.create(1L).clearBeforeLoad(true);
        definition.sources().forEach(source->source.syncPrimaryKey(true));
        service.save(new SyncTaskService.Save(taskId,service.required(taskId).getVersion(),"原任务清空重建",definition));
    }
    Long startClear() {
        return runner.start(new SyncRunService.Start(taskId,context.getBean(SyncTaskService.class).required(taskId).getVersion(),
                UUID.randomUUID().toString(),false,true,false,null,true));
    }
    @Test void sameTaskCanRebuildMismatchedIdsOnlyAfterPreviewAndExplicitConfirmation()throws Exception {
        run(false);long oldId=jdbc.queryForObject("SELECT id FROM system_company",Long.class);
        configureClear();
        assertThrows(IllegalArgumentException.class,this::startClear);
        var preview=runner.required(run(true));assertTrue(preview.getSummaryJson().contains("CLEARED"));
        assertEquals(oldId,jdbc.queryForObject("SELECT id FROM system_company",Long.class));
        assertThrows(IllegalArgumentException.class,()->run(false));
        var taskService=context.getBean(SyncTaskService.class);
        assertThrows(IllegalArgumentException.class,()->taskService.schedule(taskId,taskService.required(taskId).getVersion(),true));
        Long id=startClear();runner.execute(id);
        assertEquals("SUCCESS",runner.required(id).getStatus());
        assertEquals(1L,jdbc.queryForObject("SELECT id FROM system_company",Long.class));
        assertEquals(1L,jdbc.queryForObject("SELECT target_id FROM int_sync_binding",Long.class));
        assertTrue(runner.required(id).getSummaryJson().contains("CLEARED"));
    }
    @Test void replacementFailureRestoresOriginalRowsMappingsAndCheckpoint()throws Exception {
        run(false);long oldId=jdbc.queryForObject("SELECT id FROM system_company",Long.class);
        String mapping=jdbc.queryForObject("SELECT fields_json FROM int_sync_binding",String.class);
        configureClear();run(true);
        SyncEvidenceService evidence=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(context.getBean(SyncEvidenceService.class));
        doThrow(new IllegalStateException("提交证据失败")).doNothing().when(evidence).complete(any(),any(),any(),any(),any(),anyBoolean());
        Long id=startClear();assertThrows(IllegalStateException.class,()->runner.execute(id));
        assertEquals("FAILED",runner.required(id).getStatus());
        assertEquals(oldId,jdbc.queryForObject("SELECT id FROM system_company",Long.class));
        assertEquals(oldId,jdbc.queryForObject("SELECT target_id FROM int_sync_binding",Long.class));
        assertEquals(mapping,jdbc.queryForObject("SELECT fields_json FROM int_sync_binding",String.class));
        assertEquals(upper,context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
    }
    @Test void replacementInvalidatesOtherTasksMappingsAndSchedule()throws Exception {
        run(false);
        var service=context.getBean(SyncTaskService.class);
        var other=service.save(new SyncTaskService.Save(null,null,"同目标另一来源",EhrSyncTemplate.create(1L).sourceSystem("OTHER")));
        jdbc.update("UPDATE int_sync_task SET enabled=TRUE,checkpoint=?,validated_version=version WHERE id=?",upper,other);
        jdbc.update("INSERT INTO int_sync_binding(id,tenant_id,task_id,object_key,source_object,source_key,target_id,fields_json,last_run_id) VALUES(900001,1,?,'COMPANY','ehr_company','2',2,'{}',1)",other);
        configureClear();run(true);Long id=startClear();runner.execute(id);
        var invalidated=service.required(other);
        assertFalse(invalidated.getEnabled());assertNull(invalidated.getCheckpoint());assertNull(invalidated.getValidatedVersion());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding WHERE task_id=?",Integer.class,other));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding WHERE task_id=?",Integer.class,taskId));
    }
    @Test void emptyReplacementSnapshotNeverClearsExistingTargets()throws Exception {
        run(false);configureClear();
        when(context.getBean(MysqlSyncReader.class).read(any(),any(),anyBoolean())).thenReturn(new MysqlSyncReader.Snapshot(upper,
                List.of(new MysqlSyncReader.SourceRows("COMPANY","ehr_company",List.of()),new MysqlSyncReader.SourceRows("DEPARTMENT","ehr_department",List.of())),0));
        assertThrows(IllegalArgumentException.class,()->run(true));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding",Integer.class));
    }
    void configureMappingReset() {
        var service=context.getBean(SyncTaskService.class);
        var definition=EhrSyncTemplate.create(1L).resetMappingsBeforeLoad(true);
        definition.sources().forEach(source->source.syncPrimaryKey(true));
        service.save(new SyncTaskService.Save(taskId,service.required(taskId).getVersion(),"保留目标仅重置映射",definition));
    }
    @Test void mappingResetUpsertsBySourceIdWithoutClearingTargetsOrOtherTaskMappings()throws Exception {
        configurePrimaryKeys("UPSERT");run(false);
        jdbc.update("UPDATE system_company SET name='人工名称' WHERE id=1");
        jdbc.update("INSERT INTO system_dept(id,tenant_id,name,phone,parent_id,status) VALUES(99,1,'本地部门','local-phone',0,0)");
        jdbc.update("INSERT INTO system_company(id,tenant_id,code,name,status,version) VALUES(99,1,'LOCAL','本地公司',0,0)");
        jdbc.update("INSERT INTO int_sync_binding(id,tenant_id,task_id,object_key,source_object,source_key,target_id,fields_json,last_run_id) VALUES(900001,1,?,'COMPANY','ehr_company','99',99,'{}',1),(900002,2,?,'COMPANY','ehr_company','99',99,'{}',1)",taskId,taskId);
        var service=context.getBean(SyncTaskService.class);
        var other=service.save(new SyncTaskService.Save(null,null,"其他任务",EhrSyncTemplate.create(1L).sourceSystem("OTHER")));
        jdbc.update("INSERT INTO int_sync_binding(id,tenant_id,task_id,object_key,source_object,source_key,target_id,fields_json,last_run_id) VALUES(900003,1,?,'COMPANY','ehr_company','99',99,'{}',1)",other);
        when(context.getBean(MysqlSyncReader.class).read(any(),any(),anyBoolean())).thenReturn(new MysqlSyncReader.Snapshot(upper,
                List.of(new MysqlSyncReader.SourceRows("COMPANY","ehr_company",List.of(
                        Map.of("compID",1,"compCode","TEST001","compName","测试公司","isDisabled",0),
                        Map.of("compID",2,"compCode","TEST002","compName","新增公司","isDisabled",0))),
                        new MysqlSyncReader.SourceRows("DEPARTMENT","ehr_department",List.of())),200));
        configureMappingReset();
        assertThrows(IllegalArgumentException.class,this::startClear);
        var preview=runner.required(run(true));assertFalse(preview.getSummaryJson().contains("CLEARED"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM system_company",Integer.class));
        assertThrows(IllegalArgumentException.class,()->run(false));
        Long id=startClear();runner.execute(id);assertEquals("SUCCESS",runner.required(id).getStatus());
        assertEquals(List.of(1L,2L,99L),jdbc.queryForList("SELECT id FROM system_company ORDER BY id",Long.class));
        assertEquals("测试公司",jdbc.queryForObject("SELECT name FROM system_company WHERE id=1",String.class));
        assertEquals("local-phone",jdbc.queryForObject("SELECT phone FROM system_dept WHERE id=99",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT status FROM system_company WHERE id=99",Integer.class));
        assertEquals(List.of(1L,2L),jdbc.queryForList("SELECT target_id FROM int_sync_binding WHERE tenant_id=1 AND task_id=? ORDER BY target_id",Long.class,taskId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding WHERE tenant_id=2",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM int_sync_binding WHERE task_id=?",Integer.class,other));
    }
    @Test void mappingResetStillRejectsSameCodeWithDifferentPrimaryKeyAndKeepsOldMapping()throws Exception {
        run(false);long oldId=jdbc.queryForObject("SELECT id FROM system_company",Long.class);
        configureMappingReset();assertThrows(IllegalArgumentException.class,()->run(true));
        assertEquals(oldId,jdbc.queryForObject("SELECT id FROM system_company",Long.class));
        assertEquals(oldId,jdbc.queryForObject("SELECT target_id FROM int_sync_binding",Long.class));
    }
    @Test void mappingResetWriteFailureRollsBackMappingRemovalAndBusinessUpdate()throws Exception {
        configurePrimaryKeys("UPSERT");run(false);configureMappingReset();run(true);
        jdbc.update("UPDATE system_company SET name='人工名称' WHERE id=1");
        long bindingId=jdbc.queryForObject("SELECT id FROM int_sync_binding",Long.class);
        SyncEvidenceService evidence=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(context.getBean(SyncEvidenceService.class));
        doThrow(new IllegalStateException("提交失败")).doNothing().when(evidence).complete(any(),any(),any(),any(),any(),anyBoolean());
        Long id=startClear();assertThrows(IllegalStateException.class,()->runner.execute(id));
        assertEquals(bindingId,jdbc.queryForObject("SELECT id FROM int_sync_binding",Long.class));
        assertEquals("人工名称",jdbc.queryForObject("SELECT name FROM system_company",String.class));
        assertEquals(upper,context.getBean(SyncTaskService.class).required(taskId).getCheckpoint());
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true)
    @Import({SyncRunService.class,SyncTaskService.class,SyncDefinitionValidator.class,SyncFieldMapper.class,EhrOrganizationAdapter.class,ManagedOrganizationApiImpl.class})
    @MapperScan(basePackageClasses={SyncTaskMapper.class,CompanyMapper.class,DeptMapper.class,ManagedOrganizationMapper.class})
    static class Config {
        @Bean cn.hutool.extra.spring.SpringUtil springUtil(){return new cn.hutool.extra.spring.SpringUtil();}
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:sync_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");}
        @Bean DataSyncAdapter pagedAdapter(DataSource dataSource) {
            var jdbc=new JdbcTemplate(dataSource);
            return new DataSyncAdapter() {
                public Descriptor descriptor() {return new Descriptor("TEST_PAGED","分页测试",List.of("ORDER","LINE").stream()
                        .map(o->new ObjectDescriptor(o,o,List.of(new Field("title","标题","STRING",true)),"TEST",o,"test_paged_item",false)).toList(),
                        List.of("RETAIN"),List.of("UPSERT"),false);}
                public boolean requiresAllBindings(){return false;}
                public List<Change> preview(Batch batch){return batch.rows().stream().map(r->new Change(r.object(),r.sourceKey(),
                        Long.parseLong(r.sourceKey())+("LINE".equals(r.object())?100:0),"CREATED",Map.of(),r.fields(),null)).toList();}
                public List<Change> apply(Batch batch){
                    if(batch.rows().stream().anyMatch(r->"LINE".equals(r.object())))
                        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM test_paged_item WHERE object_key='ORDER'",Integer.class));
                    var changes=preview(batch);
                    for(var c:changes)jdbc.update("INSERT INTO test_paged_item VALUES (?,?,?)",c.targetId(),c.object(),c.after().get("title"));
                    return changes;
                }
                public void refreshCaches(){}
            };
        }
        @Bean PlatformTransactionManager transactionManager(DataSource d){return new DataSourceTransactionManager(d);}
        @Bean CacheManager cacheManager(){return new ConcurrentMapCacheManager();}
        @Bean DataSyncAdapter testItemAdapter(DataSource dataSource) {
            var owner=new JdbcTemplate(dataSource);
            return new DataSyncAdapter() {
                public Descriptor descriptor() {
                    return new Descriptor("TEST_ITEM","测试条目",List.of(new ObjectDescriptor("ITEM","条目",
                            List.of(new Field("title","标题","STRING",true)),"TEST","Item","test_sync_item",false)),List.of("RETAIN"),List.of("UPSERT"),false);
                }
                public List<Change> preview(Batch batch) {
                    return batch.rows().stream().map(row -> new Change(row.object(),row.sourceKey(),500001L,
                            batch.bindings().isEmpty()?"CREATED":"UNCHANGED",Map.of(),row.fields(),null)).toList();
                }
                public List<Change> apply(Batch batch) {
                    assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                    var changes=preview(batch);
                    changes.stream().filter(change -> "CREATED".equals(change.action())).forEach(change ->
                            owner.update("INSERT INTO test_sync_item(id,title) VALUES (?,?)",change.targetId(),change.after().get("title")));
                    return changes;
                }
                public void refreshCaches() {}
                public boolean sharesTargetAcrossSources() { return true; }
            };
        }
        @Bean SyncBatchLauncher launcher(){return mock(SyncBatchLauncher.class);}
        @Bean SyncEvidenceService evidence(){var e=mock(SyncEvidenceService.class);when(e.stage(any(),any(),any())).thenReturn(List.of());return e;}
        @Bean MysqlSyncReader reader(){return mock(MysqlSyncReader.class);}
        @Bean SyncConnectionService connections(){var c=mock(SyncConnectionService.class);when(c.resolve(any())).thenAnswer(i->i.getArgument(0));return c;}
        @Bean RedissonClient redisson()throws Exception{
            var r=mock(RedissonClient.class);var lock=mock(RLock.class);
            when(r.getLock(anyString())).thenReturn(lock);when(lock.tryLock(anyLong(),any(TimeUnit.class))).thenReturn(true);
            when(lock.isHeldByCurrentThread()).thenReturn(true);return r;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource d)throws Exception {
            var f=new MybatisSqlSessionFactoryBean();f.setDataSource(d);
            var global=new GlobalConfig();global.setMetaObjectHandler(new DefaultDBFieldHandler());f.setGlobalConfig(global);
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);f.setConfiguration(config);
            var resolver=new PathMatchingResourcePatternResolver();
            var resources=new ArrayList<org.springframework.core.io.Resource>();
            resources.addAll(List.of(resolver.getResources("classpath*:mapper/organization/*.xml")));
            resources.addAll(List.of(resolver.getResources("classpath*:mapper/sync/*.xml")));
            f.setMapperLocations(resources.toArray(org.springframework.core.io.Resource[]::new));return f.getObject();
        }
    }
}
