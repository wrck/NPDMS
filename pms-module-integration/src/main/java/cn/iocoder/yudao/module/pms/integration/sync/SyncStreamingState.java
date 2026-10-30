package cn.iocoder.yudao.module.pms.integration.sync;

import java.time.LocalDateTime;

/** Last committed streaming boundary. It advances in the same transaction as target writes and evidence. */
public record SyncStreamingState(int sourceIndex, Object lastKey, long committedRows,
                                 int committedChunks, LocalDateTime upper) {
    public static SyncStreamingState start(LocalDateTime upper) {
        return new SyncStreamingState(0, null, 0, 0, upper);
    }
    public SyncStreamingState committed(Object key, int rows) {
        return new SyncStreamingState(sourceIndex, key, committedRows + rows, committedChunks + 1, upper);
    }
    public SyncStreamingState nextSource() {
        return new SyncStreamingState(sourceIndex + 1, null, committedRows, committedChunks, upper);
    }
    public boolean finished(int sourceCount) { return sourceIndex >= sourceCount; }
}
