package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real XML proves that a simple task inherits query, tenant delete and one INSERT per batch. */
class BaseSyncDefaultsTest {
    @lombok.Data
    public static class Row implements SyncReferences.SourceAudit {
        private Long id;
        private String sourceCreator;
        private String sourceUpdater;
        private String resolvedCreator;
        private String resolvedUpdater;
        private SyncWriteAction syncAction;
    }
    static class Task extends DefaultSyncTask<Row,Void> {
        interface SourceMapper {}
        interface TargetMapper {}
        boolean after;
        boolean rejectRows;
        boolean expand;
        String preparation;
        protected String preparationStatement(SyncContext<Void> context){return preparation==null?null:targetMapperNamespace()+"."+preparation;}
        protected SyncWriteAction syncDataAction(Row row){return preparation==null?super.syncDataAction(row):row.getSyncAction();}
        int beforeCalls,afterCalls;
        record Selection(long tenantId,long afterId,int limit) {}
        record Correction(long tenantId,String creator) {}
        public String taskCode(){return "Rows";}
        public String taskName(){return "default XML workflow";}
        protected void syncDataValidate(SyncBatch<Row,Void> batch){if(rejectRows)throw new IllegalArgumentException("invalid source");}
        protected void syncDataInsertBefore(SyncBatch<Row,Void> batch){
            beforeCalls++;
            for(var row:batch.rows())assertNotNull(row.getResolvedCreator(),"common references must precede specialized prefill");
            super.syncDataInsertBefore(batch);
            if(expand){var added=new Row();added.setId(batch.rows().getFirst().getId()+100);added.setResolvedCreator("99");batch.rows().add(added);}
        }
        protected void syncDataInsertAfter(SyncBatch<Row,Void> batch){after=true;afterCalls++;}
        List<Row> select(SyncContext<Void> context,Selection query){
            return syncDataQueryBySql(new MappedCall<>(context,sourceMapperNamespace()+".selectedRows",query));
        }
        List<Row> selectWithNullHandler(SyncContext<Void> context,Selection query){
            return syncDataQueryBySql(new MappedCall<>(context,sourceMapperNamespace()+".selectedRows",query),null);
        }
        void correct(SyncContext<Void> context,String creator){
            syncDataUpdateBySql(new MappedCall<>(context,targetMapperNamespace()+".correctCreator",new Correction(context.tenantId(),creator)));
        }
        void clearMissing(SyncContext<Void> context){
            syncDataClearBySql(new MappedCall<>(context,targetMapperNamespace()+".missingDelete",new ClearQuery<>(context.tenantId(),context.preview(),null)));
        }
    }
    JdbcTemplate sourceJdbc,targetJdbc;
    SqlSession sourceSql,targetSql;
    SyncSource source;
    SyncSource.Session external;
    SyncReferences references;
    TransactionTemplate transaction;
    Task task;

