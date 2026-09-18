package cn.iocoder.yudao.module.pms.asset.service.equipment;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteMapper;
import cn.iocoder.yudao.module.pms.asset.enums.LocationResolutionStatus;
import cn.iocoder.yudao.module.pms.asset.service.location.DeviceLocationEffectiveService;
import cn.iocoder.yudao.module.pms.asset.service.location.SiteLocationTreeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.*;

/**
 * 设备当前位置生效服务（ast_device 承载，pms_equipment 兜底分支已随旧链退役）。
 * 只消费公开命令，不读取工程实施域内部数据。
 */
@Service
@RequiredArgsConstructor
public class EquipmentLocationEffectiveService {

    private final SiteMapper siteMapper;
    private final SiteLocationTreeService siteLocationTreeService;
    private final DeviceLocationEffectiveService deviceLocationEffectiveService;

    @Transactional(rollbackFor = Exception.class)
    public void effect(EquipmentLocationEffectiveCommand command) {
        validateCommand(command);
        validateLocation(command);
        if (!deviceLocationEffectiveService.effect(command)) {
            throw exception(AST_EQUIPMENT_NOT_EXISTS);
        }
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
