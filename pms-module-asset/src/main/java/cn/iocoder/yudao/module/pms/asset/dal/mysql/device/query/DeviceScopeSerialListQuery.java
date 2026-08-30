package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.List;
import java.util.Objects;

/** 按受信租户和规范化SN集合读取设备范围投影。 */
public record DeviceScopeSerialListQuery(Long tenantId, List<String> serialNumbers) {

    public DeviceScopeSerialListQuery {
        Objects.requireNonNull(tenantId, "tenantId");
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            throw new IllegalArgumentException("serialNumbers must not be empty");
        }
        serialNumbers = List.copyOf(serialNumbers);
    }
}
