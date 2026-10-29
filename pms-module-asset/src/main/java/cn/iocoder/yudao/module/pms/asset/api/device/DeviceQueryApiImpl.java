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
    private final cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceCurrentModelMapper currentModels;

    @Override
    public java.util.List<Long> findProjectDeviceIdsByProductFilter(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceProductQuery query) {
        if (query == null || query.tenantId() == null || !query.tenantId().equals(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0 || query.matchMode() == null) throw new IllegalArgumentException("PROJECT_DEVICE_SCOPE_REQUIRED");
        if (query.productFilters() == null || query.productFilters().isEmpty()) return java.util.List.of();
        return deviceMapper.selectProjectDeviceIdsByProductFilter(query);
    }

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
        return summary(device, resolveModels(java.util.List.of(device)).get(deviceId));
    }

    @Override
    public java.util.List<DeviceSummaryDTO> getVisibleDeviceSummaries(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryBatchQuery query) {
        var devices = deviceQueryService.getVisibleDevices(query);
        var models = resolveModels(devices);
        return devices.stream().map(device -> summary(device, models.get(device.getId()))).toList();
    }

    @Override
    public java.util.List<DeviceSummaryDTO> getProjectDeviceSummaries(
            cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery query) {
        var devices = deviceQueryService.getProjectDevices(query);
        var models = resolveModels(devices);
        return devices.stream().map(device -> summary(device, models.get(device.getId()))).toList();
    }

    private java.util.Map<Long, String> resolveModels(java.util.List<DeviceDO> devices) {
        var ids = devices.stream().filter(device -> device.getProductModel() == null || device.getProductModel().isBlank())
                .map(DeviceDO::getId).collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) return java.util.Map.of();
        var result = new java.util.HashMap<Long, String>();
        currentModels.selectCurrentModels(new cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.DeviceCurrentModelQuery(
                devices.getFirst().getTenantId(), ids)).forEach(model -> result.put(model.deviceId(), model.productModel()));
        return result;
    }

    private static DeviceSummaryDTO summary(DeviceDO device, String currentModel) {
        return new DeviceSummaryDTO(
                device.getId(), device.getTenantId(), device.getSn(),
                device.getProductCode(), device.getProductModel() == null || device.getProductModel().isBlank()
                        ? currentModel : device.getProductModel(), device.getProductName(),
                device.getShipmentTime(), device.getPackageNo(), device.getContractNo(),
                device.getShipmentRecordId(), device.getProjectId(), device.getProjectAssignmentVersion(),
                device.getCustomerId(), device.getCustomerAssignmentVersion(),
                device.getWarrantyStartDate(), device.getWarrantyEndDate(), device.getWarrantyStatus(),
                device.getConpVersion(), device.getConpType(), device.getConpSeries(), device.getConpMark(),
                device.getName(), device.getLocationSnapshot());
    }

    @Override
    public Set<Long> resolveProjectIds(DeviceProjectMatchQuery query) {
        return deviceQueryService.resolveProjectIds(query);
    }
}
