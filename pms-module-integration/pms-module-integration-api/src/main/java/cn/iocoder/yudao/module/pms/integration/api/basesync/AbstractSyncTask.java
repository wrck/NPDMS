package cn.iocoder.yudao.module.pms.integration.api.basesync;

import java.util.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;

/** Reference template workflow; MyBatis executes mapped statements and Spring owns the transaction. */
public abstract class AbstractSyncTask<T,P> {
    private final org.slf4j.Logger logger=org.slf4j.LoggerFactory.getLogger(getClass());
    private volatile RetrySettings retrySettings=new RetrySettings(3,2000,false);
    private volatile boolean defaultUseRowHandler=true;
    public record SourceQuery<Q>(int limit,long tenantId,boolean preview,Q parameters) {}
    public record PageQuery<Q>(int offset,int limit,long tenantId,boolean preview,Q parameters) {
        public PageQuery { if(offset<0 || limit<1 || tenantId<=0) throw new IllegalArgumentException("分页范围无效"); }
    }
    public record PageRead<Q>(SyncContext<Q> context,SqlSession session,PageQuery<Q> query) {}
    public record ClearQuery<Q>(long tenantId, boolean preview,Q parameters) {}
    public record RowParameter<R,Q>(R row,SyncContext<Q> context) {}
    /** A registered Mapper XML statement and its scene-specific parameter object, never SQL text. */
    public record MappedCall<Q>(SyncContext<?> context,String statement,Q parameter) {
        public MappedCall {
            Objects.requireNonNull(context);
            if(statement==null || !statement.matches("[A-Za-z_$][\\w$]*(\\.[\\w$]+)+"))
                throw new IllegalArgumentException("必须使用已注册的 Mapper 语句标识");
        }
    }
    public record RetrySettings(int maxRetries,long intervalMillis,boolean exponentialBackoff) {
        public RetrySettings {
            if(maxRetries<0 || intervalMillis<0) throw new IllegalArgumentException("重试配置不能为负数");
        }
    }
    public record DateRangeRequest(int dayRange,int dayOffset,LocalDateTime now) {
        public DateRangeRequest {
            if(dayRange<1 || dayOffset<0) throw new IllegalArgumentException("日期范围或偏移无效");
            Objects.requireNonNull(now);
        }
    }
    public record DateRange(LocalDateTime start,LocalDateTime end) {}
    public abstract String taskCode();
    public abstract String taskName();
    public String sourceSystem() { return "DPPMS"; }
    public Set<String> requiredPermissions() { return Set.of(); }

    public final SyncCounts execute(SyncContext<?> runtime) {
        var context=runtime.newAttempt(createParameters(runtime));
        work(context);
        runtime.runState().replaceWarnings(context.runState().warnings());
        return context.runState().counts();
    }
    protected P createParameters(SyncContext<?> runtime) { return null; }
    public void work(SyncContext<P> context) { syncData(context); }

