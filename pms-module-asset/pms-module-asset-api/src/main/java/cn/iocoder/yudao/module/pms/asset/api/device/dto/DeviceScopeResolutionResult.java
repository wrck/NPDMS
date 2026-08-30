package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;

import java.util.Comparator;
import java.util.List;

/** resolveBySerials的全量结果；INVALID不返回部分事实。 */
public record DeviceScopeResolutionResult(Decision decision, DeviceScopeFact fact,
                                          List<DeviceScopeInvalidItem> invalidItems) {

    public DeviceScopeResolutionResult {
        if (decision == null || invalidItems == null || invalidItems.stream().anyMatch(item -> item == null)) {
            throw invalid("resolution result is incomplete");
        }
        invalidItems = ordered(invalidItems);
        if (decision == Decision.RESOLVED && (fact == null || !invalidItems.isEmpty())) {
            throw invalid("RESOLVED requires fact and no invalid items");
        }
        if (decision == Decision.INVALID && (fact != null || invalidItems.isEmpty())) {
            throw invalid("INVALID requires invalid items and no partial fact");
        }
    }

    public enum Decision {
        RESOLVED,
        INVALID
    }

    private static List<DeviceScopeInvalidItem> ordered(List<DeviceScopeInvalidItem> items) {
        return items.stream().sorted(Comparator
                .comparing(DeviceScopeInvalidItem::deviceId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(DeviceScopeInvalidItem::serialNumber)).toList();
    }

    private static DeviceScopeFactException invalid(String message) {
        return new DeviceScopeFactException(DeviceScopeFactException.Code.INVALID_REQUEST, message);
    }
}
