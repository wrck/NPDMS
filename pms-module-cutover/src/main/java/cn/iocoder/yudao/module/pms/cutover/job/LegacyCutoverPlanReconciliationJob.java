package cn.iocoder.yudao.module.pms.cutover.job;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.cutover.service.plan.migration.LegacyCutoverPlanReconciliationResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.migration.LegacyCutoverPlanReconciliationService;

/** 暂停登记的旧割接方案核对Job；只消费PLT STAGED_READY批次。 */
public class LegacyCutoverPlanReconciliationJob implements JobHandler {

    private final LegacyCutoverPlanReconciliationService reconciliationService;

    public LegacyCutoverPlanReconciliationJob(LegacyCutoverPlanReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Override
    @TenantJob
    public String execute(String param) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String correlationId = "CUT-PLAN-LEGACY-JOB:" + tenantId + ":" + System.currentTimeMillis();
        LegacyCutoverPlanReconciliationResult result = reconciliationService.reconcileNext(tenantId, correlationId);
        if (!result.claimed()) {
            return "无待核对旧割接方案批次";
        }
        return "旧割接方案批次 " + result.batchId() + "：映射 " + result.mapped()
                + "，问题 " + result.issues() + "，保留 " + result.retained();
    }
}
