package cn.iocoder.yudao.module.pms.asset.dal.mysql.location.query;

import java.time.LocalDateTime;

public record DeviceLocationProjectionUpdate(
        Long tenantId,
        Long deviceId,
        Long siteId,
        Long siteLocationId,
        String resolutionStatus,
        String locationSnapshot,
        LocalDateTime effectiveFrom,
        Long locationRecordId,
        Integer expectedVersion) {
}
