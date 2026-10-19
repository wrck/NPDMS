package cn.iocoder.yudao.module.pms.integration.api.basesync;

import java.time.LocalDateTime;
import java.util.Objects;

public record SyncContext<P>(long tenantId, long actorId, long connectionId, boolean preview,
                          int batchSize, int maxRows, LocalDateTime startedAt, SyncSource source,
                          SyncIdentityStore identities, SyncReferences references, String dataName, org.apache.ibatis.session.SqlSession target,
                          boolean clear, boolean useRowHandler, P parameters, Statements statements, StepLog stepLog, RunState runState) {
    public static final class RunState {
        private SyncCounts counts=SyncCounts.empty();
        private final java.util.Set<String> warnings=new java.util.LinkedHashSet<>();
        private java.util.List<SyncReferences.AuditUser> auditCatalogue;
        public SyncCounts counts(){return counts;}
        public void add(SyncCounts value){counts=counts.plus(value);}
        public java.util.List<String> warnings(){return java.util.List.copyOf(warnings);}
        public void warning(String value){
            if(value==null || value.length()>512 || !warnings.contains(value) && warnings.stream().mapToInt(String::length).sum()+warnings.size()+value.length()>1000)
                throw new IllegalStateException("独立同步问题登记超过容量，禁止漏记");
            warnings.add(value);
        }
        public void replaceWarnings(java.util.List<String> values){warnings.clear();values.forEach(this::warning);}
        public java.util.List<SyncReferences.AuditUser> auditCatalogue(java.util.function.Supplier<java.util.List<SyncReferences.AuditUser>> loader){
            if(auditCatalogue==null)auditCatalogue=java.util.List.copyOf(loader.get());
            return auditCatalogue;
        }
    }
    public record Statements(String query,String count,String delete,String insert,String update) {
        public static Statements defaults(){return new Statements(null,null,null,null,null);}
    }
    /** Per-call equivalent of the reference's logParams, never mutable state on the task Bean. */
    public static final class StepLog {
        private LocalDateTime startedAt,finishedAt;
        private String state,failureType;
        private SyncCounts counts=SyncCounts.empty();
        public LocalDateTime startedAt(){return startedAt;}
        public LocalDateTime finishedAt(){return finishedAt;}
        public String state(){return state;}
        public String failureType(){return failureType;}
        public SyncCounts counts(){return counts;}
        public void begin(){startedAt=LocalDateTime.now();finishedAt=null;state="READING";failureType=null;counts=SyncCounts.empty();}
        public void success(SyncCounts value){finishedAt=LocalDateTime.now();state="STEP_SUCCEEDED";counts=value;}
        public void fail(Throwable failure){finishedAt=LocalDateTime.now();state="FAILED";failureType=failure.getClass().getName();}
    }
    public SyncContext(long tenantId,long actorId,long connectionId,boolean preview,int batchSize,int maxRows,
                       LocalDateTime startedAt,SyncSource source,SyncIdentityStore identities,SyncReferences references,
                       String dataName,org.apache.ibatis.session.SqlSession target,boolean clear,boolean useRowHandler) {
        this(tenantId,actorId,connectionId,preview,batchSize,maxRows,startedAt,source,identities,references,
                dataName,target,clear,useRowHandler,null,Statements.defaults(),new StepLog(),new RunState());
    }
    public SyncContext {
        if (tenantId <= 0 || actorId <= 0 || connectionId <= 0 || batchSize < 1 || batchSize > 2000
                || maxRows < 1 || maxRows > 5_000_000)
            throw new IllegalArgumentException("同步上下文无效");
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        Objects.requireNonNull(identities);
        Objects.requireNonNull(references);
        Objects.requireNonNull(statements);
        Objects.requireNonNull(stepLog);
        Objects.requireNonNull(runState);
        if(dataName==null || !dataName.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) throw new IllegalArgumentException("同步数据对象名无效");
    }
    public SyncContext<P> forData(String name) { return forData(name,clear,useRowHandler); }
    public SyncContext<P> forData(String name,boolean clear) { return forData(name,clear,true); }
    public SyncContext<P> forData(String name,boolean clear,boolean useRowHandler) {
        return new SyncContext<>(tenantId,actorId,connectionId,preview,batchSize,maxRows,startedAt,
                source,identities,references,name,target,clear,useRowHandler,parameters,Statements.defaults(),new StepLog(),runState);
    }
    public <Q> SyncContext<Q> forParameters(Q value) {
        return new SyncContext<>(tenantId,actorId,connectionId,preview,batchSize,maxRows,startedAt,
                source,identities,references,dataName,target,clear,useRowHandler,value,statements,new StepLog(),runState);
    }
    <Q> SyncContext<Q> newAttempt(Q value) {
        return new SyncContext<>(tenantId,actorId,connectionId,preview,batchSize,maxRows,startedAt,
                source,identities,references,dataName,target,clear,useRowHandler,value,statements,new StepLog(),new RunState());
    }
    public SyncContext<P> forStatements(Statements value) {
        return new SyncContext<>(tenantId,actorId,connectionId,preview,batchSize,maxRows,startedAt,
                source,identities,references,dataName,target,clear,useRowHandler,parameters,value,stepLog,runState);
    }
    public SyncContext<P> forBatchSize(int value) {
        return new SyncContext<>(tenantId,actorId,connectionId,preview,value,maxRows,startedAt,
                source,identities,references,dataName,target,clear,useRowHandler,parameters,statements,stepLog,runState);
    }
    public SyncContext<P> forSource(long sourceConnectionId) {
        if(source.local()) throw new IllegalStateException("外部连接须从原始运行上下文选择");
        return new SyncContext<>(tenantId,actorId,sourceConnectionId,preview,batchSize,maxRows,startedAt,
                source,identities,references,dataName,target,clear,useRowHandler,parameters,statements,stepLog,runState);
    }
    public SyncContext<P> forLocalData(String name,boolean clear,boolean useRowHandler) {
        return new SyncContext<>(tenantId,actorId,connectionId,preview,batchSize,maxRows,startedAt,
                SyncSource.local(target),identities,references,name,target,clear,useRowHandler,parameters,Statements.defaults(),new StepLog(),runState);
    }
    public SyncContext<P> forLocalData(String name,boolean clear) { return forLocalData(name,clear,true); }
}
