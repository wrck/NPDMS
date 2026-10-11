package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectDeviceShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ShippedProjectQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectDeviceShipmentApiImpl implements ProjectDeviceShipmentApi {
    private final ProjectDeviceShipmentMapper shipments;

    @Override
    public Set<Long> resolveShippedProjectIds(Query query) {
        if (query == null || query.tenantId() == null
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || SecurityFrameworkUtils.getLoginUserId() == null || query.visibleProjectIds() == null)
            throw new IllegalArgumentException("PROJECT_SHIPMENT_SCOPE_REQUIRED");
        if (query.visibleProjectIds().isEmpty()) return Set.of();
        return new HashSet<>(shipments.selectShippedProjectIds(new ShippedProjectQuery(query.tenantId(),query.visibleProjectIds())));
    }
}
