package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 按冻结设备身份与水位执行锁定重验。 */
public record DeviceScopeRevalidationQuery(Long tenantId, Long projectId,
                                           List<ExpectedDevice> expectedDevices,
                                           ExpectedScopeWatermark expectedScopeWatermark) {

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
        List<ExpectedWatermarkEntry> expectedEntries = expectedDevices.stream()
                .map(device -> new ExpectedWatermarkEntry(
                        device.deviceId(), device.projectAssignmentVersion()))
                .toList();
        if (expectedScopeWatermark == null || !expectedEntries.equals(expectedScopeWatermark.entries())) {
            throw invalid("expectedScopeWatermark must exactly match expectedDevices");
        }
    }

    /**
     * Provider锁行后校验稳定设备身份；同deviceId的SN比较键变化属于Owner数据损坏。
     */
    public void requireCurrentSerialIdentity(Long deviceId, String currentSerialNumber) {
        ExpectedDevice expected = expectedDevices.stream()
                .filter(device -> device.deviceId().equals(deviceId))
                .findFirst()
                .orElseThrow(() -> corrupted("locked deviceId is outside the expected set"));
        final String currentKey;
        try {
            currentKey = DeviceScopeResolveQuery.comparisonKey(currentSerialNumber);
        } catch (DeviceScopeFactException exception) {
            throw corrupted("current owner serial is invalid");
        }
        if (!DeviceScopeResolveQuery.comparisonKey(expected.serialNumber()).equals(currentKey)) {
            throw corrupted("current owner serial identity changed for the same deviceId");
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

    /** 调用方冻结的结构化期望水位；与Provider输出水位分型。 */
    public record ExpectedScopeWatermark(List<ExpectedWatermarkEntry> entries) {

        public ExpectedScopeWatermark {
            if (entries == null || entries.isEmpty() || entries.stream().anyMatch(entry -> entry == null)) {
                throw invalid("expected watermark entries must be a non-empty complete set");
            }
            entries = entries.stream().sorted(Comparator.comparing(ExpectedWatermarkEntry::deviceId)).toList();
            for (int index = 1; index < entries.size(); index++) {
                if (entries.get(index - 1).deviceId().equals(entries.get(index).deviceId())) {
                    throw invalid("duplicate deviceId in expected watermark");
                }
            }
        }
    }

    public record ExpectedWatermarkEntry(Long deviceId, Long projectAssignmentVersion) {

        public ExpectedWatermarkEntry {
            requirePositive(deviceId, "deviceId");
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

    private static DeviceScopeFactException corrupted(String message) {
        return new DeviceScopeFactException(DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED, message);
    }
}
