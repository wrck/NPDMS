package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.DeviceListProjection;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceQueryService {

    private final DeviceMapper deviceMapper;
    private final DeviceAccessScopeService accessScopeService;

    public PageResult<DeviceListProjection> getPage(VisibleDevicePageQuery query) {
        Long tenantId = currentTenantId();
        if (tenantId == null || query.tenantId() != null && !tenantId.equals(query.tenantId())) {
            return PageResult.empty();
        }
        VisibleDevicePageQuery scopedQuery = new VisibleDevicePageQuery(
                tenantId, accessScopeService.visibleProjectIds(tenantId, SecurityFrameworkUtils.getLoginUserId()),
                query.sn(), query.productCode(), query.projectId(),
                query.customerId(), query.pageNo(), query.pageSize());
        return deviceMapper.selectVisibleDevicePage(scopedQuery);
    }

    public DeviceDO getDevice(Long deviceId) {
        Long tenantId = currentTenantId();
        accessScopeService.assertVisible(tenantId, SecurityFrameworkUtils.getLoginUserId(), deviceId);
        DeviceDO device = deviceMapper.selectById(deviceId);
        if (device == null || tenantId == null || !tenantId.equals(device.getTenantId())) {
            return null;
        }
        return device;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        var loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser == null ? null : loginUser.getTenantId();
    }
}
