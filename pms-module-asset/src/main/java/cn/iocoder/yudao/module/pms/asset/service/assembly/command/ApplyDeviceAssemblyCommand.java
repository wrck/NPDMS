package cn.iocoder.yudao.module.pms.asset.service.assembly.command;

import java.time.LocalDateTime;

public record ApplyDeviceAssemblyCommand(
        Long tenantId,
        String parentDeviceSn,
        String childDeviceSn,
        String positionCode,
        String assemblyType,
        LocalDateTime effectiveAt,
        String evidenceRef,
        String sourceSystem,
        String sourceKey,
        String sourceVersion) {
}
