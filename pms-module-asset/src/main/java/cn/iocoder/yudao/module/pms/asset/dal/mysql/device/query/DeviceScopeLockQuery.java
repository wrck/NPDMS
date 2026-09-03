package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 按稳定deviceId顺序锁定设备范围投影。 */
public record DeviceScopeLockQuery(Long tenantId, List<Long> deviceIds) {

    public DeviceScopeLockQuery {
        Objects.requireNonNull(tenantId, "tenantId");
        if (deviceIds == null || deviceIds.isEmpty() || deviceIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("deviceIds must not be empty");
        }
        deviceIds = deviceIds.stream().sorted(Comparator.naturalOrder()).toList();
    }
}
