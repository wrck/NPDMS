package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;

import java.util.Comparator;
import java.util.List;

/** lockAndRevalidate的全量结果；只有版本变化属于STALE。 */
public record DeviceScopeRevalidationResult(Decision decision, DeviceScopeFact currentFact,
                                            List<DeviceScopeInvalidItem> invalidItems) {

    public DeviceScopeRevalidationResult {
        if (decision == null || invalidItems == null || invalidItems.stream().anyMatch(item -> item == null)) {
            throw corrupted("revalidation result is incomplete");
        }
        invalidItems = invalidItems.stream().sorted(Comparator
                .comparing(DeviceScopeInvalidItem::deviceId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(DeviceScopeInvalidItem::serialNumber)).toList();
        if ((decision == Decision.VALID || decision == Decision.STALE)
                && (currentFact == null || !invalidItems.isEmpty())) {
            throw corrupted("VALID or STALE requires current fact and no invalid items");
        }
        if (decision == Decision.INVALID && (currentFact != null || invalidItems.isEmpty())) {
            throw corrupted("INVALID requires invalid items and no partial fact");
        }
    }

    public enum Decision {
        VALID,
        STALE,
        INVALID
    }

    private static DeviceScopeFactException corrupted(String message) {
        return new DeviceScopeFactException(DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED, message);
    }
}
