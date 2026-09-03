package cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.query;

import java.time.LocalDateTime;

public record DeviceCurrentProductTypeClose(
        Long tenantId,
        Long deviceId,
        LocalDateTime effectiveTo) {
}