    @BeforeEach void setup() {
        var sourceData=new DriverManagerDataSource("jdbc:h2:mem:default-source-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var targetData=new DriverManagerDataSource("jdbc:h2:mem:default-target-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        sourceJdbc=new JdbcTemplate(sourceData);targetJdbc=new JdbcTemplate(targetData);
        sourceJdbc.execute("CREATE TABLE source_row(id BIGINT PRIMARY KEY,author VARCHAR(64))");
        sourceJdbc.update("INSERT INTO source_row VALUES(1,'author'),(2,'author'),(3,'author')");
        targetJdbc.execute("CREATE TABLE target_row(id BIGINT,tenant_id BIGINT,creator VARCHAR(64),PRIMARY KEY(tenant_id,id))");
        targetJdbc.update("INSERT INTO target_row VALUES(90,1,'previous'),(99,2,'other tenant')");
        sourceSql=session(sourceData,"""
            <mapper namespace="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Task$SourceMapper">
              <select id="selectRows" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT id,author AS source_creator FROM source_row ORDER BY id LIMIT #{limit}
              </select>
              <select id="selectOther" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT id+100 AS id,author AS source_creator FROM source_row ORDER BY id LIMIT #{limit}
              </select>
              <select id="selectRowsWithPaging" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT id,author AS source_creator FROM source_row ORDER BY id LIMIT #{limit} OFFSET #{offset}
              </select>
              <select id="selectedRows" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT id,author AS source_creator FROM source_row WHERE id &gt; #{afterId} ORDER BY id LIMIT #{limit}
              </select>
              <sql id="incrementRows">SELECT id,author AS source_creator FROM source_row
                WHERE id &gt; #{parameters.afterId} AND id &lt;= #{parameters.maxId} ORDER BY id LIMIT #{limit}</sql>
              <select id="selectIncrement" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row"><include refid="incrementRows"/></select>
              <select id="selectIncrementWithPaging" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row"><include refid="incrementRows"/> OFFSET #{offset}</select>
              <select id="chooseIncrement" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT id,author AS source_creator FROM source_row WHERE id=#{parameters.maxId} ORDER BY id LIMIT #{limit}
              </select>
              <select id="chooseIncrementPage" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT id,author AS source_creator FROM source_row WHERE id=#{parameters.maxId} ORDER BY id LIMIT #{limit} OFFSET #{offset}
              </select>
            </mapper>
            """);
        targetSql=session(targetData,"""
            <mapper namespace="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Task$TargetMapper">
              <select id="countRows" resultType="long">SELECT COUNT(*) FROM target_row WHERE tenant_id=#{tenantId}</select>
              <delete id="deleteRows">DELETE FROM target_row WHERE tenant_id=#{tenantId}</delete>
              <insert id="insertRows">INSERT INTO target_row VALUES
                <foreach collection="rows" item="row" separator=",">(#{row.id},#{context.tenantId},#{row.resolvedCreator})</foreach>
              </insert>
              <select id="prepareRows" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT s.id,s.creator AS resolved_creator,
                  CASE WHEN p.id IS NULL THEN 'INSERT' WHEN p.creator=s.creator THEN 'UNCHANGED' ELSE 'UPDATE' END AS sync_action
                FROM (<foreach collection="rows" item="row" separator=" UNION ALL ">
                  SELECT CAST(#{row.id} AS BIGINT) AS id,CAST(#{row.resolvedCreator} AS VARCHAR) AS creator
                </foreach>) s LEFT JOIN target_row p ON p.id=s.id AND p.tenant_id=#{context.tenantId}
                ORDER BY s.id
              </select>
              <select id="missingPreparedRow" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT id FROM target_row WHERE 1=0
              </select>
              <select id="invalidAction" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT id FROM target_row WHERE tenant_id=#{context.tenantId} AND id=1
              </select>
              <update id="updateRows">
                UPDATE target_row SET creator=#{row.resolvedCreator} WHERE tenant_id=#{context.tenantId} AND id=#{row.id} AND creator='replaceable'
              </update>
              <insert id="insertOther">INSERT INTO target_row VALUES
                <foreach collection="rows" item="row" separator=",">(#{row.id},#{context.tenantId},#{row.resolvedCreator})</foreach>
              </insert>
              <select id="selectLocal" resultType="cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncDefaultsTest$Row">
                SELECT id+100 AS id,creator AS source_creator FROM target_row WHERE tenant_id=#{tenantId} ORDER BY id LIMIT #{limit}
              </select>
              <insert id="insertLocal">INSERT INTO target_row VALUES
                <foreach collection="rows" item="row" separator=",">(#{row.id},#{context.tenantId},#{row.resolvedCreator})</foreach>
              </insert>
              <update id="correctCreator">UPDATE target_row SET creator=#{creator} WHERE tenant_id=#{tenantId}</update>
              <sql id="incrementScope">tenant_id=#{tenantId} AND id &gt; #{parameters.afterId} AND id &lt;= #{parameters.maxId}</sql>
              <select id="readReference" resultType="string">SELECT creator FROM target_row WHERE tenant_id=#{tenantId} AND id=#{parameters.referenceId}</select>
              <select id="countIncrementByParams" resultType="long">SELECT COUNT(*) FROM target_row WHERE <include refid="incrementScope"/></select>
              <delete id="deleteIncrementByParams">DELETE FROM target_row WHERE <include refid="incrementScope"/></delete>
              <insert id="insertIncrement">INSERT INTO target_row VALUES
                <foreach collection="rows" item="row" separator=",">(#{row.id},#{context.tenantId},CONCAT(#{context.parameters.prefix},#{row.resolvedCreator}))</foreach>
              </insert>
              <update id="fillIncrement">UPDATE target_row SET creator=#{parameters.finalCreator} WHERE <include refid="incrementScope"/></update>
              <select id="countChosen" resultType="long">SELECT COUNT(*) FROM target_row WHERE <include refid="incrementScope"/></select>
              <delete id="deleteChosen">DELETE FROM target_row WHERE <include refid="incrementScope"/></delete>
              <insert id="storeChosen">INSERT INTO target_row VALUES
                <foreach collection="rows" item="row" separator=",">(#{row.id},#{context.tenantId},CONCAT('custom:',#{context.parameters.prefix},#{row.resolvedCreator}))</foreach>
              </insert>
            </mapper>
            """);
        source=mock(SyncSource.class);external=mock(SyncSource.Session.class);
        when(source.open(3)).thenReturn(external);when(external.sqlSession()).thenReturn(sourceSql);
        references=mock(SyncReferences.class);
        when(references.findAuditUsers(any())).thenAnswer(call->{
            var query=call.getArgument(0,SyncReferences.AuditUsersQuery.class);
            assertEquals(1,query.tenantId());
            return query.usernames().stream().map(name->new SyncReferences.AuditUser(99,name)).toList();
        });
        transaction=new TransactionTemplate(new DataSourceTransactionManager(targetData));
        task=new Task();
    }
    SqlSession session(javax.sql.DataSource data,String xml) {
        var configuration=new Configuration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("test",new SpringManagedTransactionFactory(),data));
        String header="<?xml version=\"1.0\"?><!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"https://mybatis.org/dtd/mybatis-3-mapper.dtd\">";
        new XMLMapperBuilder(new StringReader(header+xml),configuration,UUID.randomUUID().toString(),configuration.getSqlFragments()).parse();
        return new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
    }
    SyncContext<Void> context(boolean preview){
        return new SyncContext<Void>(1,2,3,preview,2,1000,LocalDateTime.of(2026,10,17,12,0),source,
                mock(SyncIdentityStore.class),references,"Rows",targetSql,false,true);
    }
    SyncCounts run(SyncContext<Void> context){return transaction.execute(status->task.execute(context));}

