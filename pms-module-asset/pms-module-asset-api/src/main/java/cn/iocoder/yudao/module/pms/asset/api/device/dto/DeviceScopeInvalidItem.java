package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactException;

/** 无法进入完整设备范围的单项原因。 */
public record DeviceScopeInvalidItem(Long deviceId, String serialNumber, Reason reason) {

    public DeviceScopeInvalidItem {
        if (deviceId != null && deviceId <= 0) {
            throw invalid("deviceId must be positive when present");
        }
        if (serialNumber == null || serialNumber.trim().isEmpty() || reason == null) {
            throw invalid("invalid item requires serialNumber and reason");
        }
        serialNumber = serialNumber.trim();
    }

    public enum Reason {
        NOT_FOUND,
        STATUS_INELIGIBLE,
        PROJECT_MISMATCH
    }

    private static DeviceScopeFactException invalid(String message) {
        return new DeviceScopeFactException(DeviceScopeFactException.Code.INVALID_REQUEST, message);
    }
}
