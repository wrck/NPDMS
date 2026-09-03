package cn.iocoder.yudao.module.pms.cutover.job;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.cutover.service.closure.migration.LegacyCutoverClosureReconciliationService;

/** Paused legacy closure reconciliation job; it only consumes PLT STAGED_READY batches. */
public class LegacyCutoverClosureReconciliationJob implements JobHandler {

    private final LegacyCutoverClosureReconciliationService reconciliationService;

    public LegacyCutoverClosureReconciliationJob(LegacyCutoverClosureReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Override
    @TenantJob
    public String execute(String param) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String correlationId = "CUT-CLOSURE-LEGACY-JOB:" + tenantId + ":" + System.currentTimeMillis();
        var result = reconciliationService.reconcileNext(tenantId, correlationId);
        if (!result.claimed()) return "无待核对旧割接闭环批次";
        return "旧割接闭环批次 " + result.batchId() + "：问题 " + result.issues()
                + "，保留 " + result.retained();
    }
}
