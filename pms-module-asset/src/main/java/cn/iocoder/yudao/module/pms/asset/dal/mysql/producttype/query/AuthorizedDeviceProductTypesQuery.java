package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query;

import java.time.LocalDateTime;
import java.util.Set;

public record AuthorizedDeviceProductTypesQuery(
        Long tenantId,
        Set<Long> deviceIds,
        Set<Long> visibleProjectIds,
        LocalDateTime effectiveAt) {

    public AuthorizedDeviceProductTypesQuery {
        deviceIds = deviceIds == null ? null : Set.copyOf(deviceIds);
        visibleProjectIds = visibleProjectIds == null ? null : Set.copyOf(visibleProjectIds);
    }
}
