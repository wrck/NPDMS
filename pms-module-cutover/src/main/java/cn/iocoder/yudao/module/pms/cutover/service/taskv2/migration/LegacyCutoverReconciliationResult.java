package cn.iocoder.yudao.module.pms.cutover.service.taskv2.migration;

public record LegacyCutoverReconciliationResult(boolean claimed, Long batchId, long mappedCount) {

    public static LegacyCutoverReconciliationResult empty() {
        return new LegacyCutoverReconciliationResult(false, null, 0);
    }
}
