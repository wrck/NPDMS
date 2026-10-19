package cn.iocoder.yudao.module.pms.integration.sync;

/** Template-method lifecycle adapted from SPMS. The runner owns source IO, transaction, retry and receipts. */
abstract class AbstractSynchronizeTask<C, P, R> {
    protected final R syncData(C context) {
        try {
            syncDataBefore(context);
            P plan = syncDataQuery(context);
            R result = syncDataInsertWrapper(context, plan);
            syncDataSuccess(context, result);
            return result;
        } catch (RuntimeException failure) {
            syncDataFail(context, failure);
            throw failure;
        } finally {
            syncDataAfter(context);
        }
    }
    protected abstract void syncDataBefore(C context);
    protected abstract P syncDataQuery(C context);
    protected abstract R syncDataInsertWrapper(C context, P plan);
    protected void syncDataSuccess(C context, R result) {}
    protected void syncDataFail(C context, RuntimeException failure) {}
    protected void syncDataAfter(C context) {}
}
