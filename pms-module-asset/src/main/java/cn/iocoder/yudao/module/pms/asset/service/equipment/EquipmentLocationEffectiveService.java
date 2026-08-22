package cn.iocoder.yudao.module.pms.asset.service.equipment;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentVersionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteMapper;
import cn.iocoder.yudao.module.pms.asset.enums.EquipmentChangeTypeEnum;
import cn.iocoder.yudao.module.pms.asset.enums.LocationResolutionStatus;
import cn.iocoder.yudao.module.pms.asset.service.location.SiteLocationTreeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.*;

/**
 * 设备当前位置生效服务。只消费公开命令，不读取工程实施域内部数据。
 */
@Service
@RequiredArgsConstructor
public class EquipmentLocationEffectiveService {

    private final EquipmentMapper equipmentMapper;
    private final EquipmentVersionMapper equipmentVersionMapper;
    private final SiteMapper siteMapper;
    private final SiteLocationTreeService siteLocationTreeService;

    @Transactional(rollbackFor = Exception.class)
    public void effect(EquipmentLocationEffectiveCommand command) {
        validateCommand(command);
        EquipmentDO before = equipmentMapper.selectById(command.equipmentId());
        if (before == null) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
        if (Objects.equals(before.getLocationSourceInstallationId(), command.installationId())) {
            return;
        }
        validateLocation(command);

        EquipmentDO update = new EquipmentDO();
        update.setId(before.getId());
        update.setSiteId(command.siteId());
        update.setSiteLocationId(command.siteLocationId());
        update.setLocation(command.locationText());
        update.setLocationResolutionStatus(command.resolutionStatus());
        update.setLocationSnapshot(command.locationSnapshot());
        update.setLocationEffectiveFrom(command.effectiveFrom());
        update.setLocationSourceInstallationId(command.installationId());
        if (equipmentMapper.updateLocationIfMatch(update, before.getVersion()) == 0) {
            throw exception(AST_EQUIPMENT_LOCATION_CONFLICT);
        }

        EquipmentDO after = equipmentMapper.selectById(before.getId());
        EquipmentVersionDO version = new EquipmentVersionDO();
        version.setEquipmentId(before.getId());
        version.setVersionNo(equipmentVersionMapper.selectMaxVersionNo(before.getId()) + 1);
        version.setChangeType(EquipmentChangeTypeEnum.LOCATION_EFFECTIVE);
        version.setChangeDescription("安装位置生效，来源安装记录：" + command.installationId());
        version.setBeforeSnapshot(JsonUtils.toJsonString(before));
        version.setAfterSnapshot(JsonUtils.toJsonString(after));
        equipmentVersionMapper.insert(version);
    }

    private void validateCommand(EquipmentLocationEffectiveCommand command) {
        if (command == null || command.equipmentId() == null || command.installationId() == null
                || command.effectiveFrom() == null || command.resolutionStatus() == null) {
            throw exception(AST_EQUIPMENT_LOCATION_COMMAND_INVALID);
        }
        boolean resolved = LocationResolutionStatus.RESOLVED.name().equals(command.resolutionStatus());
        boolean unresolved = LocationResolutionStatus.UNRESOLVED.name().equals(command.resolutionStatus());
        if (!resolved && !unresolved) {
            throw exception(AST_EQUIPMENT_LOCATION_COMMAND_INVALID);
        }
        if (resolved != (command.siteId() != null)) {
            throw exception(AST_EQUIPMENT_LOCATION_COMMAND_INVALID);
        }
        if (!resolved && command.siteLocationId() != null) {
            throw exception(AST_EQUIPMENT_LOCATION_COMMAND_INVALID);
        }
    }

    private void validateLocation(EquipmentLocationEffectiveCommand command) {
        if (command.siteId() == null) {
            return;
        }
        SiteDO site = siteMapper.selectById(command.siteId());
        if (site == null || !CommonStatusEnum.isEnable(site.getStatus())) {
            throw exception(AST_LOCATION_REFERENCE_INVALID);
        }
        if (command.siteLocationId() == null) {
            return;
        }
        SiteLocationDO location = siteLocationTreeService.get(command.siteLocationId(), null);
        if (!Objects.equals(location.getSiteId(), command.siteId())
                || !CommonStatusEnum.isEnable(location.getStatus())) {
            throw exception(AST_LOCATION_REFERENCE_INVALID);
        }
    }
}
