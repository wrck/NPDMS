package cn.iocoder.yudao.module.pms.integration.basesync;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AbstractSyncTaskLifecycleTest {
    static class Task extends AbstractSyncTask<Integer,Void> {
        List<Integer> rows=List.of(1,2,3); List<String> events=new ArrayList<>(); boolean rejectSnapshot;
        public String taskCode(){return "TEST";} public String taskName(){return "test";}
        public Set<String> requiredPermissions(){return Set.of();}
        public void work(SyncContext<Void> c){syncData(c.forData("TEST",true,false));}
        protected void syncDataBefore(SyncContext<Void> c){events.add("before");}
        protected void syncDataQueryByRowHandler(SyncContext<Void> c,ResultHandler<Integer> handler){events.add("query");emit(rows,handler);}
        protected List<Integer> syncDataQuery(SyncContext<Void> c){events.add("query");return rows;}
        protected void syncDataValidate(SyncBatch<Integer,Void> b){events.add("validate");if(rejectSnapshot)throw new IllegalArgumentException("invalid snapshot");}
        protected void syncDataInsertBefore(SyncBatch<Integer,Void> b){events.add("prepare"+b.rows());}
        protected long syncDataClearCount(SyncContext<Void> c){events.add("count");return 1;}
        protected long syncDataClear(SyncContext<Void> c){events.add("clear");return 1;}
        protected void syncDataInsert(SyncBatch<Integer,Void> b){events.add("insert"+b.rows());}
        protected void syncDataInsertAfter(SyncBatch<Integer,Void> b){events.add("afterBatch");}
        protected void syncDataAfter(SyncContext<Void> c){events.add("after");}
        protected void syncDataSuccess(SyncContext<Void> c,SyncCounts result){events.add("success");}
        protected void syncDataFail(SyncContext<Void> c,RuntimeException failure){events.add("fail");}
        protected void syncDataFinally(SyncContext<Void> c){events.add("finally");}
    }
    static <R> void emit(Iterable<R> rows,ResultHandler<R> handler){
        var result=new DefaultResultContext<R>();
        for(var row:rows){result.nextResultObject(row);handler.handleResult(result);}
    }
    static SyncContext<Void> context(boolean preview){return new SyncContext<Void>(1,2,3,preview,2,10,LocalDateTime.of(2026,10,17,12,0),mock(SyncSource.class),mock(SyncIdentityStore.class,CALLS_REAL_METHODS),mock(SyncReferences.class),"TEST",mock(org.apache.ibatis.session.SqlSession.class),false,true);}
    @Test void followsReferenceFullReadPrefillThenSplitWorkflow(){
        var task=new Task();assertEquals(new SyncCounts(3,3,0,0,1),task.execute(context(false)));
        assertEquals(List.of("before","query","validate","count","clear","prepare[1, 2, 3]","insert[1, 2]","insert[3]","afterBatch","success","after","finally"),task.events);
    }
    @Test void invalidFullSnapshotCannotClear(){var task=new Task();task.rejectSnapshot=true;assertThrows(IllegalArgumentException.class,()->task.execute(context(false)));assertEquals(List.of("before","query","validate","fail","finally"),task.events);}
    @Test void previewHasNoWriteHooks(){var task=new Task();task.execute(context(true));assertEquals(List.of("before","query","validate","count","prepare[1, 2, 3]","success","finally"),task.events);}
    @Test void emptySnapshotCanReplaceTenantCache(){var task=new Task();task.rows=List.of();assertEquals(new SyncCounts(0,0,0,0,1),task.execute(context(false)));assertEquals(List.of("before","query","validate","count","clear","success","after","finally"),task.events);}
    @Test void rowLimitFailsBeforeClear(){var task=new Task();task.rows=Collections.nCopies(11,1);assertThrows(IllegalArgumentException.class,()->task.execute(context(false)));assertEquals(List.of("before","query","fail","finally"),task.events);}
    @Test void overriddenRowHandlerCanStreamBeyondConfiguredCapacity(){
        var task=new Task(){public void work(SyncContext<Void> c){syncData(c.forData("TEST",false,true));}};
        task.rows=Collections.nCopies(11,1);
        assertEquals(11,task.execute(context(false)).read());
        assertEquals(6,task.events.stream().filter("afterBatch"::equals).count());
    }
    @Test void sourceQueryOmitsLimitOnlyForFormalStreaming(){
        var task=new Task(){
            public void work(SyncContext<Void> c){
                assertNull(sourceQuery(c.forData("TEST",false,true)).parameter().limit());
                assertEquals(11,sourceQuery(c.forData("TEST",false,false)).parameter().limit());
            }
        };
        task.execute(context(false));
        var previewTask=new Task(){
            public void work(SyncContext<Void> c){assertEquals(11,sourceQuery(c.forData("TEST",false,true)).parameter().limit());}
        };
        previewTask.execute(context(true));
    }
    @Test void workCanOrchestrateMultipleSyncDataCalls(){
        var task=new Task(){
            public void work(SyncContext<Void> context){syncData(context.forData("Header"));syncData(context.forData("Line"));}
            protected void syncDataQueryByRowHandler(SyncContext<Void> c,ResultHandler<Integer> handler){events.add(c.dataName());emit(List.of(1),handler);}
        };
        assertEquals(2,task.execute(context(false)).inserted());assertTrue(task.events.indexOf("Header")<task.events.indexOf("Line"));
    }
    @Test void beforeHookCanReplaceValuesInMutableBatch(){
        var task=new Task(){protected void syncDataInsertBefore(SyncBatch<Integer,Void> b){b.rows().replaceAll(value->value*10);}};
        task.execute(context(false));assertTrue(task.events.contains("insert[10, 20]"));assertTrue(task.events.contains("insert[30]"));
    }
    @Test void rowHandlerFlushesLastBatchWithoutFullListQuery(){
        var task=new Task(){
            public void work(SyncContext<Void> c){syncData(c.forData("TEST",false,true));}
            protected void syncDataQueryByRowHandler(SyncContext<Void> c,ResultHandler<Integer> handler){
                var result=new DefaultResultContext<Integer>();
                for(int i=1;i<=5;i++){
                    result.nextResultObject(i);handler.handleResult(result);
                    if(i==2)assertTrue(events.contains("insert[1, 2]"),"first batch must be written before the remaining source rows are read");
                }
            }
        };
        assertEquals(5,task.execute(context(false)).inserted());assertTrue(task.events.contains("insert[5]"));assertEquals(3,task.events.stream().filter("afterBatch"::equals).count());
    }
    @Test void failureCallbackCannotSwallowOriginalError(){
        var original=new IllegalArgumentException("source failed");
        var task=new Task(){protected List<Integer> syncDataQuery(SyncContext<Void> c){throw original;}protected void syncDataFail(SyncContext<Void> c,RuntimeException e){throw new IllegalStateException("notification failed");}};
        assertSame(original,assertThrows(IllegalArgumentException.class,()->task.execute(context(false))));assertEquals(1,original.getSuppressed().length);assertEquals(List.of("before","finally"),task.events);
    }
    @Test void laterWriteFailureRollsBackClearAndEarlierBatch(){
        var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:base_sync_rollback;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE cache(id INT PRIMARY KEY,tenant_id BIGINT)");
        jdbc.update("INSERT INTO cache VALUES(9,1),(99,2)");
        var task=new Task(){
            protected long syncDataClear(SyncContext<Void> c){return jdbc.update("DELETE FROM cache WHERE tenant_id=?",c.tenantId());}
            protected void syncDataInsert(SyncBatch<Integer,Void> b){for(int id:b.rows()){if(id==3)throw new IllegalStateException("failure in last batch");jdbc.update("INSERT INTO cache VALUES(?,?)",id,b.context().tenantId());}}
        };
        var transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
        assertThrows(IllegalStateException.class,()->transaction.execute(status->task.execute(context(false))));
        assertEquals(List.of(9,99),jdbc.queryForList("SELECT id FROM cache ORDER BY id",Integer.class));assertFalse(task.events.contains("success"));assertTrue(task.events.contains("fail"));
    }
    @Test void streamingTailFailureBeyondOldLimitRollsBackEarlierBatches(){
        var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:stream_tail_rollback;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE cache(id INT PRIMARY KEY,tenant_id BIGINT)");
        jdbc.update("INSERT INTO cache VALUES(9,1),(99,2)");
        var tailFailure=new IllegalStateException("tail failure");
        var task=new Task(){
            public void work(SyncContext<Void> c){syncData(c.forData("TEST",true,true));}
            protected long syncDataClear(SyncContext<Void> c){return jdbc.update("DELETE FROM cache WHERE tenant_id=?",c.tenantId());}
            protected void syncDataInsert(SyncBatch<Integer,Void> b){for(int id:b.rows()){if(id==3)throw tailFailure;jdbc.update("INSERT INTO cache VALUES(?,?)",id,b.context().tenantId());}}
        };
        var c=new SyncContext<Void>(1,2,3,false,2,2,LocalDateTime.now(),mock(SyncSource.class),mock(SyncIdentityStore.class),mock(SyncReferences.class),"TEST",mock(org.apache.ibatis.session.SqlSession.class),false,true);
        var transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
        assertSame(tailFailure,assertThrows(IllegalStateException.class,()->transaction.execute(status->task.execute(c))));
        assertEquals(List.of(9,99),jdbc.queryForList("SELECT id FROM cache ORDER BY id",Integer.class));
    }
    @Test void finalCleanupCannotMaskOriginalCallbackFailure(){
        var original=new IllegalArgumentException("write failed");
        var task=new Task(){
            public void work(SyncContext<Void> c){syncData(c.forData("TEST",false,true));}
            protected void syncDataInsert(SyncBatch<Integer,Void> batch){throw original;}
            protected void syncDataFinally(SyncContext<Void> context){throw new IllegalStateException("cleanup failed");}
        };
        assertSame(original,assertThrows(IllegalArgumentException.class,()->task.execute(context(false))));
        assertEquals("cleanup failed",original.getSuppressed()[0].getMessage());
    }
    @Test void validationFilteringCannotReduceSourceReadCount(){
        var task=new Task(){protected void syncDataValidate(SyncBatch<Integer,Void> batch){batch.rows().removeFirst();}};
        assertEquals(new SyncCounts(3,2,0,0,1),task.execute(context(false)));
    }
    @Test void cleanupFailureAfterSuccessfulSqlStillInvokesFailureHook(){
        var task=new Task(){protected void syncDataFinally(SyncContext<Void> context){throw new IllegalStateException("cleanup failed");}};
        assertEquals("cleanup failed",assertThrows(IllegalStateException.class,()->task.execute(context(false))).getMessage());
        assertEquals(1,task.events.stream().filter("fail"::equals).count());
    }
    @Test void cacheCleanupAndFinallyErrorsCannotReplaceOriginalReadFailure(){
        var original=new IllegalArgumentException("source failed");
        var cleanup=new IllegalStateException("hook cleanup failed");
        var cache=new IllegalStateException("cache cleanup failed");
        var context=context(false);
        doThrow(cache).when(context.target()).clearCache();
        var task=new Task(){
            protected List<Integer> syncDataQuery(SyncContext<Void> c){throw original;}
            protected void syncDataFinally(SyncContext<Void> c){throw cleanup;}
        };
        assertSame(original,assertThrows(IllegalArgumentException.class,()->task.execute(context)));
        assertSame(cleanup,original.getSuppressed()[0]);assertSame(cache,cleanup.getSuppressed()[0]);
    }
}
