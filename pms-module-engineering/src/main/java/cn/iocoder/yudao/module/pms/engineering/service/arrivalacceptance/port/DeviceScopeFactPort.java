package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port;

import java.util.List;
import java.util.Set;

/** IMP消费的AST稳定设备身份与当前直接项目归属；生产适配等待AST支撑Task。 */
public interface DeviceScopeFactPort {

    DeviceScopeFact resolveBySerials(Long tenantId, Long projectId, Set<String> serialNumbers);

    /** 任一归属版本不一致时抛出带AST/DEVICE_ASSIGNMENT_STALE字段的OwnerFactVersionMismatchException；不可用等故障不得伪装为版本不一致。 */
    DeviceScopeFact lockAndRevalidate(Long tenantId, Long projectId,
                                      List<ExpectedDeviceFact> expectedDevices);

    record DeviceScopeFact(Long projectId, List<DeviceFact> devices) {

        public DeviceScopeFact {
            if (projectId == null || projectId <= 0 || devices == null
                    || devices.stream().anyMatch(device -> device == null)) {
                throw new IllegalArgumentException("invalid device scope fact");
            }
            devices = List.copyOf(devices);
        }
    }

    record DeviceFact(Long deviceId, String serialNumber, Long currentProjectId,
                      Long projectAssignmentVersion) {

        public DeviceFact {
            serialNumber = trimToNull(serialNumber);
            if (deviceId == null || deviceId <= 0 || serialNumber == null
                    || currentProjectId == null || currentProjectId <= 0
                    || projectAssignmentVersion == null || projectAssignmentVersion < 0) {
                throw new IllegalArgumentException("invalid device fact");
            }
        }
    }

    record ExpectedDeviceFact(Long deviceId, String serialNumber, Long projectAssignmentVersion) {

        public ExpectedDeviceFact {
            serialNumber = trimToNull(serialNumber);
            if (deviceId == null || deviceId <= 0 || serialNumber == null
                    || projectAssignmentVersion == null || projectAssignmentVersion < 0) {
                throw new IllegalArgumentException("invalid expected device fact");
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
