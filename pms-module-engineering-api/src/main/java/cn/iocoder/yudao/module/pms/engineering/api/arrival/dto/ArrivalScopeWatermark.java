package cn.iocoder.yudao.module.pms.engineering.api.arrival.dto;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** COM范围版本与AST设备归属版本组成的结构化水位。 */
public record ArrivalScopeWatermark(
        Long deliveryScopeVersion,
        Map<Long, Long> deviceAssignmentVersions) {

    public ArrivalScopeWatermark {
        if (deliveryScopeVersion == null || deliveryScopeVersion <= 0
                || deviceAssignmentVersions == null) {
            throw new IllegalArgumentException("invalid arrival scope watermark");
        }
        TreeMap<Long, Long> ordered = new TreeMap<>();
        deviceAssignmentVersions.forEach((deviceId, assignmentVersion) -> {
            if (deviceId == null || deviceId <= 0
                    || assignmentVersion == null || assignmentVersion < 0) {
                throw new IllegalArgumentException("invalid device assignment watermark");
            }
            ordered.put(deviceId, assignmentVersion);
        });
        deviceAssignmentVersions = Collections.unmodifiableMap(ordered);
    }
}
