package cn.iocoder.yudao.module.pms.asset.service.projection;

import java.util.List;

public record DeviceProjectionReconciliationResult(
        boolean rebuilt,
        List<DeviceProjectionType> driftTypes,
        List<DeviceProjectionType> missingSourceTypes) {
}