    /** Default reference workflow: stream, process one batch synchronously, release it, then read on. */
    protected List<T> syncData(SyncContext<P> context) {
        return syncDataInner(context,false);
    }
    protected List<T> syncDataWithPaging(SyncContext<P> context) {
        return syncDataInner(context,true);
    }
    protected List<T> syncDataInner(SyncContext<P> context,boolean paging) {
        RuntimeException failure=null;
        try {
            syncDataBefore(context);
            SyncCounts result;
            List<T> rows=new ArrayList<>();
            if(paging) {
                result=syncDataPages(context,rows);
            } else if(context.useRowHandler()) {
                long deleted=clearIfRequested(context);
                var batch=new SyncBatch<T,P>(context,List.of());
                var counts=new SyncCounts[]{new SyncCounts(0,0,0,0,deleted)};
                long[] read={0};
                RuntimeException[] callbackFailure={null};
                Runnable flush=()->{
                    if(!batch.rows().isEmpty()) counts[0]=counts[0].plus(processBatch(batch));
                };
                try {
                    syncDataQueryByRowHandler(context,row->{
                        try {
                            if(++read[0]>context.maxRows()) throw new IllegalArgumentException("源数据超过本次读取上限");
                            batch.rows().add(Objects.requireNonNull(row.getResultObject()));
                            if(batch.rows().size()==context.batchSize()) flush.run();
                        } catch(RuntimeException error) { callbackFailure[0]=error;throw error; }
                    });
                    flush.run();
                } catch(RuntimeException error) {
                    throw callbackFailure[0]==null?error:callbackFailure[0];
                } finally {
                    // A source failure may occur with an incomplete batch, before processBatch is called.
                    batch.rows().clear();
                }
                result=withReadCount(counts[0],read[0]);
            } else {
                var all=new SyncBatch<>(context,syncDataQuery(context));
                int read=all.rows().size();
                rows=all.rows();
                if(read>context.maxRows()) throw new IllegalArgumentException("源数据超过本次读取上限");
                syncDataValidate(all);
                result=new SyncCounts(0,0,0,0,clearIfRequested(context));
                result=withReadCount(result.plus(syncDataInsertWrapper(all)),read);
            }
            syncDataSuccess(context,result);
            if(!context.preview()) syncDataAfter(context);
            context.runState().add(result);
            return rows;
        } catch(RuntimeException error) {
            failure=error;
            try { syncDataFail(context,error); } catch(RuntimeException notification) { error.addSuppressed(notification); }
            throw error;
        } finally {
            RuntimeException cleanup=null;
            try { syncDataFinally(context); } catch(RuntimeException error) { cleanup=error; }
            try { context.target().clearCache(); } catch(RuntimeException error) {
                if(cleanup==null) cleanup=error; else cleanup.addSuppressed(error);
            }
            if(cleanup!=null) {
                if(failure==null) {
                    try { syncDataFail(context,cleanup); } catch(RuntimeException notification) { cleanup.addSuppressed(notification); }
                    throw cleanup;
                }
                failure.addSuppressed(cleanup);
            }
        }
    }

    private SyncCounts syncDataPages(SyncContext<P> context,List<T> resultRows) {
        SyncCounts result=SyncCounts.empty();
        int offset=0;
        boolean first=true;
        // All pages use the same source session. XML must use a stable unique ORDER BY.
        try(var source=context.source().open(context.connectionId())) {
            while(true) {
                int limit=Math.min(context.batchSize(),context.maxRows()-offset+1);
                var rows=syncDataQueryByPage(new PageRead<>(context,source.sqlSession(),new PageQuery<>(offset,limit,context.tenantId(),context.preview(),context.parameters())));
                if(rows.size()>limit || offset+rows.size()>context.maxRows())
                    throw new IllegalArgumentException("源数据超过本次读取上限或分页范围");
                resultRows.addAll(rows);
                var batch=new SyncBatch<>(context,rows);
                syncDataValidate(batch);
                if(first) { result=new SyncCounts(0,0,0,0,clearIfRequested(context));first=false; }
                if(rows.isEmpty()) break;
                int size=rows.size();
                rows=null;
                result=result.plus(syncDataInsertWrapper(batch));
                batch.rows().clear();
                context.target().clearCache();
                offset+=size;
                if(size<limit) break;
            }
        }
        return withReadCount(result,offset);
    }

    private SyncCounts processBatch(SyncBatch<T,P> batch) {
        RuntimeException failure=null;
        try {
            syncDataValidate(batch);
            return syncDataInsertWrapper(batch);
        } catch(RuntimeException error) {
            failure=error;
            throw error;
        } finally {
            batch.rows().clear();
            // Preview has no INSERT/UPDATE to invalidate MyBatis' session cache automatically.
            try { batch.context().target().clearCache(); }
            catch(RuntimeException cleanup) {
                if(failure==null) throw cleanup;
                failure.addSuppressed(cleanup);
            }
        }
    }

    private SyncCounts withReadCount(SyncCounts counts,long read) {
        return new SyncCounts(read,counts.inserted(),counts.updated(),counts.unchanged(),counts.deleted());
    }

    private long clearIfRequested(SyncContext<P> context) {
        if(!context.clear()) return 0;
        long count=syncDataClearCount(context);
        if(!context.preview() && syncDataClear(context)!=count) throw new IllegalStateException("清理期间数据已变化");
        return count;
    }

