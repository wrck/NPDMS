package cn.iocoder.yudao.module.pms.asset.service.shipment.command;

import java.time.LocalDateTime;

public record ApplyDeviceShipmentCommand(
        Long tenantId,
        String deviceSn,
        LocalDateTime shipmentTime,
        String packageNo,
        String contractNo,
        String eventType,
        String sourceSystem,
        String sourceKey,
        String sourceVersion,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime syncedAt,
        String syncStatus) {
}
