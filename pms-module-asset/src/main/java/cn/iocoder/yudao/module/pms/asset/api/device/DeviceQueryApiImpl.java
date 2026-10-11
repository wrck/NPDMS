package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceProjectMatchQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceQueryApiImpl implements DeviceQueryApi {

    private final cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper deviceMapper;
    private final DeviceQueryService deviceQueryService;

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.List<cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationFact> lockProjectInstallationDevices(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery query) {
        if (query == null || query.tenantId() == null || !query.tenantId().equals(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0) throw new IllegalArgumentException("PROJECT_DEVICE_SCOPE_REQUIRED");
        return deviceMapper.selectProjectInstallationDevicesForUpdate(query);
    }

    @Override
    public DeviceSummaryDTO getDevice(Long deviceId) {
        DeviceDO device = deviceQueryService.getDevice(deviceId);
        if (device == null) {
            return null;
        }
        return new DeviceSummaryDTO(
                device.getId(), device.getTenantId(), device.getSn(),
                device.getProductCode(), device.getProductModel(), device.getProductName(),
                device.getShipmentTime(), device.getPackageNo(), device.getContractNo(),
                device.getShipmentRecordId(), device.getProjectId(), device.getProjectAssignmentVersion(),
                device.getCustomerId(), device.getCustomerAssignmentVersion(),
                device.getWarrantyStartDate(), device.getWarrantyEndDate(), device.getWarrantyStatus(),
                device.getConpVersion(), device.getConpType(), device.getConpSeries(), device.getConpMark());
    }

    @Override
    public Set<Long> resolveProjectIds(DeviceProjectMatchQuery query) {
        return deviceQueryService.resolveProjectIds(query);
    }
}
