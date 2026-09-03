package cn.iocoder.yudao.module.pms.engineering.api.arrival.dto;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 到货签收项目事实查询。tenantId必须来自受信调用上下文。 */
public record ArrivalAcceptanceFactQuery(
        Long tenantId,
        Long projectId,
        Set<Long> deviceIds,
        List<ArrivalQuantityScopeFact> quantityScopes) {

    public ArrivalAcceptanceFactQuery {
        if (tenantId == null || tenantId < 0 || projectId == null || projectId <= 0
                || deviceIds == null) {
            throw new IllegalArgumentException("invalid arrival acceptance fact query");
        }
        TreeSet<Long> orderedDeviceIds = new TreeSet<>();
        for (Long deviceId : deviceIds) {
            if (deviceId == null || deviceId <= 0) {
                throw new IllegalArgumentException("invalid arrival device scope");
            }
            orderedDeviceIds.add(deviceId);
        }
        deviceIds = Collections.unmodifiableSet(orderedDeviceIds);
        quantityScopes = ArrivalQuantityScopeFact.normalize(quantityScopes);
        if (deviceIds.isEmpty() && quantityScopes.isEmpty()) {
            throw new IllegalArgumentException("arrival acceptance scope is empty");
        }
    }
}
