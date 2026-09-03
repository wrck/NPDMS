package cn.iocoder.yudao.module.pms.cutover.controller.admin.dashboard;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;

/** Trusted dashboard request context. Production implementation waits for full Owner assembly. */
public interface CutoverDashboardRequestContext {

    TrustedContext current();

    record TrustedContext(long tenantId, long actorId, PermissionFacts permissions) {
        public TrustedContext {
            if (tenantId <= 0 || actorId <= 0 || permissions == null) {
                throw new IllegalArgumentException("dashboard trusted context is invalid");
            }
        }
    }
}
