package cn.iocoder.yudao.module.pms.cutover.service.plan.migration;

public record LegacyCutoverPlanReconciliationResult(boolean claimed, Long batchId,
                                                     long mapped, long issues, long retained) {

    public static LegacyCutoverPlanReconciliationResult empty() {
        return new LegacyCutoverPlanReconciliationResult(false, null, 0, 0, 0);
    }
}
