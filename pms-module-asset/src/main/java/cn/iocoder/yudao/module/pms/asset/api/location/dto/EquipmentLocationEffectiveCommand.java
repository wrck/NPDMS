package cn.iocoder.yudao.module.pms.asset.api.location.dto;

import java.time.LocalDateTime;

/**
 * 已确认安装使设备当前位置生效的跨模块命令。
 */
public record EquipmentLocationEffectiveCommand(
        Long equipmentId,
        Long installationId,
        Long siteId,
        Long siteLocationId,
        String locationText,
        String resolutionStatus,
        String locationSnapshot,
        LocalDateTime effectiveFrom) {
}
