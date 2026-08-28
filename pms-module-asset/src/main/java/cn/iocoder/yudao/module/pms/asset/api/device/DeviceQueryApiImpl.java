package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceQueryApiImpl implements DeviceQueryApi {

    private final DeviceQueryService deviceQueryService;

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
}
