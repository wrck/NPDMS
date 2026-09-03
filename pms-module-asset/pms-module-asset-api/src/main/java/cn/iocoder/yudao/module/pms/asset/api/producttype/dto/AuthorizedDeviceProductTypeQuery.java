package cn.iocoder.yudao.module.pms.asset.api.producttype.dto;

import java.util.List;
import java.util.Objects;

public record AuthorizedDeviceProductTypeQuery(
        Long subjectUserId,
        List<Long> deviceIds) {

    public AuthorizedDeviceProductTypeQuery {
        deviceIds = deviceIds == null ? List.of() : deviceIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