    /** These are registered XML statement IDs, never SQL text or caller supplied table names. */
    protected String sourceMapperNamespace() { return getClass().getName()+"$SourceMapper"; }
    protected String targetMapperNamespace() { return getClass().getName()+"$TargetMapper"; }
    private String queryMapperNamespace(SyncContext<P> context) {
        return context.source().local()?targetMapperNamespace():sourceMapperNamespace();
    }
    private String statement(String namespace,String defaultName,String selected) {
        String name=selected==null || selected.isBlank()?defaultName:selected;
        String id=name.contains(".")?name:namespace+"."+name;
        if(!id.matches("[A-Za-z_$][\\w$]*(\\.[\\w$]+)+")) throw new IllegalArgumentException("必须使用已注册的 Mapper 语句标识");
        return id;
    }
    protected MappedCall<SourceQuery<P>> sourceQuery(SyncContext<P> context) {
        return new MappedCall<>(context,statement(queryMapperNamespace(context),"select"+context.dataName(),context.statements().query()),
                new SourceQuery<>(context.maxRows()+1,context.tenantId(),context.preview(),context.parameters()));
    }

    protected void syncDataQueryByRowHandler(SyncContext<P> context,ResultHandler<T> handler) {
        syncDataQueryBySql(sourceQuery(context),handler);
    }
    protected List<T> syncDataQueryByPage(PageRead<P> read) {
        var context=read.context();
        return syncDataQueryBySql(read.session(),new MappedCall<>(context,
                statement(queryMapperNamespace(context),"select"+context.dataName()+"WithPaging",context.statements().query()),read.query()));
    }
    protected <R,P> List<R> syncDataQueryBySql(MappedCall<P> call) {
        try(var source=call.context().source().open(call.context().connectionId())) {
            return syncDataQueryBySql(source.sqlSession(),call);
        }
    }
    protected <R,Q> List<R> syncDataQueryBySql(SqlSession session,MappedCall<Q> call) {
        session.clearCache();
        RuntimeException failure=null;
        try {
            List<R> rows=session.selectList(call.statement(),call.parameter());
            if(rows.size()>call.context().maxRows()) throw new IllegalArgumentException("源数据超过本次读取上限");
            return rows;
        } catch(RuntimeException error) {
            failure=error;throw error;
        } finally {
            try { session.clearCache(); }
            catch(RuntimeException cleanup) {
                if(failure==null) throw cleanup;
                failure.addSuppressed(cleanup);
            }
        }
    }
    protected <R,Q> List<R> syncDataQueryBySql(MappedCall<Q> call,ResultHandler<R> handler) {
        if(handler==null) return syncDataQueryBySql(call);
        try(var source=call.context().source().open(call.context().connectionId())) {
            syncDataQueryBySql(source.sqlSession(),call,handler);
            return Collections.emptyList();
        }
    }
    protected <R,Q> void syncDataQueryBySql(SqlSession session,MappedCall<Q> call,ResultHandler<R> handler) {
        Objects.requireNonNull(handler,"流式查询必须提供 ResultHandler");
        session.clearCache();
        RuntimeException[] callbackFailure={null};
        RuntimeException failure=null;
        long[] read={0};
        try {
            session.select(call.statement(),call.parameter(),(ResultHandler<R>)result->{
                try {
                    if(++read[0]>call.context().maxRows()) throw new IllegalArgumentException("源数据超过本次读取上限");
                    handler.handleResult(result);
                } catch(RuntimeException error) { callbackFailure[0]=error;throw error; }
            });
        } catch(RuntimeException error) {
            failure=callbackFailure[0]==null?error:callbackFailure[0];
            throw failure;
        } finally {
            try { session.clearCache(); }
            catch(RuntimeException cleanup) {
                if(failure==null) throw cleanup;
                failure.addSuppressed(cleanup);
            }
        }
    }
    protected List<T> syncDataQuery(SyncContext<P> context) {
        return syncDataQueryBySql(sourceQuery(context));
    }
    protected long syncDataClearCount(SyncContext<P> context) {
        Number count=context.target().selectOne(statement(targetMapperNamespace(),"count"+context.dataName()
                        +(context.parameters()==null?"":"ByParams"),context.statements().count()),
                new ClearQuery<>(context.tenantId(),context.preview(),context.parameters()));
        return Objects.requireNonNull(count).longValue();
    }
    protected long syncDataClear(SyncContext<P> context) {
        return syncDataClearBySql(new MappedCall<>(context,statement(targetMapperNamespace(),"delete"+context.dataName()
                        +(context.parameters()==null?"":"ByParams"),context.statements().delete()),
                new ClearQuery<>(context.tenantId(),context.preview(),context.parameters())));
    }
    protected <P> int syncDataClearBySql(MappedCall<P> call) {
        return call.context().preview()?0:call.context().target().delete(call.statement(),call.parameter());
    }
    protected <P> int syncDataInsertBySql(MappedCall<P> call) {
        return call.context().preview()?0:call.context().target().insert(call.statement(),call.parameter());
    }
    protected <P> int syncDataUpdateBySql(MappedCall<P> call) {
        return call.context().preview()?0:call.context().target().update(call.statement(),call.parameter());
    }
    protected void syncDataInsert(SyncBatch<T,P> batch) {
        if(batch.rows().isEmpty() || batch.context().preview()) return;
        int changed=syncDataInsertBySql(new MappedCall<>(batch.context(),statement(targetMapperNamespace(),"insert"+batch.context().dataName(),
                batch.context().statements().insert()),batch));
        if(changed!=batch.rows().size()) throw new IllegalStateException("同步批量插入数量不符");
    }
    /** Conditional updates retain per-row affected-count/version checks; no implicit upsert. */
    protected void syncDataUpdate(SyncBatch<T,P> batch) {
        if(batch.context().preview()) return;
        for(T row:batch.rows())
            if(syncDataUpdateBySql(new MappedCall<>(batch.context(),statement(targetMapperNamespace(),"update"+batch.context().dataName(),
                    batch.context().statements().update()),new RowParameter<>(row,batch.context())))!=1)
                throw new IllegalStateException("同步更新数量或版本冲突");
    }
    /** Opt-in MySQL upsert: XML must increment version on every duplicate, making its affected count 2. */
    protected boolean syncDataUsesUpsert(SyncContext<P> context) { return false; }