    @Test void preparedActionsAutomaticallyInsertUpdateOrSkipAndReplay(){
        targetJdbc.update("INSERT INTO target_row VALUES(2,1,'replaceable'),(3,1,'author')");
        task.preparation="prepareRows";
        assertEquals(new SyncCounts(3,1,1,1,0),run(context(true)));
        assertEquals("replaceable",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=1 AND id=2",String.class));
        assertEquals(new SyncCounts(3,1,1,1,0),run(context(false)));
        assertEquals(new SyncCounts(3,0,0,3,0),run(context(false)));
        assertEquals("other tenant",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=2",String.class));
    }
    @Test void preparedConditionalUpdateFailureRollsBackPriorInsert(){
        targetJdbc.update("INSERT INTO target_row VALUES(3,1,'protected')");
        task.preparation="prepareRows";
        assertThrows(IllegalStateException.class,()->run(context(false)));
        assertEquals(List.of(3L,90L),targetJdbc.queryForList("SELECT id FROM target_row WHERE tenant_id=1 ORDER BY id",Long.class));
        assertEquals("protected",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=1 AND id=3",String.class));
    }
    @Test void preparationCannotSilentlyLoseRowsOrIgnoreMissingStatement(){
        task.preparation="missingPreparedRow";
        assertTrue(assertThrows(IllegalStateException.class,()->run(context(false))).getMessage().contains("每条数据"));
        task.preparation="absentPreparation";
        assertThrows(RuntimeException.class,()->run(context(false)));
        assertEquals(2,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row",Integer.class));
    }
    @Test void nullPreparedActionFailsBeforeAnyWrite(){
        sourceJdbc.update("DELETE FROM source_row WHERE id<>1");
        targetJdbc.update("INSERT INTO target_row VALUES(1,1,'unchanged')");
        task.preparation="invalidAction";
        assertThrows(NullPointerException.class,()->run(context(false)));
        assertEquals(3,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row",Integer.class));
    }
    @Test void fullReadPreparationStillUsesBoundedQueries(){
        task.preparation="prepareRows";
        targetSql=mock(SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(targetSql));
        assertEquals(new SyncCounts(3,3,0,0,0),run(context(false).forData("Rows",false,false)));
        var queries=org.mockito.ArgumentCaptor.forClass(SyncBatch.class);
        verify(targetSql,times(2)).selectList(endsWith(".prepareRows"),queries.capture());
        assertEquals(List.of(2,1),queries.getAllValues().stream().map(batch->batch.rows().size()).toList());
        assertEquals(1,task.beforeCalls);assertEquals(1,task.afterCalls);
    }
    @Test void defaultQueryClearAndBatchInsertNeedNoTaskOverrides(){
        targetSql=mock(SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(targetSql));
        assertEquals(new SyncCounts(3,3,0,0,1),run(context(false).forData("Rows",true,true)));
        assertEquals(List.of(1L,2L,3L),targetJdbc.queryForList("SELECT id FROM target_row WHERE tenant_id=1 ORDER BY id",Long.class));
        assertEquals("other tenant",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=2",String.class));
        verify(targetSql,times(2)).insert(anyString(),any(SyncBatch.class));
        assertEquals(3,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row WHERE creator='author'",Integer.class));
        verify(external).close();assertTrue(task.after);
    }
    @Test void previewUsesDefaultReadAndClearCountWithoutWriting(){
        assertEquals(new SyncCounts(3,3,0,0,1),run(context(true).forData("Rows",true,true)));
        assertEquals(2,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row",Integer.class));
        assertFalse(task.after);
    }
    @Test void manyAuditNamesArePreservedWithoutAccountLookups(){
        sourceJdbc.update("DELETE FROM source_row");
        for(int i=1;i<=401;i++)sourceJdbc.update("INSERT INTO source_row VALUES(?,?)",i,"user-"+i);
        var original=context(true);
        var context=new SyncContext<Void>(1,2,3,true,500,1000,original.startedAt(),source,original.identities(),references,"Rows",targetSql,false,true);
        assertEquals(401,run(context).inserted());
        verifyNoInteractions(references);
    }
    @Test void auditNamePreservationDoesNotConsumeOrTrustUnrelatedAccounts(){
        doReturn(List.of()).when(references).findAuditUsers(any());run(context(false));
        assertEquals(3,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row WHERE creator='author'",Integer.class));
        doReturn(List.of(new SyncReferences.AuditUser(99,"different-user"))).when(references).findAuditUsers(any());
        assertEquals(new SyncCounts(3,3,0,0,4),run(context(false).forData("Rows",true,true)));
        assertEquals(3,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row WHERE creator='author'",Integer.class));
        assertEquals("other tenant",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=2",String.class));
        verifyNoInteractions(references);
    }
    @Test void failedDefaultBatchRollsBackEarlierTenantClearAndSkipsAfterHook(){
        var actual=targetSql;
        targetSql=mock(SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(actual));
        doReturn(0).when(targetSql).insert(anyString(),any());
        assertThrows(IllegalStateException.class,()->run(context(false).forData("Rows",true,true)));
        assertEquals("previous",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=1",String.class));
        assertFalse(task.after);
    }
    @Test void defaultQueryClosesSourceOnReadFailure(){
        sourceJdbc.execute("DROP TABLE source_row");
        assertThrows(RuntimeException.class,()->run(context(false)));
        verify(external).close();assertFalse(task.after);
    }
    @Test void fullReadValidationRunsBeforeRequestedClear(){
        task.rejectRows=true;
        assertThrows(IllegalArgumentException.class,()->run(context(false).forData("Rows",true,false)));
        assertEquals("previous",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=1",String.class));
    }
    @Test void multipleCallsIndependentlyChooseClearAndReadModes(){
        task=new Task(){
            protected String sourceMapperNamespace(){return Task.class.getName()+"$SourceMapper";}
            protected String targetMapperNamespace(){return Task.class.getName()+"$TargetMapper";}
            public void work(SyncContext<Void> context){
                syncData(context.forData("Rows",true,false));syncData(context.forData("Other",false,true));
            }
        };
        assertEquals(new SyncCounts(6,6,0,0,1),run(context(false)));
        assertEquals(List.of(1L,2L,3L,101L,102L,103L),targetJdbc.queryForList("SELECT id FROM target_row WHERE tenant_id=1 ORDER BY id",Long.class));
    }
    @Test void secondSyncDataFailureRollsBackTheEarlierPhase(){
        task=new Task(){
            protected String sourceMapperNamespace(){return Task.class.getName()+"$SourceMapper";}
            protected String targetMapperNamespace(){return Task.class.getName()+"$TargetMapper";}
            public void work(SyncContext<Void> context){
                syncData(context.forData("Rows",true,true));
                syncData(context.forData("Rows",false,false));
            }
        };
        assertThrows(RuntimeException.class,()->run(context(false)));
        assertEquals("previous",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=1",String.class));
        assertEquals(2,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row",Integer.class));
    }

    Task pagingTask(){return new Task(){
        protected String sourceMapperNamespace(){return Task.class.getName()+"$SourceMapper";}
        protected String targetMapperNamespace(){return Task.class.getName()+"$TargetMapper";}
        public void work(SyncContext<Void> context){syncDataWithPaging(context);}
    };}
    @Test void pagingUsesOneSessionAndClearsOnceIncludingTail(){
        task=pagingTask();targetSql=mock(SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(targetSql));
        assertEquals(new SyncCounts(3,3,0,0,1),run(context(false).forData("Rows",true,true)));
        verify(source).open(3);verify(external).close();verify(targetSql).delete(anyString(),any());
        verify(targetSql,times(2)).insert(anyString(),any());
        assertEquals(2,task.beforeCalls);assertEquals(2,task.afterCalls);
    }
    @Test void emptyPagedSnapshotStillClearsOnlyItsTenant(){
        sourceJdbc.update("DELETE FROM source_row");task=pagingTask();
        assertEquals(new SyncCounts(0,0,0,0,1),run(context(false).forData("Rows",true,true)));
        assertEquals(List.of(99L),targetJdbc.queryForList("SELECT id FROM target_row",Long.class));
        assertEquals(0,task.beforeCalls);verify(external).close();
    }
    @Test void pagingLimitProbeRollsBackEarlierPagesAndClear(){
        task=pagingTask();var original=context(false);
        var limited=new SyncContext<Void>(1,2,3,false,2,2,original.startedAt(),source,original.identities(),references,"Rows",targetSql,true,true);
        assertThrows(IllegalArgumentException.class,()->run(limited));
        assertEquals("previous",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=1",String.class));
        verify(external).close();
    }
    @Test void batchExpansionIsSplitAfterPrefillAndAfterHookRunsOncePerInputBatch(){
        task.expand=true;targetSql=mock(SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(targetSql));
        assertEquals(new SyncCounts(3,5,0,0,0),run(context(false)));
        var batches=org.mockito.ArgumentCaptor.forClass(SyncBatch.class);
        verify(targetSql,times(3)).insert(anyString(),batches.capture());
        assertEquals(List.of(2,1,2),batches.getAllValues().stream().map(batch->batch.rows().size()).toList());
        assertEquals(2,task.beforeCalls);assertEquals(2,task.afterCalls);
    }
    @Test void fullReadPrefillSeesWholeListBeforeInsertSplitting(){
        task.expand=true;targetSql=mock(SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(targetSql));
        assertEquals(new SyncCounts(3,4,0,0,0),run(context(false).forData("Rows",false,false)));
        assertEquals(1,task.beforeCalls);assertEquals(1,task.afterCalls);
        verify(targetSql,times(2)).insert(anyString(),any());
    }
    @Test void customMappedQueryUsesSceneParametersAndSelectedConnection(){
        when(source.open(4)).thenReturn(external);
        var rows=task.select(context(true).forSource(4),new Task.Selection(1,1,10));
        assertEquals(List.of(2L,3L),rows.stream().map(Row::getId).toList());
        verify(source).open(4);verify(external).close();
    }
    @Test void localSecondPhaseAndCustomUpdateJoinSameTransactionAndPreviewSkipsWrites(){
        task=new Task(){
            protected String sourceMapperNamespace(){return Task.class.getName()+"$SourceMapper";}
            protected String targetMapperNamespace(){return Task.class.getName()+"$TargetMapper";}
            public void work(SyncContext<Void> context){
                syncData(context.forData("Rows",true,true));
                // The local query sees the first phase's uncommitted rows in the Spring transaction.
                syncData(context.forLocalData("Local",false,false));
                correct(context,"corrected");
            }
        };
        targetSql=mock(SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(targetSql));
        assertEquals(6,run(context(false)).inserted());
        assertEquals(6,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row WHERE tenant_id=1 AND creator='corrected'",Integer.class));
        verify(targetSql,never()).close();verify(targetSql,never()).commit();
        task.correct(context(true),"preview must not write");
        assertEquals(6,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row WHERE creator='corrected'",Integer.class));
        assertEquals("other tenant",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=2",String.class));
    }
    @Test void missingMappedDeleteFailsWithoutFallback(){
        assertThrows(RuntimeException.class,()->transaction.executeWithoutResult(status->task.clearMissing(context(false))));
        assertEquals(2,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row",Integer.class));
    }
    @Test void dateRangesPreserveDayOffsetsAndClampToday(){
        var now=LocalDateTime.of(2024,3,1,12,34,56,123000000);
        assertEquals(new AbstractSyncTask.DateRange(LocalDateTime.of(2024,2,29,0,0),LocalDateTime.of(2024,3,1,12,34,56)),
                AbstractSyncTask.initQueryDateRange(new AbstractSyncTask.DateRangeRequest(2,0,now)));
        assertEquals(new AbstractSyncTask.DateRange(LocalDateTime.of(2024,2,28,0,0),LocalDateTime.of(2024,2,29,23,59,59)),
                AbstractSyncTask.initQueryDateRange(new AbstractSyncTask.DateRangeRequest(2,1,now)));
        assertThrows(IllegalArgumentException.class,()->new AbstractSyncTask.DateRangeRequest(0,0,now));
        assertThrows(IllegalArgumentException.class,()->new AbstractSyncTask.DateRangeRequest(1,-1,now));
    }
    @Test void prefillCanRemoveAllRowsAndStillRunItsAfterHook(){
        task=new Task(){
            protected String sourceMapperNamespace(){return Task.class.getName()+"$SourceMapper";}
            protected String targetMapperNamespace(){return Task.class.getName()+"$TargetMapper";}
            protected void syncDataInsertBefore(SyncBatch<Row,Void> batch){batch.rows().clear();}
        };
        assertEquals(new SyncCounts(3,0,0,0,0),run(context(false).forData("Rows",false,false)));
        assertEquals(1,task.afterCalls);
        assertEquals(2,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row",Integer.class));
    }
    @Test void postUpdateFailureRollsBackInsertAndClear(){
        task=new Task(){
            protected String sourceMapperNamespace(){return Task.class.getName()+"$SourceMapper";}
            protected String targetMapperNamespace(){return Task.class.getName()+"$TargetMapper";}
            protected void syncDataAfter(SyncContext<Void> context){correct(context,"changed");throw new IllegalArgumentException("post update failed");}
        };
        assertThrows(IllegalArgumentException.class,()->run(context(false).forData("Rows",true,true)));
        assertEquals(List.of(90L,99L),targetJdbc.queryForList("SELECT id FROM target_row ORDER BY id",Long.class));
        assertEquals("previous",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=1",String.class));
    }
    @Test void nativeFullReadPreservesCallbackLimitErrorAndClosesSource(){
        var original=context(false);
        var limited=new SyncContext<Void>(1,2,3,false,2,2,original.startedAt(),source,original.identities(),references,"Rows",targetSql,true,false);
        assertEquals("源数据超过本次读取上限",assertThrows(IllegalArgumentException.class,()->run(limited)).getMessage());
        verify(external).close();
        assertEquals("previous",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE tenant_id=1",String.class));
    }
    @Test void rawSqlIsNotAcceptedAsMappedStatementAndPreviewDeleteDoesNothing(){
        assertThrows(IllegalArgumentException.class,()->new AbstractSyncTask.MappedCall<>(context(false),"DELETE FROM target_row",new Task.Correction(1,"x")));
        task.clearMissing(context(true));
        assertEquals(2,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row",Integer.class));
    }

    @lombok.Data
    static class IncrementParameters {
        long afterId=1,maxId=3,referenceId=90;
        String prefix="mapped:",reference,finalCreator;
        int preparedBatches,finishedBatches;
    }
    /** Mirrors the reference's default syncData + shared params + prefill + post-update usage. */
    static class IncrementTask extends AbstractSyncTask<Row,IncrementParameters> {
        boolean paging,streaming,customStatements,failAfter;
        IncrementParameters state;
        List<Row> returned;
        SyncContext.StepLog log;
        public String taskCode(){return "Increment";}
        public String taskName(){return "reference-style incremental flow";}
        protected String sourceMapperNamespace(){return Task.class.getName()+"$SourceMapper";}
        protected String targetMapperNamespace(){return Task.class.getName()+"$TargetMapper";}
        protected IncrementParameters createParameters(SyncContext<?> runtime){state=new IncrementParameters();return state;}
        public void work(SyncContext<IncrementParameters> context){
            var call=context.forData("Increment",true,streaming).forBatchSize(1);
            if(customStatements) call=call.forStatements(new SyncContext.Statements(paging?"chooseIncrementPage":"chooseIncrement","countChosen","deleteChosen","storeChosen",null));
            log=call.stepLog();
            returned=paging?syncDataWithPaging(call):syncData(call);
        }
        protected void syncDataBefore(SyncContext<IncrementParameters> context){
            super.syncDataBefore(context);
            List<String> reference=syncDataQueryBySql(new MappedCall<>(context.forLocalData("Reference",false,false),
                    targetMapperNamespace()+".readReference",new ClearQuery<>(context.tenantId(),context.preview(),context.parameters())));
            context.parameters().reference=reference.getFirst();
        }
        protected void syncDataInsertBefore(SyncBatch<Row,IncrementParameters> batch){
            assertSame(state,batch.context().parameters());state.preparedBatches++;
            for(var row:batch.rows())row.setResolvedCreator(state.reference);
        }
        protected void syncDataInsertAfter(SyncBatch<Row,IncrementParameters> batch){
            assertSame(state,batch.context().parameters());state.finishedBatches++;
            state.finalCreator=state.prefix+state.reference+":"+state.finishedBatches;
        }
        protected void syncDataAfter(SyncContext<IncrementParameters> context){
            if(!customStatements) syncDataUpdateBySql(new MappedCall<>(context,targetMapperNamespace()+".fillIncrement",
                    new ClearQuery<>(context.tenantId(),context.preview(),context.parameters())));
            if(failAfter)throw new IllegalStateException("post fill failed");
        }
    }
    SyncCounts runIncrement(IncrementTask value,boolean preview){return transaction.execute(status->value.execute(context(preview)));}
    void seedIncrement(){targetJdbc.update("INSERT INTO target_row VALUES(2,1,'replace only this row')");}
    @Test void typedParametersFlowThroughDefaultQueryConditionalClearInsertAndPostUpdate(){
        seedIncrement();var value=new IncrementTask();
        sourceSql=mock(SqlSession.class,org.mockito.AdditionalAnswers.delegatesTo(sourceSql));when(external.sqlSession()).thenReturn(sourceSql);
        assertEquals(new SyncCounts(2,2,0,0,1),runIncrement(value,false));
        assertEquals(List.of("mapped:previous:1","mapped:previous:1"),targetJdbc.queryForList("SELECT creator FROM target_row WHERE id IN(2,3) ORDER BY id",String.class));
        assertEquals("previous",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE id=90",String.class));
        assertEquals("other tenant",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE id=99",String.class));
        verify(sourceSql).selectList(anyString(),any());verify(sourceSql,never()).select(anyString(),any(),any(ResultHandler.class));
        assertEquals("STEP_SUCCEEDED",value.log.state());assertNotNull(value.log.startedAt());assertNotNull(value.log.finishedAt());
        assertEquals(List.of(2L,3L),value.returned.stream().map(Row::getId).toList());
    }
    @Test void sameTypedParametersAlsoReachEveryPhysicalPage(){
        seedIncrement();var value=new IncrementTask();value.paging=true;
        assertEquals(new SyncCounts(2,2,0,0,1),runIncrement(value,false));
        assertEquals(2,value.state.preparedBatches);assertEquals(2,value.state.finishedBatches);
        assertEquals(List.of(2L,3L),value.returned.stream().map(Row::getId).toList());
        assertEquals(List.of(2L,3L,90L),targetJdbc.queryForList("SELECT id FROM target_row WHERE tenant_id=1 ORDER BY id",Long.class));
        verify(source).open(3);verify(external).close();
    }
    @Test void sameTypedParametersAlsoReachNativeHandlerAndAllHooks(){
        seedIncrement();var value=new IncrementTask();value.streaming=true;
        assertEquals(new SyncCounts(2,2,0,0,1),runIncrement(value,false));
        assertEquals(2,value.state.preparedBatches);assertEquals(2,value.state.finishedBatches);
        assertEquals(2,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row WHERE creator='mapped:previous:2'",Integer.class));
        assertTrue(value.returned.isEmpty(),"streaming mode must not retain the full result list");
    }
    @Test void explicitStatementIdsAreUsedByDefaultWorkflowWithoutDatabaseMethodOverrides(){
        seedIncrement();var value=new IncrementTask();value.customStatements=true;
        assertEquals(new SyncCounts(1,1,0,0,1),runIncrement(value,false));
        assertEquals("custom:mapped:previous",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE id=3",String.class));
        assertEquals(0,targetJdbc.queryForObject("SELECT COUNT(*) FROM target_row WHERE id=2",Integer.class));
    }
    @Test void explicitPagingStatementReceivesBothBusinessParametersAndOffset(){
        seedIncrement();var value=new IncrementTask();value.customStatements=true;value.paging=true;
        assertEquals(new SyncCounts(1,1,0,0,1),runIncrement(value,false));
        assertEquals(List.of(3L),value.returned.stream().map(Row::getId).toList());
        assertEquals("custom:mapped:previous",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE id=3",String.class));
    }
    @Test void typedPreviewRunsReferencesAndPreparationButNoWriteHooks(){
        seedIncrement();var value=new IncrementTask();
        assertEquals(new SyncCounts(2,2,0,0,1),runIncrement(value,true));
        assertEquals("previous",value.state.reference);assertEquals(1,value.state.preparedBatches);assertEquals(0,value.state.finishedBatches);
        assertEquals("replace only this row",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE id=2",String.class));
    }
    @Test void postFailureUpdatesStepMetadataAndRollsBackConditionalClearAndWrites(){
        seedIncrement();var value=new IncrementTask();value.failAfter=true;
        assertThrows(IllegalStateException.class,()->runIncrement(value,false));
        assertEquals("replace only this row",targetJdbc.queryForObject("SELECT creator FROM target_row WHERE id=2",String.class));
        assertEquals("FAILED",value.log.state());assertNotNull(value.log.finishedAt());assertEquals(IllegalStateException.class.getName(),value.log.failureType());
    }
    @Test void nullResultHandlerUsesTheNativeListOverload(){
        assertEquals(List.of(2L,3L),task.selectWithNullHandler(context(true),new Task.Selection(1,1,10)).stream().map(Row::getId).toList());
        verify(external).close();
    }
    @Test void everyExecutionCreatesFreshTypedParametersAndRunCounters(){
        var value=new IncrementTask();var runtime=context(true);
        assertEquals(2,transaction.execute(status->value.execute(runtime)).read());
        var firstState=value.state;var firstLog=value.log;
        assertEquals(2,transaction.execute(status->value.execute(runtime)).read());
        assertNotSame(firstState,value.state);assertNotSame(firstLog,value.log);assertEquals(1,value.state.preparedBatches);
    }
    @Test void referenceStyleVoidWorkSharesParametersAndCollectsMultipleStepCounts(){
        seedIncrement();var value=new IncrementTask(){
            public void work(SyncContext<IncrementParameters> context){
                super.work(context);
                var first=context.parameters();
                var second=syncData(context.forLocalData("Local",false,false));
                assertSame(first,context.parameters());assertEquals(3,second.size());
            }
        };
        assertEquals(new SyncCounts(5,5,0,0,1),runIncrement(value,false));
        assertEquals(2,value.state.preparedBatches);assertEquals(2,value.state.finishedBatches);
        assertEquals(List.of(2L,3L,90L,102L,103L,190L),targetJdbc.queryForList("SELECT id FROM target_row WHERE tenant_id=1 ORDER BY id",Long.class));
    }
}

