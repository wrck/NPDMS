package cn.iocoder.yudao.module.pms.integration.api.basesync;

import java.util.List;

/** Mutable input to the reference's prefill, split INSERT and after-fill workflow. */
public record SyncBatch<R,P>(SyncContext<P> context, List<R> rows) {
    public SyncBatch { rows = new java.util.ArrayList<>(rows); }
    public SyncBatch<R,P> matching(java.util.function.Predicate<R> predicate) {
        return new SyncBatch<>(context,rows.stream().filter(predicate).toList());
    }
}