    protected SyncCounts syncDataUpsert(SyncBatch<T,P> batch) {
        int size=batch.rows().size();
        if(size==0 || batch.context().preview()) return new SyncCounts(size,0,0,0,0);
        int affected=syncDataInsertBySql(new MappedCall<>(batch.context(),statement(targetMapperNamespace(),
                "insert"+batch.context().dataName(),batch.context().statements().insert()),batch));
        // No IGNORE/no-op updates: one affected row per INSERT and two per UPDATE.
        if(affected<size || affected>2L*size) throw new IllegalStateException("同步批量upsert数量不符");
        return new SyncCounts(size,2L*size-affected,affected-size,0,0);
    }

    protected SyncCounts syncDataInsertWrapper(SyncBatch<T,P> batch) {
        if(batch.rows().isEmpty()) return SyncCounts.empty();
        int read=batch.rows().size();
        syncDataReferences(batch);
        syncDataInsertBefore(batch);
        boolean upsert=syncDataUsesUpsert(batch.context());
        SyncCounts changes=upsert?SyncCounts.empty():syncDataCount(batch);
        if(!batch.context().preview()) {
            for(int start=0;start<batch.rows().size();start+=batch.context().batchSize()) {
                var part=new SyncBatch<>(batch.context(),batch.rows().subList(start,
                        Math.min(start+batch.context().batchSize(),batch.rows().size())));
                if(upsert) changes=changes.plus(syncDataUpsert(part));
                else syncDataInsert(part);
            }
            syncDataInsertAfter(batch);
        }
        return new SyncCounts(read,changes.inserted(),changes.updated(),changes.unchanged(),changes.deleted());
    }
    protected void syncDataBefore(SyncContext<P> context) { context.stepLog().begin();logger.info("{}-开始",getTag(context)); }
    protected void syncDataValidate(SyncBatch<T,P> batch) {}
    protected void syncDataInsertBefore(SyncBatch<T,P> batch) {}
    protected void syncDataInsertAfter(SyncBatch<T,P> batch) {}
    protected void syncDataAfter(SyncContext<P> context) {}
    protected void syncDataSuccess(SyncContext<P> context,SyncCounts counts) {
        context.stepLog().success(counts);
        logger.info("{}-步骤执行完成，读取={}，等待整次事务提交",getTag(context),counts.read());
    }
    protected void syncDataFail(SyncContext<P> context,RuntimeException failure) {
        context.stepLog().fail(failure);
        // Driver exceptions can contain connection credentials or source values: do not log their text.
        logger.error("{}-失败：{}",getTag(context),failure.getClass().getSimpleName());
    }
    protected void syncDataFinally(SyncContext<P> context) {}
    protected SyncCounts syncDataCount(SyncBatch<T,P> batch) {
        return new SyncCounts(batch.rows().size(),batch.rows().size(),0,0,0);
    }

