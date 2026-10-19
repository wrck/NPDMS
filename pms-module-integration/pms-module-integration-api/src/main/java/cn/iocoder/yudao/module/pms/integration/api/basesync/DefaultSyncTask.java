package cn.iocoder.yudao.module.pms.integration.api.basesync;

import java.util.Objects;

/** Optional batch preparation and CRUD dispatch on top of the reference template workflow. */
public abstract class DefaultSyncTask<T,P> extends AbstractSyncTask<T,P> {
    /** A target Mapper SELECT for batch reference lookup/value replacement; null means no preparation. */
    protected String preparationStatement(SyncContext<P> context) { return null; }

    /** Simple append/clear-and-insert tasks need only their SELECT/INSERT (and explicit COUNT/DELETE). */
    protected SyncWriteAction syncDataAction(T row) { return SyncWriteAction.INSERT; }

    @Override protected void syncDataInsertBefore(SyncBatch<T,P> batch) {
        super.syncDataInsertBefore(batch);
        String selected=preparationStatement(batch.context());
        if(selected==null) return;
        // Full reads retain full prefill semantics, but each reference query still has bounded parameters.
        for(int start=0;start<batch.rows().size();start+=batch.context().batchSize()) {
            int end=Math.min(start+batch.context().batchSize(),batch.rows().size());
            var input=new SyncBatch<>(batch.context(),batch.rows().subList(start,end));
            var call=new MappedCall<>(batch.context(),selected,input);
            var prepared=this.<T,SyncBatch<T,P>>syncDataQueryBySql(batch.context().target(),call);
            if(prepared.size()!=input.rows().size())
                throw new IllegalStateException("同步预处理必须为本批每条数据返回一条结果");
            for(int index=0;index<prepared.size();index++)
                batch.rows().set(start+index,Objects.requireNonNull(prepared.get(index),"同步预处理结果不能为空"));
        }
    }

    @Override protected SyncCounts syncDataCount(SyncBatch<T,P> batch) {
        return SyncCounts.fromActions(batch.rows(),row->Objects.requireNonNull(syncDataAction(row),"同步写入动作不能为空"));
    }

    @Override protected void syncDataInsert(SyncBatch<T,P> batch) {
        super.syncDataInsert(batch.matching(row->syncDataAction(row)==SyncWriteAction.INSERT));
        super.syncDataUpdate(batch.matching(row->syncDataAction(row)==SyncWriteAction.UPDATE));
    }
}
