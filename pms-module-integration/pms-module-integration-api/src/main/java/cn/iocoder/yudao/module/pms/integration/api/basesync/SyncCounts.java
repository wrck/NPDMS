package cn.iocoder.yudao.module.pms.integration.api.basesync;

public record SyncCounts(long read, long inserted, long updated, long unchanged, long deleted) {
    public SyncCounts {
        if (read < 0 || inserted < 0 || updated < 0 || unchanged < 0 || deleted < 0)
            throw new IllegalArgumentException("同步计数不能为负数");
    }
    public static SyncCounts empty() { return new SyncCounts(0, 0, 0, 0, 0); }
    public SyncCounts plus(SyncCounts other) {
        return new SyncCounts(Math.addExact(read, other.read), Math.addExact(inserted, other.inserted),
                Math.addExact(updated, other.updated), Math.addExact(unchanged, other.unchanged),
                Math.addExact(deleted, other.deleted));
    }
    public static <R> SyncCounts fromActions(java.util.List<R> rows,java.util.function.Function<R,SyncWriteAction> action) {
        long inserted=0,updated=0,unchanged=0;
        for(R row:rows) switch(action.apply(row)) {
            case INSERT -> inserted++;
            case UPDATE -> updated++;
            case UNCHANGED -> unchanged++;
        }
        return new SyncCounts(rows.size(),inserted,updated,unchanged,0);
    }
}
