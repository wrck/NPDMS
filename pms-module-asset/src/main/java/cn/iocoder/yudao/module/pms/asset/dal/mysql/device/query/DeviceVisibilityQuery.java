package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Set;

public record DeviceVisibilityQuery(
        Long tenantId,
        Long deviceId,
        Set<Long> visibleProjectIds) {
}
