package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public record ImplementationReadinessQuery(
        Long tenantId,
        Long projectId,
        List<ExpectedDevice> expectedDevices) {

    public ImplementationReadinessQuery {
        requirePositive(tenantId, "tenantId");
        requirePositive(projectId, "projectId");
        if (expectedDevices == null || expectedDevices.isEmpty()) {
            throw invalid("expectedDevices must not be empty");
        }
        expectedDevices = expectedDevices.stream()
                .sorted(Comparator.comparing(ExpectedDevice::deviceId)).toList();
        HashSet<Long> deviceIds = new HashSet<>();
        HashSet<String> serialKeys = new HashSet<>();
        for (ExpectedDevice device : expectedDevices) {
            if (!deviceIds.add(device.deviceId()) || !serialKeys.add(comparisonKey(device.serialNumber()))) {
                throw new ImplementationReadinessException(
                        ImplementationReadinessException.Code.DUPLICATE_DEVICE,
                        "duplicate implementation readiness device");
            }
        }
    }

    public static String comparisonKey(String serialNumber) {
        return serialNumber.trim().toUpperCase(Locale.ROOT);
    }

    static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field + " must be positive");
        }
    }

    static ImplementationReadinessException invalid(String message) {
        return new ImplementationReadinessException(ImplementationReadinessException.Code.INVALID_REQUEST, message);
    }

    public record ExpectedDevice(Long deviceId, String serialNumber, Long projectAssignmentVersion) {
        public ExpectedDevice {
            requirePositive(deviceId, "deviceId");
            if (serialNumber == null || serialNumber.isBlank() || !serialNumber.equals(serialNumber.trim())
                    || serialNumber.length() > 128) {
                throw invalid("serialNumber must be normalized and 1..128 characters");
            }
            if (projectAssignmentVersion == null || projectAssignmentVersion < 0) {
                throw invalid("projectAssignmentVersion must be non-negative");
            }
        }
    }
}
