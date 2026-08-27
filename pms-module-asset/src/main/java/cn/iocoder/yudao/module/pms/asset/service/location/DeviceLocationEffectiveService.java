package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.DeviceLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.DeviceLocationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.query.DeviceLocationProjectionUpdate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_LOCATION_CONFLICT;

@Service
public class DeviceLocationEffectiveService {

    private final DeviceMapper deviceMapper;
    private final DeviceLocationMapper locationMapper;

    public DeviceLocationEffectiveService(
            DeviceMapper deviceMapper,
            DeviceLocationMapper locationMapper) {
        this.deviceMapper = deviceMapper;
        this.locationMapper = locationMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean effect(EquipmentLocationEffectiveCommand command) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        DeviceDO device = deviceMapper.selectByTenantAndIdForUpdate(tenantId, command.equipmentId());
        if (device == null) {
            return false;
        }
        DeviceLocationDO existing = locationMapper.selectByTenantAndInstallation(tenantId, command.installationId());
        if (existing != null) {
            if (isSameRequest(existing, device.getSn(), command)) {
                return true;
            }
            throw exception(AST_EQUIPMENT_LOCATION_CONFLICT);
        }
        locationMapper.closeCurrent(tenantId, device.getSn(), command.effectiveFrom());
        DeviceLocationDO location = toLocation(tenantId, device.getSn(), command);
        locationMapper.insert(location);
        DeviceLocationProjectionUpdate update = new DeviceLocationProjectionUpdate(
                tenantId, device.getId(), command.siteId(), command.siteLocationId(),
                command.resolutionStatus(), command.locationSnapshot(), command.effectiveFrom(),
                location.getId(), device.getVersion());
        if (deviceMapper.updateLocationProjection(update) == 0) {
            throw exception(AST_EQUIPMENT_LOCATION_CONFLICT);
        }
        return true;
    }

    private boolean isSameRequest(
            DeviceLocationDO existing,
            String deviceSn,
            EquipmentLocationEffectiveCommand command) {
        return Objects.equals(existing.getDeviceSn(), deviceSn)
                && Objects.equals(existing.getSiteId(), command.siteId())
                && Objects.equals(existing.getSiteLocationId(), command.siteLocationId())
                && Objects.equals(existing.getResolutionStatus(), command.resolutionStatus())
                && Objects.equals(existing.getLocationSnapshot(), command.locationSnapshot())
                && Objects.equals(existing.getEffectiveFrom(), command.effectiveFrom());
    }

    private DeviceLocationDO toLocation(
            Long tenantId,
            String deviceSn,
            EquipmentLocationEffectiveCommand command) {
        DeviceLocationDO location = new DeviceLocationDO();
        location.setTenantId(tenantId);
        location.setDeviceSn(deviceSn);
        location.setSiteId(command.siteId());
        location.setSiteLocationId(command.siteLocationId());
        location.setResolutionStatus(command.resolutionStatus());
        location.setLocationSnapshot(command.locationSnapshot());
        location.setEffectiveFrom(command.effectiveFrom());
        location.setInstallationId(command.installationId());
        location.setSourceSystem("IMP");
        location.setSourceKey(String.valueOf(command.installationId()));
        location.setVersion(0);
        location.setCreator("");
        location.setUpdater("");
        return location;
    }
}
