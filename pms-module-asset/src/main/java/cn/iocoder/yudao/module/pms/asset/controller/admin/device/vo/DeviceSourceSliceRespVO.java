package cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo;

import java.time.LocalDateTime;

public record DeviceSourceSliceRespVO(
        String sourceSystem,
        String sourceKey,
        String sourceVersion,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime syncedAt,
        String syncStatus,
        Object data) {

    public static DeviceSourceSliceRespVO notAvailable(String sourceSystem) {
        return new DeviceSourceSliceRespVO(
                sourceSystem, null, null, null, null, "NOT_AVAILABLE", null);
    }
}
