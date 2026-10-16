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
    private final cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi scopes;

    @Override
    public Set<Long> resolveShippedProjectIds(Query query) {
        if (query == null || query.tenantId() == null
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || SecurityFrameworkUtils.getLoginUserId() == null || query.visibleProjectIds() == null)
            throw new IllegalArgumentException("PROJECT_SHIPMENT_SCOPE_REQUIRED");
        if (query.visibleProjectIds().isEmpty()) return Set.of();
        return new HashSet<>(shipments.selectShippedProjectIds(new ShippedProjectQuery(query.tenantId(),query.visibleProjectIds())));
    }

    @Override
    public DevicePage pageDevices(DeviceQuery query) {
        if (query == null || query.tenantId() == null
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.actorId() == null || !Objects.equals(query.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || query.projectId() == null || query.projectId() <= 0
                || query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100
                || query.keyword() != null && query.keyword().length() > 128)
            throw new IllegalArgumentException("PROJECT_SHIPMENT_SCOPE_REQUIRED");
        var scope = scopes.resolveCurrent(cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery.builder()
                .tenantId(query.tenantId()).subjectUserId(query.actorId()).anchorProjectId(query.projectId())
                .actionCode(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || !scope.fullProjectIds().contains(query.projectId()))
            throw new org.springframework.security.access.AccessDeniedException("无权查看该项目设备");
        var selection = cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShippedDevicePageQuery.builder()
                .tenantId(query.tenantId()).projectId(query.projectId())
                .keyword(query.keyword() == null ? "" : query.keyword().trim())
                .pageNo(query.pageNo()).pageSize(query.pageSize()).build();
        return new DevicePage(shipments.selectProjectDevices(selection).stream().map(device -> new Device(
                device.getId(), device.getSn(), device.getProductCode(), device.getProductModel(), device.getConpVersion())).toList(),
                shipments.countProjectDevices(selection));
    }
}
