package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsGatewayApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pms.integration.device-ops", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DeviceOpsReconciliationJob implements JobHandler {

    private final CollectionTaskReconciliationService service;
    private final Environment environment;
    private final CollectionBusinessResultDeliveryService resultDelivery;

    @Override
    @TenantJob
    public String execute(String param) {
        if (TenantContextHolder.getTenantId() != null) {
            return reconcile();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            TenantContextHolder.getRequiredTenantId();
        }
        String[] result = new String[1];
        TenantUtils.execute(0L, () -> result[0] = reconcile());
        return result[0];
    }

    private String reconcile() {
        return service.reconcileDue(TenantContextHolder.getRequiredTenantId()) + "；" + resultDelivery.deliverDue();
    }
}
