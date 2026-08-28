package cn.iocoder.yudao.module.pms.asset.service.version.command;

import java.time.LocalDateTime;

public record ApplyDeviceNetworkVersionCommand(
        Long tenantId,
        String deviceSn,
        String sourceDeviceKey,
        String sourceEventKey,
        LocalDateTime eventTime,
        String conpVersion,
        String conpType,
        String conpSeries,
        String conpMark,
        String bootVersion,
        String cpldVersion,
        String pcbVersion,
        Boolean customized,
        String sourceSystem,
        String sourceVersion,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime syncedAt,
        String syncStatus) {
}
