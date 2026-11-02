package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectContractProjectionJob implements JobHandler {
    private final ProjectContractProjectionService service;
    private final TenantFrameworkService tenants;

    @Override
    public String execute(String param) {
        int updated = 0;
        int failed = 0;
        for (Long tenantId : tenants.getTenantIds()) {
            try {
                updated += TenantUtils.execute(tenantId, service::reconcile);
            } catch (RuntimeException ex) {
                failed++;
                log.error("项目合同号同步失败，租户 {}", tenantId, ex);
            }
        }
        if (failed > 0) throw new IllegalStateException("项目合同号同步失败租户数: " + failed);
        return "项目合同号更新 " + updated + " 条";
    }
}
