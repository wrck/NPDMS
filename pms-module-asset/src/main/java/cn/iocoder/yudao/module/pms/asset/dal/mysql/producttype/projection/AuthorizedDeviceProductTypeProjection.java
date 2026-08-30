package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.projection;

import java.time.LocalDateTime;

public record AuthorizedDeviceProductTypeProjection(
        Long deviceId,
        String productTypeCode,
        String displayName,
        Boolean enabled,
        String sourceVersion,
        String resolutionStatus,
        String syncStatus,
        LocalDateTime lastSuccessfulSyncTime) {
}