    protected String getTag() { return "执行["+taskCode()+"]同步数据"; }
    protected String getTag(SyncContext<P> context) { return "执行["+context.dataName()+"]同步数据"; }
    public final RetrySettings retrySettings() { return retrySettings; }
    public final void setMaxRetries(int value) { var old=retrySettings;retrySettings=new RetrySettings(value,old.intervalMillis(),old.exponentialBackoff()); }
    public final void setRetryIntervalMillis(long value) { var old=retrySettings;retrySettings=new RetrySettings(old.maxRetries(),value,old.exponentialBackoff()); }
    public final void setEnableExponentialBackoff(boolean value) { var old=retrySettings;retrySettings=new RetrySettings(old.maxRetries(),old.intervalMillis(),value); }
    public final void setDefaultUseRowHandler(boolean value) { defaultUseRowHandler=value; }
    public final boolean defaultUseRowHandler() { return defaultUseRowHandler; }
    /** Only a database-confirmed transaction rollback is retryable by default, not arbitrary SQL/IO errors. */
    protected boolean shouldRetry(Throwable failure) {
        var visited=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable cause=failure;cause!=null && visited.add(cause);cause=cause.getCause())
            if(cause instanceof java.sql.SQLTransactionRollbackException) return true;
        return false;
    }
    public final boolean retryAllowed(Throwable failure) {
        return !Thread.currentThread().isInterrupted() && shouldRetry(failure);
    }
    public static DateRange initQueryDateRange(DateRangeRequest request) {
        var now=request.now().truncatedTo(ChronoUnit.SECONDS);
        var end=now.toLocalDate().minusDays(request.dayOffset()).atTime(LocalTime.of(23,59,59));
        if(end.isAfter(now)) end=now;
        return new DateRange(end.toLocalDate().minusDays(request.dayRange()-1L).atStartOfDay(),end);
    }

    /** Preserve historical usernames exactly; account IDs are not required for string audit columns. */
    protected void syncDataReferences(SyncBatch<T,P> batch) {
        var rows=batch.rows().stream().filter(SyncReferences.SourceAudit.class::isInstance)
                .map(SyncReferences.SourceAudit.class::cast).filter(SyncReferences.SourceAudit::requiresAuditReferences).toList();
        for(var row:rows) {
            row.setResolvedCreator(row.getSourceCreator());
            row.setResolvedUpdater(row.getSourceUpdater());
        }
    }

    protected static void checkTextLength(String value,int max) {
        if(value!=null && value.codePointCount(0,value.length())>max)
            throw new IllegalArgumentException("同步字段超过目标字符容量");
    }
    protected static void checkTextCapacity(String value) {
        if(value!=null && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535)
            throw new IllegalArgumentException("同步字段超过目标TEXT容量");
    }
    protected static java.math.BigDecimal exactDecimal(java.math.BigDecimal value,int precision,int scale) {
        if(value==null) return null;
        try {
            var exact=value.setScale(scale,java.math.RoundingMode.UNNECESSARY);
            if(exact.precision()>precision) throw new ArithmeticException();
            return exact;
        } catch(ArithmeticException error) { throw new IllegalArgumentException("同步数值不能无损写入目标精度"); }
    }
    protected static void checkMillisecond(java.time.LocalDateTime value) {
        if(value!=null && value.getNano()%1_000_000!=0)
            throw new IllegalArgumentException("同步时间不能无损写入毫秒精度");
    }
}
