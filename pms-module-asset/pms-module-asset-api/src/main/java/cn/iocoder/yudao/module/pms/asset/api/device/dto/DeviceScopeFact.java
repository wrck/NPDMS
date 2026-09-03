package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;

import java.util.Comparator;
import java.util.List;

/** AST解析成功后的完整设备范围事实。 */
public record DeviceScopeFact(Long tenantId, Long projectId, List<Device> devices,
                              ScopeWatermark scopeWatermark) {

    public DeviceScopeFact {
        requirePositive(tenantId, "tenantId");
        requirePositive(projectId, "projectId");
        if (devices == null || devices.isEmpty() || devices.stream().anyMatch(device -> device == null)) {
            throw corrupted("devices must be a non-empty complete set");
        }
        devices = devices.stream().sorted(Comparator.comparing(Device::deviceId)).toList();
        for (int index = 0; index < devices.size(); index++) {
            Device device = devices.get(index);
            if (!projectId.equals(device.currentProjectId())) {
                throw corrupted("device project does not match fact project");
            }
            if (index > 0 && devices.get(index - 1).deviceId().equals(device.deviceId())) {
                throw corrupted("duplicate deviceId in fact");
            }
        }
        List<WatermarkEntry> expected = devices.stream()
                .map(device -> new WatermarkEntry(device.deviceId(), device.projectAssignmentVersion()))
                .toList();
        if (scopeWatermark == null || !expected.equals(scopeWatermark.entries())) {
            throw corrupted("scopeWatermark must exactly match devices");
        }
    }

    public record Device(Long deviceId, String serialNumber, Long currentProjectId,
                         Long projectAssignmentVersion) {

        public Device {
            requirePositive(deviceId, "deviceId");
            serialNumber = trimSerial(serialNumber);
            requirePositive(currentProjectId, "currentProjectId");
            requireNonNegative(projectAssignmentVersion, "projectAssignmentVersion");
        }
    }

    public record ScopeWatermark(List<WatermarkEntry> entries) {

        public ScopeWatermark {
            if (entries == null || entries.isEmpty() || entries.stream().anyMatch(entry -> entry == null)) {
                throw corrupted("watermark entries must be a non-empty complete set");
            }
            entries = entries.stream().sorted(Comparator.comparing(WatermarkEntry::deviceId)).toList();
            for (int index = 1; index < entries.size(); index++) {
                if (entries.get(index - 1).deviceId().equals(entries.get(index).deviceId())) {
                    throw corrupted("duplicate deviceId in watermark");
                }
            }
        }
    }

    public record WatermarkEntry(Long deviceId, Long projectAssignmentVersion) {

        public WatermarkEntry {
            requirePositive(deviceId, "deviceId");
            requireNonNegative(projectAssignmentVersion, "projectAssignmentVersion");
        }
    }

    private static String trimSerial(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw corrupted("serialNumber must not be blank");
        }
        return value.trim();
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw corrupted(field + " must be positive");
        }
    }

    private static void requireNonNegative(Long value, String field) {
        if (value == null || value < 0) {
            throw corrupted(field + " must be non-negative");
        }
    }

    private static DeviceScopeFactException corrupted(String message) {
        return new DeviceScopeFactException(DeviceScopeFactException.Code.OWNER_DATA_CORRUPTED, message);
    }
}
