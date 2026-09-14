package cn.iocoder.yudao.module.pms.integration.sync;

import java.time.LocalDateTime;
import java.util.List;

/** Persisted with each successful page transaction, never advanced on a failed page. */
public record SyncPagingState(int sourceIndex, long afterId, List<Long> upperIds,
                              int completedPages, LocalDateTime upper) {
    public boolean finished() { return sourceIndex >= upperIds.size(); }
    public SyncPagingState advance(long lastId, int rowCount, int pageSize) {
        boolean finishedSource = rowCount < pageSize || lastId >= upperIds.get(sourceIndex);
        return new SyncPagingState(sourceIndex + (finishedSource ? 1 : 0), finishedSource ? 0 : lastId,
                upperIds, completedPages + 1, upper);
    }
}
