package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 按SN解析设备范围；tenantId必须与受信运行时租户一致。 */
public record DeviceScopeResolveQuery(Long tenantId, Long projectId, List<String> serialNumbers) {

    public DeviceScopeResolveQuery {
        requirePositive(tenantId, "tenantId");
        requirePositive(projectId, "projectId");
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            throw invalid("serialNumbers must not be empty");
        }
        List<String> normalized = serialNumbers.stream().map(DeviceScopeResolveQuery::trimSerial).toList();
        Set<String> keys = new HashSet<>();
        for (String serial : normalized) {
            if (!keys.add(comparisonKey(serial))) {
                throw new DeviceScopeFactException(DeviceScopeFactException.Code.DUPLICATE_SERIAL,
                        "serialNumbers contain a duplicate after normalization");
            }
        }
        serialNumbers = List.copyOf(normalized);
    }

    public static String comparisonKey(String serialNumber) {
        return trimSerial(serialNumber).toUpperCase(Locale.ROOT);
    }

    private static String trimSerial(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid("serialNumber must not be blank");
        }
        return value.trim();
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field + " must be positive");
        }
    }

    private static DeviceScopeFactException invalid(String message) {
        return new DeviceScopeFactException(DeviceScopeFactException.Code.INVALID_REQUEST, message);
    }
}
