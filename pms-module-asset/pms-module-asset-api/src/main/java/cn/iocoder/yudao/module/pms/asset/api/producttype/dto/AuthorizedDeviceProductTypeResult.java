package cn.iocoder.yudao.module.pms.asset.api.producttype.dto;

import java.time.LocalDateTime;

public record AuthorizedDeviceProductTypeResult(
        Long deviceId,
        String productTypeCode,
        String displayName,
        boolean enabled,
        String sourceVersion,
        String resolutionStatus,
        String syncStatus,
        LocalDateTime lastSuccessfulSyncTime,
        boolean fromLastSuccessfulCopy) {
}
