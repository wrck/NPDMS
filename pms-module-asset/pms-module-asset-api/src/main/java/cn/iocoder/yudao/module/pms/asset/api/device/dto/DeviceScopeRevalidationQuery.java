package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 按冻结设备身份与水位执行锁定重验。 */
public record DeviceScopeRevalidationQuery(Long tenantId, Long projectId,
                                           List<ExpectedDevice> expectedDevices,
                                           DeviceScopeFact.ScopeWatermark expectedScopeWatermark) {

    public DeviceScopeRevalidationQuery {
        requirePositive(tenantId, "tenantId");
        requirePositive(projectId, "projectId");
        if (expectedDevices == null || expectedDevices.isEmpty()
                || expectedDevices.stream().anyMatch(device -> device == null)) {
            throw invalid("expectedDevices must be a non-empty complete set");
        }
        expectedDevices = expectedDevices.stream().sorted(Comparator.comparing(ExpectedDevice::deviceId)).toList();
        Set<String> serialKeys = new HashSet<>();
        for (int index = 0; index < expectedDevices.size(); index++) {
            ExpectedDevice device = expectedDevices.get(index);
            if (index > 0 && expectedDevices.get(index - 1).deviceId().equals(device.deviceId())) {
                throw invalid("duplicate expected deviceId");
            }
            if (!serialKeys.add(DeviceScopeResolveQuery.comparisonKey(device.serialNumber()))) {
                throw new DeviceScopeFactException(DeviceScopeFactException.Code.DUPLICATE_SERIAL,
                        "expectedDevices contain a duplicate serial after normalization");
            }
        }
        List<DeviceScopeFact.WatermarkEntry> expectedEntries = expectedDevices.stream()
                .map(device -> new DeviceScopeFact.WatermarkEntry(
                        device.deviceId(), device.projectAssignmentVersion()))
                .toList();
        if (expectedScopeWatermark == null || !expectedEntries.equals(expectedScopeWatermark.entries())) {
            throw invalid("expectedScopeWatermark must exactly match expectedDevices");
        }
    }

    public record ExpectedDevice(Long deviceId, String serialNumber, Long projectAssignmentVersion) {

        public ExpectedDevice {
            requirePositive(deviceId, "deviceId");
            if (serialNumber == null || serialNumber.trim().isEmpty()) {
                throw invalid("serialNumber must not be blank");
            }
            serialNumber = serialNumber.trim();
            if (projectAssignmentVersion == null || projectAssignmentVersion < 0) {
                throw invalid("projectAssignmentVersion must be non-negative");
            }
        }
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
