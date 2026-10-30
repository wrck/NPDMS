package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query;
import java.util.Set;
public record DeviceLogSerialsQuery(Long tenantId, Long projectId, Set<String> serialNumbers) {
    public DeviceLogSerialsQuery { serialNumbers = serialNumbers == null ? Set.of() : Set.copyOf(serialNumbers); }
}
