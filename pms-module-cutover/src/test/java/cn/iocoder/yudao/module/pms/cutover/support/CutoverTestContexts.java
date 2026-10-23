package cn.iocoder.yudao.module.pms.cutover.support;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardContext;
import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext;
import cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext;
import cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext;

public final class CutoverTestContexts {
    private CutoverTestContexts() { }
    public static CutoverDashboardContext dashboard() {
        return dashboard(new Identity(1L, 9L));
    }
    public record Identity(Long tenantId, Long actorId) { }
    public static CutoverDashboardContext dashboard(Identity identity) {
        return new CutoverDashboardContext(new CutoverTaskContext(identity.tenantId(), identity.actorId(), "test", true, true, true, true, true),
                new CutoverPlanContext(identity.tenantId(), identity.actorId(), "test", true, true, true, true),
                new CutoverApprovalContext(identity.tenantId(), identity.actorId(), "test", true, true, true),
                new CutoverClosureContext(identity.tenantId(), identity.actorId(), "test", true, true, true));
    }
}
